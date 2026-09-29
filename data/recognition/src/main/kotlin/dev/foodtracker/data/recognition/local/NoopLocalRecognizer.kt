package dev.foodtracker.data.recognition.local

import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.domain.recognition.CapturedImage
import dev.foodtracker.domain.recognition.LocalFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionOutcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No on-device pass in the shipped build, and that is a deliberate size decision rather than
 * unfinished work.
 *
 * The local vision stack was built and measured: ML Kit object detection plus the TensorFlow Lite
 * runtime added roughly 25 MB of native libraries to a 3 MB app. Object detection on its own finds
 * *where* food is but cannot name it, so without a classifier model -- which cannot be committed
 * for size and licensing reasons -- all that weight buys is unnamed placeholder rows that the
 * cloud pass overwrites a second later. That is a bad trade for anyone downloading the APK.
 *
 * Reporting unavailable here is a supported state, not a failure: the orchestrator skips straight
 * to the cloud pass. See the README for how to turn the local pass back on once a model is chosen.
 */
@Singleton
class NoopLocalRecognizer @Inject constructor() : LocalFoodRecognizer {
    override val isAvailable: Boolean = false
    override suspend fun recognize(image: CapturedImage): RecognitionOutcome =
        RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)
}
