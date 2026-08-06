package uk.co.pcgsoft.tracecapture.inbox

import uk.co.pcgsoft.tracecapture.export.CreateExportDocumentRequest
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportMessage
import uk.co.pcgsoft.tracecapture.export.share.PreparedShareExport

data class InboxExportState(
    val showFormatChooser: Boolean = false,
    val showSaveOrShareChooser: Boolean = false,
    val selectedFormat: ExportFormat? = null,
    val isPreparing: Boolean = false,
    val pendingDocument: CreateExportDocumentRequest? = null,
    val pendingShare: PreparedShareExport? = null,
    val unavailableIds: Set<String> = emptySet(),
    val message: ExportMessage? = null,
    val documentLaunchConsumed: Boolean = false,
    val shareLaunchConsumed: Boolean = false
)
