package uk.co.pcgsoft.tracecapture.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportShareFileManager: ExportShareFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(isLoading = false, settings = settings) }
            }
        }
    }

    fun onDefaultInboxFilterClick() = openDialog(SettingsDialog.DefaultInboxFilter)

    fun onPreferredExportFormatClick() = openDialog(SettingsDialog.PreferredExportFormat)

    fun onTemporaryExportRetentionClick() = openDialog(SettingsDialog.TemporaryExportRetention)

    fun onDeleteTemporaryFilesClick() = openDialog(SettingsDialog.DeleteTemporaryFiles)

    fun onPrivacyAndDataClick() = openDialog(SettingsDialog.PrivacyAndData)

    fun onLicencesClick() = openDialog(SettingsDialog.Licences)

    fun onResetClick() {
        if (_uiState.value.actionInProgress != null) return
        val state = _uiState.value
        if (state.settings.confirmBeforeReset) {
            _uiState.update { it.copy(activeDialog = SettingsDialog.ResetConfirmation) }
        } else {
            reset()
        }
    }

    fun onExitSelectionToggle(enabled: Boolean) {
        persist(SettingsAction.SAVE_PREFERENCE) {
            settingsRepository.setExitSelectionAfterSuccessfulExport(enabled)
        }
    }

    fun onConfirmBeforeResetToggle(enabled: Boolean) {
        persist(SettingsAction.SAVE_PREFERENCE) {
            settingsRepository.setConfirmBeforeReset(enabled)
        }
    }

    fun onDefaultInboxFilterSelected(filter: DefaultInboxFilter) {
        _uiState.update { it.copy(activeDialog = null) }
        persist(SettingsAction.SAVE_PREFERENCE) {
            settingsRepository.setDefaultInboxFilter(filter)
        }
    }

    fun onPreferredExportFormatSelected(format: PreferredExportFormat) {
        _uiState.update { it.copy(activeDialog = null) }
        persist(SettingsAction.SAVE_PREFERENCE) {
            settingsRepository.setPreferredExportFormat(format)
        }
    }

    fun onTemporaryExportRetentionSelected(retention: TemporaryExportRetention) {
        _uiState.update { it.copy(activeDialog = null) }
        persist(SettingsAction.SAVE_PREFERENCE) {
            settingsRepository.setTemporaryExportRetention(retention)
        }
    }

    fun onDeleteTemporaryFilesConfirmed() {
        _uiState.update { it.copy(activeDialog = null) }
        val state = _uiState.value
        if (state.actionInProgress == SettingsAction.DELETE_TEMPORARY_FILES) return
        _uiState.update { it.copy(actionInProgress = SettingsAction.DELETE_TEMPORARY_FILES) }
        viewModelScope.launch {
            val message = try {
                val count = exportShareFileManager.deleteAllTemporaryExports()
                SettingsMessage.TemporaryFilesDeleted(count)
            } catch (_: Exception) {
                SettingsMessage.TemporaryFileDeletionFailed
            }
            _uiState.update { it.copy(actionInProgress = null, message = message) }
        }
    }

    fun onResetConfirmed() {
        _uiState.update { it.copy(activeDialog = null) }
        reset()
    }

    fun onDialogDismissed() {
        if (_uiState.value.actionInProgress != null) return
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun openDialog(dialog: SettingsDialog) {
        if (_uiState.value.actionInProgress != null) return
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    private fun reset() {
        val state = _uiState.value
        if (state.actionInProgress == SettingsAction.RESET) return
        _uiState.update { it.copy(actionInProgress = SettingsAction.RESET) }
        viewModelScope.launch {
            val result = settingsRepository.resetToDefaults()
            _uiState.update {
                it.copy(
                    actionInProgress = null,
                    message = if (result is SettingsWriteResult.Success) {
                        SettingsMessage.ResetComplete
                    } else {
                        SettingsMessage.ResetFailed
                    }
                )
            }
        }
    }

    private fun persist(action: SettingsAction, write: suspend () -> SettingsWriteResult) {
        val state = _uiState.value
        if (state.actionInProgress != null) return
        _uiState.update { it.copy(actionInProgress = action) }
        viewModelScope.launch {
            val result = write()
            _uiState.update {
                it.copy(
                    actionInProgress = null,
                    message = if (result is SettingsWriteResult.Success) {
                        SettingsMessage.SettingSaved
                    } else {
                        SettingsMessage.SaveFailed
                    }
                )
            }
        }
    }
}
