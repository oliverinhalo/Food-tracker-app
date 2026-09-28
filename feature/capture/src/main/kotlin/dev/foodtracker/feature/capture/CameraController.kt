package dev.foodtracker.feature.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * Thin CameraX wrapper. Kept out of the composable so binding happens once per lifecycle rather
 * than on every recomposition, which is the usual cause of a camera that takes seconds to appear.
 */
internal class CameraController {

    private var imageCapture: ImageCapture? = null

    suspend fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) {
        val provider = context.awaitCameraProvider()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = surfaceProvider
        }

        val capture = ImageCapture.Builder()
            // Latency matters more than the last few percent of quality here: the image is
            // downscaled to 1024px before it goes anywhere.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
        imageCapture = capture
    }

    fun takePicture(
        context: Context,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        val capture = imageCapture ?: run {
            onError("Camera is not ready yet.")
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // `use` guarantees the ImageProxy is closed; leaking one stalls the capture
                    // pipeline after a couple of shots.
                    image.use { onSuccess(it.toJpegBytes()) }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Could not take that photo.")
                }
            },
        )
    }

    fun unbind(context: Context) {
        imageCapture = null
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(this),
        )
    }

/** ImageCapture already yields JPEG in a single plane; this just lifts the bytes out. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
