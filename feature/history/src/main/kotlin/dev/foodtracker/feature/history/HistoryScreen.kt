package dev.foodtracker.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foodtracker.core.ui.component.MessageState
import dev.foodtracker.data.diary.LoggedMeal
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object HistoryTestTags {
    const val CHART = "history_chart"
    const val RANGE_ROW = "history_range_row"
    const val DAY_MEALS = "history_day_meals"
    const val EMPTY = "history_empty"
}

@Composable
fun HistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreen(
        state = state,
        onSelectRange = viewModel::selectRange,
        onSelectDate = viewModel::selectDate,
        onDeleteMeal = viewModel::deleteMeal,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    onSelectRange: (HistoryRange) -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onDeleteMeal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.headlineSmall)

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().testTag(HistoryTestTags.RANGE_ROW),
        ) {
            HistoryRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = state.range == range,
                    onClick = { onSelectRange(range) },
                    shape = SegmentedButtonDefaults.itemShape(index, HistoryRange.entries.size),
                ) {
                    Text(range.label)
                }
            }
        }

        if (state.daysLogged == 0 && !state.isLoading) {
            MessageState(
                icon = Icons.Default.InsertChartOutlined,
                title = "No history yet",
                body = "Log a few meals and your daily totals and trends will show up here.",
                modifier = Modifier.testTag(HistoryTestTags.EMPTY),
            )
            return@Column
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat("Average", "${state.averageCalories} kcal")
                    Stat("Days logged", state.daysLogged.toString())
                    Stat("On target", "${state.daysOnTarget}/${state.daysLogged}")
                }

                TrendChart(
                    points = state.points,
                    goal = state.goal,
                    maxCalories = state.maxCalories,
                    selectedDate = state.selectedDate,
                    onSelectDate = onSelectDate,
                )
            }
        }

        Text(
            text = state.selectedDate.format(DAY_HEADING),
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.selectedMeals.isEmpty()) {
            Text(
                text = "Nothing logged on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().testTag(HistoryTestTags.DAY_MEALS),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selectedMeals.forEach { meal ->
                    MealRow(meal = meal, onDelete = { onDeleteMeal(meal.id) })
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MealRow(meal: LoggedMeal, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = meal.items.joinToString(", ") { it.name }.ifBlank { "No items" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${meal.totals.calories.roundToInt()} kcal",
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete this meal",
                )
            }
        }
    }
}

private val DAY_HEADING: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
