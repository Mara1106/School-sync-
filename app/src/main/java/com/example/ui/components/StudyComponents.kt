package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.calendar.GoogleCalendarSyncManager
import com.example.data.model.Course
import com.example.data.model.PriorityLevel
import com.example.data.model.StudyItem
import com.example.data.model.StudyItemType
import com.example.ui.theme.CourseColorSwatches
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun parseCourseColor(hex: String, fallback: Color): Color {
  return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

fun formatDateTimeShort(millis: Long): String {
  if (millis <= 0L) return "Not set"
  return SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(millis))
}

fun formatRelativeCountdown(dueAtMillis: Long, isCompleted: Boolean): Pair<String, Boolean> {
  if (isCompleted) return "Completed" to false
  val diff = dueAtMillis - System.currentTimeMillis()
  val absMinutes = abs(diff) / 60_000L
  val hours = absMinutes / 60L
  val days = hours / 24L

  if (diff < 0L) {
    val label =
      when {
        absMinutes < 60L -> "Overdue ${absMinutes}m"
        hours < 24L -> "Overdue ${hours}h"
        else -> "Overdue ${days}d"
      }
    return label to true
  }

  val label =
    when {
      absMinutes < 60L -> "In ${absMinutes.coerceAtLeast(1)}m"
      hours < 24L -> "In ${hours}h ${absMinutes % 60L}m"
      days == 1L -> "Tomorrow"
      else -> "In ${days}d"
    }
  return label to (hours < 24L)
}

fun formatReminderLead(minutes: Long): String =
  when {
    minutes <= 0L -> "Off"
    minutes < 60L -> "${minutes}m before"
    minutes < 1440L -> "${minutes / 60L}h before"
    else -> "${minutes / 1440L}d before"
  }

