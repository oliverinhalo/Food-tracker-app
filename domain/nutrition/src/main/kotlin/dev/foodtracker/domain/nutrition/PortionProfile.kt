package dev.foodtracker.domain.nutrition

/**
 * How much a food weighs per household measure.
 *
 * Volume and count units are meaningless without this: a cup of spinach is ~30 g and a cup of rice
 * is ~185 g, so a single "grams per cup" constant would be wrong by a factor of six. Every food
 * resolves to one of these, falling back through its category to [GENERIC] when nothing better is
 * known.
 */
data class PortionProfile(
    val gramsPerCup: Double,
    val gramsPerTablespoon: Double,
    val gramsPerPiece: Double,
    val gramsPerServing: Double,
) {
    init {
        require(gramsPerCup > 0 && gramsPerTablespoon > 0 && gramsPerPiece > 0 && gramsPerServing > 0) {
            "portion profile values must all be positive"
        }
    }

    companion object {
        /**
         * Used when a food matches no category. Roughly a mixed cooked dish: wrong for extremes,
         * but never absurd, and the user's own corrections quickly override it.
         */
        val GENERIC = PortionProfile(
            gramsPerCup = 150.0,
            gramsPerTablespoon = 15.0,
            gramsPerPiece = 85.0,
            gramsPerServing = 100.0,
        )
    }
}

/**
 * Food categories coarse enough to be guessable from a label, but distinct enough that their
 * densities genuinely differ. Order matters in [FoodCategory.of]: the first keyword hit wins, so
 * more specific categories are listed before the ones that would otherwise swallow them.
 */
