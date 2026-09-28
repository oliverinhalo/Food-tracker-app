package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.MeasurementUnit
import org.junit.Test

class PortionConversionTest {

    @Test
    fun `grams are the identity conversion`() {
        assertThat(PortionConversion.toGrams(137.0, MeasurementUnit.GRAM)).isEqualTo(137.0)
    }

    @Test
    fun `ounces use the exact avoirdupois factor`() {
        assertThat(PortionConversion.toGrams(1.0, MeasurementUnit.OUNCE)).isWithin(0.0001).of(28.349523125)
    }

    @Test
    fun `every unit round-trips through grams`() {
        MeasurementUnit.entries.forEach { unit ->
            val grams = PortionConversion.toGrams(3.0, unit)
            assertThat(PortionConversion.fromGrams(grams, unit)).isWithin(1e-9).of(3.0)
        }
    }

    @Test
    fun `a tablespoon is a sixteenth of a cup`() {
        val cup = PortionConversion.toGrams(1.0, MeasurementUnit.CUP)
        val tablespoon = PortionConversion.toGrams(16.0, MeasurementUnit.TABLESPOON)
        assertThat(tablespoon).isWithin(1e-9).of(cup)
    }

    @Test
    fun `every unit has a usable step and slider range`() {
        MeasurementUnit.entries.forEach { unit ->
            assertThat(PortionConversion.stepFor(unit)).isGreaterThan(0.0)
            assertThat(PortionConversion.sliderMaxFor(unit)).isGreaterThan(PortionConversion.stepFor(unit))
        }
    }
}
