package uk.co.pcgsoft.tracecapture.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: CaptureRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(InboxFilter.PENDING)
    private val _searchQuery = MutableStateFlow("")
    private val _pendingDelete = MutableStateFlow<CaptureItem?>(null)
    private val _actionInProgressIds = MutableStateFlow<Set<String>>(emptySet())
    private val _message = MutableStateFlow<InboxMessage?>(null)

    private val _debouncedSearchQuery = _searchQuery
        .debounce { if (it.isEmpty()) 0 else 250 }
        .distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<InboxUiState> = combine(
        combine(_filter, _searchQuery, _debouncedSearchQuery.onStart { emit("") }) { f, s, ds -> 
            Triple(f, s, ds) 
        },
        combine(_pendingDelete, _actionInProgressIds, _message) { p, a, m -> 
            Triple(p, a, m) 
        }
    ) { t1, t2 ->
        InternalState(
            filter = t1.first,
            searchQuery = t1.second,
            debouncedSearchQuery = t1.third,
            pendingDelete = t2.first,
            actionInProgressIds = t2.second,
            message = t2.third
        )
    }.flatMapLatest { state ->
        val query = state.debouncedSearchQuery.trim()
        val baseFlow: Flow<List<CaptureItem>> = if (query.isEmpty()) {
            when (state.filter) {
                InboxFilter.PENDING -> repository.observeByStatus(CaptureStatus.PENDING)
                InboxFilter.REVIEWED -> repository.observeByStatus(CaptureStatus.REVIEWED)
                InboxFilter.ARCHIVED -> repository.observeByStatus(CaptureStatus.ARCHIVED)
                InboxFilter.ALL -> repository.observeInbox()
            }
        } else {
            repository.search(query).map { captures ->
                val statusToFilter: CaptureStatus? = when (state.filter) {
                    InboxFilter.PENDING -> CaptureStatus.PENDING
                    InboxFilter.REVIEWED -> CaptureStatus.REVIEWED
                    InboxFilter.ARCHIVED -> CaptureStatus.ARCHIVED
                    InboxFilter.ALL -> null
                }
                if (statusToFilter != null) {
                    captures.filter { it.status == statusToFilter }
                } else {
                    captures
                }
            }
        }

        baseFlow.map { captures ->
            InboxUiState(
                isLoading = false,
                filter = state.filter,
                searchQuery = state.searchQuery,
                captures = captures,
                pendingDelete = state.pendingDelete,
                actionInProgressIds = state.actionInProgressIds,
                message = state.message
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxUiState(isLoading = true)
    )

    fun onFilterSelected(filter: InboxFilter) {
        _filter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun markReviewed(id: String) {
        performAction(id, InboxAction.MARK_REVIEWED) { repository.markReviewed(id) }
    }

    fun archive(id: String) {
        performAction(id, InboxAction.ARCHIVE) { repository.archive(id) }
    }

    fun restoreToPending(id: String) {
        performAction(id, InboxAction.RESTORE) { repository.restoreToPending(id) }
    }

    fun onDeleteRequested(item: CaptureItem) {
        _pendingDelete.value = item
    }

    fun onDeleteConfirmed() {
        val item = _pendingDelete.value ?: return
        _pendingDelete.value = null
        performAction(item.id, InboxAction.DELETE) { repository.softDelete(item.id) }
    }

    fun onDeleteCancelled() {
        _pendingDelete.value = null
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun onLinkCopied() {
        _message.value = InboxMessage.LinkCopied
    }

    private fun performAction(id: String, actionType: InboxAction, action: suspend () -> Unit) {
        if (_actionInProgressIds.value.contains(id)) return

        _actionInProgressIds.value = _actionInProgressIds.value + id
        viewModelScope.launch {
            try {
                action()
                _message.value = InboxMessage.ActionSucceeded(actionType)
            } catch (e: Exception) {
                _message.value = InboxMessage.ActionFailed(actionType)
            } finally {
                _actionInProgressIds.value = _actionInProgressIds.value - id
            }
        }
    }

    private data class InternalState(
        val filter: InboxFilter,
        val searchQuery: String,
        val debouncedSearchQuery: String,
        val pendingDelete: CaptureItem?,
        val actionInProgressIds: Set<String>,
        val message: InboxMessage?
    )
}
