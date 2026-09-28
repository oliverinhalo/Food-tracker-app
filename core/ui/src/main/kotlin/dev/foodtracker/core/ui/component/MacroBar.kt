package dev.foodtracker.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import kotlin.math.roundToInt

/**
 * Protein / carbs / fat split as a single bar. Widths are by *calorie* contribution, not grams:
 * a gram of fat carries more than twice the energy of a gram of protein, so a gram-proportional
 * bar misrepresents what the meal actually is.
 */
@Composable
fun MacroBar(
    nutrients: Nutrients,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val proteinKcal = (nutrients.proteinGrams * 4).toFloat()
    val carbsKcal = (nutrients.carbsGrams * 4).toFloat()
    val fatKcal = (nutrients.fatGrams * 9).toFloat()
    val total = proteinKcal + carbsKcal + fatKcal

    val palette = FoodTrackerTheme.macros
    val proteinWeight by animateFloatAsState(
        targetValue = if (total <= 0f) 0f else proteinKcal / total,
        label = "proteinWeight",
    )
    val carbsWeight by animateFloatAsState(
        targetValue = if (total <= 0f) 0f else carbsKcal / total,
        label = "carbsWeight",
    )
    val fatWeight by animateFloatAsState(
        targetValue = if (total <= 0f) 0f else fatKcal / total,
        label = "fatWeight",
    )

    val description = if (total <= 0f) {
        "Macro breakdown unavailable"
    } else {
        "Protein ${(proteinWeight * 100).roundToInt()} percent, " +
            "carbohydrates ${(carbsWeight * 100).roundToInt()} percent, " +
            "fat ${(fatWeight * 100).roundToInt()} percent of calories"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        if (total <= 0f) return@Row
        if (proteinWeight > 0f) Segment(proteinWeight, palette.protein)
        if (carbsWeight > 0f) Segment(carbsWeight, palette.carbs)
        if (fatWeight > 0f) Segment(fatWeight, palette.fat)
    }
}

@Composable
private fun RowScope.Segment(weight: Float, color: Color) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(color),
    )
}
