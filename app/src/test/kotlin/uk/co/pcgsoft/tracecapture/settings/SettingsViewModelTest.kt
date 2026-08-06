package uk.co.pcgsoft.tracecapture.settings

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSettingsRepository()
    private val shareManager = mockk<ExportShareFileManager>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SettingsViewModel(repository, shareManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingStateUntilFirstEmission() = runTest {
        // A settings flow that emits only after a gate lets us observe the loading
        // state before the first emission arrives.
        val gate = CompletableDeferred<AppSettings>()
        val gatedRepo = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flow { emit(gate.await()) }
            override suspend fun setDefaultInboxFilter(filter: DefaultInboxFilter) = SettingsWriteResult.Success
            override suspend fun setPreferredExportFormat(format: PreferredExportFormat) = SettingsWriteResult.Success
            override suspend fun setExitSelectionAfterSuccessfulExport(enabled: Boolean) = SettingsWriteResult.Success
            override suspend fun setTemporaryExportRetention(retention: TemporaryExportRetention) = SettingsWriteResult.Success
            override suspend fun setConfirmBeforeReset(enabled: Boolean) = SettingsWriteResult.Success
            override suspend fun resetToDefaults() = SettingsWriteResult.Success
        }
        val vm = SettingsViewModel(gatedRepo, shareManager)
        assertTrue(vm.uiState.value.isLoading)
        gate.complete(SettingsDefaults.value)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(SettingsDefaults.value, vm.uiState.value.settings)
    }

    @Test
    fun firstSettingsEmissionPopulatesState() = runTest {
        repository.emit(SettingsDefaults.value.copy(defaultInboxFilter = DefaultInboxFilter.ALL))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(DefaultInboxFilter.ALL, viewModel.uiState.value.settings.defaultInboxFilter)
    }

    @Test
    fun openAndDismissEachDialog() = runTest {
        advanceUntilIdle()

        viewModel.onDefaultInboxFilterClick()
        assertEquals(SettingsDialog.DefaultInboxFilter, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()
        assertNull(viewModel.uiState.value.activeDialog)

        viewModel.onPreferredExportFormatClick()
        assertEquals(SettingsDialog.PreferredExportFormat, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()

        viewModel.onTemporaryExportRetentionClick()
        assertEquals(SettingsDialog.TemporaryExportRetention, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()

        viewModel.onDeleteTemporaryFilesClick()
        assertEquals(SettingsDialog.DeleteTemporaryFiles, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()

        viewModel.onPrivacyAndDataClick()
        assertEquals(SettingsDialog.PrivacyAndData, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()

        viewModel.onLicencesClick()
        assertEquals(SettingsDialog.Licences, viewModel.uiState.value.activeDialog)
        viewModel.onDialogDismissed()
    }

    @Test
    fun selectDefaultInboxFilterSavesAndClosesDialog() = runTest {
        advanceUntilIdle()
        viewModel.onDefaultInboxFilterClick()
        viewModel.onDefaultInboxFilterSelected(DefaultInboxFilter.REVIEWED)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeDialog)
        assertEquals(DefaultInboxFilter.REVIEWED, viewModel.uiState.value.settings.defaultInboxFilter)
        assertEquals(SettingsMessage.SettingSaved, viewModel.uiState.value.message)
    }

    @Test
    fun selectPreferredExportFormatSaves() = runTest {
        advanceUntilIdle()
        viewModel.onPreferredExportFormatClick()
        viewModel.onPreferredExportFormatSelected(PreferredExportFormat.JSON)
        advanceUntilIdle()

        assertEquals(PreferredExportFormat.JSON, viewModel.uiState.value.settings.preferredExportFormat)
        assertEquals(SettingsMessage.SettingSaved, viewModel.uiState.value.message)
    }

    @Test
    fun selectTemporaryRetentionSaves() = runTest {
        advanceUntilIdle()
        viewModel.onTemporaryExportRetentionClick()
        viewModel.onTemporaryExportRetentionSelected(TemporaryExportRetention.ONE_HOUR)
        advanceUntilIdle()

        assertEquals(TemporaryExportRetention.ONE_HOUR, viewModel.uiState.value.settings.temporaryExportRetention)
    }

    @Test
    fun saveFailureKeepsPreviousValueAndShowsTypedMessage() = runTest {
        advanceUntilIdle()
        repository.failWrites = true

        viewModel.onDefaultInboxFilterClick()
        viewModel.onDefaultInboxFilterSelected(DefaultInboxFilter.REVIEWED)
        advanceUntilIdle()

        assertEquals(SettingsMessage.SaveFailed, viewModel.uiState.value.message)
        // Previous known value is retained (default PENDING was never changed).
        assertEquals(DefaultInboxFilter.PENDING, viewModel.uiState.value.settings.defaultInboxFilter)
    }

    @Test
    fun switchUpdatePersists() = runTest {
        advanceUntilIdle()
        viewModel.onExitSelectionToggle(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.settings.exitSelectionAfterSuccessfulExport)

        viewModel.onConfirmBeforeResetToggle(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.settings.confirmBeforeReset)
    }

    @Test
    fun repeatedActionGuard() = runTest {
        advanceUntilIdle()
        // First toggle sets actionInProgress synchronously; the second is ignored.
        viewModel.onExitSelectionToggle(false)
        viewModel.onExitSelectionToggle(true)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.settings.exitSelectionAfterSuccessfulExport)
    }

    @Test
    fun resetConfirmationEnabledShowsDialogThenResets() = runTest {
        advanceUntilIdle()
        repository.setDefaultInboxFilter(DefaultInboxFilter.ALL)
        advanceUntilIdle()

        viewModel.onResetClick()
        assertEquals(SettingsDialog.ResetConfirmation, viewModel.uiState.value.activeDialog)

        viewModel.onResetConfirmed()
        advanceUntilIdle()

        assertEquals(SettingsDefaults.value, viewModel.uiState.value.settings)
        assertEquals(SettingsMessage.ResetComplete, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.activeDialog)
    }

    @Test
    fun resetConfirmationDisabledResetsImmediately() = runTest {
        repository.emit(SettingsDefaults.value.copy(confirmBeforeReset = false))
        advanceUntilIdle()
        repository.setDefaultInboxFilter(DefaultInboxFilter.ALL)
        advanceUntilIdle()

        viewModel.onResetClick()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeDialog)
        assertEquals(SettingsDefaults.value, viewModel.uiState.value.settings)
        assertEquals(SettingsMessage.ResetComplete, viewModel.uiState.value.message)
    }

    @Test
    fun resetFailureShowsTypedMessage() = runTest {
        advanceUntilIdle()
        repository.failWrites = true

        viewModel.onResetClick()
        viewModel.onResetConfirmed()
        advanceUntilIdle()

        assertEquals(SettingsMessage.ResetFailed, viewModel.uiState.value.message)
    }

    @Test
    fun deleteTemporaryFilesConfirmationThenDeletion() = runTest {
        coEvery { shareManager.deleteAllTemporaryExports() } returns 3
        advanceUntilIdle()
        viewModel.onDeleteTemporaryFilesClick()
        assertEquals(SettingsDialog.DeleteTemporaryFiles, viewModel.uiState.value.activeDialog)

        viewModel.onDeleteTemporaryFilesConfirmed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeDialog)
        assertEquals(SettingsMessage.TemporaryFilesDeleted(3), viewModel.uiState.value.message)
    }

    @Test
    fun deleteTemporaryFilesZeroCount() = runTest {
        coEvery { shareManager.deleteAllTemporaryExports() } returns 0
        advanceUntilIdle()
        viewModel.onDeleteTemporaryFilesClick()
        viewModel.onDeleteTemporaryFilesConfirmed()
        advanceUntilIdle()

        assertEquals(SettingsMessage.TemporaryFilesDeleted(0), viewModel.uiState.value.message)
    }

    @Test
    fun deleteTemporaryFilesFailureShowsTypedMessage() = runTest {
        coEvery { shareManager.deleteAllTemporaryExports() } throws RuntimeException("io")
        advanceUntilIdle()
        viewModel.onDeleteTemporaryFilesClick()
        viewModel.onDeleteTemporaryFilesConfirmed()
        advanceUntilIdle()

        assertEquals(SettingsMessage.TemporaryFileDeletionFailed, viewModel.uiState.value.message)
    }

    @Test
    fun messageConsumptionClearsMessage() = runTest {
        advanceUntilIdle()
        viewModel.onExitSelectionToggle(false)
        advanceUntilIdle()
        assertEquals(SettingsMessage.SettingSaved, viewModel.uiState.value.message)

        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun noRawExceptionExposure() = runTest {
        // Every failure path surfaces a typed message, never an exception.
        repository.failWrites = true
        advanceUntilIdle()

        viewModel.onExitSelectionToggle(false)
        advanceUntilIdle()
        assertEquals(SettingsMessage.SaveFailed, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.actionInProgress)
    }

    @Test
    fun actionInProgressClearedAfterWrite() = runTest {
        advanceUntilIdle()
        viewModel.onExitSelectionToggle(false)
        assertEquals(SettingsAction.SAVE_PREFERENCE, viewModel.uiState.value.actionInProgress)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.actionInProgress)
    }
}
