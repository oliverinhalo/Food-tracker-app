package dev.foodtracker.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setHome(
        state: HomeUiState,
        onAddManually: () -> Unit = {},
        onEditMeal: (String) -> Unit = {},
        onQuickAdd: (QuickAddFood) -> Unit = {},
    ) {
        composeRule.setContent {
            FoodTrackerTheme(dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onAddManually = onAddManually,
                    onEditMeal = onEditMeal,
                    onQuickAdd = onQuickAdd,
                    onDismissQuickAddResult = {},
                )
            }
        }
    }

    private fun meal(id: String = "m1", calories: Int = 520) = MealSummary(
        id = id,
        mealType = MealType.LUNCH,
        itemNames = listOf("Chicken breast", "Rice"),
        calories = calories,
    )

    @Test
    fun todaysProgressIsShown() {
        setHome(
            HomeUiState(
                calorieGoal = 2000,
                consumedNutrients = Nutrients(520.0, 40.0, 60.0, 12.0),
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.CALORIE_RING).assertIsDisplayed()
        composeRule.onNodeWithText("520").assertIsDisplayed()
        composeRule.onNodeWithText("of 2000 kcal").assertIsDisplayed()
    }

    @Test
    fun anUntouchedDayExplainsWhatToDo() {
        setHome(HomeUiState())

        composeRule.onNodeWithTag(HomeTestTags.EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing logged yet").assertIsDisplayed()
    }

    @Test
    fun loggedMealsAreListedWithTheirCalories() {
        setHome(HomeUiState(meals = listOf(meal())))

        composeRule.onNodeWithTag(HomeTestTags.MEAL_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Chicken breast, Rice").assertIsDisplayed()
        composeRule.onNodeWithText("520 kcal").assertIsDisplayed()
    }

    @Test
    fun tappingAMealOpensItForEditing() {
        var edited: String? = null
        setHome(HomeUiState(meals = listOf(meal(id = "meal-7"))), onEditMeal = { edited = it })

        composeRule.onNodeWithText("Chicken breast, Rice").performClick()

        assert(edited == "meal-7") { "expected the meal id to be reported, got $edited" }
    }

    @Test
    fun addingFoodByHandIsAlwaysAvailable() {
        var tapped = false
        setHome(HomeUiState(), onAddManually = { tapped = true })

        composeRule.onNodeWithTag(HomeTestTags.ADD_MANUALLY).performClick()

        assert(tapped) { "expected the manual add action to fire" }
    }

    @Test
    fun frequentFoodsLogInOneTap() {
        val porridge = QuickAddFood("porridge", "Porridge", null, "usda:1")
        var logged: QuickAddFood? = null

        setHome(HomeUiState(quickAdd = listOf(porridge)), onQuickAdd = { logged = it })

        composeRule.onNodeWithTag(HomeTestTags.QUICK_ADD).assertIsDisplayed()
        composeRule.onNodeWithText("Porridge").performClick()

        assert(logged == porridge) { "expected the quick add to fire with the food, got $logged" }
    }

    @Test
    fun aQuickAddConfirmsItself() {
        setHome(
            HomeUiState(
                quickAdd = listOf(QuickAddFood("porridge", "Porridge", null, null)),
                quickAddResult = QuickAddResult.Logged("Logged Porridge"),
            ),
        )

        composeRule.onNodeWithText("Logged Porridge").assertIsDisplayed()
    }

    @Test
    fun aFailedQuickAddSaysSoRatherThanLookingLikeSuccess() {
        setHome(
            HomeUiState(
                quickAdd = listOf(QuickAddFood("porridge", "Porridge", null, null)),
                quickAddResult = QuickAddResult.Failed("Couldn't find Porridge."),
            ),
        )

        composeRule.onNodeWithText("Couldn't find Porridge.").assertIsDisplayed()
    }
}
