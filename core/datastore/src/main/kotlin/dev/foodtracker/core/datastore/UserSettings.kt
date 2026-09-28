package dev.foodtracker.core.datastore

/** Measurement system for display. Storage is always metric; this only affects presentation. */
enum class UnitSystem { METRIC, IMPERIAL }

data class UserSettings(
    val dailyCalorieGoal: Int = DEFAULT_CALORIE_GOAL,
    val proteinGoalGrams: Int = DEFAULT_PROTEIN_GOAL,
    val carbsGoalGrams: Int = DEFAULT_CARBS_GOAL,
    val fatGoalGrams: Int = DEFAULT_FAT_GOAL,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val localOnlyMode: Boolean = false,
    val dynamicColor: Boolean = true,
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
