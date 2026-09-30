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
import dev.foodtracker.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private object Keys {
        val CALORIE_GOAL = intPreferencesKey("daily_calorie_goal")
        val PROTEIN_GOAL = intPreferencesKey("protein_goal_grams")
        val CARBS_GOAL = intPreferencesKey("carbs_goal_grams")
        val FAT_GOAL = intPreferencesKey("fat_goal_grams")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val IMAGE_QUALITY = stringPreferencesKey("image_quality")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val REANALYSE = booleanPreferencesKey("reanalyse_queued")
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
            themeMode = prefs[Keys.THEME_MODE].toEnum(ThemeMode.entries, ThemeMode.SYSTEM),
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            imageQuality = prefs[Keys.IMAGE_QUALITY].toEnum(ImageQuality.entries, ImageQuality.BALANCED),
            geminiModel = prefs[Keys.GEMINI_MODEL].toEnum(GeminiModelChoice.entries, GeminiModelChoice.AUTO),
            reanalyseQueuedPhotos = prefs[Keys.REANALYSE] ?: true,
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

    /**
     * Writes the key to encrypted storage and mirrors only its presence into settings.
     *
     * On the IO dispatcher because the first touch of the encrypted store builds a Keystore-backed
     * master key, which is far too slow to do on the thread that is drawing.
     */
    suspend fun setGeminiApiKey(key: String?) {
        withContext(ioDispatcher) { secureKeyStore.setGeminiApiKey(key) }
        edit { it[Keys.API_KEY_PRESENT] = !key.isNullOrBlank() }
    }

    /** The saved keys, read off the main thread, for showing before a reinstall. */
    suspend fun revealGeminiApiKey(): String? = withContext(ioDispatcher) { secureKeyStore.geminiApiKey() }

    suspend fun revealUsdaApiKey(): String? = withContext(ioDispatcher) { secureKeyStore.usdaApiKey() }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }

    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[Keys.HAPTICS] = enabled }

    suspend fun setImageQuality(value: ImageQuality) = edit { it[Keys.IMAGE_QUALITY] = value.name }

    suspend fun setGeminiModel(value: GeminiModelChoice) = edit { it[Keys.GEMINI_MODEL] = value.name }

    suspend fun setReanalyseQueuedPhotos(enabled: Boolean) = edit { it[Keys.REANALYSE] = enabled }

    /**
     * Brings the "a key is saved" flags back in line with what is actually stored.
     *
     * The flags live in DataStore, which Auto Backup restores; the keys live behind the device's
     * own Keystore, which it cannot. After a restore onto a new phone, Settings would otherwise
     * say a key was saved while every scan failed for want of one.
     */
    suspend fun reconcileKeyPresence() = withContext(ioDispatcher) {
        val gemini = !secureKeyStore.geminiApiKey().isNullOrBlank()
        val usda = !secureKeyStore.usdaApiKey().isNullOrBlank()
        edit {
            it[Keys.API_KEY_PRESENT] = gemini
            it[Keys.USDA_KEY_PRESENT] = usda
        }
    }

    /** Wipes every preference. Used by "delete all data", which must leave nothing behind. */
    suspend fun clearAll() {
        withContext(ioDispatcher) {
            secureKeyStore.setGeminiApiKey(null)
            secureKeyStore.setUsdaApiKey(null)
        }
        context.settingsDataStore.edit { it.clear() }
    }

    suspend fun setUsdaApiKey(key: String?) {
        withContext(ioDispatcher) { secureKeyStore.setUsdaApiKey(key) }
        edit { it[Keys.USDA_KEY_PRESENT] = !key.isNullOrBlank() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun runCatchingUnitSystem(raw: String): UnitSystem =
        UnitSystem.entries.firstOrNull { it.name == raw } ?: UnitSystem.METRIC
}

/**
 * Reads a stored enum name back, falling back when the value is unknown.
 *
 * A downgrade, or a setting removed in a later version, would otherwise leave a name in storage
 * that no longer resolves and crash on read.
 */
private fun <T : Enum<T>> String?.toEnum(values: List<T>, fallback: T): T =
    values.firstOrNull { it.name == this } ?: fallback
