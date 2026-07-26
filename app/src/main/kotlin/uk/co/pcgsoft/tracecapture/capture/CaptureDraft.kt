package uk.co.pcgsoft.tracecapture.capture

import uk.co.pcgsoft.tracecapture.domain.CaptureType

data class CaptureDraft(
    val originalContent: String,
    val primaryUrl: String?,
    val detectedUrls: List<String>,
    val sourcePackageName: String?,
    val sourceLabel: String?,
    val captureType: CaptureType
)
