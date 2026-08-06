package uk.co.pcgsoft.tracecapture.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                defaultInboxFilter = DefaultInboxFilter.fromPersisted(
                    prefs[KEY_DEFAULT_INBOX_FILTER]
                ),
                preferredExportFormat = PreferredExportFormat.fromPersisted(
                    prefs[KEY_PREFERRED_EXPORT_FORMAT]
                ),
                exitSelectionAfterSuccessfulExport =
                    prefs[KEY_EXIT_SELECTION_AFTER_SUCCESS]
                        ?: SettingsDefaults.value.exitSelectionAfterSuccessfulExport,
                temporaryExportRetention = TemporaryExportRetention.fromPersisted(
                    prefs[KEY_TEMPORARY_EXPORT_RETENTION]
                ),
                confirmBeforeReset =
                    prefs[KEY_CONFIRM_BEFORE_RESET]
                        ?: SettingsDefaults.value.confirmBeforeReset
            )
        }

    override suspend fun setDefaultInboxFilter(filter: DefaultInboxFilter): SettingsWriteResult =
        write { it[KEY_DEFAULT_INBOX_FILTER] = filter.persistedValue }

    override suspend fun setPreferredExportFormat(format: PreferredExportFormat): SettingsWriteResult =
        write { it[KEY_PREFERRED_EXPORT_FORMAT] = format.persistedValue }

    override suspend fun setExitSelectionAfterSuccessfulExport(enabled: Boolean): SettingsWriteResult =
        write { it[KEY_EXIT_SELECTION_AFTER_SUCCESS] = enabled }

    override suspend fun setTemporaryExportRetention(retention: TemporaryExportRetention): SettingsWriteResult =
        write { it[KEY_TEMPORARY_EXPORT_RETENTION] = retention.persistedValue }

    override suspend fun setConfirmBeforeReset(enabled: Boolean): SettingsWriteResult =
        write { it[KEY_CONFIRM_BEFORE_RESET] = enabled }

    override suspend fun resetToDefaults(): SettingsWriteResult =
        write { it.clear() }

    private suspend fun write(
        block: (MutablePreferences) -> Unit
    ): SettingsWriteResult = try {
        dataStore.edit { prefs -> block(prefs) }
        SettingsWriteResult.Success
    } catch (_: Exception) {
        SettingsWriteResult.Failure
    }

    private companion object {
        val KEY_DEFAULT_INBOX_FILTER = stringPreferencesKey(SettingsDefaults.KEY_DEFAULT_INBOX_FILTER)
        val KEY_PREFERRED_EXPORT_FORMAT = stringPreferencesKey(SettingsDefaults.KEY_PREFERRED_EXPORT_FORMAT)
        val KEY_EXIT_SELECTION_AFTER_SUCCESS = booleanPreferencesKey(SettingsDefaults.KEY_EXIT_SELECTION_AFTER_SUCCESS)
        val KEY_TEMPORARY_EXPORT_RETENTION = stringPreferencesKey(SettingsDefaults.KEY_TEMPORARY_EXPORT_RETENTION)
        val KEY_CONFIRM_BEFORE_RESET = booleanPreferencesKey(SettingsDefaults.KEY_CONFIRM_BEFORE_RESET)
    }
}
