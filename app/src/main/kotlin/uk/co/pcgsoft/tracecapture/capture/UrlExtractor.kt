package uk.co.pcgsoft.tracecapture.capture

import uk.co.pcgsoft.tracecapture.domain.CaptureType

interface UrlExtractor {
    fun extractUrls(text: String): UrlExtractionResult
}

data class UrlExtractionResult(
    val urls: List<String>,
    val primaryUrl: String?,
    val captureType: CaptureType
)
