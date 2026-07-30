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

    private val captureId: String? = savedStateHandle.get<String>("captureId")
        ?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(CaptureDetailUiState(isLoading = true))
    val uiState: StateFlow<CaptureDetailUiState> = _uiState.asStateFlow()
    private var noteSaveJob: Job? = null
    private var hasLoadedCapture = false
    private var removalHandled = false

    init {
        val id = captureId
        if (id == null) {
            _uiState.value = CaptureDetailUiState(isLoading = false, isMissing = true)
        } else {
            viewModelScope.launch {
                repository.observeById(id).collect { capture ->
                    when {
                        capture != null -> updateLoadedCapture(capture)
                        hasLoadedCapture -> signalCaptureRemoved()
                        else -> _uiState.update {
                            it.copy(isLoading = false, isMissing = true, capture = null)
                        }
                    }
                }
            }
        }
    }

    private fun updateLoadedCapture(capture: uk.co.pcgsoft.tracecapture.domain.CaptureItem) {
        if (removalHandled) return

        hasLoadedCapture = true
        _uiState.update { state ->
            val noteDraft = if (state.capture == null || !state.noteChanged) {
                capture.note.orEmpty()
            } else {
                state.noteDraft
            }
            state.copy(
                isLoading = false,
                capture = capture,
                isMissing = false,
                noteDraft = noteDraft,
                noteChanged = noteDraft != capture.note.orEmpty()
            )
        }
    }

    private fun signalCaptureRemoved() {
        if (removalHandled) return

        removalHandled = true
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isMissing = false,
                isSavingNote = false,
                actionInProgress = null,
                showDeleteConfirmation = false,
                showUnsavedChangesDialog = false,
                message = CaptureDetailMessage.CaptureRemoved,
                pendingNavigation = true
            )
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
        val captureId = capture.id
        val note = state.noteDraft.trim().ifBlank { null }
        if (state.isSavingNote) return

        _uiState.update {
            it.copy(isSavingNote = true, actionInProgress = DetailAction.SAVE_NOTE)
        }
        noteSaveJob?.cancel()
        noteSaveJob = viewModelScope.launch {
            try {
                repository.updateNote(captureId, note)
                if (!removalHandled) {
                    _uiState.update { it.copy(message = CaptureDetailMessage.NoteSaved) }
                }
            } catch (e: Exception) {
                if (!removalHandled) {
                    _uiState.update {
                        it.copy(message = CaptureDetailMessage.ActionFailed(DetailAction.SAVE_NOTE))
                    }
                }
            } finally {
                if (!removalHandled) {
                    _uiState.update { it.copy(isSavingNote = false, actionInProgress = null) }
                }
            }
        }
    }

    fun onDeleteRequested() {
        if (uiState.value.capture != null && !removalHandled) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun onDeleteConfirmed() {
        val capture = uiState.value.capture ?: return
        if (uiState.value.actionInProgress != null || removalHandled) return
        val captureId = capture.id

        _uiState.update {
            it.copy(actionInProgress = DetailAction.DELETE, showDeleteConfirmation = false)
        }

        viewModelScope.launch {
            try {
                repository.softDelete(captureId)
                signalCaptureRemoved()
            } catch (e: Exception) {
                if (!removalHandled) {
                    _uiState.update {
                        it.copy(message = CaptureDetailMessage.ActionFailed(DetailAction.DELETE))
                    }
                }
            } finally {
                if (!removalHandled) {
                    _uiState.update { it.copy(actionInProgress = null) }
                }
            }
        }
    }

    fun onDeleteCancelled() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onMarkReviewed() {
        val capture = uiState.value.capture ?: return
        if (capture.status != CaptureStatus.PENDING) return

        performStatusAction(DetailAction.MARK_REVIEWED) {
            repository.markReviewed(capture.id)
        }
    }

    fun onArchive() {
        val capture = uiState.value.capture ?: return
        if (capture.status !in setOf(CaptureStatus.PENDING, CaptureStatus.REVIEWED)) return

        performStatusAction(DetailAction.ARCHIVE) {
            repository.archive(capture.id)
        }
    }

    fun onRestoreToPending() {
        val capture = uiState.value.capture ?: return
        if (capture.status !in setOf(CaptureStatus.REVIEWED, CaptureStatus.ARCHIVED)) return

        performStatusAction(DetailAction.RESTORE_PENDING) {
            repository.restoreToPending(capture.id)
        }
    }

    private fun performStatusAction(action: DetailAction, block: suspend () -> Unit) {
        if (uiState.value.actionInProgress != null || removalHandled) return

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
                if (!removalHandled) {
                    _uiState.update { it.copy(message = message) }
                }
            } catch (e: Exception) {
                if (!removalHandled) {
                    _uiState.update { it.copy(message = CaptureDetailMessage.ActionFailed(action)) }
                }
            } finally {
                if (!removalHandled) {
                    _uiState.update { it.copy(actionInProgress = null) }
                }
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
