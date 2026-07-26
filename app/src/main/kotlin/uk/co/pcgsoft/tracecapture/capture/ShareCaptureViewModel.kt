package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    data class Failed(
        val draft: CaptureDraft,
        val note: String,
        val duplicate: DuplicateCaptureWarning?,
        val message: String
    ) : ShareCaptureUiState
}

@HiltViewModel
class ShareCaptureViewModel @Inject constructor(
    private val processor: SharedCaptureProcessor,
    private val repository: CaptureRepository,
    private val factory: CaptureItemFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareCaptureUiState>(ShareCaptureUiState.Loading)
    val uiState: StateFlow<ShareCaptureUiState> = _uiState.asStateFlow()

    private var duplicateLookupJob: Job? = null
    private var saveJob: Job? = null

    fun processIntent(intent: Intent?) {
        if (isSaving()) return

        duplicateLookupJob?.cancel()
        duplicateLookupJob = null

        if (intent == null) {
            _uiState.value = ShareCaptureUiState.Invalid(ShareRejectionReason.MISSING_CONTENT)
            return
        }

        val result = processor.process(intent)
        when (result) {
            is SharedCaptureResult.Ready -> {
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

        val draftToSave = state.draft
        val noteToSave = state.note.ifBlank { null }

        _uiState.value = state.copy(isSaving = true)

        saveJob = viewModelScope.launch {
            try {
                val item = factory.create(
                    originalContent = draftToSave.originalContent,
                    primaryUrl = draftToSave.primaryUrl,
                    detectedUrls = draftToSave.detectedUrls,
                    sourcePackageName = draftToSave.sourcePackageName,
                    sourceLabel = draftToSave.sourceLabel,
                    note = noteToSave,
                    captureType = draftToSave.captureType
                )

                repository.save(item)

                _uiState.value = ShareCaptureUiState.Saved(captureId = item.id)
            } catch (e: Exception) {
                val currentState = _uiState.value
                if (currentState is ShareCaptureUiState.Ready) {
                    _uiState.value = ShareCaptureUiState.Failed(
                        draft = draftToSave,
                        note = state.note,
                        duplicate = currentState.duplicate,
                        message = "TRACE Capture could not save this item. Nothing was lost; try again."
                    )
                }
            }
        }
    }

    fun retry() {
        val state = _uiState.value as? ShareCaptureUiState.Failed ?: return
        _uiState.value = ShareCaptureUiState.Ready(
            draft = state.draft,
            note = state.note,
            duplicate = state.duplicate,
            isSaving = false
        )
    }

    private fun isSaving(): Boolean {
        return _uiState.value is ShareCaptureUiState.Ready &&
            (_uiState.value as ShareCaptureUiState.Ready).isSaving
    }

    private fun checkDuplicate(primaryUrl: String) {
        duplicateLookupJob = viewModelScope.launch {
            try {
                val duplicates = repository.findExactUrlDuplicates(primaryUrl)
                if (duplicates.isNotEmpty()) {
                    val newest = duplicates.maxBy { it.createdAtEpochMillis }
                    val current = _uiState.value
                    if (current is ShareCaptureUiState.Ready &&
                        current.draft.primaryUrl == primaryUrl
                    ) {
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
