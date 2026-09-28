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

@Immutable
data class ResultsUiState(
    val phase: AnalysisPhase = AnalysisPhase.ANALYZING,
    val items: List<DetectedItem> = emptyList(),
    val mealType: MealType = MealType.SNACK,
    val degradeReason: DegradeReason? = null,
    val errorMessage: String? = null,
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

    val isRefining: Boolean
        get() = phase == AnalysisPhase.PROVISIONAL
}

/** Everything the user can do from the sheet. One sealed type keeps the ViewModel's surface honest. */
sealed interface ResultsAction {
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
