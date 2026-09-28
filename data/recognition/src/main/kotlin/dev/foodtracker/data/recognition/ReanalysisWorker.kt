package dev.foodtracker.data.recognition

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.foodtracker.core.datastore.SettingsRepository
import dev.foodtracker.domain.recognition.CloudFoodRecognizer
import dev.foodtracker.domain.recognition.RecognitionOutcome
import kotlinx.coroutines.flow.first

/**
 * Retries queued captures once the network is back.
 *
 * Results are written into the food cache rather than surfaced as a notification: the value is
 * that re-opening the photo, or logging the same food again, now resolves instantly and correctly.
 * Interrupting someone hours later to tell them about a sandwich would not be welcome.
 */
@HiltWorker
class ReanalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val queue: OfflineReanalysisQueue,
    private val cloudRecognizer: CloudFoodRecognizer,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        // Nothing to retry with, and retrying would only burn battery.
        if (settings.localOnlyMode || !settings.hasApiKey) return Result.success()

        val pending = queue.pending()
        if (pending.isEmpty()) return Result.success()

        var retryLater = false

        for (entry in pending) {
            if (entry.attempts >= OfflineReanalysisQueue.MAX_ATTEMPTS) {
                queue.remove(entry.captureId)
                continue
            }

            val image = queue.load(entry) ?: continue
            queue.markAttempted(entry.captureId)

            when (cloudRecognizer.recognize(image)) {
                is RecognitionOutcome.Success -> queue.remove(entry.captureId)
                is RecognitionOutcome.Unavailable -> retryLater = true
            }
        }

        return if (retryLater) Result.retry() else Result.success()
    }
}
