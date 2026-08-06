package uk.co.pcgsoft.tracecapture.settings

import kotlinx.coroutines.flow.Flow

sealed interface SettingsWriteResult {
    data object Success : SettingsWriteResult
    data object Failure : SettingsWriteResult
}

/**
 * Repository boundary for device-local settings. Implementations must expose a
 * `Flow<AppSettings>` that never crashes on read failures and must return typed
 * write results instead of leaking exceptions.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultInboxFilter(filter: DefaultInboxFilter): SettingsWriteResult
    suspend fun setPreferredExportFormat(format: PreferredExportFormat): SettingsWriteResult
    suspend fun setExitSelectionAfterSuccessfulExport(enabled: Boolean): SettingsWriteResult
    suspend fun setTemporaryExportRetention(retention: TemporaryExportRetention): SettingsWriteResult
    suspend fun setConfirmBeforeReset(enabled: Boolean): SettingsWriteResult
    suspend fun resetToDefaults(): SettingsWriteResult
}
