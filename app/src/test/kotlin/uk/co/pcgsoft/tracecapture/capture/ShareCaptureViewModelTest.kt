package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemFactory
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

@OptIn(ExperimentalCoroutinesApi::class)
class ShareCaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val processor = mockk<SharedCaptureProcessor>()
    private val repository = mockk<CaptureRepository>()
    private val factory = CaptureItemFactory()
    private lateinit var viewModel: ShareCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ShareCaptureViewModel(processor, repository, factory)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun draftA() = CaptureDraft(
        originalContent = "https://a.com",
        primaryUrl = "https://a.com",
        detectedUrls = listOf("https://a.com"),
        sourcePackageName = null,
        sourceLabel = null,
        captureType = CaptureType.URL
    )

    private fun draftB() = CaptureDraft(
        originalContent = "https://b.com",
        primaryUrl = "https://b.com",
        detectedUrls = listOf("https://b.com"),
        sourcePackageName = null,
        sourceLabel = null,
        captureType = CaptureType.URL
    )

    private fun dupItem(id: String, url: String, createdAt: Long) = CaptureItem(
        id = id, createdAtEpochMillis = createdAt, updatedAtEpochMillis = createdAt,
        originalContent = "old", primaryUrl = url, detectedUrls = emptyList(),
        sourcePackageName = null, sourceLabel = null, note = null,
        captureType = CaptureType.URL, status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY, duplicateOfId = null,
        archivedAtEpochMillis = null, deletedAtEpochMillis = null
    )

    @Test
    fun `processIntent with valid URL sets Ready state`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns emptyList()

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Ready)
        val ready = state as ShareCaptureUiState.Ready
        assertEquals("https://a.com", ready.draft.primaryUrl)
        assertEquals("", ready.note)
        assertNull(ready.duplicate)
    }

    @Test
    fun `processIntent with parser rejection sets Invalid state`() {
        every { processor.process(any()) } returns SharedCaptureResult.Rejected(ShareRejectionReason.UNSUPPORTED_MIME_TYPE)

        viewModel.processIntent(mockk())

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Invalid)
        assertEquals(ShareRejectionReason.UNSUPPORTED_MIME_TYPE, (state as ShareCaptureUiState.Invalid).reason)
    }

    @Test
    fun `processIntent with null intent sets Invalid state`() {
        viewModel.processIntent(null)

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Invalid)
    }

    @Test
    fun `processIntent ignores new intent while saving`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } coAnswers { kotlinx.coroutines.delay(10_000) }

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        // Save is in progress, isSaving = true

        viewModel.processIntent(mockk())

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Ready)
        val ready = state as ShareCaptureUiState.Ready
        assertEquals("https://a.com", ready.draft.primaryUrl)
        assertTrue(ready.isSaving)
    }

    @Test
    fun `updateNote changes note in Ready state`() {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()

        viewModel.processIntent(mockk())
        viewModel.updateNote("my note")

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertEquals("my note", state.note)
    }

    @Test
    fun `updateNote enforces 2000 char limit`() {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()

        viewModel.processIntent(mockk())
        viewModel.updateNote("valid")
        viewModel.updateNote("a".repeat(2500))

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertEquals("valid", state.note)
    }

    @Test
    fun `save creates item via factory and saves via repository`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        coVerify { repository.save(any()) }
        assertTrue(viewModel.uiState.value is ShareCaptureUiState.Saved)
    }

    @Test
    fun `save snapshots exact draft when new intent arrives`() = runTest {
        val intentA = mockk<Intent>()
        val intentB = mockk<Intent>()

        every { processor.process(intentA) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns emptyList()
        coEvery { repository.save(any()) } coAnswers { kotlinx.coroutines.delay(10_000) }

        viewModel.processIntent(intentA)
        advanceUntilIdle()
        viewModel.save()

        every { processor.process(intentB) } returns SharedCaptureResult.Ready(draftB())
        viewModel.processIntent(intentB)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.save(match { it.originalContent == "https://a.com" })
        }
    }

    @Test
    fun `save with note includes note in saved item`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("content with https://a.com", "https://a.com", listOf("https://a.com"), null, null, CaptureType.URL_WITH_TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("my note")
        viewModel.save()
        advanceUntilIdle()

        coVerify { repository.save(match { it.note == "my note" }) }
    }

    @Test
    fun `save with blank note stores null`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("   ")
        viewModel.save()
        advanceUntilIdle()

        coVerify { repository.save(match { it.note == null }) }
    }

    @Test
    fun `repeated Save taps produce one repository call`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `save failure sets Failed state retaining draft note and duplicate`() = runTest {
        val dup = DuplicateCaptureWarning("dup-1", 500L, 2)
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("https://x.com", "https://x.com", listOf("https://x.com"), null, null, CaptureType.URL)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns listOf(
            dupItem("dup-1", "https://x.com", 500L)
        )
        coEvery { repository.save(any()) } throws RuntimeException("DB error")

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        viewModel.updateNote("important note")
        val readyBeforeSave = viewModel.uiState.value as ShareCaptureUiState.Ready
        val dupBeforeSave = readyBeforeSave.duplicate

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Failed
        assertEquals("important note", state.note)
        assertEquals(dupBeforeSave?.existingCaptureId, state.duplicate?.existingCaptureId)
        assertEquals("https://x.com", state.draft.primaryUrl)
    }

    @Test
    fun `retry restores Ready with retained note and duplicate`() = runTest {
        val dup = DuplicateCaptureWarning("dup-1", 500L, 2)
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("https://x.com", "https://x.com", listOf("https://x.com"), null, null, CaptureType.URL)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns listOf(
            dupItem("dup-1", "https://x.com", 500L)
        )
        coEvery { repository.save(any()) } throws RuntimeException("DB error")

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("my note")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ShareCaptureUiState.Failed)

        coEvery { repository.save(any()) } returns Unit

        viewModel.retry()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertEquals("my note", state.note)
        assertNotNull(state.duplicate)
        assertEquals("dup-1", state.duplicate?.existingCaptureId)
        assertEquals("https://x.com", state.draft.primaryUrl)
    }

    @Test
    fun `successful retry saves the retained note`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("https://x.com", "https://x.com", listOf("https://x.com"), null, null, CaptureType.URL)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()

        var shouldFail = true
        coEvery { repository.save(any()) } coAnswers {
            if (shouldFail) throw RuntimeException("DB error")
        }

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("retry note")
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ShareCaptureUiState.Failed)

        shouldFail = false
        viewModel.retry()
        viewModel.save()
        advanceUntilIdle()

        coVerify {
            repository.save(match { it.note == "retry note" })
        }
        assertTrue(viewModel.uiState.value is ShareCaptureUiState.Saved)
    }

    @Test
    fun `original content unchanged through failure and retry`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("original text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } throws RuntimeException("DB error")

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        coEvery { repository.save(any()) } returns Unit
        viewModel.retry()
        viewModel.save()
        advanceUntilIdle()

        coVerify {
            repository.save(match { it.originalContent == "original text" })
        }
    }

    @Test
    fun `duplicate found shows warning`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns listOf(
            dupItem("dup-1", "https://a.com", 1000L)
        )

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertNotNull(state.duplicate)
        assertEquals("dup-1", state.duplicate?.existingCaptureId)
        assertEquals(1000L, state.duplicate?.capturedAtEpochMillis)
    }

    @Test
    fun `no duplicate found does not show warning`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns emptyList()

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertNull(state.duplicate)
    }

    @Test
    fun `text-only capture skips duplicate check`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft("just text", null, emptyList(), null, null, CaptureType.TEXT)
        )

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.findExactUrlDuplicates(any(), any()) }
    }

    @Test
    fun `stale duplicate result does not apply to new draft`() = runTest {
        val intentA = mockk<Intent>()
        val intentB = mockk<Intent>()

        every { processor.process(intentA) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns listOf(
            dupItem("dup-a", "https://a.com", 1000L)
        )

        viewModel.processIntent(intentA)

        every { processor.process(intentB) } returns SharedCaptureResult.Ready(draftB())
        coEvery { repository.findExactUrlDuplicates("https://b.com", null) } returns emptyList()

        viewModel.processIntent(intentB)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertEquals("https://b.com", state.draft.primaryUrl)
        assertNull(state.duplicate)
    }

    @Test
    fun `multiple duplicates selects newest date`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(draftA())
        coEvery { repository.findExactUrlDuplicates("https://a.com", null) } returns listOf(
            dupItem("old", "https://a.com", 100L),
            dupItem("new", "https://a.com", 500L),
            dupItem("mid", "https://a.com", 300L)
        )

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertNotNull(state.duplicate)
        assertEquals("new", state.duplicate?.existingCaptureId)
        assertEquals(500L, state.duplicate?.capturedAtEpochMillis)
        assertEquals(3, state.duplicate?.existingCount)
    }

    @Test
    fun `saved item has correct domain properties`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            CaptureDraft(
                originalContent = "https://example.com",
                primaryUrl = "https://example.com",
                detectedUrls = listOf("https://example.com"),
                sourcePackageName = "com.example.app",
                sourceLabel = "Example",
                captureType = CaptureType.URL
            )
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        coVerify {
            repository.save(match {
                it.status == CaptureStatus.PENDING &&
                it.syncStatus == SyncStatus.LOCAL_ONLY &&
                it.captureType == CaptureType.URL &&
                it.sourcePackageName == "com.example.app"
            })
        }
    }
}
