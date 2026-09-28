package dev.foodtracker.domain.recognition

import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.FoodAlternative
import dev.foodtracker.core.model.NormalizedBox
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource

internal fun item(
    id: String,
    name: String,
    confidence: Float = 0.8f,
    grams: Double = 100.0,
    source: RecognitionSource = RecognitionSource.ON_DEVICE,
    box: NormalizedBox? = null,
    alternatives: List<FoodAlternative> = emptyList(),
    cookingMethod: String? = null,
) = DetectedItem(
    id = id,
    name = name,
    confidence = confidence,
    portion = Portion.ofGrams(grams),
    source = source,
    boundingBox = box,
    alternatives = alternatives,
    cookingMethod = cookingMethod,
)

internal fun box(left: Float, top: Float, right: Float, bottom: Float) =
    NormalizedBox(left, top, right, bottom)
