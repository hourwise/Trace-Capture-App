package uk.co.pcgsoft.tracecapture.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.detail.CaptureExportViewModel
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.ExportCoordinator
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportResult
import uk.co.pcgsoft.tracecapture.export.file.ExportFileWriter
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import uk.co.pcgsoft.tracecapture.inbox.InboxExportViewModel
import java.io.IOException

/**
 * Preferred export format behaviour shared by the detail and bulk export
 * ViewModels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportPreferenceIntegrationTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CaptureRepository>()
    private val coordinator = mockk<ExportCoordinator>()
    private val writer = mockk<ExportFileWriter>()
    private val shareManager = mockk<ExportShareFileManager>()
    private val settingsRepository = FakeSettingsRepository()

    private val item = CaptureItem(
        id = "item",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
        originalContent = "content",
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun success(format: ExportFormat) = ExportResult.Success(
        format = format,
        content = "{}".toByteArray(),
        mimeType = format.mimeType,
        suggestedFileName = if (format == ExportFormat.JSON) "export.json" else "export.txt",
        captureCount = 1
    )

    // ---- Bulk export (InboxExportViewModel) ----

    private fun bulkViewModel(repo: SettingsRepository = settingsRepository): InboxExportViewModel =
        InboxExportViewModel(repository, coordinator, writer, shareManager, repo)

    @Test
    fun bulkAskEveryTimeShowsFormatChooser() = runTest {
        val vm = bulkViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertTrue(vm.exportState.value.showFormatChooser)
        assertFalse(vm.exportState.value.showSaveOrShareChooser)
        assertNull(vm.exportState.value.selectedFormat)
    }

    @Test
    fun bulkJsonSkipsFormatChooserAndPreselectsJson() = runTest {
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.JSON))
        val vm = bulkViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertFalse(vm.exportState.value.showFormatChooser)
        assertTrue(vm.exportState.value.showSaveOrShareChooser)
        assertEquals(ExportFormat.JSON, vm.exportState.value.selectedFormat)
    }

    @Test
    fun bulkPlainTextSkipsFormatChooserAndPreselectsPlainText() = runTest {
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.PLAIN_TEXT))
        val vm = bulkViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertFalse(vm.exportState.value.showFormatChooser)
        assertTrue(vm.exportState.value.showSaveOrShareChooser)
        assertEquals(ExportFormat.PLAIN_TEXT, vm.exportState.value.selectedFormat)
    }

    @Test
    fun bulkPreferenceReadFailureFallsBackToChooser() = runTest {
        val failingRepo = mockk<SettingsRepository>()
        coEvery { failingRepo.settings } returns flow { throw IOException("boom") }
        val vm = bulkViewModel(failingRepo)
        vm.onExportRequested()
        advanceUntilIdle()

        assertTrue(vm.exportState.value.showFormatChooser)
        assertNull(vm.exportState.value.selectedFormat)
    }

    @Test
    fun bulkNoAutomaticExportBeforeExplicitSaveOrShare() = runTest {
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.JSON)
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.JSON))
        val vm = bulkViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        // Selecting the preferred format alone must not begin an export.
        coVerify(exactly = 0) { coordinator.prepareExport(any(), any(), any()) }

        coEvery { repository.getActiveByIds(setOf("item")) } returns listOf(item)
        vm.onSaveFileRequested(setOf("item"), listOf("item"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(item),
                format = ExportFormat.JSON,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
    }

    @Test
    fun bulkSettingSnapshotStableDuringOperation() = runTest {
        coEvery { repository.getActiveByIds(setOf("item")) } returns listOf(item)
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.JSON)
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.JSON))
        val vm = bulkViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        // The preference changes after the operation began; the export keeps JSON.
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.ASK_EVERY_TIME))
        vm.onSaveFileRequested(setOf("item"), listOf("item"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(item),
                format = ExportFormat.JSON,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SUPPLIED_CAPTURE_LIST
            )
        }
    }

    // ---- Detail export (CaptureExportViewModel) ----

    private fun detailViewModel(repo: SettingsRepository = settingsRepository): CaptureExportViewModel =
        CaptureExportViewModel(coordinator, writer, shareManager, repo)

    @Test
    fun detailAskEveryTimeShowsFormatChooser() = runTest {
        val vm = detailViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertTrue(vm.exportState.value.showFormatChooser)
        assertFalse(vm.exportState.value.showSaveOrShareChooser)
        assertNull(vm.exportState.value.selectedFormat)
    }

    @Test
    fun detailJsonSkipsFormatChooserAndSaveUsesJson() = runTest {
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.JSON))
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.JSON)
        val vm = detailViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertFalse(vm.exportState.value.showFormatChooser)
        assertTrue(vm.exportState.value.showSaveOrShareChooser)
        assertEquals(ExportFormat.JSON, vm.exportState.value.selectedFormat)

        vm.onSaveFileRequested(item)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(item),
                format = ExportFormat.JSON,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SINGLE_CAPTURE
            )
        }
    }

    @Test
    fun detailPlainTextSkipsFormatChooser() = runTest {
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.PLAIN_TEXT))
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.PLAIN_TEXT)
        val vm = detailViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        assertFalse(vm.exportState.value.showFormatChooser)
        assertEquals(ExportFormat.PLAIN_TEXT, vm.exportState.value.selectedFormat)

        vm.onShareRequested(item)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(item),
                format = ExportFormat.PLAIN_TEXT,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SINGLE_CAPTURE
            )
        }
    }

    @Test
    fun detailPreferenceReadFailureFallsBackToChooser() = runTest {
        val failingRepo = mockk<SettingsRepository>()
        coEvery { failingRepo.settings } returns flow { throw IOException("boom") }
        val vm = detailViewModel(failingRepo)
        vm.onExportRequested()
        advanceUntilIdle()

        assertTrue(vm.exportState.value.showFormatChooser)
        assertNull(vm.exportState.value.selectedFormat)
    }

    @Test
    fun detailCancellationCausesNoExport() = runTest {
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.JSON)
        val vm = detailViewModel()
        vm.onExportRequested()
        advanceUntilIdle()
        vm.onExportDialogCancelled()
        advanceUntilIdle()

        coVerify(exactly = 0) { coordinator.prepareExport(any(), any(), any()) }
        assertFalse(vm.exportState.value.showFormatChooser)
        assertFalse(vm.exportState.value.showSaveOrShareChooser)
    }

    @Test
    fun detailSettingSnapshotStableDuringOperation() = runTest {
        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.PLAIN_TEXT))
        coEvery { coordinator.prepareExport(any(), any(), any()) } returns success(ExportFormat.PLAIN_TEXT)
        val vm = detailViewModel()
        vm.onExportRequested()
        advanceUntilIdle()

        settingsRepository.emit(SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.JSON))
        vm.onSaveFileRequested(item)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            coordinator.prepareExport(
                captures = listOf(item),
                format = ExportFormat.PLAIN_TEXT,
                source = uk.co.pcgsoft.tracecapture.export.ExportSource.SINGLE_CAPTURE
            )
        }
    }
}
