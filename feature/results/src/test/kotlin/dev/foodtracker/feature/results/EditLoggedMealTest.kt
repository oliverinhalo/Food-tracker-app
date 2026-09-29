package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.data.diary.LoggedFood
import dev.foodtracker.domain.nutrition.UnitConverter
import org.junit.Test

/**
 * Reopening a logged meal converts its stored per-portion nutrition back to per-100g, because the
 * sheet scales by grams. Getting this wrong would scale already-scaled numbers, so a meal would
 * change its calories just by being opened.
 */
class EditLoggedMealTest {

    private val converter = UnitConverter()

    private fun logged(
        grams: Double,
        calories: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
    ) = LoggedFood(
        id = "item-1",
        name = "chicken breast",
        brand = null,
        portion = Portion.ofGrams(grams),
        nutrients = Nutrients(calories, protein, carbs, fat),
    )

    @Test
    fun `opening a logged meal does not change its calories`() {
        // 250 g logged at 412.5 kcal must still read 412.5 kcal when reopened.
        val item = logged(grams = 250.0, calories = 412.5, protein = 77.5).toDetectedItem()

        assertThat(item.nutrients!!.calories).isWithin(0.001).of(412.5)
        assertThat(item.nutrients!!.proteinGrams).isWithin(0.001).of(77.5)
    }

    @Test
    fun `per-100g values are recovered so editing the amount rescales correctly`() {
        val item = logged(grams = 250.0, calories = 412.5).toDetectedItem()

        // 412.5 kcal for 250 g is 165 kcal per 100 g.
        assertThat(item.nutrientsPer100g!!.calories).isWithin(0.001).of(165.0)

        val halved = item.withPortion(125.0, MeasurementUnit.GRAM, converter)
        assertThat(halved.nutrients!!.calories).isWithin(0.001).of(206.25)
    }

    @Test
    fun `an item logged with no nutrition stays without it rather than dividing by zero`() {
        val item = LoggedFood(
            id = "item-2",
            name = "mystery",
            brand = null,
            portion = Portion.ofGrams(0.0),
            nutrients = Nutrients.ZERO,
        ).toDetectedItem()

        assertThat(item.nutrientsPer100g).isNull()
        assertThat(item.nutrients).isNull()
    }

    @Test
    fun `a reopened item counts as user-entered so a stale lookup cannot overwrite it`() {
        assertThat(logged(100.0, 165.0).toDetectedItem().source).isEqualTo(RecognitionSource.USER)
    }

    @Test
    fun `the portion is preserved exactly`() {
        val original = Portion(amount = 2.0, unit = MeasurementUnit.CUP, grams = 370.0)
        val item = logged(370.0, 480.0).copy(portion = original).toDetectedItem()

        assertThat(item.portion).isEqualTo(original)
    }

    @Test
    fun `editing state is reflected in what the sheet can do`() {
        val editing = ResultsUiState(
            phase = AnalysisPhase.COMPLETE,
            items = listOf(logged(100.0, 165.0).toDetectedItem()),
            editingMealId = "meal-1",
        )

        assertThat(editing.isEditing).isTrue()
        assertThat(editing.canConfirm).isTrue()
        assertThat(ResultsUiState().isEditing).isFalse()
    }
}
