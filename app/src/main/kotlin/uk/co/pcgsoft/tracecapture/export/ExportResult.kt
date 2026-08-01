package uk.co.pcgsoft.tracecapture.export

sealed interface ExportResult {
    data class Success(
        val format: ExportFormat,
        val content: ByteArray,
        val mimeType: String,
        val suggestedFileName: String,
        val captureCount: Int
    ) : ExportResult

    data class Failure(
        val failure: ExportFailure
    ) : ExportResult
}
