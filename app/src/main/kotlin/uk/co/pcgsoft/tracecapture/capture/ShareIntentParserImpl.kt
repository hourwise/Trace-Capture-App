package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareIntentParserImpl @Inject constructor() : ShareIntentParser {

    companion object {
        private const val MAX_CONTENT_LENGTH = 100_000
    }

    override fun parse(intent: Intent): ShareIntentParseResult {
        if (intent.action != Intent.ACTION_SEND) {
            return ShareIntentParseResult.Rejected(ShareRejectionReason.UNSUPPORTED_ACTION)
        }

        val mimeType = intent.type
        if (mimeType != null && mimeType != "text/plain") {
            return ShareIntentParseResult.Rejected(ShareRejectionReason.UNSUPPORTED_MIME_TYPE)
        }

        val raw = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        if (raw == null) {
            return ShareIntentParseResult.Rejected(ShareRejectionReason.MISSING_CONTENT)
        }

        val text = raw.toString()
        if (text.isBlank()) {
            return ShareIntentParseResult.Rejected(ShareRejectionReason.BLANK_CONTENT)
        }

        if (text.length > MAX_CONTENT_LENGTH) {
            return ShareIntentParseResult.Rejected(ShareRejectionReason.CONTENT_TOO_LONG)
        }

        val hint = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)

        return ShareIntentParseResult.Success(
            content = SharedContent(
                textContent = text,
                sourcePackageHint = hint
            )
        )
    }
}
