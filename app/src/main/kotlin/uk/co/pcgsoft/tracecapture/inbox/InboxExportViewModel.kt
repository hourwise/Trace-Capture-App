package uk.co.pcgsoft.tracecapture.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
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
class InboxExportViewModel @Inject constructor(
    private val repository: CaptureRepository,
    private val exportCoordinator: ExportCoordinator,
    private val exportFileWriter: ExportFileWriter,
    private val exportShareFileManager: ExportShareFileManager
) : ViewModel() {

    private val _exportState = MutableStateFlow(InboxExportState())
    val exportState: StateFlow<InboxExportState> = _exportState.asStateFlow()

    fun onExportRequested() {
        val state = _exportState.value
        if (state.isPreparing || state.pendingDocument != null || state.pendingShare != null) return
        _exportState.update { it.copy(showFormatChooser = true, message = null) }
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
        if (_exportState.value.isPreparing) return
        _exportState.value = InboxExportState()
    }

    fun onSaveFileRequested(selectedIds: Set<String>, visibleOrderIds: List<String>) {
        val state = _exportState.value
        val format = state.selectedFormat ?: return
        if (state.isPreparing || selectedIds.isEmpty()) {
            if (selectedIds.isEmpty()) _exportState.update { it.copy(message = ExportMessage.EmptyExport) }
            return
        }
        _exportState.update {
            it.copy(showSaveOrShareChooser = true, isPreparing = true, message = null)
        }
        viewModelScope.launch {
            when (val result = prepare(selectedIds, visibleOrderIds, format)) {
                is ExportResult.Success -> _exportState.update {
                    it.copy(
                        showSaveOrShareChooser = false,
                        isPreparing = false,
                        pendingDocument = CreateExportDocumentRequest(
                            mimeType = result.mimeType,
                            suggestedFileName = result.suggestedFileName,
                            content = result.content
                        ),
                        documentLaunchConsumed = false
                    )
                }
                is ExportResult.Failure -> _exportState.update {
                    it.copy(
                        showSaveOrShareChooser = true,
                        isPreparing = false,
                        message = result.failure.toExportMessage()
                    )
                }
            }
        }
    }

    fun onDocumentLaunchStarted() {
        _exportState.update {
            if (it.pendingDocument == null) it else it.copy(documentLaunchConsumed = true)
        }
    }

    fun onDocumentUriReceived(uri: android.net.Uri?) {
        val request = _exportState.value.pendingDocument ?: return
        _exportState.update {
            it.copy(
                pendingDocument = null,
                documentLaunchConsumed = false
            )
        }
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

    fun onShareRequested(selectedIds: Set<String>, visibleOrderIds: List<String>) {
        val state = _exportState.value
        val format = state.selectedFormat ?: return
        if (state.isPreparing || selectedIds.isEmpty()) {
            if (selectedIds.isEmpty()) _exportState.update { it.copy(message = ExportMessage.EmptyExport) }
            return
        }
        _exportState.update {
            it.copy(showSaveOrShareChooser = true, isPreparing = true, message = null)
        }
        viewModelScope.launch {
            when (val result = prepare(selectedIds, visibleOrderIds, format)) {
                is ExportResult.Success -> {
                    try {
                        val prepared = exportShareFileManager.prepareShareExport(
                            content = result.content,
                            mimeType = result.mimeType,
                            fileName = result.suggestedFileName
                        )
                        _exportState.update {
                            it.copy(
                                showSaveOrShareChooser = false,
                                isPreparing = false,
                                pendingShare = prepared,
                                shareLaunchConsumed = false
                            )
                        }
                    } catch (_: Exception) {
                        _exportState.update {
                            it.copy(
                                showSaveOrShareChooser = true,
                                isPreparing = false,
                                message = ExportMessage.ExportFailed
                            )
                        }
                    }
                }
                is ExportResult.Failure -> _exportState.update {
                    it.copy(
                        showSaveOrShareChooser = true,
                        isPreparing = false,
                        message = result.failure.toExportMessage()
                    )
                }
            }
        }
    }

    fun onShareLaunchStarted() {
        _exportState.update {
            if (it.pendingShare == null) it else it.copy(shareLaunchConsumed = true)
        }
    }

    fun onShareLaunched() {
        _exportState.update {
            it.copy(
                pendingShare = null,
                shareLaunchConsumed = false,
                message = ExportMessage.ExportShared
            )
        }
    }

    fun onShareFailedNoApp() {
        _exportState.update {
            it.copy(
                pendingShare = null,
                shareLaunchConsumed = false,
                message = ExportMessage.NoSharingApp
            )
        }
    }

    fun onUnavailableIdsHandled() {
        _exportState.update { it.copy(unavailableIds = emptySet()) }
    }

    fun onExportMessageShown() {
        _exportState.update { it.copy(message = null) }
    }

    private suspend fun prepare(
        selectedIds: Set<String>,
        visibleOrderIds: List<String>,
        format: ExportFormat
    ): ExportResult {
        val activeCaptures = try {
            repository.getActiveByIds(selectedIds)
        } catch (_: Exception) {
            return ExportResult.Failure(ExportFailure.FormattingFailed())
        }
        val activeIds = activeCaptures.asSequence().map { it.id }.toSet()
        val unavailableIds = selectedIds - activeIds
        if (unavailableIds.isNotEmpty()) {
            _exportState.update { it.copy(unavailableIds = unavailableIds) }
        }
        val order = visibleOrderIds.withIndex().associate { it.value to it.index }
        val orderedCaptures = activeCaptures.sortedWith(
            compareBy<CaptureItem> { order[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.createdAtEpochMillis }
                .thenByDescending { it.id }
        )
        return try {
            exportCoordinator.prepareExport(
                captures = orderedCaptures,
                format = format,
                source = ExportSource.SUPPLIED_CAPTURE_LIST
            )
        } catch (_: Exception) {
            ExportResult.Failure(ExportFailure.FormattingFailed())
        }
    }
}
