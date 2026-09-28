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
import dev.foodtracker.data.recognition.CaptureStore
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
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var captureId: String? = null

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
            }
        }
    }

    fun onAction(action: ResultsAction) {
        when (action) {
            is ResultsAction.ChangeQuantity -> updateItem(action.itemId) { item ->
                item.withPortion(amount = action.amount, unit = item.portion.unit)
            }

            is ResultsAction.ChangeUnit -> updateItem(action.itemId) { item ->
                // Keep the mass the user already settled on and re-express it in the new unit,
                // rather than reinterpreting "150" as 150 cups.
                val amountInNewUnit = PortionConversion.fromGrams(item.portion.grams, action.unit)
                item.withPortion(amount = amountInNewUnit, unit = action.unit)
            }

            is ResultsAction.ChangeItemName -> updateItem(action.itemId) { item ->
                item.copy(name = action.name, source = RecognitionSource.USER, confidence = 1f)
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
        // Phase 2 writes this to the diary; for now the sheet just closes cleanly so the flow and
        // its UI tests are exercisable end to end.
        _uiState.update { it.copy(isLogging = true) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLogging = false, loggedSuccessfully = true) }
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
internal fun DetectedItem.withPortion(amount: Double, unit: MeasurementUnit): DetectedItem {
    val safeAmount = amount.coerceAtLeast(0.0)
    return copy(
        portion = Portion(
            amount = safeAmount,
            unit = unit,
            grams = PortionConversion.toGrams(safeAmount, unit),
            // The recogniser's phrasing ("1 cup") describes the original estimate, so it stops
            // being true the moment the user changes the amount.
            householdDescription = null,
        ),
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
