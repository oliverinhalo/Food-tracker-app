package dev.foodtracker.domain.recognition

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
}
