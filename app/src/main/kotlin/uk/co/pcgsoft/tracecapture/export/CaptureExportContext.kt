package uk.co.pcgsoft.tracecapture.export

import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo

data class CaptureExportContext(
    val exportedAtEpochMillis: Long,
    val source: ExportSource,
    val application: ExportApplicationInfo
)
