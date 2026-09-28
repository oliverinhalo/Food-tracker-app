package dev.foodtracker.feature.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionEvent
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.data.diary.DiaryRepository
import dev.foodtracker.data.recognition.CaptureStore
import dev.foodtracker.data.nutrition.NutritionRepository
import dev.foodtracker.data.nutrition.ResolveNutrition
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.nutrition.UnitConverter
import dev.foodtracker.domain.nutrition.foodKeyOf
import dev.foodtracker.domain.recognition.RecognitionConfig
import dev.foodtracker.domain.recognition.RecognitionOrchestrator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val orchestrator: RecognitionOrchestrator,
    private val settingsRepository: SettingsRepository,
    private val captureStore: CaptureStore,
    private val nutritionRepository: NutritionRepository,
    private val resolveNutrition: ResolveNutrition,
    private val diaryRepository: DiaryRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val converter = UnitConverter()

    private var analysisJob: Job? = null
    private var captureId: String? = null

    /** What the recogniser first proposed, so a later edit can be measured against it. */
    private val originalEstimates = mutableMapOf<String, Double>()

    fun analyze(captureId: String) {
        if (this.captureId == captureId && analysisJob?.isActive == true) return
        this.captureId = captureId

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = AnalysisPhase.ANALYZING,
                    degradeReason = null,
                    errorMessage = null,
                    mealType = MealType.suggestedFor(timeProvider.now()),
                )
            }

            val image = captureStore.load(captureId)
            if (image == null) {
                _uiState.update {
                    it.copy(
                        phase = AnalysisPhase.FAILED,
                        errorMessage = "That photo is no longer available. Try taking it again.",
                    )
                }
                return@launch
            }

            val settings = settingsRepository.settings.first()
            val config = when {
                settings.localOnlyMode -> RecognitionConfig(false, DegradeReason.LOCAL_ONLY_MODE)
                !settings.hasApiKey -> RecognitionConfig(false, DegradeReason.NO_API_KEY)
                else -> RecognitionConfig(cloudEnabled = true)
            }

            orchestrator.recognize(captureId, image, config).collect { event ->
                _uiState.update { state -> state.reduce(event) }
                // Nutrition lookup runs after each pass rather than only at the end, so provisional
                // items show calories instead of a skeleton while the cloud pass is still running.
                resolveNutritionForCurrentItems()
            }
        }
    }

    private suspend fun resolveNutritionForCurrentItems() {
        val items = _uiState.value.items
        if (items.isEmpty()) return

        val resolved = resolveNutrition(items)
        resolved.forEach { item -> originalEstimates.putIfAbsent(item.id, item.portion.grams) }

        _uiState.update { state ->
            // The user may have edited while the lookup was in flight; their version wins.
            val edited = state.items.associateBy { it.id }
            state.copy(
                items = resolved.map { item ->
                    val live = edited[item.id]
                    if (live != null && live.source == RecognitionSource.USER) {
                        live.copy(nutrientsPer100g = live.nutrientsPer100g ?: item.nutrientsPer100g)
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun onAction(action: ResultsAction) {
        when (action) {
            is ResultsAction.ChangeQuantity -> updateItem(action.itemId) { item ->
                item.withPortion(action.amount, item.portion.unit, converter)
            }

            is ResultsAction.ChangeUnit -> updateItem(action.itemId) { item ->
                // Keep the mass the user settled on and re-express it, rather than reinterpreting
                // "150" as 150 cups.
                val profile = FoodCategory.profileFor(item.name)
                item.copy(
                    portion = converter.convert(item.portion, action.unit, profile, foodKeyOf(item.name, item.brand)),
                    source = RecognitionSource.USER,
                )
            }

            is ResultsAction.ChangeItemName -> {
                updateItem(action.itemId) { item ->
                    // A different food means different nutrition; clear it so it re-resolves.
                    item.copy(
                        name = action.name,
                        source = RecognitionSource.USER,
                        confidence = 1f,
                        nutrientsPer100g = null,
                        foodId = null,
                    )
                }
                viewModelScope.launch { resolveNutritionForCurrentItems() }
            }

            is ResultsAction.RemoveItem -> _uiState.update { state ->
                state.copy(items = state.items.filterNot { it.id == action.itemId })
            }

            is ResultsAction.RestoreItem -> _uiState.update { state ->
                val restored = state.items.toMutableList()
                restored.add(action.index.coerceIn(0, restored.size), action.item)
                state.copy(items = restored)
            }

            is ResultsAction.ChangeMealType -> _uiState.update { it.copy(mealType = action.mealType) }

            ResultsAction.AddEmptyItem -> _uiState.update { state ->
                state.copy(items = state.items + newManualItem())
            }

            ResultsAction.Retry -> captureId?.let { analyze(it) }

            ResultsAction.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm) return

        _uiState.update { it.copy(isLogging = true) }

        viewModelScope.launch {
            runCatching {
                diaryRepository.logMeal(mealType = state.mealType, items = state.items)
                recordPortionCorrections(state.items)
            }.onSuccess {
                _uiState.update { it.copy(isLogging = false, loggedSuccessfully = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLogging = false, errorMessage = error.message ?: "Could not save this meal.")
                }
            }
        }
    }

    /**
     * Teaches the app how this person actually portions each food. Only items the user visibly
     * changed count -- logging an untouched estimate says nothing about their habits, and treating
     * it as confirmation would lock in the recogniser's own bias.
     */
    private suspend fun recordPortionCorrections(items: List<DetectedItem>) {
        items.forEach { item ->
            if (item.source != RecognitionSource.USER) return@forEach
            val original = originalEstimates[item.id] ?: return@forEach
            nutritionRepository.recordCorrection(
                foodKey = foodKeyOf(item.name, item.brand),
                unit = item.portion.unit,
                estimatedGrams = original,
                correctedGrams = item.portion.grams,
            )
        }
    }

    private fun updateItem(itemId: String, transform: (DetectedItem) -> DetectedItem) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == itemId) transform(it) else it })
        }
    }

    private fun newManualItem() = DetectedItem(
        id = UUID.randomUUID().toString(),
        name = "",
        confidence = 1f,
        portion = Portion.ofGrams(100.0),
        source = RecognitionSource.USER,
    )
}

/** A portion edit always recomputes grams, so nutrition never reads a stale mass. */
internal fun DetectedItem.withPortion(
    amount: Double,
    unit: MeasurementUnit,
    converter: UnitConverter,
): DetectedItem {
    val safeAmount = amount.coerceAtLeast(0.0)
    val profile = FoodCategory.profileFor(name)
    return copy(
        portion = converter.portionOf(safeAmount, unit, profile, foodKeyOf(name, brand)),
        source = RecognitionSource.USER,
    )
}

internal fun ResultsUiState.reduce(event: RecognitionEvent): ResultsUiState = when (event) {
    is RecognitionEvent.Provisional -> copy(
        phase = AnalysisPhase.PROVISIONAL,
        items = event.items,
        degradeReason = null,
        errorMessage = null,
    )

    is RecognitionEvent.Refined -> copy(
        phase = AnalysisPhase.COMPLETE,
        items = event.items,
        degradeReason = null,
        errorMessage = null,
    )

    is RecognitionEvent.Degraded -> copy(
        phase = AnalysisPhase.COMPLETE,
        items = event.items,
        degradeReason = event.reason,
    )

    is RecognitionEvent.Failed -> copy(
        phase = AnalysisPhase.FAILED,
        degradeReason = event.reason,
        errorMessage = event.message,
    )
}
