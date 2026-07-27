package uk.co.pcgsoft.tracecapture

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import uk.co.pcgsoft.tracecapture.data.DataModule
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.local.TraceCaptureDatabase
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.data.repository.RoomCaptureRepository
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class]
)
object TestDataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TraceCaptureDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            TraceCaptureDatabase::class.java
        ).allowMainThreadQueries().build()
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
