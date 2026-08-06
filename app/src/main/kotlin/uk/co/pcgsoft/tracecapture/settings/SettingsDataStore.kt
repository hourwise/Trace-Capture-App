package uk.co.pcgsoft.tracecapture.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single application-scoped DataStore instance for the settings file.
 * The delegate is file-level so only one instance exists per process.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SettingsDefaults.DATASTORE_FILE_NAME
)
