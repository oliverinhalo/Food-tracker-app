package dev.foodtracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Non-secret user preferences. The Gemini API key deliberately does NOT live here -- see
 * [SecureKeyStore]. Only the derived `hasApiKey` flag is exposed through settings so UI can react
 * to the key's presence without the key itself flowing through the UI layer.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore,
) {
    private object Keys {
        val CALORIE_GOAL = intPreferencesKey("daily_calorie_goal")
        val PROTEIN_GOAL = intPreferencesKey("protein_goal_grams")
        val CARBS_GOAL = intPreferencesKey("carbs_goal_grams")
        val FAT_GOAL = intPreferencesKey("fat_goal_grams")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val LOCAL_ONLY = booleanPreferencesKey("local_only_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val API_KEY_PRESENT = booleanPreferencesKey("api_key_present")
        val USDA_KEY_PRESENT = booleanPreferencesKey("usda_key_present")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { prefs ->
        UserSettings(
            dailyCalorieGoal = prefs[Keys.CALORIE_GOAL] ?: UserSettings.DEFAULT_CALORIE_GOAL,
            proteinGoalGrams = prefs[Keys.PROTEIN_GOAL] ?: UserSettings.DEFAULT_PROTEIN_GOAL,
            carbsGoalGrams = prefs[Keys.CARBS_GOAL] ?: UserSettings.DEFAULT_CARBS_GOAL,
            fatGoalGrams = prefs[Keys.FAT_GOAL] ?: UserSettings.DEFAULT_FAT_GOAL,
            unitSystem = prefs[Keys.UNIT_SYSTEM]?.let(::runCatchingUnitSystem) ?: UnitSystem.METRIC,
            localOnlyMode = prefs[Keys.LOCAL_ONLY] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            hasApiKey = prefs[Keys.API_KEY_PRESENT] ?: false,
            hasUsdaKey = prefs[Keys.USDA_KEY_PRESENT] ?: false,
        )
    }

    suspend fun setDailyCalorieGoal(value: Int) = edit { it[Keys.CALORIE_GOAL] = value.coerceIn(500, 10_000) }

    suspend fun setMacroGoals(proteinGrams: Int, carbsGrams: Int, fatGrams: Int) = edit {
        it[Keys.PROTEIN_GOAL] = proteinGrams.coerceIn(0, 1_000)
        it[Keys.CARBS_GOAL] = carbsGrams.coerceIn(0, 1_000)
        it[Keys.FAT_GOAL] = fatGrams.coerceIn(0, 1_000)
    }

    suspend fun setUnitSystem(value: UnitSystem) = edit { it[Keys.UNIT_SYSTEM] = value.name }

    suspend fun setLocalOnlyMode(enabled: Boolean) = edit { it[Keys.LOCAL_ONLY] = enabled }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    /** Writes the key to encrypted storage and mirrors only its presence into settings. */
    suspend fun setGeminiApiKey(key: String?) {
        secureKeyStore.setGeminiApiKey(key)
        edit { it[Keys.API_KEY_PRESENT] = !key.isNullOrBlank() }
    }

    suspend fun setUsdaApiKey(key: String?) {
        secureKeyStore.setUsdaApiKey(key)
        edit { it[Keys.USDA_KEY_PRESENT] = !key.isNullOrBlank() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun runCatchingUnitSystem(raw: String): UnitSystem =
        UnitSystem.entries.firstOrNull { it.name == raw } ?: UnitSystem.METRIC
}
