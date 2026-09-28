package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Portion
import org.junit.Test

class UnitConverterTest {

    private val converter = UnitConverter()
    private val generic = PortionProfile.GENERIC

    @Test
    fun `grams convert to themselves`() {
        assertThat(converter.toGrams(137.0, MeasurementUnit.GRAM, generic)).isEqualTo(137.0)
    }

    @Test
    fun `an ounce is the exact avoirdupois value`() {
        assertThat(converter.toGrams(1.0, MeasurementUnit.OUNCE, generic)).isWithin(1e-9).of(28.349523125)
    }

    @Test
    fun `every unit round-trips through grams for every category`() {
        val profiles = FoodCategory.entries.map { it.profile } + PortionProfile.GENERIC
        for (profile in profiles) {
            for (unit in MeasurementUnit.entries) {
                val grams = converter.toGrams(2.5, unit, profile)
                assertThat(converter.fromGrams(grams, unit, profile)).isWithin(1e-9).of(2.5)
            }
        }
    }

    @Test
    fun `a cup of leaves weighs far less than a cup of rice`() {
        val leaves = converter.toGrams(1.0, MeasurementUnit.CUP, FoodCategory.LEAFY_VEGETABLE.profile)
        val rice = converter.toGrams(1.0, MeasurementUnit.CUP, FoodCategory.GRAIN_COOKED.profile)

        // This factor is the whole reason per-food profiles exist; a single constant cannot serve both.
        assertThat(rice / leaves).isGreaterThan(5.0)
    }

    @Test
    fun `changing the display unit preserves the logged mass exactly`() {
        val original = Portion.ofGrams(185.0)

        val asCups = converter.convert(original, MeasurementUnit.CUP, FoodCategory.GRAIN_COOKED.profile)

        assertThat(asCups.grams).isEqualTo(185.0)
        assertThat(asCups.unit).isEqualTo(MeasurementUnit.CUP)
        assertThat(asCups.amount).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `converting a portion drops phrasing that described the old amount`() {
        val original = Portion(amount = 1.0, unit = MeasurementUnit.CUP, grams = 185.0, householdDescription = "1 cup")

        val asGrams = converter.convert(original, MeasurementUnit.GRAM, FoodCategory.GRAIN_COOKED.profile)

        assertThat(asGrams.householdDescription).isNull()
    }

    @Test
    fun `a learned override replaces the category value for that food only`() {
        val store = object : PortionOverrideStore {
            override fun gramsPerUnit(foodKey: String, unit: MeasurementUnit): Double? =
                if (foodKey == "my cereal" && unit == MeasurementUnit.CUP) 65.0 else null
        }
        val learning = UnitConverter(store)
        val profile = FoodCategory.CEREAL_DRY.profile

        assertThat(learning.toGrams(1.0, MeasurementUnit.CUP, profile, foodKey = "my cereal")).isEqualTo(65.0)
        assertThat(learning.toGrams(1.0, MeasurementUnit.CUP, profile, foodKey = "other cereal"))
            .isEqualTo(profile.gramsPerCup)
    }

    @Test
    fun `a learned override can never redefine a mass unit`() {
        val store = object : PortionOverrideStore {
            override fun gramsPerUnit(foodKey: String, unit: MeasurementUnit) = 999.0
        }
        val learning = UnitConverter(store)

        // Letting a correction change what an ounce means would corrupt every past log.
        assertThat(learning.toGrams(1.0, MeasurementUnit.OUNCE, generic, foodKey = "anything"))
            .isWithin(1e-9).of(UnitConverter.GRAMS_PER_OUNCE)
        assertThat(learning.toGrams(50.0, MeasurementUnit.GRAM, generic, foodKey = "anything")).isEqualTo(50.0)
    }

    @Test
    fun `a negative amount is rejected rather than silently logged`() {
        val error = runCatching { converter.toGrams(-1.0, MeasurementUnit.GRAM, generic) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
