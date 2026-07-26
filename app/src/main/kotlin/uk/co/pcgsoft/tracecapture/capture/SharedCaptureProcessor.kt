package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent

interface SharedCaptureProcessor {
    fun process(intent: Intent): SharedCaptureResult
}

sealed interface SharedCaptureResult {
    data class Ready(val draft: CaptureDraft) : SharedCaptureResult
    data class Rejected(val reason: ShareRejectionReason) : SharedCaptureResult
}
