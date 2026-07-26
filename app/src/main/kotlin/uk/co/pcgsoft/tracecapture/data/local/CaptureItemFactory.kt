package uk.co.pcgsoft.tracecapture.data.local

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureItemFactory @Inject constructor() {

    fun create(
        originalContent: String,
        primaryUrl: String?,
        detectedUrls: List<String>,
        sourcePackageName: String?,
        sourceLabel: String?,
        note: String?,
        captureType: CaptureType
    ): CaptureItem {
        val now = currentEpochMillis()
        return CaptureItem(
            id = UUID.randomUUID().toString(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            originalContent = originalContent,
            primaryUrl = primaryUrl,
            detectedUrls = detectedUrls,
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
            note = note,
            captureType = captureType,
            status = CaptureStatus.PENDING,
            syncStatus = SyncStatus.LOCAL_ONLY,
            duplicateOfId = null,
            archivedAtEpochMillis = null,
            deletedAtEpochMillis = null
        )
    }

    internal fun currentEpochMillis(): Long = System.currentTimeMillis()
}
