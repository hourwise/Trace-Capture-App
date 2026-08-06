package uk.co.pcgsoft.tracecapture.inbox

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import uk.co.pcgsoft.tracecapture.export.ExportCoordinator
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportMessage
import uk.co.pcgsoft.tracecapture.export.ExportResult
import uk.co.pcgsoft.tracecapture.export.ExportSource
import uk.co.pcgsoft.tracecapture.export.file.ExportFileWriter
import uk.co.pcgsoft.tracecapture.export.file.FileWriteResult
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import uk.co.pcgsoft.tracecapture.export.share.PreparedShareExport

@OptIn(ExperimentalCoroutinesApi::class)
class InboxExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CaptureRepository>()
    private val coordinator = mockk<ExportCoordinator>()
    private val writer = mockk<ExportFileWriter>()
    private val shareManager = mockk<ExportShareFileManager>()
    private lateinit var viewModel: InboxExportViewModel

    private val older = item("older", 1_000L)
    private val newer = item("newer", 2_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = InboxExportViewModel(repository, coordinator, writer, shareManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectedCapturesAreResolvedAndPassedInVisibleOrder() = runTest {
        coEvery { repository.getActiveByIds(setOf("older", "newer")) } returns listOf(newer, older)
        coEvery {
            coordinator.prepareExport(
                captures = listOf(older, newer),
                format = ExportFormat.JSON,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SUPPLIED_CAPTURE_LIST
            )
        } returns ExportResult.Success(
            format = ExportFormat.JSON,
            content = "{}".toByteArray(),
            mimeType = ExportFormat.JSON.mimeType,
            suggestedFileName = "export.json",
            captureCount = 2
        )

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(
            selectedIds = setOf("older", "newer"),
            visibleOrderIds = listOf("older", "newer")
        )
        advanceUntilIdle()

        // The exact invocation above verifies both deterministic ordering and source metadata.
        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(older, newer),
                format = ExportFormat.JSON,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
        assertEquals("export.json", viewModel.exportState.value.pendingDocument?.suggestedFileName)
    }

    @Test
    fun emptySelectionIsBlocked() = runTest {
        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(emptySet(), emptyList())

        assertEquals(uk.co.pcgsoft.tracecapture.export.ExportMessage.EmptyExport, viewModel.exportState.value.message)
    }

    @Test
    fun unavailableIdsAreExposedAndExportUsesRemainingCaptures() = runTest {
        coEvery { repository.getActiveByIds(setOf("older", "gone")) } returns listOf(older)
        coEvery {
            coordinator.prepareExport(captures = any(), format = any(), source = any())
        } returns ExportResult.Success(
            format = ExportFormat.PLAIN_TEXT,
            content = "text".toByteArray(),
            mimeType = ExportFormat.PLAIN_TEXT.mimeType,
            suggestedFileName = "export.txt",
            captureCount = 1
        )

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.PLAIN_TEXT)
        viewModel.onSaveFileRequested(setOf("older", "gone"), listOf("older", "gone"))
        advanceUntilIdle()

        assertEquals(setOf("gone"), viewModel.exportState.value.unavailableIds)
        assertTrue(viewModel.exportState.value.pendingDocument != null)
    }

    @Test
    fun documentCancellationLeavesPendingSelectionWorkflowReusable() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery {
            coordinator.prepareExport(captures = any(), format = any(), source = any())
        } returns ExportResult.Success(
            format = ExportFormat.JSON,
            content = byteArrayOf(1),
            mimeType = ExportFormat.JSON.mimeType,
            suggestedFileName = "export.json",
            captureCount = 1
        )

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()
        viewModel.onDocumentLaunchStarted()
        viewModel.onDocumentUriReceived(null)

        assertEquals(null, viewModel.exportState.value.pendingDocument)
        assertEquals(false, viewModel.exportState.value.isPreparing)
    }

    @Test
    fun selectAllExportsInVisibleOrder() = runTest {
        val middle = item("middle", 1_500L)
        coEvery { repository.getActiveByIds(setOf("older", "middle", "newer")) } returns
            listOf(newer, middle, older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(
            selectedIds = setOf("older", "middle", "newer"),
            visibleOrderIds = listOf("newer", "middle", "older")
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(newer, middle, older),
                format = ExportFormat.JSON,
                source = ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
    }

    @Test
    fun repositoryReturnOrderDoesNotChangeExportOrder() = runTest {
        val middle = item("middle", 1_500L)
        // Repository returns oldest-first; visible order must stay authoritative.
        coEvery { repository.getActiveByIds(setOf("older", "middle", "newer")) } returns
            listOf(older, middle, newer)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(
            selectedIds = setOf("older", "middle", "newer"),
            visibleOrderIds = listOf("newer", "middle", "older")
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(newer, middle, older),
                format = ExportFormat.JSON,
                source = ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
    }

    @Test
    fun missingIdsDoNotShiftRemainingOrdering() = runTest {
        val middle = item("middle", 1_500L)
        // "newer" is unavailable, but "middle" and "older" keep their visible positions.
        coEvery { repository.getActiveByIds(setOf("older", "middle", "newer")) } returns
            listOf(middle, older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(
            selectedIds = setOf("older", "middle", "newer"),
            visibleOrderIds = listOf("newer", "middle", "older")
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(middle, older),
                format = ExportFormat.JSON,
                source = ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
        assertEquals(setOf("newer"), viewModel.exportState.value.unavailableIds)
    }

    @Test
    fun equalTimestampsUseDeterministicIdFallbackForIdsAbsentFromVisibleOrder() = runTest {
        val idA = item("id-a", 1_000L)
        val idB = item("id-b", 1_000L)
        val idC = item("id-c", 2_000L)
        // id-a and id-b share a timestamp and neither appears in the visible order.
        coEvery { repository.getActiveByIds(setOf("id-a", "id-b", "id-c")) } returns
            listOf(idA, idC, idB)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(
            selectedIds = setOf("id-a", "id-b", "id-c"),
            visibleOrderIds = listOf("id-c")
        )
        advanceUntilIdle()

        // id-c is visible (first); absent ids fall back to newest-first then id DESC.
        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(idC, idB, idA),
                format = ExportFormat.JSON,
                source = ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
    }

    @Test
    fun documentLaunchIsConsumedOnceAndNeverRelaunches() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()
        assertTrue(viewModel.exportState.value.pendingDocument != null)
        assertFalse(viewModel.exportState.value.documentLaunchConsumed)

        // First launch marks consumed; recomposition/rotation calling again changes nothing.
        viewModel.onDocumentLaunchStarted()
        assertTrue(viewModel.exportState.value.documentLaunchConsumed)
        viewModel.onDocumentLaunchStarted()
        assertTrue(viewModel.exportState.value.documentLaunchConsumed)
        assertTrue(viewModel.exportState.value.pendingDocument != null)
    }

    @Test
    fun documentCancellationIsNotReportedAsFailure() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()
        viewModel.onDocumentLaunchStarted()
        viewModel.onDocumentUriReceived(null)

        assertEquals(null, viewModel.exportState.value.pendingDocument)
        assertEquals(null, viewModel.exportState.value.message)
        assertFalse(viewModel.exportState.value.isPreparing)
    }

    @Test
    fun successfulSaveReportsSavedMessage() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)
        coEvery { writer.write(any(), any()) } returns FileWriteResult.Success

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()
        viewModel.onDocumentLaunchStarted()
        viewModel.onDocumentUriReceived(mockk<android.net.Uri>())
        advanceUntilIdle()

        assertEquals(ExportMessage.ExportSaved, viewModel.exportState.value.message)
        assertFalse(viewModel.exportState.value.isPreparing)
        assertEquals(null, viewModel.exportState.value.pendingDocument)
    }

    @Test
    fun failedWriteKeepsStateForRetry() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)
        coEvery { writer.write(any(), any()) } returns FileWriteResult.Failure("boom")

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()
        viewModel.onDocumentLaunchStarted()
        viewModel.onDocumentUriReceived(mockk<android.net.Uri>())
        advanceUntilIdle()

        assertEquals(ExportMessage.FileWriteFailed, viewModel.exportState.value.message)
        assertFalse(viewModel.exportState.value.isPreparing)
        // Failure message never exits selection (screen only exits on success messages).
        assertEquals(ExportMessage.FileWriteFailed, viewModel.exportState.value.message)
    }

    @Test
    fun repeatedSaveTapsCannotQueueTwoDocumentRequests() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.JSON)

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.JSON)
        // Two rapid taps before preparation completes.
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        viewModel.onSaveFileRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(captures = any(), format = any(), source = any())
        }
        assertTrue(viewModel.exportState.value.pendingDocument != null)
    }

    @Test
    fun shareLaunchIsConsumedOnce() = runTest {
        prepareShare(testScheduler)
        viewModel.onShareLaunchStarted()
        assertTrue(viewModel.exportState.value.shareLaunchConsumed)
        assertTrue(viewModel.exportState.value.pendingShare != null)
        viewModel.onShareLaunchStarted()
        assertTrue(viewModel.exportState.value.shareLaunchConsumed)
        assertTrue(viewModel.exportState.value.pendingShare != null)
    }

    @Test
    fun shareCancellationClearsPendingWithoutMessage() = runTest {
        prepareShare(testScheduler)
        viewModel.onShareLaunchStarted()
        viewModel.onShareCancelled()

        assertEquals(null, viewModel.exportState.value.pendingShare)
        assertEquals(null, viewModel.exportState.value.message)
        assertFalse(viewModel.exportState.value.isPreparing)
    }

    @Test
    fun chooserCallbackReportsLaunchNotDelivery() = runTest {
        prepareShare(testScheduler)
        viewModel.onShareLaunchStarted()
        viewModel.onShareLaunched()

        assertEquals(ExportMessage.ShareChooserOpened, viewModel.exportState.value.message)
        assertEquals(null, viewModel.exportState.value.pendingShare)
    }

    @Test
    fun failedSharePreparationLeavesWorkflowReusable() = runTest {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.PLAIN_TEXT)
        coEvery { shareManager.prepareShareExport(any(), any(), any()) } throws RuntimeException("no file")

        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.PLAIN_TEXT)
        viewModel.onShareRequested(setOf("older"), listOf("older"))
        advanceUntilIdle()

        assertEquals(ExportMessage.ExportFailed, viewModel.exportState.value.message)
        assertFalse(viewModel.exportState.value.isPreparing)
        assertTrue(viewModel.exportState.value.showSaveOrShareChooser)
        assertEquals(null, viewModel.exportState.value.pendingShare)
    }

    private fun success(format: ExportFormat): ExportResult.Success = ExportResult.Success(
        format = format,
        content = "{}".toByteArray(),
        mimeType = format.mimeType,
        suggestedFileName = if (format == ExportFormat.JSON) "export.json" else "export.txt",
        captureCount = 1
    )

    private fun prepareShare(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) {
        coEvery { repository.getActiveByIds(setOf("older")) } returns listOf(older)
        coEvery { coordinator.prepareExport(captures = any(), format = any(), source = any()) } returns
            success(ExportFormat.PLAIN_TEXT)
        coEvery { shareManager.prepareShareExport(any(), any(), any()) } returns PreparedShareExport(
            contentUri = mockk<android.net.Uri>(),
            mimeType = "text/plain",
            fileName = "export.txt"
        )
        viewModel.onExportRequested()
        viewModel.onExportFormatSelected(ExportFormat.PLAIN_TEXT)
        viewModel.onShareRequested(setOf("older"), listOf("older"))
        scheduler.advanceUntilIdle()
        assertTrue(viewModel.exportState.value.pendingShare != null)
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
