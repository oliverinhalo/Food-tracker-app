package dev.foodtracker.domain.nutrition

import dev.foodtracker.core.text.TextSimilarity

/** A database row offered as a match, reduced to what matching actually needs. */
data class MatchCandidate<T>(
    val value: T,
    val name: String,
    val caloriesPer100g: Double,
    val isBranded: Boolean,
)

/**
 * Picks which database row actually is the food we asked for.
 *
 * Taking the first search result is not good enough, and not by a small margin. USDA's own search
 * answers "steamed broccoli" with "Corn, white, steamed" at 386 kcal/100g -- the real broccoli row
 * (35 kcal) is sixth -- and answers "white rice" with rice *flour* at 359 kcal when cooked rice is
 * 130. Logging the first hit is an 11x error on one and a 3x error on the other, which is exactly
 * the kind of wrongness that makes a calorie tracker useless without ever looking broken.
 *
 * So every candidate is scored: it has to be the same food (not merely share a word), its
 * preparation should agree, and its energy has to be physically plausible for what it claims to be.
 */
class FoodMatcher(
    private val minimumScore: Double = 0.42,
) {

    fun <T> bestMatch(
        query: String,
        candidates: List<MatchCandidate<T>>,
        cookingMethod: String? = null,
        preferBranded: Boolean = false,
    ): T? = rank(query, candidates, cookingMethod, preferBranded).firstOrNull()?.value

    /** Scored candidates, best first, with anything below [minimumScore] discarded. */
    fun <T> rank(
        query: String,
        candidates: List<MatchCandidate<T>>,
        cookingMethod: String? = null,
        preferBranded: Boolean = false,
    ): List<MatchCandidate<T>> {
        if (query.isBlank()) return emptyList()
        val category = FoodCategory.of(query)
        val identity = identityTokens(query)

        return candidates
            .map { it to score(query, identity, category, it, cookingMethod, preferBranded) }
            .filter { (_, score) -> score >= minimumScore }
            .sortedByDescending { (_, score) -> score }
            .map { (candidate, _) -> candidate }
    }

    internal fun <T> score(
        query: String,
        identity: Set<String>,
        category: FoodCategory?,
        candidate: MatchCandidate<T>,
        cookingMethod: String?,
        preferBranded: Boolean,
    ): Double {
        val candidateTokens = TextSimilarity.normalizeTokens(candidate.name).toSet()
        // TextSimilarity strips preparation words as noise, which is right for deciding *which
        // food* a row is but leaves nothing to judge its state by. Cooking comparisons therefore
        // run on the untouched words.
        val candidateWords = rawWords(candidate.name)

        // The food itself must be named. "Corn, steamed" shares "steamed" with "steamed broccoli"
        // and nothing else that matters, so without this it scores as a plausible match.
        if (identity.isNotEmpty() && identity.none { it in candidateTokens }) return 0.0

        // Energy has to make sense for what the food claims to be. This is the backstop that
        // catches a vegetable row at 386 kcal/100g however well its name happens to match.
        if (category != null && !category.isPlausibleEnergy(candidate.caloriesPer100g)) return 0.0

        var score = TextSimilarity.coverage(query, candidate.name).toDouble()

        // A row describing a different form of the ingredient is a different food: rice flour is
        // not rice, and dried apricot is not apricot.
        if (candidateWords.any { it in DISQUALIFYING_FORMS } && identity.none { it in DISQUALIFYING_FORMS }) {
            score -= 0.35
        }

        if (cookingMethod != null) {
            val method = cookingMethod.lowercase()
            val candidateSaysCooked = candidateWords.any { it in COOKED_WORDS }
            val candidateSaysRaw = candidateWords.any { it in RAW_WORDS }
            val wantsCooked = method !in RAW_WORDS

            when {
                candidateWords.contains(method) -> score += 0.20
                wantsCooked && candidateSaysCooked -> score += 0.25
                // "Broccoli, raw" when the plate clearly holds steamed broccoli.
                wantsCooked && candidateSaysRaw -> score -= 0.25
                !wantsCooked && candidateSaysCooked -> score -= 0.25
            }
        }

        // Generic foods belong to curated databases; branded rows are for barcoded products.
        score += if (candidate.isBranded == preferBranded) 0.05 else -0.05

        return score.coerceIn(0.0, 2.0)
    }

    /**
     * The words that say *which food* this is, with preparation and filler stripped out. What
     * remains is what a candidate must actually contain.
     */
    /** Words exactly as written, so preparation and form survive for the checks that need them. */
    private fun rawWords(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }.toSet()

    internal fun identityTokens(query: String): Set<String> =
        TextSimilarity.normalizeTokens(query)
            .filterNot { it in COOKED_WORDS || it in RAW_WORDS || it in DESCRIPTOR_WORDS }
            .toSet()

    private companion object {
        val COOKED_WORDS = setOf(
            "cooked", "boiled", "steamed", "grilled", "fried", "baked", "roasted", "poached",
            "sauteed", "braised", "toasted", "barbecued", "smoked", "stewed",
        )
        val RAW_WORDS = setOf("raw", "fresh", "uncooked", "dried", "dry")

        /** Words that say nothing about identity and would otherwise dilute the overlap score. */
        val DESCRIPTOR_WORDS = setOf(
            "hot", "cold", "warm", "large", "small", "medium", "homemade", "mixed",
        )

        /**
         * A processed form of an ingredient is a different food with wildly different energy.
         * Rice flour is 359 kcal/100g where cooked rice is 130.
         */
        val DISQUALIFYING_FORMS = setOf(
            "flour", "powder", "dried", "dehydrated", "concentrate", "extract", "oil", "syrup", "juice",
        )
    }
}

/**
 * Physically plausible energy ranges, per 100 g, for each category. Deliberately generous: these
 * exist to catch a row that is the wrong *food*, not to second-guess an unusual recipe.
 */
internal fun FoodCategory.isPlausibleEnergy(caloriesPer100g: Double): Boolean {
    if (caloriesPer100g < 0) return false
    val range = when (this) {
        FoodCategory.OIL_OR_FAT -> 250.0..950.0
        FoodCategory.SAUCE_OR_DRESSING -> 0.0..750.0
        FoodCategory.SOUP_OR_STEW -> 0.0..250.0
        FoodCategory.BEVERAGE -> 0.0..300.0
        FoodCategory.DAIRY_LIQUID -> 5.0..400.0
        FoodCategory.YOGHURT_OR_SOFT_DAIRY -> 15.0..300.0
        FoodCategory.CHEESE -> 30.0..500.0
        FoodCategory.BREAD_OR_BAKED -> 100.0..550.0
        FoodCategory.CEREAL_DRY -> 250.0..550.0
        // Cooked grains are mostly water. This is the bound that rejects dry rice and rice flour.
        FoodCategory.GRAIN_COOKED -> 40.0..220.0
        FoodCategory.POTATO_OR_ROOT -> 10.0..400.0
        FoodCategory.LEGUME -> 20.0..420.0
        FoodCategory.MEAT_OR_FISH -> 30.0..650.0
        FoodCategory.EGG -> 40.0..420.0
        FoodCategory.NUT_OR_SEED -> 300.0..800.0
        FoodCategory.FRUIT -> 10.0..400.0
        // This is what rejects "Corn, white, steamed" at 386 kcal for a broccoli query.
        FoodCategory.LEAFY_VEGETABLE -> 0.0..90.0
        FoodCategory.VEGETABLE -> 0.0..160.0
        FoodCategory.SNACK_OR_SWEET -> 100.0..700.0
    }
    return caloriesPer100g in range
}
