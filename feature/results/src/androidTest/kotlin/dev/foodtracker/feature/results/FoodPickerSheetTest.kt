package dev.foodtracker.feature.results

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import org.junit.Rule
import org.junit.Test

/**
 * The picker is the app's escape hatch: when the recogniser is wrong, or nothing resolved, this is
 * the only way forward. These cover that it opens, searches, and applies a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
class FoodPickerSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun option(id: String, name: String, kcal: Int = 120) = FoodOption(
        id = id,
        name = name,
        brand = null,
        caloriesPer100g = kcal,
        origin = FoodOption.Origin.DATABASE,
    )

    private fun setPicker(
        state: FoodPickerState,
        onAction: (ResultsAction) -> Unit = {},
    ) {
        composeRule.setContent {
            FoodTrackerTheme(dynamicColor = false) {
                FoodPickerSheet(state = state, onAction = onAction)
            }
        }
    }

    @Test
    fun searchResultsAreListed() {
        setPicker(
            FoodPickerState(
                itemId = "1",
                itemName = "rice",
                query = "rice",
                results = listOf(option("a", "Rice, white, cooked"), option("b", "Brown rice, cooked")),
            ),
        )

        composeRule.onNodeWithText("Rice, white, cooked").assertIsDisplayed()
        composeRule.onNodeWithText("Brown rice, cooked").assertIsDisplayed()
    }

    @Test
    fun typingRaisesASearch() {
        var lastQuery: String? = null
        setPicker(
            state = FoodPickerState(itemId = "1", itemName = ""),
            onAction = { if (it is ResultsAction.PickerQueryChanged) lastQuery = it.query },
        )

        composeRule.onNodeWithTag(ResultsTestTags.PICKER_SEARCH_FIELD).performTextInput("oats")

        assert(lastQuery == "oats") { "expected the query to reach the ViewModel, got $lastQuery" }
    }

    @Test
    fun choosingAResultAppliesIt() {
        var chosen: FoodOption? = null
        val chicken = option("a", "Chicken breast, grilled", kcal = 165)

        setPicker(
            state = FoodPickerState(itemId = "1", itemName = "chicken", query = "chicken", results = listOf(chicken)),
            onAction = { if (it is ResultsAction.SelectFood) chosen = it.option },
        )

        composeRule.onNodeWithText("Chicken breast, grilled").performClick()

        assert(chosen == chicken) { "expected the chosen option to be reported, got $chosen" }
    }

    @Test
    fun theAiGuessesAreOfferedBeforeTheUserTypes() {
        setPicker(
            FoodPickerState(
                itemId = "1",
                itemName = "chicken",
                suggestions = listOf(
                    FoodOption("ai:turkey", "Turkey breast", null, 0, FoodOption.Origin.AI_SUGGESTION),
                ),
            ),
        )

        composeRule.onNodeWithText("The AI's other guesses").assertIsDisplayed()
        composeRule.onNodeWithText("Turkey breast").assertIsDisplayed()
    }

    @Test
    fun frequentFoodsAreOfferedBeforeTheUserTypes() {
        setPicker(
            FoodPickerState(
                itemId = "1",
                itemName = "",
                recents = listOf(option("r1", "Porridge with milk")),
            ),
        )

        composeRule.onNodeWithText("You log these often").assertIsDisplayed()
        composeRule.onNodeWithText("Porridge with milk").assertIsDisplayed()
    }

    @Test
    fun suggestionsGiveWayOnceTheUserTypes() {
        setPicker(
            FoodPickerState(
                itemId = "1",
                itemName = "chicken",
                query = "tofu",
                suggestions = listOf(
                    FoodOption("ai:turkey", "Turkey breast", null, 0, FoodOption.Origin.AI_SUGGESTION),
                ),
                recents = listOf(option("r1", "Porridge with milk")),
                results = listOf(option("a", "Tofu, firm")),
            ),
        )

        composeRule.onNodeWithText("Tofu, firm").assertIsDisplayed()
        composeRule.onNodeWithText("Turkey breast").assertDoesNotExist()
        composeRule.onNodeWithText("Porridge with milk").assertDoesNotExist()
    }

    @Test
    fun anEmptySearchExplainsItself() {
        setPicker(
            FoodPickerState(
                itemId = "1",
                itemName = "zzz",
                query = "zzz",
                message = "No matches. Try a simpler name.",
            ),
        )

        composeRule.onNodeWithText("No matches. Try a simpler name.").assertIsDisplayed()
    }

    @Test
    fun scanningIsOffered() {
        var scanned = false
        setPicker(
            state = FoodPickerState(itemId = "1", itemName = "yoghurt"),
            onAction = { if (it is ResultsAction.ScanBarcode) scanned = true },
        )

        composeRule.onNodeWithTag(ResultsTestTags.PICKER_SCAN).performClick()

        assert(scanned) { "expected the scan action to be raised" }
    }
}
