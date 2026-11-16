package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Course
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.data.model.ThemeModeOption
import com.example.data.model.ThemePaletteOption
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.ui.components.formatDateTimeShort
import com.example.ui.components.formatReminderLead
import com.example.ui.components.parseCourseColor
import com.example.ui.theme.CourseColorSwatches
import com.example.ui.theme.ThemePreferencesStore

@Composable
fun CoursesScreen(
  coursesState: UiState<List<Course>>,
  itemsState: UiState<List<StudyItem>>,
  onAddCourse: () -> Unit,
  onEditCourse: (Course) -> Unit,
  onDeleteCourse: (Course) -> Unit,
) {
  val courses = (coursesState as? UiState.Success)?.data.orEmpty()
  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Enrolled Courses", style = MaterialTheme.typography.headlineSmall)
            Text(
              text = "Group homework and exams by subject",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(
            onClick = onAddCourse,
            modifier = Modifier.testTag("add_course_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Course")
          }
        }
      }

      if (courses.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
              Text("No courses added yet", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Add your semester courses (e.g., CS 301, MATH 240) to color-code deadlines and exams.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(courses, key = { it.id }) { course ->
          val courseColor = parseCourseColor(course.colorHex, MaterialTheme.colorScheme.primary)
          val hwCount = allItems.count {
            !it.isCompleted && it.courseCode == course.code && it.typeEnum == StudyItemType.HOMEWORK
          }
          val examCount = allItems.count {
            !it.isCompleted && it.courseCode == course.code && it.typeEnum == StudyItemType.EXAM
          }

          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth().clickable { onEditCourse(course) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.width(6.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(courseColor)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = course.code,
                  style = MaterialTheme.typography.labelMedium,
                  color = courseColor,
                )
                Text(text = course.name, style = MaterialTheme.typography.titleMedium)
                if (course.instructor.isNotBlank()) {
                  Text(
                    text = course.instructor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = "$hwCount homework • $examCount exam${if (examCount == 1) "" else "s"} pending",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { onDeleteCourse(course) }) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Delete course",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(80.dp)) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsAndSettingsScreen(
  isGuestMode: Boolean,
  userEmail: String?,
  userName: String?,
  hasNotificationPermission: Boolean,
  preferencesState: UiState<UserPreferences>,
  itemsState: UiState<List<StudyItem>>,
  expandManualOnLaunch: Boolean = false,
  onOpenProfile: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
  onUpdateTheme: (ThemeModeOption, ThemePaletteOption, String) -> Unit,
  onApplySuggestedTheme: () -> Unit,
  onSendTestNotification: () -> Unit,
  onSignInWithGoogle: () -> Unit,
  onExitGuestMode: () -> Unit,
  onSignOut: () -> Unit,
) {
  val prefs = (preferencesState as? UiState.Success)?.data ?: UserPreferences()
  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()
  val scheduledAlerts = remember(allItems, prefs) {
    val now = System.currentTimeMillis()
    allItems
      .filter {
        prefs.pushNotificationsEnabled &&
          !it.isCompleted &&
          it.notifyEnabled &&
          it.reminderMinutesBefore > 0L &&
          (it.dueAtMillis - it.reminderMinutesBefore * 60_000L) > now
      }
      .sortedBy { it.dueAtMillis - it.reminderMinutesBefore * 60_000L }
  }

  val leadOptions = listOf(15L to "15m", 30L to "30m", 60L to "1h", 180L to "3h", 1440L to "1d", 2880L to "2d")
  val digestHours = listOf(7L to "7 AM", 8L to "8 AM", 9L to "9 AM", 18L to "6 PM", 20L to "8 PM")

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Student Profile Summary Card
      item {
        StudentProfileSummaryCard(
          prefs = prefs,
          fallbackName = userName,
          onOpenProfile = onOpenProfile,
        )
      }

      // Permission Status Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (hasNotificationPermission) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.errorContainer
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector =
                  if (hasNotificationPermission) Icons.Default.NotificationsActive
                  else Icons.Default.NotificationsOff,
                contentDescription = "Notification permission status",
                tint =
                  if (hasNotificationPermission) MaterialTheme.colorScheme.onTertiaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text =
                    if (hasNotificationPermission) "Push Notifications Active"
                    else "Notifications Permission Required",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    if (hasNotificationPermission) "Deadline & exam reminders will alert you on time."
                    else "Grant permission so StudySync can alert you before deadlines.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
            if (!hasNotificationPermission) {
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = onRequestNotificationPermission,
                modifier = Modifier.testTag("grant_notifications_button"),
              ) {
                Text("Allow")
              }
            }
          }
        }
      }

      // Notification Controls
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Notification Preferences", style = MaterialTheme.typography.titleLarge)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Deadline Push Alerts", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Notify before homework and exams",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = prefs.pushNotificationsEnabled,
                onCheckedChange = { enabled ->
                  onUpdatePreferences { it.copy(pushNotificationsEnabled = enabled) }
                },
              )
            }

            Text("Default Homework Reminder", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              leadOptions.forEach { (mins, label) ->
                FilterChip(
                  selected = prefs.defaultHomeworkReminderMinutes == mins,
                  onClick = {
                    onUpdatePreferences { it.copy(defaultHomeworkReminderMinutes = mins) }
                  },
                  label = { Text(label) },
                )
              }
            }

            Text("Default Exam Alert", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              leadOptions.forEach { (mins, label) ->
                FilterChip(
                  selected = prefs.defaultExamReminderMinutes == mins,
                  onClick = {
                    onUpdatePreferences { it.copy(defaultExamReminderMinutes = mins) }
                  },
                  label = { Text(label) },
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Daily Study Digest", style = MaterialTheme.typography.titleSmall)
                Text(
                  "Daily summary of pending items",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = prefs.dailyDigestEnabled,
                onCheckedChange = { enabled ->
                  onUpdatePreferences { it.copy(dailyDigestEnabled = enabled) }
                },
              )
            }

            if (prefs.dailyDigestEnabled) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digestHours.forEach { (hour, label) ->
                  FilterChip(
                    selected = prefs.dailyDigestHour == hour,
                    onClick = { onUpdatePreferences { it.copy(dailyDigestHour = hour) } },
                    label = { Text(label) },
                  )
                }
              }
            }

            OutlinedButton(
              onClick = {
                if (!hasNotificationPermission) {
                  onRequestNotificationPermission()
                } else {
                  onSendTestNotification()
                }
              },
              modifier =
                Modifier.fillMaxWidth()
                  .height(48.dp)
                  .testTag("send_test_notification_button"),
            ) {
              Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.send_test_notification))
            }
          }
        }
      }

      // Theme & Appearance Card (Suggested & Manual)
      item {
        ThemeSettingsCard(
          prefs = prefs,
          onUpdateTheme = onUpdateTheme,
          onApplySuggestedTheme = onApplySuggestedTheme,
        )
      }

      // StudySync Manual & Tips Card
      item {
        StudySyncManualCard(expandInitially = expandManualOnLaunch)
      }

      // Scheduled Alerts Queue
      item {
        Text("Scheduled Alert Queue (${scheduledAlerts.size})", style = MaterialTheme.typography.titleLarge)
      }

      if (scheduledAlerts.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No upcoming alerts queued. Add a pending homework or exam with notifications enabled.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(scheduledAlerts, key = { it.id }) { item ->
          val fireAt = item.dueAtMillis - (item.reminderMinutesBefore * 60_000L)
          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "Fires ${formatDateTimeShort(fireAt)} (${formatReminderLead(item.reminderMinutesBefore)})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      // Account Card
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            if (isGuestMode) {
              Text("Local Guest Mode", style = MaterialTheme.typography.bodyLarge)
              Text(
                text = "Your schedule and notifications are saved locally on this device. Sign in anytime to back up to the cloud and sync Google Calendar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = onSignInWithGoogle,
                modifier = Modifier.fillMaxWidth().testTag("settings_google_sign_in_button"),
              ) {
                Text(stringResource(R.string.auth_cta_google))
              }
              OutlinedButton(
                onClick = onExitGuestMode,
                modifier = Modifier.fillMaxWidth().testTag("exit_guest_button"),
              ) {
                Text("Show Welcome Screen")
              }
            } else {
              if (!userName.isNullOrBlank()) {
                Text(userName, style = MaterialTheme.typography.bodyLarge)
              }
              if (!userEmail.isNullOrBlank()) {
                Text(
                  userEmail,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().testTag("sign_out_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(80.dp)) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettingsCard(
  prefs: UserPreferences,
  onUpdateTheme: (ThemeModeOption, ThemePaletteOption, String) -> Unit,
  onApplySuggestedTheme: () -> Unit,
) {
  val currentMode = ThemeModeOption.fromWire(prefs.themeMode)
  val currentPalette = ThemePaletteOption.fromWire(prefs.themePalette)
  val suggestion = remember { ThemePreferencesStore.smartSuggestionForHour() }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().testTag("theme_settings_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
          imageVector = Icons.Default.Palette,
          contentDescription = "Theme settings",
          tint = MaterialTheme.colorScheme.primary,
        )
        Column {
          Text("Appearance & Theme", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Choose a smart suggested theme or customize manually",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Smart Suggestion Banner
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Suggested Theme",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Text(
            text = suggestion.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = onApplySuggestedTheme,
            modifier = Modifier.fillMaxWidth().testTag("apply_suggested_theme_button"),
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Apply Suggestion (${suggestion.recommendedPalette.label})")
          }
        }
      }

      // Mode Selection (Suggested Auto vs Manual Light/Dark)
      Text("Brightness Mode (Suggested vs Manual)", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeModeOption.entries.forEach { mode ->
          FilterChip(
            selected = currentMode == mode,
            onClick = { onUpdateTheme(mode, currentPalette, prefs.manualAccentHex) },
            label = { Text(mode.label) },
            modifier = Modifier.testTag("theme_mode_${mode.wireValue}"),
          )
        }
      }

      // Suggested Theme Palettes
      Text("Suggested Color Themes", style = MaterialTheme.typography.titleSmall)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        ThemePaletteOption.entries.filter { it.isSuggested }.forEach { preset ->
          val presetColor = parseCourseColor(preset.primaryHex, MaterialTheme.colorScheme.primary)
          FilterChip(
            selected = currentPalette == preset,
            onClick = { onUpdateTheme(currentMode, preset, preset.primaryHex) },
            leadingIcon = {
              Box(
                modifier = Modifier.size(12.dp).clip(CircleShape).background(presetColor)
              )
            },
            label = { Text(preset.label) },
            modifier = Modifier.testTag("theme_preset_${preset.wireValue}"),
          )
        }
      }

      // Manual Custom Color Picker
      Text("Manual Custom Color", style = MaterialTheme.typography.titleSmall)
      Text(
        text = "Pick any accent swatch manually to tailor buttons, badges, and highlights:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CourseColorSwatches.forEachIndexed { idx, hex ->
          val swatchColor = parseCourseColor(hex, MaterialTheme.colorScheme.primary)
          val isManualSelected =
            currentPalette == ThemePaletteOption.MANUAL && prefs.manualAccentHex.equals(hex, ignoreCase = true)
          Box(
            modifier =
              Modifier.size(36.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                  width = if (isManualSelected) 3.dp else 1.dp,
                  color =
                    if (isManualSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                  shape = CircleShape,
                )
                .clickable {
                  onUpdateTheme(currentMode, ThemePaletteOption.MANUAL, hex)
                }
                .testTag("manual_theme_swatch_$idx"),
            contentAlignment = Alignment.Center,
          ) {
            if (isManualSelected) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected manual theme color",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudySyncManualCard(expandInitially: Boolean = false) {
  var expanded by remember(expandInitially) { mutableStateOf(expandInitially) }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().testTag("study_manual_card"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clickable { expanded = !expanded }
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = "User manual",
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text("StudySync User Manual & Guide", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "How streaks, charts, grades, and themes work",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (expanded) "Collapse manual" else "Expand manual",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ManualGuideRow(
            title = "1. Daily Study Streak",
            body = "Complete any homework/exam or tap Study Check-In once per day to build your streak milestones (3d Spark, 7d Blaze, 14d Scholar, 30d Legend).",
          )
          ManualGuideRow(
            title = "2. Weekly Progress Chart",
            body = "View completed assignments vs. remaining deadlines across Monday–Sunday on your Schedule tab. Tap any day column for details.",
          )
          ManualGuideRow(
            title = "3. Subject Grades",
            body = "Open the Grades tab to record numeric scores and letter grades (A, B, C, or custom labels) and track your GPA.",
          )
          ManualGuideRow(
            title = "4. Suggested & Manual Themes",
            body = "Use Apply Suggestion for time-of-day study comfort, or switch manually between Light/Dark and custom accent colors.",
          )
          ManualGuideRow(
            title = "5. Reminders & Google Calendar",
            body = "Guest mode saves everything locally with push alerts. Sign in with Google anytime to back up to the cloud and sync Google Calendar.",
          )
        }
      }
    }
  }
}

@Composable
private fun ManualGuideRow(title: String, body: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StudentProfileSummaryCard(
  prefs: UserPreferences,
  fallbackName: String?,
  onOpenProfile: () -> Unit,
) {
  val displayName = prefs.studentName.takeIf { it.isNotBlank() } ?: fallbackName ?: "Student"
  val initials =
    displayName
      .trim()
      .split(" ")
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercaseChar().toString() }
      .ifBlank { "S" }
  val infoLine =
    listOfNotNull(
      prefs.studentMajor.trim().takeIf { it.isNotBlank() },
      prefs.studentYear.trim().takeIf { it.isNotBlank() },
      prefs.studentSchool.trim().takeIf { it.isNotBlank() },
    ).joinToString(" • ")

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().testTag("settings_profile_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = initials,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = displayName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                infoLine.ifBlank { "Add your school, major, year & bio" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
          onClick = onOpenProfile,
          modifier = Modifier.testTag("open_profile_from_settings_button"),
        ) {
          Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (prefs.studentBio.isBlank() && prefs.studentMajor.isBlank()) "Set Up" else "Edit")
        }
      }

      if (prefs.studentBio.isNotBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = prefs.studentBio,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
          )
        }
      }

      if (prefs.studyGoal.isNotBlank()) {
        Text(
          text = "Goal: ${prefs.studyGoal}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
