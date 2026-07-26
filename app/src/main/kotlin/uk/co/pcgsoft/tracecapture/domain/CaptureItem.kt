package uk.co.pcgsoft.tracecapture.domain

data class CaptureItem(
    val id: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val originalContent: String,
    val primaryUrl: String?,
    val detectedUrls: List<String>,
    val sourcePackageName: String?,
    val sourceLabel: String?,
    val note: String?,
    val captureType: CaptureType,
    val status: CaptureStatus,
    val syncStatus: SyncStatus,
    val duplicateOfId: String?,
    val archivedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?
)

enum class CaptureType {
    URL,
    TEXT,
    URL_WITH_TEXT,
    MULTIPLE_URLS,
    UNKNOWN
}

enum class CaptureStatus {
    PENDING,
    REVIEWED,
    ARCHIVED
}

enum class SyncStatus {
    LOCAL_ONLY,
    QUEUED,
    SYNCING,
    SYNCED,
    FAILED
}
