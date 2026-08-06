package uk.co.pcgsoft.tracecapture.inbox

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

@OptIn(ExperimentalCoroutinesApi::class)
class BulkExportSelectionTest {

    private val repository = mockk<CaptureRepository>(relaxed = true)
    private val pendingFlow = MutableStateFlow<List<CaptureItem>>(emptyList())
    private val reviewedFlow = MutableStateFlow<List<CaptureItem>>(emptyList())
    private val inboxFlow = MutableStateFlow<List<CaptureItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeByStatus(CaptureStatus.PENDING) } returns pendingFlow
        every { repository.observeByStatus(CaptureStatus.REVIEWED) } returns reviewedFlow
        every { repository.observeByStatus(CaptureStatus.ARCHIVED) } returns MutableStateFlow(emptyList())
        every { repository.observeInbox() } returns inboxFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectionIsIdBasedAndTogglesWithoutDuplicates() = runTest {
        val items = listOf(item("first", 2_000L), item("second", 1_000L))
        pendingFlow.value = items
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("first")
        viewModel.onSelectionToggled("first")
        viewModel.onSelectionToggled("first")

        assertEquals(setOf("first"), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)
        job.cancel()
    }

    @Test
    fun selectAllAndClearAllUseCurrentVisibleResults() = runTest {
        val items = listOf(item("pending", 2_000L))
        pendingFlow.value = items
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSelectionRequested()
        viewModel.onSelectAllOrClear()
        assertEquals(setOf("pending"), viewModel.uiState.value.selection.selectedIds)
        viewModel.onSelectAllOrClear()
        assertTrue(viewModel.uiState.value.selection.selectedIds.isEmpty())
        job.cancel()
    }

    @Test
    fun changingResultsReconcilesSelectionAndExitsWhenEmpty() = runTest {
        val selected = item("selected", 2_000L)
        val other = item("other", 1_000L)
        pendingFlow.value = listOf(selected, other)
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("selected")

        pendingFlow.value = listOf(other)
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)

        pendingFlow.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.selection.isActive)
        job.cancel()
    }

    @Test
    fun smallSelectionIsRestoredFromSavedStateHandle() = runTest {
        val selected = item("selected", 2_000L)
        pendingFlow.value = listOf(selected)
        val savedState = SavedStateHandle()
        val first = InboxViewModel(repository, savedState)
        val firstJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.uiState.collect() }
        advanceUntilIdle()
        first.onSelectionRequested()
        first.onSelectionToggled("selected")
        advanceUntilIdle()

        val restored = InboxViewModel(repository, savedState)
        val restoredJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect() }
        advanceUntilIdle()

        assertTrue(restored.uiState.value.selection.isActive)
        assertEquals(setOf("selected"), restored.uiState.value.selection.selectedIds)
        firstJob.cancel()
        restoredJob.cancel()
    }

    @Test
    fun largeSelectionRestoresModeWithoutPersistingAllIds() = runTest {
        val items = (0 until 501).map { index -> item("id-$index", index.toLong()) }
        pendingFlow.value = items
        val savedState = SavedStateHandle()
        val first = InboxViewModel(repository, savedState)
        val firstJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.uiState.collect() }
        advanceUntilIdle()
        first.onSelectionRequested()
        first.onSelectAllOrClear()
        advanceUntilIdle()

        val restored = InboxViewModel(repository, savedState)
        val restoredJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect() }
        advanceUntilIdle()

        assertTrue(restored.uiState.value.selection.isActive)
        assertTrue(restored.uiState.value.selection.selectedIds.isEmpty())
        firstJob.cancel()
        restoredJob.cancel()
    }

    @Test
    fun selectionUpdatesExactlyOnceWhenItemDisappears() = runTest {
        pendingFlow.value = listOf(item("a", 2_000L), item("b", 1_000L))
        val viewModel = InboxViewModel(repository)
        var emissions = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { emissions++ }
        }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("a")
        viewModel.onSelectionToggled("b")
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), viewModel.uiState.value.selection.selectedIds)

        val before = emissions
        pendingFlow.value = listOf(item("b", 1_000L)) // "a" disappears
        advanceUntilIdle()
        val after = emissions

        assertEquals(setOf("b"), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)
        // One reconcile write plus its render: bounded, never a storm.
        assertTrue(after - before <= 2)
        // Fully settled: no self-sustaining feedback.
        advanceUntilIdle()
        assertEquals(after, emissions)
        job.cancel()
    }

    @Test
    fun reconciliationSettlesWithoutInfiniteEmissions() = runTest {
        pendingFlow.value = listOf(item("a", 3_000L), item("b", 2_000L))
        val viewModel = InboxViewModel(repository)
        var emissions = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { emissions++ }
        }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("a")
        viewModel.onSelectionToggled("b")
        advanceUntilIdle()

        // Settled: further idle advancement must not produce any emissions, proving
        // there is no self-sustaining write-back loop.
        advanceUntilIdle()
        val settled = emissions
        advanceUntilIdle()
        assertEquals(settled, emissions)

        // A normal Room emission that does not affect the selection renders exactly
        // once and then settles again.
        val before = emissions
        pendingFlow.value = listOf(item("a", 3_000L), item("b", 2_000L), item("c", 1_000L))
        advanceUntilIdle()
        val after = emissions
        assertEquals(before + 1, after)
        advanceUntilIdle()
        assertEquals(after, emissions)
        job.cancel()
    }

    @Test
    fun filterChangeReconcilesSelection() = runTest {
        val pending = item("pending", 2_000L)
        val reviewed = item("reviewed", 1_000L, status = CaptureStatus.REVIEWED)
        pendingFlow.value = listOf(pending)
        reviewedFlow.value = listOf(reviewed)
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("pending")
        assertEquals(setOf("pending"), viewModel.uiState.value.selection.selectedIds)

        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)
        job.cancel()
    }

    @Test
    fun searchChangeReconcilesSelection() = runTest {
        val searchFlow = MutableStateFlow<List<CaptureItem>>(emptyList())
        every { repository.search("alpha") } returns searchFlow
        pendingFlow.value = listOf(item("match", 2_000L), item("other", 1_000L))
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("match")
        assertEquals(setOf("match"), viewModel.uiState.value.selection.selectedIds)

        // Switch to a search whose results exclude the selected item.
        viewModel.onSearchQueryChanged("alpha")
        searchFlow.value = listOf(item("other", 1_000L))
        advanceTimeBy(250)
        runCurrent()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)
        job.cancel()
    }

    @Test
    fun emptyResultExitsSelectionMode() = runTest {
        pendingFlow.value = listOf(item("a", 2_000L))
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        assertTrue(viewModel.uiState.value.selection.isActive)

        pendingFlow.value = emptyList()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.selection.isActive)
        assertTrue(viewModel.uiState.value.selection.selectedIds.isEmpty())
        job.cancel()
    }

    @Test
    fun selectedCountStableDuringNormalRoomEmissions() = runTest {
        pendingFlow.value = listOf(item("a", 3_000L), item("b", 2_000L))
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("a")
        viewModel.onSelectionToggled("b")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.selection.selectedIds.size)

        // Normal Room emissions (new unselected items appearing) must not disturb
        // the selected set or its count.
        repeat(5) { index ->
            pendingFlow.value = listOf(
                item("a", 3_000L),
                item("b", 2_000L),
                item("c-$index", 1_000L - index)
            )
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.selection.selectedIds.size)
        }
        assertEquals(setOf("a", "b"), viewModel.uiState.value.selection.selectedIds)
        job.cancel()
    }

    @Test
    fun staleRestoredIdsAreRemovedAfterFirstRoomEmission() = runTest {
        pendingFlow.value = listOf(item("fresh", 2_000L))
        val savedState = SavedStateHandle()
        savedState["inbox_selection_active"] = true
        savedState["inbox_selected_ids"] = ArrayList(listOf("stale", "fresh"))
        val viewModel = InboxViewModel(repository, savedState)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()

        // The first real Room emission prunes the stale ID but keeps the fresh one.
        assertEquals(setOf("fresh"), viewModel.uiState.value.selection.selectedIds)
        assertTrue(viewModel.uiState.value.selection.isActive)
        job.cancel()
    }

    @Test
    fun savedStateHandleStoresOnlyScalarsAndIdList() = runTest {
        pendingFlow.value = listOf(item("a", 2_000L))
        val savedState = SavedStateHandle()
        val viewModel = InboxViewModel(repository, savedState)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        viewModel.onSelectionToggled("a")
        advanceUntilIdle()

        // Only the two scalar/id-list keys may enter SavedStateHandle — never capture objects.
        assertEquals(setOf("inbox_selection_active", "inbox_selected_ids"), savedState.keys())
        assertEquals(true, savedState.get<Boolean>("inbox_selection_active"))
        assertEquals(listOf("a"), savedState.get<ArrayList<String>>("inbox_selected_ids"))
        job.cancel()
    }

    @Test
    fun selectionExitIsIdempotent() = runTest {
        pendingFlow.value = listOf(item("a", 2_000L))
        val viewModel = InboxViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        advanceUntilIdle()
        viewModel.onSelectionRequested()
        assertTrue(viewModel.uiState.value.selection.isActive)

        viewModel.onSelectionExit()
        viewModel.onSelectionExit()
        viewModel.onSelectionExit()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.selection.isActive)
        assertTrue(viewModel.uiState.value.selection.selectedIds.isEmpty())
        job.cancel()
    }

    private fun item(
        id: String,
        createdAt: Long,
        status: CaptureStatus = CaptureStatus.PENDING
    ) = CaptureItem(
        id = id,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
        originalContent = id,
        captureType = CaptureType.TEXT,
        status = status,
        primaryUrl = null,
        detectedUrls = emptyList(),
        sourcePackageName = null,
        sourceLabel = null,
        note = null,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )
}
