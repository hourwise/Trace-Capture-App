package uk.co.pcgsoft.tracecapture.export

sealed interface ExportFailure {
    data object EmptySelection : ExportFailure
    data object TooManyCaptures : ExportFailure
    data object OutputTooLarge : ExportFailure
    data class FormattingFailed(val reason: String? = null) : ExportFailure
    data class WriteFailed(val reason: String? = null) : ExportFailure
}
