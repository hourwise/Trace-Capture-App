package uk.co.pcgsoft.tracecapture.export.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptureExportDocument(
    val schemaVersion: String,
    val exportedAt: String,
    val application: ExportApplicationInfo,
    val selection: ExportSelectionInfo,
    val captures: List<CaptureExportItem>
) {
    companion object {
        const val SCHEMA_VERSION = "1"
    }
}

@Serializable
data class ExportApplicationInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

@Serializable
data class ExportSelectionInfo(
    val captureCount: Int,
    val source: String
)
