package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareIntentParserImpl @Inject constructor() : ShareIntentParser {

    companion object {
        private const val MAX_CONTENT_LENGTH = 100_000
    }

    override fun parse(intent: Intent): ShareIntentResult? {
        val action = intent.action
        if (action != Intent.ACTION_SEND) return null

        val mimeType = intent.type
        if (mimeType != null && mimeType != "text/plain") return null

        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        if (text.isBlank()) return null
        if (text.length > MAX_CONTENT_LENGTH) return null

        val sourceHint = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)

        return ShareIntentResult(
            textContent = text,
            sourcePackageHint = sourceHint
        )
    }
}
