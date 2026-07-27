package uk.co.pcgsoft.tracecapture.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.pcgsoft.tracecapture.R
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaptureCard(
    item: CaptureItem,
    onCaptureSelected: (String) -> Unit,
    onMarkReviewed: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (CaptureItem) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCaptureSelected(item.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryLine = getPrimaryLine(item)
                Text(
                    text = primaryLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.capture_actions)
                        )
                    }
                    CaptureActionMenu(
                        item = item,
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onMarkReviewed = onMarkReviewed,
                        onArchive = onArchive,
                        onRestore = onRestore,
                        onDelete = onDelete,
                        onOpenUrl = onOpenUrl,
                        onCopyUrl = onCopyUrl
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val dateStr = formatTimestamp(item.createdAtEpochMillis)
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (item.status != CaptureStatus.PENDING) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(item.status)
                }

                if (item.duplicateOfId != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.duplicate_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.originalContent,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.note ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }

            if (item.detectedUrls.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.urls_found, item.detectedUrls.size, item.detectedUrls.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CaptureActionMenu(
    item: CaptureItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMarkReviewed: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (CaptureItem) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (item.primaryUrl != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_url)) },
                onClick = {
                    onOpenUrl(item.primaryUrl!!)
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_url)) },
                onClick = {
                    onCopyUrl(item.primaryUrl!!)
                    onDismiss()
                }
            )
        }

        when (item.status) {
            CaptureStatus.PENDING -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_reviewed)) },
                    onClick = {
                        onMarkReviewed(item.id)
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archive)) },
                    onClick = {
                        onArchive(item.id)
                        onDismiss()
                    }
                )
            }
            CaptureStatus.REVIEWED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.restore)) },
                    onClick = {
                        onRestore(item.id)
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archive)) },
                    onClick = {
                        onArchive(item.id)
                        onDismiss()
                    }
                )
            }
            CaptureStatus.ARCHIVED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.restore)) },
                    onClick = {
                        onRestore(item.id)
                        onDismiss()
                    }
                )
            }
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete)) },
            onClick = {
                onDelete(item)
                onDismiss()
            }
        )
    }
}

@Composable
private fun StatusBadge(status: CaptureStatus) {
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
private fun getPrimaryLine(item: CaptureItem): String {
    item.sourceLabel?.let { return it }
    item.primaryUrl?.let { url ->
        return try {
            URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
    }
    return stringResource(R.string.text_capture_label)
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK)
    return formatter.format(Date(timestamp))
}
