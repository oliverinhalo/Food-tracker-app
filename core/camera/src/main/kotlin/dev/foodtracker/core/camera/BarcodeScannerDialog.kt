package dev.foodtracker.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

object BarcodeScannerTestTags {
    const val DIALOG = "barcode_scanner_dialog"
    const val CANCEL = "barcode_scanner_cancel"
}

/**
 * Full-screen barcode scanner. Reports the first retail code it reads and then stops, so a
 * packet held in front of the camera cannot fire the callback repeatedly.
 */
@Composable
fun BarcodeScannerDialog(
    onBarcode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) onDismiss()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(BarcodeScannerTestTags.DIALOG),
        ) {
            if (hasPermission) {
                ScannerPreview(onBarcode = onBarcode)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .semantics { contentDescription = "Point the camera at a barcode" },
                )
                Text(
                    text = "Point at a product barcode",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .testTag(BarcodeScannerTestTags.CANCEL),
            ) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

@Composable
private fun ScannerPreview(onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)

    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.PERFORMANCE }
    }
    // Analysis runs off the main thread; ML Kit's callbacks come back on it.
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                // Barcode reading only needs the newest frame; queuing them adds latency.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(executor, BarcodeAnalyzer { currentOnBarcode(it) }) }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
