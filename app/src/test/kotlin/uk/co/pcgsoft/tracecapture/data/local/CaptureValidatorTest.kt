package uk.co.pcgsoft.tracecapture.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

class CaptureValidatorTest {

    private val validator = CaptureValidator()

    private fun validItem() = CaptureItem(
        id = "test-id",
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
        originalContent = "valid content with https://example.com",
        primaryUrl = "https://example.com",
        detectedUrls = listOf("https://example.com"),
        sourcePackageName = null,
        sourceLabel = null,
        note = null,
        captureType = CaptureType.URL_WITH_TEXT,
        status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    @Test
    fun `valid item passes validation`() {
        validator.validate(validItem())
    }

    @Test
    fun `blank content is rejected`() {
        val item = validItem().copy(originalContent = "   ")
        val exception = assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
        assertTrue(exception.message?.contains("blank", ignoreCase = true) == true)
    }

    @Test
    fun `empty content is rejected`() {
        val item = validItem().copy(originalContent = "")
        assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
    }

    @Test
    fun `content exceeding maximum length is rejected`() {
        val longContent = "a".repeat(CaptureValidator.MAX_ORIGINAL_CONTENT_LENGTH + 1)
        val item = validItem().copy(originalContent = longContent)
        val exception = assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
        assertTrue(exception.message?.contains("exceeds maximum") == true)
    }

    @Test
    fun `content at maximum length passes`() {
        val maxContent = "a".repeat(CaptureValidator.MAX_ORIGINAL_CONTENT_LENGTH)
        val item = validItem().copy(originalContent = maxContent)
        validator.validate(item)
    }

    @Test
    fun `note exceeding maximum length is rejected`() {
        val longNote = "b".repeat(CaptureValidator.MAX_NOTE_LENGTH + 1)
        val item = validItem().copy(note = longNote)
        assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
    }

    @Test
    fun `note at maximum length passes`() {
        val maxNote = "b".repeat(CaptureValidator.MAX_NOTE_LENGTH)
        val item = validItem().copy(note = maxNote)
        validator.validate(item)
    }

    @Test
    fun `null note passes validation`() {
        val item = validItem().copy(note = null)
        validator.validate(item)
    }

    @Test
    fun `blank URL in detected URLs is rejected`() {
        val item = validItem().copy(detectedUrls = listOf("https://example.com", "  "))
        assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
    }

    @Test
    fun `too many unique URLs are rejected`() {
        val urls = (1..CaptureValidator.MAX_DETECTED_URLS + 1).map { "https://example.com/$it" }
        val item = validItem().copy(detectedUrls = urls)
        assertThrows(CaptureValidationException::class.java) {
            validator.validate(item)
        }
    }

    @Test
    fun `maximum unique URLs pass`() {
        val urls = (1..CaptureValidator.MAX_DETECTED_URLS).map { "https://example.com/$it" }
        val item = validItem().copy(detectedUrls = urls)
        validator.validate(item)
    }

    @Test
    fun `sanitize removes blank URLs`() {
        val item = validItem().copy(
            detectedUrls = listOf("https://example.com", "  ", "https://test.com")
        )
        val result = validator.sanitize(item)
        assertEquals(listOf("https://example.com", "https://test.com"), result.detectedUrls)
    }

    @Test
    fun `sanitize trims URLs`() {
        val item = validItem().copy(
            detectedUrls = listOf("  https://example.com  ")
        )
        val result = validator.sanitize(item)
        assertEquals(listOf("https://example.com"), result.detectedUrls)
    }

    @Test
    fun `sanitize removes duplicate URLs preserving order`() {
        val item = validItem().copy(
            detectedUrls = listOf("https://a.com", "https://b.com", "https://a.com")
        )
        val result = validator.sanitize(item)
        assertEquals(listOf("https://a.com", "https://b.com"), result.detectedUrls)
    }

    @Test
    fun `sanitize preserves original content unchanged`() {
        val original = "Original text with URL"
        val item = validItem().copy(
            originalContent = original,
            detectedUrls = listOf("  https://example.com  ")
        )
        val result = validator.sanitize(item)
        assertEquals(original, result.originalContent)
    }
}
