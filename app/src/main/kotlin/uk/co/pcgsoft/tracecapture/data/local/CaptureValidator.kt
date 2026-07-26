package uk.co.pcgsoft.tracecapture.data.local

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureValidator @Inject constructor() {

    companion object {
        const val MAX_ORIGINAL_CONTENT_LENGTH = 100_000
        const val MAX_NOTE_LENGTH = 2_000
        const val MAX_DETECTED_URLS = 25
    }

    fun validate(item: CaptureItem) {
        if (item.originalContent.isBlank()) {
            throw CaptureValidationException("Original content must not be blank")
        }
        if (item.originalContent.length > MAX_ORIGINAL_CONTENT_LENGTH) {
            throw CaptureValidationException(
                "Original content exceeds maximum length of $MAX_ORIGINAL_CONTENT_LENGTH characters"
            )
        }
        if (item.note?.length ?: 0 > MAX_NOTE_LENGTH) {
            throw CaptureValidationException("Note exceeds maximum length of $MAX_NOTE_LENGTH characters")
        }
        if (item.detectedUrls.any { it.isBlank() }) {
            throw CaptureValidationException("Detected URLs must not be blank")
        }
        val uniqueNonBlank = item.detectedUrls.distinct()
        if (uniqueNonBlank.size > MAX_DETECTED_URLS) {
            throw CaptureValidationException("Number of unique detected URLs exceeds maximum of $MAX_DETECTED_URLS")
        }
    }

    fun sanitize(item: CaptureItem): CaptureItem {
        val sanitizedUrls = item.detectedUrls
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return item.copy(detectedUrls = sanitizedUrls)
    }
}
