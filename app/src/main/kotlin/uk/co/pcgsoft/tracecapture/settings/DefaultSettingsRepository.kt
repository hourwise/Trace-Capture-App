package uk.co.pcgsoft.tracecapture.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op repository that always exposes defaults and treats writes as successful
 * no-ops. Used as a constructor default so settings-aware ViewModels remain
 * constructible without a persistence layer (tests and safety fallback).
 */
class DefaultSettingsRepository : SettingsRepository {

    override val settings: Flow<AppSettings> = flowOf(SettingsDefaults.value)

    override suspend fun setDefaultInboxFilter(filter: DefaultInboxFilter): SettingsWriteResult =
        SettingsWriteResult.Success

    override suspend fun setPreferredExportFormat(format: PreferredExportFormat): SettingsWriteResult =
        SettingsWriteResult.Success

    override suspend fun setExitSelectionAfterSuccessfulExport(enabled: Boolean): SettingsWriteResult =
        SettingsWriteResult.Success

    override suspend fun setTemporaryExportRetention(retention: TemporaryExportRetention): SettingsWriteResult =
        SettingsWriteResult.Success

    override suspend fun setConfirmBeforeReset(enabled: Boolean): SettingsWriteResult =
        SettingsWriteResult.Success

    override suspend fun resetToDefaults(): SettingsWriteResult = SettingsWriteResult.Success
}
