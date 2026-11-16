package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StudyItemType(val wireValue: String, val label: String) {
  HOMEWORK("homework", "Homework"),
  EXAM("exam", "Exam");

  companion object {
    fun fromWire(value: String): StudyItemType =
      entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: HOMEWORK
  }
}

enum class PriorityLevel(val wireValue: String, val label: String) {
  LOW("low", "Low"),
  MEDIUM("medium", "Medium"),
  HIGH("high", "High");

  companion object {
    fun fromWire(value: String): PriorityLevel =
      entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: MEDIUM
  }
}

enum class ItemStatus(val wireValue: String) {
  PENDING("pending"),
  COMPLETED("completed");

  companion object {
    fun fromWire(value: String): ItemStatus =
      entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: PENDING
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class Course(
  @DocumentId val id: String = "",
  val userId: String = "",
  val code: String = "",
  val name: String = "",
  val instructor: String = "",
  val colorHex: String = "#1E3A5F",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
    "userId" to ownerUid,
    "code" to code.trim().take(24),
    "name" to name.trim().take(80),
    "instructor" to instructor.trim().take(80),
    "colorHex" to colorHex.ifBlank { "#1E3A5F" },
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "code" to code.trim().take(24),
    "name" to name.trim().take(80),
    "instructor" to instructor.trim().take(80),
    "colorHex" to colorHex.ifBlank { "#1E3A5F" },
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@IgnoreExtraProperties
data class StudyItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val description: String = "",
  val itemType: String = StudyItemType.HOMEWORK.wireValue,
  val courseCode: String = "",
  val courseName: String = "",
  val courseColorHex: String = "#1E3A5F",
  val location: String = "",
  val dueAtMillis: Long = 0L,
  val durationMinutes: Long = 60L,
  val priority: String = PriorityLevel.MEDIUM.wireValue,
  val status: String = ItemStatus.PENDING.wireValue,
  val reminderMinutesBefore: Long = 60L,
  val notifyEnabled: Boolean = true,
  val googleCalendarEventId: String = "",
  val calendarSyncedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val typeEnum: StudyItemType get() = StudyItemType.fromWire(itemType)
  val priorityEnum: PriorityLevel get() = PriorityLevel.fromWire(priority)
  val isCompleted: Boolean get() = status == ItemStatus.COMPLETED.wireValue
  val isSyncedWithCalendar: Boolean get() = googleCalendarEventId.isNotBlank()

  fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
    "userId" to ownerUid,
    "title" to title.trim().take(120),
    "description" to description.trim().take(500),
    "itemType" to StudyItemType.fromWire(itemType).wireValue,
    "courseCode" to courseCode.trim().take(24),
    "courseName" to courseName.trim().take(80),
    "courseColorHex" to courseColorHex.ifBlank { "#1E3A5F" },
    "location" to location.trim().take(100),
    "dueAtMillis" to dueAtMillis.coerceAtLeast(1L),
    "durationMinutes" to durationMinutes.coerceIn(5L, 720L),
    "priority" to PriorityLevel.fromWire(priority).wireValue,
    "status" to ItemStatus.fromWire(status).wireValue,
    "reminderMinutesBefore" to reminderMinutesBefore.coerceIn(0L, 10080L),
    "notifyEnabled" to notifyEnabled,
    "googleCalendarEventId" to googleCalendarEventId.take(256),
    "calendarSyncedAtMillis" to calendarSyncedAtMillis.coerceAtLeast(0L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "title" to title.trim().take(120),
    "description" to description.trim().take(500),
    "itemType" to StudyItemType.fromWire(itemType).wireValue,
    "courseCode" to courseCode.trim().take(24),
    "courseName" to courseName.trim().take(80),
    "courseColorHex" to courseColorHex.ifBlank { "#1E3A5F" },
    "location" to location.trim().take(100),
    "dueAtMillis" to dueAtMillis.coerceAtLeast(1L),
    "durationMinutes" to durationMinutes.coerceIn(5L, 720L),
    "priority" to PriorityLevel.fromWire(priority).wireValue,
    "status" to ItemStatus.fromWire(status).wireValue,
    "reminderMinutesBefore" to reminderMinutesBefore.coerceIn(0L, 10080L),
    "notifyEnabled" to notifyEnabled,
    "googleCalendarEventId" to googleCalendarEventId.take(256),
    "calendarSyncedAtMillis" to calendarSyncedAtMillis.coerceAtLeast(0L),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@IgnoreExtraProperties
data class UserPreferences(
  @DocumentId val id: String = "preferences",
  val userId: String = "",
  val pushNotificationsEnabled: Boolean = true,
  val defaultHomeworkReminderMinutes: Long = 60L,
  val defaultExamReminderMinutes: Long = 1440L,
  val dailyDigestEnabled: Boolean = true,
  val dailyDigestHour: Long = 8L,
  val googleCalendarAutoSync: Boolean = false,
  val lastCalendarSyncMillis: Long = 0L,
  val currentStreakDays: Long = 0L,
  val longestStreakDays: Long = 0L,
  val lastActiveDayKey: String = "",
  val recentStreakDaysCsv: String = "",
  val themeMode: String = ThemeModeOption.SUGGESTED.wireValue,
  val themePalette: String = ThemePaletteOption.INDIGO.wireValue,
  val manualAccentHex: String = "#1E3A5F",
  val studentName: String = "",
  val studentSchool: String = "",
  val studentMajor: String = "",
  val studentYear: String = "",
  val studentIdLabel: String = "",
  val studyGoal: String = "",
  val studentBio: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
    "userId" to ownerUid,
    "pushNotificationsEnabled" to pushNotificationsEnabled,
    "defaultHomeworkReminderMinutes" to defaultHomeworkReminderMinutes.coerceIn(0L, 10080L),
    "defaultExamReminderMinutes" to defaultExamReminderMinutes.coerceIn(0L, 10080L),
    "dailyDigestEnabled" to dailyDigestEnabled,
    "dailyDigestHour" to dailyDigestHour.coerceIn(0L, 23L),
    "googleCalendarAutoSync" to googleCalendarAutoSync,
    "lastCalendarSyncMillis" to lastCalendarSyncMillis.coerceAtLeast(0L),
    "currentStreakDays" to currentStreakDays.coerceIn(0L, 10000L),
    "longestStreakDays" to longestStreakDays.coerceIn(0L, 10000L),
    "lastActiveDayKey" to lastActiveDayKey.take(16),
    "recentStreakDaysCsv" to recentStreakDaysCsv.take(180),
    "themeMode" to ThemeModeOption.fromWire(themeMode).wireValue,
    "themePalette" to ThemePaletteOption.fromWire(themePalette).wireValue,
    "manualAccentHex" to manualAccentHex.take(9).ifBlank { "#1E3A5F" },
    "studentName" to studentName.trim().take(80),
    "studentSchool" to studentSchool.trim().take(100),
    "studentMajor" to studentMajor.trim().take(80),
    "studentYear" to studentYear.trim().take(40),
    "studentIdLabel" to studentIdLabel.trim().take(40),
    "studyGoal" to studyGoal.trim().take(160),
    "studentBio" to studentBio.trim().take(400),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "pushNotificationsEnabled" to pushNotificationsEnabled,
    "defaultHomeworkReminderMinutes" to defaultHomeworkReminderMinutes.coerceIn(0L, 10080L),
    "defaultExamReminderMinutes" to defaultExamReminderMinutes.coerceIn(0L, 10080L),
    "dailyDigestEnabled" to dailyDigestEnabled,
    "dailyDigestHour" to dailyDigestHour.coerceIn(0L, 23L),
    "googleCalendarAutoSync" to googleCalendarAutoSync,
    "lastCalendarSyncMillis" to lastCalendarSyncMillis.coerceAtLeast(0L),
    "currentStreakDays" to currentStreakDays.coerceIn(0L, 10000L),
    "longestStreakDays" to longestStreakDays.coerceIn(0L, 10000L),
    "lastActiveDayKey" to lastActiveDayKey.take(16),
    "recentStreakDaysCsv" to recentStreakDaysCsv.take(180),
    "themeMode" to ThemeModeOption.fromWire(themeMode).wireValue,
    "themePalette" to ThemePaletteOption.fromWire(themePalette).wireValue,
    "manualAccentHex" to manualAccentHex.take(9).ifBlank { "#1E3A5F" },
    "studentName" to studentName.trim().take(80),
    "studentSchool" to studentSchool.trim().take(100),
    "studentMajor" to studentMajor.trim().take(80),
    "studentYear" to studentYear.trim().take(40),
    "studentIdLabel" to studentIdLabel.trim().take(40),
    "studyGoal" to studyGoal.trim().take(160),
    "studentBio" to studentBio.trim().take(400),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

enum class ThemeModeOption(val wireValue: String, val label: String) {
  SUGGESTED("suggested", "Suggested (Auto)"),
  LIGHT("light", "Manual Light"),
  DARK("dark", "Manual Dark");

  companion object {
    fun fromWire(value: String): ThemeModeOption =
      entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: SUGGESTED
  }
}

enum class ThemePaletteOption(
  val wireValue: String,
  val label: String,
  val description: String,
  val primaryHex: String,
  val secondaryHex: String,
  val isSuggested: Boolean,
) {
  INDIGO("indigo", "Academic Indigo", "Suggested • Crisp ink & warm paper", "#1E3A5F", "#D97706", true),
  EMERALD("emerald", "Forest Focus", "Suggested • Calm eye-relief teal", "#0F766E", "#059669", true),
  SUNSET("sunset", "Sunset Amber", "Suggested • Warm energizing gold", "#B45309", "#DC2626", true),
  VIOLET("violet", "Scholar Plum", "Suggested • Deep study plum", "#6D28D9", "#0D9488", true),
  MANUAL("manual", "Manual Custom", "Your custom chosen accent color", "#1E3A5F", "#D97706", false);

  companion object {
    fun fromWire(value: String): ThemePaletteOption =
      entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: INDIGO
  }
}

@IgnoreExtraProperties
data class SubjectGrade(
  @DocumentId val id: String = "",
  val userId: String = "",
  val subjectCode: String = "",
  val subjectName: String = "",
  val assessmentTitle: String = "",
  val numericScore: Double = 0.0,
  val maxScore: Double = 100.0,
  val letterGrade: String = "A",
  val courseColorHex: String = "#1E3A5F",
  val notes: String = "",
  val gradedAtMillis: Long = System.currentTimeMillis(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val percentage: Double
    get() = if (maxScore > 0.0) ((numericScore / maxScore) * 100.0).coerceIn(0.0, 200.0) else 0.0

  fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
    "userId" to ownerUid,
    "subjectCode" to subjectCode.trim().take(24),
    "subjectName" to subjectName.trim().take(80),
    "assessmentTitle" to assessmentTitle.trim().take(100),
    "numericScore" to numericScore.coerceIn(0.0, 10000.0),
    "maxScore" to maxScore.coerceIn(1.0, 10000.0),
    "letterGrade" to letterGrade.trim().ifBlank { GradeScale.letterFromPercentage(percentage) }.take(12),
    "courseColorHex" to courseColorHex.ifBlank { "#1E3A5F" },
    "notes" to notes.trim().take(300),
    "gradedAtMillis" to gradedAtMillis.coerceAtLeast(1L),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "subjectCode" to subjectCode.trim().take(24),
    "subjectName" to subjectName.trim().take(80),
    "assessmentTitle" to assessmentTitle.trim().take(100),
    "numericScore" to numericScore.coerceIn(0.0, 10000.0),
    "maxScore" to maxScore.coerceIn(1.0, 10000.0),
    "letterGrade" to letterGrade.trim().ifBlank { GradeScale.letterFromPercentage(percentage) }.take(12),
    "courseColorHex" to courseColorHex.ifBlank { "#1E3A5F" },
    "notes" to notes.trim().take(300),
    "gradedAtMillis" to gradedAtMillis.coerceAtLeast(1L),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

object GradeScale {
  val standardLetters = listOf("A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "F")

  fun letterFromPercentage(percent: Double): String =
    when {
      percent >= 97.0 -> "A+"
      percent >= 93.0 -> "A"
      percent >= 90.0 -> "A-"
      percent >= 87.0 -> "B+"
      percent >= 83.0 -> "B"
      percent >= 80.0 -> "B-"
      percent >= 77.0 -> "C+"
      percent >= 73.0 -> "C"
      percent >= 70.0 -> "C-"
      percent >= 67.0 -> "D+"
      percent >= 60.0 -> "D"
      else -> "F"
    }

  fun gpaPointsFromLetter(letter: String): Double? =
    when (letter.trim().uppercase()) {
      "A+", "A" -> 4.0
      "A-" -> 3.7
      "B+" -> 3.3
      "B" -> 3.0
      "B-" -> 2.7
      "C+" -> 2.3
      "C" -> 2.0
      "C-" -> 1.7
      "D+" -> 1.3
      "D" -> 1.0
      "F" -> 0.0
      else -> null
    }
}
