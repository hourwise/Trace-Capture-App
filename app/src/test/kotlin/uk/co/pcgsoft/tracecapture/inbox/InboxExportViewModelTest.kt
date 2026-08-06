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
import uk.co.pcgsoft.tracecapture.export.ExportResult
import uk.co.pcgsoft.tracecapture.export.file.ExportFileWriter
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager

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
