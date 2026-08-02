package uk.co.pcgsoft.tracecapture.export

import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo

class CaptureExportMapperTest {

    private val timestampFormatter = mockk<UtcTimestampFormatter>()
    private val mapper = CaptureExportMapper(timestampFormatter)

    @Test
    fun `maps CaptureItem to CaptureExportItem correctly`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val capture = createTestCapture()
        val item = mapper.toExportItem(capture)

        assertEquals("test-id", item.id)
        assertEquals("Test Content", item.originalContent)
        assertEquals("https://example.com", item.primaryUrl)
        assertEquals(listOf("https://example.com"), item.detectedUrls)
        assertEquals("com.example.app", item.sourcePackageName)
        assertEquals("Example App", item.sourceLabel)
        assertEquals("Test Note", item.note)
    }

    @Test
    fun `maps status label correctly for PENDING`() {
        val label = mapper.statusLabel(CaptureStatus.PENDING)
        assertEquals("Pending", label)
    }

    @Test
    fun `maps status label correctly for REVIEWED`() {
        val label = mapper.statusLabel(CaptureStatus.REVIEWED)
        assertEquals("Reviewed", label)
    }

    @Test
    fun `maps status label correctly for ARCHIVED`() {
        val label = mapper.statusLabel(CaptureStatus.ARCHIVED)
        assertEquals("Archived", label)
    }

    @Test
    fun `maps status string correctly for PENDING`() {
        val status = mapper.statusString(CaptureStatus.PENDING)
        assertEquals("PENDING", status)
    }

    @Test
    fun `maps status string correctly for REVIEWED`() {
        val status = mapper.statusString(CaptureStatus.REVIEWED)
        assertEquals("REVIEWED", status)
    }

    @Test
    fun `maps type label correctly for URL`() {
        val label = mapper.typeLabel(CaptureType.URL)
        assertEquals("URL", label)
    }

    @Test
    fun `maps type label correctly for TEXT`() {
        val label = mapper.typeLabel(CaptureType.TEXT)
        assertEquals("Text", label)
    }

    @Test
    fun `maps type label correctly for URL_WITH_TEXT`() {
        val label = mapper.typeLabel(CaptureType.URL_WITH_TEXT)
        assertEquals("URL with text", label)
    }

    @Test
    fun `maps type label correctly for MULTIPLE_URLS`() {
        val label = mapper.typeLabel(CaptureType.MULTIPLE_URLS)
        assertEquals("Multiple URLs", label)
    }

    @Test
    fun `maps type string correctly for URL`() {
        val type = mapper.typeString(CaptureType.URL)
        assertEquals("URL", type)
    }

    @Test
    fun `maps type string correctly for TEXT`() {
        val type = mapper.typeString(CaptureType.TEXT)
        assertEquals("TEXT", type)
    }

    @Test
    fun `maps type string correctly for MULTIPLE_URLS`() {
        val type = mapper.typeString(CaptureType.MULTIPLE_URLS)
        assertEquals("MULTIPLE_URLS", type)
    }

    @Test
    fun `maps sync status string correctly`() {
        assertEquals("LOCAL_ONLY", mapper.syncStatusString(SyncStatus.LOCAL_ONLY))
        assertEquals("QUEUED", mapper.syncStatusString(SyncStatus.QUEUED))
        assertEquals("SYNCING", mapper.syncStatusString(SyncStatus.SYNCING))
        assertEquals("SYNCED", mapper.syncStatusString(SyncStatus.SYNCED))
        assertEquals("FAILED", mapper.syncStatusString(SyncStatus.FAILED))
    }

    @Test
    fun `resolveSourceLabel returns sourceLabel when present`() {
        val capture = createTestCapture(sourceLabel = "Custom Label")
        val label = mapper.resolveSourceLabel(capture)
        assertEquals("Custom Label", label)
    }

    @Test
    fun `resolveSourceLabel extracts domain from primaryUrl when sourceLabel is null`() {
        val capture = createTestCapture(sourceLabel = null, primaryUrl = "https://www.example.com/page")
        val label = mapper.resolveSourceLabel(capture)
        assertEquals("example.com", label)
    }

    @Test
    fun `resolveSourceLabel removes www prefix from domain`() {
        val capture = createTestCapture(sourceLabel = null, primaryUrl = "https://www.github.com/repo")
        val label = mapper.resolveSourceLabel(capture)
        assertEquals("github.com", label)
    }

    @Test
    fun `resolveSourceLabel returns package name when URL and label are null`() {
        val capture = createTestCapture(sourceLabel = null, primaryUrl = null, sourcePackageName = "com.example")
        val label = mapper.resolveSourceLabel(capture)
        assertEquals("com.example", label)
    }

    @Test
    fun `resolveSourceLabel returns null when all sources are null`() {
        val capture = createTestCapture(sourceLabel = null, primaryUrl = null, sourcePackageName = null)
        val label = mapper.resolveSourceLabel(capture)
        assertNull(label)
    }

    @Test
    fun `creates valid CaptureExportDocument`() {
        every { timestampFormatter.format(any()) } returnsMany listOf(
            "2021-01-01T00:00:00Z",
            "2021-01-02T00:00:00Z"
        )
        every { timestampFormatter.formatOrNull(null) } returns null

        val captures = listOf(createTestCapture())
        val appInfo = ExportApplicationInfo(
            name = "Test App",
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1L
        )
        val context = CaptureExportContext(
            exportedAtEpochMillis = 1609459200000L,
            source = ExportSource.SINGLE_CAPTURE,
            application = appInfo
        )

        val doc = mapper.toDocument(captures, context)

        assertEquals(CaptureExportDocument.SCHEMA_VERSION, doc.schemaVersion)
        assertEquals(appInfo, doc.application)
        assertEquals(1, doc.captures.size)
        assertEquals(1, doc.selection.captureCount)
        assertEquals("single_capture", doc.selection.source)
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
        duplicateOfId: String? = null,
        archivedAtEpochMillis: Long? = null,
        deletedAtEpochMillis: Long? = null
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
        archivedAtEpochMillis = archivedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )
}
