package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.MeasurementUnit
import org.junit.Test

class PortionLearnerTest {

    private val learner = PortionLearner()
    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun correction(
        corrected: Double,
        estimated: Double = 100.0,
        ageDays: Long = 0,
        unit: MeasurementUnit = MeasurementUnit.GRAM,
        foodKey: String = "porridge",
    ) = PortionCorrection(
        foodKey = foodKey,
        unit = unit,
        estimatedGrams = estimated,
        correctedGrams = corrected,
        recordedAtMillis = now - ageDays * day,
    )

    @Test
    fun `with nothing learned the estimate is returned untouched`() {
        assertThat(learner.bias(estimatedGrams = 150.0, learned = null)).isEqualTo(150.0)
    }

    @Test
    fun `a single correction moves the estimate but does not replace it`() {
        val learned = LearnedPortion("porridge", MeasurementUnit.GRAM, grams = 300.0, sampleCount = 1)

        val biased = learner.bias(estimatedGrams = 100.0, learned = learned)

        // Must move toward 300, but one data point should not be taken as gospel.
        assertThat(biased).isGreaterThan(100.0)
        assertThat(biased).isLessThan(300.0)
    }

    @Test
    fun `more corrections pull the estimate closer to the learned value`() {
        val few = LearnedPortion("porridge", MeasurementUnit.GRAM, grams = 300.0, sampleCount = 1)
        val many = LearnedPortion("porridge", MeasurementUnit.GRAM, grams = 300.0, sampleCount = 10)

        assertThat(learner.bias(100.0, many)).isGreaterThan(learner.bias(100.0, few))
    }

    @Test
    fun `learning never fully overrides what the camera actually sees`() {
        val learned = LearnedPortion("porridge", MeasurementUnit.GRAM, grams = 300.0, sampleCount = 10_000)

        // A genuinely bigger serving must still be able to show through.
        assertThat(learner.bias(1000.0, learned)).isGreaterThan(300.0)
        assertThat(learner.confidenceAfter(10_000)).isLessThan(1.0)
    }

    @Test
    fun `confidence grows with samples and starts at zero`() {
        assertThat(learner.confidenceAfter(0)).isEqualTo(0.0)
        assertThat(learner.confidenceAfter(1)).isLessThan(learner.confidenceAfter(2))
        assertThat(learner.confidenceAfter(2)).isLessThan(learner.confidenceAfter(8))
    }

    @Test
    fun `corrections are averaged per food and unit`() {
        val learned = learner.learn(
            listOf(
                correction(corrected = 200.0),
                correction(corrected = 300.0),
                correction(corrected = 250.0, foodKey = "rice"),
            ),
            nowMillis = now,
        )

        val porridge = learned.single { it.foodKey == "porridge" }
        assertThat(porridge.grams).isWithin(0.001).of(250.0)
        assertThat(porridge.sampleCount).isEqualTo(2)
        assertThat(learned.single { it.foodKey == "rice" }.grams).isEqualTo(250.0)
    }

    @Test
    fun `the same food in different units is learned separately`() {
        val learned = learner.learn(
            listOf(
                correction(corrected = 200.0, unit = MeasurementUnit.GRAM),
                correction(corrected = 60.0, unit = MeasurementUnit.CUP),
            ),
            nowMillis = now,
        )

        assertThat(learned).hasSize(2)
        assertThat(learned.map { it.unit }).containsExactly(MeasurementUnit.GRAM, MeasurementUnit.CUP)
    }

    @Test
    fun `recent corrections outweigh old ones`() {
        val learned = learner.learn(
            listOf(
                correction(corrected = 100.0, ageDays = 365),
                correction(corrected = 300.0, ageDays = 0),
            ),
            nowMillis = now,
        ).single()

        // A plain mean would be 200; recency weighting must favour the recent 300.
        assertThat(learned.grams).isGreaterThan(280.0)
    }

    @Test
    fun `a trivial nudge is not recorded as a habit`() {
        assertThat(learner.isSignificant(correction(estimated = 150.0, corrected = 152.0))).isFalse()
        assertThat(learner.isSignificant(correction(estimated = 150.0, corrected = 220.0))).isTrue()
    }

    @Test
    fun `a correction to zero is never treated as a habit`() {
        assertThat(learner.isSignificant(correction(estimated = 150.0, corrected = 0.0))).isFalse()
    }

    @Test
    fun `food keys normalise case punctuation and brand`() {
        assertThat(foodKeyOf("Grilled Chicken!")).isEqualTo(foodKeyOf("grilled   chicken"))
        assertThat(foodKeyOf("yoghurt", brand = "Fage")).isNotEqualTo(foodKeyOf("yoghurt"))
        assertThat(foodKeyOf("yoghurt", brand = "FAGE")).isEqualTo(foodKeyOf("yoghurt", brand = "fage"))
    }
}
