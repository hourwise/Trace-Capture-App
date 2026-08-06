package uk.co.pcgsoft.tracecapture.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.pcgsoft.tracecapture.BuildConfig
import uk.co.pcgsoft.tracecapture.R

data class SettingsAppInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val packageName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appInfo = SettingsAppInfo(
        appName = context.getString(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        packageName = BuildConfig.APPLICATION_ID
    )

    SettingsContent(
        uiState = uiState,
        appInfo = appInfo,
        onNavigateBack = onNavigateBack,
        onDefaultInboxFilterClick = viewModel::onDefaultInboxFilterClick,
        onPreferredExportFormatClick = viewModel::onPreferredExportFormatClick,
        onTemporaryExportRetentionClick = viewModel::onTemporaryExportRetentionClick,
        onDeleteTemporaryFilesClick = viewModel::onDeleteTemporaryFilesClick,
        onPrivacyAndDataClick = viewModel::onPrivacyAndDataClick,
        onLicencesClick = viewModel::onLicencesClick,
        onResetClick = viewModel::onResetClick,
        onExitSelectionToggle = viewModel::onExitSelectionToggle,
        onConfirmBeforeResetToggle = viewModel::onConfirmBeforeResetToggle,
        onDefaultInboxFilterSelected = viewModel::onDefaultInboxFilterSelected,
        onPreferredExportFormatSelected = viewModel::onPreferredExportFormatSelected,
        onTemporaryExportRetentionSelected = viewModel::onTemporaryExportRetentionSelected,
        onDeleteTemporaryFilesConfirmed = viewModel::onDeleteTemporaryFilesConfirmed,
        onResetConfirmed = viewModel::onResetConfirmed,
        onDialogDismissed = viewModel::onDialogDismissed,
        onMessageShown = viewModel::onMessageShown,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    appInfo: SettingsAppInfo,
    onNavigateBack: () -> Unit,
    onDefaultInboxFilterClick: () -> Unit,
    onPreferredExportFormatClick: () -> Unit,
    onTemporaryExportRetentionClick: () -> Unit,
    onDeleteTemporaryFilesClick: () -> Unit,
    onPrivacyAndDataClick: () -> Unit,
    onLicencesClick: () -> Unit,
    onResetClick: () -> Unit,
    onExitSelectionToggle: (Boolean) -> Unit,
    onConfirmBeforeResetToggle: (Boolean) -> Unit,
    onDefaultInboxFilterSelected: (DefaultInboxFilter) -> Unit,
    onPreferredExportFormatSelected: (PreferredExportFormat) -> Unit,
    onTemporaryExportRetentionSelected: (TemporaryExportRetention) -> Unit,
    onDeleteTemporaryFilesConfirmed: () -> Unit,
    onResetConfirmed: () -> Unit,
    onDialogDismissed: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            val text = when (message) {
                SettingsMessage.SettingSaved -> context.getString(R.string.settings_saved)
                SettingsMessage.SaveFailed -> context.getString(R.string.settings_save_failed)
                SettingsMessage.ResetComplete -> context.getString(R.string.settings_reset_complete)
                SettingsMessage.ResetFailed -> context.getString(R.string.settings_reset_failed)
                is SettingsMessage.TemporaryFilesDeleted -> if (message.count == 0) {
                    context.getString(R.string.settings_no_temporary_files)
                } else {
                    context.getString(R.string.settings_temporary_files_deleted)
                }
                SettingsMessage.TemporaryFileDeletionFailed ->
                    context.getString(R.string.settings_temporary_file_deletion_failed)
            }
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val settings = uiState.settings
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(key = "capture-and-inbox-header") {
                    SettingsSectionHeader(stringResource(R.string.settings_capture_and_inbox))
                }
                item(key = "default-inbox-filter") {
                    SettingsRow(
                        label = stringResource(R.string.settings_default_inbox_filter),
                        summary = stringResource(settings.defaultInboxFilter.labelRes()),
                        onClick = onDefaultInboxFilterClick
                    )
                }

                item(key = "export-header") {
                    SettingsSectionHeader(stringResource(R.string.settings_export))
                }
                item(key = "preferred-export-format") {
                    SettingsRow(
                        label = stringResource(R.string.settings_preferred_export_format),
                        summary = stringResource(settings.preferredExportFormat.labelRes()),
                        onClick = onPreferredExportFormatClick
                    )
                }
                item(key = "exit-selection-after-success") {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_exit_selection_after_successful_export),
                        checked = settings.exitSelectionAfterSuccessfulExport,
                        onCheckedChange = onExitSelectionToggle
                    )
                }
                item(key = "temporary-export-retention") {
                    SettingsRow(
                        label = stringResource(R.string.settings_temporary_export_retention),
                        summary = stringResource(settings.temporaryExportRetention.labelRes()),
                        onClick = onTemporaryExportRetentionClick
                    )
                }

                item(key = "data-and-privacy-header") {
                    SettingsSectionHeader(stringResource(R.string.settings_data_and_privacy))
                }
                item(key = "privacy-summary") {
                    Text(
                        text = stringResource(R.string.settings_local_storage_info),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_no_automatic_upload),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                item(key = "manage-temporary-files") {
                    SettingsRow(
                        label = stringResource(R.string.settings_manage_temporary_export_files),
                        summary = stringResource(R.string.settings_delete_temporary_files_now),
                        onClick = onDeleteTemporaryFilesClick
                    )
                }

                item(key = "app-header") {
                    SettingsSectionHeader(stringResource(R.string.settings_app))
                }
                item(key = "app-version") {
                    SettingsRow(
                        label = stringResource(R.string.settings_version),
                        summary = context.getString(
                            R.string.settings_version_value,
                            appInfo.versionName,
                            appInfo.versionCode
                        )
                    )
                }
                item(key = "app-build") {
                    SettingsRow(
                        label = stringResource(R.string.settings_build),
                        summary = appInfo.versionCode.toString()
                    )
                }
                item(key = "app-package") {
                    SettingsRow(
                        label = stringResource(R.string.settings_package),
                        summary = appInfo.packageName
                    )
                }
                item(key = "open-source-licences") {
                    SettingsRow(
                        label = stringResource(R.string.settings_open_source_licences),
                        onClick = onLicencesClick
                    )
                }
                item(key = "privacy-and-data") {
                    SettingsRow(
                        label = stringResource(R.string.settings_privacy_and_data),
                        onClick = onPrivacyAndDataClick
                    )
                }

                item(key = "reset-header") {
                    SettingsSectionHeader(stringResource(R.string.settings_reset))
                }
                item(key = "confirm-before-reset") {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_confirm_before_reset),
                        checked = settings.confirmBeforeReset,
                        onCheckedChange = onConfirmBeforeResetToggle
                    )
                }
                item(key = "reset-to-defaults") {
                    SettingsRow(
                        label = stringResource(R.string.settings_reset_to_defaults),
                        onClick = onResetClick
                    )
                }
                item(key = "bottom-spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    when (uiState.activeDialog) {
        SettingsDialog.DefaultInboxFilter -> SingleChoiceDialog(
            title = stringResource(R.string.settings_default_inbox_filter),
            options = DefaultInboxFilter.entries,
            selected = uiState.settings.defaultInboxFilter,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onDefaultInboxFilterSelected,
            onDismiss = onDialogDismissed
        )
        SettingsDialog.PreferredExportFormat -> SingleChoiceDialog(
            title = stringResource(R.string.settings_preferred_export_format),
            options = PreferredExportFormat.entries,
            selected = uiState.settings.preferredExportFormat,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onPreferredExportFormatSelected,
            onDismiss = onDialogDismissed
        )
        SettingsDialog.TemporaryExportRetention -> SingleChoiceDialog(
            title = stringResource(R.string.settings_temporary_export_retention),
            options = TemporaryExportRetention.entries,
            selected = uiState.settings.temporaryExportRetention,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onTemporaryExportRetentionSelected,
            onDismiss = onDialogDismissed
        )
        SettingsDialog.DeleteTemporaryFiles -> ConfirmDialog(
            title = stringResource(R.string.settings_delete_temporary_files_title),
            body = stringResource(R.string.settings_delete_temporary_files_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = onDeleteTemporaryFilesConfirmed,
            onDismiss = onDialogDismissed
        )
        SettingsDialog.ResetConfirmation -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_body),
            confirmLabel = stringResource(R.string.settings_reset_to_defaults),
            onConfirm = onResetConfirmed,
            onDismiss = onDialogDismissed
        )
        SettingsDialog.PrivacyAndData -> InfoDialog(
            title = stringResource(R.string.settings_privacy_title),
            body = stringResource(R.string.settings_privacy_body),
            onDismiss = onDialogDismissed
        )
        SettingsDialog.Licences -> InfoDialog(
            title = stringResource(R.string.settings_licences_title),
            body = stringResource(R.string.settings_licences_body),
            onDismiss = onDialogDismissed
        )
        null -> Unit
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    label: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp)
    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = option == selected,
                                onValueChange = { onSelect(option) },
                                role = Role.RadioButton
                            )
                            .semantics { if (option == selected) this.selected = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(selected = option == selected, onClick = null)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) }
        }
    )
}

private fun DefaultInboxFilter.labelRes(): Int = when (this) {
    DefaultInboxFilter.PENDING -> R.string.filter_pending
    DefaultInboxFilter.REVIEWED -> R.string.filter_reviewed
    DefaultInboxFilter.ARCHIVED -> R.string.filter_archived
    DefaultInboxFilter.ALL -> R.string.filter_all
}

private fun PreferredExportFormat.labelRes(): Int = when (this) {
    PreferredExportFormat.ASK_EVERY_TIME -> R.string.settings_ask_every_time
    PreferredExportFormat.JSON -> R.string.export_format_json
    PreferredExportFormat.PLAIN_TEXT -> R.string.export_format_plain_text
}

private fun TemporaryExportRetention.labelRes(): Int = when (this) {
    TemporaryExportRetention.ONE_HOUR -> R.string.settings_retention_one_hour
    TemporaryExportRetention.TWENTY_FOUR_HOURS -> R.string.settings_retention_twenty_four_hours
    TemporaryExportRetention.SEVEN_DAYS -> R.string.settings_retention_seven_days
}
