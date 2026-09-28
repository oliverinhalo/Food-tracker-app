package dev.foodtracker.data.recognition.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foodtracker.core.common.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

data class ClassifiedFood(val label: String, val confidence: Float)

/**
 * Optional on-device food classifier.
 *
 * The model is not committed (size and licensing), so this is built to be absent: [isAvailable]
 * reports false, nothing is loaded, and the pipeline goes straight to the cloud pass. Drop a
 * Food-101 style model at `assets/food_classifier.tflite` with matching `food_labels.txt` and it
 * starts working with no other change.
 */
@Singleton
class TfLiteFoodClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val labels: List<String> by lazy { loadLabels() }

    private val interpreter: Interpreter? by lazy {
        if (!hasAsset(MODEL_ASSET)) return@lazy null
        runCatching {
            Interpreter(loadModel(), Interpreter.Options().apply { numThreads = 2 })
        }.getOrNull()
    }

    val isAvailable: Boolean get() = hasAsset(MODEL_ASSET) && labels.isNotEmpty()

    suspend fun classify(jpegBytes: ByteArray, topK: Int = 3): List<ClassifiedFood> =
        withContext(dispatcher) {
            val model = interpreter ?: return@withContext emptyList()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return@withContext emptyList()

            val inputShape = model.getInputTensor(0).shape()
            val size = inputShape.getOrElse(1) { DEFAULT_INPUT_SIZE }
            val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)

            val output = Array(1) { FloatArray(labels.size) }
            runCatching { model.run(scaled.toInputBuffer(size), output) }
                .onFailure { return@withContext emptyList() }

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            output[0]
                .mapIndexed { index, score -> ClassifiedFood(labels.getOrElse(index) { "" }, score) }
                .filter { it.label.isNotBlank() && it.confidence >= MIN_CONFIDENCE }
                .sortedByDescending { it.confidence }
                .take(topK)
        }

    /** Float input, normalised to 0..1. Quantised models would need a UINT8 buffer instead. */
    private fun Bitmap.toInputBuffer(size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3).apply { order(ByteOrder.nativeOrder()) }
        val pixels = IntArray(size * size)
        getPixels(pixels, 0, size, 0, 0, size, size)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModel(): ByteBuffer {
        val descriptor = context.assets.openFd(MODEL_ASSET)
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }
    }

    private fun loadLabels(): List<String> = runCatching {
        context.assets.open(LABELS_ASSET).bufferedReader().useLines { lines ->
            lines.map { it.trim().replace('_', ' ') }.filter { it.isNotBlank() }.toList()
        }
    }.getOrDefault(emptyList())

    private fun hasAsset(name: String): Boolean =
        runCatching { context.assets.open(name).close(); true }.getOrDefault(false)

    private companion object {
        const val MODEL_ASSET = "food_classifier.tflite"
        const val LABELS_ASSET = "food_labels.txt"
        const val DEFAULT_INPUT_SIZE = 224
        const val MIN_CONFIDENCE = 0.12f
    }
}
