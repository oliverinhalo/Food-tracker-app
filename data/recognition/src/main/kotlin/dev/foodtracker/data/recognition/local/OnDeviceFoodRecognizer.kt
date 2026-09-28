package dev.foodtracker.data.recognition.local

import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem
import dev.foodtracker.core.model.FoodAlternative
import dev.foodtracker.core.model.NormalizedBox
import dev.foodtracker.core.model.Portion
import dev.foodtracker.core.model.RecognitionSource
import dev.foodtracker.domain.nutrition.FoodCategory
import dev.foodtracker.domain.recognition.CapturedImage
import dev.foodtracker.domain.recognition.LocalFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionOutcome
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The instant, offline first pass.
 *
 * Two sources, because neither is sufficient alone: ML Kit object detection finds *where* the food
 * is on the plate but only labels it coarsely, while the TFLite classifier names the food but sees
 * the whole frame at once. Boxes from the detector are paired with the classifier's labels so the
 * merger has something to align the cloud result against.
 *
 * Portions here are deliberately category defaults, not estimates: a classifier cannot judge how
 * much is on the plate, and inventing a number would be worse than showing a sensible default the
 * cloud pass will replace within a second or two.
 */
@Singleton
class OnDeviceFoodRecognizer @Inject constructor(
    private val classifier: TfLiteFoodClassifier,
    private val objectDetector: FoodObjectDetector,
) : LocalFoodRecognizer {

    override val isAvailable: Boolean
        get() = classifier.isAvailable || objectDetector.isAvailable

    override suspend fun recognize(image: CapturedImage): RecognitionOutcome {
        if (!isAvailable) return RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)

        val classifications = classifier.classify(image.bytes)
        val boxes = objectDetector.detect(image.bytes)

        if (classifications.isEmpty() && boxes.isEmpty()) {
            return RecognitionOutcome.Success(emptyList())
        }

        // With a classifier, its best guess names the meal and detected boxes give it position.
        if (classifications.isNotEmpty()) {
            val best = classifications.first()
            return RecognitionOutcome.Success(
                listOf(
                    detectedItem(
                        name = best.label,
                        confidence = best.confidence,
                        box = boxes.firstOrNull(),
                        alternatives = classifications.drop(1)
                            .map { FoodAlternative(it.label, it.confidence) },
                    ),
                ),
            )
        }

        // Detection only: we know something edible is there but not what. Naming it "Food" would
        // be useless to log, so these act purely as placeholders the cloud pass fills in.
        return RecognitionOutcome.Success(
            boxes.take(MAX_PLACEHOLDERS).map { box ->
                detectedItem(name = "Food item", confidence = 0.2f, box = box, alternatives = emptyList())
            },
        )
    }

    private fun detectedItem(
        name: String,
        confidence: Float,
        box: NormalizedBox?,
        alternatives: List<FoodAlternative>,
    ): DetectedItem {
        val profile = FoodCategory.profileFor(name)
        return DetectedItem(
            id = UUID.randomUUID().toString(),
            name = name,
            confidence = confidence,
            portion = Portion.ofGrams(profile.gramsPerServing),
            source = RecognitionSource.ON_DEVICE,
            alternatives = alternatives,
            boundingBox = box,
        )
    }

    private companion object {
        const val MAX_PLACEHOLDERS = 4
    }
}
