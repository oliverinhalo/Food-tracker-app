package dev.foodtracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoMeals
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import dev.foodtracker.core.ui.component.CalorieRing
import dev.foodtracker.core.ui.component.MacroBar
import dev.foodtracker.core.ui.component.MessageState

object HomeTestTags {
    const val CALORIE_RING = "home_calorie_ring"
    const val EMPTY_STATE = "home_empty_state"
    const val MEAL_LIST = "home_meal_list"
}

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state, modifier = modifier)
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
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
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )

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
                state.meals.forEach { meal -> MealRow(meal) }
            }
        }
    }
}

@Composable
private fun MealRow(meal: MealSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
