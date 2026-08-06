package uk.co.pcgsoft.tracecapture.settings

import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.inbox.InboxFilter

/**
 * Immutable, device-local application settings.
 */
data class AppSettings(
    val defaultInboxFilter: DefaultInboxFilter = DefaultInboxFilter.PENDING,
    val preferredExportFormat: PreferredExportFormat = PreferredExportFormat.ASK_EVERY_TIME,
    val exitSelectionAfterSuccessfulExport: Boolean = true,
    val temporaryExportRetention: TemporaryExportRetention = TemporaryExportRetention.TWENTY_FOUR_HOURS,
    val confirmBeforeReset: Boolean = true
)

/**
 * Stable persisted string values. Never persist ordinals or enum `.name`.
 */
enum class DefaultInboxFilter(val persistedValue: String) {
    PENDING("pending"),
    REVIEWED("reviewed"),
    ARCHIVED("archived"),
    ALL("all");

    companion object {
        fun fromPersisted(value: String?): DefaultInboxFilter =
            entries.firstOrNull { it.persistedValue == value } ?: PENDING
    }
}

enum class PreferredExportFormat(val persistedValue: String) {
    ASK_EVERY_TIME("ask_every_time"),
    JSON("json"),
    PLAIN_TEXT("plain_text");

    companion object {
        fun fromPersisted(value: String?): PreferredExportFormat =
            entries.firstOrNull { it.persistedValue == value } ?: ASK_EVERY_TIME
    }
}

enum class TemporaryExportRetention(val persistedValue: String) {
    ONE_HOUR("one_hour"),
    TWENTY_FOUR_HOURS("twenty_four_hours"),
    SEVEN_DAYS("seven_days");

    companion object {
        fun fromPersisted(value: String?): TemporaryExportRetention =
            entries.firstOrNull { it.persistedValue == value } ?: TWENTY_FOUR_HOURS
    }
}

fun DefaultInboxFilter.toInboxFilter(): InboxFilter = when (this) {
    DefaultInboxFilter.PENDING -> InboxFilter.PENDING
    DefaultInboxFilter.REVIEWED -> InboxFilter.REVIEWED
    DefaultInboxFilter.ARCHIVED -> InboxFilter.ARCHIVED
    DefaultInboxFilter.ALL -> InboxFilter.ALL
}

/** Returns null for "ask every time", otherwise the concrete export format. */
fun PreferredExportFormat.toExportFormat(): ExportFormat? = when (this) {
    PreferredExportFormat.ASK_EVERY_TIME -> null
    PreferredExportFormat.JSON -> ExportFormat.JSON
    PreferredExportFormat.PLAIN_TEXT -> ExportFormat.PLAIN_TEXT
}

val TemporaryExportRetention.retentionMillis: Long
    get() = when (this) {
        TemporaryExportRetention.ONE_HOUR -> 60L * 60 * 1000
        TemporaryExportRetention.TWENTY_FOUR_HOURS -> 24L * 60 * 60 * 1000
        TemporaryExportRetention.SEVEN_DAYS -> 7L * 24 * 60 * 60 * 1000
    }
