package uk.co.pcgsoft.tracecapture.export

data class CreateExportDocumentRequest(
    val mimeType: String,
    val suggestedFileName: String,
    val content: ByteArray
)
