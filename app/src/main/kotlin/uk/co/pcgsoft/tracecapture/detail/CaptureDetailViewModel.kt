package uk.co.pcgsoft.tracecapture.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import javax.inject.Inject

@HiltViewModel
class CaptureDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CaptureRepository
) : ViewModel() {

    private val captureId: String = checkNotNull(savedStateHandle.get<String>("captureId")) {
        "captureId must be set in navigation route"
    }

    private val _uiState = MutableStateFlow(CaptureDetailUiState(isLoading = true))
    val uiState: StateFlow<CaptureDetailUiState> = _uiState.asStateFlow()
    private var noteSaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeById(captureId)
                .onStart {
                    _uiState.update { state ->
                        state.copy(isLoading = false, isMissing = true)
                    }
                }
                .collect { capture ->
                    _uiState.update { state ->
                        val noteDraft = if (state.capture == null || !state.noteChanged) {
                            capture?.note.orEmpty()
                        } else {
                            state.noteDraft
                        }
                        state.copy(
                            isLoading = false,
                            capture = capture,
                            isMissing = capture == null,
                            noteDraft = noteDraft,
                            noteChanged = noteDraft != capture?.note.orEmpty()
                        )
                    }
                }
        }
    }

    fun onNoteChanged(text: String) {
        if (text.length <= 2000) {
            _uiState.update { state ->
                state.copy(
                    noteDraft = text,
                    noteChanged = text != state.capture?.note.orEmpty()
                )
            }
        }
    }

    fun onSaveNote() {
        val state = uiState.value
        val capture = state.capture ?: return
        val note = state.noteDraft.trim().ifBlank { null }
        if (state.isSavingNote) return

        _uiState.update {
            it.copy(isSavingNote = true, actionInProgress = DetailAction.SAVE_NOTE)
        }
        noteSaveJob?.cancel()
        noteSaveJob = viewModelScope.launch {
            try {
                repository.updateNote(capture.id, note)
                _uiState.update { it.copy(message = CaptureDetailMessage.NoteSaved) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = CaptureDetailMessage.ActionFailed(DetailAction.SAVE_NOTE))
                }
            } finally {
                _uiState.update { it.copy(isSavingNote = false, actionInProgress = null) }
            }
        }
    }

    fun onDeleteRequested() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDeleteConfirmed() {
        val capture = uiState.value.capture ?: return
        if (uiState.value.actionInProgress != null) return

        _uiState.update {
            it.copy(actionInProgress = DetailAction.DELETE, showDeleteConfirmation = false)
        }

        viewModelScope.launch {
            try {
                repository.softDelete(capture.id)
                _uiState.update {
                    it.copy(
                        message = CaptureDetailMessage.CaptureRemoved,
                        pendingNavigation = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = CaptureDetailMessage.ActionFailed(DetailAction.DELETE))
                }
            } finally {
                _uiState.update { it.copy(actionInProgress = null) }
            }
        }
    }

    fun onDeleteCancelled() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onMarkReviewed() {
        performStatusAction(DetailAction.MARK_REVIEWED) {
            if (uiState.value.capture?.status == CaptureStatus.PENDING) {
                repository.markReviewed(captureId)
            }
        }
    }

    fun onArchive() {
        performStatusAction(DetailAction.ARCHIVE) {
            repository.archive(captureId)
        }
    }

    fun onRestoreToPending() {
        performStatusAction(DetailAction.RESTORE_PENDING) {
            repository.restoreToPending(captureId)
        }
    }

    private fun performStatusAction(action: DetailAction, block: suspend () -> Unit) {
        if (uiState.value.actionInProgress != null) return

        _uiState.update { it.copy(actionInProgress = action) }
        viewModelScope.launch {
            try {
                block()
                val message = when (action) {
                    DetailAction.MARK_REVIEWED -> CaptureDetailMessage.MarkedReviewed
                    DetailAction.ARCHIVE -> CaptureDetailMessage.Archived
                    DetailAction.RESTORE_PENDING -> CaptureDetailMessage.Restored
                    else -> return@launch
                }
                _uiState.update { it.copy(message = message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = CaptureDetailMessage.ActionFailed(action)) }
            } finally {
                _uiState.update { it.copy(actionInProgress = null) }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onLinkCopied() {
        _uiState.update { it.copy(message = CaptureDetailMessage.LinkCopied) }
    }

    fun onContentCopied() {
        _uiState.update { it.copy(message = CaptureDetailMessage.ContentCopied) }
    }

    fun onBackRequested(): Boolean {
        val state = uiState.value
        if (state.noteChanged && !state.pendingNavigation) {
            _uiState.update { it.copy(showUnsavedChangesDialog = true) }
            return false
        }
        return true
    }

    fun onKeepEditing() {
        _uiState.update { it.copy(showUnsavedChangesDialog = false) }
    }

    fun onDiscardChanges() {
        _uiState.update {
            it.copy(showUnsavedChangesDialog = false, pendingNavigation = true)
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(pendingNavigation = false) }
    }

}
