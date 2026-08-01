package uk.co.pcgsoft.tracecapture.export

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.co.pcgsoft.tracecapture.BuildConfig
import uk.co.pcgsoft.tracecapture.R
import uk.co.pcgsoft.tracecapture.export.file.AndroidExportFileWriter
import uk.co.pcgsoft.tracecapture.export.file.ExportFileWriter
import uk.co.pcgsoft.tracecapture.export.model.ExportApplicationInfo
import uk.co.pcgsoft.tracecapture.export.share.AndroidExportShareFileManager
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindExportCoordinator(impl: DefaultExportCoordinator): ExportCoordinator

    @Binds
    @Singleton
    abstract fun bindExportFileWriter(impl: AndroidExportFileWriter): ExportFileWriter

    @Binds
    @Singleton
    abstract fun bindExportShareFileManager(
        impl: AndroidExportShareFileManager
    ): ExportShareFileManager

    @Binds
    @Singleton
    abstract fun bindExportClock(impl: SystemExportClock): ExportClock

    companion object {

        @Provides
        @Singleton
        fun provideExportApplicationInfo(
            @ApplicationContext context: Context
        ): ExportApplicationInfo {
            return ExportApplicationInfo(
                name = context.getString(R.string.app_name),
                packageName = context.packageName,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong()
            )
        }

        @Provides
        @Singleton
        fun provideExportLimits(): ExportLimits = ExportLimits()
    }
}
