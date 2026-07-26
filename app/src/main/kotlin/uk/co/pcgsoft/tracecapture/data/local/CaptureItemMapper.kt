package uk.co.pcgsoft.tracecapture.data.local

import uk.co.pcgsoft.tracecapture.data.local.util.parseUrlList
import uk.co.pcgsoft.tracecapture.data.local.util.toUrlListJson
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

internal fun CaptureItemEntity.toDomain(): CaptureItem {
    return CaptureItem(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        originalContent = originalContent,
        primaryUrl = primaryUrl,
        detectedUrls = parseUrlList(detectedUrlsJson),
        sourcePackageName = sourcePackageName,
        sourceLabel = sourceLabel,
        note = note,
        captureType = captureType,
        status = status,
        syncStatus = syncStatus,
        duplicateOfId = duplicateOfId,
        archivedAtEpochMillis = archivedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )
}

internal fun CaptureItem.toEntity(): CaptureItemEntity {
    return CaptureItemEntity(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        originalContent = originalContent,
        primaryUrl = primaryUrl,
        detectedUrlsJson = toUrlListJson(detectedUrls),
        sourcePackageName = sourcePackageName,
        sourceLabel = sourceLabel,
        note = note,
        captureType = captureType,
        status = status,
        syncStatus = syncStatus,
        duplicateOfId = duplicateOfId,
        archivedAtEpochMillis = archivedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis
    )
}

internal fun List<CaptureItemEntity>.toDomain(): List<CaptureItem> = map { it.toDomain() }
internal fun List<CaptureItem>.toEntity(): List<CaptureItemEntity> = map { it.toEntity() }
