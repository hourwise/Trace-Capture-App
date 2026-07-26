package uk.co.pcgsoft.tracecapture.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.R
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
    private val _isLoading = MutableStateFlow(true)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<InboxUiState> = combine(
        _isLoading,
        _filter,
        _searchQuery.debounce(250),
        _pendingDelete,
        _actionInProgressIds,
        _message
    ) { args: Array<Any?> ->
        CombinedParams(
            isLoading = args[0] as Boolean,
            filter = args[1] as InboxFilter,
            searchQuery = args[2] as String,
            pendingDelete = args[3] as CaptureItem?,
            actionInProgressIds = args[4] as Set<String>,
            message = args[5] as InboxMessage?
        )
    }.flatMapLatest { params: CombinedParams ->
        val query = params.searchQuery.trim()
        val baseFlow: Flow<List<CaptureItem>> = if (query.isEmpty()) {
            when (params.filter) {
                InboxFilter.PENDING -> repository.observeByStatus(CaptureStatus.PENDING)
                InboxFilter.REVIEWED -> repository.observeByStatus(CaptureStatus.REVIEWED)
                InboxFilter.ARCHIVED -> repository.observeByStatus(CaptureStatus.ARCHIVED)
                InboxFilter.ALL -> repository.observeInbox()
            }
        } else {
            repository.search(query)
        }

        baseFlow.map { captures ->
            val statusToFilter: CaptureStatus? = when (params.filter) {
                InboxFilter.PENDING -> CaptureStatus.PENDING
                InboxFilter.REVIEWED -> CaptureStatus.REVIEWED
                InboxFilter.ARCHIVED -> CaptureStatus.ARCHIVED
                InboxFilter.ALL -> null
            }

            val filtered = if (query.isNotEmpty() && statusToFilter != null) {
                captures.filter { it.status == statusToFilter }
            } else {
                captures
            }
            InboxUiState(
                isLoading = false,
                filter = params.filter,
                searchQuery = params.searchQuery,
                captures = filtered,
                pendingDelete = params.pendingDelete,
                actionInProgressIds = params.actionInProgressIds,
                message = params.message
            )
        }
    }.onEach { state ->
        if (_isLoading.value && (state.captures.isNotEmpty() || !state.isLoading)) {
            _isLoading.value = false
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = InboxUiState(isLoading = true)
    )

    fun onFilterSelected(filter: InboxFilter) {
        _filter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun markReviewed(id: String) {
        performAction(id) { repository.markReviewed(id) }
    }

    fun archive(id: String) {
        performAction(id) { repository.archive(id) }
    }

    fun restoreToPending(id: String) {
        performAction(id) { repository.restoreToPending(id) }
    }

    fun onDeleteRequested(item: CaptureItem) {
        _pendingDelete.value = item
    }

    fun onDeleteConfirmed() {
        val item = _pendingDelete.value ?: return
        _pendingDelete.value = null
        performAction(item.id) { repository.softDelete(item.id) }
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

    private fun performAction(id: String, action: suspend () -> Unit) {
        if (_actionInProgressIds.value.contains(id)) return

        _actionInProgressIds.value = _actionInProgressIds.value + id
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                _message.value = InboxMessage.Error(R.string.save_failed)
            } finally {
                _actionInProgressIds.value = _actionInProgressIds.value - id
            }
        }
    }

    private data class CombinedParams(
        val isLoading: Boolean,
        val filter: InboxFilter,
        val searchQuery: String,
        val pendingDelete: CaptureItem?,
        val actionInProgressIds: Set<String>,
        val message: InboxMessage?
    )
}
