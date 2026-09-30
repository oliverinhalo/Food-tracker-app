package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Portion
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.nutrition.UnitConverter
import org.junit.Test

/**
 * The metric/imperial setting used to change nothing at all. These cover what it now decides:
 * which unit a plain mass is shown in, and what the picker offers first.
 */
class DisplayUnitsTest {

    private val converter = UnitConverter()

    @Test
    fun `imperial puts ounces first, metric puts grams first`() {
        assertThat(MeasurementUnit.pickerOrderFor(imperial = true).first()).isEqualTo(MeasurementUnit.OUNCE)
        assertThat(MeasurementUnit.pickerOrderFor(imperial = false).first()).isEqualTo(MeasurementUnit.GRAM)
    }

    @Test
    fun `neither ordering loses a unit`() {
        listOf(true, false).forEach { imperial ->
            assertThat(MeasurementUnit.pickerOrderFor(imperial))
                .containsExactlyElementsIn(MeasurementUnit.entries)
        }
    }

    @Test
    fun `the default mass unit follows the system`() {
        assertThat(MeasurementUnit.massUnitFor(imperial = true)).isEqualTo(MeasurementUnit.OUNCE)
        assertThat(MeasurementUnit.massUnitFor(imperial = false)).isEqualTo(MeasurementUnit.GRAM)
    }

    @Test
    fun `switching the display unit does not change how much was logged`() {
        val metric = Portion.ofGrams(150.0)

        val imperial = converter.convert(
            metric,
            MeasurementUnit.OUNCE,
            FoodCategory.profileFor("chicken breast"),
            "chicken breast",
        )

        assertThat(imperial.unit).isEqualTo(MeasurementUnit.OUNCE)
        assertThat(imperial.amount).isWithin(0.05).of(5.29)
        // The whole point: the mass that feeds the calorie maths is untouched.
        assertThat(imperial.grams).isWithin(0.01).of(150.0)
    }

    @Test
    fun `a portion described as a cup is left alone`() {
        // Converting "1 cup" to 168 g would throw away the more useful description, so only plain
        // masses follow the setting.
        assertThat(MeasurementUnit.CUP.isAbsoluteMass).isFalse()
        assertThat(MeasurementUnit.PIECE.isAbsoluteMass).isFalse()
        assertThat(MeasurementUnit.GRAM.isAbsoluteMass).isTrue()
        assertThat(MeasurementUnit.OUNCE.isAbsoluteMass).isTrue()
    }
}
