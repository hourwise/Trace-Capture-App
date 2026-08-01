package uk.co.pcgsoft.tracecapture.detail

import uk.co.pcgsoft.tracecapture.export.CreateExportDocumentRequest
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportMessage
import uk.co.pcgsoft.tracecapture.export.share.PreparedShareExport

data class DetailExportState(
    val showFormatChooser: Boolean = false,
    val selectedFormat: ExportFormat? = null,
    val showSaveOrShareChooser: Boolean = false,
    val isPreparing: Boolean = false,
    val pendingDocument: CreateExportDocumentRequest? = null,
    val pendingShare: PreparedShareExport? = null,
    val message: ExportMessage? = null
)
