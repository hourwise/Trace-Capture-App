package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent

interface SharedCaptureProcessor {
    fun process(intent: Intent): CaptureDraft?
}
