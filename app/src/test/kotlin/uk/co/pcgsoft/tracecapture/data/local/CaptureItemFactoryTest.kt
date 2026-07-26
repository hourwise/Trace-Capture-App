package uk.co.pcgsoft.tracecapture.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

class CaptureItemFactoryTest {

    private val factory = CaptureItemFactory()

    @Test
    fun `create generates a UUID id`() {
        val item = factory.create(
            originalContent = "test",
            primaryUrl = null,
            detectedUrls = emptyList(),
            sourcePackageName = null,
            sourceLabel = null,
            note = null,
            captureType = CaptureType.TEXT
        )
        assertNotNull(item.id)
        assertTrue(item.id.isNotEmpty())
    }

    @Test
    fun `create generates unique UUIDs for consecutive calls`() {
        val item1 = factory.create("a", null, emptyList(), null, null, null, CaptureType.TEXT)
        val item2 = factory.create("b", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertTrue(item1.id != item2.id)
    }

    @Test
    fun `create sets createdAtEpochMillis to current time`() {
        val before = System.currentTimeMillis()
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        val after = System.currentTimeMillis()
        assertTrue(item.createdAtEpochMillis >= before)
        assertTrue(item.createdAtEpochMillis <= after)
    }

    @Test
    fun `create sets updatedAtEpochMillis equal to createdAtEpochMillis`() {
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertEquals(item.createdAtEpochMillis, item.updatedAtEpochMillis)
    }

    @Test
    fun `create defaults status to PENDING`() {
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertEquals(CaptureStatus.PENDING, item.status)
    }

    @Test
    fun `create defaults syncStatus to LOCAL_ONLY`() {
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertEquals(SyncStatus.LOCAL_ONLY, item.syncStatus)
    }

    @Test
    fun `create defaults duplicateOfId to null`() {
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertNull(item.duplicateOfId)
    }

    @Test
    fun `create defaults archivedAt and deletedAt to null`() {
        val item = factory.create("test", null, emptyList(), null, null, null, CaptureType.TEXT)
        assertNull(item.archivedAtEpochMillis)
        assertNull(item.deletedAtEpochMillis)
    }

    @Test
    fun `create preserves all provided fields`() {
        val item = factory.create(
            originalContent = "Check this out https://example.com",
            primaryUrl = "https://example.com",
            detectedUrls = listOf("https://example.com"),
            sourcePackageName = "com.example.app",
            sourceLabel = "Example App",
            note = "interesting article",
            captureType = CaptureType.URL_WITH_TEXT
        )
        assertEquals("Check this out https://example.com", item.originalContent)
        assertEquals("https://example.com", item.primaryUrl)
        assertEquals(listOf("https://example.com"), item.detectedUrls)
        assertEquals("com.example.app", item.sourcePackageName)
        assertEquals("Example App", item.sourceLabel)
        assertEquals("interesting article", item.note)
        assertEquals(CaptureType.URL_WITH_TEXT, item.captureType)
    }
}
