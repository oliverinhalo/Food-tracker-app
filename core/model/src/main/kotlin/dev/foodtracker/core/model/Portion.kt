package dev.foodtracker.core.model

/**
 * A quantity of food, always carrying its resolved mass in [grams] so nutrition maths never has to
 * re-run a unit conversion. [amount] and [unit] are what the user sees and edits;
 * [householdDescription] is the human phrasing the recogniser suggested, e.g. "1 cup, cooked".
 */
data class Portion(
    val amount: Double,
    val unit: MeasurementUnit,
    val grams: Double,
    val householdDescription: String? = null,
) {
    init {
        require(amount >= 0.0) { "amount must not be negative, was $amount" }
        require(grams >= 0.0) { "grams must not be negative, was $grams" }
    }

    companion object {
        fun ofGrams(grams: Double, householdDescription: String? = null): Portion =
            Portion(amount = grams, unit = MeasurementUnit.GRAM, grams = grams, householdDescription = householdDescription)
    }
}
