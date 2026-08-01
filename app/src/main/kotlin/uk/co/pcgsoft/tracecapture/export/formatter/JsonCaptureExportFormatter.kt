package uk.co.pcgsoft.tracecapture.export.formatter

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.CaptureExportContext
import uk.co.pcgsoft.tracecapture.export.CaptureExportMapper
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonCaptureExportFormatter @Inject constructor(
    private val mapper: CaptureExportMapper
) : CaptureExportFormatter {

    override val format: ExportFormat = ExportFormat.JSON

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    override fun format(
        captures: List<CaptureItem>,
        context: CaptureExportContext
    ): FormattedExport {
        val document = mapper.toDocument(captures, context)
        val text = json.encodeToString(CaptureExportDocument.serializer(), document)
        return FormattedExport(
            format = ExportFormat.JSON,
            content = text.toByteArray(Charsets.UTF_8),
            mimeType = ExportFormat.JSON.mimeType
        )
    }
}
