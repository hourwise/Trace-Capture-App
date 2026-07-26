package uk.co.pcgsoft.tracecapture.capture

data class DuplicateCaptureWarning(
    val existingCaptureId: String,
    val capturedAtEpochMillis: Long,
    val existingCount: Int
)
