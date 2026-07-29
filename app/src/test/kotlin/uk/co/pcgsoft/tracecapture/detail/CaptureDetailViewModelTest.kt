package uk.co.pcgsoft.tracecapture.detail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureDetailViewModelTest {

    private lateinit var viewModel: CaptureDetailViewModel
    private val repository: CaptureRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val captureFlow = MutableStateFlow<CaptureItem?>(null)

    private val sampleCapture = CaptureItem(
        id = "detail-test-id",
        createdAtEpochMillis = 1722000000000L,
        updatedAtEpochMillis = 1722000000000L,
        originalContent = "Sample original content for testing",
        primaryUrl = "https://example.com/test",
        detectedUrls = listOf("https://example.com/test", "https://example.com/related"),
        sourcePackageName = "com.example",
        sourceLabel = "TestApp",
        note = "Existing note",
        captureType = CaptureType.URL_WITH_TEXT,
        status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeById("detail-test-id") } returns captureFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid ID loads capture`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isMissing)
        assertEquals(sampleCapture, state.capture)
        job.cancel()
    }

    @Test
    fun `missing ID sets missing state`() = runTest {
        captureFlow.value = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("nonexistent"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isMissing)
        assertNull(state.capture)
        job.cancel()
    }

    @Test
    fun `missing capture sets missing state`() = runTest {
        captureFlow.value = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMissing)
        job.cancel()
    }

    @Test
    fun `initial note draft comes from stored note`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("Existing note", viewModel.uiState.value.noteDraft)
        job.cancel()
    }

    @Test
    fun `null note becomes empty draft`() = runTest {
        captureFlow.value = sampleCapture.copy(note = null)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.noteDraft)
        job.cancel()
    }

    @Test
    fun `note editing sets dirty state`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.noteChanged)

        viewModel.onNoteChanged("Changed note")
        assertTrue(viewModel.uiState.value.noteChanged)
        assertEquals("Changed note", viewModel.uiState.value.noteDraft)
        job.cancel()
    }

    @Test
    fun `unchanged note keeps Save disabled`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.noteChanged)
        job.cancel()
    }

    @Test
    fun `over-limit note is rejected`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("A")
        assertEquals("A", viewModel.uiState.value.noteDraft)

        val longText = "A".repeat(3000)
        viewModel.onNoteChanged(longText)
        assertEquals("A", viewModel.uiState.value.noteDraft)
        job.cancel()
    }

    @Test
    fun `successful note save calls repository once`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.updateNote(any(), any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("Updated note")
        viewModel.onSaveNote()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateNote("detail-test-id", "Updated note") }
        job.cancel()
    }

    @Test
    fun `blank note saves as null`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.updateNote(any(), any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("   ")
        viewModel.onSaveNote()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateNote("detail-test-id", null) }
        job.cancel()
    }

    @Test
    fun `note save failure preserves draft`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.updateNote(any(), any()) } throws Exception("DB error")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("Draft to preserve")
        viewModel.onSaveNote()
        advanceUntilIdle()

        assertEquals("Draft to preserve", viewModel.uiState.value.noteDraft)
        assertTrue(viewModel.uiState.value.message is CaptureDetailMessage.ActionFailed)
        job.cancel()
    }

    @Test
    fun `mark reviewed calls repository`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.markReviewed(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onMarkReviewed()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markReviewed("detail-test-id") }
        job.cancel()
    }

    @Test
    fun `archive calls repository`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.archive(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onArchive()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.archive("detail-test-id") }
        job.cancel()
    }

    @Test
    fun `restore calls repository`() = runTest {
        captureFlow.value = sampleCapture.copy(status = CaptureStatus.ARCHIVED)
        coEvery { repository.restoreToPending(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onRestoreToPending()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.restoreToPending("detail-test-id") }
        job.cancel()
    }

    @Test
    fun `repeated action while active is ignored`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.markReviewed(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            Unit
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onMarkReviewed()
        viewModel.onMarkReviewed()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markReviewed("detail-test-id") }
        job.cancel()
    }

    @Test
    fun `action failure exposes typed failure`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.markReviewed(any()) } throws Exception("Failed")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onMarkReviewed()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.message is CaptureDetailMessage.ActionFailed)
        assertEquals(
            DetailAction.MARK_REVIEWED,
            (viewModel.uiState.value.message as CaptureDetailMessage.ActionFailed).action
        )
        job.cancel()
    }

    @Test
    fun `delete requires confirmation`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)
        job.cancel()
    }

    @Test
    fun `cancelled deletion does not delete`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.softDelete(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onDeleteRequested()
        viewModel.onDeleteCancelled()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.softDelete(any()) }
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
        job.cancel()
    }

    @Test
    fun `confirmed deletion calls repository`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.softDelete(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.softDelete("detail-test-id") }
        job.cancel()
    }

    @Test
    fun `deletion success emits navigation event`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.softDelete(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingNavigation)
        job.cancel()
    }

    @Test
    fun `unsaved changes back request shows confirmation`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showUnsavedChangesDialog)

        viewModel.onNoteChanged("Changed")
        val canGoBack = viewModel.onBackRequested()
        assertFalse(canGoBack)
        assertTrue(viewModel.uiState.value.showUnsavedChangesDialog)
        job.cancel()
    }

    @Test
    fun `discard confirms navigation`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("Changed")
        viewModel.onBackRequested()
        assertTrue(viewModel.uiState.value.showUnsavedChangesDialog)

        viewModel.onDiscardChanges()
        assertFalse(viewModel.uiState.value.showUnsavedChangesDialog)
        assertTrue(viewModel.uiState.value.pendingNavigation)
        job.cancel()
    }

    @Test
    fun `keep editing cancels navigation`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("Changed")
        viewModel.onBackRequested()
        assertTrue(viewModel.uiState.value.showUnsavedChangesDialog)

        viewModel.onKeepEditing()
        assertFalse(viewModel.uiState.value.showUnsavedChangesDialog)
        assertFalse(viewModel.uiState.value.pendingNavigation)
        job.cancel()
    }

    @Test
    fun `no unsaved dialog when note unchanged`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val canGoBack = viewModel.onBackRequested()
        assertTrue(canGoBack)
        assertFalse(viewModel.uiState.value.showUnsavedChangesDialog)
        job.cancel()
    }

    @Test
    fun `action failure for archive exposes typed failure`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.archive(any()) } throws Exception("Failed")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onArchive()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.message is CaptureDetailMessage.ActionFailed)
        assertEquals(
            DetailAction.ARCHIVE,
            (viewModel.uiState.value.message as CaptureDetailMessage.ActionFailed).action
        )
        job.cancel()
    }

    @Test
    fun `action failure for restore exposes typed failure`() = runTest {
        captureFlow.value = sampleCapture.copy(status = CaptureStatus.ARCHIVED)
        coEvery { repository.restoreToPending(any()) } throws Exception("Failed")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onRestoreToPending()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.message is CaptureDetailMessage.ActionFailed)
        assertEquals(
            DetailAction.RESTORE_PENDING,
            (viewModel.uiState.value.message as CaptureDetailMessage.ActionFailed).action
        )
        job.cancel()
    }

    @Test
    fun `action failure for delete exposes typed failure`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.softDelete(any()) } throws Exception("Failed")
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.message is CaptureDetailMessage.ActionFailed)
        assertEquals(
            DetailAction.DELETE,
            (viewModel.uiState.value.message as CaptureDetailMessage.ActionFailed).action
        )
        assertFalse(viewModel.uiState.value.pendingNavigation)
        job.cancel()
    }

    @Test
    fun `successful note save emits NoteSaved message`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.updateNote(any(), any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onNoteChanged("Updated note")
        viewModel.onSaveNote()
        advanceUntilIdle()

        assertEquals(CaptureDetailMessage.NoteSaved, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `mark reviewed success emits MarkedReviewed message`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.markReviewed(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onMarkReviewed()
        advanceUntilIdle()

        assertEquals(CaptureDetailMessage.MarkedReviewed, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `archive success emits Archived message`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.archive(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onArchive()
        advanceUntilIdle()

        assertEquals(CaptureDetailMessage.Archived, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `restore success emits Restored message`() = runTest {
        captureFlow.value = sampleCapture.copy(status = CaptureStatus.ARCHIVED)
        coEvery { repository.restoreToPending(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onRestoreToPending()
        advanceUntilIdle()

        assertEquals(CaptureDetailMessage.Restored, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `deletion success emits CaptureRemoved message`() = runTest {
        captureFlow.value = sampleCapture
        coEvery { repository.softDelete(any()) } returns Unit
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(CaptureDetailMessage.CaptureRemoved, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `link copied sets message`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onLinkCopied()
        assertEquals(CaptureDetailMessage.LinkCopied, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `content copied sets message`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onContentCopied()
        assertEquals(CaptureDetailMessage.ContentCopied, viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun `message is cleared after onMessageShown`() = runTest {
        captureFlow.value = sampleCapture
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel = CaptureDetailViewModel(
                savedStateHandle = createHandle("detail-test-id"),
                repository = repository
            )
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onLinkCopied()
        assertEquals(CaptureDetailMessage.LinkCopied, viewModel.uiState.value.message)

        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
        job.cancel()
    }

    private fun createHandle(captureId: String) =
        androidx.lifecycle.SavedStateHandle(mapOf("captureId" to captureId))
}
