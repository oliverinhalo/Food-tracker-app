package dev.foodtracker.domain.nutrition

import dev.foodtracker.core.model.FoodVariant

/**
 * Foods whose appearance does not reveal what they are made of.
 *
 * Some foods simply cannot be identified from a photo, however good the recogniser: a pie is a
 * pastry case, and steak, chicken and apple pie are indistinguishable from the outside while
 * differing by hundreds of calories. Guessing silently and being wrong is worse here than
 * anywhere else in the app, because the user has no reason to doubt the answer.
 *
 * The cloud recogniser is asked for these directly, and usually answers well. This table is the
 * fallback for when it cannot be reached, for foods added by hand, and for older results that
 * predate the question being asked.
 */
object AmbiguousFoods {

    data class VariantGroup(
        val question: String,
        val variants: List<String>,
        internal val keywords: List<String>,
    )

    private val GROUPS = listOf(
        VariantGroup(
            question = "What kind of pie?",
            variants = listOf("steak pie", "chicken pie", "apple pie", "cheese and onion pie", "pork pie", "fish pie"),
            keywords = listOf("pie", "pasty", "turnover"),
        ),
        VariantGroup(
            question = "What kind of sausage?",
            variants = listOf("pork sausage", "beef sausage", "chicken sausage", "vegan sausage", "chorizo"),
            keywords = listOf("sausage", "banger", "frankfurter", "hotdog"),
        ),
        VariantGroup(
            question = "What kind of burger?",
            variants = listOf("beef burger", "chicken burger", "veggie burger", "vegan burger", "fish burger"),
            keywords = listOf("burger", "patty"),
        ),
        VariantGroup(
            question = "What kind of curry?",
            variants = listOf("chicken curry", "beef curry", "lamb curry", "vegetable curry", "chickpea curry", "fish curry"),
            keywords = listOf("curry", "masala", "korma", "balti"),
        ),
        VariantGroup(
            question = "What's in the sandwich?",
            variants = listOf("chicken sandwich", "cheese sandwich", "ham sandwich", "tuna sandwich", "egg sandwich", "bacon sandwich"),
            keywords = listOf("sandwich", "sarnie", "baguette", "sub", "panini", "toastie"),
        ),
        VariantGroup(
            question = "What's in the wrap?",
            variants = listOf("chicken wrap", "falafel wrap", "beef wrap", "halloumi wrap", "tuna wrap"),
            keywords = listOf("wrap", "burrito", "kebab"),
        ),
        VariantGroup(
            question = "What kind of soup?",
            variants = listOf("tomato soup", "chicken soup", "vegetable soup", "lentil soup", "mushroom soup"),
            keywords = listOf("soup", "broth"),
        ),
        VariantGroup(
            question = "What kind of milk?",
            variants = listOf("whole milk", "semi-skimmed milk", "skimmed milk", "oat milk", "almond milk", "soya milk"),
            keywords = listOf("milk"),
        ),
        VariantGroup(
            question = "What kind of yoghurt?",
            variants = listOf("greek yoghurt", "natural yoghurt", "low fat yoghurt", "fruit yoghurt", "soya yoghurt"),
            keywords = listOf("yoghurt", "yogurt"),
        ),
        VariantGroup(
            question = "What kind of pasta dish?",
            variants = listOf("spaghetti bolognese", "macaroni cheese", "pasta with tomato sauce", "carbonara", "vegetable pasta"),
            keywords = listOf("pasta", "spaghetti", "penne", "fusilli", "tagliatelle"),
        ),
        VariantGroup(
            question = "What kind of stir fry?",
            variants = listOf("chicken stir fry", "beef stir fry", "prawn stir fry", "tofu stir fry", "vegetable stir fry"),
            keywords = listOf("stir fry", "stirfry", "noodles", "chow mein"),
        ),
        VariantGroup(
            question = "What kind of pizza?",
            variants = listOf("margherita pizza", "pepperoni pizza", "vegetable pizza", "meat feast pizza", "cheese pizza"),
            keywords = listOf("pizza"),
        ),
        VariantGroup(
            question = "What kind of smoothie?",
            variants = listOf("fruit smoothie", "green smoothie", "protein shake", "milkshake"),
            keywords = listOf("smoothie", "shake"),
        ),
        VariantGroup(
            question = "What kind of cheese?",
            variants = listOf("cheddar", "mozzarella", "brie", "feta", "halloumi", "cream cheese"),
            keywords = listOf("cheese"),
        ),
        VariantGroup(
            question = "What kind of rice dish?",
            variants = listOf("white rice", "brown rice", "fried rice", "pilau rice", "risotto"),
            keywords = listOf("rice"),
        ),
    )

    /**
     * The variant group for a food label, if its composition is genuinely unclear from looking.
     *
     * A label that already names its filling is not ambiguous: "steak pie" needs no question, and
     * asking one anyway would be noise on every single item.
     */
    fun groupFor(label: String): VariantGroup? {
        val lowered = label.lowercase()
        val group = GROUPS.firstOrNull { g -> g.keywords.any { lowered.contains(it) } } ?: return null

        // Already specific: the label contains the distinguishing word of one of the answers.
        // "Distinguishing" excludes the group's own keywords, or "oat milk" reduces to just "milk"
        // and every plain "milk" looks like it has already been answered.
        val alreadyAnswered = group.variants.any { variant ->
            val distinguishing = variant.lowercase()
                .split(' ')
                .filter { it.length >= 3 && group.keywords.none { keyword -> keyword == it } }
            distinguishing.isNotEmpty() && distinguishing.all { lowered.contains(it) }
        }
        return group.takeUnless { alreadyAnswered }
    }

    fun variantsFor(label: String): List<FoodVariant> =
        groupFor(label)?.variants?.map { FoodVariant(it, confidence = 0f) }.orEmpty()

    fun questionFor(label: String): String? = groupFor(label)?.question
}
