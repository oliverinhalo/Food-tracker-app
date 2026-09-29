package dev.foodtracker.feature.results

import androidx.compose.runtime.Immutable
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.sum

/** How far along the pipeline is. Drives skeletons, the progress pill and the confirm button. */
enum class AnalysisPhase {
    ANALYZING,
    PROVISIONAL,
    COMPLETE,
    FAILED,
}

/**
 * What the app is doing right now, in the user's terms.
 *
 * "Analysing" for eight seconds reads as a hang; naming each step makes the same wait legible and
 * tells the user which part is slow when something goes wrong.
 */
enum class AnalysisStage(val label: String) {
    PREPARING("Preparing photo…"),
    UPLOADING("Uploading to Gemini…"),
    IDENTIFYING("Identifying foods…"),
    LOOKING_UP_NUTRITION("Looking up nutrition…"),
    SAVING("Saving meal…"),
    DONE(""),
}

@Immutable
data class ResultsUiState(
    val phase: AnalysisPhase = AnalysisPhase.ANALYZING,
    val stage: AnalysisStage = AnalysisStage.PREPARING,
    val items: List<DetectedItem> = emptyList(),
    val mealType: MealType = MealType.SNACK,
    val degradeReason: DegradeReason? = null,
    val errorMessage: String? = null,
    val picker: FoodPickerState? = null,
    /** Set when the sheet is editing an already-logged meal rather than creating one. */
    val editingMealId: String? = null,
    val isLogging: Boolean = false,
    val loggedSuccessfully: Boolean = false,
) {
    /** Live totals for the sheet header. Items still awaiting nutrition contribute nothing. */
    val totals: Nutrients
        get() = items.mapNotNull { it.nutrients }.sum()

    val hasResolvedNutrition: Boolean
        get() = items.any { it.nutrients != null }

    val canConfirm: Boolean
        get() = items.isNotEmpty() && !isLogging

    val isEditing: Boolean get() = editingMealId != null

    val isRefining: Boolean
        get() = phase == AnalysisPhase.PROVISIONAL

    /** Items the databases could not identify; they log, but with no calories attached. */
    val unresolvedCount: Int
        get() = items.count { it.nutrientsPer100g == null && it.name.isNotBlank() }

    val isBusy: Boolean
        get() = stage != AnalysisStage.DONE
}

/** Everything the user can do from the sheet. One sealed type keeps the ViewModel's surface honest. */
sealed interface ResultsAction {
    /** Opens the "Change item" picker for one item. */
    data class OpenPicker(val itemId: String) : ResultsAction
    data object ClosePicker : ResultsAction
    data class PickerQueryChanged(val query: String) : ResultsAction
    data class SelectFood(val option: FoodOption) : ResultsAction
    data class ScanBarcode(val itemId: String) : ResultsAction

    data class ChangeQuantity(val itemId: String, val amount: Double) : ResultsAction
    data class ChangeUnit(val itemId: String, val unit: dev.foodtracker.core.model.MeasurementUnit) : ResultsAction
    data class ChangeItemName(val itemId: String, val name: String) : ResultsAction
    data class RemoveItem(val itemId: String) : ResultsAction
    data class RestoreItem(val item: DetectedItem, val index: Int) : ResultsAction
    data class ChangeMealType(val mealType: MealType) : ResultsAction
    data object AddEmptyItem : ResultsAction
    data object Retry : ResultsAction
    data object Confirm : ResultsAction
}
