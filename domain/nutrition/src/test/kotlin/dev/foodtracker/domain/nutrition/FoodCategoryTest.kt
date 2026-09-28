package dev.foodtracker.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FoodCategoryTest {

    @Test
    fun `common foods land in the right category`() {
        val cases = mapOf(
            "grilled chicken breast" to FoodCategory.MEAT_OR_FISH,
            "white rice" to FoodCategory.GRAIN_COOKED,
            "baby spinach" to FoodCategory.LEAFY_VEGETABLE,
            "cheddar cheese" to FoodCategory.CHEESE,
            "olive oil" to FoodCategory.OIL_OR_FAT,
            "orange juice" to FoodCategory.BEVERAGE,
            "greek yoghurt" to FoodCategory.YOGHURT_OR_SOFT_DAIRY,
            "scrambled eggs" to FoodCategory.EGG,
            "sourdough bread" to FoodCategory.BREAD_OR_BAKED,
            "lentil soup" to FoodCategory.SOUP_OR_STEW,
        )

        cases.forEach { (label, expected) ->
            assertThat(FoodCategory.of(label)).isEqualTo(expected)
        }
    }

    @Test
    fun `more specific categories win over ones that would swallow them`() {
        // "breakfast cereal" must not be read as a cooked grain, and bread is not a grain dish.
        assertThat(FoodCategory.of("breakfast cereal")).isEqualTo(FoodCategory.CEREAL_DRY)
        assertThat(FoodCategory.of("rice bread")).isEqualTo(FoodCategory.BREAD_OR_BAKED)
        // Cottage cheese is soft dairy, not a hard cheese portion.
        assertThat(FoodCategory.of("cottage cheese")).isEqualTo(FoodCategory.YOGHURT_OR_SOFT_DAIRY)
    }

    @Test
    fun `keywords match whole words only`() {
        // "pealed" contains "pea" and "beanbag" contains "bean"; neither is a legume.
        assertThat(FoodCategory.of("pealed surface")).isNull()
        assertThat(FoodCategory.of("beanbag")).isNull()
    }

    @Test
    fun `plurals are matched`() {
        assertThat(FoodCategory.of("baked beans")).isEqualTo(FoodCategory.LEGUME)
        assertThat(FoodCategory.of("mixed nuts")).isEqualTo(FoodCategory.NUT_OR_SEED)
    }

    @Test
    fun `an unknown food falls back to the generic profile rather than failing`() {
        assertThat(FoodCategory.of("zzzz unknown thing")).isNull()
        assertThat(FoodCategory.profileFor("zzzz unknown thing")).isEqualTo(PortionProfile.GENERIC)
    }

    @Test
    fun `every category profile is internally sensible`() {
        FoodCategory.entries.forEach { category ->
            val p = category.profile
            // A tablespoon is 1/16 of a cup by volume, so its mass must be far below a cup's.
            assertThat(p.gramsPerTablespoon).isLessThan(p.gramsPerCup)
            assertThat(p.gramsPerCup).isGreaterThan(0.0)
            assertThat(p.gramsPerPiece).isGreaterThan(0.0)
            assertThat(p.gramsPerServing).isGreaterThan(0.0)
        }
    }
}
