package uk.co.pcgsoft.tracecapture.export.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptureExportItem(
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val originalContent: String,
    val primaryUrl: String?,
    val detectedUrls: List<String>,
    val sourcePackageName: String?,
    val sourceLabel: String?,
    val note: String?,
    val captureType: String,
    val status: String,
    val syncStatus: String,
    val duplicateOfId: String?,
    val archivedAt: String?,
    val deletedAt: String?
)
