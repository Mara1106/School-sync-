package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Course
import com.example.data.model.ItemStatus
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.SubjectGrade
import com.example.data.model.UserPreferences
import com.example.data.repository.LocalStudyRepository
import com.example.data.repository.StudyDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalStudyRepositoryTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    File(context.filesDir, "studysync_local_data.json").delete()
  }

  @Test
  fun courseAndStudyItemCrud_persistsAcrossRepositoryInstances() = runBlocking {
    val repo = LocalStudyRepository(context)
    val guestId = LocalStudyRepository.LOCAL_USER_ID

    val courseId =
      repo
        .createCourse(
          guestId,
          Course(code = "CS 201", name = "Data Structures", instructor = "Prof. Ada", colorHex = "#0D9488"),
        )
        .getOrThrow()

    val itemId =
      repo
        .createStudyItem(
          guestId,
          StudyItem(
            title = "Binary Trees Lab",
            description = "Implement AVL rotations",
            itemType = StudyItemType.HOMEWORK.wireValue,
            courseCode = "CS 201",
            courseName = "Data Structures",
            courseColorHex = "#0D9488",
            dueAtMillis = 1_800_000_000_000L,
            priority = PriorityLevel.HIGH.wireValue,
            reminderMinutesBefore = 120L,
          ),
        )
        .getOrThrow()

    repo.toggleItemStatus(guestId, itemId, completed = true).getOrThrow()
    repo
      .savePreferences(
        guestId,
        UserPreferences(
          pushNotificationsEnabled = true,
          defaultHomeworkReminderMinutes = 90L,
          defaultExamReminderMinutes = 1440L,
          dailyDigestEnabled = false,
          dailyDigestHour = 9L,
          currentStreakDays = 5L,
          longestStreakDays = 9L,
          lastActiveDayKey = "2026-11-16",
          recentStreakDaysCsv = "2026-11-15,2026-11-16",
          themeMode = "dark",
          themePalette = "manual",
          manualAccentHex = "#0D9488",
          studentName = "Alex Rivera",
          studentSchool = "Pacific State University",
          studentMajor = "Computer Science",
          studentYear = "Junior",
          studentIdLabel = "Class of 2027",
          studyGoal = "Keep 7-day study streak",
          studentBio = "Focused on algorithms and distributed systems.",
        ),
      )
      .getOrThrow()

    // Reload from disk using a fresh instance
    val reloaded = LocalStudyRepository(context)
    val courses = reloaded.observeCourses(guestId).first()
    val items = reloaded.observeStudyItems(guestId).first()
    val prefs = reloaded.observePreferences(guestId).first()

    assertEquals(1, courses.size)
    assertEquals(courseId, courses.first().id)
    assertEquals("CS 201", courses.first().code)

    assertEquals(1, items.size)
    assertEquals(itemId, items.first().id)
    assertEquals("Binary Trees Lab", items.first().title)
    assertEquals(ItemStatus.COMPLETED.wireValue, items.first().status)

    assertNotNull(prefs)
    assertEquals(90L, prefs?.defaultHomeworkReminderMinutes)
    assertEquals(false, prefs?.dailyDigestEnabled)
    assertEquals(5L, prefs?.currentStreakDays)
    assertEquals(9L, prefs?.longestStreakDays)
    assertEquals("2026-11-16", prefs?.lastActiveDayKey)
    assertEquals("2026-11-15,2026-11-16", prefs?.recentStreakDaysCsv)
    assertEquals("dark", prefs?.themeMode)
    assertEquals("manual", prefs?.themePalette)
    assertEquals("#0D9488", prefs?.manualAccentHex)
    assertEquals("Alex Rivera", prefs?.studentName)
    assertEquals("Pacific State University", prefs?.studentSchool)
    assertEquals("Computer Science", prefs?.studentMajor)
    assertEquals("Junior", prefs?.studentYear)
    assertEquals("Class of 2027", prefs?.studentIdLabel)
    assertEquals("Keep 7-day study streak", prefs?.studyGoal)
    assertEquals("Focused on algorithms and distributed systems.", prefs?.studentBio)

    reloaded.deleteStudyItem(guestId, itemId).getOrThrow()
    reloaded.deleteCourse(guestId, courseId).getOrThrow()
    assertTrue(reloaded.observeStudyItems(guestId).first().isEmpty())
    assertTrue(reloaded.observeCourses(guestId).first().isEmpty())
  }

  @Test
  fun migrateGuestDataToCloud_transfersRecordsAndClearsLocalStore() = runBlocking {
    val localRepo = LocalStudyRepository(context)
    val guestId = LocalStudyRepository.LOCAL_USER_ID

    localRepo.createCourse(guestId, Course(code = "BIO 101", name = "Cell Biology"))
    localRepo.createStudyItem(
      guestId,
      StudyItem(
        title = "Midterm Review",
        itemType = StudyItemType.EXAM.wireValue,
        courseCode = "BIO 101",
        dueAtMillis = 1_800_100_000_000L,
      ),
    )
    localRepo.savePreferences(
      guestId,
      UserPreferences(
        defaultExamReminderMinutes = 720L,
        currentStreakDays = 4L,
        longestStreakDays = 7L,
        themeMode = "light",
        themePalette = "sunset",
        studentName = "Maya Lin",
        studentMajor = "Biology",
        studentBio = "Pre-med student preparing for finals.",
      ),
    )
    localRepo.createGrade(
      guestId,
      SubjectGrade(
        subjectCode = "BIO 101",
        subjectName = "Cell Biology",
        assessmentTitle = "Midterm",
        numericScore = 91.5,
        maxScore = 100.0,
        letterGrade = "A-",
      ),
    )

    val fakeCloud = FakeCloudStudyDataSource()
    val migratedCount = localRepo.migrateGuestDataToCloud(fakeCloud, "cloud_uid_42")

    assertEquals(3, migratedCount)
    assertEquals(1, fakeCloud.createdCourses.size)
    assertEquals("cloud_uid_42", fakeCloud.createdCourses.first().first)
    assertEquals("BIO 101", fakeCloud.createdCourses.first().second.code)

    assertEquals(1, fakeCloud.createdItems.size)
    assertEquals("Midterm Review", fakeCloud.createdItems.first().second.title)
    assertEquals(1, fakeCloud.createdGrades.size)
    assertEquals("A-", fakeCloud.createdGrades.first().second.letterGrade)
    assertEquals(91.5, fakeCloud.createdGrades.first().second.numericScore, 0.01)
    assertEquals(720L, fakeCloud.savedPreferences?.defaultExamReminderMinutes)
    assertEquals(4L, fakeCloud.savedPreferences?.currentStreakDays)
    assertEquals(7L, fakeCloud.savedPreferences?.longestStreakDays)
    assertEquals("light", fakeCloud.savedPreferences?.themeMode)
    assertEquals("sunset", fakeCloud.savedPreferences?.themePalette)
    assertEquals("Maya Lin", fakeCloud.savedPreferences?.studentName)
    assertEquals("Biology", fakeCloud.savedPreferences?.studentMajor)
    assertEquals("Pre-med student preparing for finals.", fakeCloud.savedPreferences?.studentBio)

    // Local guest data should now be cleared on memory and disk
    assertTrue(localRepo.observeCourses(guestId).first().isEmpty())
    assertTrue(localRepo.observeStudyItems(guestId).first().isEmpty())
    assertTrue(localRepo.observeGrades(guestId).first().isEmpty())
    assertNull(localRepo.observePreferences(guestId).first())

    val reloaded = LocalStudyRepository(context)
    assertTrue(reloaded.observeCourses(guestId).first().isEmpty())
    assertTrue(reloaded.observeStudyItems(guestId).first().isEmpty())
    assertTrue(reloaded.observeGrades(guestId).first().isEmpty())
  }

  private class FakeCloudStudyDataSource : StudyDataSource {
    val createdCourses = mutableListOf<Pair<String, Course>>()
    val createdItems = mutableListOf<Pair<String, StudyItem>>()
    val createdGrades = mutableListOf<Pair<String, SubjectGrade>>()
    var savedPreferences: UserPreferences? = null

    override fun observeCourses(userId: String): Flow<List<Course>> = MutableStateFlow(emptyList())
    override fun observeStudyItems(userId: String): Flow<List<StudyItem>> = MutableStateFlow(emptyList())
    override fun observePreferences(userId: String): Flow<UserPreferences?> = MutableStateFlow(null)
    override fun observeGrades(userId: String): Flow<List<SubjectGrade>> = MutableStateFlow(emptyList())

    override suspend fun createCourse(userId: String, course: Course): Result<String> {
      createdCourses.add(userId to course)
      return Result.success("cloud_course_${createdCourses.size}")
    }

    override suspend fun updateCourse(userId: String, course: Course): Result<Unit> = Result.success(Unit)
    override suspend fun deleteCourse(userId: String, courseId: String): Result<Unit> = Result.success(Unit)

    override suspend fun createStudyItem(userId: String, item: StudyItem): Result<String> {
      createdItems.add(userId to item)
      return Result.success("cloud_item_${createdItems.size}")
    }

    override suspend fun getStudyItem(userId: String, itemId: String): Result<StudyItem?> = Result.success(null)
    override suspend fun updateStudyItem(userId: String, item: StudyItem): Result<Unit> = Result.success(Unit)
    override suspend fun toggleItemStatus(userId: String, itemId: String, completed: Boolean): Result<Unit> =
      Result.success(Unit)

    override suspend fun updateCalendarSyncInfo(
      userId: String,
      itemId: String,
      googleCalendarEventId: String,
      syncedAtMillis: Long,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteStudyItem(userId: String, itemId: String): Result<Unit> = Result.success(Unit)

    override suspend fun savePreferences(userId: String, preferences: UserPreferences): Result<Unit> {
      savedPreferences = preferences
      return Result.success(Unit)
    }

    override suspend fun createGrade(userId: String, grade: SubjectGrade): Result<String> {
      createdGrades.add(userId to grade)
      return Result.success("cloud_grade_${createdGrades.size}")
    }

    override suspend fun updateGrade(userId: String, grade: SubjectGrade): Result<Unit> = Result.success(Unit)
    override suspend fun deleteGrade(userId: String, gradeId: String): Result<Unit> = Result.success(Unit)
  }
}
