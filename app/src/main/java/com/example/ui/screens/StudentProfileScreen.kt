package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StudyStreakCalculator
import com.example.data.model.UserPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentProfileScreen(
  preferences: UserPreferences,
  fallbackDisplayName: String?,
  userEmail: String?,
  isGuestMode: Boolean,
  coursesCount: Int,
  gradesCount: Int,
  onSaveProfile: (
    studentName: String,
    studentSchool: String,
    studentMajor: String,
    studentYear: String,
    studentIdLabel: String,
    studyGoal: String,
    studentBio: String,
  ) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val initialName =
    remember(preferences.studentName, fallbackDisplayName) {
      preferences.studentName.ifBlank { fallbackDisplayName?.takeIf { it != "Student" }.orEmpty() }
    }
  var studentName by remember(preferences.studentName, fallbackDisplayName) {
    mutableStateOf(initialName)
  }
  var studentSchool by remember(preferences.studentSchool) { mutableStateOf(preferences.studentSchool) }
  var studentMajor by remember(preferences.studentMajor) { mutableStateOf(preferences.studentMajor) }
  var studentYear by remember(preferences.studentYear) { mutableStateOf(preferences.studentYear) }
  var studentIdLabel by remember(preferences.studentIdLabel) { mutableStateOf(preferences.studentIdLabel) }
  var studyGoal by remember(preferences.studyGoal) { mutableStateOf(preferences.studyGoal) }
  var studentBio by remember(preferences.studentBio) { mutableStateOf(preferences.studentBio) }

  val yearPresets = listOf("High School", "Freshman", "Sophomore", "Junior", "Senior", "Grad")
  val streakSummary = remember(preferences) { StudyStreakCalculator.effectiveStreakSummary(preferences) }

  val displayTitle = studentName.trim().ifBlank { fallbackDisplayName ?: "Student" }
  val initials =
    displayTitle
      .split(" ")
      .filter { it.isNotBlank() }
      .take(2)
      .joinToString("") { it.first().uppercaseChar().toString() }
      .ifBlank { "S" }

  fun commitProfile() {
    onSaveProfile(
      studentName,
      studentSchool,
      studentMajor,
      studentYear,
      studentIdLabel,
      studyGoal,
      studentBio,
    )
    onBack()
  }

  Scaffold(
    contentWindowInsets = WindowInsets.navigationBars,
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets.statusBars,
        title = { Text(stringResource(R.string.student_profile_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("profile_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          TextButton(
            onClick = ::commitProfile,
            modifier = Modifier.testTag("profile_top_save_button"),
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Student Profile Card Preview
        item {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                  Brush.linearGradient(
                    colors =
                      listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.tertiary,
                      )
                  )
                )
                .padding(20.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(62.dp)
                      .clip(CircleShape)
                      .background(Color.White.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val subtitleParts =
                    listOfNotNull(
                      studentMajor.trim().takeIf { it.isNotBlank() },
                      studentYear.trim().takeIf { it.isNotBlank() },
                    )
                  Text(
                    text =
                      if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ")
                      else if (isGuestMode) "Local Student Profile"
                      else userEmail ?: "Cloud Student Profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (studentSchool.isNotBlank() || studentIdLabel.isNotBlank()) {
                    Text(
                      text =
                        listOfNotNull(
                          studentSchool.trim().takeIf { it.isNotBlank() },
                          studentIdLabel.trim().takeIf { it.isNotBlank() },
                        ).joinToString(" • "),
                      style = MaterialTheme.typography.bodySmall,
                      color = Color.White.copy(alpha = 0.8f),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }

              if (studentBio.isNotBlank()) {
                Surface(
                  color = Color.White.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = studentBio.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ProfileStatPill(
                  label = "Streak",
                  value = "${streakSummary.currentStreak}d",
                  modifier = Modifier.weight(1f),
                )
                ProfileStatPill(
                  label = "Courses",
                  value = coursesCount.toString(),
                  modifier = Modifier.weight(1f),
                )
                ProfileStatPill(
                  label = "Grades",
                  value = gradesCount.toString(),
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        // Student Info Card
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
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Text("Student Info", style = MaterialTheme.typography.titleLarge)
              }

              OutlinedTextField(
                value = studentName,
                onValueChange = { if (it.length <= 80) studentName = it },
                label = { Text("Full Name or Nickname") },
                placeholder = { Text("e.g., Alex Rivera") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
              )

              OutlinedTextField(
                value = studentSchool,
                onValueChange = { if (it.length <= 100) studentSchool = it },
                label = { Text("School / University") },
                placeholder = { Text("e.g., Pacific State University") },
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_school_input"),
              )

              OutlinedTextField(
                value = studentMajor,
                onValueChange = { if (it.length <= 80) studentMajor = it },
                label = { Text("Major / Study Track") },
                placeholder = { Text("e.g., Computer Science & Math") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_major_input"),
              )

              Text("Academic Year / Grade Level", style = MaterialTheme.typography.titleSmall)
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                yearPresets.forEach { preset ->
                  FilterChip(
                    selected = studentYear.equals(preset, ignoreCase = true),
                    onClick = { studentYear = preset },
                    label = { Text(preset) },
                  )
                }
              }

              OutlinedTextField(
                value = studentYear,
                onValueChange = { if (it.length <= 40) studentYear = it },
                label = { Text("Year / Level (or custom)") },
                placeholder = { Text("e.g., Junior or 11th Grade") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_year_input"),
              )

              OutlinedTextField(
                value = studentIdLabel,
                onValueChange = { if (it.length <= 40) studentIdLabel = it },
                label = { Text("Student ID / Cohort (optional)") },
                placeholder = { Text("e.g., Class of 2027 • ID 2049") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_id_input"),
              )

              OutlinedTextField(
                value = studyGoal,
                onValueChange = { if (it.length <= 160) studyGoal = it },
                label = { Text("Semester Study Goal") },
                placeholder = { Text("e.g., Maintain a 3.8 GPA & submit homework early") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_goal_input"),
              )
            }
          }
        }

        // Student Bio Card
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text("Student Bio", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                  text = "${studentBio.length}/400",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              OutlinedTextField(
                value = studentBio,
                onValueChange = { if (it.length <= 400) studentBio = it },
                label = { Text("About You") },
                placeholder = {
                  Text("Share your academic interests, favorite subjects, clubs, or study habits...")
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("profile_bio_input"),
              )
            }
          }
        }

        // Save Action Button
        item {
          Button(
            onClick = ::commitProfile,
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("save_profile_button"),
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.save_profile))
          }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }
}

@Composable
private fun ProfileStatPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.16f),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f),
      )
    }
  }
}
