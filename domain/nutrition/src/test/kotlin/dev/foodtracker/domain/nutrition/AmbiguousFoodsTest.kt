package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmbiguousFoodsTest {

    @Test
    fun `a pie is asked about, because looking cannot settle it`() {
        val group = AmbiguousFoods.groupFor("pie")

        assertThat(group).isNotNull()
        assertThat(group!!.question).isEqualTo("What kind of pie?")
        assertThat(group.variants).contains("steak pie")
        assertThat(group.variants).contains("apple pie")
    }

    @Test
    fun `a sausage offers a meat-free option, since that is the biggest difference`() {
        val variants = AmbiguousFoods.variantsFor("sausage").map { it.name }

        assertThat(variants).contains("pork sausage")
        assertThat(variants).contains("vegan sausage")
    }

    @Test
    fun `a label that already says what is inside is not asked about`() {
        // Asking "what kind of pie?" about a steak pie would be noise on every item.
        assertThat(AmbiguousFoods.groupFor("steak pie")).isNull()
        assertThat(AmbiguousFoods.groupFor("chicken curry")).isNull()
        assertThat(AmbiguousFoods.groupFor("vegan sausage")).isNull()
    }

    @Test
    fun `an unambiguous food is never asked about`() {
        assertThat(AmbiguousFoods.groupFor("banana")).isNull()
        assertThat(AmbiguousFoods.groupFor("grilled chicken breast")).isNull()
        assertThat(AmbiguousFoods.variantsFor("apple")).isEmpty()
    }

    @Test
    fun `every variant is a complete food name, not a filling`() {
        // "steak" alone cannot be looked up as a pie; the whole point is a searchable name.
        AmbiguousFoods.variantsFor("pie").forEach { variant ->
            assertThat(variant.name.split(' ').size).isAtLeast(2)
        }
    }

    @Test
    fun `every group offers a real choice`() {
        listOf("pie", "sausage", "burger", "curry", "sandwich", "wrap", "soup", "milk", "pizza")
            .forEach { label ->
                val group = AmbiguousFoods.groupFor(label)
                assertThat(group).isNotNull()
                assertThat(group!!.variants.size).isAtLeast(3)
                assertThat(group.question).endsWith("?")
            }
    }

    @Test
    fun `variants differ enough to matter`() {
        // The reason this feature exists: a vegan sausage and a pork sausage are not the same
        // food, and neither are a steak pie and an apple pie.
        val pie = AmbiguousFoods.variantsFor("pie").map { it.name }
        assertThat(pie).containsAtLeast("steak pie", "apple pie")
    }

    @Test
    fun `matching is case and phrasing insensitive`() {
        assertThat(AmbiguousFoods.groupFor("Slice of PIE")).isNotNull()
        assertThat(AmbiguousFoods.groupFor("a large sausage")).isNotNull()
    }
}
