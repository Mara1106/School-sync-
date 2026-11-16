package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Course
import com.example.data.model.GradeScale
import com.example.data.model.SubjectGrade
import com.example.data.model.UiState
import com.example.ui.components.formatDateTimeShort
import com.example.ui.components.parseCourseColor
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AcademicIndigo
import com.example.ui.theme.CourseColorSwatches
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.SageTeal
import java.util.Locale

@Composable
fun GradesScreen(
  gradesState: UiState<List<SubjectGrade>>,
  onAddGrade: () -> Unit,
  onEditGrade: (SubjectGrade) -> Unit,
  onDeleteGrade: (SubjectGrade) -> Unit,
) {
  val grades = (gradesState as? UiState.Success)?.data.orEmpty()
  val averagePercent = remember(grades) {
    if (grades.isEmpty()) 0.0 else grades.map { it.percentage }.average()
  }
  val gpaValue = remember(grades) {
    val mapped = grades.mapNotNull { GradeScale.gpaPointsFromLetter(it.letterGrade) }
    if (mapped.isEmpty()) null else mapped.average()
  }
  val overallLetter = remember(grades, averagePercent) {
    if (grades.isEmpty()) "--" else GradeScale.letterFromPercentage(averagePercent)
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Gradebook Overview Card
      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(24.dp))
              .background(
                Brush.linearGradient(
                  colors =
                    listOf(
                      Color(0xFF1E3A5F),
                      Color(0xFF1B4D5C),
                      Color(0xFF0F3D3E),
                    )
                )
              )
              .padding(20.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "ACADEMIC GRADES",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF99F6E4),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Subject Score Tracker",
                  style = MaterialTheme.typography.headlineSmall,
                  color = Color.White,
                )
              }
              Button(
                onClick = onAddGrade,
                modifier = Modifier.testTag("add_grade_button"),
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Score")
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              GradeSummaryMetric(
                value = if (grades.isEmpty()) "--" else String.format(Locale.getDefault(), "%.1f%%", averagePercent),
                label = "Avg Score",
                accent = Color(0xFF5EEAD4),
                modifier = Modifier.weight(1f),
              )
              GradeSummaryMetric(
                value = overallLetter,
                label = "Avg Letter",
                accent = Color(0xFFFDE68A),
                modifier = Modifier.weight(1f),
              )
              GradeSummaryMetric(
                value = gpaValue?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: grades.size.toString(),
                label = if (gpaValue != null) "GPA (4.0)" else "Recorded",
                accent = Color(0xFFBFDBFE),
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      if (grades.isEmpty()) {
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
                imageVector = Icons.Default.Grade,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
              Text("No subject scores recorded yet", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Log your number score and letter grade (A, B, C, or custom) for each subject to track your performance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = onAddGrade,
                modifier = Modifier.testTag("empty_add_grade_button"),
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Subject Score")
              }
            }
          }
        }
      } else {
        items(grades, key = { it.id }) { grade ->
          SubjectGradeCard(
            grade = grade,
            onClick = { onEditGrade(grade) },
            onDelete = { onDeleteGrade(grade) },
          )
        }
      }

      item { Spacer(modifier = Modifier.height(80.dp)) }
    }
  }
}

