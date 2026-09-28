package dev.foodtracker.domain.nutrition

import dev.foodtracker.core.model.MeasurementUnit
import kotlin.math.abs
import kotlin.math.pow

/** One recorded correction: what the app proposed, and what the user changed it to. */
data class PortionCorrection(
    val foodKey: String,
    val unit: MeasurementUnit,
    val estimatedGrams: Double,
    val correctedGrams: Double,
    val recordedAtMillis: Long,
)

/** What the app has learned about how this person actually portions one food. */
data class LearnedPortion(
    val foodKey: String,
    val unit: MeasurementUnit,
    val grams: Double,
    val sampleCount: Int,
)

/**
 * Biases future portion estimates toward the user's own corrections.
 *
 * The design constraint that matters: one correction is weak evidence and ten is strong, so the
 * blend has to start cautious and tighten as samples accumulate. Jumping straight to the first
 * correction would make a single mis-tap permanently distort a food; ignoring corrections until
 * some arbitrary threshold would make the feature feel broken. [confidenceAfter] ramps between
 * those, and a decaying weight means a portion size the user has moved on from fades out.
 */
class PortionLearner(
    private val maxConfidence: Double = 0.85,
    private val halfLifeMillis: Long = THIRTY_DAYS_MILLIS,
) {

    /**
     * Collapses a food's correction history into one learned mass per unit.
     * Corrections are weighted by recency, so recent behaviour dominates without older data
     * being thrown away outright.
     */
    fun learn(corrections: List<PortionCorrection>, nowMillis: Long): List<LearnedPortion> =
        corrections
            .groupBy { it.foodKey to it.unit }
            .mapNotNull { (key, group) ->
                val weighted = group.sumOf { it.correctedGrams * recencyWeight(it, nowMillis) }
                val weights = group.sumOf { recencyWeight(it, nowMillis) }
                if (weights <= 0.0) return@mapNotNull null
                LearnedPortion(
                    foodKey = key.first,
                    unit = key.second,
                    grams = weighted / weights,
                    sampleCount = group.size,
                )
            }

    /**
     * Blends a fresh estimate with what we have learned. Returns the estimate unchanged when
     * there is nothing learned, so an unknown food is never silently nudged.
     */
    fun bias(estimatedGrams: Double, learned: LearnedPortion?): Double {
        if (learned == null || learned.sampleCount <= 0) return estimatedGrams
        val confidence = confidenceAfter(learned.sampleCount)
        return estimatedGrams * (1 - confidence) + learned.grams * confidence
    }

    /**
     * How far to trust the learned value, in 0..[maxConfidence]. Never reaches 1: the recogniser
     * is looking at *this* plate, and the learned value is only a habit, so a genuinely larger
     * serving must still be able to show through.
     */
    fun confidenceAfter(sampleCount: Int): Double {
        if (sampleCount <= 0) return 0.0
        // 1 sample -> 0.5 of max, 2 -> 0.67, 4 -> 0.8, approaching max as samples grow.
        return maxConfidence * (sampleCount / (sampleCount + 1.0))
    }

    /**
     * Whether a correction is worth recording at all. Nudging 150 g to 152 g says nothing about
     * the user's habits and would only dilute the signal.
     */
    fun isSignificant(correction: PortionCorrection): Boolean {
        if (correction.correctedGrams <= 0.0) return false
        val estimate = correction.estimatedGrams
        if (estimate <= 0.0) return true
        return abs(correction.correctedGrams - estimate) / estimate >= MIN_RELATIVE_CHANGE
    }

    private fun recencyWeight(correction: PortionCorrection, nowMillis: Long): Double {
        val age = (nowMillis - correction.recordedAtMillis).coerceAtLeast(0L)
        return 0.5.pow(age.toDouble() / halfLifeMillis.toDouble())
    }

    companion object {
        const val MIN_RELATIVE_CHANGE = 0.10
        const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Normalises a food label into a stable key, so "Grilled Chicken" and "grilled chicken" agree. */
fun foodKeyOf(name: String, brand: String? = null): String {
    val base = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    val brandPart = brand?.lowercase()?.replace(Regex("[^a-z0-9]+"), " ")?.trim()
    return if (brandPart.isNullOrBlank()) base else "$brandPart::$base"
}
