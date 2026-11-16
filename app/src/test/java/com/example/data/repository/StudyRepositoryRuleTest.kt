package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Course
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.SubjectGrade
import com.example.data.model.UserPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndObserveStudyItem_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)

    val item =
      StudyItem(
        title = "Midterm Exam: Operating Systems",
        description = "Chapters 1-6, closed book",
        itemType = StudyItemType.EXAM.wireValue,
        courseCode = "CS 350",
        courseName = "Operating Systems",
        courseColorHex = "#D97706",
        location = "Science Hall 102",
        dueAtMillis = System.currentTimeMillis() + 86_400_000L,
        durationMinutes = 120L,
        priority = PriorityLevel.HIGH.wireValue,
        reminderMinutesBefore = 1440L,
        notifyEnabled = true,
      )

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createStudyItem(uid, item) }
    assertTrue(createResult.isSuccess)
    val createdId = createResult.getOrThrow()
    assertTrue(createdId.isNotBlank())

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeStudyItems(uid).first { list -> list.any { it.id == createdId } }
      }
    val saved = observed.first { it.id == createdId }
    assertEquals("Midterm Exam: Operating Systems", saved.title)
    assertEquals(StudyItemType.EXAM, saved.typeEnum)

    val syncResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateCalendarSyncInfo(uid, createdId, "gcal_event_abc", 1700000000000L)
      }
    assertTrue(syncResult.isSuccess)

    val toggleResult =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.toggleItemStatus(uid, createdId, true) }
    assertTrue(toggleResult.isSuccess)
  }

  @Test
  fun createCourseAndPreferences_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)

    val courseResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createCourse(
          uid,
          Course(code = "MATH 240", name = "Linear Algebra", instructor = "Dr. Gauss", colorHex = "#0D9488"),
        )
      }
    assertTrue(courseResult.isSuccess)

    val prefsResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.savePreferences(
          uid,
          UserPreferences(
            pushNotificationsEnabled = true,
            defaultHomeworkReminderMinutes = 120L,
            defaultExamReminderMinutes = 1440L,
            dailyDigestEnabled = true,
            dailyDigestHour = 8L,
            googleCalendarAutoSync = true,
            currentStreakDays = 6L,
            longestStreakDays = 10L,
            lastActiveDayKey = "2026-11-16",
            recentStreakDaysCsv = "2026-11-15,2026-11-16",
            themeMode = "dark",
            themePalette = "emerald",
            manualAccentHex = "#0F766E",
            studentName = "Alice Student",
            studentSchool = "State University",
            studentMajor = "Computer Science",
            studentYear = "Junior",
            studentIdLabel = "Class of 2027",
            studyGoal = "Ace operating systems",
            studentBio = "Building distributed study tools.",
          ),
        )
      }
    assertTrue(prefsResult.isSuccess)

    val observedPrefs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePreferences(uid).first { it != null }
      }
    assertEquals(6L, observedPrefs?.currentStreakDays)
    assertEquals("dark", observedPrefs?.themeMode)
    assertEquals("emerald", observedPrefs?.themePalette)
    assertEquals("Alice Student", observedPrefs?.studentName)
    assertEquals("Computer Science", observedPrefs?.studentMajor)
    assertEquals("Building distributed study tools.", observedPrefs?.studentBio)
  }

  @Test
  fun crossUserAccess_studyItem_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)
    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createStudyItem(
            aliceUid,
            StudyItem(
              title = "Alice Homework",
              dueAtMillis = System.currentTimeMillis() + 3_600_000L,
            ),
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRead = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getStudyItem(aliceUid, itemId) }
    assertTrue(bobRead.isFailure)
    val ex = bobRead.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun unauthenticatedUser_observeStudyItems_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = StudyRepository(firestore)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeStudyItems(aliceUid).first() }
    } catch (e: Throwable) {
      caught = e
    }
    assertNotNull(caught)
    val fsEx = caught?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun createAndObserveSubjectGrade_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore)

    val gradeId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createGrade(
            uid,
            SubjectGrade(
              subjectCode = "CS 350",
              subjectName = "Operating Systems",
              assessmentTitle = "Final Exam",
              numericScore = 94.5,
              maxScore = 100.0,
              letterGrade = "A",
            ),
          )
          .getOrThrow()
      }

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGrades(uid).first { list -> list.any { it.id == gradeId } }
      }
    val saved = observed.first { it.id == gradeId }
    assertEquals("A", saved.letterGrade)
    assertEquals(94.5, saved.numericScore, 0.01)

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateGrade(uid, saved.copy(numericScore = 97.0, letterGrade = "A+"))
      }
    assertTrue(updateResult.isSuccess)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_student@test.com"
    const val BOB_EMAIL = "bob_student@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
