package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.Nutrients
import org.junit.Test

class NutritionCalculatorTest {

    private fun nutrients(kcal: Double, p: Double, c: Double, f: Double) =
        Nutrients(calories = kcal, proteinGrams = p, carbsGrams = c, fatGrams = f)

    @Test
    fun `per-100g values scale to the portion`() {
        val per100g = nutrients(165.0, 31.0, 0.0, 3.6)

        val portion = NutritionCalculator.forPortion(per100g, grams = 250.0)

        assertThat(portion.calories).isWithin(0.001).of(412.5)
        assertThat(portion.proteinGrams).isWithin(0.001).of(77.5)
    }

    @Test
    fun `a zero-gram portion contributes nothing`() {
        val portion = NutritionCalculator.forPortion(nutrients(165.0, 31.0, 0.0, 3.6), grams = 0.0)
        assertThat(portion.calories).isEqualTo(0.0)
    }

    @Test
    fun `atwater factors are applied`() {
        // 10g protein + 20g carbs + 9g fat = 40 + 80 + 81 = 201 kcal
        assertThat(NutritionCalculator.caloriesFromMacros(nutrients(0.0, 10.0, 20.0, 9.0)))
            .isWithin(0.001).of(201.0)
    }

    @Test
    fun `a consistent row is accepted`() {
        assertThat(NutritionCalculator.energyLooksConsistent(nutrients(165.0, 31.0, 0.0, 3.6))).isTrue()
    }

    @Test
    fun `a row with kilojoules in the calorie field is rejected`() {
        // 2228 kJ is 532 kcal; a row stating 2228 "kcal" against these macros is wrong by 4x.
        assertThat(NutritionCalculator.energyLooksConsistent(nutrients(2228.0, 6.3, 57.5, 30.9))).isFalse()
    }

    @Test
    fun `an implausible energy is replaced by the computed one`() {
        val bad = nutrients(2228.0, 6.3, 57.5, 30.9)

        val fixed = NutritionCalculator.withReconciledEnergy(bad)

        assertThat(fixed.calories).isWithin(1.0).of(533.0)
        // Macros must be left exactly as the database stated them.
        assertThat(fixed.proteinGrams).isEqualTo(6.3)
        assertThat(fixed.carbsGrams).isEqualTo(57.5)
        assertThat(fixed.fatGrams).isEqualTo(30.9)
    }

    @Test
    fun `a good row passes through reconciliation untouched`() {
        val good = nutrients(165.0, 31.0, 0.0, 3.6)
        assertThat(NutritionCalculator.withReconciledEnergy(good)).isEqualTo(good)
    }

    @Test
    fun `black coffee is not flagged just because it has no macros`() {
        assertThat(NutritionCalculator.energyLooksConsistent(nutrients(2.0, 0.1, 0.0, 0.0))).isTrue()
    }

    @Test
    fun `a calorie-dense row with no macros at all is flagged`() {
        assertThat(NutritionCalculator.energyLooksConsistent(nutrients(400.0, 0.0, 0.0, 0.0))).isFalse()
    }

    @Test
    fun `a food with energy but no macro breakdown keeps its calories`() {
        // Regression: reconciliation replaced the stated energy with the computed zero, so any
        // Open Food Facts row carrying energy without macros -- very common -- logged as 0 kcal.
        val energyOnly = nutrients(250.0, 0.0, 0.0, 0.0)

        assertThat(NutritionCalculator.withReconciledEnergy(energyOnly).calories).isEqualTo(250.0)
    }

    @Test
    fun `a row with macros is still corrected`() {
        val kilojoulesInTheCalorieField = nutrients(2228.0, 6.3, 57.5, 30.9)

        assertThat(NutritionCalculator.withReconciledEnergy(kilojoulesInTheCalorieField).calories)
            .isWithin(2.0).of(533.0)
    }

    @Test
    fun `a genuinely zero-calorie food stays at zero`() {
        val water = nutrients(0.0, 0.0, 0.0, 0.0)

        assertThat(NutritionCalculator.withReconciledEnergy(water).calories).isEqualTo(0.0)
    }
}
