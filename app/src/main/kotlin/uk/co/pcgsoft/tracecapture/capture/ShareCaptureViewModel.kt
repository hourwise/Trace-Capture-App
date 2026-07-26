package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemFactory
import uk.co.pcgsoft.tracecapture.data.local.CaptureValidator
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import javax.inject.Inject

sealed interface ShareCaptureUiState {
    data object Loading : ShareCaptureUiState
    data class Ready(
        val draft: CaptureDraft,
        val note: String,
        val duplicate: DuplicateCaptureWarning?,
        val isSaving: Boolean
    ) : ShareCaptureUiState
    data class Invalid(val reason: ShareRejectionReason) : ShareCaptureUiState
    data class Saved(val captureId: String) : ShareCaptureUiState
    data class Failed(val reason: ShareRejectionReason? = null, val draft: CaptureDraft?) : ShareCaptureUiState
}

@HiltViewModel
class ShareCaptureViewModel @Inject constructor(
    private val processor: SharedCaptureProcessor,
    private val repository: CaptureRepository,
    private val factory: CaptureItemFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareCaptureUiState>(ShareCaptureUiState.Loading)
    val uiState: StateFlow<ShareCaptureUiState> = _uiState.asStateFlow()

    private var currentDraft: CaptureDraft? = null

    fun processIntent(intent: Intent?) {
        if (intent == null) {
            _uiState.value = ShareCaptureUiState.Invalid(ShareRejectionReason.MISSING_CONTENT)
            return
        }

        val result = processor.process(intent)
        when (result) {
            is SharedCaptureResult.Ready -> {
                currentDraft = result.draft
                _uiState.value = ShareCaptureUiState.Ready(
                    draft = result.draft,
                    note = "",
                    duplicate = null,
                    isSaving = false
                )
                if (result.draft.primaryUrl != null) {
                    checkDuplicate(result.draft.primaryUrl)
                }
            }
            is SharedCaptureResult.Rejected -> {
                currentDraft = null
                _uiState.value = ShareCaptureUiState.Invalid(result.reason)
            }
        }
    }

    fun updateNote(note: String) {
        val current = _uiState.value as? ShareCaptureUiState.Ready ?: return
        if (note.length <= CaptureValidator.MAX_NOTE_LENGTH) {
            _uiState.value = current.copy(note = note)
        }
    }

    fun save() {
        val state = _uiState.value as? ShareCaptureUiState.Ready ?: return
        if (state.isSaving) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val draft = currentDraft ?: return@launch
                val note = state.note.ifBlank { null }

                val item = factory.create(
                    originalContent = draft.originalContent,
                    primaryUrl = draft.primaryUrl,
                    detectedUrls = draft.detectedUrls,
                    sourcePackageName = draft.sourcePackageName,
                    sourceLabel = draft.sourceLabel,
                    note = note,
                    captureType = draft.captureType
                )

                repository.save(item)

                _uiState.value = ShareCaptureUiState.Saved(captureId = item.id)
            } catch (e: Exception) {
                _uiState.value = ShareCaptureUiState.Failed(draft = currentDraft)
            }
        }
    }

    fun retry() {
        val state = _uiState.value as? ShareCaptureUiState.Failed ?: return
        currentDraft = state.draft
        if (state.draft != null) {
            _uiState.value = ShareCaptureUiState.Ready(
                draft = state.draft,
                note = "",
                duplicate = null,
                isSaving = false
            )
            if (state.draft.primaryUrl != null) {
                checkDuplicate(state.draft.primaryUrl)
            }
        }
    }

    private fun checkDuplicate(primaryUrl: String) {
        viewModelScope.launch {
            try {
                val duplicates = repository.findExactUrlDuplicates(primaryUrl)
                if (duplicates.isNotEmpty()) {
                    val newest = duplicates.maxBy { it.createdAtEpochMillis }
                    val current = _uiState.value
                    if (current is ShareCaptureUiState.Ready) {
                        _uiState.value = current.copy(
                            duplicate = DuplicateCaptureWarning(
                                existingCaptureId = newest.id,
                                capturedAtEpochMillis = newest.createdAtEpochMillis,
                                existingCount = duplicates.size
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Duplicate check failure should not block saving
            }
        }
    }
}
