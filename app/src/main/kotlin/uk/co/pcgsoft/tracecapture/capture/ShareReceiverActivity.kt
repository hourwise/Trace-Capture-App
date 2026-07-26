package uk.co.pcgsoft.tracecapture.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var processor: SharedCaptureProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val draft = processor.process(intent)

        setContent {
            ShareReceiverScreen(
                draft = draft,
                onClose = { finish() }
            )
        }
    }
}

@Composable
private fun ShareReceiverScreen(
    draft: CaptureDraft?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "TRACE Capture (Diag)",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))

        if (draft == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No parseable content received.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            DiagnosticField("Capture Type", draft.captureType.name)

            draft.sourceLabel?.let {
                DiagnosticField("Source", it)
            }
            draft.sourcePackageName?.let {
                DiagnosticField("Source Package", it)
            }
            draft.primaryUrl?.let {
                DiagnosticField("Primary URL", it)
            }
            DiagnosticField("Detected URLs", "${draft.detectedUrls.size}")

            if (draft.detectedUrls.isNotEmpty()) {
                DiagnosticField("All URLs", draft.detectedUrls.joinToString("\n"))
            }

            DiagnosticField("Content Preview", draft.originalContent.take(200))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun DiagnosticField(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
