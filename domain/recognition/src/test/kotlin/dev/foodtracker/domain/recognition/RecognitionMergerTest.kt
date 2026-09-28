package dev.foodtracker.domain.recognition

import com.google.common.truth.Truth.assertThat
import dev.foodtracker.core.model.FoodAlternative
import dev.foodtracker.core.model.RecognitionSource
import org.junit.Test

class RecognitionMergerTest {

    private val merger = RecognitionMerger()

    @Test
    fun `empty incoming leaves existing untouched`() {
        val existing = listOf(item("1", "apple"))
        assertThat(merger.merge(existing, emptyList())).isEqualTo(existing)
    }

    @Test
    fun `empty existing takes incoming wholesale`() {
        val incoming = listOf(item("c1", "apple", source = RecognitionSource.CLOUD))
        assertThat(merger.merge(emptyList(), incoming)).isEqualTo(incoming)
    }

    @Test
    fun `matched item keeps the existing id so the list does not re-key`() {
        val existing = listOf(item("local-1", "chicken", source = RecognitionSource.ON_DEVICE))
        val incoming = listOf(item("cloud-9", "grilled chicken breast", source = RecognitionSource.CLOUD))

        val merged = merger.merge(existing, incoming)

        assertThat(merged).hasSize(1)
        assertThat(merged.single().id).isEqualTo("local-1")
    }

    @Test
    fun `cloud detail wins over the local guess`() {
        val existing = listOf(item("local-1", "chicken", grams = 100.0))
        val incoming = listOf(
            item(
                "cloud-9",
                "grilled chicken breast",
                grams = 172.0,
                source = RecognitionSource.CLOUD,
                cookingMethod = "grilled",
            ),
        )

        val merged = merger.merge(existing, incoming).single()

        assertThat(merged.name).isEqualTo("grilled chicken breast")
        assertThat(merged.portion.grams).isEqualTo(172.0)
        assertThat(merged.cookingMethod).isEqualTo("grilled")
        assertThat(merged.source).isEqualTo(RecognitionSource.CLOUD)
    }

    @Test
    fun `the overridden local label survives as an alternative`() {
        // The labels disagree entirely, so it is the overlapping box that identifies them as the
        // same item -- exactly the case where the user may want the original guess back.
        val existing = listOf(
            item("local-1", "chicken", confidence = 0.55f, box = box(0.2f, 0.2f, 0.7f, 0.7f)),
        )
        val incoming = listOf(
            item("cloud-9", "turkey breast", source = RecognitionSource.CLOUD, box = box(0.21f, 0.19f, 0.71f, 0.69f)),
        )

        val merged = merger.merge(existing, incoming).single()

        assertThat(merged.name).isEqualTo("turkey breast")
        assertThat(merged.alternatives.map { it.name }).contains("chicken")
    }

    @Test
    fun `unrelated labels with no boxes stay separate rather than overriding each other`() {
        val existing = listOf(item("local-1", "chicken", confidence = 0.9f))
        val incoming = listOf(item("cloud-9", "turkey breast", source = RecognitionSource.CLOUD))

        val merged = merger.merge(existing, incoming)

        assertThat(merged.map { it.name }).containsExactly("turkey breast", "chicken")
    }

    @Test
    fun `a user edit is never overwritten by a late cloud response`() {
        val existing = listOf(
            item("u1", "my protein shake", grams = 350.0, source = RecognitionSource.USER),
        )
        val incoming = listOf(
            item("cloud-9", "protein shake", grams = 240.0, source = RecognitionSource.CLOUD),
        )

        val merged = merger.merge(existing, incoming).single()

        assertThat(merged.name).isEqualTo("my protein shake")
        assertThat(merged.portion.grams).isEqualTo(350.0)
        assertThat(merged.source).isEqualTo(RecognitionSource.USER)
    }

    @Test
    fun `cloud items with no local counterpart are added`() {
        val existing = listOf(item("local-1", "rice"))
        val incoming = listOf(
            item("cloud-1", "rice", source = RecognitionSource.CLOUD),
            item("cloud-2", "soy sauce", source = RecognitionSource.CLOUD),
        )

        val merged = merger.merge(existing, incoming)

        assertThat(merged.map { it.name }).containsExactly("rice", "soy sauce")
    }

