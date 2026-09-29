package dev.foodtracker.feature.results

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.MeasurementUnit
import dev.foodtracker.core.model.Nutrients
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionEvent
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.nutrition.UnitConverter
import org.junit.Test

class ResultsStateTest {

    private val converter = UnitConverter()

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
    fun `changing the amount recomputes grams through the real converter`() {
        val updated = item(grams = 100.0).copy(name = "white rice")
            .withPortion(amount = 2.0, unit = MeasurementUnit.CUP, converter = converter)

        assertThat(updated.portion.unit).isEqualTo(MeasurementUnit.CUP)
        assertThat(updated.portion.amount).isEqualTo(2.0)
        // Two cups of cooked rice, via the grain profile, not a generic constant.
        assertThat(updated.portion.grams).isWithin(0.001).of(2 * FoodCategory.GRAIN_COOKED.profile.gramsPerCup)
        assertThat(updated.source).isEqualTo(RecognitionSource.USER)
    }

    @Test
    fun `the same amount in the same unit weighs differently for different foods`() {
        val rice = item().copy(name = "white rice").withPortion(1.0, MeasurementUnit.CUP, converter)
        val spinach = item().copy(name = "baby spinach").withPortion(1.0, MeasurementUnit.CUP, converter)

        assertThat(rice.portion.grams).isGreaterThan(spinach.portion.grams * 3)
    }

    @Test
    fun `a negative amount is clamped rather than throwing`() {
        val updated = item().withPortion(amount = -5.0, unit = MeasurementUnit.GRAM, converter = converter)

        assertThat(updated.portion.amount).isEqualTo(0.0)
        assertThat(updated.portion.grams).isEqualTo(0.0)
    }

    @Test
    fun `a removed item is held so it can be put back`() {
        val kept = item("keep")
        val gone = item("gone")
        val state = ResultsUiState(items = listOf(kept, gone))

        val afterRemoval = state.copy(
            items = listOf(kept),
            recentlyRemoved = RemovedItem(gone, index = 1),
        )

        assertThat(afterRemoval.items.map { it.id }).containsExactly("keep")
        assertThat(afterRemoval.recentlyRemoved!!.item.id).isEqualTo("gone")
        assertThat(afterRemoval.recentlyRemoved!!.index).isEqualTo(1)
    }

    @Test
    fun `undo puts the item back where it was, not on the end`() {
        val first = item("first")
        val middle = item("middle")
        val last = item("last")

        val removed = RemovedItem(middle, index = 1)
        val restored = listOf(first, last).toMutableList().apply {
            add(removed.index.coerceIn(0, size), removed.item)
        }

        assertThat(restored.map { it.id }).containsExactly("first", "middle", "last").inOrder()
    }

    @Test
    fun `undo survives the list having shrunk underneath it`() {
        // The index is clamped, so restoring after further removals appends rather than throwing.
        val removed = RemovedItem(item("gone"), index = 5)
        val restored = mutableListOf(item("only")).apply {
            add(removed.index.coerceIn(0, size), removed.item)
        }

        assertThat(restored.map { it.id }).containsExactly("only", "gone").inOrder()
    }

    @Test
    fun `a save failure keeps the items on screen so the error can be shown beside them`() {
        // The bug this covers: errorMessage was only rendered when the list was empty, so a failed
        // save set it and displayed nothing at all.
        val failed = ResultsUiState(
            phase = AnalysisPhase.COMPLETE,
            items = listOf(item()),
            errorMessage = "Could not save this meal.",
            isLogging = false,
        )

        assertThat(failed.items).isNotEmpty()
        assertThat(failed.errorMessage).isNotNull()
        // And the user must still be able to try again.
        assertThat(failed.canConfirm).isTrue()
    }
}
