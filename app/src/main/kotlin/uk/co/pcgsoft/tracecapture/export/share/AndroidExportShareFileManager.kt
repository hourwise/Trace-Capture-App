package uk.co.pcgsoft.tracecapture.export.share

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidExportShareFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheCleaner: ExportCacheCleaner
) : ExportShareFileManager {

    override suspend fun prepareShareExport(
        content: ByteArray,
        mimeType: String,
        fileName: String
    ): PreparedShareExport = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, EXPORT_CACHE_DIRECTORY)
        directory.mkdirs()
        cacheCleaner.cleanup(directory)
        val file = File(directory, fileName)
        file.writeBytes(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        PreparedShareExport(
            contentUri = uri,
            mimeType = mimeType,
            fileName = fileName
        )
    }

    private companion object {
        const val EXPORT_CACHE_DIRECTORY = "exports"
    }
}
