package dev.foodtracker.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import dev.foodtracker.data.diary.LoggedFood
import dev.foodtracker.data.diary.LoggedMeal
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 3, 14)

    private fun day(offset: Long, calories: Int) = DayPoint(
        date = today.minusDays(offset),
        calories = calories,
        nutrients = Nutrients(calories.toDouble(), 0.0, 0.0, 0.0),
    )

    private fun meal(id: String = "m1") = LoggedMeal(
        id = id,
        mealType = MealType.DINNER,
        date = today,
        loggedAtMillis = 0,
        items = listOf(
            LoggedFood("i1", "Salmon", null, Portion.ofGrams(150.0), Nutrients(310.0, 34.0, 0.0, 19.0)),
        ),
    )

    private fun setHistory(
        state: HistoryUiState,
        onSelectRange: (HistoryRange) -> Unit = {},
        onSelectDate: (LocalDate) -> Unit = {},
        onDeleteMeal: (String) -> Unit = {},
        onEditMeal: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            FoodTrackerTheme(dynamicColor = false) {
                HistoryScreen(
                    state = state,
                    onSelectRange = onSelectRange,
                    onSelectDate = onSelectDate,
                    onDeleteMeal = onDeleteMeal,
                    onEditMeal = onEditMeal,
                )
            }
        }
    }

    @Test
    fun withNothingLoggedItSaysSoRatherThanDrawingAnEmptyChart() {
        setHistory(HistoryUiState(isLoading = false, selectedDate = today))

        composeRule.onNodeWithTag(HistoryTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No history yet").assertIsDisplayed()
    }

    @Test
    fun theTrendAndItsSummaryAreShown() {
        setHistory(
            HistoryUiState(
                isLoading = false,
                selectedDate = today,
                goal = 2000,
                points = listOf(day(2, 1800), day(1, 2200), day(0, 1900)),
            ),
        )

        composeRule.onNodeWithTag(HistoryTestTags.CHART).assertIsDisplayed()
        // Average over days with something logged: (1800 + 2200 + 1900) / 3.
        composeRule.onNodeWithText("1966 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun daysOnTargetCountOnlyThoseWithinTheGoal() {
        val state = HistoryUiState(
            isLoading = false,
            selectedDate = today,
            goal = 2000,
            points = listOf(day(2, 1800), day(1, 2200), day(0, 1900)),
        )

        assert(state.daysOnTarget == 2) { "expected 2 days on target, got ${state.daysOnTarget}" }
        assert(state.daysLogged == 3) { "expected 3 days logged, got ${state.daysLogged}" }
    }

    @Test
    fun anUntrackedDayDoesNotDragTheAverageDown() {
        val state = HistoryUiState(
            isLoading = false,
            goal = 2000,
            points = listOf(day(2, 2000), day(1, 0), day(0, 2000)),
        )

        // Averaging over the empty day would report 1333 and flatter the numbers.
        assert(state.averageCalories == 2000) { "expected 2000, got ${state.averageCalories}" }
        assert(state.daysLogged == 2) { "expected 2 days logged, got ${state.daysLogged}" }
    }

    @Test
    fun theRangeCanBeChanged() {
        var selected: HistoryRange? = null
        setHistory(
            state = HistoryUiState(isLoading = false, selectedDate = today, points = listOf(day(0, 1800))),
            onSelectRange = { selected = it },
        )

        composeRule.onNodeWithText("30 days").performClick()

        assert(selected == HistoryRange.MONTH) { "expected the month range, got $selected" }
    }

    @Test
    fun theSelectedDaysMealsAreListedAndCanBeOpened() {
        var edited: String? = null
        setHistory(
            state = HistoryUiState(
                isLoading = false,
                selectedDate = today,
                points = listOf(day(0, 310)),
                selectedMeals = listOf(meal(id = "meal-3")),
            ),
            onEditMeal = { edited = it },
        )

        composeRule.onNodeWithTag(HistoryTestTags.DAY_MEALS).assertIsDisplayed()
        composeRule.onNodeWithText("Salmon").performClick()

        assert(edited == "meal-3") { "expected the meal id, got $edited" }
    }

    @Test
    fun aMealCanBeDeleted() {
        var deleted: String? = null
        setHistory(
            state = HistoryUiState(
                isLoading = false,
                selectedDate = today,
                points = listOf(day(0, 310)),
                selectedMeals = listOf(meal(id = "meal-9")),
            ),
            onDeleteMeal = { deleted = it },
        )

        composeRule.onNodeWithContentDescription("Delete this meal").performClick()

        assert(deleted == "meal-9") { "expected the meal id, got $deleted" }
    }
}
