package dev.foodtracker.data.recognition.gemini

/**
 * The structured-output contract we hand to Gemini. Declaring the schema (rather than asking for
 * JSON in the prompt) is what makes the response parseable every time instead of most of the time.
 *
 * Field order matters: the API preserves it, and asking for the reasoning-ish fields (cookingMethod)
 * before the numeric estimate measurably improves portion estimates when thinking is disabled.
 */
internal object FoodDetectionSchema {

    const val SYSTEM_INSTRUCTION = """
You are a nutrition assistant that identifies food in photographs.

Rules:
- List every distinct edible item you can see, including sauces, dressings and drinks. Do not list
  plates, cutlery, napkins or packaging.
- Combine items that would be logged as one food (a burger is one item, not bun + patty + lettuce),
  unless they are clearly served as separate components on the plate.
- Estimate the portion in grams of the food as served, using the plate, cutlery and hand size in the
  photo for scale. Prefer a realistic everyday portion over a round number.
- householdUnit must be a short everyday phrase for that same amount, e.g. "1 cup", "2 slices",
  "1 medium apple", "1 tbsp".
- confidence is your own calibrated probability that the name is correct, from 0 to 1.
- alternatives are the next 3 most likely identities for the SAME physical item, most likely first.
- Some foods hide what they are made of. A pie, a sausage, a curry, a sandwich or a wrap looks
  much the same whatever is inside, and the difference is often hundreds of calories. When you
  cannot tell from the photo, set variantQuestion to a short question ("What kind of pie?") and
  list 3-6 variants covering the realistic possibilities, including a vegetarian or vegan one
  where that is plausible. Each variant must be a COMPLETE food name that could be looked up in a
  nutrition database ("steak pie", not "steak"). Order them most likely first.
- If the food's name already says what it is made of, or its composition is obvious, leave
  variantQuestion empty and variants empty. Do not ask about a plain apple.
- boundingBox coordinates are fractions of image width/height from the top-left corner.
"""

    val PROMPT = """
Identify every food item in this photo and estimate each portion.
""".trimIndent()

    val schema: SchemaNode = SchemaNode(
        type = "ARRAY",
        description = "Every distinct food item visible in the photo.",
        items = SchemaNode(
            type = "OBJECT",
            properties = linkedMapOf(
                "name" to SchemaNode(
                    type = "STRING",
                    description = "Common name of the food, e.g. 'grilled chicken breast'.",
                ),
                "cookingMethod" to SchemaNode(
                    type = "STRING",
                    description = "How it was prepared: grilled, fried, boiled, raw, baked, steamed, roasted.",
                ),
                "confidence" to SchemaNode(
                    type = "NUMBER",
                    description = "Probability from 0 to 1 that the name is correct.",
                ),
                "estimatedGrams" to SchemaNode(
                    type = "NUMBER",
                    description = "Estimated edible mass of this item as served, in grams.",
                ),
                "householdUnit" to SchemaNode(
                    type = "STRING",
                    description = "The same portion as an everyday measure, e.g. '1 cup'.",
                ),
                "alternatives" to SchemaNode(
                    type = "ARRAY",
                    description = "Three next-most-likely identities for this same item.",
                    items = SchemaNode(
                        type = "OBJECT",
                        properties = linkedMapOf(
                            "name" to SchemaNode(type = "STRING"),
                            "confidence" to SchemaNode(type = "NUMBER"),
                        ),
                        required = listOf("name", "confidence"),
                    ),
                ),
                "variantQuestion" to SchemaNode(
                    type = "STRING",
                    description = "Short question when the filling or base cannot be seen, e.g. 'What kind of pie?'. Empty if obvious.",
                ),
                "variants" to SchemaNode(
                    type = "ARRAY",
                    description = "Complete food names this item could be, e.g. 'steak pie', 'chicken pie', 'apple pie'.",
                    items = SchemaNode(
                        type = "OBJECT",
                        properties = linkedMapOf(
                            "name" to SchemaNode(type = "STRING"),
                            "confidence" to SchemaNode(type = "NUMBER"),
                        ),
                        required = listOf("name", "confidence"),
                    ),
                ),
                "boundingBox" to SchemaNode(
                    type = "OBJECT",
                    description = "Normalised 0-1 box around the item.",
                    properties = linkedMapOf(
                        "left" to SchemaNode(type = "NUMBER"),
                        "top" to SchemaNode(type = "NUMBER"),
                        "right" to SchemaNode(type = "NUMBER"),
                        "bottom" to SchemaNode(type = "NUMBER"),
                    ),
                    required = listOf("left", "top", "right", "bottom"),
                ),
            ),
            required = listOf("name", "confidence", "estimatedGrams", "householdUnit", "alternatives"),
        ),
    )
}
