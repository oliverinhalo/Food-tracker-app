package dev.foodtracker.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foodtracker.data.recognition.CaptureStore
import dev.foodtracker.data.recognition.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val captureId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor,
    private val captureStore: CaptureStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /**
     * Compresses and parks the frame, then hands back an id. Compression runs off the main thread
     * inside [ImageCompressor]; the shutter is re-enabled only once the id exists, so a double tap
     * cannot start two analyses.
     */
    fun onFrameCaptured(jpegBytes: ByteArray) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val compressed = imageCompressor.compress(jpegBytes)
                captureStore.save(compressed)
            }.onSuccess { id ->
                _uiState.update { it.copy(isCapturing = false, captureId = id) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = error.message ?: "Could not process that photo.",
                    )
                }
            }
        }
    }

    /**
     * Imports an existing photo. The picker hands back a content URI whose permission grant lasts
     * only for this launch, so the bytes are read immediately rather than the URI being stored.
     */
    fun onGalleryImageSelected(uri: Uri) {
        if (_uiState.value.isCapturing) return
        _uiState.update { it.copy(isCapturing = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error("That image could not be opened.")

                val compressed = imageCompressor.compress(bytes)
                captureStore.save(compressed)
            }.onSuccess { id ->
                _uiState.update { it.copy(isCapturing = false, captureId = id) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = error.message ?: "Could not read that photo.",
                    )
                }
            }
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update { it.copy(isCapturing = false, errorMessage = message) }
    }

    /** Called once navigation to the results sheet has happened, so back-navigation is clean. */
    fun onCaptureConsumed() {
        _uiState.update { it.copy(captureId = null) }
    }
}
