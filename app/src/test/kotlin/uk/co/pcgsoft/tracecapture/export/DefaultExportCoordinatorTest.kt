package uk.co.pcgsoft.tracecapture.export

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.file.ExportFileNameFactory
import uk.co.pcgsoft.tracecapture.export.formatter.FormattedExport
import uk.co.pcgsoft.tracecapture.export.formatter.JsonCaptureExportFormatter
import uk.co.pcgsoft.tracecapture.export.formatter.TextCaptureExportFormatter
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo

class DefaultExportCoordinatorTest {

    private val jsonFormatter = mockk<JsonCaptureExportFormatter>()
    private val textFormatter = mockk<TextCaptureExportFormatter>()
    private val fileNameFactory = mockk<ExportFileNameFactory>()
    private val clock = mockk<ExportClock>()
    private val applicationInfo = mockk<ExportApplicationInfo>()
    private val limits = ExportLimits(maxCaptures = 10_000, maxOutputBytes = 50L * 1024 * 1024)

    private val coordinator = DefaultExportCoordinator(
        jsonFormatter = jsonFormatter,
        textFormatter = textFormatter,
        fileNameFactory = fileNameFactory,
        clock = clock,
        applicationInfo = applicationInfo,
        limits = limits
    )

    @Test
    fun `empty captures returns EmptySelection failure`() = runTest {
        val result = coordinator.prepareExport(emptyList(), ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Failure)
        assertTrue((result as ExportResult.Failure).failure is ExportFailure.EmptySelection)
    }

