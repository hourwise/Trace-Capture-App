package uk.co.pcgsoft.tracecapture.export

data class ExportLimits(
    val maxCaptures: Int = 10_000,
    val maxOutputBytes: Long = 50L * 1024 * 1024
)
