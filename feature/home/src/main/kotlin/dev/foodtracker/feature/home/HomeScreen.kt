package dev.foodtracker.feature.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NoMeals
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foodtracker.core.ui.component.CalorieRing
import dev.foodtracker.core.ui.component.MacroBar
import kotlinx.coroutines.delay
import java.io.File
import dev.foodtracker.core.ui.component.rememberHaptics
import dev.foodtracker.core.ui.component.MessageState

object HomeTestTags {
    const val CALORIE_RING = "home_calorie_ring"
    const val EMPTY_STATE = "home_empty_state"
    const val MEAL_LIST = "home_meal_list"
    const val ADD_MANUALLY = "home_add_manually"
    const val QUICK_ADD = "home_quick_add"

    fun deleteMeal(id: String) = "home_delete_meal_$id"
}

@Composable
fun HomeRoute(
    onAddManually: () -> Unit,
    onEditMeal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onAddManually = onAddManually,
        onEditMeal = onEditMeal,
        onDeleteMeal = viewModel::deleteMeal,
        onQuickAdd = viewModel::quickAdd,
        onDismissQuickAddResult = viewModel::dismissQuickAddResult,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onAddManually: () -> Unit,
    onEditMeal: (String) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onQuickAdd: (QuickAddFood) -> Unit,
    onDismissQuickAddResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Today", style = MaterialTheme.typography.headlineSmall)
            // Not everything can be photographed: a coffee already drunk, a packet in a bag.
            TextButton(
                onClick = onAddManually,
                modifier = Modifier.testTag(HomeTestTags.ADD_MANUALLY),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add food")
            }
        }

        CalorieRing(
            consumed = state.consumedCalories,
            goal = state.calorieGoal,
            modifier = Modifier.testTag(HomeTestTags.CALORIE_RING),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.consumedCalories.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "of ${state.calorieGoal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Macros", style = MaterialTheme.typography.titleSmall)
                MacroBar(nutrients = state.consumedNutrients, height = 8.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MacroSummary("Protein", state.consumedNutrients.proteinGrams, state.proteinGoal)
                    MacroSummary("Carbs", state.consumedNutrients.carbsGrams, state.carbsGoal)
                    MacroSummary("Fat", state.consumedNutrients.fatGrams, state.fatGoal)
                }
            }
        }

        if (state.quickAdd.isNotEmpty()) {
            QuickAddRow(
                foods = state.quickAdd,
                result = state.quickAddResult,
                onQuickAdd = onQuickAdd,
                onDismissResult = onDismissQuickAddResult,
            )
        }

        if (state.meals.isEmpty()) {
            MessageState(
                icon = Icons.Default.NoMeals,
                title = "Nothing logged yet",
                body = "Tap the camera button to scan your first meal of the day.",
                modifier = Modifier.testTag(HomeTestTags.EMPTY_STATE),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.MEAL_LIST),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Meals", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
                state.meals.forEach { meal ->
                    MealRow(
                        meal = meal,
                        onClick = { onEditMeal(meal.id) },
                        onDelete = { onDeleteMeal(meal.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealRow(meal: MealSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${meal.mealType.name.lowercase()}, ${meal.calories} calories. Tap to edit." },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MealThumbnail(meal.photoPath)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = meal.itemNames.joinToString(", ").ifBlank { "No items" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${meal.calories} kcal", style = MaterialTheme.typography.titleSmall)
            // Deleting was only possible from History, which is an odd place to look for a meal
            // you logged a minute ago.
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(HomeTestTags.deleteMeal(meal.id)),
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete this meal")
            }
        }
    }
}

/**
 * The meal's own photo, which turns the list into a visual diary. Decorative rather than described:
 * the meal type and its foods are already announced by the row.
 */
@Composable
private fun MealThumbnail(path: String?) {
    if (path == null) return

    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

/**
 * One tap to log a food you eat often. The whole point is that it takes no decisions: no portion
 * dialog, no search, just the thing you had yesterday at the size you had it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddRow(
    foods: List<QuickAddFood>,
    result: QuickAddResult?,
    onQuickAdd: (QuickAddFood) -> Unit,
    onDismissResult: () -> Unit,
) {
    val haptics = rememberHaptics()

    // Confirmation clears itself; a tap that produced no visible change reads as a broken button.
    LaunchedEffect(result) {
        if (result != null) {
            // A failure needs longer: it asks the reader to go and do something about it.
            delay(if (result is QuickAddResult.Failed) 5_000 else 2_500)
            onDismissResult()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.QUICK_ADD),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = result?.message ?: "Log again",
            style = MaterialTheme.typography.titleSmall,
            color = when (result) {
                is QuickAddResult.Logged -> MaterialTheme.colorScheme.primary
                is QuickAddResult.Failed -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            foods.forEach { food ->
                AssistChip(
                    onClick = {
                        haptics.confirm()
                        onQuickAdd(food)
                    },
                    label = {
                        Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.semantics {
                        contentDescription = "Log ${food.name} again"
                    },
                )
            }
        }
    }
}

@Composable
private fun MacroSummary(label: String, consumed: Double, goal: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = "${consumed.toInt()} / $goal g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
