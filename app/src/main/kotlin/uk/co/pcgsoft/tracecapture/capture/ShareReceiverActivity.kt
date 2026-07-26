package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    private var currentIntent by mutableStateOf(intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ShareCaptureViewModel = hiltViewModel()
            LaunchedEffect(currentIntent) {
                currentIntent?.let { viewModel.processIntent(it) }
            }
            QuickCaptureScreen(
                state = viewModel.uiState.collectAsState().value,
                onNoteChange = viewModel::updateNote,
                onSave = viewModel::save,
                onRetry = viewModel::retry,
                onCancel = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }
}

@Composable
private fun QuickCaptureScreen(
    state: ShareCaptureUiState,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(state) {
        if (state is ShareCaptureUiState.Saved) {
            kotlinx.coroutines.delay(1200L)
            onCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(uk.co.pcgsoft.tracecapture.R.string.app_name),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is ShareCaptureUiState.Loading -> LoadingIndicator()
            is ShareCaptureUiState.Ready -> ReadyContent(
                state = s,
                onNoteChange = onNoteChange,
                onSave = onSave,
                onCancel = onCancel
            )
            is ShareCaptureUiState.Invalid -> InvalidContent(
                message = stringResource(s.reason.messageRes())
            )
            is ShareCaptureUiState.Saved -> SavedConfirmation()
            is ShareCaptureUiState.Failed -> FailedContent(
                message = stringResource(s.messageResId),
                onRetry = onRetry,
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ReadyContent(
    state: ShareCaptureUiState.Ready,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val draft = state.draft

    if (state.isSaving) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }

    draft.sourceLabel?.let { label ->
        Text(
            text = stringResource(uk.co.pcgsoft.tracecapture.R.string.from_source, label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    draft.primaryUrl?.let { url ->
        val display = try {
            URI(url).host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
        Text(
            text = display,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    val preview = if (draft.captureType == uk.co.pcgsoft.tracecapture.domain.CaptureType.URL) {
        draft.primaryUrl?.let {
            try {
                URI(it).host?.removePrefix("www.") ?: it
            } catch (_: Exception) {
                it
            }
        } ?: draft.originalContent
    } else {
        draft.originalContent.take(120)
    }

    Spacer(Modifier.height(4.dp))
    Text(
        text = preview,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (draft.detectedUrls.size > 1) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(uk.co.pcgsoft.tracecapture.R.plurals.urls_found, draft.detectedUrls.size, draft.detectedUrls.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.note,
        onValueChange = onNoteChange,
        label = { Text(stringResource(uk.co.pcgsoft.tracecapture.R.string.note_hint)) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Note input" },
        maxLines = 4,
        singleLine = false
    )

    state.duplicate?.let { dup ->
        Spacer(Modifier.height(12.dp))
        val formatter = SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.UK)
        val dateStr = formatter.format(Date(dup.capturedAtEpochMillis))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Duplicate warning" }
        ) {
            Text(
                text = stringResource(
                    uk.co.pcgsoft.tracecapture.R.string.duplicate_warning,
                    dateStr
                ),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !state.isSaving
        ) {
            Text(stringResource(uk.co.pcgsoft.tracecapture.R.string.cancel_button))
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onSave,
            enabled = !state.isSaving
        ) {
            Text(stringResource(uk.co.pcgsoft.tracecapture.R.string.save_button))
        }
    }
}

@Composable
private fun InvalidContent(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SavedConfirmation() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(uk.co.pcgsoft.tracecapture.R.string.saved_confirmation),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(uk.co.pcgsoft.tracecapture.R.string.cancel_button))
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onRetry) {
            Text(stringResource(uk.co.pcgsoft.tracecapture.R.string.retry_button))
        }
    }
}

private fun ShareRejectionReason.messageRes(): Int = when (this) {
    ShareRejectionReason.UNSUPPORTED_ACTION -> uk.co.pcgsoft.tracecapture.R.string.unsupported_action
    ShareRejectionReason.UNSUPPORTED_MIME_TYPE -> uk.co.pcgsoft.tracecapture.R.string.unsupported_mime
    ShareRejectionReason.MISSING_CONTENT -> uk.co.pcgsoft.tracecapture.R.string.missing_content
    ShareRejectionReason.BLANK_CONTENT -> uk.co.pcgsoft.tracecapture.R.string.missing_content
    ShareRejectionReason.CONTENT_TOO_LONG -> uk.co.pcgsoft.tracecapture.R.string.content_too_long
}
