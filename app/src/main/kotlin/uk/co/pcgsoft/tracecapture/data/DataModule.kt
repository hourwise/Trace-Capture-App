package uk.co.pcgsoft.tracecapture.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.local.TraceCaptureDatabase
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.data.repository.RoomCaptureRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TraceCaptureDatabase {
        return Room.databaseBuilder(
            context,
            TraceCaptureDatabase::class.java,
            "trace-capture.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideCaptureItemDao(database: TraceCaptureDatabase): CaptureItemDao {
        return database.captureItemDao()
    }

    @Provides
    @Singleton
    fun provideCaptureRepository(repository: RoomCaptureRepository): CaptureRepository {
        return repository
    }
}
