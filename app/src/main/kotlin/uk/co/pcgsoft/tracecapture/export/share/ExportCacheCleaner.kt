package uk.co.pcgsoft.tracecapture.export.share

import kotlinx.coroutines.flow.first
import uk.co.pcgsoft.tracecapture.settings.SettingsRepository
import uk.co.pcgsoft.tracecapture.settings.retentionMillis
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cleans the dedicated temporary shared-export cache directory. Only files with
 * the [EXPORT_FILE_PREFIX] are ever considered, so unrelated cache files and
 * user-saved documents are never touched.
 */
@Singleton
class ExportCacheCleaner @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    companion object {
        const val EXPORT_FILE_PREFIX = "trace-capture-"
        const val DEFAULT_RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }

    /**
     * Deletes dedicated export files older than [maxAgeMillis]. Pure and
     * deterministic so it can be tested with an injected clock.
     */
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

    /** Cleanup using the currently persisted retention period; falls back to 24h. */
    suspend fun cleanupWithCurrentRetention(
        directory: File,
        now: Long = System.currentTimeMillis()
    ): Int {
        val retentionMillis = try {
            settingsRepository.settings.first().temporaryExportRetention.retentionMillis
        } catch (_: Exception) {
            DEFAULT_RETENTION_MILLIS
        }
        return cleanup(directory, retentionMillis, now)
    }

    /** Deletes every dedicated temporary export file in [directory]. */
    fun deleteAll(directory: File): Int {
        if (!directory.exists() || !directory.isDirectory) return 0
        var deleted = 0
        directory.listFiles { file ->
            file.isFile && file.name.startsWith(EXPORT_FILE_PREFIX)
        }?.forEach { file ->
            if (file.delete()) deleted++
        }
        return deleted
    }
}
