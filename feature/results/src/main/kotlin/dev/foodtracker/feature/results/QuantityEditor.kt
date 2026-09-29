package dev.foodtracker.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.ui.component.rememberHaptics
import kotlin.math.round

/**
 * Stepper + slider + unit picker for one item.
 *
 * The stepper is the precise control and the slider the coarse one; they edit the same value, so
 * the slider snaps to the unit's step to stop a drag producing "137.42 g". Every change fires a
 * light haptic, which is what makes dragging feel physical rather than laggy.
 */
@Composable
internal fun QuantityEditor(
    itemId: String,
    amount: Double,
    unit: MeasurementUnit,
    onAmountChange: (Double) -> Unit,
    onUnitChange: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val step = PortionControls.stepFor(unit)
    val sliderMax = PortionControls.sliderMaxFor(unit)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptics.tick()
                    onAmountChange((amount - step).coerceAtLeast(0.0))
                },
                enabled = amount > 0.0,
                modifier = Modifier
                    .size(MIN_TOUCH_TARGET)
                    .testTag(ResultsTestTags.itemDecrement(itemId))
                    .semantics { contentDescription = "Decrease amount" },
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }

            Text(
                text = ResultsFormatting.amount(amount, unit),
                style = MaterialTheme.typography.titleMedium,
                // A minimum rather than a fixed width: at large system font scales a fixed one
                // clips four-digit amounts.
                modifier = Modifier.widthIn(min = 56.dp),
            )

            FilledTonalIconButton(
                onClick = {
                    haptics.tick()
                    onAmountChange(amount + step)
                },
                modifier = Modifier
                    .size(MIN_TOUCH_TARGET)
                    .testTag(ResultsTestTags.itemIncrement(itemId))
                    .semantics { contentDescription = "Increase amount" },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }

            UnitPicker(
                itemId = itemId,
                unit = unit,
                onUnitChange = onUnitChange,
            )
        }

        Slider(
            value = amount.coerceIn(0.0, sliderMax).toFloat(),
            onValueChange = { raw ->
                val snapped = round(raw / step) * step
                onAmountChange(snapped.toDouble())
            },
            onValueChangeFinished = { haptics.tick() },
            valueRange = 0f..sliderMax.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ResultsTestTags.itemSlider(itemId))
                .semantics {
                    contentDescription = "Amount in ${unit.abbreviation}"
                },
        )
    }
}

@Composable
private fun UnitPicker(
    itemId: String,
    unit: MeasurementUnit,
    onUnitChange: (MeasurementUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = Modifier
            .testTag(ResultsTestTags.itemUnitPicker(itemId))
            .semantics { contentDescription = "Unit: ${unit.abbreviation}. Tap to change." },
    ) {
        Text(unit.abbreviation)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MeasurementUnit.pickerOrder.forEach { candidate ->
            DropdownMenuItem(
                text = { Text(candidate.abbreviation) },
                onClick = {
                    expanded = false
                    onUnitChange(candidate)
                },
            )
        }
    }
}

/** Android's minimum accessible touch target. */
private val MIN_TOUCH_TARGET = 48.dp
