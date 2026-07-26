package uk.co.pcgsoft.tracecapture.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

@Entity(tableName = "capture_items")
data class CaptureItemEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,

    @ColumnInfo(name = "original_content")
    val originalContent: String,

    @ColumnInfo(name = "primary_url")
    val primaryUrl: String?,

    @ColumnInfo(name = "detected_urls")
    val detectedUrlsJson: String?,

    @ColumnInfo(name = "source_package_name")
    val sourcePackageName: String?,

    @ColumnInfo(name = "source_label")
    val sourceLabel: String?,

    val note: String?,

    @ColumnInfo(name = "capture_type")
    val captureType: CaptureType,

    val status: CaptureStatus,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus,

    @ColumnInfo(name = "duplicate_of_id")
    val duplicateOfId: String?,

    @ColumnInfo(name = "archived_at")
    val archivedAtEpochMillis: Long?,

    @ColumnInfo(name = "deleted_at")
    val deletedAtEpochMillis: Long?
)
