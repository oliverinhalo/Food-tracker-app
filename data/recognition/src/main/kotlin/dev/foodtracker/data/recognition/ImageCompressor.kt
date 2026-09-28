package dev.foodtracker.data.recognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import dev.foodtracker.core.common.di.DefaultDispatcher
import dev.foodtracker.domain.recognition.CapturedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a camera capture to something worth uploading.
 *
 * A modern sensor gives ~4000px / 4MB per frame; on a slow connection that alone is several
 * seconds before the model even starts. At [MAX_DIMENSION] the model's accuracy is unchanged for
 * this task while the payload drops to tens of kilobytes.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun compress(jpegBytes: ByteArray): CapturedImage = withContext(dispatcher) {
        // Decode bounds first so we never allocate the full-size bitmap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
            ?: return@withContext CapturedImage(jpegBytes, bounds.outWidth, bounds.outHeight)

        val oriented = decoded.applyExifRotation(jpegBytes)
        val scaled = oriented.scaledToFit(MAX_DIMENSION)

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

        if (scaled !== decoded) scaled.recycle()
        if (oriented !== decoded && oriented !== scaled) oriented.recycle()
        decoded.recycle()

        CapturedImage(
            bytes = output.toByteArray(),
            width = scaled.width,
            height = scaled.height,
        )
    }

    /** Power-of-two subsampling done by the decoder itself, which is far cheaper than scaling after. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        val longest = max(width, height)
        while (longest / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaledToFit(maxDimension: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxDimension) return this
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * CameraX writes orientation into EXIF rather than rotating pixels. Re-encoding drops the EXIF
     * tag, so a sideways photo would reach the model sideways and wreck both recognition and the
     * bounding boxes.
     */
    private fun Bitmap.applyExifRotation(source: ByteArray): Bitmap {
        val degrees = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(source))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    companion object {
        const val MAX_DIMENSION = 1024
        const val JPEG_QUALITY = 80
    }
}
