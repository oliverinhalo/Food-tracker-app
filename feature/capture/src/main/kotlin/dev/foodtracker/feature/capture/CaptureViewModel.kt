package dev.foodtracker.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foodtracker.data.recognition.CaptureStore
import dev.foodtracker.data.recognition.ImageCompressor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val captureId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
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

    fun onCaptureFailed(message: String) {
        _uiState.update { it.copy(isCapturing = false, errorMessage = message) }
    }

    /** Called once navigation to the results sheet has happened, so back-navigation is clean. */
    fun onCaptureConsumed() {
        _uiState.update { it.copy(captureId = null) }
    }
}
