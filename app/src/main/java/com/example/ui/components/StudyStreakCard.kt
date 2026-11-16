package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyStreakCalculator
import com.example.data.model.UserPreferences

@Composable
fun StudyStreakCard(
  preferences: UserPreferences,
  onCheckInToday: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val summary = remember(preferences) { StudyStreakCalculator.effectiveStreakSummary(preferences) }

  val infiniteTransition = rememberInfiniteTransition(label = "streakFlamePulse")
  val flameScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = if (summary.checkedInToday) 1.12f else 1.04f,
    animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
    label = "flameScale",
  )

  val flameTint by animateColorAsState(
    targetValue =
      when {
        summary.checkedInToday -> Color(0xFFFF6B2C) // TikTok-inspired vibrant flame orange
        summary.atRiskToday -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
      },
    label = "flameTint",
  )

  val cardBorderColor =
    when {
      summary.checkedInToday -> Color(0xFFFF6B2C).copy(alpha = 0.45f)
      summary.atRiskToday -> Color(0xFFF59E0B).copy(alpha = 0.65f)
      else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    }

  Card(
    modifier = modifier.fillMaxWidth().testTag("daily_streak_card"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.25.dp, cardBorderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          // Animated Flame Badge
          Box(
            modifier =
              Modifier.size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                  if (summary.checkedInToday || summary.atRiskToday) {
                    Brush.linearGradient(
                      colors = listOf(Color(0xFFFF6B2C).copy(alpha = 0.18f), Color(0xFFF59E0B).copy(alpha = 0.22f))
                    )
                  } else {
                    Brush.linearGradient(
                      colors =
                        listOf(
                          MaterialTheme.colorScheme.surfaceVariant,
                          MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                  }
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (summary.currentStreak >= 7L) Icons.Default.Whatshot else Icons.Default.LocalFireDepartment,
              contentDescription = "Daily study streak flame",
              tint = flameTint,
              modifier = Modifier.size(30.dp).scale(flameScale),
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "${summary.currentStreak} Day Streak",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Surface(
                color =
                  when {
                    summary.checkedInToday -> Color(0xFFFF6B2C).copy(alpha = 0.14f)
                    summary.atRiskToday -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                  },
                shape = RoundedCornerShape(20.dp),
              ) {
                Text(
                  text = if (summary.atRiskToday) "At Risk" else summary.milestoneTitle,
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    when {
                      summary.checkedInToday -> Color(0xFFEA580C)
                      summary.atRiskToday -> MaterialTheme.colorScheme.onErrorContainer
                      else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }

            Text(
              text = summary.statusMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 7-Day TikTok-Style Streak Activity Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        summary.weekDays.forEach { day ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
              modifier =
                Modifier.size(32.dp)
                  .clip(CircleShape)
                  .background(
                    when {
                      day.isActive -> Color(0xFFFF6B2C)
                      day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                      else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    }
                  )
                  .border(
                    width = if (day.isToday && !day.isActive) 1.5.dp else 0.dp,
                    color = if (day.isToday && !day.isActive) Color(0xFFFF6B2C) else Color.Transparent,
                    shape = CircleShape,
                  ),
              contentAlignment = Alignment.Center,
            ) {
              if (day.isActive) {
                Icon(
                  imageVector = Icons.Default.LocalFireDepartment,
                  contentDescription = "${day.dayLabel} active",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp),
                )
              } else {
                Text(
                  text = day.dayLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (day.isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Text(
              text = if (day.isToday) "Today" else day.dayLabel,
              style = MaterialTheme.typography.labelSmall,
              color =
                if (day.isToday) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Footer: Best Streak + Daily Check-In Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Best: ${summary.longestStreak}d • Next goal: ${summary.nextMilestoneDays}d",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
          onClick = onCheckInToday,
          enabled = !summary.checkedInToday,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFFFF6B2C),
              contentColor = Color.White,
              disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
              disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("streak_checkin_button"),
        ) {
          if (summary.checkedInToday) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Studied Today")
          } else {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Study Check-In")
          }
        }
      }
    }
  }
}
