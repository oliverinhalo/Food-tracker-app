package dev.foodtracker.domain.recognition

import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.DetectedItem

/** A JPEG-encoded capture, already compressed for upload. */
data class CapturedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
) {
    // Data classes with an array member need these; identity comparison would break caching.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedImage) return false
        return width == other.width && height == other.height && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * (31 * bytes.contentHashCode() + width) + height
}

sealed interface RecognitionOutcome {
    data class Success(val items: List<DetectedItem>) : RecognitionOutcome
    data class Unavailable(val reason: DegradeReason, val message: String? = null) : RecognitionOutcome
}

/** Fast, offline first pass. Never throws; an unavailable model returns [RecognitionOutcome.Unavailable]. */
interface LocalFoodRecognizer {
    val isAvailable: Boolean
    suspend fun recognize(image: CapturedImage): RecognitionOutcome
}

/** Slower, multi-item, portion-aware pass backed by a hosted model. */
interface CloudFoodRecognizer {
    suspend fun recognize(image: CapturedImage): RecognitionOutcome
}

/** Photos parked for a later cloud pass because the network or the key was missing. */
interface ReanalysisQueue {
    suspend fun enqueue(captureId: String, image: CapturedImage)
}
