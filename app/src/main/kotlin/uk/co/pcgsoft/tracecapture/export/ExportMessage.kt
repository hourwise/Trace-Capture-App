package uk.co.pcgsoft.tracecapture.export

sealed interface ExportMessage {
    data object ExportSaved : ExportMessage
    data object ShareChooserOpened : ExportMessage
    data object ExportFailed : ExportMessage
    data object FileWriteFailed : ExportMessage
    data object NoSharingApp : ExportMessage
    data object SaveNoteFirst : ExportMessage
    data object EmptyExport : ExportMessage
    data object ExportTooLarge : ExportMessage
}

fun ExportFailure.toExportMessage(): ExportMessage = when (this) {
    ExportFailure.EmptySelection -> ExportMessage.EmptyExport
    ExportFailure.TooManyCaptures -> ExportMessage.ExportTooLarge
    ExportFailure.OutputTooLarge -> ExportMessage.ExportTooLarge
    is ExportFailure.FormattingFailed -> ExportMessage.ExportFailed
    is ExportFailure.WriteFailed -> ExportMessage.FileWriteFailed
}
