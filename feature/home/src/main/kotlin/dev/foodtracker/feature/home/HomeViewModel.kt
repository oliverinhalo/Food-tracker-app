package dev.foodtracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.data.diary.DiaryRepository
import dev.foodtracker.data.diary.LoggedMeal
import dev.foodtracker.data.nutrition.NutritionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class QuickAddFood(
    val foodKey: String,
    val name: String,
    val brand: String?,
    val cachedFoodId: String?,
)

data class HomeUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 120,
    val carbsGoal: Int = 220,
    val fatGoal: Int = 65,
    val consumedNutrients: Nutrients = Nutrients.ZERO,
    val meals: List<MealSummary> = emptyList(),
    val quickAdd: List<QuickAddFood> = emptyList(),
    val justLogged: String? = null,
) {
    val consumedCalories: Int get() = consumedNutrients.calories.roundToInt()
    val remainingCalories: Int get() = calorieGoal - consumedCalories
}

data class MealSummary(
    val id: String,
    val mealType: MealType,
    val itemNames: List<String>,
    val calories: Int,
    val photoPath: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val diaryRepository: DiaryRepository,
    private val nutritionRepository: NutritionRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val justLogged = MutableStateFlow<String?>(null)

    // Keyed on the date flow rather than a date read once at construction: leaving the app open
    // past midnight otherwise keeps yesterday's totals on screen under today's heading.
    val uiState: StateFlow<HomeUiState> = timeProvider.todayFlow().flatMapLatest { today ->
        combine(
            settingsRepository.settings,
            diaryRepository.totalsFor(today),
            diaryRepository.mealsFor(today),
            diaryRepository.favourites(limit = QUICK_ADD_COUNT),
            justLogged,
        ) { settings, totals, meals, favourites, logged ->
            HomeUiState(
                calorieGoal = settings.dailyCalorieGoal,
                proteinGoal = settings.proteinGoalGrams,
                carbsGoal = settings.carbsGoalGrams,
                fatGoal = settings.fatGoalGrams,
                consumedNutrients = totals,
                meals = meals.map { it.toSummary() },
                quickAdd = favourites.map { QuickAddFood(it.foodKey, it.name, it.brand, it.cachedFoodId) },
                justLogged = logged,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /**
     * Logs a repeat food in one tap, at the size it is usually eaten.
     *
     * Most days are made of the same few meals, and making someone photograph their usual porridge
     * every morning is the fastest way to get them to stop tracking. The portion comes from the
     * learner rather than a flat default, so it matches what this person actually eats.
     */
    fun quickAdd(food: QuickAddFood) {
        viewModelScope.launch {
            val record = food.cachedFoodId?.let { nutritionRepository.cachedById(it) }
                ?: nutritionRepository.resolve(food.name, food.brand)

            if (record == null) {
                justLogged.value = "Couldn't find ${food.name}. Open Add food to search for it."
                return@launch
            }

            val grams = nutritionRepository.biasEstimate(
                foodKey = food.foodKey,
                unit = MeasurementUnit.GRAM,
                estimatedGrams = record.servingSizeGrams ?: DEFAULT_QUICK_ADD_GRAMS,
            )

            diaryRepository.logMeal(
                mealType = MealType.suggestedFor(timeProvider.now()),
                items = listOf(
                    DetectedItem(
                        id = UUID.randomUUID().toString(),
                        name = record.name,
                        confidence = 1f,
                        portion = Portion.ofGrams(grams),
                        source = RecognitionSource.USER,
                        brand = record.brand,
                        nutrientsPer100g = record.per100g,
                        foodId = record.id,
                    ),
                ),
            )

            justLogged.value = "Logged ${record.name}"
        }
    }

    fun dismissJustLogged() {
        justLogged.value = null
    }
}

private const val QUICK_ADD_COUNT = 6

/** Only used when the food database has no serving size and nothing has been learned. */
private const val DEFAULT_QUICK_ADD_GRAMS = 100.0

private fun LoggedMeal.toSummary() = MealSummary(
    id = id,
    mealType = mealType,
    itemNames = items.map { it.name },
    calories = totals.calories.roundToInt(),
    photoPath = photoPath,
)
