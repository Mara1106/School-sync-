package com.example

import com.example.calendar.CalendarEventDateTime
import com.example.calendar.GoogleCalendarSyncManager
import com.example.data.model.GradeScale
import com.example.data.model.ItemStatus
import com.example.data.model.StudyItem
import com.example.data.model.StudyStreakCalculator
import com.example.data.model.ThemeModeOption
import com.example.data.model.ThemePaletteOption
import com.example.data.model.UserPreferences
import com.example.ui.components.computeCurrentWeekStats
import com.example.ui.components.formatRelativeCountdown
import com.example.ui.components.formatReminderLead
import com.example.ui.theme.ThemePreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StudySchedulerAndCalendarTest {

  @Test
  fun formatReminderLead_formatsMinutesHoursAndDays() {
    assertEquals("Off", formatReminderLead(0L))
    assertEquals("30m before", formatReminderLead(30L))
    assertEquals("1h before", formatReminderLead(60L))
    assertEquals("1d before", formatReminderLead(1440L))
    assertEquals("2d before", formatReminderLead(2880L))
  }

  @Test
  fun formatRelativeCountdown_handlesCompletedOverdueAndUpcoming() {
    val now = System.currentTimeMillis()
    val (completedLabel, completedUrgent) = formatRelativeCountdown(now - 60_000L, isCompleted = true)
    assertEquals("Completed", completedLabel)
    assertFalse(completedUrgent)

    val (overdueLabel, overdueUrgent) = formatRelativeCountdown(now - 2L * 3_600_000L, isCompleted = false)
    assertTrue(overdueLabel.startsWith("Overdue"))
    assertTrue(overdueUrgent)

    val (upcomingLabel, upcomingUrgent) = formatRelativeCountdown(now + 3L * 86_400_000L + 3_600_000L, isCompleted = false)
    assertTrue(upcomingLabel.startsWith("In "))
    assertFalse(upcomingUrgent)
  }

  @Test
  fun googleCalendarIso8601_roundTripsEpochMillis() {
    val sampleMillis = 1_795_000_000_000L
    val iso = GoogleCalendarSyncManager.formatIso8601(sampleMillis)
    val parsed = GoogleCalendarSyncManager.parseIso8601ToMillis(CalendarEventDateTime(dateTime = iso))
    assertNotNull(parsed)
    assertEquals(sampleMillis / 1000L, parsed!! / 1000L)
  }

  @Test
  fun computeCurrentWeekStats_countsCompletedVsRemainingAndIgnoresOtherWeeks() {
    val refCal =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 18, 12, 0, 0) // Wednesday Nov 18, 2026
        set(Calendar.MILLISECOND, 0)
      }
    val wedNoon = refCal.timeInMillis
    val monNoon =
      (refCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
    val nextWeekMillis =
      (refCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 10) }.timeInMillis

    val items =
      listOf(
        StudyItem(id = "1", title = "Done Mon", dueAtMillis = monNoon, status = ItemStatus.COMPLETED.wireValue),
        StudyItem(id = "2", title = "Due Wed 1", dueAtMillis = wedNoon, status = ItemStatus.PENDING.wireValue),
        StudyItem(id = "3", title = "Due Wed 2", dueAtMillis = wedNoon + 1000L, status = ItemStatus.PENDING.wireValue),
        StudyItem(id = "4", title = "Done Wed", dueAtMillis = wedNoon + 2000L, status = ItemStatus.COMPLETED.wireValue),
        StudyItem(id = "5", title = "Next week", dueAtMillis = nextWeekMillis, status = ItemStatus.PENDING.wireValue),
      )

    val summary = computeCurrentWeekStats(items, referenceMillis = wedNoon)
    assertEquals(7, summary.days.size)
    assertEquals(2, summary.totalCompleted)
    assertEquals(2, summary.totalRemaining)
    assertEquals(50, summary.completionPercent)
    assertEquals(1, summary.days[0].completedCount) // Monday
    assertEquals(0, summary.days[0].remainingCount)
    assertEquals(1, summary.days[2].completedCount) // Wednesday
    assertEquals(2, summary.days[2].remainingCount)
    assertTrue(summary.days[2].isToday)
  }

  @Test
  fun gradeScale_mapsPercentagesAndLettersAccurately() {
    assertEquals("A+", GradeScale.letterFromPercentage(98.0))
    assertEquals("A", GradeScale.letterFromPercentage(94.0))
    assertEquals("B+", GradeScale.letterFromPercentage(88.0))
    assertEquals("C", GradeScale.letterFromPercentage(75.0))
    assertEquals("F", GradeScale.letterFromPercentage(52.0))
    assertEquals(4.0, GradeScale.gpaPointsFromLetter("A")!!, 0.01)
    assertEquals(3.0, GradeScale.gpaPointsFromLetter("B")!!, 0.01)
  }

  @Test
  fun studyStreakCalculator_incrementsConsecutivelyHandlesSameDayAndResetsOnGap() {
    val day1 =
      Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 16, 12, 0, 0) // Monday
        set(Calendar.MILLISECOND, 0)
      }
    val day2 = (day1.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val day3 = (day1.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }
    val day5 = (day1.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 4) }

    val afterDay1 = StudyStreakCalculator.recordStudyActivity(UserPreferences(), day1.timeInMillis)
    assertEquals(1L, afterDay1.currentStreakDays)
    assertEquals(1L, afterDay1.longestStreakDays)

    // Same-day repeat is idempotent
    val sameDayRepeat = StudyStreakCalculator.recordStudyActivity(afterDay1, day1.timeInMillis + 3_600_000L)
    assertEquals(1L, sameDayRepeat.currentStreakDays)

    // Consecutive days increment streak
    val afterDay2 = StudyStreakCalculator.recordStudyActivity(sameDayRepeat, day2.timeInMillis)
    assertEquals(2L, afterDay2.currentStreakDays)
    val afterDay3 = StudyStreakCalculator.recordStudyActivity(afterDay2, day3.timeInMillis)
    assertEquals(3L, afterDay3.currentStreakDays)
    assertEquals(3L, afterDay3.longestStreakDays)

    // Checking summary on day 3 shows checkedInToday + 3-Day Spark milestone + 7 weekDays
    val summaryOnDay3 = StudyStreakCalculator.effectiveStreakSummary(afterDay3, day3.timeInMillis)
    assertTrue(summaryOnDay3.checkedInToday)
    assertFalse(summaryOnDay3.atRiskToday)
    assertEquals("3-Day Spark", summaryOnDay3.milestoneTitle)
    assertEquals(7, summaryOnDay3.weekDays.size)
    assertTrue(summaryOnDay3.weekDays.count { it.isActive } == 3)

    // Checking summary on day 4 without check-in marks streak atRiskToday
    val day4 = (day1.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 3) }
    val summaryAtRisk = StudyStreakCalculator.effectiveStreakSummary(afterDay3, day4.timeInMillis)
    assertFalse(summaryAtRisk.checkedInToday)
    assertTrue(summaryAtRisk.atRiskToday)
    assertEquals(3L, summaryAtRisk.currentStreak)

    // Missing day 4 and checking in on day 5 resets currentStreak to 1 but keeps longestStreak = 3
    val afterGap = StudyStreakCalculator.recordStudyActivity(afterDay3, day5.timeInMillis)
    assertEquals(1L, afterGap.currentStreakDays)
    assertEquals(3L, afterGap.longestStreakDays)
  }

  @Test
  fun themePreferencesStore_smartSuggestionAdaptsToTimeOfDay() {
    val daytime = ThemePreferencesStore.smartSuggestionForHour(10)
    assertEquals(ThemeModeOption.LIGHT, daytime.recommendedMode)
    assertEquals(ThemePaletteOption.INDIGO, daytime.recommendedPalette)

    val evening = ThemePreferencesStore.smartSuggestionForHour(21)
    assertEquals(ThemeModeOption.DARK, evening.recommendedMode)
    assertEquals(ThemePaletteOption.EMERALD, evening.recommendedPalette)
  }
}
