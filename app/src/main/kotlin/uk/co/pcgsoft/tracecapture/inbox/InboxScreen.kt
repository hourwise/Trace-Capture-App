package uk.co.pcgsoft.tracecapture.inbox

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.pcgsoft.tracecapture.R
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportMessage

@Composable
fun InboxScreen(
    onCaptureSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
    exportViewModel: InboxExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.exportState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    BackHandler(enabled = uiState.selection.isActive) {
        if (!exportState.isPreparing) viewModel.onSelectionExit()
    }

    LaunchedEffect(exportState.unavailableIds) {
        if (exportState.unavailableIds.isNotEmpty()) {
            viewModel.onSelectionItemsUnavailable(exportState.unavailableIds)
            exportViewModel.onUnavailableIdsHandled()
        }
    }

    LaunchedEffect(exportState.message, exportState.exitSelectionAfterSuccess) {
        if (exportState.exitSelectionAfterSuccess &&
            (exportState.message == ExportMessage.ExportSaved ||
                exportState.message == ExportMessage.ShareChooserOpened)
        ) {
            viewModel.onSelectionExit()
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            exportViewModel.onDocumentUriReceived(result.data?.data)
        } else {
            exportViewModel.onDocumentUriReceived(null)
        }
    }

    LaunchedEffect(exportState.pendingDocument) {
        val request = exportState.pendingDocument ?: return@LaunchedEffect
        if (exportState.documentLaunchConsumed) return@LaunchedEffect
        exportViewModel.onDocumentLaunchStarted()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = request.mimeType
            putExtra(Intent.EXTRA_TITLE, request.suggestedFileName)
        }
        createDocumentLauncher.launch(intent)
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // The chooser launched a target; report the launch (not delivery).
            exportViewModel.onShareLaunched()
        } else {
            // The user dismissed the chooser without picking an app: keep the
            // selection active and clear the pending share without an error.
            exportViewModel.onShareCancelled()
        }
    }

    LaunchedEffect(exportState.pendingShare) {
        val share = exportState.pendingShare ?: return@LaunchedEffect
        if (exportState.shareLaunchConsumed) return@LaunchedEffect
        exportViewModel.onShareLaunchStarted()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = share.mimeType
            putExtra(Intent.EXTRA_STREAM, share.contentUri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
            clipData = ClipData.newRawUri(null, share.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(sendIntent, context.getString(R.string.share_export))
            shareLauncher.launch(chooser)
        } catch (_: Exception) {
            exportViewModel.onShareFailedNoApp()
        }
    }

    InboxContent(
        uiState = uiState,
        onCaptureSelected = { id ->
            if (uiState.selection.isActive) viewModel.onSelectionToggled(id)
            else onCaptureSelected(id)
        },
        onCaptureLongPressed = viewModel::onCaptureLongPressed,
        onSelectionRequested = viewModel::onSelectionRequested,
        onSelectionExit = viewModel::onSelectionExit,
        onSelectAllOrClear = viewModel::onSelectAllOrClear,
        onOpenSettings = onOpenSettings,
        onExportRequested = exportViewModel::onExportRequested,
        onFilterSelected = viewModel::onFilterSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onMarkReviewed = viewModel::markReviewed,
        onArchive = viewModel::archive,
        onRestore = viewModel::restoreToPending,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onMessageShown = viewModel::onMessageShown,
        onLinkCopied = viewModel::onLinkCopied,
        exportMessage = exportState.message,
        onExportMessageShown = exportViewModel::onExportMessageShown,
        isPreparingExport = exportState.isPreparing,
        modifier = modifier
    )

    if (exportState.showFormatChooser) {
        BulkExportFormatDialog(
            count = uiState.selection.selectedIds.size,
            onFormatSelected = exportViewModel::onExportFormatSelected,
            onDismiss = exportViewModel::onExportDialogCancelled
        )
    }

    val selectedFormat = exportState.selectedFormat
    if (exportState.showSaveOrShareChooser && selectedFormat != null) {
        BulkExportSaveOrShareDialog(
            count = uiState.selection.selectedIds.size,
            format = selectedFormat,
            isPreparing = exportState.isPreparing,
            onSave = {
                exportViewModel.onSaveFileRequested(
                    selectedIds = uiState.selection.selectedIds,
                    visibleOrderIds = uiState.captures.map { it.id }
                )
            },
            onShare = {
                exportViewModel.onShareRequested(
                    selectedIds = uiState.selection.selectedIds,
                    visibleOrderIds = uiState.captures.map { it.id }
                )
            },
            onDismiss = exportViewModel::onExportDialogCancelled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxContent(
    uiState: InboxUiState,
    onCaptureSelected: (String) -> Unit,
    onFilterSelected: (InboxFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMarkReviewed: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDeleteRequested: (CaptureItem) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onMessageShown: () -> Unit,
    onLinkCopied: () -> Unit,
    exportMessage: ExportMessage? = null,
    onExportMessageShown: () -> Unit = {},
    onCaptureLongPressed: (String) -> Unit = {},
    onSelectionRequested: () -> Unit = {},
    onSelectionExit: () -> Unit = {},
    onSelectAllOrClear: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onExportRequested: () -> Unit = {},
    isPreparingExport: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardLabel = stringResource(R.string.copy_url)

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            val text = when (message) {
                InboxMessage.LinkCopied -> context.getString(R.string.link_copied)
                is InboxMessage.SelectedCapturesUnavailable -> context.resources.getQuantityString(
                    R.plurals.selected_captures_unavailable,
                    message.count,
                    message.count
                )
                is InboxMessage.ActionSucceeded -> context.getString(message.action.successResId())
                is InboxMessage.ActionFailed -> context.getString(message.action.failureResId())
            }
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }

    LaunchedEffect(exportMessage) {
        exportMessage?.let { message ->
            val resId = when (message) {
                ExportMessage.ExportSaved -> R.string.export_saved
                ExportMessage.ShareChooserOpened -> R.string.export_share_chooser_opened
                ExportMessage.ExportFailed -> R.string.export_failed
                ExportMessage.FileWriteFailed -> R.string.export_write_failed
                ExportMessage.NoSharingApp -> R.string.export_no_sharing_app
                ExportMessage.SaveNoteFirst -> R.string.export_save_note_first
                ExportMessage.EmptyExport -> R.string.export_empty
                ExportMessage.ExportTooLarge -> R.string.export_too_large
            }
            snackbarHostState.showSnackbar(context.getString(resId))
            onExportMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (uiState.selection.isActive) {
                    TopAppBar(
                        title = {
                            Text(
                                pluralStringResource(
                                    R.plurals.selected_count,
                                    uiState.selection.selectedIds.size,
                                    uiState.selection.selectedIds.size
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onSelectionExit) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.exit_selection)
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = onSelectAllOrClear) {
                                Text(
                                    if (uiState.selection.selectedIds.size == uiState.captures.size &&
                                        uiState.captures.isNotEmpty() &&
                                        uiState.selection.selectedIds.containsAll(uiState.captures.map { it.id })
                                    ) {
                                        stringResource(R.string.clear_all)
                                    } else {
                                        stringResource(R.string.select_all)
                                    }
                                )
                            }
                            TextButton(
                                onClick = onExportRequested,
                                enabled = uiState.selection.selectedIds.isNotEmpty()
                            ) {
                                Text(stringResource(R.string.export_selected))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.inbox_title)) },
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                            if (!uiState.isLoading && uiState.captures.isNotEmpty()) {
                                TextButton(onClick = onSelectionRequested) {
                                    Text(stringResource(R.string.select))
                                }
                            }
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    modifier = Modifier.padding(16.dp)
                )

                FilterChips(
                    selectedFilter = uiState.filter,
                    onFilterSelected = onFilterSelected,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.captures.isEmpty()) {
                    EmptyState(
                        filter = uiState.filter,
                        isSearch = uiState.searchQuery.isNotEmpty(),
                        onClearSearch = { onSearchQueryChanged("") }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.captures, key = { it.id }) { item ->
                            CaptureCard(
                                item = item,
                                selectionMode = uiState.selection.isActive,
                                selected = item.id in uiState.selection.selectedIds,
                                onCaptureSelected = onCaptureSelected,
                                onLongClick = { onCaptureLongPressed(item.id) },
                                onMarkReviewed = onMarkReviewed,
                                onArchive = onArchive,
                                onRestore = onRestore,
                                onDelete = onDeleteRequested,
                                onOpenUrl = { url -> context.openUrl(url) },
                                onCopyUrl = { url ->
                                    context.copyToClipboard(clipboardLabel, url)
                                    onLinkCopied()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isPreparingExport) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.systemBars.asPaddingValues()),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.bulk_export_progress))
                    }
                }
            }
        }
    }

    uiState.pendingDelete?.let { item ->
        DeleteConfirmationDialog(
            item = item,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled
        )
    }
}

