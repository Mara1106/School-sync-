package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calendar.GoogleCalendarEventDto
import com.example.calendar.GoogleCalendarSyncManager
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.ui.CalendarSyncUiState
import com.example.ui.components.StudyItemCard
import com.example.ui.components.formatDateTimeShort
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
  isGuestMode: Boolean,
  itemsState: UiState<List<StudyItem>>,
  preferencesState: UiState<UserPreferences>,
  calendarState: CalendarSyncUiState,
  onSyncNow: () -> Unit,
  onSignInForCalendar: () -> Unit,
  onToggleAutoSync: (Boolean) -> Unit,
  onToggleComplete: (StudyItem) -> Unit,
  onEditItem: (StudyItem) -> Unit,
  onImportRemoteEvent: (GoogleCalendarEventDto, asExam: Boolean) -> Unit,
) {
  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()
  val prefs = (preferencesState as? UiState.Success)?.data ?: UserPreferences()

  var selectedDayOffset by remember { mutableIntStateOf(0) }
  val days = remember {
    (0..13).map { offset ->
      Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    }
  }

  val selectedDayStart = days[selectedDayOffset].timeInMillis
  val selectedDayEnd = selectedDayStart + 86_400_000L
  val itemsForSelectedDay = remember(allItems, selectedDayOffset) {
    allItems.filter { it.dueAtMillis in selectedDayStart until selectedDayEnd }
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Google Calendar Sync Control Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  color =
                    if (calendarState.isConnected) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(
                    imageVector =
                      if (calendarState.isConnected) Icons.Default.CloudDone
                      else Icons.Default.CalendarMonth,
                    contentDescription = "Google Calendar",
                    tint =
                      if (calendarState.isConnected) MaterialTheme.colorScheme.onTertiaryContainer
                      else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Google Calendar Sync", style = MaterialTheme.typography.titleMedium)
                  val syncStatus =
                    when {
                      isGuestMode -> "Sign in with Google to sync directly to Google Calendar"
                      calendarState.isSyncing -> "Synchronizing events..."
                      calendarState.isConnected -> calendarState.lastSummaryText ?: "Connected"
                      prefs.lastCalendarSyncMillis > 0L ->
                        "Last synced ${formatDateTimeShort(prefs.lastCalendarSyncMillis)}"
                      else -> "Push homework & exams to Google Calendar"
                    }
                  Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            if (!calendarState.errorMessage.isNullOrBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = calendarState.errorMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            if (isGuestMode) {
              Button(
                onClick = onSignInForCalendar,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(48.dp)
                    .testTag("calendar_sign_in_button"),
              ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google to Sync")
              }
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = "Auto-sync when editing items",
                  style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                  checked = prefs.googleCalendarAutoSync,
                  onCheckedChange = onToggleAutoSync,
                )
              }

              Button(
                onClick = onSyncNow,
                enabled = !calendarState.isSyncing,
                modifier =
                  Modifier.fillMaxWidth()
                    .height(48.dp)
                    .testTag("sync_google_calendar_button"),
              ) {
                if (calendarState.isSyncing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Syncing...")
                } else {
                  Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(if (calendarState.isConnected) "Sync Now & Refresh" else "Connect & Sync Google Calendar")
                }
              }
            }
          }
        }
      }

      // 14-Day Horizontal Date Strip
      item {
        Text("Two-Week Agenda", style = MaterialTheme.typography.titleLarge)
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(days.indices.toList()) { idx ->
            val dayStart = days[idx].timeInMillis
            val dayEnd = dayStart + 86_400_000L
            val dayItems = allItems.filter { it.dueAtMillis in dayStart until dayEnd && !it.isCompleted }
            val hasExam = dayItems.any { it.typeEnum == StudyItemType.EXAM }
            val hasHomework = dayItems.any { it.typeEnum == StudyItemType.HOMEWORK }
            val isSelected = idx == selectedDayOffset

            val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayStart))
            val dayNum = SimpleDateFormat("d", Locale.getDefault()).format(Date(dayStart))

            Surface(
              color =
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(16.dp),
              border =
                if (isSelected) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
              modifier =
                Modifier.width(58.dp)
                  .clickable { selectedDayOffset = idx }
                  .testTag("calendar_day_$idx"),
            ) {
              Column(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = dayName.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = dayNum,
                  style = MaterialTheme.typography.titleLarge,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier.height(8.dp),
                ) {
                  if (hasHomework) {
                    Box(
                      modifier =
                        Modifier.size(6.dp)
                          .clip(CircleShape)
                          .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                          )
                    )
                  }
                  if (hasExam) {
                    Box(
                      modifier =
                        Modifier.size(6.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.secondary)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Items for Selected Date
      item {
        val headerDate =
          SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(selectedDayStart))
        Text(
          text = "Due on $headerDate",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (itemsForSelectedDay.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No homework or exams scheduled on this day.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(itemsForSelectedDay, key = { it.id }) { item ->
          StudyItemCard(
            item = item,
            onToggleComplete = { onToggleComplete(item) },
            onClick = { onEditItem(item) },
          )
        }
      }

      // Remote Google Calendar Events (if synced)
      if (calendarState.remoteEvents.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(4.dp))
          Text("Upcoming on Google Calendar", style = MaterialTheme.typography.titleLarge)
        }
        items(calendarState.remoteEvents.take(15), key = { it.id ?: it.hashCode() }) { event ->
          val alreadyLinked = allItems.any { it.googleCalendarEventId == event.id && !event.id.isNullOrBlank() }
          val startMillis = GoogleCalendarSyncManager.parseIso8601ToMillis(event.start)
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = event.summary ?: "Untitled Event",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (alreadyLinked) {
                  Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                  ) {
                    Text(
                      text = "Synced",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                  }
                }
              }
              if (startMillis != null) {
                Text(
                  text = formatDateTimeShort(startMillis),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (!alreadyLinked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { onImportRemoteEvent(event, false) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import Homework", style = MaterialTheme.typography.labelLarge)
                  }
                  OutlinedButton(
                    onClick = { onImportRemoteEvent(event, true) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  ) {
                    Text("Import Exam", style = MaterialTheme.typography.labelLarge)
                  }
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(80.dp)) }
    }
  }
}
