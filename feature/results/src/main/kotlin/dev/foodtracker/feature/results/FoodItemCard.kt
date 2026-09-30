package dev.foodtracker.feature.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.ui.component.MacroBar
import dev.foodtracker.core.ui.component.SkeletonBlock

/**
 * One detected food. Collapsed it is a scannable summary; expanded it exposes the editing controls.
 * Starting collapsed matters: the common case is "the AI got it right, log it", and a sheet of
 * expanded editors buries the confirm button.
 */
@Composable
internal fun FoodItemCard(
    item: DetectedItem,
    onAction: (ResultsAction) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    imperial: Boolean = false,
) {
    var expanded by remember(item.id) { mutableStateOf(initiallyExpanded) }
    val nutrients = item.nutrients

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ResultsTestTags.itemCard(item.id))
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription = buildString {
                            append(item.name.ifBlank { "Unnamed item" })
                            append(", ")
                            append(ResultsFormatting.portionLabel(item.portion.amount, item.portion.unit))
                            if (nutrients != null) {
                                append(", ${ResultsFormatting.calories(nutrients.calories)} calories")
                            }
                            append(if (expanded) ". Tap to collapse." else ". Tap to edit.")
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name.ifBlank { "Tap to name this item" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = portionSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (nutrients != null) {
                    Text(
                        text = "${ResultsFormatting.calories(nutrients.calories)} kcal",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    // Nutrition lookup is still in flight (phase 2 wires the real sources).
                    SkeletonBlock(modifier = Modifier.width(64.dp), height = 20.dp)
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            if (nutrients != null) {
                MacroBar(nutrients = nutrients)
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuantityEditor(
                        itemId = item.id,
                        amount = item.portion.amount,
                        unit = item.portion.unit,
                        onAmountChange = { onAction(ResultsAction.ChangeQuantity(item.id, it)) },
                        onUnitChange = { onAction(ResultsAction.ChangeUnit(item.id, it)) },
                        imperial = imperial,
                    )

                    TextButton(
                        onClick = { onAction(ResultsAction.OpenPicker(item.id)) },
                        modifier = Modifier
                            .testTag(ResultsTestTags.itemAlternatives(item.id))
                            .semantics {
                                contentDescription = if (item.alternatives.isEmpty()) {
                                    "Change item. Search the food databases."
                                } else {
                                    "Change item. ${item.alternatives.size} suggestions, or search."
                                }
                            },
                    ) {
                        Text(
                            when {
                                item.nutrientsPer100g == null -> "Find this food"
                                item.variantQuestion != null -> item.variantQuestion!!
                                else -> "Change item"
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun portionSubtitle(item: DetectedItem): String {
    val portion = ResultsFormatting.portionLabel(item.portion.amount, item.portion.unit)
    val household = item.portion.householdDescription
    val cooking = item.cookingMethod

    return buildString {
        append(portion)
        if (item.portion.unit != MeasurementUnit.GRAM) {
            append(" (${ResultsFormatting.grams(item.portion.grams)})")
        }
        if (!household.isNullOrBlank()) append(" · $household")
        if (!cooking.isNullOrBlank()) append(" · $cooking")
    }
}
