package uk.co.pcgsoft.tracecapture.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

class CaptureItemMapperTest {

    private val entity = CaptureItemEntity(
        id = "test-id",
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 2000L,
        originalContent = "test content https://example.com",
        primaryUrl = "https://example.com",
        detectedUrlsJson = """["https://example.com"]""",
        sourcePackageName = "com.example",
        sourceLabel = "Example",
        note = "a note",
        captureType = CaptureType.URL_WITH_TEXT,
        status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    private val domain = CaptureItem(
        id = "test-id",
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 2000L,
        originalContent = "test content https://example.com",
        primaryUrl = "https://example.com",
        detectedUrls = listOf("https://example.com"),
        sourcePackageName = "com.example",
        sourceLabel = "Example",
        note = "a note",
        captureType = CaptureType.URL_WITH_TEXT,
        status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    @Test
    fun `entity maps to domain correctly`() {
        val result = entity.toDomain()
        assertEquals(domain, result)
    }

    @Test
    fun `domain maps to entity correctly`() {
        val result = domain.toEntity()
        assertEquals(entity, result)
    }

    @Test
    fun `round-trip entity to domain to entity preserves all fields`() {
        val roundTripped = entity.toDomain().toEntity()
        assertEquals(entity, roundTripped)
    }

    @Test
    fun `round-trip domain to entity to domain preserves all fields`() {
        val roundTripped = domain.toEntity().toDomain()
        assertEquals(domain, roundTripped)
    }

    @Test
    fun `empty detected URLs are handled`() {
        val entityWithEmptyUrls = entity.copy(detectedUrlsJson = null)
        val domainResult = entityWithEmptyUrls.toDomain()
        assertTrue(domainResult.detectedUrls.isEmpty())
    }

    @Test
    fun `entity with archived fields maps correctly`() {
        val archived = entity.copy(
            status = CaptureStatus.ARCHIVED,
            archivedAtEpochMillis = 3000L,
            note = "archived note"
        )
        val result = archived.toDomain()
        assertEquals(CaptureStatus.ARCHIVED, result.status)
        assertNotNull(result.archivedAtEpochMillis)
    }

    @Test
    fun `entity with deleted fields maps correctly`() {
        val deleted = entity.copy(
            deletedAtEpochMillis = 4000L
        )
        val result = deleted.toDomain()
        assertNotNull(result.deletedAtEpochMillis)
    }

    @Test
    fun `domain with duplicateOfId maps correctly`() {
        val withDuplicate = domain.copy(duplicateOfId = "existing-id")
        val entityResult = withDuplicate.toEntity()
        assertEquals("existing-id", entityResult.duplicateOfId)
    }
}
