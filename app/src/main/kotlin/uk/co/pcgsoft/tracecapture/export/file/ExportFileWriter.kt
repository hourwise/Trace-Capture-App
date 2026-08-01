package uk.co.pcgsoft.tracecapture.export.file

import android.net.Uri

sealed interface FileWriteResult {
    data object Success : FileWriteResult
    data class Failure(val reason: String? = null) : FileWriteResult
}

interface ExportFileWriter {
    suspend fun write(uri: Uri, content: ByteArray): FileWriteResult
}
