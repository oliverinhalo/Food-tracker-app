package dev.foodtracker.domain.recognition

import dev.foodtracker.core.model.DegradeReason
import dev.foodtracker.core.model.RecognitionEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs the hybrid pipeline for one capture and reports progress as it happens.
 *
 * Both passes start together rather than in sequence: the local pass is what makes the sheet feel
 * instant, but waiting for it before dialling out would add its latency to the cloud result too.
 * The local result is emitted as soon as it lands, then the cloud result is merged into it.
 */
class RecognitionOrchestrator(
    private val localRecognizer: LocalFoodRecognizer,
    private val cloudRecognizer: CloudFoodRecognizer,
    private val merger: RecognitionMerger = RecognitionMerger(),
    private val reanalysisQueue: ReanalysisQueue? = null,
) {

    fun recognize(
        captureId: String,
        image: CapturedImage,
        config: RecognitionConfig,
    ): Flow<RecognitionEvent> = flow {
        coroutineScope {
            val cloudPass = if (config.cloudEnabled) {
                async { cloudRecognizer.recognize(image) }
            } else {
                null
            }

            val localOutcome = if (localRecognizer.isAvailable) {
                localRecognizer.recognize(image)
            } else {
                RecognitionOutcome.Unavailable(DegradeReason.MODEL_UNAVAILABLE)
            }

            var current = (localOutcome as? RecognitionOutcome.Success)?.items.orEmpty()
            if (current.isNotEmpty()) {
                emit(RecognitionEvent.Provisional(current))
            }

            if (cloudPass == null) {
                val reason = config.cloudDisabledReason ?: DegradeReason.LOCAL_ONLY_MODE
                if (current.isEmpty()) {
                    emit(RecognitionEvent.Failed(reason, "No results available offline."))
                } else {
                    emit(RecognitionEvent.Degraded(reason, current))
                }
                if (reason.isRecoverable) reanalysisQueue?.enqueue(captureId, image)
                return@coroutineScope
            }

            when (val cloudOutcome = cloudPass.await()) {
                is RecognitionOutcome.Success -> {
                    current = merger.merge(existing = current, incoming = cloudOutcome.items)
                    emit(RecognitionEvent.Refined(current))
                }

                is RecognitionOutcome.Unavailable -> {
                    if (cloudOutcome.reason.isRecoverable) {
                        reanalysisQueue?.enqueue(captureId, image)
                    }
                    if (current.isEmpty()) {
                        emit(
                            RecognitionEvent.Failed(
                                cloudOutcome.reason,
                                cloudOutcome.message ?: "Could not identify this meal.",
                            ),
                        )
                    } else {
                        emit(RecognitionEvent.Degraded(cloudOutcome.reason, current))
                    }
                }
            }
        }
    }
}

data class RecognitionConfig(
    val cloudEnabled: Boolean,
    val cloudDisabledReason: DegradeReason? = null,
)

/** Whether re-running the cloud pass later could plausibly succeed. */
val DegradeReason.isRecoverable: Boolean
    get() = when (this) {
        DegradeReason.OFFLINE, DegradeReason.RATE_LIMITED, DegradeReason.CLOUD_ERROR, DegradeReason.MODEL_UNAVAILABLE -> true
        DegradeReason.NO_API_KEY, DegradeReason.LOCAL_ONLY_MODE -> false
    }
