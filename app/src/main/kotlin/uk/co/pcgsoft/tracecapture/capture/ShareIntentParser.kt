package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent

interface ShareIntentParser {
    fun parse(intent: Intent): ShareIntentParseResult
}

sealed interface ShareIntentParseResult {
    data class Success(val content: SharedContent) : ShareIntentParseResult
    data class Rejected(val reason: ShareRejectionReason) : ShareIntentParseResult
}

data class SharedContent(
    val textContent: String,
    val sourcePackageHint: String?
)

enum class ShareRejectionReason {
    UNSUPPORTED_ACTION,
    UNSUPPORTED_MIME_TYPE,
    MISSING_CONTENT,
    BLANK_CONTENT,
    CONTENT_TOO_LONG
}
