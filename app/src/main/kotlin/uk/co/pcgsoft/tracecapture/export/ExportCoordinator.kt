package uk.co.pcgsoft.tracecapture.export

import uk.co.pcgsoft.tracecapture.domain.CaptureItem

interface ExportCoordinator {
    suspend fun prepareExport(
        captures: List<CaptureItem>,
        format: ExportFormat,
        source: ExportSource
    ): ExportResult
}
