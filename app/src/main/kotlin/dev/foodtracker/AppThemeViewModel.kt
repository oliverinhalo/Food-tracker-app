package dev.foodtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.core.datastore.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Just enough of the settings to draw the first frame correctly. */
data class AppTheme(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

/**
 * Holds the theme alone, rather than the whole of Settings.
 *
 * The Activity previously read its colours from [dev.foodtracker.feature.settings.SettingsViewModel],
 * which pulls in the diary DAOs and the backup machinery -- all of it constructed on the main
 * thread during startup for three booleans. This asks for only what the first frame needs.
 *
 * `null` means the stored preferences have not been read yet, which the splash screen waits on so
 * a dark-theme user never sees a white frame first.
 */
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val theme: StateFlow<AppTheme?> = settingsRepository.settings
        .map { AppTheme(it.themeMode, it.dynamicColor, it.hapticsEnabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
