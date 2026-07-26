package uk.co.pcgsoft.tracecapture.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

internal class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromUrlList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toUrlList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromCaptureType(value: CaptureType): String = value.name

    @TypeConverter
    fun toCaptureType(value: String): CaptureType = CaptureType.valueOf(value)

    @TypeConverter
    fun fromCaptureStatus(value: CaptureStatus): String = value.name

    @TypeConverter
    fun toCaptureStatus(value: String): CaptureStatus = CaptureStatus.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
