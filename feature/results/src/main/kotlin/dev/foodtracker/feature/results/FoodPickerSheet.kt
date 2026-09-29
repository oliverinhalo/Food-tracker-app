package dev.foodtracker.feature.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember

/**
 * "Change item": the escape hatch for when the recogniser is wrong, or when no database row could
 * be matched at all. Without this the user is stuck with whatever the AI guessed, which is the
 * single most likely way this app frustrates someone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodPickerSheet(
    state: FoodPickerState,
    onAction: (ResultsAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = { onAction(ResultsAction.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(ResultsTestTags.PICKER_SHEET),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Change item",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onAction(ResultsAction.ScanBarcode(state.itemId)) },
                    modifier = Modifier.testTag(ResultsTestTags.PICKER_SCAN),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(ResultsAction.PickerQueryChanged(it)) },
                label = { Text("Search foods and brands") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(ResultsTestTags.PICKER_SEARCH_FIELD),
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .testTag(ResultsTestTags.PICKER_RESULTS),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (state.showVariants) {
                    item { SectionLabel(state.variantQuestion ?: "What kind?") }
                    items(state.variants, key = { "variant-" + it.id }) { option ->
                        FoodOptionRow(option) { onAction(ResultsAction.SelectFood(option)) }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                if (state.showSuggestions) {
                    item { SectionLabel("The AI's other guesses") }
                    items(state.suggestions, key = { it.id }) { option ->
                        FoodOptionRow(option) { onAction(ResultsAction.SelectFood(option)) }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                if (state.showRecents) {
                    item { SectionLabel("You log these often") }
                    items(state.recents, key = { "recent-" + it.id }) { option ->
                        FoodOptionRow(option) { onAction(ResultsAction.SelectFood(option)) }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                if (state.showVariants || state.showSuggestions || state.showRecents) {
                    item { SectionLabel("From the food databases") }
                }

                items(state.results, key = { it.id }) { option ->
                    FoodOptionRow(option) { onAction(ResultsAction.SelectFood(option)) }
                }
            }
        }
    }

    // Opening the picker means the guess was wrong, so the keyboard should already be waiting.
    LaunchedEffect(state.itemId) {
        if (state.query.isBlank() && state.suggestions.isEmpty()) {
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FoodOptionRow(option: FoodOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics { contentDescription = "${option.name}. ${option.subtitle}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (option.origin) {
                FoodOption.Origin.AI_SUGGESTION -> Icons.Default.AutoAwesome
                FoodOption.Origin.VARIANT -> Icons.Default.Tune
                FoodOption.Origin.BARCODE -> Icons.Default.QrCodeScanner
                FoodOption.Origin.DATABASE -> Icons.Default.Restaurant
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.origin != FoodOption.Origin.AI_SUGGESTION && option.origin != FoodOption.Origin.VARIANT) {
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
