package uk.co.pcgsoft.tracecapture.settings

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = SettingsDefaults.value,
    val activeDialog: SettingsDialog? = null,
    val actionInProgress: SettingsAction? = null,
    val message: SettingsMessage? = null
)

sealed interface SettingsDialog {
    data object DefaultInboxFilter : SettingsDialog
    data object PreferredExportFormat : SettingsDialog
    data object TemporaryExportRetention : SettingsDialog
    data object DeleteTemporaryFiles : SettingsDialog
    data object ResetConfirmation : SettingsDialog
    data object PrivacyAndData : SettingsDialog
    data object Licences : SettingsDialog
}

enum class SettingsAction {
    SAVE_PREFERENCE,
    DELETE_TEMPORARY_FILES,
    RESET
}

sealed interface SettingsMessage {
    data object SettingSaved : SettingsMessage
    data object SaveFailed : SettingsMessage
    data object ResetComplete : SettingsMessage
    data object ResetFailed : SettingsMessage
    data class TemporaryFilesDeleted(val count: Int) : SettingsMessage
    data object TemporaryFileDeletionFailed : SettingsMessage
}
