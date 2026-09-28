package dev.foodtracker.core.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage

/**
 * Reads product barcodes from the camera stream.
 *
 * Restricted to the retail symbologies Open Food Facts indexes: scanning for every format ML Kit
 * supports is measurably slower and invites false positives from QR codes on packaging.
 */
@OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build(),
    )

    /** Set once a code is accepted, so the rest of the in-flight frames are ignored. */
    @Volatile
    private var finished = false

    override fun analyze(imageProxy: ImageProxy) {
        if (finished) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue }?.takeIf { it.isNotBlank() }
                if (value != null && !finished) {
                    finished = true
                    onBarcode(value)
                }
            }
            // The proxy must be closed on every path or the analyser stalls after a few frames.
            .addOnCompleteListener { imageProxy.close() }
    }
}
