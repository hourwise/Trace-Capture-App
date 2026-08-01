package uk.co.pcgsoft.tracecapture.export.formatter

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.CaptureExportContext
import uk.co.pcgsoft.tracecapture.export.CaptureExportMapper
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ReadableUtcDateFormatter
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextCaptureExportFormatter @Inject constructor(
    private val mapper: CaptureExportMapper,
    private val dateFormatter: ReadableUtcDateFormatter
) : CaptureExportFormatter {

    override val format: ExportFormat = ExportFormat.PLAIN_TEXT

    override fun format(
        captures: List<CaptureItem>,
        context: CaptureExportContext
    ): FormattedExport {
        val lines = buildList {
            add("TRACE Capture Export")
            add("Schema version: ${CaptureExportDocument.SCHEMA_VERSION}")
            add("Exported: ${dateFormatter.format(context.exportedAtEpochMillis)}")
            add("Captures: ${captures.size}")
            add("")
            captures.forEachIndexed { index, capture ->
                if (index > 0) add("")
                add(SEPARATOR)
                add("CAPTURE ${index + 1}")
                add(SEPARATOR)
                add("ID: ${capture.id}")
                add("Status: ${mapper.statusLabel(capture.status)}")
                add("Captured: ${dateFormatter.format(capture.createdAtEpochMillis)}")
                add("Updated: ${dateFormatter.format(capture.updatedAtEpochMillis)}")
                add("Source: ${mapper.resolveSourceLabel(capture) ?: "Unknown"}")
                add("Type: ${mapper.typeLabel(capture.captureType)}")
                capture.duplicateOfId?.let { duplicateId ->
                    add("Duplicate of: $duplicateId")
                }
                add("")
                capture.primaryUrl?.let { url ->
                    add("PRIMARY URL")
                    add(url)
                    add("")
                }
                if (capture.detectedUrls.isNotEmpty()) {
                    add("DETECTED URLS")
                    capture.detectedUrls.forEachIndexed { urlIndex, url ->
                        add("${urlIndex + 1}. $url")
                    }
                    add("")
                }
                add("ORIGINAL CONTENT")
                add(capture.originalContent)
                if (!capture.note.isNullOrBlank()) {
                    add("")
                    add("NOTE")
                    add(capture.note)
                }
            }
        }
        val text = lines.joinToString("\n") + "\n"
        return FormattedExport(
            format = ExportFormat.PLAIN_TEXT,
            content = text.toByteArray(Charsets.UTF_8),
            mimeType = ExportFormat.PLAIN_TEXT.mimeType
        )
    }

    private companion object {
        const val SEPARATOR = "=".repeat(60)
    }
}
