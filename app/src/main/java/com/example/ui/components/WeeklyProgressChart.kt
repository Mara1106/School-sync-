package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudyItem
import com.example.ui.theme.AmberGold
import com.example.ui.theme.SageTeal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class WeeklyDayStat(
  val dayLabel: String,
  val dateLabel: String,
  val completedCount: Int,
  val remainingCount: Int,
  val isToday: Boolean,
)

data class WeeklyScheduleSummary(
  val weekRangeLabel: String,
  val days: List<WeeklyDayStat>,
  val totalCompleted: Int,
  val totalRemaining: Int,
) {
  val totalItems: Int get() = totalCompleted + totalRemaining
  val completionPercent: Int
    get() = if (totalItems == 0) 0 else ((totalCompleted.toFloat() / totalItems.toFloat()) * 100f).toInt()
}

fun computeCurrentWeekStats(
  items: List<StudyItem>,
  referenceMillis: Long = System.currentTimeMillis(),
): WeeklyScheduleSummary {
  val weekStartCal =
    Calendar.getInstance().apply {
      timeInMillis = referenceMillis
      set(Calendar.HOUR_OF_DAY, 0)
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

  val todayStartCal =
    Calendar.getInstance().apply {
      timeInMillis = referenceMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
  val todayStartMillis = todayStartCal.timeInMillis

  val dayNameFmt = SimpleDateFormat("EEE", Locale.getDefault())
  val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

  val days = mutableListOf<WeeklyDayStat>()
  var totalCompleted = 0
  var totalRemaining = 0

  val cursor = weekStartCal.clone() as Calendar
  val firstDayMillis = cursor.timeInMillis
  var lastDayMillis = firstDayMillis

  repeat(7) {
    val dayStart = cursor.timeInMillis
    val dayEnd = dayStart + 86_400_000L
    lastDayMillis = dayStart

    val dayItems = items.filter { it.dueAtMillis in dayStart until dayEnd }
    val completed = dayItems.count { it.isCompleted }
    val remaining = dayItems.count { !it.isCompleted }

    totalCompleted += completed
    totalRemaining += remaining

    days.add(
      WeeklyDayStat(
        dayLabel = dayNameFmt.format(Date(dayStart)),
        dateLabel = dateFmt.format(Date(dayStart)),
        completedCount = completed,
        remainingCount = remaining,
        isToday = dayStart == todayStartMillis,
      )
    )
    cursor.add(Calendar.DAY_OF_YEAR, 1)
  }

  val rangeLabel = "${dateFmt.format(Date(firstDayMillis))} – ${dateFmt.format(Date(lastDayMillis))}"
  return WeeklyScheduleSummary(
    weekRangeLabel = rangeLabel,
    days = days,
    totalCompleted = totalCompleted,
    totalRemaining = totalRemaining,
  )
}

@Composable
fun WeeklyProgressChartCard(
  allItems: List<StudyItem>,
  modifier: Modifier = Modifier,
) {
  val summary = remember(allItems) { computeCurrentWeekStats(allItems) }
  val initialSelected = remember(summary) {
    summary.days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
  }
  var selectedDayIndex by remember(summary.weekRangeLabel) { mutableIntStateOf(initialSelected) }

  val completedColor = SageTeal
  val remainingColor = AmberGold
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
  val selectedHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

  val maxBarValue = remember(summary) {
    val rawMax = summary.days.maxOfOrNull { max(it.completedCount, it.remainingCount) } ?: 0
    max(4, ceil(rawMax.toFloat() / 2f).toInt() * 2)
  }

  Card(
    modifier = modifier.fillMaxWidth().testTag("weekly_progress_chart_card"),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.BarChart,
              contentDescription = "Weekly progress chart",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(7.dp).size(18.dp),
            )
          }
          Column {
            Text(
              text = "Weekly Progress",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = summary.weekRangeLabel,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(20.dp),
        ) {
          Text(
            text = "${summary.completionPercent}% Done",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      // Summary KPI Counters + Legend
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        WeeklyLegendChip(
          color = completedColor,
          count = summary.totalCompleted,
          label = "Completed",
          modifier = Modifier.weight(1f),
        )
        WeeklyLegendChip(
          color = remainingColor,
          count = summary.totalRemaining,
          label = "Remaining",
          modifier = Modifier.weight(1f),
        )
      }

      // Cartesian Grouped Bar Chart (Recharts-inspired)
      val density = LocalDensity.current
      val tickTextSizePx = with(density) { 10.sp.toPx() }
      val tickColorArgb = labelColor.toArgb()

      val animatedCompleted =
        summary.days.mapIndexed { idx, day ->
          val target = day.completedCount.toFloat() / maxBarValue.toFloat()
          animateFloatAsState(
            targetValue = target,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bar_completed_$idx",
          ).value
        }

      val animatedRemaining =
        summary.days.mapIndexed { idx, day ->
          val target = day.remainingCount.toFloat() / maxBarValue.toFloat()
          animateFloatAsState(
            targetValue = target,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bar_remaining_$idx",
          ).value
        }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(176.dp)
            .padding(top = 4.dp),
      ) {
        // Draw Cartesian gridlines, Y-axis labels, and grouped vertical bars
        Canvas(
          modifier =
            Modifier.fillMaxSize()
              .padding(bottom = 26.dp),
        ) {
          val leftAxisWidth = 26.dp.toPx()
          val chartWidth = (size.width - leftAxisWidth).coerceAtLeast(1f)
          val chartHeight = (size.height - 8.dp.toPx()).coerceAtLeast(1f)
          val topPadding = 6.dp.toPx()

          val tickPaint =
            android.graphics.Paint().apply {
              isAntiAlias = true
              color = tickColorArgb
              textSize = tickTextSizePx
              textAlign = android.graphics.Paint.Align.RIGHT
            }

          val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
          val steps = 2
          for (i in 0..steps) {
            val fraction = i.toFloat() / steps.toFloat()
            val y = topPadding + chartHeight * (1f - fraction)
            val tickVal = (maxBarValue * fraction).toInt()

            drawLine(
              color = gridColor,
              start = Offset(leftAxisWidth, y),
              end = Offset(size.width, y),
              strokeWidth = if (i == 0) 1.5.dp.toPx() else 1.dp.toPx(),
              pathEffect = if (i == 0) null else dashEffect,
            )

            drawContext.canvas.nativeCanvas.drawText(
              tickVal.toString(),
              leftAxisWidth - 6.dp.toPx(),
              y + tickTextSizePx * 0.35f,
              tickPaint,
            )
          }

          val groupCount = 7
          val slotWidth = chartWidth / groupCount.toFloat()
          val barWidth = (slotWidth * 0.24f).coerceIn(8.dp.toPx(), 16.dp.toPx())
          val barGap = 4.dp.toPx()
          val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

          summary.days.forEachIndexed { idx, _ ->
            val slotLeft = leftAxisWidth + idx * slotWidth
            val centerX = slotLeft + slotWidth / 2f

            if (idx == selectedDayIndex) {
              drawRoundRect(
                color = selectedHighlightColor,
                topLeft = Offset(slotLeft + 3.dp.toPx(), topPadding),
                size = Size(slotWidth - 6.dp.toPx(), chartHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
              )
            }

            val compHeight = (animatedCompleted[idx] * chartHeight).coerceAtLeast(
              if (summary.days[idx].completedCount > 0) 4.dp.toPx() else 0f
            )
            if (compHeight > 0f) {
              drawRoundRect(
                color = completedColor,
                topLeft = Offset(centerX - barGap / 2f - barWidth, topPadding + chartHeight - compHeight),
                size = Size(barWidth, compHeight),
                cornerRadius = radius,
              )
            }

            val remHeight = (animatedRemaining[idx] * chartHeight).coerceAtLeast(
              if (summary.days[idx].remainingCount > 0) 4.dp.toPx() else 0f
            )
            if (remHeight > 0f) {
              drawRoundRect(
                color = remainingColor,
                topLeft = Offset(centerX + barGap / 2f, topPadding + chartHeight - remHeight),
                size = Size(barWidth, remHeight),
                cornerRadius = radius,
              )
            }
          }
        }

        // Interactive tap columns + X-axis day labels
        Row(
          modifier =
            Modifier.fillMaxSize()
              .padding(start = 26.dp),
        ) {
          summary.days.forEachIndexed { idx, day ->
            val isSelected = idx == selectedDayIndex
            Box(
              modifier =
                Modifier.weight(1f)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { selectedDayIndex = idx }
                  .testTag("chart_day_column_$idx"),
              contentAlignment = Alignment.BottomCenter,
            ) {
              Surface(
                color =
                  when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else -> Color.Transparent
                  },
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = day.dayLabel,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                  color =
                    when {
                      isSelected -> MaterialTheme.colorScheme.onPrimary
                      day.isToday -> MaterialTheme.colorScheme.primary
                      else -> labelColor
                    },
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
              }
            }
          }
        }
      }

      // Interactive Recharts-style Tooltip Callout for Selected Day
      val activeDay = summary.days.getOrNull(selectedDayIndex)
      if (activeDay != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("weekly_chart_tooltip"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (activeDay.isToday) "Today (${activeDay.dateLabel})"
                else "${activeDay.dayLabel}, ${activeDay.dateLabel}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "${activeDay.completedCount} completed • ${activeDay.remainingCount} due",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (summary.totalItems == 0) {
        Text(
          text = "No assignments or exams due this week yet — add an item to track your weekly progress.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun WeeklyLegendChip(
  color: Color,
  count: Int,
  label: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(10.dp)
            .clip(CircleShape)
            .background(color)
      )
      Column {
        Text(
          text = count.toString(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(2.dp))
    }
  }
}
