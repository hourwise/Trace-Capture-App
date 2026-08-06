package uk.co.pcgsoft.tracecapture.inbox

import androidx.lifecycle.SavedStateHandle
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
    private val repository: CaptureRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _filter = MutableStateFlow(InboxFilter.PENDING)
    private val _searchQuery = MutableStateFlow("")
    private val _selection = MutableStateFlow(loadSelection())
    private val _pendingDelete = MutableStateFlow<CaptureItem?>(null)
    private val _actionInProgressIds = MutableStateFlow<Set<String>>(emptySet())
    private val _message = MutableStateFlow<InboxMessage?>(null)

    init {
        _selection
            .onEach(::persistSelection)
            .launchIn(viewModelScope)
    }

    private val _debouncedSearchQuery = _searchQuery
        .debounce { if (it.isEmpty()) 0 else 250 }
        .distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val baseUiState: StateFlow<BaseUiState> = combine(
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

        // Pure projection of visible captures + meta. Selection reconciliation happens in
        // exactly one place: the final combine below. Nothing here mutates _selection.
        baseFlow.map { captures ->
            BaseUiState(
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
        initialValue = BaseUiState(isLoading = true)
    )

    // Single authoritative reconciliation path: the latest visible captures reconcile the
    // persisted selection once, the reconciled result is stored once, and UI state renders
    // from that result. There is no write-back from rendered state, so there is no
    // two-way feedback loop. Reconciliation is skipped while loading so an initial empty
    // emission can never clobber a selection restored from SavedStateHandle.
    val uiState: StateFlow<InboxUiState> = combine(baseUiState, _selection) { base, selection ->
        val reconciled = if (base.isLoading) {
            selection
        } else {
            reconcileSelection(
                selection = selection,
                visibleIds = base.captures.asSequence().map { it.id }.toSet(),
                hasVisibleCaptures = base.captures.isNotEmpty()
            )
        }
        if (reconciled != selection) setSelection(reconciled)
        InboxUiState(
            isLoading = base.isLoading,
            filter = base.filter,
            searchQuery = base.searchQuery,
            captures = base.captures,
            selection = reconciled,
            pendingDelete = base.pendingDelete,
            actionInProgressIds = base.actionInProgressIds,
            message = base.message
        )
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

    fun onSelectionRequested() {
        val state = uiState.value
        if (state.isLoading || state.captures.isEmpty() || state.actionInProgressIds.isNotEmpty()) return
        setSelection(InboxSelectionState(isActive = true))
    }

    fun onCaptureLongPressed(id: String) {
        val state = uiState.value
        if (state.isLoading || state.actionInProgressIds.isNotEmpty()) return
        if (!state.selection.isActive) {
            if (state.captures.any { it.id == id }) {
                setSelection(InboxSelectionState(isActive = true, selectedIds = setOf(id)))
            }
        }
    }

    fun onSelectionToggled(id: String) {
        val state = uiState.value
        if (!state.selection.isActive || state.actionInProgressIds.isNotEmpty()) return
        if (state.captures.none { it.id == id }) return
        setSelection(state.selection.copy(selectedIds = state.selection.selectedIds.toggle(id)))
    }

    fun onSelectAllOrClear() {
        val state = uiState.value
        if (!state.selection.isActive || state.captures.isEmpty()) return
        val visibleIds = state.captures.mapTo(linkedSetOf()) { it.id }
        val allSelected = state.selection.selectedIds.size == visibleIds.size &&
            state.selection.selectedIds.containsAll(visibleIds)
        setSelection(
            state.selection.copy(
                selectedIds = if (allSelected) emptySet() else visibleIds
            )
        )
    }

    fun onSelectionItemsUnavailable(ids: Set<String>) {
        val unavailableCount = ids.intersect(_selection.value.selectedIds).size
        if (unavailableCount == 0) return
        setSelection(_selection.value.copy(selectedIds = _selection.value.selectedIds - ids))
        _message.value = InboxMessage.SelectedCapturesUnavailable(unavailableCount)
    }

    fun onSelectionExit() {
        val current = _selection.value
        // Idempotent: repeated calls (e.g. a LaunchedEffect re-running after recreation)
        // must not produce redundant persistence writes or re-enter selection.
        if (current.isActive || current.selectedIds.isNotEmpty()) {
            setSelection(InboxSelectionState())
        }
    }

    fun markReviewed(id: String) {
        if (_selection.value.isActive) return
        performAction(id, InboxAction.MARK_REVIEWED) { repository.markReviewed(id) }
    }

    fun archive(id: String) {
        if (_selection.value.isActive) return
        performAction(id, InboxAction.ARCHIVE) { repository.archive(id) }
    }

    fun restoreToPending(id: String) {
        if (_selection.value.isActive) return
        performAction(id, InboxAction.RESTORE) { repository.restoreToPending(id) }
    }

    fun onDeleteRequested(item: CaptureItem) {
        if (_selection.value.isActive) return
        _pendingDelete.value = item
    }

    fun onDeleteConfirmed() {
        if (_selection.value.isActive) return
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

    /**
     * Pure reconciliation: intersects persisted IDs with the visible IDs and exits
     * selection mode when nothing is visible. Never mutates state itself; the caller
     * (the single combine below) stores the result once.
     */
    private fun reconcileSelection(
        selection: InboxSelectionState,
        visibleIds: Set<String>,
        hasVisibleCaptures: Boolean
    ): InboxSelectionState {
        if (!hasVisibleCaptures) return InboxSelectionState()
        return selection.copy(selectedIds = selection.selectedIds.intersect(visibleIds))
    }

    private fun setSelection(selection: InboxSelectionState) {
        _selection.value = selection
    }

    private fun persistSelection(selection: InboxSelectionState) {
        savedStateHandle[KEY_SELECTION_ACTIVE] = selection.isActive
        savedStateHandle[KEY_SELECTED_IDS] = if (selection.selectedIds.size <= MAX_RESTORED_SELECTION) {
            ArrayList(selection.selectedIds)
        } else {
            // Keep contextual mode after recreation, but avoid a large Bundle payload.
            ArrayList<String>()
        }
    }

    private fun loadSelection(): InboxSelectionState {
        val active = savedStateHandle.get<Boolean>(KEY_SELECTION_ACTIVE) ?: false
        val ids = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_IDS)?.toSet().orEmpty()
        return InboxSelectionState(isActive = active, selectedIds = ids)
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

    private data class BaseUiState(
        val isLoading: Boolean = true,
        val filter: InboxFilter = InboxFilter.PENDING,
        val searchQuery: String = "",
        val captures: List<CaptureItem> = emptyList(),
        val pendingDelete: CaptureItem? = null,
        val actionInProgressIds: Set<String> = emptySet(),
        val message: InboxMessage? = null
    )

    private companion object {
        const val KEY_SELECTION_ACTIVE = "inbox_selection_active"
        const val KEY_SELECTED_IDS = "inbox_selected_ids"
        const val MAX_RESTORED_SELECTION = 500

        fun Set<String>.toggle(id: String): Set<String> =
            if (contains(id)) this - id else this + id
    }
}
