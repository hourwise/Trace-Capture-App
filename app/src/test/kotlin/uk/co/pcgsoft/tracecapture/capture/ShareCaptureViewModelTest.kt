package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import uk.co.pcgsoft.tracecapture.data.local.CaptureValidator
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureType

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

    @Test
    fun `processIntent with valid URL sets Ready state`() = runTest {
        val intent = mockk<Intent>()
        every { processor.process(intent) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft(
                originalContent = "https://example.com",
                primaryUrl = "https://example.com",
                detectedUrls = listOf("https://example.com"),
                sourcePackageName = "com.example",
                sourceLabel = "Example",
                captureType = CaptureType.URL
            )
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()

        viewModel.processIntent(intent)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Ready)
        val ready = state as ShareCaptureUiState.Ready
        assertEquals("https://example.com", ready.draft.primaryUrl)
        assertEquals("", ready.note)
        assertNull(ready.duplicate)
    }

    @Test
    fun `processIntent with parser rejection sets Invalid state`() {
        val intent = mockk<Intent>()
        every { processor.process(intent) } returns SharedCaptureResult.Rejected(ShareRejectionReason.UNSUPPORTED_MIME_TYPE)

        viewModel.processIntent(intent)

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
    fun `updateNote changes note in Ready state`() {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
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
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()

        viewModel.processIntent(mockk())
        viewModel.updateNote("valid")
        val longNote = "a".repeat(2500)
        viewModel.updateNote(longNote)

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertEquals("valid", state.note)
    }

    @Test
    fun `save creates item via factory and saves via repository`() = runTest {
        val intent = mockk<Intent>()
        every { processor.process(intent) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft(
                originalContent = "https://example.com",
                primaryUrl = "https://example.com",
                detectedUrls = listOf("https://example.com"),
                sourcePackageName = null,
                sourceLabel = null,
                captureType = CaptureType.URL
            )
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(intent)
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        coVerify { repository.save(any()) }
        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Saved)
    }

    @Test
    fun `save with note includes note in saved item`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("content with https://a.com", "https://a.com", listOf("https://a.com"), null, null, CaptureType.URL_WITH_TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("my note")
        viewModel.save()
        advanceUntilIdle()

        coVerify {
            repository.save(match {
                it.note == "my note" && it.originalContent == "content with https://a.com"
            })
        }
    }

    @Test
    fun `save with blank note stores null`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } returns Unit

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.updateNote("   ")
        viewModel.save()
        advanceUntilIdle()

        coVerify {
            repository.save(match { it.note == null })
        }
    }

    @Test
    fun `repeated Save taps produce one repository call`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
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
    fun `save failure sets Failed state`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } throws RuntimeException("DB error")

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Failed)
        assertNotNull((state as ShareCaptureUiState.Failed).draft)
    }

    @Test
    fun `retry after failure returns to Ready state`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("text", null, emptyList(), null, null, CaptureType.TEXT)
        )
        coEvery { repository.findExactUrlDuplicates(any(), any()) } returns emptyList()
        coEvery { repository.save(any()) } throws RuntimeException("DB error")

        viewModel.processIntent(mockk())
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ShareCaptureUiState.Failed)

        coEvery { repository.save(any()) } returns Unit

        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ShareCaptureUiState.Ready)
    }

    @Test
    fun `duplicate found shows warning`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft(
                originalContent = "https://example.com",
                primaryUrl = "https://example.com",
                detectedUrls = listOf("https://example.com"),
                sourcePackageName = null,
                sourceLabel = null,
                captureType = CaptureType.URL
            )
        )
        coEvery { repository.findExactUrlDuplicates("https://example.com", null) } returns listOf(
            CaptureItem(
                id = "dup-1",
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                originalContent = "old",
                primaryUrl = "https://example.com",
                detectedUrls = emptyList(),
                sourcePackageName = null,
                sourceLabel = null,
                note = null,
                captureType = CaptureType.URL,
                status = uk.co.pcgsoft.tracecapture.domain.CaptureStatus.PENDING,
                syncStatus = uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY,
                duplicateOfId = null,
                archivedAtEpochMillis = null,
                deletedAtEpochMillis = null
            )
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
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("https://example.com", "https://example.com", listOf("https://example.com"), null, null, CaptureType.URL)
        )
        coEvery { repository.findExactUrlDuplicates("https://example.com", null) } returns emptyList()

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        val state = viewModel.uiState.value as ShareCaptureUiState.Ready
        assertNull(state.duplicate)
    }

    @Test
    fun `text-only capture skips duplicate check`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("just text", null, emptyList(), null, null, CaptureType.TEXT)
        )

        viewModel.processIntent(mockk())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.findExactUrlDuplicates(any(), any()) }
    }

    @Test
    fun `multiple duplicates selects newest date`() = runTest {
        every { processor.process(any()) } returns SharedCaptureResult.Ready(
            draft = CaptureDraft("https://example.com", "https://example.com", listOf("https://example.com"), null, null, CaptureType.URL)
        )
        coEvery { repository.findExactUrlDuplicates("https://example.com", null) } returns listOf(
            CaptureItem("old", 100L, 100L, "old", "https://example.com", emptyList(), null, null, null, CaptureType.URL, uk.co.pcgsoft.tracecapture.domain.CaptureStatus.PENDING, uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY, null, null, null),
            CaptureItem("new", 500L, 500L, "new", "https://example.com", emptyList(), null, null, null, CaptureType.URL, uk.co.pcgsoft.tracecapture.domain.CaptureStatus.PENDING, uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY, null, null, null),
            CaptureItem("mid", 300L, 300L, "mid", "https://example.com", emptyList(), null, null, null, CaptureType.URL, uk.co.pcgsoft.tracecapture.domain.CaptureStatus.PENDING, uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY, null, null, null)
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
            draft = CaptureDraft(
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
                it.status == uk.co.pcgsoft.tracecapture.domain.CaptureStatus.PENDING &&
                it.syncStatus == uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY &&
                it.captureType == CaptureType.URL &&
                it.sourcePackageName == "com.example.app"
            })
        }
    }
}
