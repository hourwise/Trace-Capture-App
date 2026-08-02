package uk.co.pcgsoft.tracecapture.export.formatter

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
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
import uk.co.pcgsoft.tracecapture.export.UtcTimestampFormatter
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo

class JsonCaptureExportFormatterTest {

    private val timestampFormatter = mockk<UtcTimestampFormatter>()
    private val mapper = CaptureExportMapper(timestampFormatter)
    private val formatter = JsonCaptureExportFormatter(mapper)

    @Test
    fun `has ExportFormat_JSON as format`() {
        assertEquals(ExportFormat.JSON, formatter.format)
    }

    @Test
    fun `formats captures to valid JSON`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        // Should be valid JSON (doesn't throw)
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(CaptureExportDocument.serializer(), result.content.decodeToString())
        assertNotNull(decoded)
    }

    @Test
    fun `JSON contains all required fields`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val json = result.content.decodeToString()

        assertTrue(json.contains("schemaVersion"))
        assertTrue(json.contains("exportedAt"))
        assertTrue(json.contains("application"))
        assertTrue(json.contains("selection"))
        assertTrue(json.contains("captures"))
    }

    @Test
    fun `JSON is properly pretty-printed`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)
        val json = result.content.decodeToString()

        // Pretty-printed JSON should have indentation
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("  "))
    }

    @Test
    fun `handles empty capture list`() {
        every { timestampFormatter.format(any()) } returns "2021-01-01T00:00:00Z"

        val captures = emptyList<CaptureItem>()
        val context = createTestContext()

        val result = formatter.format(captures, context)

        // Should still be valid JSON with empty captures array
        val json = result.content.decodeToString()
        assertTrue(json.contains("\"captures\""))
        assertTrue(json.contains("[]"))
    }

    @Test
    fun `returns correct format in result`() {
        every { timestampFormatter.format(any()) } returns "2021-01-01T00:00:00Z"
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        assertEquals(ExportFormat.JSON, result.format)
    }

    @Test
    fun `returns correct mime type in result`() {
        every { timestampFormatter.format(any()) } returns "2021-01-01T00:00:00Z"
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        assertEquals("application/json", result.mimeType)
    }

    @Test
    fun `content is UTF-8 encoded`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val context = createTestContext()

        val result = formatter.format(captures, context)

        // Should be decodable as UTF-8 JSON
        val json = String(result.content, Charsets.UTF_8)
        assertNotNull(json)
        assertTrue(json.startsWith("{"))
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
