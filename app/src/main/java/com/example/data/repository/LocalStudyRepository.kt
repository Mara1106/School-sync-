package com.example.data.repository

import android.content.Context
import com.example.data.model.Course
import com.example.data.model.ItemStatus
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.SubjectGrade
import com.example.data.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalStudyRepository(context: Context) : StudyDataSource {

  private val appContext = context.applicationContext
  private val storageFile = File(appContext.filesDir, FILE_NAME)
  private val mutex = Mutex()

  private val coursesFlow = MutableStateFlow<List<Course>>(emptyList())
  private val itemsFlow = MutableStateFlow<List<StudyItem>>(emptyList())
  private val preferencesFlow = MutableStateFlow<UserPreferences?>(null)
  private val gradesFlow = MutableStateFlow<List<SubjectGrade>>(emptyList())

  init {
    loadFromDiskSync()
  }

  override fun observeCourses(userId: String): Flow<List<Course>> = coursesFlow.asStateFlow()

  override fun observeStudyItems(userId: String): Flow<List<StudyItem>> = itemsFlow.asStateFlow()

  override fun observePreferences(userId: String): Flow<UserPreferences?> =
    preferencesFlow.asStateFlow()

  override fun observeGrades(userId: String): Flow<List<SubjectGrade>> = gradesFlow.asStateFlow()

  override suspend fun createCourse(userId: String, course: Course): Result<String> =
    mutex.withLock {
      val id = "local_course_${UUID.randomUUID().toString().take(12)}"
      val saved = course.copy(id = id, userId = LOCAL_USER_ID)
      coursesFlow.value = (coursesFlow.value + saved).sortedBy { it.code.lowercase() }
      persistToDisk()
      Result.success(id)
    }

  override suspend fun updateCourse(userId: String, course: Course): Result<Unit> =
    mutex.withLock {
      coursesFlow.value =
        coursesFlow.value
          .map { if (it.id == course.id) course.copy(userId = LOCAL_USER_ID) else it }
          .sortedBy { it.code.lowercase() }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun deleteCourse(userId: String, courseId: String): Result<Unit> =
    mutex.withLock {
      coursesFlow.value = coursesFlow.value.filterNot { it.id == courseId }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun createStudyItem(userId: String, item: StudyItem): Result<String> =
    mutex.withLock {
      val id = "local_item_${UUID.randomUUID().toString().take(12)}"
      val saved = item.copy(id = id, userId = LOCAL_USER_ID)
      itemsFlow.value = (itemsFlow.value + saved).sortedBy { it.dueAtMillis }
      persistToDisk()
      Result.success(id)
    }

  override suspend fun getStudyItem(userId: String, itemId: String): Result<StudyItem?> =
    Result.success(itemsFlow.value.firstOrNull { it.id == itemId })

  override suspend fun updateStudyItem(userId: String, item: StudyItem): Result<Unit> =
    mutex.withLock {
      itemsFlow.value =
        itemsFlow.value
          .map { if (it.id == item.id) item.copy(userId = LOCAL_USER_ID) else it }
          .sortedBy { it.dueAtMillis }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun toggleItemStatus(
    userId: String,
    itemId: String,
    completed: Boolean,
  ): Result<Unit> =
    mutex.withLock {
      val newStatus = if (completed) ItemStatus.COMPLETED.wireValue else ItemStatus.PENDING.wireValue
      itemsFlow.value =
        itemsFlow.value.map { if (it.id == itemId) it.copy(status = newStatus) else it }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun updateCalendarSyncInfo(
    userId: String,
    itemId: String,
    googleCalendarEventId: String,
    syncedAtMillis: Long,
  ): Result<Unit> =
    mutex.withLock {
      itemsFlow.value =
        itemsFlow.value.map {
          if (it.id == itemId) {
            it.copy(
              googleCalendarEventId = googleCalendarEventId,
              calendarSyncedAtMillis = syncedAtMillis,
            )
          } else {
            it
          }
        }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun deleteStudyItem(userId: String, itemId: String): Result<Unit> =
    mutex.withLock {
      itemsFlow.value = itemsFlow.value.filterNot { it.id == itemId }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun savePreferences(
    userId: String,
    preferences: UserPreferences,
  ): Result<Unit> =
    mutex.withLock {
      preferencesFlow.value = preferences.copy(userId = LOCAL_USER_ID)
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun createGrade(userId: String, grade: SubjectGrade): Result<String> =
    mutex.withLock {
      val id = "local_grade_${UUID.randomUUID().toString().take(12)}"
      val saved = grade.copy(id = id, userId = LOCAL_USER_ID)
      gradesFlow.value = (gradesFlow.value + saved).sortedByDescending { it.gradedAtMillis }
      persistToDisk()
      Result.success(id)
    }

  override suspend fun updateGrade(userId: String, grade: SubjectGrade): Result<Unit> =
    mutex.withLock {
      gradesFlow.value =
        gradesFlow.value
          .map { if (it.id == grade.id) grade.copy(userId = LOCAL_USER_ID) else it }
          .sortedByDescending { it.gradedAtMillis }
      persistToDisk()
      Result.success(Unit)
    }

  override suspend fun deleteGrade(userId: String, gradeId: String): Result<Unit> =
    mutex.withLock {
      gradesFlow.value = gradesFlow.value.filterNot { it.id == gradeId }
      persistToDisk()
      Result.success(Unit)
    }

  fun currentItemsSnapshot(): List<StudyItem> = itemsFlow.value

  suspend fun migrateGuestDataToCloud(cloudRepository: StudyDataSource, uid: String): Int =
    mutex.withLock {
      val localCourses = coursesFlow.value
      val localItems = itemsFlow.value
      val localPrefs = preferencesFlow.value
      val localGrades = gradesFlow.value

      if (
        localCourses.isEmpty() &&
          localItems.isEmpty() &&
          localGrades.isEmpty() &&
          localPrefs == null
      ) {
        return 0
      }

      var migratedCount = 0
      for (course in localCourses) {
        if (cloudRepository.createCourse(uid, course).isSuccess) {
          migratedCount++
        }
      }
      for (item in localItems) {
        if (cloudRepository.createStudyItem(uid, item).isSuccess) {
          migratedCount++
        }
      }
      for (grade in localGrades) {
        if (cloudRepository.createGrade(uid, grade).isSuccess) {
          migratedCount++
        }
      }
      if (localPrefs != null) {
        cloudRepository.savePreferences(uid, localPrefs.copy(userId = uid))
      }

      coursesFlow.value = emptyList()
      itemsFlow.value = emptyList()
      gradesFlow.value = emptyList()
      preferencesFlow.value = null
      persistToDisk()
      migratedCount
    }

  private fun loadFromDiskSync() {
    if (!storageFile.exists()) return
    runCatching {
      val root = JSONObject(storageFile.readText())
      val coursesArr = root.optJSONArray("courses") ?: JSONArray()
      val loadedCourses =
        (0 until coursesArr.length()).map { i ->
          val o = coursesArr.getJSONObject(i)
          Course(
            id = o.optString("id"),
            userId = LOCAL_USER_ID,
            code = o.optString("code"),
            name = o.optString("name"),
            instructor = o.optString("instructor"),
            colorHex = o.optString("colorHex", "#1E3A5F"),
          )
        }
      coursesFlow.value = loadedCourses.sortedBy { it.code.lowercase() }

      val itemsArr = root.optJSONArray("study_items") ?: JSONArray()
      val loadedItems =
        (0 until itemsArr.length()).map { i ->
          val o = itemsArr.getJSONObject(i)
          StudyItem(
            id = o.optString("id"),
            userId = LOCAL_USER_ID,
            title = o.optString("title"),
            description = o.optString("description"),
            itemType = o.optString("itemType", StudyItemType.HOMEWORK.wireValue),
            courseCode = o.optString("courseCode"),
            courseName = o.optString("courseName"),
            courseColorHex = o.optString("courseColorHex", "#1E3A5F"),
            location = o.optString("location"),
            dueAtMillis = o.optLong("dueAtMillis", System.currentTimeMillis()),
            durationMinutes = o.optLong("durationMinutes", 60L),
            priority = o.optString("priority", PriorityLevel.MEDIUM.wireValue),
            status = o.optString("status", ItemStatus.PENDING.wireValue),
            reminderMinutesBefore = o.optLong("reminderMinutesBefore", 60L),
            notifyEnabled = o.optBoolean("notifyEnabled", true),
            googleCalendarEventId = o.optString("googleCalendarEventId"),
            calendarSyncedAtMillis = o.optLong("calendarSyncedAtMillis", 0L),
          )
        }
      itemsFlow.value = loadedItems.sortedBy { it.dueAtMillis }

      val prefObj = root.optJSONObject("preferences")
      if (prefObj != null) {
        preferencesFlow.value =
          UserPreferences(
            userId = LOCAL_USER_ID,
            pushNotificationsEnabled = prefObj.optBoolean("pushNotificationsEnabled", true),
            defaultHomeworkReminderMinutes = prefObj.optLong("defaultHomeworkReminderMinutes", 60L),
            defaultExamReminderMinutes = prefObj.optLong("defaultExamReminderMinutes", 1440L),
            dailyDigestEnabled = prefObj.optBoolean("dailyDigestEnabled", true),
            dailyDigestHour = prefObj.optLong("dailyDigestHour", 8L),
            googleCalendarAutoSync = prefObj.optBoolean("googleCalendarAutoSync", false),
            lastCalendarSyncMillis = prefObj.optLong("lastCalendarSyncMillis", 0L),
            currentStreakDays = prefObj.optLong("currentStreakDays", 0L),
            longestStreakDays = prefObj.optLong("longestStreakDays", 0L),
            lastActiveDayKey = prefObj.optString("lastActiveDayKey", ""),
            recentStreakDaysCsv = prefObj.optString("recentStreakDaysCsv", ""),
            themeMode = prefObj.optString("themeMode", "suggested"),
            themePalette = prefObj.optString("themePalette", "indigo"),
            manualAccentHex = prefObj.optString("manualAccentHex", "#1E3A5F"),
            studentName = prefObj.optString("studentName", ""),
            studentSchool = prefObj.optString("studentSchool", ""),
            studentMajor = prefObj.optString("studentMajor", ""),
            studentYear = prefObj.optString("studentYear", ""),
            studentIdLabel = prefObj.optString("studentIdLabel", ""),
            studyGoal = prefObj.optString("studyGoal", ""),
            studentBio = prefObj.optString("studentBio", ""),
          )
      }

      val gradesArr = root.optJSONArray("grades") ?: JSONArray()
      val loadedGrades =
        (0 until gradesArr.length()).map { i ->
          val o = gradesArr.getJSONObject(i)
          SubjectGrade(
            id = o.optString("id"),
            userId = LOCAL_USER_ID,
            subjectCode = o.optString("subjectCode"),
            subjectName = o.optString("subjectName"),
            assessmentTitle = o.optString("assessmentTitle"),
            numericScore = o.optDouble("numericScore", 0.0),
            maxScore = o.optDouble("maxScore", 100.0),
            letterGrade = o.optString("letterGrade", "A"),
            courseColorHex = o.optString("courseColorHex", "#1E3A5F"),
            notes = o.optString("notes"),
            gradedAtMillis = o.optLong("gradedAtMillis", System.currentTimeMillis()),
          )
        }
      gradesFlow.value = loadedGrades.sortedByDescending { it.gradedAtMillis }
    }
  }

  private suspend fun persistToDisk() =
    withContext(Dispatchers.IO) {
      val coursesArr =
        JSONArray().apply {
          coursesFlow.value.forEach { c ->
            put(
              JSONObject().apply {
                put("id", c.id)
                put("code", c.code)
                put("name", c.name)
                put("instructor", c.instructor)
                put("colorHex", c.colorHex)
              }
            )
          }
        }

      val itemsArr =
        JSONArray().apply {
          itemsFlow.value.forEach { item ->
            put(
              JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("description", item.description)
                put("itemType", item.itemType)
                put("courseCode", item.courseCode)
                put("courseName", item.courseName)
                put("courseColorHex", item.courseColorHex)
                put("location", item.location)
                put("dueAtMillis", item.dueAtMillis)
                put("durationMinutes", item.durationMinutes)
                put("priority", item.priority)
                put("status", item.status)
                put("reminderMinutesBefore", item.reminderMinutesBefore)
                put("notifyEnabled", item.notifyEnabled)
                put("googleCalendarEventId", item.googleCalendarEventId)
                put("calendarSyncedAtMillis", item.calendarSyncedAtMillis)
              }
            )
          }
        }

      val gradesArr =
        JSONArray().apply {
          gradesFlow.value.forEach { g ->
            put(
              JSONObject().apply {
                put("id", g.id)
                put("subjectCode", g.subjectCode)
                put("subjectName", g.subjectName)
                put("assessmentTitle", g.assessmentTitle)
                put("numericScore", g.numericScore)
                put("maxScore", g.maxScore)
                put("letterGrade", g.letterGrade)
                put("courseColorHex", g.courseColorHex)
                put("notes", g.notes)
                put("gradedAtMillis", g.gradedAtMillis)
              }
            )
          }
        }

      val root =
        JSONObject().apply {
          put("courses", coursesArr)
          put("study_items", itemsArr)
          put("grades", gradesArr)
          preferencesFlow.value?.let { p ->
            put(
              "preferences",
              JSONObject().apply {
                put("pushNotificationsEnabled", p.pushNotificationsEnabled)
                put("defaultHomeworkReminderMinutes", p.defaultHomeworkReminderMinutes)
                put("defaultExamReminderMinutes", p.defaultExamReminderMinutes)
                put("dailyDigestEnabled", p.dailyDigestEnabled)
                put("dailyDigestHour", p.dailyDigestHour)
                put("googleCalendarAutoSync", p.googleCalendarAutoSync)
                put("lastCalendarSyncMillis", p.lastCalendarSyncMillis)
                put("currentStreakDays", p.currentStreakDays)
                put("longestStreakDays", p.longestStreakDays)
                put("lastActiveDayKey", p.lastActiveDayKey)
                put("recentStreakDaysCsv", p.recentStreakDaysCsv)
                put("themeMode", p.themeMode)
                put("themePalette", p.themePalette)
                put("manualAccentHex", p.manualAccentHex)
                put("studentName", p.studentName)
                put("studentSchool", p.studentSchool)
                put("studentMajor", p.studentMajor)
                put("studentYear", p.studentYear)
                put("studentIdLabel", p.studentIdLabel)
                put("studyGoal", p.studyGoal)
                put("studentBio", p.studentBio)
              },
            )
          }
        }

      val temp = File(appContext.filesDir, "$FILE_NAME.tmp")
      temp.writeText(root.toString())
      temp.renameTo(storageFile)
    }

  companion object {
    const val LOCAL_USER_ID = "local_guest"
    private const val FILE_NAME = "studysync_local_data.json"
  }
}
