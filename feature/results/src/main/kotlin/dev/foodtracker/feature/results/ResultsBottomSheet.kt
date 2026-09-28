package dev.foodtracker.feature.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.camera.BarcodeScannerDialog
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.ui.component.MacroBar
import dev.foodtracker.core.ui.component.MessageState
import dev.foodtracker.core.ui.component.SkeletonBlock
import dev.foodtracker.core.ui.component.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsBottomSheet(
    state: ResultsUiState,
    onAction: (ResultsAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBarcodeScanned: (String) -> Unit = {},
    onBarcodeScanCancelled: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
) {
    val haptics = rememberHaptics()

    LaunchedEffect(state.loggedSuccessfully) {
        if (state.loggedSuccessfully) {
            haptics.confirm()
            onDismiss()
        }
    }

    state.picker?.let { picker ->
        FoodPickerSheet(state = picker, onAction = onAction)

        if (picker.isScanning) {
            BarcodeScannerDialog(
                onBarcode = onBarcodeScanned,
                onDismiss = onBarcodeScanCancelled,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(ResultsTestTags.SHEET),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            TotalsHeader(state = state)

            HorizontalDivider()

            when {
                state.phase == AnalysisPhase.FAILED && state.items.isEmpty() -> {
                    MessageState(
                        icon = Icons.Default.ErrorOutline,
                        title = "Couldn't read that meal",
                        body = state.errorMessage,
                        actionLabel = "Try again",
                        onAction = { onAction(ResultsAction.Retry) },
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                state.phase == AnalysisPhase.ANALYZING && state.items.isEmpty() -> {
                    AnalyzingSkeleton(state.stage)
                }

                else -> {
                    ItemList(state = state, onAction = onAction)
                }
            }

            if (state.items.isNotEmpty()) {
                MealTypeRow(
                    selected = state.mealType,
                    onSelect = { onAction(ResultsAction.ChangeMealType(it)) },
                )

                Button(
                    onClick = { onAction(ResultsAction.Confirm) },
                    enabled = state.canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag(ResultsTestTags.CONFIRM),
                ) {
                    Text(if (state.isLogging) "Logging…" else "Log ${state.items.size} item${if (state.items.size == 1) "" else "s"}")
                }
            }
        }
    }
}

@Composable
private fun TotalsHeader(state: ResultsUiState) {
    val totals = state.totals

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "This meal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasResolvedNutrition) {
                    Text(
                        text = "${ResultsFormatting.calories(totals.calories)} kcal",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .testTag(ResultsTestTags.TOTALS_CALORIES)
                            // Totals change as the user drags a slider; announcing politely keeps
                            // TalkBack useful without interrupting every frame.
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f), height = 32.dp)
                }
            }

            if (state.isBusy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .testTag(ResultsTestTags.STAGE_LABEL)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.stage.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.hasResolvedNutrition) {
            MacroBar(nutrients = totals, height = 8.dp)
            MacroLegend(state = state)
        }

        state.degradeReason?.let { reason ->
            DegradeBanner(message = reason.bannerMessage(), isActionable = reason.isUserActionable)
        }

        if (!state.isBusy && state.unresolvedCount > 0) {
            DegradeBanner(
                message = "No nutrition data for ${state.unresolvedCount} item" +
                    (if (state.unresolvedCount == 1) "" else "s") +
                    ". Tap the item and pick a different name, or log it without calories.",
                isActionable = true,
            )
        }
    }
}

@Composable
private fun MacroLegend(state: ResultsUiState) {
    val totals = state.totals
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MacroLegendItem("Protein", totals.proteinGrams)
        MacroLegendItem("Carbs", totals.carbsGrams)
        MacroLegendItem("Fat", totals.fatGrams)
    }
}

@Composable
private fun MacroLegendItem(label: String, grams: Double) {
    Text(
        text = "$label ${ResultsFormatting.grams(grams)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DegradeBanner(message: String, isActionable: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultsTestTags.DEGRADE_BANNER),
        colors = CardDefaults.cardColors(
            containerColor = if (isActionable) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isActionable) Icons.Default.ErrorOutline else Icons.Default.Info,
                contentDescription = null,
            )
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnalyzingSkeleton(stage: AnalysisStage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag(ResultsTestTags.ANALYZING)
            .semantics { contentDescription = stage.label.ifBlank { "Analysing your meal" } },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text = stage.label, style = MaterialTheme.typography.bodyMedium)
        }
        repeat(3) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 72.dp, cornerRadius = 12.dp)
        }
    }
}

@Composable
private fun ItemList(state: ResultsUiState, onAction: (ResultsAction) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultsTestTags.ITEM_LIST),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = state.items, key = { it.id }) { item ->
            SwipeToDeleteItem(
                item = item,
                onDelete = { onAction(ResultsAction.RemoveItem(item.id)) },
            ) {
                FoodItemCard(item = item, onAction = onAction)
            }
        }

        item {
            OutlinedButton(
                onClick = { onAction(ResultsAction.AddEmptyItem) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResultsTestTags.ADD_ITEM),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Add missed item")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTypeRow(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(ResultsTestTags.MEAL_TYPE_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.displayName) },
            )
        }
    }
}

internal val MealType.displayName: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    item: DetectedItem,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        // A short swipe deletes by accident on a list you are actively editing; require most of
        // the card's width to cross before it counts.
        positionalThreshold = { distance -> distance * 0.55f },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            haptics.confirm()
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DeleteBackground(dismissState.dismissDirection) },
        content = { content() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteBackground(direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
