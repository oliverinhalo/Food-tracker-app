package dev.foodtracker.feature.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foodtracker.core.ui.component.MessageState
import dev.foodtracker.core.ui.component.rememberHaptics

object CaptureTestTags {
    const val SHUTTER = "capture_shutter"
    const val PREVIEW = "capture_preview"
    const val PERMISSION_STATE = "capture_permission_state"
    const val GALLERY = "capture_gallery"
}

@Composable
fun CaptureRoute(
    onCaptureReady: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.captureId) {
        state.captureId?.let {
            onCaptureReady(it)
            viewModel.onCaptureConsumed()
        }
    }

    CaptureScreen(
        state = state,
        onFrameCaptured = viewModel::onFrameCaptured,
        onCaptureFailed = viewModel::onCaptureFailed,
        onGalleryImageSelected = viewModel::onGalleryImageSelected,
        modifier = modifier,
    )
}

@Composable
internal fun CaptureScreen(
    state: CaptureUiState,
    onFrameCaptured: (ByteArray) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onGalleryImageSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    // The photo picker needs no permission of its own, so importing from the gallery stays
    // available even when camera access has been refused.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onGalleryImageSelected) }

    fun pickFromGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                state = state,
                onFrameCaptured = onFrameCaptured,
                onCaptureFailed = onCaptureFailed,
                onPickFromGallery = ::pickFromGallery,
            )
        } else {
            PermissionState(
                permanentlyDenied = permissionRequested,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
                onPickFromGallery = ::pickFromGallery,
            )
        }

        state.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) { Text(message) }
        }
    }
}

@Composable
private fun BoxScope.CameraPreview(
    state: CaptureUiState,
    onFrameCaptured: (ByteArray) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val controller = remember { CameraController() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) {
        runCatching { controller.bind(context, lifecycleOwner, previewView.surfaceProvider) }
            .onFailure { onCaptureFailed(it.message ?: "Could not start the camera.") }
    }

    DisposableEffect(Unit) {
        onDispose { controller.unbind(context) }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxSize()
            .testTag(CaptureTestTags.PREVIEW)
            .semantics { contentDescription = "Camera viewfinder" },
    )

    FloatingActionButton(
        onClick = {
            if (state.isCapturing) return@FloatingActionButton
            haptics.confirm()
            controller.takePicture(context, onSuccess = onFrameCaptured, onError = onCaptureFailed)
        },
        shape = CircleShape,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
            .size(72.dp)
            .testTag(CaptureTestTags.SHUTTER)
            .semantics { contentDescription = "Take a photo of your meal" },
    ) {
        if (state.isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    FilledTonalIconButton(
        onClick = onPickFromGallery,
        enabled = !state.isCapturing,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(start = 28.dp, bottom = 54.dp)
            .size(48.dp)
            .testTag(CaptureTestTags.GALLERY)
            .semantics { contentDescription = "Choose a photo from your gallery" },
    ) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
    }
}

@Composable
private fun BoxScope.PermissionState(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .testTag(CaptureTestTags.PERMISSION_STATE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MessageState(
            icon = Icons.Default.NoPhotography,
            title = "Camera access needed",
            body = "Food Tracker uses the camera to identify what's on your plate. Photos stay on your device unless you turn on cloud analysis.",
            actionLabel = if (permanentlyDenied) "Open settings" else "Allow camera",
            onAction = if (permanentlyDenied) onOpenSettings else onRequest,
        )
        TextButton(
            onClick = onPickFromGallery,
            modifier = Modifier.testTag(CaptureTestTags.GALLERY),
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose from gallery")
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
