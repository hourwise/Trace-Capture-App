package uk.co.pcgsoft.tracecapture.capture

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun provideUrlExtractor(impl: UrlExtractorImpl): UrlExtractor = impl

    @Provides
    @Singleton
    fun provideShareIntentParser(impl: ShareIntentParserImpl): ShareIntentParser = impl

    @Provides
    @Singleton
    fun provideSharedCaptureProcessor(impl: SharedCaptureProcessorImpl): SharedCaptureProcessor = impl

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager =
        context.packageManager

    @Provides
    @Singleton
    fun provideSourceApplicationResolver(impl: SourceApplicationResolverImpl): SourceApplicationResolver =
        impl
}
