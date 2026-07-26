package uk.co.pcgsoft.tracecapture.inbox

import uk.co.pcgsoft.tracecapture.domain.CaptureItem

data class InboxUiState(
    val isLoading: Boolean = true,
    val filter: InboxFilter = InboxFilter.PENDING,
    val searchQuery: String = "",
    val captures: List<CaptureItem> = emptyList(),
    val pendingDelete: CaptureItem? = null,
    val actionInProgressIds: Set<String> = emptySet(),
    val message: InboxMessage? = null
)

sealed interface InboxMessage {
    data class Info(val messageResId: Int, val formatArgs: List<Any> = emptyList()) : InboxMessage
    data class Error(val messageResId: Int) : InboxMessage
    data object LinkCopied : InboxMessage
}
