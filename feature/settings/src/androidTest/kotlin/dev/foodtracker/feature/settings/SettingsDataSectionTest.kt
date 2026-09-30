package dev.foodtracker.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.foodtracker.core.datastore.UserSettings
import dev.foodtracker.core.ui.theme.FoodTrackerTheme
import org.junit.Rule
import org.junit.Test

/**
 * Erasing is the one action in the app that destroys data outright, so the guard in front of it
 * matters more than any other piece of this screen.
 */
class SettingsDataSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var deleted = false

    private fun setScreen(dataTask: DataTaskState = DataTaskState.Idle) {
        deleted = false
        composeRule.setContent {
            FoodTrackerTheme(dynamicColor = false) {
                SettingsScreen(
                    settings = UserSettings(),
                    dataTask = dataTask,
                    onSaveApiKey = {},
                    onClearApiKey = {},
                    onSaveUsdaKey = {},
                    onClearUsdaKey = {},
                    onRevealGeminiKey = { null },
                    onRevealUsdaKey = { null },
                    onCalorieGoalChange = {},
                    onMacroGoalsChange = { _, _, _ -> },
                    onUnitSystemChange = {},
                    onLocalOnlyChange = {},
                    onDynamicColorChange = {},
                    onThemeModeChange = {},
                    onHapticsChange = {},
                    onImageQualityChange = {},
                    onGeminiModelChange = {},
                    onReanalyseChange = {},
                    onExport = {},
                    onImport = {},
                    onDeleteEverything = { deleted = true },
                    onDismissDataTask = {},
                )
            }
        }
    }

    @Test
    fun deletingAsksFirst() {
        setScreen()

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ALL).performScrollTo().performClick()

        composeRule.onNodeWithText("Delete everything?").assertIsDisplayed()
        assert(!deleted) { "the tap alone must not delete anything" }
    }

    @Test
    fun cancellingLeavesTheDataAlone() {
        setScreen()

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ALL).performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assert(!deleted) { "cancelling must not delete anything" }
    }

    @Test
    fun confirmingDeletes() {
        setScreen()

        composeRule.onNodeWithTag(SettingsTestTags.DELETE_ALL).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.DELETE_CONFIRM).performClick()

        assert(deleted) { "confirming should have erased" }
    }

    @Test
    fun aFinishedTaskSaysSo() {
        setScreen(DataTaskState.Done("Restored 3 meals."))

        composeRule.onNodeWithTag(SettingsTestTags.DATA_TASK_MESSAGE).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Restored 3 meals.").assertIsDisplayed()
    }
}
