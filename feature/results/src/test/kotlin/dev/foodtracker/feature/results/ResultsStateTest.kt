package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionEvent
import dev.foodtracker.core.model.RecognitionSource
import org.junit.Test

class ResultsStateTest {

    private fun item(
        id: String = "1",
        grams: Double = 100.0,
        per100g: Nutrients? = Nutrients(calories = 200.0, proteinGrams = 10.0, carbsGrams = 20.0, fatGrams = 8.0),
    ) = DetectedItem(
        id = id,
        name = "test food",
        confidence = 0.9f,
        portion = Portion.ofGrams(grams),
        source = RecognitionSource.CLOUD,
        nutrientsPer100g = per100g,
    )

    @Test
    fun `totals scale with the portion`() {
        val state = ResultsUiState(items = listOf(item(grams = 250.0)))

        // 250g of a 200kcal/100g food is 500 kcal.
        assertThat(state.totals.calories).isWithin(0.001).of(500.0)
        assertThat(state.totals.proteinGrams).isWithin(0.001).of(25.0)
    }

    @Test
    fun `totals sum across items`() {
        val state = ResultsUiState(items = listOf(item("1", 100.0), item("2", 50.0)))

        assertThat(state.totals.calories).isWithin(0.001).of(300.0)
    }

    @Test
    fun `items still awaiting nutrition contribute nothing to the totals`() {
        val state = ResultsUiState(items = listOf(item("1", 100.0), item("2", 100.0, per100g = null)))

        assertThat(state.totals.calories).isWithin(0.001).of(200.0)
        assertThat(state.hasResolvedNutrition).isTrue()
    }

    @Test
    fun `an empty list cannot be confirmed`() {
        assertThat(ResultsUiState().canConfirm).isFalse()
    }

    @Test
    fun `confirm is blocked while a log is in flight`() {
        val state = ResultsUiState(items = listOf(item()), isLogging = true)
        assertThat(state.canConfirm).isFalse()
    }

    @Test
    fun `provisional results move the sheet into the refining state`() {
        val state = ResultsUiState().reduce(RecognitionEvent.Provisional(listOf(item())))

        assertThat(state.phase).isEqualTo(AnalysisPhase.PROVISIONAL)
        assertThat(state.isRefining).isTrue()
    }

    @Test
    fun `refined results replace provisional ones and clear any warning`() {
        val state = ResultsUiState(degradeReason = DegradeReason.OFFLINE)
            .reduce(RecognitionEvent.Refined(listOf(item("cloud"))))

        assertThat(state.phase).isEqualTo(AnalysisPhase.COMPLETE)
        assertThat(state.items.single().id).isEqualTo("cloud")
        assertThat(state.degradeReason).isNull()
        assertThat(state.isRefining).isFalse()
    }

    @Test
    fun `degrading keeps the items and explains why`() {
        val state = ResultsUiState().reduce(
            RecognitionEvent.Degraded(DegradeReason.RATE_LIMITED, listOf(item())),
        )

        assertThat(state.phase).isEqualTo(AnalysisPhase.COMPLETE)
        assertThat(state.items).hasSize(1)
        assertThat(state.degradeReason).isEqualTo(DegradeReason.RATE_LIMITED)
        assertThat(state.canConfirm).isTrue()
    }

    @Test
    fun `a failure surfaces a message and cannot be confirmed`() {
        val state = ResultsUiState().reduce(
            RecognitionEvent.Failed(DegradeReason.NO_API_KEY, "No key"),
        )

        assertThat(state.phase).isEqualTo(AnalysisPhase.FAILED)
        assertThat(state.errorMessage).isEqualTo("No key")
        assertThat(state.canConfirm).isFalse()
    }

    @Test
    fun `changing the amount recomputes grams`() {
        val updated = item(grams = 100.0).withPortion(amount = 2.0, unit = MeasurementUnit.CUP)

        assertThat(updated.portion.unit).isEqualTo(MeasurementUnit.CUP)
        assertThat(updated.portion.amount).isEqualTo(2.0)
        assertThat(updated.portion.grams).isGreaterThan(100.0)
        assertThat(updated.source).isEqualTo(RecognitionSource.USER)
    }

    @Test
    fun `a negative amount is clamped rather than throwing`() {
        val updated = item().withPortion(amount = -5.0, unit = MeasurementUnit.GRAM)

        assertThat(updated.portion.amount).isEqualTo(0.0)
        assertThat(updated.portion.grams).isEqualTo(0.0)
    }

    @Test
    fun `the recogniser's household phrasing is dropped once the user edits the amount`() {
        val original = item().copy(
            portion = Portion(amount = 1.0, unit = MeasurementUnit.CUP, grams = 150.0, householdDescription = "1 cup"),
        )

        val updated = original.withPortion(amount = 3.0, unit = MeasurementUnit.CUP)

        assertThat(updated.portion.householdDescription).isNull()
    }
}