    @Test
    fun `a confident local item the cloud never mentioned is kept`() {
        val existing = listOf(item("local-1", "butter", confidence = 0.9f))
        val incoming = listOf(item("cloud-1", "toast", source = RecognitionSource.CLOUD))

        val merged = merger.merge(existing, incoming)

        assertThat(merged.map { it.name }).containsExactly("toast", "butter")
    }

    @Test
    fun `a low-confidence local item the cloud never mentioned is dropped as noise`() {
        val existing = listOf(item("local-1", "gravy", confidence = 0.2f))
        val incoming = listOf(item("cloud-1", "roast potatoes", source = RecognitionSource.CLOUD))

        val merged = merger.merge(existing, incoming)

        assertThat(merged.map { it.name }).containsExactly("roast potatoes")
    }

    @Test
    fun `boxes pointing at different regions are not merged even with a shared word`() {
        val existing = listOf(item("local-1", "green salad", box = box(0.0f, 0.0f, 0.3f, 0.3f), confidence = 0.9f))
        val incoming = listOf(
            item("cloud-1", "green beans", source = RecognitionSource.CLOUD, box = box(0.7f, 0.7f, 1.0f, 1.0f)),
        )

        val merged = merger.merge(existing, incoming)

        assertThat(merged).hasSize(2)
    }

    @Test
    fun `overlapping boxes merge even when the labels disagree`() {
        val existing = listOf(item("local-1", "pasta", box = box(0.1f, 0.1f, 0.6f, 0.6f)))
        val incoming = listOf(
            item("cloud-1", "spaghetti bolognese", source = RecognitionSource.CLOUD, box = box(0.12f, 0.08f, 0.62f, 0.58f)),
        )

        val merged = merger.merge(existing, incoming)

        assertThat(merged).hasSize(1)
        assertThat(merged.single().name).isEqualTo("spaghetti bolognese")
    }

    @Test
    fun `a local item hidden under an overlapping cloud box is dropped rather than duplicated`() {
        val existing = listOf(
            item("local-1", "pasta", box = box(0.1f, 0.1f, 0.6f, 0.6f), confidence = 0.95f),
            item("local-2", "noodles", box = box(0.11f, 0.11f, 0.61f, 0.61f), confidence = 0.95f),
        )
        val incoming = listOf(
            item("cloud-1", "spaghetti bolognese", source = RecognitionSource.CLOUD, box = box(0.1f, 0.1f, 0.6f, 0.6f)),
        )

        val merged = merger.merge(existing, incoming)

        assertThat(merged).hasSize(1)
        assertThat(merged.single().name).isEqualTo("spaghetti bolognese")
    }

    @Test
    fun `one cloud item cannot consume two local items`() {
        val existing = listOf(
            item("local-1", "apple", confidence = 0.9f),
            item("local-2", "apple", confidence = 0.9f),
        )
        val incoming = listOf(item("cloud-1", "apple", source = RecognitionSource.CLOUD))

        val merged = merger.merge(existing, incoming)

        // The second local apple is not matched, and has no box to be occluded by, so it survives.
        assertThat(merged).hasSize(2)
        assertThat(merged.count { it.id == "local-1" }).isEqualTo(1)
    }

    @Test
    fun `alternatives are de-duplicated and capped`() {
        val existing = listOf(
            item(
                "local-1",
                "cola",
                alternatives = (1..8).map { FoodAlternative("soda $it", 0.5f) },
            ),
        )
        val incoming = listOf(
            item(
                "cloud-1",
                "cola",
                source = RecognitionSource.CLOUD,
                alternatives = listOf(FoodAlternative("Cola", 0.4f), FoodAlternative("soda 1", 0.3f)),
            ),
        )

        val merged = merger.merge(existing, incoming).single()

        assertThat(merged.alternatives.size).isAtMost(6)
        // "Cola" normalises to the winner's own name and must not be offered as an alternative.
        assertThat(merged.alternatives.map { it.name.lowercase() }).doesNotContain("cola")
        assertThat(merged.alternatives.map { it.name }).containsNoDuplicates()
    }
}
