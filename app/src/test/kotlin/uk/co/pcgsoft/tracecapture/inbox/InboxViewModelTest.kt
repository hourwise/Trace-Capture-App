package uk.co.pcgsoft.tracecapture.inbox

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus

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
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf(pendingItem), state.captures)
    }

    @Test
    fun `filter change updates observed items`() = runTest {
        viewModel.onFilterSelected(InboxFilter.REVIEWED)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(InboxFilter.REVIEWED, state.filter)
        assertEquals(listOf(reviewedItem), state.captures)
    }

    @Test
    fun `all filter shows all non-deleted items`() = runTest {
        viewModel.onFilterSelected(InboxFilter.ALL)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.captures.size)
    }

    @Test
    fun `search filters results within active filter`() = runTest {
        every { repository.search("Chrome") } returns MutableStateFlow(listOf(pendingItem))
        
        viewModel.onSearchQueryChanged("Chrome")
        advanceUntilIdle() // Wait for debounce
        
        val state = viewModel.uiState.value
        assertEquals("Chrome", state.searchQuery)
        assertEquals(listOf(pendingItem), state.captures)
    }

    @Test
    fun `markReviewed calls repository`() = runTest {
        viewModel.markReviewed("1")
        advanceUntilIdle()
        coVerify { repository.markReviewed("1") }
    }

    @Test
    fun `archive calls repository`() = runTest {
        viewModel.archive("1")
        advanceUntilIdle()
        coVerify { repository.archive("1") }
    }

    @Test
    fun `restoreToPending calls repository`() = runTest {
        viewModel.restoreToPending("2")
        advanceUntilIdle()
        coVerify { repository.restoreToPending("2") }
    }

    @Test
    fun `soft delete requires confirmation`() = runTest {
        viewModel.onDeleteRequested(pendingItem)
        // Ensure state update is processed
        advanceUntilIdle()
        assertEquals(pendingItem, viewModel.uiState.value.pendingDelete)
        
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()
        
        coVerify { repository.softDelete("1") }
        assertEquals(null, viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun `cancelled delete does not call repository`() = runTest {
        viewModel.onDeleteRequested(pendingItem)
        viewModel.onDeleteCancelled()
        
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.softDelete(any()) }
        assertEquals(null, viewModel.uiState.value.pendingDelete)
    }
}