@Composable
fun StudyItemCard(
  item: StudyItem,
  onToggleComplete: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accentColor = parseCourseColor(item.courseColorHex, MaterialTheme.colorScheme.primary)
  val isExam = item.typeEnum == StudyItemType.EXAM
  val (countdownText, isUrgent) = remember(item.dueAtMillis, item.status) {
    formatRelativeCountdown(item.dueAtMillis, item.isCompleted)
  }

  val containerColor by animateColorAsState(
    targetValue =
      if (item.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      else MaterialTheme.colorScheme.surface,
    label = "cardContainerColor",
  )

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .alpha(if (item.isCompleted) 0.72f else 1f)
        .clickable(onClick = onClick)
        .testTag("study_item_card_${item.id}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border =
      BorderStroke(
        width = if (isExam && !item.isCompleted) 1.5.dp else 1.dp,
        color =
          if (isExam && !item.isCompleted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCompleted) 0.dp else 1.5.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      // Course Color Ribbon
      Box(
        modifier =
          Modifier.width(4.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isExam) MaterialTheme.colorScheme.secondary else accentColor)
      )

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Type Badge
          Surface(
            color =
              if (isExam) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = if (isExam) Icons.Outlined.Quiz else Icons.AutoMirrored.Outlined.Assignment,
                contentDescription = item.typeEnum.label,
                modifier = Modifier.size(13.dp),
                tint =
                  if (isExam) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = item.typeEnum.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isExam) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          if (item.courseCode.isNotBlank()) {
            Surface(
              color = accentColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = item.courseCode,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }

          Spacer(modifier = Modifier.weight(1f))

          // Countdown Pill
          Surface(
            color =
              when {
                item.isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
                isUrgent -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
              },
            shape = RoundedCornerShape(20.dp),
          ) {
            Text(
              text = countdownText,
              style = MaterialTheme.typography.labelSmall,
              color =
                when {
                  item.isCompleted -> MaterialTheme.colorScheme.onTertiaryContainer
                  isUrgent -> MaterialTheme.colorScheme.onErrorContainer
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = "Due time",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = formatDateTimeShort(item.dueAtMillis),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (item.location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Place,
                contentDescription = "Location",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.width(2.dp))
              Text(
                text = item.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status icons row (Notification + Calendar Sync + Priority)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector =
                if (item.notifyEnabled && item.reminderMinutesBefore > 0)
                  Icons.Default.NotificationsActive
                else Icons.Default.NotificationsOff,
              contentDescription = "Notification reminder",
              modifier = Modifier.size(14.dp),
              tint =
                if (item.notifyEnabled && item.reminderMinutesBefore > 0)
                  MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text =
                if (item.notifyEnabled && item.reminderMinutesBefore > 0)
                  formatReminderLead(item.reminderMinutesBefore)
                else "Muted",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (item.isSyncedWithCalendar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Synced with Google Calendar",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "GCal Synced",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
          }

          if (item.priorityEnum == PriorityLevel.HIGH && !item.isCompleted) {
            Text(
              text = "• High Priority",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // Completion Toggle Button
      IconButton(
        onClick = onToggleComplete,
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("toggle_complete_${item.id}"),
      ) {
        if (item.isCompleted) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Completed",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(26.dp),
          )
        } else {
          Box(
            modifier =
              Modifier.size(24.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyItemEditorSheet(
  initialItem: StudyItem?,
  courses: List<Course>,
  defaultHomeworkLead: Long,
  defaultExamLead: Long,
  onDismiss: () -> Unit,
  onSave: (StudyItem, isNew: Boolean) -> Unit,
  onDelete: ((StudyItem) -> Unit)? = null,
  onQuickCreateCourse: () -> Unit,
) {
  val context = LocalContext.current
  val isNew = initialItem == null
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var itemType by remember {
    mutableStateOf(initialItem?.typeEnum ?: StudyItemType.HOMEWORK)
  }
  var title by remember { mutableStateOf(initialItem?.title.orEmpty()) }
  var description by remember { mutableStateOf(initialItem?.description.orEmpty()) }
  var location by remember { mutableStateOf(initialItem?.location.orEmpty()) }
  var selectedCourseCode by remember { mutableStateOf(initialItem?.courseCode.orEmpty()) }
  var selectedCourseName by remember { mutableStateOf(initialItem?.courseName.orEmpty()) }
  var selectedCourseColor by remember {
    mutableStateOf(initialItem?.courseColorHex?.ifBlank { "#1E3A5F" } ?: "#1E3A5F")
  }
  var dueAtMillis by remember {
    mutableLongStateOf(
      initialItem?.dueAtMillis ?: (System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
    )
  }
  var durationMinutes by remember {
    mutableLongStateOf(initialItem?.durationMinutes ?: 60L)
  }
  var priority by remember {
    mutableStateOf(initialItem?.priorityEnum ?: PriorityLevel.MEDIUM)
  }
  var notifyEnabled by remember {
    mutableStateOf(initialItem?.notifyEnabled ?: true)
  }
  var reminderMinutesBefore by remember {
    mutableLongStateOf(
      initialItem?.reminderMinutesBefore
        ?: if (itemType == StudyItemType.EXAM) defaultExamLead else defaultHomeworkLead
    )
  }

  val reminderOptions = listOf(15L to "15m", 30L to "30m", 60L to "1h", 180L to "3h", 1440L to "1d", 2880L to "2d")
  val durationOptions = listOf(30L to "30m", 60L to "1h", 90L to "1.5h", 120L to "2h", 180L to "3h")

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = if (isNew) "Schedule Study Item" else "Edit ${itemType.label}",
          style = MaterialTheme.typography.headlineSmall,
        )
        if (!isNew && initialItem != null && onDelete != null) {
          IconButton(
            onClick = {
              onDelete(initialItem)
              onDismiss()
            },
            modifier = Modifier.testTag("delete_item_button"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete item",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // Type Segmented Toggle
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StudyItemType.entries.forEachIndexed { index, type ->
          SegmentedButton(
            selected = itemType == type,
            onClick = {
              itemType = type
              if (isNew) {
                reminderMinutesBefore =
                  if (type == StudyItemType.EXAM) defaultExamLead else defaultHomeworkLead
                durationMinutes = if (type == StudyItemType.EXAM) 120L else 60L
                if (type == StudyItemType.EXAM) priority = PriorityLevel.HIGH
              }
            },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
          ) {
            Text(type.label)
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(if (itemType == StudyItemType.EXAM) "Exam Title" else "Homework Title") },
        placeholder = {
          Text(if (itemType == StudyItemType.EXAM) "e.g., Midterm II: Graph Theory" else "e.g., Problem Set 4")
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("item_title_input"),
      )

      // Course Selection Chips
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Course", style = MaterialTheme.typography.titleSmall)
          TextButton(onClick = onQuickCreateCourse) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Course")
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedCourseCode.isBlank(),
            onClick = {
              selectedCourseCode = ""
              selectedCourseName = ""
              selectedCourseColor = "#1E3A5F"
            },
            label = { Text("General") },
          )
          courses.forEach { course ->
            val selected = selectedCourseCode == course.code
            FilterChip(
              selected = selected,
              onClick = {
                selectedCourseCode = course.code
                selectedCourseName = course.name
                selectedCourseColor = course.colorHex
              },
              label = { Text(course.code) },
            )
          }
        }
      }

      // Date & Time Pickers
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
            DatePickerDialog(
              context,
              { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                  timeInMillis = dueAtMillis
                  set(Calendar.YEAR, year)
                  set(Calendar.MONTH, month)
                  set(Calendar.DAY_OF_MONTH, day)
                }
                dueAtMillis = updated.timeInMillis
              },
              cal.get(Calendar.YEAR),
              cal.get(Calendar.MONTH),
              cal.get(Calendar.DAY_OF_MONTH),
            ).show()
          },
          modifier = Modifier.weight(1f).testTag("pick_date_button"),
        ) {
          Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(dueAtMillis)))
        }

        OutlinedButton(
          onClick = {
            val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
            TimePickerDialog(
              context,
              { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                  timeInMillis = dueAtMillis
                  set(Calendar.HOUR_OF_DAY, hour)
                  set(Calendar.MINUTE, minute)
                  set(Calendar.SECOND, 0)
                }
                dueAtMillis = updated.timeInMillis
              },
              cal.get(Calendar.HOUR_OF_DAY),
              cal.get(Calendar.MINUTE),
              false,
            ).show()
          },
          modifier = Modifier.weight(1f).testTag("pick_time_button"),
        ) {
          Icon(Icons.Default.Schedule, contentDescription = "Pick time", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dueAtMillis)))
        }
      }

      // Duration
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Calendar Duration", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          durationOptions.forEach { (mins, label) ->
            FilterChip(
              selected = durationMinutes == mins,
              onClick = { durationMinutes = mins },
              label = { Text(label) },
            )
          }
        }
      }

      // Priority
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Priority", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PriorityLevel.entries.forEach { level ->
            FilterChip(
              selected = priority == level,
              onClick = { priority = level },
              label = { Text(level.label) },
            )
          }
        }
      }

      // Push Notification Reminder
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Deadline Push Alert", style = MaterialTheme.typography.titleSmall)
            }
            Switch(checked = notifyEnabled, onCheckedChange = { notifyEnabled = it })
          }
          if (notifyEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              reminderOptions.forEach { (mins, label) ->
                FilterChip(
                  selected = reminderMinutesBefore == mins,
                  onClick = { reminderMinutesBefore = mins },
                  label = { Text(label) },
                )
              }
            }
          }
        }
      }

      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text(if (itemType == StudyItemType.EXAM) "Room / Hall" else "Submission Portal / Location") },
        placeholder = { Text(if (itemType == StudyItemType.EXAM) "e.g., Engineering Hall 204" else "e.g., Canvas / Gradescope") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Notes / Syllabus Scope") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )

      if (!isNew && initialItem != null) {
        OutlinedButton(
          onClick = {
            runCatching {
              context.startActivity(GoogleCalendarSyncManager.createOpenInCalendarAppIntent(initialItem))
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Open in Calendar App")
          Spacer(modifier = Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
        }
      }

      Button(
        onClick = {
          if (title.isBlank()) return@Button
          val updated =
            (initialItem ?: StudyItem()).copy(
              title = title.trim(),
              description = description.trim(),
              itemType = itemType.wireValue,
              courseCode = selectedCourseCode,
              courseName = selectedCourseName,
              courseColorHex = selectedCourseColor,
              location = location.trim(),
              dueAtMillis = dueAtMillis,
              durationMinutes = durationMinutes,
              priority = priority.wireValue,
              reminderMinutesBefore = if (notifyEnabled) reminderMinutesBefore else 0L,
              notifyEnabled = notifyEnabled,
            )
          onSave(updated, isNew)
          onDismiss()
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_study_item_button"),
      ) {
        Text(if (isNew) "Save to Schedule" else "Save Changes")
      }
    }
  }
}

@Composable
fun CourseEditorDialog(
  initialCourse: Course?,
  onDismiss: () -> Unit,
  onSave: (Course, isNew: Boolean) -> Unit,
) {
  val isNew = initialCourse == null
  var code by remember { mutableStateOf(initialCourse?.code.orEmpty()) }
  var name by remember { mutableStateOf(initialCourse?.name.orEmpty()) }
  var instructor by remember { mutableStateOf(initialCourse?.instructor.orEmpty()) }
  var selectedColor by remember { mutableStateOf(initialCourse?.colorHex ?: CourseColorSwatches.first()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (isNew) "Add Course" else "Edit Course") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = code,
          onValueChange = { code = it },
          label = { Text("Course Code") },
          placeholder = { Text("e.g., CS 301") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("course_code_input"),
        )
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Course Name") },
          placeholder = { Text("e.g., Algorithms & Data Structures") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("course_name_input"),
        )
        OutlinedTextField(
          value = instructor,
          onValueChange = { instructor = it },
          label = { Text("Instructor (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("Tag Color", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CourseColorSwatches.forEach { hex ->
            val swatch = parseCourseColor(hex, MaterialTheme.colorScheme.primary)
            val isSelected = selectedColor.equals(hex, ignoreCase = true)
            Box(
              modifier =
                Modifier.size(30.dp)
                  .clip(CircleShape)
                  .background(swatch)
                  .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                  )
                  .clickable { selectedColor = hex },
              contentAlignment = Alignment.Center,
            ) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected color",
                  tint = Color.White,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (code.isNotBlank() && name.isNotBlank()) {
            onSave(
              (initialCourse ?: Course()).copy(
                code = code.trim(),
                name = name.trim(),
                instructor = instructor.trim(),
                colorHex = selectedColor,
              ),
              isNew,
            )
            onDismiss()
          }
        },
        enabled = code.isNotBlank() && name.isNotBlank(),
        modifier = Modifier.testTag("save_course_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
