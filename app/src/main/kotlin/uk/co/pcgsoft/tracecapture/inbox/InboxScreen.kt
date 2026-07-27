package uk.co.pcgsoft.tracecapture.inbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.pcgsoft.tracecapture.R
import uk.co.pcgsoft.tracecapture.domain.CaptureItem

@Composable
fun InboxScreen(
    onCaptureSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InboxContent(
        uiState = uiState,
        onCaptureSelected = onCaptureSelected,
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
        modifier = modifier
    )
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            val text = when (message) {
                InboxMessage.LinkCopied -> context.getString(R.string.link_copied)
                is InboxMessage.ActionSucceeded -> {
                    val resId = when (message.action) {
                        InboxAction.MARK_REVIEWED -> R.string.action_succeeded_mark_reviewed
                        InboxAction.ARCHIVE -> R.string.action_succeeded_archive
                        InboxAction.RESTORE -> R.string.action_succeeded_restore
                        InboxAction.DELETE -> R.string.action_succeeded_delete
                    }
                    context.getString(resId)
                }
                is InboxMessage.ActionFailed -> {
                    val resId = when (message.action) {
                        InboxAction.MARK_REVIEWED -> R.string.action_failed_mark_reviewed
                        InboxAction.ARCHIVE -> R.string.action_failed_archive
                        InboxAction.RESTORE -> R.string.action_failed_restore
                        InboxAction.DELETE -> R.string.action_failed_delete
                    }
                    context.getString(resId)
                }
            }
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }

    // Handle "Copied URL" for clipboard label - use a constant or resource
    val clipboardLabel = stringResource(R.string.copy_url)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) }
            )
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
                            onCaptureSelected = { 
                                onCaptureSelected(it)
                            },
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

    uiState.pendingDelete?.let { item ->
        DeleteConfirmationDialog(
            item = item,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled
        )
    }
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
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        if (isSearch) {
            // No Spacer needed if we want it simple, but let's keep it for spacing
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClearSearch) {
                Text(stringResource(R.string.clear_search))
            }
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
        text = { 
            Text(text = stringResource(R.string.delete_confirmation, preview))
        },
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

private fun InboxFilter.toResId(): Int = when (this) {
    InboxFilter.PENDING -> R.string.filter_pending
    InboxFilter.REVIEWED -> R.string.filter_reviewed
    InboxFilter.ARCHIVED -> R.string.filter_archived
    InboxFilter.ALL -> R.string.filter_all
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
