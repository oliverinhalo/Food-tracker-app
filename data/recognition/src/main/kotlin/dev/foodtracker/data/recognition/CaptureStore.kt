package dev.foodtracker.data.recognition

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foodtracker.core.common.di.IoDispatcher
import dev.foodtracker.domain.recognition.CapturedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds compressed captures between the camera screen and the results sheet.
 *
 * Navigation arguments carry only a capture id -- a compressed JPEG is still tens of kilobytes and
 * putting that in a saved-state bundle risks TransactionTooLargeException. An in-memory entry keeps
 * the common path allocation-free, and a file copy survives process death so re-analysis after the
 * app is killed still has the photo.
 */
@Singleton
class CaptureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val inMemory = ConcurrentHashMap<String, CapturedImage>()

    private val directory: File by lazy {
        File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
    }

    suspend fun save(image: CapturedImage): String = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        inMemory[id] = image
        runCatching { fileFor(id).writeBytes(image.bytes) }
        pruneOldCaptures()
        id
    }

    suspend fun load(id: String): CapturedImage? {
        inMemory[id]?.let { return it }
        return withContext(ioDispatcher) {
            val file = fileFor(id)
            if (!file.exists()) return@withContext null
            // Dimensions are only used for logging and bounding-box overlays; a reload after
            // process death can recover the bytes without them.
            runCatching { CapturedImage(file.readBytes(), width = 0, height = 0) }.getOrNull()
        }
    }

    suspend fun delete(id: String) = withContext(ioDispatcher) {
        inMemory.remove(id)
        runCatching { fileFor(id).delete() }
        Unit
    }

    private fun fileFor(id: String) = File(directory, "$id.jpg")

    /** Keeps the cache bounded; the queue for re-analysis owns anything it still needs. */
    private fun pruneOldCaptures() {
        runCatching {
            val files = directory.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_CACHED_CAPTURES).forEach { file ->
                file.delete()
                inMemory.remove(file.nameWithoutExtension)
            }
        }
    }

    private companion object {
        const val CAPTURE_DIR = "captures"
        const val MAX_CACHED_CAPTURES = 20
    }
}
