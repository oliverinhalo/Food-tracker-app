package dev.foodtracker.data.recognition.local

import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.domain.recognition.CapturedImage
import dev.foodtracker.domain.recognition.LocalFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionOutcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder on-device pass. Phase 3 replaces this with the TFLite classifier plus ML Kit object
 * detection; until then it reports itself unavailable, which the orchestrator already handles as
 * "no provisional results, go straight to the cloud pass".
 */
@Singleton
class NoopLocalRecognizer @Inject constructor() : LocalFoodRecognizer {
    override val isAvailable: Boolean = false
    override suspend fun recognize(image: CapturedImage): RecognitionOutcome =
        RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)
}