enum class FoodCategory(
    val profile: PortionProfile,
    internal val keywords: List<String>,
) {
    OIL_OR_FAT(
        PortionProfile(gramsPerCup = 218.0, gramsPerTablespoon = 13.6, gramsPerPiece = 5.0, gramsPerServing = 14.0),
        listOf("oil", "butter", "margarine", "ghee", "lard", "mayonnaise", "mayo"),
    ),
    SAUCE_OR_DRESSING(
        PortionProfile(gramsPerCup = 240.0, gramsPerTablespoon = 15.0, gramsPerPiece = 15.0, gramsPerServing = 30.0),
        listOf("sauce", "dressing", "ketchup", "mustard", "gravy", "salsa", "hummus", "dip", "syrup", "jam", "honey"),
    ),
    // Before every ingredient category: "lentil soup" and "chicken curry" are liquid dishes whose
    // density comes from the preparation, not from the ingredient the label happens to name first.
    SOUP_OR_STEW(
        PortionProfile(gramsPerCup = 245.0, gramsPerTablespoon = 15.0, gramsPerPiece = 245.0, gramsPerServing = 300.0),
        listOf("soup", "stew", "broth", "curry", "chowder", "casserole"),
    ),
    BEVERAGE(
        PortionProfile(gramsPerCup = 240.0, gramsPerTablespoon = 15.0, gramsPerPiece = 330.0, gramsPerServing = 250.0),
        listOf("juice", "soda", "cola", "coffee", "tea", "beer", "wine", "smoothie", "water", "milkshake", "drink"),
    ),
    DAIRY_LIQUID(
        PortionProfile(gramsPerCup = 244.0, gramsPerTablespoon = 15.0, gramsPerPiece = 244.0, gramsPerServing = 244.0),
        listOf("milk", "cream", "buttermilk", "kefir"),
    ),
    YOGHURT_OR_SOFT_DAIRY(
        PortionProfile(gramsPerCup = 245.0, gramsPerTablespoon = 15.0, gramsPerPiece = 170.0, gramsPerServing = 170.0),
        listOf("yoghurt", "yogurt", "quark", "skyr", "cottage cheese", "creme fraiche"),
    ),
    CHEESE(
        PortionProfile(gramsPerCup = 113.0, gramsPerTablespoon = 7.0, gramsPerPiece = 28.0, gramsPerServing = 30.0),
        listOf("cheese", "cheddar", "mozzarella", "parmesan", "feta", "brie", "gouda"),
    ),
    // Before GRAIN_COOKED: "breakfast cereal" and "bread" must not be read as cooked grains.
    BREAD_OR_BAKED(
        PortionProfile(gramsPerCup = 50.0, gramsPerTablespoon = 4.0, gramsPerPiece = 30.0, gramsPerServing = 60.0),
        listOf("bread", "toast", "bagel", "roll", "bun", "croissant", "muffin", "pancake", "waffle", "tortilla", "pita", "naan"),
    ),
    CEREAL_DRY(
        PortionProfile(gramsPerCup = 40.0, gramsPerTablespoon = 3.0, gramsPerPiece = 40.0, gramsPerServing = 40.0),
        listOf("cereal", "granola", "muesli", "cornflakes", "oats", "porridge", "oatmeal"),
    ),
    GRAIN_COOKED(
        PortionProfile(gramsPerCup = 185.0, gramsPerTablespoon = 12.0, gramsPerPiece = 150.0, gramsPerServing = 150.0),
        listOf("rice", "pasta", "spaghetti", "noodle", "couscous", "quinoa", "barley", "bulgur", "risotto", "macaroni"),
    ),
    POTATO_OR_ROOT(
        PortionProfile(gramsPerCup = 150.0, gramsPerTablespoon = 10.0, gramsPerPiece = 170.0, gramsPerServing = 150.0),
        listOf("potato", "fries", "chips", "sweet potato", "yam", "cassava", "carrot", "beetroot", "parsnip"),
    ),
    LEGUME(
        PortionProfile(gramsPerCup = 180.0, gramsPerTablespoon = 12.0, gramsPerPiece = 180.0, gramsPerServing = 150.0),
        listOf("bean", "lentil", "chickpea", "pea", "tofu", "tempeh", "edamame"),
    ),
    MEAT_OR_FISH(
        PortionProfile(gramsPerCup = 140.0, gramsPerTablespoon = 10.0, gramsPerPiece = 120.0, gramsPerServing = 120.0),
        listOf(
            "chicken", "beef", "pork", "lamb", "turkey", "duck", "steak", "mince", "bacon", "sausage",
            "ham", "fish", "salmon", "tuna", "cod", "prawn", "shrimp", "meat", "burger", "patty",
        ),
    ),
    EGG(
        PortionProfile(gramsPerCup = 243.0, gramsPerTablespoon = 15.0, gramsPerPiece = 50.0, gramsPerServing = 100.0),
        listOf("egg", "omelette", "omelet", "frittata"),
    ),
    NUT_OR_SEED(
        PortionProfile(gramsPerCup = 140.0, gramsPerTablespoon = 9.0, gramsPerPiece = 1.5, gramsPerServing = 30.0),
        listOf("nut", "almond", "cashew", "walnut", "peanut", "pistachio", "seed", "sesame"),
    ),
    FRUIT(
        PortionProfile(gramsPerCup = 150.0, gramsPerTablespoon = 10.0, gramsPerPiece = 120.0, gramsPerServing = 120.0),
        listOf("apple", "banana", "orange", "berry", "berries", "grape", "melon", "peach", "pear", "mango", "pineapple", "fruit", "avocado"),
    ),
    LEAFY_VEGETABLE(
        PortionProfile(gramsPerCup = 30.0, gramsPerTablespoon = 2.0, gramsPerPiece = 30.0, gramsPerServing = 80.0),
        listOf("lettuce", "spinach", "kale", "rocket", "arugula", "salad", "cabbage", "greens"),
    ),
    VEGETABLE(
        PortionProfile(gramsPerCup = 90.0, gramsPerTablespoon = 6.0, gramsPerPiece = 80.0, gramsPerServing = 80.0),
        listOf("broccoli", "cauliflower", "courgette", "zucchini", "pepper", "tomato", "onion", "mushroom", "cucumber", "aubergine", "eggplant", "vegetable", "sweetcorn", "corn"),
    ),
    SNACK_OR_SWEET(
        PortionProfile(gramsPerCup = 120.0, gramsPerTablespoon = 8.0, gramsPerPiece = 25.0, gramsPerServing = 30.0),
        listOf("chocolate", "biscuit", "cookie", "cake", "crisps", "candy", "sweet", "ice cream", "doughnut", "donut", "brownie"),
    ),
    ;

    companion object {
        /**
         * Best-effort category for a food label. Matches on whole words so "pealed" does not read
         * as "pea" and "beanbag" does not read as "bean".
         */
        fun of(label: String): FoodCategory? {
            val words = label.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return null
            val normalized = words.joinToString(" ")

            return entries.firstOrNull { category ->
                category.keywords.any { keyword ->
                    if (keyword.contains(' ')) {
                        normalized.contains(keyword)
                    } else {
                        words.any { word -> word == keyword || word == "${keyword}s" || word == "${keyword}es" }
                    }
                }
            }
        }

        fun profileFor(label: String): PortionProfile = of(label)?.profile ?: PortionProfile.GENERIC
    }
}
