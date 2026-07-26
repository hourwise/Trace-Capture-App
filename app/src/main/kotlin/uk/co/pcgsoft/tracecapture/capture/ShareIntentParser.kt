package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent

interface ShareIntentParser {
    fun parse(intent: Intent): ShareIntentResult?
}

data class ShareIntentResult(
    val textContent: String,
    val sourcePackageHint: String?
)
