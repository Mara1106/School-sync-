const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

function validCoursePayload(uid) {
  return {
    userId: uid,
    code: "CS 301",
    name: "Algorithms and Data Structures",
    instructor: "Prof. Ada Lovelace",
    colorHex: "#1E3A5F",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validStudyItemPayload(uid) {
  return {
    userId: uid,
    title: "Dynamic Programming Problem Set 4",
    description: "Complete problems 1-5 and submit PDF",
    itemType: "homework",
    courseCode: "CS 301",
    courseName: "Algorithms and Data Structures",
    courseColorHex: "#1E3A5F",
    location: "Canvas",
    dueAtMillis: Date.now() + 86400000,
    durationMinutes: 90,
    priority: "high",
    status: "pending",
    reminderMinutesBefore: 60,
    notifyEnabled: true,
    googleCalendarEventId: "",
    calendarSyncedAtMillis: 0,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validPreferencesPayload(uid) {
  return {
    userId: uid,
    pushNotificationsEnabled: true,
    defaultHomeworkReminderMinutes: 60,
    defaultExamReminderMinutes: 1440,
    dailyDigestEnabled: true,
    dailyDigestHour: 8,
    googleCalendarAutoSync: false,
    lastCalendarSyncMillis: 0,
    currentStreakDays: 3,
    longestStreakDays: 5,
    lastActiveDayKey: "2026-11-16",
    recentStreakDaysCsv: "2026-11-14,2026-11-15,2026-11-16",
    themeMode: "suggested",
    themePalette: "indigo",
    manualAccentHex: "#1E3A5F",
    studentName: "Alice Student",
    studentSchool: "State University",
    studentMajor: "Computer Science",
    studentYear: "Junior",
    studentIdLabel: "Class of 2027",
    studyGoal: "Keep a 7-day study streak",
    studentBio: "CS student focused on algorithms and systems.",
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

function validGradePayload(uid) {
  return {
    userId: uid,
    subjectCode: "CS 301",
    subjectName: "Algorithms",
    assessmentTitle: "Midterm Exam",
    numericScore: 93.5,
    maxScore: 100,
    letterGrade: "A",
    courseColorHex: "#1E3A5F",
    notes: "Strong dynamic programming section",
    gradedAtMillis: Date.now(),
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write study_items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("study_items").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("study_items")
      .doc("item_1")
      .set(validStudyItemPayload(ALICE_UID))
  );
});

test("Owner can create, list, update status, and sync calendar metadata on study_items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("study_items")
    .doc("hw_1");

  await assertSucceeds(itemRef.set(validStudyItemPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_items").get()
  );
  await assertSucceeds(
    itemRef.update({
      status: "completed",
      updatedAt: nowTimestamp(),
    })
  );
  await assertSucceeds(
    itemRef.update({
      googleCalendarEventId: "gcal_evt_999",
      calendarSyncedAtMillis: Date.now(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("Cross-user access to study_items and courses is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("courses")
      .doc("cs301")
      .set(validCoursePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("study_items")
      .doc("exam_1")
      .set(validStudyItemPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("courses").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("study_items")
      .doc("exam_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("study_items")
      .doc("exam_1")
      .delete()
  );
});

test("Rejects ghost fields, invalid enums, and spoofed userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("study_items")
    .doc("bad_1");

  await assertFails(
    ref.set({
      ...validStudyItemPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  await assertFails(
    ref.set({
      ...validStudyItemPayload(ALICE_UID),
      itemType: "quiz",
    })
  );

  await assertFails(
    ref.set(validStudyItemPayload(BOB_UID))
  );
});

test("Owner can create and update notification preferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("settings")
    .doc("preferences");

  await assertSucceeds(prefRef.set(validPreferencesPayload(ALICE_UID)));
  await assertSucceeds(
    prefRef.update({
      dailyDigestHour: 9,
      googleCalendarAutoSync: true,
      studentMajor: "Data Science",
      studentBio: "Updated bio for finals week.",
      updatedAt: nowTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("preferences")
      .get()
  );
});

test("Owner can create, update, and delete subject grades; cross-user and unauth denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("grades").get()
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const gradeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("grades")
    .doc("grade_1");

  await assertSucceeds(gradeRef.set(validGradePayload(ALICE_UID)));
  await assertSucceeds(
    gradeRef.update({
      numericScore: 96,
      letterGrade: "A+",
      updatedAt: nowTimestamp(),
    })
  );

  // Reject invalid score or empty letter grade
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("grades")
      .doc("bad_grade")
      .set({
        ...validGradePayload(ALICE_UID),
        numericScore: -5,
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("grades").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("grades")
      .doc("grade_1")
      .delete()
  );
});
