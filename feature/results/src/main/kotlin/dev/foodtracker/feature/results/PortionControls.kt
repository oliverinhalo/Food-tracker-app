package dev.foodtracker.feature.results

import dev.foodtracker.core.model.MeasurementUnit

/**
 * Presentation-only sizing for the stepper and slider. The actual gram conversion lives in
 * `:domain:nutrition`; these are just what feels right to drag.
 */
internal object PortionControls {

    /** Step size when tapping + or -. */
    fun stepFor(unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> 5.0
        MeasurementUnit.OUNCE -> 0.5
        MeasurementUnit.CUP -> 0.25
        MeasurementUnit.TABLESPOON -> 0.5
        MeasurementUnit.PIECE -> 1.0
        MeasurementUnit.SERVING -> 0.5
    }

    /** Slider upper bound, chosen so a typical portion sits near the middle of the track. */
    fun sliderMaxFor(unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> 600.0
        MeasurementUnit.OUNCE -> 24.0
        MeasurementUnit.CUP -> 5.0
        MeasurementUnit.TABLESPOON -> 16.0
        MeasurementUnit.PIECE -> 10.0
        MeasurementUnit.SERVING -> 5.0
    }
}
