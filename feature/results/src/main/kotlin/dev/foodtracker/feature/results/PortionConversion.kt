package dev.foodtracker.feature.results

import dev.foodtracker.core.model.MeasurementUnit

/**
 * Interim unit conversion for phase 1.
 *
 * Phase 2 replaces this with the real `:domain:nutrition` converter backed by per-food density and
 * typical-serving tables. Until those exist, the volume and count units use a single generic
 * density so the stepper and slider still behave sensibly; only grams and ounces are exact here.
 */
internal object PortionConversion {

    private const val GRAMS_PER_OUNCE = 28.349523125

    /** Rough mixed-food density. Deliberately one number: a wrong-but-stable guess beats a lookup
     *  that silently changes a logged portion when the real tables land. */
    private const val GENERIC_GRAMS_PER_CUP = 150.0
    private const val GENERIC_GRAMS_PER_TABLESPOON = GENERIC_GRAMS_PER_CUP / 16.0
    private const val GENERIC_GRAMS_PER_PIECE = 80.0
    private const val GENERIC_GRAMS_PER_SERVING = 100.0

    fun toGrams(amount: Double, unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> amount
        MeasurementUnit.OUNCE -> amount * GRAMS_PER_OUNCE
        MeasurementUnit.CUP -> amount * GENERIC_GRAMS_PER_CUP
        MeasurementUnit.TABLESPOON -> amount * GENERIC_GRAMS_PER_TABLESPOON
        MeasurementUnit.PIECE -> amount * GENERIC_GRAMS_PER_PIECE
        MeasurementUnit.SERVING -> amount * GENERIC_GRAMS_PER_SERVING
    }

    fun fromGrams(grams: Double, unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> grams
        MeasurementUnit.OUNCE -> grams / GRAMS_PER_OUNCE
        MeasurementUnit.CUP -> grams / GENERIC_GRAMS_PER_CUP
        MeasurementUnit.TABLESPOON -> grams / GENERIC_GRAMS_PER_TABLESPOON
        MeasurementUnit.PIECE -> grams / GENERIC_GRAMS_PER_PIECE
        MeasurementUnit.SERVING -> grams / GENERIC_GRAMS_PER_SERVING
    }

    /** Step size that feels right for each unit when tapping +/-. */
    fun stepFor(unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> 5.0
        MeasurementUnit.OUNCE -> 0.5
        MeasurementUnit.CUP -> 0.25
        MeasurementUnit.TABLESPOON -> 0.5
        MeasurementUnit.PIECE -> 1.0
        MeasurementUnit.SERVING -> 0.5
    }

    /** Upper bound of the slider, chosen so a typical portion sits near the middle of the track. */
    fun sliderMaxFor(unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> 600.0
        MeasurementUnit.OUNCE -> 24.0
        MeasurementUnit.CUP -> 5.0
        MeasurementUnit.TABLESPOON -> 16.0
        MeasurementUnit.PIECE -> 10.0
        MeasurementUnit.SERVING -> 5.0
    }
}
