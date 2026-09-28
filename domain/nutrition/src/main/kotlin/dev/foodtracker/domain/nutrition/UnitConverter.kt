package dev.foodtracker.domain.nutrition

import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Portion

/**
 * Converts between the units the user can pick and the grams every nutrition calculation runs on.
 *
 * Grams are the single source of truth: a [Portion] always carries its resolved mass, so changing
 * the display unit never changes how much food was logged.
 */
class UnitConverter(
    private val overrides: PortionOverrideStore = PortionOverrideStore.EMPTY,
) {

    fun toGrams(amount: Double, unit: MeasurementUnit, profile: PortionProfile, foodKey: String? = null): Double {
        require(amount >= 0) { "amount must not be negative, was $amount" }
        val effective = effectiveProfile(profile, foodKey, unit)
        return amount * gramsPerUnit(unit, effective)
    }

    fun fromGrams(grams: Double, unit: MeasurementUnit, profile: PortionProfile, foodKey: String? = null): Double {
        require(grams >= 0) { "grams must not be negative, was $grams" }
        val effective = effectiveProfile(profile, foodKey, unit)
        return grams / gramsPerUnit(unit, effective)
    }

    /** Re-expresses a portion in a different unit, preserving its mass exactly. */
    fun convert(portion: Portion, to: MeasurementUnit, profile: PortionProfile, foodKey: String? = null): Portion =
        Portion(
            amount = fromGrams(portion.grams, to, profile, foodKey),
            unit = to,
            grams = portion.grams,
            // The original phrasing described the original amount, so it no longer applies.
            householdDescription = null,
        )

    /** Builds a portion from a user-entered amount, resolving its mass. */
    fun portionOf(amount: Double, unit: MeasurementUnit, profile: PortionProfile, foodKey: String? = null): Portion =
        Portion(
            amount = amount,
            unit = unit,
            grams = toGrams(amount, unit, profile, foodKey),
        )

    private fun gramsPerUnit(unit: MeasurementUnit, profile: PortionProfile): Double = when (unit) {
        MeasurementUnit.GRAM -> 1.0
        MeasurementUnit.OUNCE -> GRAMS_PER_OUNCE
        MeasurementUnit.CUP -> profile.gramsPerCup
        MeasurementUnit.TABLESPOON -> profile.gramsPerTablespoon
        MeasurementUnit.PIECE -> profile.gramsPerPiece
        MeasurementUnit.SERVING -> profile.gramsPerServing
    }

    /**
     * A user correction for this exact food beats the category table. Mass units are never
     * overridden -- an ounce is an ounce, and letting a correction change it would silently
     * corrupt every previously logged portion.
     */
    private fun effectiveProfile(profile: PortionProfile, foodKey: String?, unit: MeasurementUnit): PortionProfile {
        if (foodKey == null || unit.isAbsoluteMass) return profile
        val learned = overrides.gramsPerUnit(foodKey, unit) ?: return profile
        return when (unit) {
            MeasurementUnit.CUP -> profile.copy(gramsPerCup = learned)
            MeasurementUnit.TABLESPOON -> profile.copy(gramsPerTablespoon = learned)
            MeasurementUnit.PIECE -> profile.copy(gramsPerPiece = learned)
            MeasurementUnit.SERVING -> profile.copy(gramsPerServing = learned)
            MeasurementUnit.GRAM, MeasurementUnit.OUNCE -> profile
        }
    }

    companion object {
        /** The international avoirdupois ounce, exactly. */
        const val GRAMS_PER_OUNCE = 28.349523125
    }
}

/** Per-food, per-unit masses learned from the user's own corrections. */
interface PortionOverrideStore {
    fun gramsPerUnit(foodKey: String, unit: MeasurementUnit): Double?

    companion object {
        val EMPTY = object : PortionOverrideStore {
            override fun gramsPerUnit(foodKey: String, unit: MeasurementUnit): Double? = null
        }
    }
}
