package uk.co.pcgsoft.tracecapture.inbox

import uk.co.pcgsoft.tracecapture.domain.CaptureItem

data class InboxUiState(
    val isLoading: Boolean = true,
    val filter: InboxFilter = InboxFilter.PENDING,
    val searchQuery: String = "",
    val captures: List<CaptureItem> = emptyList(),
    val selection: InboxSelectionState = InboxSelectionState(),
    val pendingDelete: CaptureItem? = null,
    val actionInProgressIds: Set<String> = emptySet(),
    val message: InboxMessage? = null
)

data class InboxSelectionState(
    val isActive: Boolean = false,
    val selectedIds: Set<String> = emptySet()
)

sealed interface InboxMessage {
    data object LinkCopied : InboxMessage
    data class SelectedCapturesUnavailable(val count: Int) : InboxMessage
    data class ActionSucceeded(val action: InboxAction) : InboxMessage
    data class ActionFailed(val action: InboxAction) : InboxMessage
}

enum class InboxAction {
    MARK_REVIEWED,
    ARCHIVE,
    RESTORE,
    DELETE
}
