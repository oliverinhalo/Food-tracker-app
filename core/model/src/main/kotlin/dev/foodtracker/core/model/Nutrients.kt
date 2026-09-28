package dev.foodtracker.core.model

/**
 * Macro and energy values. Two distinct meanings share this type, kept apart by construction:
 * a [per100g] reference block as returned by a food database, and an absolute block for one
 * portion produced by [Nutrients.forGrams]. Mixing them up is the classic bug in this domain,
 * so the only way to get portion values is through that function.
 */
data class Nutrients(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
) {
    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        calories = calories + other.calories,
        proteinGrams = proteinGrams + other.proteinGrams,
        carbsGrams = carbsGrams + other.carbsGrams,
        fatGrams = fatGrams + other.fatGrams,
        fiberGrams = fiberGrams sumOrNull other.fiberGrams,
        sugarGrams = sugarGrams sumOrNull other.sugarGrams,
        sodiumMilligrams = sodiumMilligrams sumOrNull other.sodiumMilligrams,
    )

    operator fun times(factor: Double): Nutrients = Nutrients(
        calories = calories * factor,
        proteinGrams = proteinGrams * factor,
        carbsGrams = carbsGrams * factor,
        fatGrams = fatGrams * factor,
        fiberGrams = fiberGrams?.times(factor),
        sugarGrams = sugarGrams?.times(factor),
        sodiumMilligrams = sodiumMilligrams?.times(factor),
    )

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        /** Scales a per-100g reference block to an absolute portion. */
        fun forGrams(per100g: Nutrients, grams: Double): Nutrients {
            require(grams >= 0.0) { "grams must not be negative, was $grams" }
            return per100g * (grams / 100.0)
        }
    }
}

private infix fun Double?.sumOrNull(other: Double?): Double? =
    if (this == null && other == null) null else (this ?: 0.0) + (other ?: 0.0)

/** Sums a collection of already-scaled portion nutrients, e.g. for the sheet totals. */
fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
