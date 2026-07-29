package uk.co.pcgsoft.tracecapture.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.pcgsoft.tracecapture.R
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.pendingNavigation) {
        if (uiState.pendingNavigation) {
            viewModel.onNavigated()
            onNavigateBack()
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val copyUrlLabel = stringResource(R.string.copy_url)
    val copyContentLabel = stringResource(R.string.copy_content)

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            val text = when (message) {
                CaptureDetailMessage.NoteSaved -> context.getString(R.string.note_saved)
                CaptureDetailMessage.MarkedReviewed -> context.getString(R.string.action_succeeded_mark_reviewed)
                CaptureDetailMessage.Archived -> context.getString(R.string.action_succeeded_archive)
                CaptureDetailMessage.Restored -> context.getString(R.string.action_succeeded_restore)
                CaptureDetailMessage.LinkCopied -> context.getString(R.string.link_copied)
                CaptureDetailMessage.ContentCopied -> context.getString(R.string.content_copied)
                CaptureDetailMessage.CaptureRemoved -> context.getString(R.string.action_succeeded_delete)
                is CaptureDetailMessage.ActionFailed -> {
                    val resId = when (message.action) {
                        DetailAction.SAVE_NOTE -> R.string.note_save_failed
                        DetailAction.MARK_REVIEWED -> R.string.action_failed_mark_reviewed
                        DetailAction.ARCHIVE -> R.string.action_failed_archive
                        DetailAction.RESTORE_PENDING -> R.string.action_failed_restore
                        DetailAction.DELETE -> R.string.action_failed_delete
                    }
                    context.getString(resId)
                }
            }
            snackbarHostState.showSnackbar(text)
            viewModel.onMessageShown()
        }
    }

    if (uiState.showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onKeepEditing = viewModel::onKeepEditing,
            onDiscard = viewModel::onDiscardChanges
        )
    }

    if (uiState.showDeleteConfirmation && uiState.capture != null) {
        DeleteConfirmDialog(
            capture = uiState.capture!!,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteCancelled
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.onBackRequested()) {
                        } else {
                            onNavigateBack()
                        }
                    }) {
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
        CaptureDetailContent(
            uiState = uiState,
            onNoteChanged = viewModel::onNoteChanged,
            onSaveNote = viewModel::onSaveNote,
            onOpenUrl = { context.openUrl(it) },
            onCopyUrl = { url ->
                context.copyToClipboard(copyUrlLabel, url)
                viewModel.onLinkCopied()
            },
            onCopyContent = { content ->
                context.copyToClipboard(copyContentLabel, content)
                viewModel.onContentCopied()
            },
            onMarkReviewed = viewModel::onMarkReviewed,
            onArchive = viewModel::onArchive,
            onRestore = viewModel::onRestoreToPending,
            onDelete = viewModel::onDeleteRequested,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun CaptureDetailContent(
    uiState: CaptureDetailUiState,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopyContent: (String) -> Unit,
    onMarkReviewed: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.isMissing || uiState.capture == null -> {
            MissingState(modifier = modifier)
        }
        else -> {
            CaptureDetailLoaded(
                capture = uiState.capture!!,
                noteDraft = uiState.noteDraft,
                noteChanged = uiState.noteChanged,
                isSavingNote = uiState.isSavingNote,
                actionInProgress = uiState.actionInProgress,
                onNoteChanged = onNoteChanged,
                onSaveNote = onSaveNote,
                onOpenUrl = onOpenUrl,
                onCopyUrl = onCopyUrl,
                onCopyContent = onCopyContent,
                onMarkReviewed = onMarkReviewed,
                onArchive = onArchive,
                onRestore = onRestore,
                onDelete = onDelete,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MissingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.capture_not_found),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.capture_not_found_detail),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CaptureDetailLoaded(
    capture: CaptureItem,
    noteDraft: String,
    noteChanged: Boolean,
    isSavingNote: Boolean,
    actionInProgress: DetailAction?,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopyContent: (String) -> Unit,
    onMarkReviewed: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        StatusSection(capture)

        Spacer(modifier = Modifier.height(16.dp))

        DateTimeSection(capture)

        Spacer(modifier = Modifier.height(16.dp))

        ContentSection(capture, onCopyContent)

        capture.primaryUrl?.let { url ->
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryUrlSection(url, onOpenUrl, onCopyUrl)
        }

        if (capture.detectedUrls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DetectedUrlsSection(capture.detectedUrls, onOpenUrl, onCopyUrl)
        }

        Spacer(modifier = Modifier.height(16.dp))
        NoteSection(
            note = noteDraft,
            noteChanged = noteChanged,
            isSaving = isSavingNote,
            onNoteChanged = onNoteChanged,
            onSave = onSaveNote
        )

        Spacer(modifier = Modifier.height(16.dp))
        ActionSection(
            capture = capture,
            actionInProgress = actionInProgress,
            onMarkReviewed = onMarkReviewed,
            onArchive = onArchive,
            onRestore = onRestore,
            onDelete = onDelete
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatusSection(capture: CaptureItem) {
    val sourceLabel = getSourceLabel(capture)

    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(capture.status)
        if (capture.duplicateOfId != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.duplicate_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (sourceLabel != null) {
        Text(
            text = stringResource(R.string.from_source, sourceLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusChip(status: CaptureStatus) {
    val text = when (status) {
        CaptureStatus.PENDING -> stringResource(R.string.filter_pending)
        CaptureStatus.REVIEWED -> stringResource(R.string.filter_reviewed)
        CaptureStatus.ARCHIVED -> stringResource(R.string.filter_archived)
    }
    SuggestionChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.height(20.dp)
    )
}

@Composable
private fun DateTimeSection(capture: CaptureItem) {
    val dateFormatter = remember { SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.UK) }
    val updatedFormatter = remember { SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.UK) }

    Text(
        text = stringResource(R.string.captured_date, dateFormatter.format(Date(capture.createdAtEpochMillis))),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (capture.updatedAtEpochMillis > capture.createdAtEpochMillis + 1000) {
        Text(
            text = stringResource(R.string.updated_date, updatedFormatter.format(Date(capture.updatedAtEpochMillis))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val syncLabel = when (capture.syncStatus) {
        uk.co.pcgsoft.tracecapture.domain.SyncStatus.LOCAL_ONLY -> stringResource(R.string.sync_local)
        uk.co.pcgsoft.tracecapture.domain.SyncStatus.QUEUED -> stringResource(R.string.sync_queued)
        uk.co.pcgsoft.tracecapture.domain.SyncStatus.SYNCING -> stringResource(R.string.sync_syncing)
        uk.co.pcgsoft.tracecapture.domain.SyncStatus.SYNCED -> stringResource(R.string.sync_synced)
        uk.co.pcgsoft.tracecapture.domain.SyncStatus.FAILED -> stringResource(R.string.sync_failed)
    }
    Text(
        text = syncLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ContentSection(capture: CaptureItem, onCopyContent: (String) -> Unit) {
    val originalContentDescription = stringResource(R.string.original_content)

    Text(
        text = stringResource(R.string.original_content),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = originalContentDescription }
    ) {
        SelectionContainer {
            Text(
                text = capture.originalContent,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(onClick = { onCopyContent(capture.originalContent) }) {
        Text(stringResource(R.string.copy_content))
    }
}

@Composable
private fun PrimaryUrlSection(
    url: String,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.primary_url),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = url,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onOpenUrl(url) }) {
            Text(stringResource(R.string.open_url))
        }
        OutlinedButton(onClick = { onCopyUrl(url) }) {
            Text(stringResource(R.string.copy_url))
        }
    }
}

@Composable
private fun DetectedUrlsSection(
    urls: List<String>,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    Text(
        text = pluralStringResource(R.plurals.detected_urls, urls.size, urls.size),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))

    urls.forEach { url ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onOpenUrl(url) }) {
                Text(stringResource(R.string.open_url), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { onCopyUrl(url) }) {
                Text(stringResource(R.string.copy_url), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NoteSection(
    note: String,
    noteChanged: Boolean,
    isSaving: Boolean,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Text(
        text = stringResource(R.string.editorial_note),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
        value = note,
        onValueChange = onNoteChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.note_hint)) },
        minLines = 3,
        maxLines = 6,
        supportingText = {
            Text(
                text = stringResource(R.string.char_count, note.length),
                color = if (note.length > 1900) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        enabled = !isSaving
    )

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onSave,
        enabled = noteChanged && !isSaving
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(R.string.save_note))
    }
}

@Composable
private fun ActionSection(
    capture: CaptureItem,
    actionInProgress: DetailAction?,
    onMarkReviewed: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Text(
        text = stringResource(R.string.actions),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (capture.status) {
        CaptureStatus.PENDING -> {
            Button(
                onClick = onMarkReviewed,
                enabled = actionInProgress == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.mark_reviewed))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onArchive,
                enabled = actionInProgress == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.archive))
            }
        }
        CaptureStatus.REVIEWED -> {
            Button(
                onClick = onRestore,
                enabled = actionInProgress == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.restore_to_pending))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onArchive,
                enabled = actionInProgress == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.archive))
            }
        }
        CaptureStatus.ARCHIVED -> {
            Button(
                onClick = onRestore,
                enabled = actionInProgress == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.restore_to_pending))
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onDelete,
        enabled = actionInProgress == null,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.delete))
    }
}

@Composable
private fun UnsavedChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.unsaved_changes_title)) },
        text = { Text(stringResource(R.string.unsaved_changes_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.discard_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.keep_editing))
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    capture: CaptureItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = remember(capture.originalContent) {
        val truncated = capture.originalContent.take(100)
        if (capture.originalContent.length > 100) "$truncated..." else truncated
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.delete_confirmation_detail, preview)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

private fun getSourceLabel(capture: CaptureItem): String? {
    capture.sourceLabel?.let { return it }
    capture.primaryUrl?.let { url ->
        return try {
            URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
    }
    return null
}

private fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}
