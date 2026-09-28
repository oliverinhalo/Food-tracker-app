package dev.foodtracker.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextSimilarityTest {

    @Test
    fun `word order and filler words do not matter`() {
        val a = TextSimilarity.similarity("grilled chicken breast", "chicken breast, grilled")
        assertThat(a).isEqualTo(1.0f)
    }

    @Test
    fun `plurals match their singular`() {
        assertThat(TextSimilarity.similarity("scrambled eggs", "scrambled egg")).isEqualTo(1.0f)
    }

    @Test
    fun `a one-character typo still matches`() {
        assertThat(TextSimilarity.similarity("tomatoe soup", "tomato soup")).isEqualTo(1.0f)
    }

    @Test
    fun `different foods sharing a word score below the merge threshold`() {
        assertThat(TextSimilarity.similarity("chicken breast", "chicken soup")).isLessThan(0.75f)
    }

    @Test
    fun `short words are not fuzzy-matched`() {
        // "rice" and "ribs" are one edit apart but must never be treated as the same food.
        assertThat(TextSimilarity.similarity("rice", "ribs")).isEqualTo(0f)
    }

    @Test
    fun `a subset label does not score as a perfect match`() {
        val score = TextSimilarity.similarity("chicken", "chicken caesar salad with croutons")
        assertThat(score).isLessThan(1.0f)
        assertThat(score).isGreaterThan(0f)
    }

    @Test
    fun `unrelated foods score zero`() {
        assertThat(TextSimilarity.similarity("banana", "steak")).isEqualTo(0f)
    }

    @Test
    fun `blank input is never a match`() {
        assertThat(TextSimilarity.similarity("", "apple")).isEqualTo(0f)
    }

    @Test
    fun `coverage prefers the entry that actually covers the query over a shorter one`() {
        val query = "grilled chicken breast"
        val plainCut = "Chicken, broiler or fryers, breast, skinless, boneless, meat only, cooked, grilled"
        val processed = "Chicken breast, roll, oven-roasted"

        // Symmetric similarity rewards the short processed product purely for being short.
        assertThat(TextSimilarity.similarity(query, processed))
            .isGreaterThan(TextSimilarity.similarity(query, plainCut))

        // Coverage gets it the right way round.
        assertThat(TextSimilarity.coverage(query, plainCut))
            .isGreaterThan(TextSimilarity.coverage(query, processed))
    }

    @Test
    fun `coverage is one when the candidate contains every query word`() {
        assertThat(TextSimilarity.coverage("white rice", "white rice")).isEqualTo(1.0f)
    }

    @Test
    fun `coverage is zero for unrelated foods`() {
        assertThat(TextSimilarity.coverage("banana", "steak")).isEqualTo(0f)
    }
}
