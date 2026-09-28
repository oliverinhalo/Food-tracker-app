package dev.foodtracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.model.Nutrients
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 120,
    val carbsGoal: Int = 220,
    val fatGoal: Int = 65,
    val consumedCalories: Int = 0,
    val consumedNutrients: Nutrients = Nutrients.ZERO,
    val meals: List<LoggedMealSummary> = emptyList(),
)

/** Placeholder until the diary lands in phase 2. */
data class LoggedMealSummary(
    val id: String,
    val title: String,
    val calories: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Goals are live from settings today; consumption arrives with the diary in phase 2.
    val uiState: StateFlow<HomeUiState> = settingsRepository.settings
        .map { settings ->
            HomeUiState(
                calorieGoal = settings.dailyCalorieGoal,
                proteinGoal = settings.proteinGoalGrams,
                carbsGoal = settings.carbsGoalGrams,
                fatGoal = settings.fatGoalGrams,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )
}
