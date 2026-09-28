package dev.foodtracker.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily calories against the goal.
 *
 * Bars rather than a line: days are discrete and often missing, and a line would invent a trend
 * across days with nothing logged. The goal is a dashed rule so over and under are readable at a
 * glance without needing the axis.
 */
@Composable
internal fun TrendChart(
    points: List<DayPoint>,
    goal: Int,
    maxCalories: Int,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedOutline = MaterialTheme.colorScheme.onSurface
    val goalColor = MaterialTheme.colorScheme.outline
    val ceiling = maxOf(maxCalories, goal, 1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag(HistoryTestTags.CHART),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val isSelected = point.date == selectedDate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp)
                        .clickable { onSelectDate(point.date) }
                        .semantics {
                            contentDescription = buildString {
                                append(point.date.format(SPOKEN_DATE))
                                append(": ")
                                append(if (point.hasData) "${point.calories} calories" else "nothing logged")
                                if (isSelected) append(", selected")
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val goalY = size.height * (1f - goal.toFloat() / ceiling)
                        val barHeight = size.height * (point.calories.toFloat() / ceiling)

                        if (point.hasData) {
                            drawRect(
                                color = if (point.calories > goal) overColor else barColor,
                                topLeft = Offset(0f, size.height - barHeight),
                                size = Size(size.width, barHeight),
                            )
                        } else {
                            // A thin stub marks the day as present but empty, so gaps are visible.
                            drawRect(
                                color = emptyColor,
                                topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                size = Size(size.width, 3.dp.toPx()),
                            )
                        }

                        if (isSelected) {
                            drawRect(
                                color = selectedOutline,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }

                        drawLine(
                            color = goalColor,
                            start = Offset(0f, goalY),
                            end = Offset(size.width, goalY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            points.forEach { point ->
                Text(
                    text = point.date.format(AXIS_LABEL),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    // Labelled by the bar above; repeating it would double every announcement.
                    modifier = Modifier.weight(1f).semantics { contentDescription = "" },
                )
            }
        }

        Text(
            text = "Dashed line is your $goal kcal goal",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val AXIS_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d")
private val SPOKEN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

