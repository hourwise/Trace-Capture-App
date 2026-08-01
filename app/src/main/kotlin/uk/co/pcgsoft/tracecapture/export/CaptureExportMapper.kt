package uk.co.pcgsoft.tracecapture.export

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportDocument
import uk.co.pcgsoft.tracecapture.export.model.CaptureExportItem
import uk.co.pcgsoft.tracecapture.export.model.ExportSelectionInfo
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureExportMapper @Inject constructor(
    private val timestampFormatter: UtcTimestampFormatter
) {

    fun toDocument(
        captures: List<CaptureItem>,
        context: CaptureExportContext
    ): CaptureExportDocument {
        return CaptureExportDocument(
            schemaVersion = CaptureExportDocument.SCHEMA_VERSION,
            exportedAt = timestampFormatter.format(context.exportedAtEpochMillis),
            application = context.application,
            selection = ExportSelectionInfo(
                captureCount = captures.size,
                source = context.source.wireValue
            ),
            captures = captures.map { toExportItem(it) }
        )
    }

    fun toExportItem(capture: CaptureItem): CaptureExportItem {
        return CaptureExportItem(
            id = capture.id,
            createdAt = timestampFormatter.format(capture.createdAtEpochMillis),
            updatedAt = timestampFormatter.format(capture.updatedAtEpochMillis),
            originalContent = capture.originalContent,
            primaryUrl = capture.primaryUrl,
            detectedUrls = capture.detectedUrls.toList(),
            sourcePackageName = capture.sourcePackageName,
            sourceLabel = capture.sourceLabel,
            note = capture.note,
            captureType = typeString(capture.captureType),
            status = statusString(capture.status),
            syncStatus = syncStatusString(capture.syncStatus),
            duplicateOfId = capture.duplicateOfId,
            archivedAt = timestampFormatter.formatOrNull(capture.archivedAtEpochMillis),
            deletedAt = timestampFormatter.formatOrNull(capture.deletedAtEpochMillis)
        )
    }

    fun typeString(captureType: CaptureType): String = when (captureType) {
        CaptureType.URL -> "URL"
        CaptureType.TEXT -> "TEXT"
        CaptureType.URL_WITH_TEXT -> "URL_WITH_TEXT"
        CaptureType.MULTIPLE_URLS -> "MULTIPLE_URLS"
        CaptureType.UNKNOWN -> "UNKNOWN"
    }

    fun statusString(status: CaptureStatus): String = when (status) {
        CaptureStatus.PENDING -> "PENDING"
        CaptureStatus.REVIEWED -> "REVIEWED"
        CaptureStatus.ARCHIVED -> "ARCHIVED"
    }

    fun syncStatusString(syncStatus: SyncStatus): String = when (syncStatus) {
        SyncStatus.LOCAL_ONLY -> "LOCAL_ONLY"
        SyncStatus.QUEUED -> "QUEUED"
        SyncStatus.SYNCING -> "SYNCING"
        SyncStatus.SYNCED -> "SYNCED"
        SyncStatus.FAILED -> "FAILED"
    }

    fun typeLabel(captureType: CaptureType): String = when (captureType) {
        CaptureType.URL -> "URL"
        CaptureType.TEXT -> "Text"
        CaptureType.URL_WITH_TEXT -> "URL with text"
        CaptureType.MULTIPLE_URLS -> "Multiple URLs"
        CaptureType.UNKNOWN -> "Unknown"
    }

    fun statusLabel(status: CaptureStatus): String = when (status) {
        CaptureStatus.PENDING -> "Pending"
        CaptureStatus.REVIEWED -> "Reviewed"
        CaptureStatus.ARCHIVED -> "Archived"
    }

    fun resolveSourceLabel(capture: CaptureItem): String? {
        capture.sourceLabel?.let { return it }
        capture.primaryUrl?.let { url ->
            return try {
                URI(url).host?.removePrefix("www.") ?: url
            } catch (_: Exception) {
                url
            }
        }
        return capture.sourcePackageName
    }
}
