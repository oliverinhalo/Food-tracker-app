package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The cases here are not invented: they are the actual top results USDA and Open Food Facts
 * returned for ordinary queries, in the order they returned them. Each one was a wrong number the
 * app would have logged.
 */
class FoodMatcherTest {

    private val matcher = FoodMatcher()

    private fun candidate(name: String, kcal: Double, branded: Boolean = false) =
        MatchCandidate(value = name, name = name, caloriesPer100g = kcal, isBranded = branded)

    @Test
    fun `steamed corn is not an acceptable answer for steamed broccoli`() {
        // USDA's real ranking for "steamed broccoli": the corn row came first, at 11x the calories.
        val usdaOrder = listOf(
            candidate("Corn, white, steamed (Navajo)", 386.0),
            candidate("Stew, steamed corn (Navajo)", 112.0),
            candidate("Broccoli, raw", 31.0),
            candidate("Broccoli, cooked, boiled, drained, with salt", 35.0),
            candidate("Lambsquarters, steamed (Northern Plains Indians)", 48.0),
        )

        val best = matcher.bestMatch("steamed broccoli", usdaOrder, cookingMethod = "steamed")

        assertThat(best).isEqualTo("Broccoli, cooked, boiled, drained, with salt")
    }

    @Test
    fun `rice flour is not an acceptable answer for white rice`() {
        val usdaOrder = listOf(
            candidate("Flour, rice, white, unenriched", 359.0),
            candidate("Rice flour, white, unenriched", 366.0),
            candidate("Rice, white, glutinous, unenriched, uncooked", 370.0),
            candidate("Rice, white, medium-grain, enriched, cooked", 130.0),
        )

        val best = matcher.bestMatch("white rice", usdaOrder, cookingMethod = "boiled")

        assertThat(best).isEqualTo("Rice, white, medium-grain, enriched, cooked")
    }

    @Test
    fun `an implausible energy for the category is rejected outright`() {
        // Even a perfect name match cannot make a leafy vegetable 386 kcal per 100g.
        val best = matcher.bestMatch("broccoli", listOf(candidate("Broccoli", 386.0)))
        assertThat(best).isNull()
    }

    @Test
    fun `a cooked row is preferred over a raw one when the plate shows cooking`() {
        val best = matcher.bestMatch(
            "steamed broccoli",
            listOf(candidate("Broccoli, raw", 31.0), candidate("Broccoli, cooked", 35.0)),
            cookingMethod = "steamed",
        )

        assertThat(best).isEqualTo("Broccoli, cooked")
    }

    @Test
    fun `a raw row is preferred when nothing was cooked`() {
        val best = matcher.bestMatch(
            "raw spinach",
            listOf(candidate("Spinach, cooked", 23.0), candidate("Spinach, raw", 23.0)),
            cookingMethod = "raw",
        )

        assertThat(best).isEqualTo("Spinach, raw")
    }

    @Test
    fun `a candidate sharing only a preparation word is not a match`() {
        // "steamed" is common to both, but corn is not broccoli.
        val best = matcher.bestMatch("steamed broccoli", listOf(candidate("Corn, steamed", 96.0)))
        assertThat(best).isNull()
    }

    @Test
    fun `nothing plausible returns null rather than the least bad guess`() {
        val best = matcher.bestMatch(
            "grilled chicken breast",
            listOf(candidate("Banana, raw", 89.0), candidate("Olive oil", 884.0)),
        )
        assertThat(best).isNull()
    }

    @Test
    fun `identity tokens drop preparation and keep the food`() {
        assertThat(matcher.identityTokens("grilled chicken breast")).containsExactly("chicken", "breast")
        assertThat(matcher.identityTokens("steamed broccoli")).containsExactly("broccoli")
    }

    @Test
    fun `branded rows are preferred only when a brand was asked for`() {
        val options = listOf(
            candidate("Greek yoghurt", 97.0, branded = false),
            candidate("Greek yoghurt", 97.0, branded = true),
        )

        assertThat(matcher.rank("greek yoghurt", options, preferBranded = true).first().isBranded).isTrue()
        assertThat(matcher.rank("greek yoghurt", options, preferBranded = false).first().isBranded).isFalse()
    }

    @Test
    fun `a dried form is not matched for a fresh food`() {
        val best = matcher.bestMatch(
            "apricot",
            listOf(candidate("Apricots, dried", 241.0), candidate("Apricots, raw", 48.0)),
        )
        assertThat(best).isEqualTo("Apricots, raw")
    }

    @Test
    fun `an empty query matches nothing`() {
        assertThat(matcher.bestMatch("", listOf(candidate("Broccoli, raw", 31.0)))).isNull()
    }

    @Test
    fun `plausibility bounds reject the real failures and accept the real foods`() {
        assertThat(FoodCategory.VEGETABLE.isPlausibleEnergy(386.0)).isFalse()
        assertThat(FoodCategory.VEGETABLE.isPlausibleEnergy(35.0)).isTrue()
        assertThat(FoodCategory.GRAIN_COOKED.isPlausibleEnergy(359.0)).isFalse()
        assertThat(FoodCategory.GRAIN_COOKED.isPlausibleEnergy(130.0)).isTrue()
        assertThat(FoodCategory.OIL_OR_FAT.isPlausibleEnergy(884.0)).isTrue()
        assertThat(FoodCategory.NUT_OR_SEED.isPlausibleEnergy(579.0)).isTrue()
    }
}
