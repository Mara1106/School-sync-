package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class StreakWeekDay(
  val dayLabel: String,
  val dateKey: String,
  val isActive: Boolean,
  val isToday: Boolean,
)

data class StudyStreakSummary(
  val currentStreak: Long,
  val longestStreak: Long,
  val checkedInToday: Boolean,
  val atRiskToday: Boolean,
  val milestoneTitle: String,
  val nextMilestoneDays: Long,
  val statusMessage: String,
  val weekDays: List<StreakWeekDay>,
)

object StudyStreakCalculator {

  fun dateKeyForMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

  fun previousDateKey(millis: Long): String {
    val cal = Calendar.getInstance().apply {
      timeInMillis = millis
      add(Calendar.DAY_OF_YEAR, -1)
    }
    return dateKeyForMillis(cal.timeInMillis)
  }

  fun parseRecentKeys(csv: String): List<String> =
    csv
      .split(",")
      .map { it.trim() }
      .filter { it.length == 10 && it[4] == '-' && it[7] == '-' }
      .distinct()
      .sorted()

  fun recordStudyActivity(
    prefs: UserPreferences,
    nowMillis: Long = System.currentTimeMillis(),
  ): UserPreferences {
    val todayKey = dateKeyForMillis(nowMillis)
    val yesterdayKey = previousDateKey(nowMillis)
    val existingKeys = parseRecentKeys(prefs.recentStreakDaysCsv)

    val updatedStreak =
      when (prefs.lastActiveDayKey) {
        todayKey -> prefs.currentStreakDays.coerceAtLeast(1L)
        yesterdayKey -> prefs.currentStreakDays + 1L
        else -> 1L
      }
    val updatedLongest = max(prefs.longestStreakDays, updatedStreak)
    val updatedKeys = (existingKeys + todayKey).distinct().sorted().takeLast(14)

    return prefs.copy(
      currentStreakDays = updatedStreak,
      longestStreakDays = updatedLongest,
      lastActiveDayKey = todayKey,
      recentStreakDaysCsv = updatedKeys.joinToString(","),
    )
  }

  fun effectiveStreakSummary(
    prefs: UserPreferences,
    nowMillis: Long = System.currentTimeMillis(),
  ): StudyStreakSummary {
    val todayKey = dateKeyForMillis(nowMillis)
    val yesterdayKey = previousDateKey(nowMillis)
    val activeKeys = parseRecentKeys(prefs.recentStreakDaysCsv).toSet()

    val checkedInToday = prefs.lastActiveDayKey == todayKey
    val atRiskToday = !checkedInToday && prefs.lastActiveDayKey == yesterdayKey && prefs.currentStreakDays > 0L

    val effectiveCount =
      when {
        checkedInToday -> prefs.currentStreakDays.coerceAtLeast(1L)
        atRiskToday -> prefs.currentStreakDays
        else -> 0L
      }

    val longest = max(prefs.longestStreakDays, effectiveCount)

    val (milestoneTitle, nextGoal) =
      when {
        effectiveCount >= 30L -> "30-Day Legend" to 60L
        effectiveCount >= 14L -> "14-Day Scholar" to 30L
        effectiveCount >= 7L -> "7-Day Blaze" to 14L
        effectiveCount >= 3L -> "3-Day Spark" to 7L
        effectiveCount >= 1L -> "Streak Started" to 3L
        else -> "Start Your Streak" to 3L
      }

    val statusMessage =
      when {
        checkedInToday && effectiveCount >= 7L ->
          "Unstoppable! ${effectiveCount}-day blaze locked in today."
        checkedInToday ->
          "Streak secured for today! Keep the momentum going."
        atRiskToday ->
          "Finish 1 task or check in today to save your ${effectiveCount}-day streak!"
        else ->
          "Complete a task or log a study session to light day 1."
      }

    // Build Monday..Sunday week activity dots
    val weekStartCal =
      Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        val dow = get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday =
          when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
          }
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
      }

    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val weekDays =
      (0..6).map { offset ->
        val dayCal = (weekStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        val key = dateKeyForMillis(dayCal.timeInMillis)
        StreakWeekDay(
          dayLabel = dayFmt.format(dayCal.time).take(1).uppercase(),
          dateKey = key,
          isActive = activeKeys.contains(key),
          isToday = key == todayKey,
        )
      }

    return StudyStreakSummary(
      currentStreak = effectiveCount,
      longestStreak = longest,
      checkedInToday = checkedInToday,
      atRiskToday = atRiskToday,
      milestoneTitle = milestoneTitle,
      nextMilestoneDays = nextGoal,
      statusMessage = statusMessage,
      weekDays = weekDays,
    )
  }
}
