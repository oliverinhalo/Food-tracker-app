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
import dev.foodtracker.data.nutrition.FoodRecord
import dev.foodtracker.data.nutrition.NutritionRepository
import dev.foodtracker.data.nutrition.ResolveNutrition
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.nutrition.UnitConverter
import dev.foodtracker.domain.nutrition.foodKeyOf
import dev.foodtracker.domain.recognition.RecognitionConfig
import dev.foodtracker.domain.recognition.RecognitionOrchestrator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Records behind the options currently on offer, so a pick can apply full nutrition. */
    private val offeredRecords = mutableMapOf<String, FoodRecord>()

    private var searchJob: Job? = null

    /**
     * Opens the sheet with no photo behind it, for foods that cannot be photographed -- a coffee
     * already drunk, a packet in a bag, yesterday's dinner. Reuses the whole editing and logging
     * path rather than growing a second one.
     */
    fun startManualEntry() {
        analysisJob?.cancel()
        captureId = null
        originalEstimates.clear()

        val item = newManualItem()
        _uiState.value = ResultsUiState(
            phase = AnalysisPhase.COMPLETE,
            stage = AnalysisStage.DONE,
            items = listOf(item),
            mealType = MealType.suggestedFor(timeProvider.now()),
        )
        openPicker(item.id)
    }

    fun analyze(captureId: String) {
        if (this.captureId == captureId && analysisJob?.isActive == true) return
        this.captureId = captureId

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = AnalysisPhase.ANALYZING,
                    stage = AnalysisStage.PREPARING,
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
                        stage = AnalysisStage.DONE,
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

            _uiState.update {
                it.copy(stage = if (config.cloudEnabled) AnalysisStage.UPLOADING else AnalysisStage.IDENTIFYING)
            }

            orchestrator.recognize(captureId, image, config).collect { event ->
                _uiState.update { state -> state.reduce(event).copy(stage = AnalysisStage.IDENTIFYING) }
                // Nutrition lookup runs after each pass rather than only at the end, so provisional
                // items show calories instead of a skeleton while the cloud pass is still running.
                resolveNutritionForCurrentItems()
            }

            _uiState.update { it.copy(stage = AnalysisStage.DONE) }
        }
    }

    private suspend fun resolveNutritionForCurrentItems() {
        val items = _uiState.value.items
        if (items.isEmpty()) return

        _uiState.update { it.copy(stage = AnalysisStage.LOOKING_UP_NUTRITION) }
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

            is ResultsAction.OpenPicker -> openPicker(action.itemId)

            ResultsAction.ClosePicker -> {
                searchJob?.cancel()
                _uiState.update { it.copy(picker = null) }
            }

            is ResultsAction.PickerQueryChanged -> onPickerQueryChanged(action.query)

            is ResultsAction.SelectFood -> applyFoodChoice(action.option)

            is ResultsAction.ScanBarcode ->
                _uiState.update { it.copy(picker = it.picker?.copy(isScanning = true)) }

            ResultsAction.Retry -> captureId?.let { analyze(it) }

            ResultsAction.Confirm -> confirm()
        }
    }

    private fun openPicker(itemId: String) {
        val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return

        _uiState.update {
            it.copy(
                picker = FoodPickerState(
                    itemId = itemId,
                    itemName = item.name,
                    suggestions = item.alternatives.map { alternative ->
                        FoodOption(
                            id = "ai:${alternative.name}",
                            name = alternative.name,
                            brand = null,
                            caloriesPer100g = 0,
                            origin = FoodOption.Origin.AI_SUGGESTION,
                        )
                    },
                ),
            )
        }

        // Seed with the item's own name so the list is useful before the user types anything.
        if (item.name.isNotBlank()) {
            runSearch(item.name, showQuery = false)
        } else {
            loadRecents()
        }
    }

    /**
     * Foods this person logs often, offered before they type anything. Most days are made of the
     * same handful of meals, so this is the difference between logging breakfast in one tap and
     * searching for porridge every morning.
     */
    private fun loadRecents() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val favourites = diaryRepository.favourites(limit = 12).first()
            if (favourites.isEmpty()) return@launch

            val options = favourites.mapNotNull { favourite ->
                val record = favourite.cachedFoodId?.let { nutritionRepository.cachedById(it) }
                if (record != null) {
                    offeredRecords[record.id] = record
                    record.toOption()
                } else {
                    FoodOption(
                        id = "recent:${favourite.foodKey}",
                        name = favourite.name,
                        brand = favourite.brand,
                        caloriesPer100g = 0,
                        origin = FoodOption.Origin.AI_SUGGESTION,
                    )
                }
            }

            _uiState.update { state ->
                val picker = state.picker ?: return@update state
                if (picker.query.isNotBlank()) return@update state
                state.copy(picker = picker.copy(recents = options))
            }
        }
    }

    private fun onPickerQueryChanged(query: String) {
        _uiState.update { it.copy(picker = it.picker?.copy(query = query)) }
        runSearch(query, showQuery = true)
    }

    private fun runSearch(query: String, showQuery: Boolean) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(picker = it.picker?.copy(results = emptyList(), isSearching = false)) }
            return
        }

        searchJob = viewModelScope.launch {
            // Typing a food name fires a request per keystroke otherwise, and both databases are
            // rate-limited.
            if (showQuery) delay(SEARCH_DEBOUNCE_MILLIS)

            _uiState.update { it.copy(picker = it.picker?.copy(isSearching = true, message = null)) }

            val records = runCatching { nutritionRepository.search(query, limit = 20) }
                .getOrElse { emptyList() }

            records.forEach { offeredRecords[it.id] = it }

            _uiState.update { state ->
                val picker = state.picker ?: return@update state
                state.copy(
                    picker = picker.copy(
                        isSearching = false,
                        results = records.map { it.toOption() },
                        message = if (records.isEmpty()) "No matches. Try a simpler name." else null,
                    ),
                )
            }
        }
    }

    private fun applyFoodChoice(option: FoodOption) {
        val picker = _uiState.value.picker ?: return
        val record = offeredRecords[option.id]

        updateItem(picker.itemId) { item ->
            item.copy(
                name = option.name,
                brand = record?.brand,
                source = RecognitionSource.USER,
                confidence = 1f,
                // An AI suggestion carries no nutrition of its own, so it has to be looked up.
                nutrientsPer100g = record?.per100g,
                foodId = record?.id,
            )
        }

        _uiState.update { it.copy(picker = null) }
        searchJob?.cancel()

        if (record == null) viewModelScope.launch { resolveNutritionForCurrentItems() }
    }

    /** Result of the barcode scanner: a product lookup that either lands or explains itself. */
    fun onBarcodeScanned(barcode: String) {
        _uiState.update { it.copy(picker = it.picker?.copy(isScanning = false, isSearching = true)) }

        viewModelScope.launch {
            val record = runCatching { nutritionRepository.byBarcode(barcode) }.getOrNull()

            if (record == null) {
                _uiState.update {
                    it.copy(
                        picker = it.picker?.copy(
                            isSearching = false,
                            message = "That barcode isn't in Open Food Facts. Try searching by name.",
                        ),
                    )
                }
                return@launch
            }

            offeredRecords[record.id] = record
            applyFoodChoice(record.toOption())
        }
    }

    fun onBarcodeScanCancelled() {
        _uiState.update { it.copy(picker = it.picker?.copy(isScanning = false)) }
    }

    private fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm) return

        _uiState.update { it.copy(isLogging = true, stage = AnalysisStage.SAVING) }

        viewModelScope.launch {
            runCatching {
                diaryRepository.logMeal(mealType = state.mealType, items = state.items)
                recordPortionCorrections(state.items)
            }.onSuccess {
                _uiState.update { it.copy(isLogging = false, stage = AnalysisStage.DONE, loggedSuccessfully = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLogging = false,
                        stage = AnalysisStage.DONE,
                        errorMessage = error.message ?: "Could not save this meal.",
                    )
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

private const val SEARCH_DEBOUNCE_MILLIS = 300L

internal fun FoodRecord.toOption(): FoodOption = FoodOption(
    id = id,
    name = name,
    brand = brand,
    caloriesPer100g = per100g.calories.toInt(),
    origin = if (barcode != null) FoodOption.Origin.BARCODE else FoodOption.Origin.DATABASE,
)
