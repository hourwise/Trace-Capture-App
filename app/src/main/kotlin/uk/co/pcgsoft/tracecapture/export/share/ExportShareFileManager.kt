package uk.co.pcgsoft.tracecapture.export.share

import android.net.Uri

data class PreparedShareExport(
    val contentUri: Uri,
    val mimeType: String,
    val fileName: String
)

interface ExportShareFileManager {
    suspend fun prepareShareExport(
        content: ByteArray,
        mimeType: String,
        fileName: String
    ): PreparedShareExport

    /** Deletes all dedicated temporary shared-export files; returns the count removed. */
    suspend fun deleteAllTemporaryExports(): Int
}
