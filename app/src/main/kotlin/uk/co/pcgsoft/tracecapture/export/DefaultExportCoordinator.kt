package uk.co.pcgsoft.tracecapture.export

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.file.ExportFileNameFactory
import uk.co.pcgsoft.tracecapture.export.formatter.CaptureExportFormatter
import uk.co.pcgsoft.tracecapture.export.formatter.JsonCaptureExportFormatter
import uk.co.pcgsoft.tracecapture.export.formatter.TextCaptureExportFormatter
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultExportCoordinator @Inject constructor(
    private val jsonFormatter: JsonCaptureExportFormatter,
    private val textFormatter: TextCaptureExportFormatter,
    private val fileNameFactory: ExportFileNameFactory,
    private val clock: ExportClock,
    private val applicationInfo: ExportApplicationInfo,
    private val limits: ExportLimits
) : ExportCoordinator {

    override suspend fun prepareExport(
        captures: List<CaptureItem>,
        format: ExportFormat,
        source: ExportSource
    ): ExportResult {
        if (captures.isEmpty()) {
            return ExportResult.Failure(ExportFailure.EmptySelection)
        }
        if (captures.size > limits.maxCaptures) {
            return ExportResult.Failure(ExportFailure.TooManyCaptures)
        }
        val context = CaptureExportContext(
            exportedAtEpochMillis = clock.nowEpochMillis(),
            source = source,
            application = applicationInfo
        )
        val formatted = try {
            formatterFor(format).format(captures, context)
        } catch (_: Exception) {
            return ExportResult.Failure(ExportFailure.FormattingFailed())
        }
        if (formatted.content.size > limits.maxOutputBytes) {
            return ExportResult.Failure(ExportFailure.OutputTooLarge)
        }
        return ExportResult.Success(
            format = format,
            content = formatted.content,
            mimeType = formatted.mimeType,
            suggestedFileName = fileNameFactory.create(
                captures,
                context.exportedAtEpochMillis,
                format
            ),
            captureCount = captures.size
        )
    }

    private fun formatterFor(format: ExportFormat): CaptureExportFormatter = when (format) {
        ExportFormat.JSON -> jsonFormatter
        ExportFormat.PLAIN_TEXT -> textFormatter
    }
}
