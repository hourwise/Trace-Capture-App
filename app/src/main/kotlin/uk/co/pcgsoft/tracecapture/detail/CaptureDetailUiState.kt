package uk.co.pcgsoft.tracecapture.detail

import uk.co.pcgsoft.tracecapture.domain.CaptureItem

data class CaptureDetailUiState(
    val isLoading: Boolean = true,
    val capture: CaptureItem? = null,
    val isMissing: Boolean = false,
    val noteDraft: String = "",
    val noteChanged: Boolean = false,
    val isSavingNote: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val showUnsavedChangesDialog: Boolean = false,
    val pendingNavigation: Boolean = false,
    val actionInProgress: DetailAction? = null,
    val message: CaptureDetailMessage? = null
)

sealed interface CaptureDetailMessage {
    data object NoteSaved : CaptureDetailMessage
    data object MarkedReviewed : CaptureDetailMessage
    data object Archived : CaptureDetailMessage
    data object Restored : CaptureDetailMessage
    data object LinkCopied : CaptureDetailMessage
    data object ContentCopied : CaptureDetailMessage
    data object CaptureRemoved : CaptureDetailMessage
    data class ActionFailed(val action: DetailAction) : CaptureDetailMessage
}

enum class DetailAction {
    SAVE_NOTE,
    MARK_REVIEWED,
    RESTORE_PENDING,
    ARCHIVE,
    DELETE
}
