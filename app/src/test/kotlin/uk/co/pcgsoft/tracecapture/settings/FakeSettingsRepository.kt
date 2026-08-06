package uk.co.pcgsoft.tracecapture.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] fake for tests. Writes update the exposed flow
 * and return [SettingsWriteResult.Success] unless [failWrites] is enabled.
 */
class FakeSettingsRepository(
    initial: AppSettings = SettingsDefaults.value
) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = _settings

    var failWrites: Boolean = false

    fun emit(settings: AppSettings) {
        _settings.value = settings
    }

    fun current(): AppSettings = _settings.value

    override suspend fun setDefaultInboxFilter(filter: DefaultInboxFilter): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.update { it.copy(defaultInboxFilter = filter) }
            SettingsWriteResult.Success
        }

    override suspend fun setPreferredExportFormat(format: PreferredExportFormat): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.update { it.copy(preferredExportFormat = format) }
            SettingsWriteResult.Success
        }

    override suspend fun setExitSelectionAfterSuccessfulExport(enabled: Boolean): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.update { it.copy(exitSelectionAfterSuccessfulExport = enabled) }
            SettingsWriteResult.Success
        }

    override suspend fun setTemporaryExportRetention(retention: TemporaryExportRetention): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.update { it.copy(temporaryExportRetention = retention) }
            SettingsWriteResult.Success
        }

    override suspend fun setConfirmBeforeReset(enabled: Boolean): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.update { it.copy(confirmBeforeReset = enabled) }
            SettingsWriteResult.Success
        }

    override suspend fun resetToDefaults(): SettingsWriteResult =
        if (failWrites) SettingsWriteResult.Failure
        else {
            _settings.value = SettingsDefaults.value
            SettingsWriteResult.Success
        }
}
