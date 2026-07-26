package uk.co.pcgsoft.tracecapture.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CaptureItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TraceCaptureDatabase : RoomDatabase() {

    abstract fun captureItemDao(): CaptureItemDao
}
