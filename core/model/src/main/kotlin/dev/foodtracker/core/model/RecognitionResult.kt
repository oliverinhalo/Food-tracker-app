package dev.foodtracker.core.model

/** Why the pipeline could not use the cloud recogniser. Drives the banner in the results sheet. */
enum class DegradeReason {
    NO_API_KEY,
    OFFLINE,
    RATE_LIMITED,
    MODEL_UNAVAILABLE,
    LOCAL_ONLY_MODE,
    CLOUD_ERROR,
}

/**
 * Progressive output of the recognition pipeline. A capture emits [Provisional] within a few
 * hundred milliseconds, then usually [Refined]; [Degraded] means the local results are final for
 * now and the photo has been queued for re-analysis.
 */
sealed interface RecognitionEvent {
    data class Provisional(val items: List<DetectedItem>) : RecognitionEvent

    data class Refined(val items: List<DetectedItem>) : RecognitionEvent

    data class Degraded(val reason: DegradeReason, val items: List<DetectedItem>) : RecognitionEvent

    data class Failed(val reason: DegradeReason, val message: String) : RecognitionEvent
}
