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
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun item(id: String, createdAt: Long) = CaptureItem(
        id = id,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
        originalContent = id,
        captureType = CaptureType.TEXT,
        status = CaptureStatus.PENDING,
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
