package dev.foodtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.datastore.GeminiModelChoice
import dev.foodtracker.core.datastore.ImageQuality
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.datastore.ThemeMode
import dev.foodtracker.core.datastore.UnitSystem
import dev.foodtracker.core.datastore.UserSettings
import dev.foodtracker.data.diary.DiaryBackup
import dev.foodtracker.data.diary.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the data section is currently doing, and what it last did.
 *
 * Export and erase are both slow enough to need a progress state and consequential enough that a
 * silent finish would leave the user unsure whether anything happened.
 */
sealed interface DataTaskState {
    data object Idle : DataTaskState
    data object Working : DataTaskState
    data class Done(val message: String) : DataTaskState
    data class Failed(val message: String) : DataTaskState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val diaryBackup: DiaryBackup,
) : ViewModel() {

    private val _dataTask = MutableStateFlow<DataTaskState>(DataTaskState.Idle)
    val dataTask: StateFlow<DataTaskState> = _dataTask.asStateFlow()

    private val _revealedGeminiKey = MutableStateFlow<String?>(null)

    /**
     * The saved key, so it can be copied out before a reinstall.
     *
     * Android wipes app data when an app is uninstalled, and a key stored behind the keystore
     * cannot be restored from a backup even if one existed. Being able to read your own key back
     * is the difference between a reinstall costing a tap and costing a trip to Google AI Studio.
     *
     * Read asynchronously: decrypting it touches the Keystore, which is not something to do on the
     * thread handling the tap.
     */
    val revealedGeminiKey: StateFlow<String?> = _revealedGeminiKey.asStateFlow()

    private val _revealedUsdaKey = MutableStateFlow<String?>(null)
    val revealedUsdaKey: StateFlow<String?> = _revealedUsdaKey.asStateFlow()

    fun revealGeminiKey() = viewModelScope.launch {
        _revealedGeminiKey.value = settingsRepository.revealGeminiApiKey()
    }

    fun hideGeminiKey() {
        _revealedGeminiKey.value = null
    }

    fun revealUsdaKey() = viewModelScope.launch {
        _revealedUsdaKey.value = settingsRepository.revealUsdaApiKey()
    }

    fun hideUsdaKey() {
        _revealedUsdaKey.value = null
    }

    val uiState: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserSettings(),
        )

    fun setApiKey(key: String) = viewModelScope.launch {
        settingsRepository.setGeminiApiKey(key.trim().takeIf { it.isNotBlank() })
        // A key on screen after it has been replaced is the old one, which is worse than none.
        _revealedGeminiKey.value = null
    }

    fun clearApiKey() = viewModelScope.launch {
        settingsRepository.setGeminiApiKey(null)
        _revealedGeminiKey.value = null
    }

    fun setUsdaKey(key: String) = viewModelScope.launch {
        settingsRepository.setUsdaApiKey(key.trim().takeIf { it.isNotBlank() })
        _revealedUsdaKey.value = null
    }

    fun clearUsdaKey() = viewModelScope.launch {
        settingsRepository.setUsdaApiKey(null)
        _revealedUsdaKey.value = null
    }

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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHapticsEnabled(enabled)
    }

    fun setImageQuality(quality: ImageQuality) = viewModelScope.launch {
        settingsRepository.setImageQuality(quality)
    }

    fun setGeminiModel(choice: GeminiModelChoice) = viewModelScope.launch {
        settingsRepository.setGeminiModel(choice)
    }

    fun setReanalyseQueuedPhotos(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReanalyseQueuedPhotos(enabled)
    }

    /**
     * Writes the diary to a file the user chose.
     *
     * The caller supplies the writer because only the Activity can hold the document picker; this
     * keeps the file system out of the ViewModel while still owning the progress state.
     */
    fun export(write: (String) -> Unit) = viewModelScope.launch {
        _dataTask.value = DataTaskState.Working
        _dataTask.value = runCatching {
            val payload = diaryBackup.export()
            write(payload)
            DataTaskState.Done("Diary exported.")
        }.getOrElse { DataTaskState.Failed("Couldn't write that file.") }
    }

    fun import(read: () -> String?) = viewModelScope.launch {
        _dataTask.value = DataTaskState.Working
        _dataTask.value = runCatching {
            val contents = read() ?: return@runCatching DataTaskState.Failed("Couldn't read that file.")
            when (val result = diaryBackup.import(contents)) {
                is ImportResult.Imported ->
                    DataTaskState.Done("Restored ${result.meals} meal${if (result.meals == 1) "" else "s"}.")
                is ImportResult.Failed -> DataTaskState.Failed(result.reason)
            }
        }.getOrElse { DataTaskState.Failed("Couldn't read that file.") }
    }

    /** Erases the diary, the caches, what was learned, the photos, and both API keys. */
    fun deleteEverything() = viewModelScope.launch {
        _dataTask.value = DataTaskState.Working
        _revealedGeminiKey.value = null
        _revealedUsdaKey.value = null
        _dataTask.value = runCatching {
            diaryBackup.deleteEverything()
            settingsRepository.clearAll()
            DataTaskState.Done("Everything deleted.")
        }.getOrElse { DataTaskState.Failed("Couldn't delete everything. Try again.") }
    }

    fun dismissDataTask() {
        _dataTask.value = DataTaskState.Idle
    }
}
