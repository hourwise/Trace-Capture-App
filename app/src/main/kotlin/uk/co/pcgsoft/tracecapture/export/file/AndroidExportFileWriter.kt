package uk.co.pcgsoft.tracecapture.export.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : ExportFileWriter {

    internal var streamOpener: (Uri) -> OutputStream? = { uri ->
        try {
            context.contentResolver.openOutputStream(uri)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun write(uri: Uri, content: ByteArray): FileWriteResult =
        withContext(Dispatchers.IO) {
            val output = streamOpener(uri)
            if (output == null) {
                FileWriteResult.Failure()
            } else {
                try {
                    output.use { it.write(content) }
                    FileWriteResult.Success
                } catch (_: Exception) {
                    FileWriteResult.Failure()
                }
            }
        }
}
