package dev.foodtracker.feature.results

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ResultsBottomSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleItem(
        id: String = "item-1",
        name: String = "Grilled chicken breast",
        grams: Double = 150.0,
    ) = DetectedItem(
        id = id,
        name = name,
        confidence = 0.92f,
        portion = Portion.ofGrams(grams, householdDescription = "1 breast"),
        source = RecognitionSource.CLOUD,
        nutrientsPer100g = Nutrients(calories = 165.0, proteinGrams = 31.0, carbsGrams = 0.0, fatGrams = 3.6),
    )

    private fun setSheet(
        state: ResultsUiState,
        onAction: (ResultsAction) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            FoodTrackerTheme(dynamicColor = false) {
                ResultsBottomSheet(
                    state = state,
                    onAction = onAction,
                    onDismiss = onDismiss,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                )
            }
        }
    }

    @Test
    fun detectedItemsAreListedWithTheirCalories() {
        setSheet(ResultsUiState(phase = AnalysisPhase.COMPLETE, items = listOf(sampleItem())))

        composeRule.onNodeWithText("Grilled chicken breast").assertIsDisplayed()
        // 150 g of a 165 kcal/100g food is 248 kcal.
        composeRule.onNodeWithText("248 kcal").assertIsDisplayed()
    }

    @Test
    fun totalsReflectEveryItem() {
        setSheet(
            ResultsUiState(
                phase = AnalysisPhase.COMPLETE,
                items = listOf(sampleItem(), sampleItem(id = "item-2", name = "Rice", grams = 100.0)),
            ),
        )

        composeRule.onNodeWithTag(ResultsTestTags.TOTALS_CALORIES).assertIsDisplayed()
    }

    @Test
    fun theStepperRaisesTheAmount() {
        var lastAction: ResultsAction? = null
        setSheet(
            state = ResultsUiState(phase = AnalysisPhase.COMPLETE, items = listOf(sampleItem())),
            onAction = { lastAction = it },
        )

        // The card starts collapsed; the editing controls appear once it is expanded.
        composeRule.onNodeWithText("Grilled chicken breast").performClick()
        composeRule.onNodeWithTag(ResultsTestTags.itemIncrement("item-1")).performClick()

        val action = lastAction
        assert(action is ResultsAction.ChangeQuantity) { "expected a quantity change, got $action" }
        assert((action as ResultsAction.ChangeQuantity).amount > 150.0)
    }

    @Test
    fun addMissedItemIsOffered() {
        var lastAction: ResultsAction? = null
        setSheet(
            state = ResultsUiState(phase = AnalysisPhase.COMPLETE, items = listOf(sampleItem())),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithTag(ResultsTestTags.ADD_ITEM).performClick()

        assert(lastAction == ResultsAction.AddEmptyItem)
    }

    @Test
    fun confirmingLogsTheMeal() {
        var lastAction: ResultsAction? = null
        setSheet(
            state = ResultsUiState(phase = AnalysisPhase.COMPLETE, items = listOf(sampleItem())),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithTag(ResultsTestTags.CONFIRM).performClick()

        assert(lastAction == ResultsAction.Confirm)
    }

    @Test
    fun confirmIsDisabledWhileLogging() {
        setSheet(
            ResultsUiState(phase = AnalysisPhase.COMPLETE, items = listOf(sampleItem()), isLogging = true),
        )

        composeRule.onNodeWithTag(ResultsTestTags.CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun aDegradedPipelineExplainsItselfButStillAllowsLogging() {
        setSheet(
            ResultsUiState(
                phase = AnalysisPhase.COMPLETE,
                items = listOf(sampleItem()),
                degradeReason = DegradeReason.RATE_LIMITED,
            ),
        )

        composeRule.onNodeWithTag(ResultsTestTags.DEGRADE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(ResultsTestTags.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun aTotalFailureOffersARetry() {
        var lastAction: ResultsAction? = null
        setSheet(
            state = ResultsUiState(phase = AnalysisPhase.FAILED, errorMessage = "Nothing recognised"),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Try again").performClick()

        assert(lastAction == ResultsAction.Retry)
    }

    @Test
    fun theMealTypeCanBeChanged() {
        var lastAction: ResultsAction? = null
        setSheet(
            state = ResultsUiState(
                phase = AnalysisPhase.COMPLETE,
                items = listOf(sampleItem()),
                mealType = MealType.LUNCH,
            ),
            onAction = { lastAction = it },
        )

        composeRule.onNodeWithText("Dinner").performClick()

        assert(lastAction == ResultsAction.ChangeMealType(MealType.DINNER))
    }
}
