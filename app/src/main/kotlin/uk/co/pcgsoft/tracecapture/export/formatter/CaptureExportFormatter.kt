package uk.co.pcgsoft.tracecapture.export.formatter

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.CaptureExportContext
import uk.co.pcgsoft.tracecapture.export.ExportFormat

data class FormattedExport(
    val format: ExportFormat,
    val content: ByteArray,
    val mimeType: String
)

interface CaptureExportFormatter {
    val format: ExportFormat

    fun format(
        captures: List<CaptureItem>,
        context: CaptureExportContext
    ): FormattedExport
}
