package dev.foodtracker.domain.nutrition

import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.sum

/**
 * Turns per-100g reference values into the numbers shown on screen.
 *
 * Every food database in use here (USDA, Open Food Facts) publishes per-100g values, so the whole
 * app normalises to that at the data boundary and this is the only place the scaling happens.
 */
object NutritionCalculator {

    fun forPortion(per100g: Nutrients, grams: Double): Nutrients = Nutrients.forGrams(per100g, grams)

    /** Totals for a meal. Items whose nutrition has not resolved yet simply contribute nothing. */
    fun totals(items: List<DetectedItem>): Nutrients = items.mapNotNull { it.nutrients }.sum()

    /**
     * Energy from macros, used to sanity-check a database row before it reaches the user.
     * Atwater factors: 4 kcal/g protein and carbohydrate, 9 kcal/g fat.
     */
    fun caloriesFromMacros(nutrients: Nutrients): Double =
        nutrients.proteinGrams * 4 + nutrients.carbsGrams * 4 + nutrients.fatGrams * 9

    /**
     * Whether a row's stated energy is consistent with its macros.
     *
     * Open Food Facts is crowd-sourced and routinely carries rows where energy is out by an order
     * of magnitude (kJ entered in the kcal field, or per-serving values entered as per-100g). Such
     * a row would quietly wreck a day's total, so callers prefer a computed value over a stated one
     * that fails this check. The tolerance is wide because fibre, alcohol and polyols legitimately
     * shift the balance.
     */
    fun energyLooksConsistent(nutrients: Nutrients, tolerance: Double = 0.30): Boolean {
        val computed = caloriesFromMacros(nutrients)
        val difference = kotlin.math.abs(nutrients.calories - computed)

        // A ratio is the wrong test near zero: black coffee at 2 kcal against 0.4 kcal of macros
        // is a 5x ratio but a 1.6 kcal discrepancy, which cannot distort anything. What this check
        // exists to catch is order-of-magnitude errors, so small absolute gaps always pass.
        if (difference <= NEGLIGIBLE_KCAL_PER_100G) return true

        if (nutrients.calories <= 0.0) return false
        if (computed <= 0.0) return false

        val ratio = nutrients.calories / computed
        return ratio in (1 - tolerance)..(1 + tolerance)
    }

    /** Below this, a per-100g energy disagreement cannot meaningfully move a day's total. */
    private const val NEGLIGIBLE_KCAL_PER_100G = 20.0

    /**
     * Replaces an implausible stated energy with the Atwater figure, leaving macros untouched.
     *
     * A row with no macros at all is left alone: there is nothing to compute from, and substituting
     * the computed zero would log a real food as zero calories. Plenty of Open Food Facts entries
     * carry energy without a macro breakdown, so this is the common case, not the edge case.
     */
    fun withReconciledEnergy(nutrients: Nutrients): Nutrients {
        if (energyLooksConsistent(nutrients)) return nutrients
        val computed = caloriesFromMacros(nutrients)
        if (computed <= 0.0) return nutrients
        return nutrients.copy(calories = computed)
    }
}
