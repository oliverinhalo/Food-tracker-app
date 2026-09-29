package dev.foodtracker.core.model

/** Where a detection came from. Ordered weakest to strongest; used to resolve merge conflicts. */
enum class RecognitionSource(val trustRank: Int) {
    ON_DEVICE(0),
    GEMINI_NANO(1),
    CLOUD(2),
    USER(3),
}

/** Normalised [0,1] box relative to the captured image, origin top-left. */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height

    /** Intersection-over-union, the standard box-overlap measure used when merging detections. */
    fun iou(other: NormalizedBox): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interArea = (interRight - interLeft).coerceAtLeast(0f) * (interBottom - interTop).coerceAtLeast(0f)
        val union = area + other.area - interArea
        return if (union <= 0f) 0f else interArea / union
    }
}

/** One of the recogniser's runner-up guesses, offered in the "Change item" dropdown. */
data class FoodAlternative(
    val name: String,
    val confidence: Float,
)

/**
 * What a food might be made of, when its appearance does not say.
 *
 * This is a different question from [FoodAlternative], and conflating them loses information. An
 * alternative says "this might not be a sausage at all"; a variant says "it is definitely a
 * sausage, but pork and vegan sausages differ by more than double in calories". A pie is the
 * clearest case: steak, chicken and apple pie look nearly identical from the outside and are
 * nothing alike nutritionally, and no amount of looking harder at the photo can settle it.
 *
 * [name] is a complete food name, not a filling, so it can be looked up directly.
 */
data class FoodVariant(
    val name: String,
    val confidence: Float,
)

/**
 * A single food the pipeline believes is in the photo. The same type carries provisional on-device
 * results and refined cloud results; [source] says which, and [nutrients] stays null until the
 * nutrition layer resolves it.
 */
data class DetectedItem(
    val id: String,
    val name: String,
    val confidence: Float,
    val portion: Portion,
    val source: RecognitionSource,
    val cookingMethod: String? = null,
    val alternatives: List<FoodAlternative> = emptyList(),
    val variants: List<FoodVariant> = emptyList(),
    /** Prompt for the variant picker, e.g. "What kind of pie?". Null when the food is unambiguous. */
    val variantQuestion: String? = null,
    val boundingBox: NormalizedBox? = null,
    val nutrientsPer100g: Nutrients? = null,
    val brand: String? = null,
    val foodId: String? = null,
) {
    /** Absolute nutrients for this item's current portion, or null while lookup is pending. */
    val nutrients: Nutrients?
        get() = nutrientsPer100g?.let { Nutrients.forGrams(it, portion.grams) }
}
