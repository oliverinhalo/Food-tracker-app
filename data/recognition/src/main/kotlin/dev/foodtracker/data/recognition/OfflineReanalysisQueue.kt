package dev.foodtracker.data.recognition

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foodtracker.core.common.TimeProvider
import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.core.database.dao.FoodDao
import dev.foodtracker.core.database.entity.PendingAnalysisEntity
import dev.foodtracker.domain.recognition.CapturedImage
import dev.foodtracker.domain.recognition.ReanalysisQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parks captures the cloud pass could not handle and retries them when conditions allow.
 *
 * A photo taken on a train is the case this exists for: the local pass is all you get at the time,
 * but the meal is still worth identifying properly once there is signal. WorkManager owns the
 * waiting so the retry survives the app being closed.
 */
@Singleton
class OfflineReanalysisQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodDao,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReanalysisQueue {

    val pendingCount: Flow<Int> = foodDao.pendingAnalysisCount()

    override suspend fun enqueue(captureId: String, image: CapturedImage) {
        withContext(ioDispatcher) {
            val directory = File(context.filesDir, QUEUE_DIR).apply { mkdirs() }
            val file = File(directory, "$captureId.jpg")

            // The cache directory the capture store uses can be reclaimed by the system at any
            // time, so a queued photo is copied somewhere that survives.
            runCatching { file.writeBytes(image.bytes) }.onFailure { return@withContext }

            foodDao.enqueueAnalysis(
                PendingAnalysisEntity(
                    captureId = captureId,
                    imagePath = file.absolutePath,
                    queuedAtMillis = timeProvider.epochMillis(),
                ),
            )

            scheduleWork()
        }
    }

    suspend fun pending(limit: Int = 10): List<PendingAnalysisEntity> =
        withContext(ioDispatcher) { foodDao.pendingAnalyses(limit) }

    suspend fun load(pending: PendingAnalysisEntity): CapturedImage? = withContext(ioDispatcher) {
        val file = File(pending.imagePath)
        if (!file.exists()) {
            // The file is gone, so the entry can never succeed; drop it rather than retry forever.
            foodDao.dequeueAnalysis(pending.captureId)
            return@withContext null
        }
        runCatching { CapturedImage(file.readBytes(), width = 0, height = 0) }.getOrNull()
    }

    suspend fun markAttempted(captureId: String) = withContext(ioDispatcher) {
        foodDao.markAttempted(captureId, timeProvider.epochMillis())
    }

    suspend fun remove(captureId: String) = withContext(ioDispatcher) {
        foodDao.dequeueAnalysis(captureId)
        runCatching { File(File(context.filesDir, QUEUE_DIR), "$captureId.jpg").delete() }
        Unit
    }

    private fun scheduleWork() {
        val request = OneTimeWorkRequestBuilder<ReanalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // KEEP rather than REPLACE: several photos queued in a row should share one run, not
        // restart the backoff each time.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "reanalysis"
        private const val QUEUE_DIR = "pending-captures"

        /** Give up after this many failures; something about the photo or key is wrong. */
        const val MAX_ATTEMPTS = 5
    }
}
