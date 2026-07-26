package uk.co.pcgsoft.tracecapture.capture

interface SourceApplicationResolver {
    fun resolve(packageName: String?): SourceApplicationInfo
}

data class SourceApplicationInfo(
    val packageName: String?,
    val displayLabel: String?
)
