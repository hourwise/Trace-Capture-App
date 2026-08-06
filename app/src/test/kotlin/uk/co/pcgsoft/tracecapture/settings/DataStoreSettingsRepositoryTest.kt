package uk.co.pcgsoft.tracecapture.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun createDataStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )

    @Test
    fun defaultsWhenNoPreferencesExist() = runTest {
        val repository = DataStoreSettingsRepository(
            createDataStore(File(tempFolder.root, "empty.preferences_pb"))
        )
        assertEquals(SettingsDefaults.value, repository.settings.first())
    }

    @Test
    fun eachEnumPersistsAndReloads() = runTest {
        val repository = DataStoreSettingsRepository(
            createDataStore(File(tempFolder.root, "enums.preferences_pb"))
        )

        DefaultInboxFilter.entries.forEach { filter ->
            repository.setDefaultInboxFilter(filter)
            assertEquals(filter, repository.settings.first().defaultInboxFilter)
        }
        PreferredExportFormat.entries.forEach { format ->
            repository.setPreferredExportFormat(format)
            assertEquals(format, repository.settings.first().preferredExportFormat)
        }
        TemporaryExportRetention.entries.forEach { retention ->
            repository.setTemporaryExportRetention(retention)
            assertEquals(retention, repository.settings.first().temporaryExportRetention)
        }
    }

    @Test
    fun booleansPersistAndReload() = runTest {
        val repository = DataStoreSettingsRepository(
            createDataStore(File(tempFolder.root, "bools.preferences_pb"))
        )

        repository.setExitSelectionAfterSuccessfulExport(false)
        repository.setConfirmBeforeReset(false)

        val loaded = repository.settings.first()
        assertEquals(false, loaded.exitSelectionAfterSuccessfulExport)
        assertEquals(false, loaded.confirmBeforeReset)
    }

    @Test
    fun malformedValueFallsBackSafely() = runTest {
        val dataStore = createDataStore(File(tempFolder.root, "malformed.preferences_pb"))
        val repository = DataStoreSettingsRepository(dataStore)
        assertEquals(SettingsWriteResult.Success, repository.setDefaultInboxFilter(DefaultInboxFilter.REVIEWED))

        // Simulate a malformed value being written outside the repository contract.
        dataStore.edit {
            it[stringPreferencesKey(SettingsDefaults.KEY_DEFAULT_INBOX_FILTER)] = "not-a-real-value"
        }

        val loaded = repository.settings.first()
        assertEquals(DefaultInboxFilter.PENDING, loaded.defaultInboxFilter)
        assertEquals(PreferredExportFormat.ASK_EVERY_TIME, loaded.preferredExportFormat)
    }

    @Test
    fun unknownFutureValueFallsBackSafely() = runTest {
        val dataStore = createDataStore(File(tempFolder.root, "future.preferences_pb"))
        val repository = DataStoreSettingsRepository(dataStore)
        dataStore.edit {
            it[stringPreferencesKey(SettingsDefaults.KEY_PREFERRED_EXPORT_FORMAT)] = "future_format"
            it[stringPreferencesKey(SettingsDefaults.KEY_TEMPORARY_EXPORT_RETENTION)] = "future_retention"
        }

        val loaded = repository.settings.first()
        assertEquals(PreferredExportFormat.ASK_EVERY_TIME, loaded.preferredExportFormat)
        assertEquals(TemporaryExportRetention.TWENTY_FOUR_HOURS, loaded.temporaryExportRetention)
    }

    @Test
    fun resetClearsAllStoredValuesAtomically() = runTest {
        val repository = DataStoreSettingsRepository(
            createDataStore(File(tempFolder.root, "reset.preferences_pb"))
        )

        repository.setDefaultInboxFilter(DefaultInboxFilter.ALL)
        repository.setPreferredExportFormat(PreferredExportFormat.JSON)
        repository.setExitSelectionAfterSuccessfulExport(false)
        repository.setConfirmBeforeReset(false)

        assertEquals(SettingsWriteResult.Success, repository.resetToDefaults())
        assertEquals(SettingsDefaults.value, repository.settings.first())
    }

    @Test
    fun writeFailureReturnsTypedFailure() = runTest {
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw IOException("write failed")
            }
        }
        val repository = DataStoreSettingsRepository(failingDataStore)

        assertEquals(
            SettingsWriteResult.Failure,
            repository.setDefaultInboxFilter(DefaultInboxFilter.PENDING)
        )
        assertEquals(
            SettingsWriteResult.Failure,
            repository.setTemporaryExportRetention(TemporaryExportRetention.ONE_HOUR)
        )
        assertEquals(SettingsWriteResult.Failure, repository.resetToDefaults())
    }

    @Test
    fun readFailureEmitsSafeDefaultsWithoutCrashing() = runTest {
        val throwingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw IOException("write failed")
            }
        }
        val repository = DataStoreSettingsRepository(throwingDataStore)

        assertEquals(SettingsDefaults.value, repository.settings.first())
    }

    @Test
    fun corruptFileEmitsDefaultsWithoutCrashing() = runTest {
        val file = File(tempFolder.root, "corrupt.preferences_pb")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val repository = DataStoreSettingsRepository(createDataStore(file))

        assertEquals(SettingsDefaults.value, repository.settings.first())
    }

    @Test
    fun oneDataStoreInstanceInTests() = runTest {
        // One DataStore instance shared by two repositories on the same file.
        val dataStore = createDataStore(File(tempFolder.root, "shared.preferences_pb"))
        val repositoryA = DataStoreSettingsRepository(dataStore)
        repositoryA.setDefaultInboxFilter(DefaultInboxFilter.ARCHIVED)

        val repositoryB = DataStoreSettingsRepository(dataStore)
        assertEquals(DefaultInboxFilter.ARCHIVED, repositoryB.settings.first().defaultInboxFilter)
    }

    @Test
    fun noOrdinalPersistence() = runTest {
        val dataStore = createDataStore(File(tempFolder.root, "ordinal.preferences_pb"))
        val repository = DataStoreSettingsRepository(dataStore)
        repository.setDefaultInboxFilter(DefaultInboxFilter.REVIEWED)

        val raw = dataStore.data.first()[stringPreferencesKey(SettingsDefaults.KEY_DEFAULT_INBOX_FILTER)]
        assertEquals("reviewed", raw)
    }
}

