package dev.foodtracker.data.recognition.local

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dev.foodtracker.core.common.di.DefaultDispatcher
import dev.foodtracker.core.model.NormalizedBox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Finds the distinct things on the plate, so items can be positioned and counted before the cloud
 * pass returns. ML Kit's default model classifies only into very coarse buckets, so its labels are
 * discarded -- the boxes are the useful part, and they are what lets the merger align a cloud
 * detection with an on-device one.
 */
@Singleton
class FoodObjectDetector @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val detector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build(),
        )
    }

    /** Bundled with the app rather than downloaded, so it is always there. */
    val isAvailable: Boolean = true

    suspend fun detect(jpegBytes: ByteArray): List<NormalizedBox> = withContext(dispatcher) {
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return@withContext emptyList()

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (width <= 0f || height <= 0f) return@withContext emptyList()

        val input = InputImage.fromBitmap(bitmap, 0)

        val boxes = suspendCancellableCoroutine { continuation ->
            detector.process(input)
                .addOnSuccessListener { objects ->
                    continuation.resume(
                        objects.mapNotNull { detected ->
                            val rect = detected.boundingBox
                            val box = NormalizedBox(
                                left = (rect.left / width).coerceIn(0f, 1f),
                                top = (rect.top / height).coerceIn(0f, 1f),
                                right = (rect.right / width).coerceIn(0f, 1f),
                                bottom = (rect.bottom / height).coerceIn(0f, 1f),
                            )
                            // A box covering nearly the whole frame is the plate or the table, not
                            // an item on it.
                            box.takeIf { it.area in MIN_AREA..MAX_AREA }
                        },
                    )
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

        bitmap.recycle()
        boxes.sortedByDescending { it.area }
    }

    private companion object {
        const val MIN_AREA = 0.01f
        const val MAX_AREA = 0.85f
    }
}
