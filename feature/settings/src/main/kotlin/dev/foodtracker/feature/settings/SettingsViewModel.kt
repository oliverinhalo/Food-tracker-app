package dev.foodtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.datastore.UnitSystem
import dev.foodtracker.core.datastore.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettings(),
        )

    fun setApiKey(key: String) = viewModelScope.launch {
        settingsRepository.setGeminiApiKey(key.trim().takeIf { it.isNotBlank() })
    }

    fun clearApiKey() = viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }

    fun setUsdaKey(key: String) = viewModelScope.launch {
        settingsRepository.setUsdaApiKey(key.trim().takeIf { it.isNotBlank() })
    }

    fun clearUsdaKey() = viewModelScope.launch { settingsRepository.setUsdaApiKey(null) }

    fun setCalorieGoal(goal: Int) = viewModelScope.launch {
        settingsRepository.setDailyCalorieGoal(goal)
    }

    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch {
        settingsRepository.setUnitSystem(system)
    }

    fun setLocalOnlyMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLocalOnlyMode(enabled)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }
}