@Composable
private fun GradeSummaryMetric(
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
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
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
private fun SubjectGradeCard(
  grade: SubjectGrade,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  val courseColor = parseCourseColor(grade.courseColorHex, MaterialTheme.colorScheme.primary)
  val letterUpper = grade.letterGrade.trim().uppercase()
  val badgeColor =
    when {
      letterUpper.startsWith("A") -> SageTeal
      letterUpper.startsWith("B") -> AcademicIndigo
      letterUpper.startsWith("C") -> AmberGold
      letterUpper.startsWith("D") || letterUpper.startsWith("F") -> CrimsonCoral
      else -> courseColor
    }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("grade_card_${grade.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Letter Grade Badge
      Surface(
        color = badgeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
        modifier = Modifier.size(56.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = grade.letterGrade,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            maxLines = 1,
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (grade.subjectCode.isNotBlank()) {
            Surface(
              color = courseColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = grade.subjectCode,
                style = MaterialTheme.typography.labelSmall,
                color = courseColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          if (grade.assessmentTitle.isNotBlank()) {
            Text(
              text = grade.assessmentTitle,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Text(
          text = grade.subjectName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        val formattedScore =
          if (grade.maxScore == 100.0) {
            "${formatCleanNumber(grade.numericScore)} / 100 (${formatCleanNumber(grade.percentage)}%)"
          } else {
            "${formatCleanNumber(grade.numericScore)} / ${formatCleanNumber(grade.maxScore)} (${formatCleanNumber(grade.percentage)}%)"
          }

        Text(
          text = formattedScore,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )

        if (grade.notes.isNotBlank()) {
          Text(
            text = grade.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        } else {
          Text(
            text = "Recorded ${formatDateTimeShort(grade.gradedAtMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_grade_${grade.id}")) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Delete score",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatCleanNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.getDefault(), "%.1f", value)
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectGradeEditorSheet(
  initialGrade: SubjectGrade?,
  courses: List<Course>,
  onDismiss: () -> Unit,
  onSave: (SubjectGrade, isNew: Boolean) -> Unit,
  onDelete: ((SubjectGrade) -> Unit)? = null,
) {
  val isNew = initialGrade == null
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var subjectCode by remember { mutableStateOf(initialGrade?.subjectCode.orEmpty()) }
  var subjectName by remember { mutableStateOf(initialGrade?.subjectName.orEmpty()) }
  var assessmentTitle by remember { mutableStateOf(initialGrade?.assessmentTitle.orEmpty()) }
  var scoreText by remember {
    mutableStateOf(
      if (initialGrade != null) formatCleanNumber(initialGrade.numericScore) else ""
    )
  }
  var maxScoreText by remember {
    mutableStateOf(
      if (initialGrade != null) formatCleanNumber(initialGrade.maxScore) else "100"
    )
  }
  var letterGrade by remember { mutableStateOf(initialGrade?.letterGrade ?: "A") }
  var letterEditedManually by remember { mutableStateOf(!isNew) }
  var selectedColor by remember {
    mutableStateOf(initialGrade?.courseColorHex?.ifBlank { CourseColorSwatches.first() } ?: CourseColorSwatches.first())
  }
  var notes by remember { mutableStateOf(initialGrade?.notes.orEmpty()) }

  val parsedScore = scoreText.toDoubleOrNull()
  val parsedMax = maxScoreText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 100.0
  val computedPct =
    if (parsedScore != null && parsedScore >= 0.0) {
      ((parsedScore / parsedMax) * 100.0).coerceIn(0.0, 200.0)
    } else null

  val canSave = subjectName.isNotBlank() && parsedScore != null && parsedScore >= 0.0 && letterGrade.isNotBlank()

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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (isNew) "Record Subject Score" else "Edit Subject Score",
          style = MaterialTheme.typography.headlineSmall,
        )
        if (!isNew && initialGrade != null && onDelete != null) {
          IconButton(
            onClick = {
              onDelete(initialGrade)
              onDismiss()
            },
            modifier = Modifier.testTag("delete_grade_sheet_button"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteOutline,
              contentDescription = "Delete score",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      if (courses.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Pick Enrolled Course (optional)", style = MaterialTheme.typography.titleSmall)
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            courses.forEach { course ->
              val isSelected = subjectCode == course.code && subjectName == course.name
              FilterChip(
                selected = isSelected,
                onClick = {
                  subjectCode = course.code
                  subjectName = course.name
                  selectedColor = course.colorHex
                },
                label = { Text("${course.code} • ${course.name}") },
              )
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = subjectCode,
          onValueChange = { subjectCode = it },
          label = { Text("Code") },
          placeholder = { Text("MATH 240") },
          singleLine = true,
          modifier = Modifier.weight(0.38f).testTag("grade_subject_code_input"),
        )
        OutlinedTextField(
          value = subjectName,
          onValueChange = { subjectName = it },
          label = { Text("Subject Name *") },
          placeholder = { Text("Linear Algebra") },
          singleLine = true,
          modifier = Modifier.weight(0.62f).testTag("grade_subject_name_input"),
        )
      }

      OutlinedTextField(
        value = assessmentTitle,
        onValueChange = { assessmentTitle = it },
        label = { Text("Exam / Assessment Label (optional)") },
        placeholder = { Text("e.g., Midterm Exam, Final Grade") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("grade_assessment_input"),
      )

      // Number Score + Max Score
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = scoreText,
          onValueChange = { input ->
            scoreText = input
            val num = input.toDoubleOrNull()
            if (!letterEditedManually && num != null && num >= 0.0) {
              val pct = ((num / parsedMax) * 100.0).coerceIn(0.0, 200.0)
              letterGrade = GradeScale.letterFromPercentage(pct)
            }
          },
          label = { Text("Number Score *") },
          placeholder = { Text("e.g., 92.5") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("grade_numeric_score_input"),
        )
        OutlinedTextField(
          value = maxScoreText,
          onValueChange = { input ->
            maxScoreText = input
            val nextMax = input.toDoubleOrNull()?.takeIf { it > 0.0 }
            if (!letterEditedManually && parsedScore != null && nextMax != null) {
              val pct = ((parsedScore / nextMax) * 100.0).coerceIn(0.0, 200.0)
              letterGrade = GradeScale.letterFromPercentage(pct)
            }
          },
          label = { Text("Out Of") },
          placeholder = { Text("100") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(0.6f).testTag("grade_max_score_input"),
        )
      }

      if (computedPct != null) {
        Text(
          text = "Calculated percentage: ${formatCleanNumber(computedPct)}%",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      // Letter Grade Quick Chips + Custom Input
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Letter Score (tap or type custom)", style = MaterialTheme.typography.titleSmall)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          GradeScale.standardLetters.forEach { letter ->
            FilterChip(
              selected = letterGrade.equals(letter, ignoreCase = true),
              onClick = {
                letterGrade = letter
                letterEditedManually = true
              },
              label = { Text(letter) },
              modifier = Modifier.testTag("letter_chip_${letter}"),
            )
          }
        }
        OutlinedTextField(
          value = letterGrade,
          onValueChange = {
            letterGrade = it.take(12)
            letterEditedManually = true
          },
          label = { Text("Letter / Grade Label *") },
          placeholder = { Text("A, B+, Pass, Distinction...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("grade_letter_input"),
        )
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes / Feedback (optional)") },
        minLines = 2,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )

      Button(
        onClick = {
          val score = parsedScore ?: return@Button
          val grade =
            (initialGrade ?: SubjectGrade()).copy(
              subjectCode = subjectCode.trim(),
              subjectName = subjectName.trim(),
              assessmentTitle = assessmentTitle.trim(),
              numericScore = score,
              maxScore = parsedMax,
              letterGrade = letterGrade.trim(),
              courseColorHex = selectedColor,
              notes = notes.trim(),
              gradedAtMillis = initialGrade?.gradedAtMillis ?: System.currentTimeMillis(),
            )
          onSave(grade, isNew)
          onDismiss()
        },
        enabled = canSave,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_grade_button"),
      ) {
        Text(if (isNew) "Save Score" else "Update Score")
      }
    }
  }
}
