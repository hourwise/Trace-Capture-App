package uk.co.pcgsoft.tracecapture.export.formatter

import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.CaptureExportContext
import uk.co.pcgsoft.tracecapture.export.CaptureExportMapper
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportSource
import uk.co.pcgsoft.tracecapture.export.ReadableUtcDateFormatter
import uk.co.pcgsoft.tracecapture.export.UtcTimestampFormatter
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo

class TextCaptureExportFormatterTest {

    private val timestampFormatter = mockk<UtcTimestampFormatter>()
    private val dateFormatter = mockk<ReadableUtcDateFormatter>()
    private val mapper = CaptureExportMapper(timestampFormatter)
    private val formatter = TextCaptureExportFormatter(mapper, dateFormatter)

    @Test
    fun `has ExportFormat_PLAIN_TEXT as format`() {
        assertEquals(ExportFormat.PLAIN_TEXT, formatter.format)
    }

    @Test
    fun `formats captures to text with proper sections`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("TRACE Capture Export"))
        assertTrue(text.contains("Schema version:"))
        assertTrue(text.contains("Exported:"))
        assertTrue(text.contains("Captures:"))
    }

    @Test
    fun `includes SEPARATOR constant with 60 equals signs`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        val expectedSeparator = "============================================================"
        assertTrue(text.contains(expectedSeparator))
        assertEquals(60, expectedSeparator.length)
    }

    @Test
    fun `includes header with schema version and export date`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("Schema version: ${CaptureExportDocument.SCHEMA_VERSION}"))
        assertTrue(text.contains("Exported:"))
    }

    @Test
    fun `includes all capture details`() {
        setupDateFormatterMocks()
        val capture = createTestCapture(
            id = "test-id-123",
            sourceLabel = "Example App"
        )
        val captures = listOf(capture)
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("CAPTURE 1"))
        assertTrue(text.contains("ID: test-id-123"))
        assertTrue(text.contains("Status: Pending"))
        assertTrue(text.contains("Type: URL with text"))
        assertTrue(text.contains("Source: Example App"))
        assertTrue(text.contains("PRIMARY URL"))
        assertTrue(text.contains("https://example.com"))
        assertTrue(text.contains("ORIGINAL CONTENT"))
        assertTrue(text.contains("Test Content"))
    }

    @Test
    fun `includes detected URLs section when present`() {
        setupDateFormatterMocks()
        val capture = createTestCapture(
            detectedUrls = listOf("https://example.com", "https://github.com")
        )
        val captures = listOf(capture)
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("DETECTED URLS"))
        assertTrue(text.contains("1. https://example.com"))
        assertTrue(text.contains("2. https://github.com"))
    }

    @Test
    fun `includes note when present`() {
        setupDateFormatterMocks()
        val capture = createTestCapture(note = "Important note about this capture")
        val captures = listOf(capture)
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("NOTE"))
        assertTrue(text.contains("Important note about this capture"))
    }

    @Test
    fun `does not include note when null or blank`() {
        setupDateFormatterMocks()
        val capture = createTestCapture(note = null)
        val captures = listOf(capture)
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        // Find "ORIGINAL CONTENT" and ensure "NOTE" doesn't appear after it without a value
        val originalContentIndex = text.indexOf("ORIGINAL CONTENT")
        val noteIndex = text.indexOf("NOTE")
        
        // Either NOTE doesn't exist, or if it does, it's before ORIGINAL CONTENT (from a different test)
        assertTrue(noteIndex == -1)
    }

    @Test
    fun `includes duplicate info when present`() {
        setupDateFormatterMocks()
        val capture = createTestCapture(duplicateOfId = "duplicate-of-id-456")
        val captures = listOf(capture)
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("Duplicate of: duplicate-of-id-456"))
    }

    @Test
    fun `handles multiple captures with separators`() {
        setupDateFormatterMocks()
        val captures = listOf(
            createTestCapture(id = "id-1"),
            createTestCapture(id = "id-2"),
            createTestCapture(id = "id-3")
        )
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("CAPTURE 1"))
        assertTrue(text.contains("CAPTURE 2"))
        assertTrue(text.contains("CAPTURE 3"))
        assertTrue(text.contains("ID: id-1"))
        assertTrue(text.contains("ID: id-2"))
        assertTrue(text.contains("ID: id-3"))
    }

    @Test
    fun `returns correct format in result`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        assertEquals(ExportFormat.PLAIN_TEXT, result.format)
    }

    @Test
    fun `returns correct mime type in result`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        assertEquals("text/plain", result.mimeType)
    }

    @Test
    fun `content is UTF-8 encoded`() {
        setupDateFormatterMocks()
        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        val text = String(result.content, Charsets.UTF_8)
        assertNotNull(text)
        assertTrue(text.contains("TRACE Capture Export"))
    }

    @Test
    fun `handles empty capture list with header only`() {
        every { dateFormatter.format(any()) } returns "1 January 2021, 00:00 UTC"

        val captures = emptyList<CaptureItem>()
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val text = result.content.decodeToString()

        assertTrue(text.contains("TRACE Capture Export"))
        assertTrue(text.contains("Captures: 0"))
        assertNotNull(text)
    }

    private fun setupDateFormatterMocks() {
        every { dateFormatter.format(1609459200000L) } returns "1 January 2021, 00:00 UTC"
        every { dateFormatter.format(1609545600000L) } returns "2 January 2021, 00:00 UTC"
        every { dateFormatter.format(1609462800000L) } returns "1 January 2021, 01:00 UTC"
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
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId: String? = null
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
        duplicateOfId = duplicateOfId,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    private fun createTestContext() = CaptureExportContext(
        exportedAtEpochMillis = 1609459200000L,
        source = ExportSource.SINGLE_CAPTURE,
        application = ExportApplicationInfo(
            name = "Test App",
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1L
        )
    )
}
