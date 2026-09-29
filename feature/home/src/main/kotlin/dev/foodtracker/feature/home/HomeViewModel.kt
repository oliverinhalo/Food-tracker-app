package dev.foodtracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.MealType
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.data.diary.DiaryRepository
import dev.foodtracker.data.diary.LoggedMeal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

data class HomeUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 120,
    val carbsGoal: Int = 220,
    val fatGoal: Int = 65,
    val consumedNutrients: Nutrients = Nutrients.ZERO,
    val meals: List<MealSummary> = emptyList(),
) {
    val consumedCalories: Int get() = consumedNutrients.calories.roundToInt()
    val remainingCalories: Int get() = calorieGoal - consumedCalories
}

data class MealSummary(
    val id: String,
    val mealType: MealType,
    val itemNames: List<String>,
    val calories: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    diaryRepository: DiaryRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    // Keyed on the date flow rather than a date read once at construction: leaving the app open
    // past midnight otherwise keeps yesterday's totals on screen under today's heading.
    val uiState: StateFlow<HomeUiState> = timeProvider.todayFlow().flatMapLatest { today ->
        combine(
            settingsRepository.settings,
            diaryRepository.totalsFor(today),
            diaryRepository.mealsFor(today),
        ) { settings, totals, meals ->
            HomeUiState(
                calorieGoal = settings.dailyCalorieGoal,
                proteinGoal = settings.proteinGoalGrams,
                carbsGoal = settings.carbsGoalGrams,
                fatGoal = settings.fatGoalGrams,
                consumedNutrients = totals,
                meals = meals.map { it.toSummary() },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

private fun LoggedMeal.toSummary() = MealSummary(
    id = id,
    mealType = mealType,
    itemNames = items.map { it.name },
    calories = totals.calories.roundToInt(),
)