    @Test
    fun `too many captures returns TooManyCaptures failure`() = runTest {
        val limitsWithSmallMax = ExportLimits(maxCaptures = 5, maxOutputBytes = 50L * 1024 * 1024)
        val coordinatorWithSmallLimit = DefaultExportCoordinator(
            jsonFormatter = jsonFormatter,
            textFormatter = textFormatter,
            fileNameFactory = fileNameFactory,
            clock = clock,
            applicationInfo = applicationInfo,
            limits = limitsWithSmallMax
        )

        val captures = (1..10).map { createTestCapture(id = "id-$it") }
        val result = coordinatorWithSmallLimit.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Failure)
        assertTrue((result as ExportResult.Failure).failure is ExportFailure.TooManyCaptures)
    }

    @Test
    fun `valid captures with JSON format returns success`() = runTest {
        val captures = listOf(createTestCapture())
        val formattedContent = "{}".toByteArray()

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            jsonFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.JSON,
            content = formattedContent,
            mimeType = "application/json"
        )
        every { fileNameFactory.create(captures, 1609459200000L, ExportFormat.JSON) } returns "export.json"

        val result = coordinator.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Success)
        result as ExportResult.Success
        assertEquals(ExportFormat.JSON, result.format)
        assertEquals("application/json", result.mimeType)
        assertEquals("export.json", result.suggestedFileName)
        assertEquals(1, result.captureCount)
    }

    @Test
    fun `valid captures with PLAIN_TEXT format returns success`() = runTest {
        val captures = listOf(createTestCapture())
        val formattedContent = "Text export".toByteArray()

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            textFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.PLAIN_TEXT,
            content = formattedContent,
            mimeType = "text/plain"
        )
        every { fileNameFactory.create(captures, 1609459200000L, ExportFormat.PLAIN_TEXT) } returns "export.txt"

        val result = coordinator.prepareExport(captures, ExportFormat.PLAIN_TEXT, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Success)
        result as ExportResult.Success
        assertEquals(ExportFormat.PLAIN_TEXT, result.format)
        assertEquals("text/plain", result.mimeType)
        assertEquals("export.txt", result.suggestedFileName)
    }

    @Test
    fun `output too large returns OutputTooLarge failure`() = runTest {
        val limitsWithSmallMax = ExportLimits(maxCaptures = 10_000, maxOutputBytes = 100L)
        val coordinatorWithSmallLimit = DefaultExportCoordinator(
            jsonFormatter = jsonFormatter,
            textFormatter = textFormatter,
            fileNameFactory = fileNameFactory,
            clock = clock,
            applicationInfo = applicationInfo,
            limits = limitsWithSmallMax
        )

        val captures = listOf(createTestCapture())
        val largeContent = ByteArray(200) // Exceeds the 100-byte limit

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            jsonFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.JSON,
            content = largeContent,
            mimeType = "application/json"
        )

        val result = coordinatorWithSmallLimit.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Failure)
        assertTrue((result as ExportResult.Failure).failure is ExportFailure.OutputTooLarge)
    }

    @Test
    fun `formatter exception returns FormattingFailed failure`() = runTest {
        val captures = listOf(createTestCapture())

        every { clock.nowEpochMillis() } returns 1609459200000L
        every { jsonFormatter.format(captures, any()) } throws RuntimeException("Formatting error")

        val result = coordinator.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Failure)
        assertTrue((result as ExportResult.Failure).failure is ExportFailure.FormattingFailed)
    }

    @Test
    fun `correct formatter is used for JSON format`() = runTest {
        val captures = listOf(createTestCapture())
        val formattedContent = "{}".toByteArray()

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            jsonFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.JSON,
            content = formattedContent,
            mimeType = "application/json"
        )
        every { fileNameFactory.create(captures, 1609459200000L, ExportFormat.JSON) } returns "export.json"

        coordinator.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        // Verify jsonFormatter was called and textFormatter was not
        verify(exactly = 1) { jsonFormatter.format(captures, any()) }
        verify(exactly = 0) { textFormatter.format(any(), any()) }
    }

    @Test
    fun `correct formatter is used for PLAIN_TEXT format`() = runTest {
        val captures = listOf(createTestCapture())
        val formattedContent = "Text export".toByteArray()

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            textFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.PLAIN_TEXT,
            content = formattedContent,
            mimeType = "text/plain"
        )
        every { fileNameFactory.create(captures, 1609459200000L, ExportFormat.PLAIN_TEXT) } returns "export.txt"

        coordinator.prepareExport(captures, ExportFormat.PLAIN_TEXT, ExportSource.SINGLE_CAPTURE)

        // Verify textFormatter was called and jsonFormatter was not
        verify(exactly = 0) { jsonFormatter.format(any(), any()) }
        verify(exactly = 1) { textFormatter.format(captures, any()) }
    }

    @Test
    fun `passes correct context to formatter`() = runTest {
        val captures = listOf(createTestCapture())
        val formattedContent = "{}".toByteArray()
        val nowMillis = 1609459200000L

        every { clock.nowEpochMillis() } returns nowMillis
        every {
            jsonFormatter.format(any(), any())
        } returns FormattedExport(
            format = ExportFormat.JSON,
            content = formattedContent,
            mimeType = "application/json"
        )
        every { fileNameFactory.create(captures, nowMillis, ExportFormat.JSON) } returns "export.json"

        val source = ExportSource.SUPPLIED_CAPTURE_LIST
        coordinator.prepareExport(captures, ExportFormat.JSON, source)

        // Verify that the context passed contains correct values
        verify {
            jsonFormatter.format(captures, match { context ->
                context.exportedAtEpochMillis == nowMillis &&
                context.source == source &&
                context.application == applicationInfo
            })
        }
    }

    @Test
    fun `success result includes correct capture count`() = runTest {
        val captures = (1..5).map { createTestCapture(id = "id-$it") }
        val formattedContent = "{}".toByteArray()

        every { clock.nowEpochMillis() } returns 1609459200000L
        every {
            jsonFormatter.format(captures, any())
        } returns FormattedExport(
            format = ExportFormat.JSON,
            content = formattedContent,
            mimeType = "application/json"
        )
        every { fileNameFactory.create(captures, 1609459200000L, ExportFormat.JSON) } returns "export.json"

        val result = coordinator.prepareExport(captures, ExportFormat.JSON, ExportSource.SINGLE_CAPTURE)

        assertTrue(result is ExportResult.Success)
        result as ExportResult.Success
        assertEquals(5, result.captureCount)
    }

    private fun createTestCapture(
        id: String = "test-id",
        createdAtEpochMillis: Long = 1609459200000L,
        updatedAtEpochMillis: Long = 1609545600000L,
        originalContent: String = "Test Content",
        primaryUrl: String? = "https://example.com",
        detectedUrls: List<String> = listOf("https://example.com"),
        sourcePackageName: String? = "com.example.app",
        sourceLabel: String? = "Example App",
        note: String? = "Test Note",
        captureType: CaptureType = CaptureType.URL_WITH_TEXT,
        status: CaptureStatus = CaptureStatus.PENDING,
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
    ) = CaptureItem(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        originalContent = originalContent,
        primaryUrl = primaryUrl,
        detectedUrls = detectedUrls,
        sourcePackageName = sourcePackageName,
        sourceLabel = sourceLabel,
        note = note,
        captureType = captureType,
        status = status,
        syncStatus = syncStatus,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )
}
