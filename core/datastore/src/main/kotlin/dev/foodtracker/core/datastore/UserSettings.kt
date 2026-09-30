package dev.foodtracker.core.datastore

/** Measurement system for display. Storage is always metric; this only affects presentation. */
enum class UnitSystem { METRIC, IMPERIAL }

/** Follows the system by default, because most people set this once at the OS level. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How much image quality to spend on a scan.
 *
 * Bigger is not better past a point: the recogniser's accuracy stops improving well before the
 * payload stops growing, and every extra kilobyte is time the user spends watching a spinner on a
 * slow connection.
 */
enum class ImageQuality(val maxDimension: Int, val jpegQuality: Int, val label: String) {
    DATA_SAVER(768, 70, "Data saver"),
    BALANCED(1024, 80, "Balanced"),
    HIGH(1536, 90, "High detail"),
}

/**
 * Which hosted model to use.
 *
 * [AUTO] walks the fallback chain, which matters because the free tier returns 503 on the newest
 * models far more often than it rate-limits. Pinning one is offered for anyone who wants
 * predictable behaviour and would rather see a failure than a quiet downgrade.
 */
enum class GeminiModelChoice(val modelId: String?, val label: String) {
    AUTO(null, "Automatic (recommended)"),
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash"),
    FLASH_3_7("gemini-3.7-flash", "Gemini 3.7 Flash"),
    FLASH_3_5("gemini-3.5-flash", "Gemini 3.5 Flash"),
    FLASH_3_PREVIEW("gemini-3-flash-preview", "Gemini 3 Flash (preview)"),
}

data class UserSettings(
    val dailyCalorieGoal: Int = DEFAULT_CALORIE_GOAL,
    val proteinGoalGrams: Int = DEFAULT_PROTEIN_GOAL,
    val carbsGoalGrams: Int = DEFAULT_CARBS_GOAL,
    val fatGoalGrams: Int = DEFAULT_FAT_GOAL,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val imageQuality: ImageQuality = ImageQuality.BALANCED,
    val geminiModel: GeminiModelChoice = GeminiModelChoice.AUTO,
    val localOnlyMode: Boolean = false,
    val reanalyseQueuedPhotos: Boolean = true,
    val hasApiKey: Boolean = false,
    val hasUsdaKey: Boolean = false,
) {
    companion object {
        const val DEFAULT_CALORIE_GOAL = 2000
        const val DEFAULT_PROTEIN_GOAL = 120
        const val DEFAULT_CARBS_GOAL = 220
        const val DEFAULT_FAT_GOAL = 65
    }
}
