package dev.foodtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.datastore.SecureKeyStore
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
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    /**
     * The saved key, so it can be copied out before a reinstall.
     *
     * Android wipes app data when an app is uninstalled, and a key stored behind the keystore
     * cannot be restored from a backup even if one existed. Being able to read your own key back
     * is the difference between a reinstall costing a tap and costing a trip to Google AI Studio.
     */
    fun revealGeminiKey(): String? = secureKeyStore.geminiApiKey()

    fun revealUsdaKey(): String? = secureKeyStore.usdaApiKey()

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

    fun setMacroGoals(proteinGrams: Int, carbsGrams: Int, fatGrams: Int) = viewModelScope.launch {
        settingsRepository.setMacroGoals(proteinGrams, carbsGrams, fatGrams)
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
