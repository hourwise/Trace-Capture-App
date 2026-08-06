package uk.co.pcgsoft.tracecapture.inbox

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.settings.DefaultInboxFilter
import uk.co.pcgsoft.tracecapture.settings.FakeSettingsRepository
import uk.co.pcgsoft.tracecapture.settings.SettingsDefaults
import uk.co.pcgsoft.tracecapture.settings.SettingsRepository
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private lateinit var viewModel: InboxViewModel
    private val repository: CaptureRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val pendingItem = mockk<CaptureItem> {
        every { id } returns "1"
        every { status } returns CaptureStatus.PENDING
        every { originalContent } returns "Pending content"
        every { primaryUrl } returns "https://example.com/1"
        every { note } returns null
        every { sourceLabel } returns "Chrome"
    }

    private val reviewedItem = mockk<CaptureItem> {
        every { id } returns "2"
        every { status } returns CaptureStatus.REVIEWED
        every { originalContent } returns "Reviewed content"
        every { primaryUrl } returns "https://example.com/2"
        every { note } returns "Some note"
        every { sourceLabel } returns "Firefox"
    }

    private val pendingFlow = MutableStateFlow(listOf(pendingItem))
    private val reviewedFlow = MutableStateFlow(listOf(reviewedItem))
    private val inboxFlow = MutableStateFlow(listOf(pendingItem, reviewedItem))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { repository.observeByStatus(CaptureStatus.PENDING) } returns pendingFlow
        every { repository.observeByStatus(CaptureStatus.REVIEWED) } returns reviewedFlow
        every { repository.observeByStatus(CaptureStatus.ARCHIVED) } returns MutableStateFlow(emptyList())
        every { repository.observeInbox() } returns inboxFlow
        
        viewModel = InboxViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default state is pending and loading`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(InboxFilter.PENDING, state.filter)
        assertTrue(state.isLoading)
    }

    @Test
    fun `observes pending items by default`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf(pendingItem), state.captures)
        job.cancel()
    }

    @Test
    fun `filter change updates observed items`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(InboxFilter.REVIEWED, state.filter)
        assertEquals(listOf(reviewedItem), state.captures)
        job.cancel()
    }

    @Test
    fun `all filter shows all non-deleted items`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onFilterSelected(InboxFilter.ALL)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.captures.size)
        job.cancel()
    }

    @Test
    fun `search query updates immediately but execution is debounced`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        every { repository.search("abc") } returns MutableStateFlow(listOf(pendingItem))
        
        viewModel.onSearchQueryChanged("a")
        runCurrent()
        assertEquals("a", viewModel.uiState.value.searchQuery)
        
        viewModel.onSearchQueryChanged("ab")
        runCurrent()
        assertEquals("ab", viewModel.uiState.value.searchQuery)
        
        viewModel.onSearchQueryChanged("abc")
        runCurrent()
        assertEquals("abc", viewModel.uiState.value.searchQuery)
        
        // Before debounce time - search should NOT have been called
        coVerify(exactly = 0) { repository.search(any()) }
        
        // After debounce time
        advanceTimeBy(250)
        runCurrent()
        
        coVerify(exactly = 1) { repository.search("abc") }
        assertEquals(listOf(pendingItem), viewModel.uiState.value.captures)
        job.cancel()
    }

    @Test
    fun `clearing search restores the active filter`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        viewModel.onSearchQueryChanged("test")
        advanceUntilIdle()
        
        viewModel.onSearchQueryChanged("")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(InboxFilter.REVIEWED, state.filter)
        assertEquals(listOf(reviewedItem), state.captures)
        job.cancel()
    }

    @Test
    fun `markReviewed success sets message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.markReviewed("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionSucceeded(InboxAction.MARK_REVIEWED), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `archive success sets message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.archive("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionSucceeded(InboxAction.ARCHIVE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `restore success sets message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.restoreToPending("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionSucceeded(InboxAction.RESTORE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `delete success sets message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onDeleteRequested(pendingItem)
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionSucceeded(InboxAction.DELETE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `action failure sets error message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        coEvery { repository.markReviewed(any()) } throws Exception("Failed")
        viewModel.markReviewed("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionFailed(InboxAction.MARK_REVIEWED), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `archive failure sets error message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        coEvery { repository.archive(any()) } throws Exception("Failed")
        viewModel.archive("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionFailed(InboxAction.ARCHIVE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `restore failure sets error message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        coEvery { repository.restoreToPending(any()) } throws Exception("Failed")
        viewModel.restoreToPending("1")
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionFailed(InboxAction.RESTORE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `delete failure sets error message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        coEvery { repository.softDelete(any()) } throws Exception("Failed")
        viewModel.onDeleteRequested(pendingItem)
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()
        assertEquals(InboxMessage.ActionFailed(InboxAction.DELETE), viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `link copied sets message`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onLinkCopied()
        advanceUntilIdle()
        assertEquals(InboxMessage.LinkCopied, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `message is cleared after onMessageShown`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onLinkCopied()
        advanceUntilIdle()
        assertEquals(InboxMessage.LinkCopied, viewModel.uiState.value.message)

        viewModel.onMessageShown()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `filter change does not wait for search debounce`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        advanceUntilIdle()
        assertEquals(InboxFilter.REVIEWED, viewModel.uiState.value.filter)
        assertEquals(listOf(reviewedItem), viewModel.uiState.value.captures)
        job.cancel()
    }

    @Test
    fun `search query unchanged when filters change`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onSearchQueryChanged("test")
        runCurrent()
        assertEquals("test", viewModel.uiState.value.searchQuery)

        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        advanceUntilIdle()
        assertEquals("test", viewModel.uiState.value.searchQuery)
        job.cancel()
    }

    @Test
    fun `action in progress prevents duplicate action`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.markReviewed("1")
        viewModel.markReviewed("1")
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.markReviewed("1") }
        job.cancel()
    }

    @Test
    fun `soft delete requires confirmation`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onDeleteRequested(pendingItem)
        advanceUntilIdle()
        assertEquals(pendingItem, viewModel.uiState.value.pendingDelete)
        
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()
        
        coVerify { repository.softDelete("1") }
        assertEquals(null, viewModel.uiState.value.pendingDelete)
        job.cancel()
    }

    @Test
    fun `cancelled delete does not call repository`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.onDeleteRequested(pendingItem)
        viewModel.onDeleteCancelled()
        
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.softDelete(any()) }
        assertEquals(null, viewModel.uiState.value.pendingDelete)
        job.cancel()
    }

    // ---- Phase 7: default inbox filter ----------------

    @Test
    fun `pending fallback when no preference exists`() = runTest {
        val vm = InboxViewModel(repository, settingsRepository = FakeSettingsRepository())
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(InboxFilter.PENDING, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `stored reviewed default applies on fresh session`() = runTest {
        val fake = FakeSettingsRepository(
            SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.REVIEWED)
        )
        val vm = InboxViewModel(repository, settingsRepository = fake)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(InboxFilter.REVIEWED, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `stored archived default applies on fresh session`() = runTest {
        val fake = FakeSettingsRepository(
            SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.ARCHIVED)
        )
        val vm = InboxViewModel(repository, settingsRepository = fake)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(InboxFilter.ARCHIVED, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `stored all default applies on fresh session`() = runTest {
        val fake = FakeSettingsRepository(
            SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.ALL)
        )
        val vm = InboxViewModel(repository, settingsRepository = fake)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(InboxFilter.ALL, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `explicit current session choice is not overwritten by later emission`() = runTest {
        val fake = FakeSettingsRepository()
        val vm = InboxViewModel(repository, settingsRepository = fake)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        // User chooses before the async default read lands; a later DataStore
        // emission must not overwrite the explicit choice.
        vm.onFilterSelected(InboxFilter.ALL)
        fake.emit(SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.REVIEWED))
        advanceUntilIdle()
        assertEquals(InboxFilter.ALL, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `malformed preference read falls back to pending`() = runTest {
        val failingRepo = mockk<SettingsRepository>()
        coEvery { failingRepo.settings } returns flow { throw IOException("boom") }
        val vm = InboxViewModel(repository, settingsRepository = failingRepo)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(InboxFilter.PENDING, vm.uiState.value.filter)
        job.cancel()
    }

    @Test
    fun `changing setting does not disrupt an active inbox session`() = runTest {
        val fake = FakeSettingsRepository()
        val vm = InboxViewModel(repository, settingsRepository = fake)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        vm.onFilterSelected(InboxFilter.REVIEWED)
        fake.emit(SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.ALL))
        advanceUntilIdle()
        assertEquals(InboxFilter.REVIEWED, vm.uiState.value.filter)
        job.cancel()
    }
}