@Composable
private fun BulkExportFormatDialog(
    count: Int,
    onFormatSelected: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.export_selected_title, count, count))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { onFormatSelected(ExportFormat.JSON) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.export_format_json)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.export_format_json_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { onFormatSelected(ExportFormat.PLAIN_TEXT) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.export_format_plain_text)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.export_format_plain_text_description),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@Composable
private fun BulkExportSaveOrShareDialog(
    count: Int,
    format: ExportFormat,
    isPreparing: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val description = if (format == ExportFormat.JSON) {
        stringResource(R.string.export_format_json_description)
    } else {
        stringResource(R.string.export_format_plain_text_description)
    }
    AlertDialog(
        onDismissRequest = { if (!isPreparing) onDismiss() },
        title = {
            Text(pluralStringResource(R.plurals.export_selected_title, count, count))
        },
        text = { Text(description) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare, enabled = !isPreparing) {
                    Text(stringResource(R.string.export_share))
                }
                Button(onClick = onSave, enabled = !isPreparing) {
                    Text(stringResource(R.string.export_save_file))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPreparing) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun FilterChips(
    selectedFilter: InboxFilter,
    onFilterSelected: (InboxFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(InboxFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.toResId())) }
            )
        }
    }
}

@Composable
private fun EmptyState(
    filter: InboxFilter,
    isSearch: Boolean,
    onClearSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val message = when {
            isSearch -> stringResource(R.string.empty_search)
            filter == InboxFilter.PENDING -> stringResource(R.string.empty_inbox)
            filter == InboxFilter.REVIEWED -> stringResource(R.string.empty_reviewed)
            filter == InboxFilter.ARCHIVED -> stringResource(R.string.empty_archived)
            else -> stringResource(R.string.empty_inbox)
        }

        Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

        if (isSearch) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClearSearch) { Text(stringResource(R.string.clear_search)) }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    item: CaptureItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = remember(item.originalContent) {
        val truncated = item.originalContent.take(100)
        if (item.originalContent.length > 100) "$truncated..." else truncated
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(text = stringResource(R.string.delete_confirmation, preview)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

private fun InboxAction.successResId(): Int = when (this) {
    InboxAction.MARK_REVIEWED -> R.string.action_succeeded_mark_reviewed
    InboxAction.ARCHIVE -> R.string.action_succeeded_archive
    InboxAction.RESTORE -> R.string.action_succeeded_restore
    InboxAction.DELETE -> R.string.action_succeeded_delete
}

private fun InboxAction.failureResId(): Int = when (this) {
    InboxAction.MARK_REVIEWED -> R.string.action_failed_mark_reviewed
    InboxAction.ARCHIVE -> R.string.action_failed_archive
    InboxAction.RESTORE -> R.string.action_failed_restore
    InboxAction.DELETE -> R.string.action_failed_delete
}

private fun InboxFilter.toResId(): Int = when (this) {
    InboxFilter.PENDING -> R.string.filter_pending
    InboxFilter.REVIEWED -> R.string.filter_reviewed
    InboxFilter.ARCHIVED -> R.string.filter_archived
    InboxFilter.ALL -> R.string.filter_all
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
