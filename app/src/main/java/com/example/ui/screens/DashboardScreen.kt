package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Course
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.ui.CalendarSyncUiState
import com.example.ui.ScheduleFilter
import com.example.ui.components.StudyItemCard
import com.example.ui.components.StudyStreakCard
import com.example.ui.components.WeeklyProgressChartCard
import com.example.ui.components.formatRelativeCountdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
  userDisplayName: String?,
  isGuestMode: Boolean,
  allItemsState: UiState<List<StudyItem>>,
  filteredItemsState: UiState<List<StudyItem>>,
  coursesState: UiState<List<Course>>,
  preferencesState: UiState<UserPreferences>,
  selectedFilter: ScheduleFilter,
  selectedCourseCode: String?,
  calendarState: CalendarSyncUiState,
  onSelectFilter: (ScheduleFilter) -> Unit,
  onSelectCourse: (String?) -> Unit,
  onToggleComplete: (StudyItem) -> Unit,
  onEditItem: (StudyItem) -> Unit,
  onOpenNewItem: () -> Unit,
  onCheckInStreak: () -> Unit,
  onSyncCalendarClick: () -> Unit,
  onOpenAlertsClick: () -> Unit,
) {
  val allItems = (allItemsState as? UiState.Success)?.data.orEmpty()
  val courses = (coursesState as? UiState.Success)?.data.orEmpty()
  val prefs = (preferencesState as? UiState.Success)?.data ?: UserPreferences()
  val pendingHomework = remember(allItems) {
    allItems.count { !it.isCompleted && it.typeEnum == StudyItemType.HOMEWORK }
  }
  val upcomingExams = remember(allItems) {
    allItems.filter { !it.isCompleted && it.typeEnum == StudyItemType.EXAM }
  }
  val nextExam = upcomingExams.minByOrNull { it.dueAtMillis }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Hero Academic Snapshot Card
      item {
        AcademicOverviewBanner(
          userDisplayName = prefs.studentName.takeIf { it.isNotBlank() } ?: userDisplayName,
          studentSubtitle =
            listOfNotNull(
              prefs.studentMajor.trim().takeIf { it.isNotBlank() },
              prefs.studentYear.trim().takeIf { it.isNotBlank() },
            ).joinToString(" • ").takeIf { it.isNotBlank() },
          isGuestMode = isGuestMode,
          pendingHomeworkCount = pendingHomework,
          upcomingExamCount = upcomingExams.size,
          nextExam = nextExam,
          calendarState = calendarState,
          onSyncCalendarClick = onSyncCalendarClick,
          onOpenAlertsClick = onOpenAlertsClick,
        )
      }

      // TikTok-Style Daily Study Streak Card
      item {
        StudyStreakCard(
          preferences = prefs,
          onCheckInToday = onCheckInStreak,
        )
      }

      // Weekly Progress Chart (Completed vs Remaining for Current Week)
      item {
        WeeklyProgressChartCard(allItems = allItems)
      }

      // Schedule Filter Chips
      item {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ScheduleFilter.entries.forEach { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { onSelectFilter(filter) },
              label = { Text(filter.label) },
              modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
            )
          }
        }
      }

      // Course Filter Row (if user has courses)
      if (courses.isNotEmpty()) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = selectedCourseCode == null,
              onClick = { onSelectCourse(null) },
              label = { Text("All Courses") },
            )
            courses.forEach { course ->
              FilterChip(
                selected = selectedCourseCode == course.code,
                onClick = { onSelectCourse(course.code) },
                label = { Text(course.code) },
              )
            }
          }
        }
      }

      when (filteredItemsState) {
        is UiState.Loading -> {
          item {
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        is UiState.Error -> {
          item {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = filteredItemsState.message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          val items = filteredItemsState.data
          if (items.isEmpty()) {
            item {
              EmptyScheduleCard(
                filterLabel = selectedFilter.label,
                onAddClick = onOpenNewItem,
              )
            }
          } else {
            items(items, key = { it.id }) { item ->
              StudyItemCard(
                item = item,
                onToggleComplete = { onToggleComplete(item) },
                onClick = { onEditItem(item) },
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(80.dp)) }
    }
  }
}

@Composable
private fun AcademicOverviewBanner(
  userDisplayName: String?,
  studentSubtitle: String?,
  isGuestMode: Boolean,
  pendingHomeworkCount: Int,
  upcomingExamCount: Int,
  nextExam: StudyItem?,
  calendarState: CalendarSyncUiState,
  onSyncCalendarClick: () -> Unit,
  onOpenAlertsClick: () -> Unit,
) {
  val todayLabel = remember {
    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
  }
  val firstName = userDisplayName?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "Student"

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(
          Brush.linearGradient(
            colors =
              listOf(
                Color(0xFF1E3A5F),
                Color(0xFF284D7B),
                Color(0xFF1B4D5C),
              )
          )
        )
        .padding(20.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = todayLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFBFDBFE),
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Hello, $firstName",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
          if (!studentSubtitle.isNullOrBlank()) {
            Text(
              text = studentSubtitle,
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFDBEAFE),
            )
          }
        }

        Surface(
          color = Color.White.copy(alpha = 0.14f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.clickable(onClick = onOpenAlertsClick),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Notification alerts",
              tint = Color(0xFFFBBF24),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Alerts",
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        StatBlock(
          value = pendingHomeworkCount.toString(),
          label = "Homework Due",
          accent = Color(0xFF93C5FD),
          modifier = Modifier.weight(1f),
        )
        StatBlock(
          value = upcomingExamCount.toString(),
          label = "Upcoming Exams",
          accent = Color(0xFFFBBF24),
          modifier = Modifier.weight(1f),
        )
      }

      if (nextExam != null) {
        val (countdown, _) = formatRelativeCountdown(nextExam.dueAtMillis, false)
        Surface(
          color = Color.White.copy(alpha = 0.12f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Next Exam: ${nextExam.title}",
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = countdown,
              style = MaterialTheme.typography.labelMedium,
              color = Color(0xFFFDE68A),
            )
          }
        }
      }

      // Quick Sync Bar
      Surface(
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSyncCalendarClick),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (calendarState.isConnected) Icons.Default.CloudDone else Icons.Default.Sync,
              contentDescription = "Google Calendar sync",
              tint = if (calendarState.isConnected) Color(0xFF5EEAD4) else Color.White,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                when {
                  isGuestMode -> "Local mode • Sign in with Google for Calendar sync"
                  calendarState.isSyncing -> "Syncing with Google Calendar..."
                  calendarState.isConnected -> calendarState.lastSummaryText ?: "Google Calendar synced"
                  else -> "Sync schedule with Google Calendar"
                },
              style = MaterialTheme.typography.bodySmall,
              color = Color.White,
            )
          }
          if (calendarState.isSyncing) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatBlock(
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.12f),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.85f),
      )
    }
  }
}

@Composable
private fun EmptyScheduleCard(
  filterLabel: String,
  onAddClick: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_schedule),
        contentDescription = "Clean notebook empty schedule illustration",
        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )
      Text(
        text = "No $filterLabel items yet",
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = "Add your upcoming homework deadlines or exam dates to enable smart push reminders and Google Calendar sync.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onAddClick,
        modifier = Modifier.testTag("empty_add_item_button"),
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Homework or Exam")
      }
    }
  }
}
