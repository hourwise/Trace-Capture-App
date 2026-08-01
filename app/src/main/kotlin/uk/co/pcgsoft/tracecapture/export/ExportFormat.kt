package uk.co.pcgsoft.tracecapture.export

enum class ExportFormat(
    val mimeType: String,
    val extension: String
) {
    JSON(
        mimeType = "application/json",
        extension = ".json"
    ),
    PLAIN_TEXT(
        mimeType = "text/plain",
        extension = ".txt"
    )
}
