package dev.foodtracker.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Progress toward the daily calorie goal. Overshoot is drawn as a second, darker sweep on top of a
 * full ring rather than clamping at 100%, so going over is visible at a glance instead of looking
 * identical to hitting the goal exactly.
 */
@Composable
fun CalorieRing(
    consumed: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit = {},
) {
    val rawFraction = if (goal <= 0) 0f else consumed.toFloat() / goal.toFloat()
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 700),
        label = "calorieRingFraction",
    )
    val overshoot by animateFloatAsState(
        targetValue = (rawFraction - 1f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "calorieRingOvershoot",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val overshootColor = MaterialTheme.colorScheme.error

    val remaining = goal - consumed
    val description = when {
        goal <= 0 -> "$consumed calories logged"
        remaining >= 0 -> "$consumed of $goal calories, $remaining remaining"
        else -> "$consumed of $goal calories, ${-remaining} over goal"
    }

    Box(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            if (fraction > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = START_ANGLE,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
            if (overshoot > 0f) {
                drawArc(
                    color = overshootColor,
                    startAngle = START_ANGLE,
                    sweepAngle = 360f * overshoot,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}

/** 12 o'clock. Canvas angles start at 3 o'clock, so the ring is rotated a quarter turn back. */
private const val START_ANGLE = -90f
