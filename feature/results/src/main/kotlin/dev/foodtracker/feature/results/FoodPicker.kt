package dev.foodtracker.feature.results

import androidx.compose.runtime.Immutable

/**
 * A food the user can choose instead of what the recogniser guessed. Deliberately a UI-level type
 * rather than the data layer's `FoodRecord`, so the database's shape does not leak into UI state.
 */
@Immutable
data class FoodOption(
    val id: String,
    val name: String,
    val brand: String?,
    val caloriesPer100g: Int,
    val origin: Origin,
) {
    enum class Origin { AI_SUGGESTION, VARIANT, DATABASE, BARCODE }

    val subtitle: String
        get() = buildString {
            if (!brand.isNullOrBlank()) append(brand).append(" · ")
            append("$caloriesPer100g kcal/100g")
        }
}

/** State of the "Change item" picker, open for exactly one item at a time. */
@Immutable
data class FoodPickerState(
    val itemId: String,
    val itemName: String,
    val query: String = "",
    val isSearching: Boolean = false,
    val suggestions: List<FoodOption> = emptyList(),
    /** "What kind of pie?" -- shown above everything else, because it is the likeliest fix. */
    val variantQuestion: String? = null,
    val variants: List<FoodOption> = emptyList(),
    val results: List<FoodOption> = emptyList(),
    val recents: List<FoodOption> = emptyList(),
    val message: String? = null,
    val isScanning: Boolean = false,
) {
    /** Suggestions and recents are only worth showing before the user types their own query. */
    val showVariants: Boolean get() = query.isBlank() && variants.isNotEmpty()
    val showSuggestions: Boolean get() = query.isBlank() && suggestions.isNotEmpty()
    val showRecents: Boolean get() = query.isBlank() && recents.isNotEmpty()
}
