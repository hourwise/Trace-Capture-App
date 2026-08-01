package uk.co.pcgsoft.tracecapture.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.CreateExportDocumentRequest
import uk.co.pcgsoft.tracecapture.export.ExportCoordinator
import uk.co.pcgsoft.tracecapture.export.ExportFailure
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.export.ExportMessage
import uk.co.pcgsoft.tracecapture.export.ExportResult
import uk.co.pcgsoft.tracecapture.export.ExportSource
import uk.co.pcgsoft.tracecapture.export.file.ExportFileWriter
import uk.co.pcgsoft.tracecapture.export.file.FileWriteResult
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import uk.co.pcgsoft.tracecapture.export.toExportMessage
import javax.inject.Inject

@HiltViewModel
class CaptureExportViewModel @Inject constructor(
    private val exportCoordinator: ExportCoordinator,
    private val exportFileWriter: ExportFileWriter,
    private val exportShareFileManager: ExportShareFileManager
) : ViewModel() {

    private val _exportState = MutableStateFlow(DetailExportState())
    val exportState: StateFlow<DetailExportState> = _exportState.asStateFlow()

    fun onExportRequested() {
        val state = _exportState.value
        if (state.isPreparing || state.pendingDocument != null || state.pendingShare != null) return
        _exportState.update { it.copy(showFormatChooser = true) }
    }

    fun onExportBlocked() {
        _exportState.update { it.copy(message = ExportMessage.SaveNoteFirst) }
    }

    fun onExportFormatSelected(format: ExportFormat) {
        _exportState.update {
            it.copy(
                showFormatChooser = false,
                selectedFormat = format,
                showSaveOrShareChooser = true
            )
        }
    }

    fun onExportDialogCancelled() {
        _exportState.value = DetailExportState()
    }

    fun onSaveFileRequested(capture: CaptureItem) {
        val state = _exportState.value
        val format = state.selectedFormat ?: return
        if (state.isPreparing) return
        _exportState.update { it.copy(showSaveOrShareChooser = false, isPreparing = true) }
        viewModelScope.launch {
            when (val result = prepare(capture, format)) {
                is ExportResult.Success -> _exportState.update {
                    it.copy(
                        isPreparing = false,
                        pendingDocument = CreateExportDocumentRequest(
                            mimeType = result.mimeType,
                            suggestedFileName = result.suggestedFileName,
                            content = result.content
                        )
                    )
                }
                is ExportResult.Failure -> _exportState.update {
                    it.copy(isPreparing = false, message = result.failure.toExportMessage())
                }
            }
        }
    }

    fun onDocumentUriReceived(uri: Uri?) {
        val request = _exportState.value.pendingDocument ?: return
        _exportState.update { it.copy(pendingDocument = null) }
        if (uri == null) return
        _exportState.update { it.copy(isPreparing = true) }
        viewModelScope.launch {
            val result = exportFileWriter.write(uri, request.content)
            _exportState.update {
                it.copy(
                    isPreparing = false,
                    message = when (result) {
                        is FileWriteResult.Success -> ExportMessage.ExportSaved
                        is FileWriteResult.Failure -> ExportMessage.FileWriteFailed
                    }
                )
            }
        }
    }

    fun onShareRequested(capture: CaptureItem) {
        val state = _exportState.value
        val format = state.selectedFormat ?: return
        if (state.isPreparing) return
        _exportState.update { it.copy(showSaveOrShareChooser = false, isPreparing = true) }
        viewModelScope.launch {
            when (val result = prepare(capture, format)) {
                is ExportResult.Success -> {
                    try {
                        val prepared = exportShareFileManager.prepareShareExport(
                            content = result.content,
                            mimeType = result.mimeType,
                            fileName = result.suggestedFileName
                        )
                        _exportState.update { it.copy(isPreparing = false, pendingShare = prepared) }
                    } catch (_: Exception) {
                        _exportState.update {
                            it.copy(isPreparing = false, message = ExportMessage.ExportFailed)
                        }
                    }
                }
                is ExportResult.Failure -> _exportState.update {
                    it.copy(isPreparing = false, message = result.failure.toExportMessage())
                }
            }
        }
    }

    fun onShareLaunched() {
        _exportState.update { it.copy(pendingShare = null, message = ExportMessage.ExportShared) }
    }

    fun onShareFailedNoApp() {
        _exportState.update { it.copy(pendingShare = null, message = ExportMessage.NoSharingApp) }
    }

    fun onExportMessageShown() {
        _exportState.update { it.copy(message = null) }
    }

    private suspend fun prepare(capture: CaptureItem, format: ExportFormat): ExportResult {
        return try {
            exportCoordinator.prepareExport(
                captures = listOf(capture),
                format = format,
                source = ExportSource.SINGLE_CAPTURE
            )
        } catch (_: Exception) {
            ExportResult.Failure(ExportFailure.FormattingFailed())
        }
    }
}
