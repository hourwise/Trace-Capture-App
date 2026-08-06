package uk.co.pcgsoft.tracecapture.settings

/**
 * Single authoritative defaults object plus stable preference keys.
 */
object SettingsDefaults {

    const val DATASTORE_FILE_NAME = "trace_capture_settings"

    const val KEY_DEFAULT_INBOX_FILTER = "default_inbox_filter"
    const val KEY_PREFERRED_EXPORT_FORMAT = "preferred_export_format"
    const val KEY_EXIT_SELECTION_AFTER_SUCCESS = "exit_selection_after_successful_export"
    const val KEY_TEMPORARY_EXPORT_RETENTION = "temporary_export_retention"
    const val KEY_CONFIRM_BEFORE_RESET = "confirm_before_reset"

    val value: AppSettings = AppSettings()
}
