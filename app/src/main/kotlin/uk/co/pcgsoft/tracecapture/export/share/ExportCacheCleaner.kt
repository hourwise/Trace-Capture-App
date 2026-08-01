package uk.co.pcgsoft.tracecapture.export.share

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportCacheCleaner @Inject constructor() {

    companion object {
        const val EXPORT_FILE_PREFIX = "trace-capture-"
        const val DEFAULT_RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }

    fun cleanup(
        directory: File,
        maxAgeMillis: Long = DEFAULT_RETENTION_MILLIS,
        now: Long = System.currentTimeMillis()
    ): Int {
        if (!directory.exists() || !directory.isDirectory) return 0
        val cutoff = now - maxAgeMillis
        var deleted = 0
        directory.listFiles { file ->
            file.isFile && file.name.startsWith(EXPORT_FILE_PREFIX)
        }?.forEach { file ->
            if (file.lastModified() < cutoff && file.delete()) {
                deleted++
            }
        }
        return deleted
    }
}
