package uk.co.pcgsoft.tracecapture.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {

    @Test
    fun defaultsAreAuthoritative() {
        val defaults = SettingsDefaults.value
        assertEquals(DefaultInboxFilter.PENDING, defaults.defaultInboxFilter)
        assertEquals(PreferredExportFormat.ASK_EVERY_TIME, defaults.preferredExportFormat)
        assertTrue(defaults.exitSelectionAfterSuccessfulExport)
        assertEquals(TemporaryExportRetention.TWENTY_FOUR_HOURS, defaults.temporaryExportRetention)
        assertTrue(defaults.confirmBeforeReset)
    }

    @Test
    fun defaultInboxFilterPersistedValuesRoundTrip() {
        DefaultInboxFilter.entries.forEach { filter ->
            assertEquals(filter, DefaultInboxFilter.fromPersisted(filter.persistedValue))
        }
    }

    @Test
    fun preferredExportFormatPersistedValuesRoundTrip() {
        PreferredExportFormat.entries.forEach { format ->
            assertEquals(format, PreferredExportFormat.fromPersisted(format.persistedValue))
        }
    }

    @Test
    fun temporaryExportRetentionPersistedValuesRoundTrip() {
        TemporaryExportRetention.entries.forEach { retention ->
            assertEquals(retention, TemporaryExportRetention.fromPersisted(retention.persistedValue))
        }
    }

    @Test
    fun unknownValuesFallBackSafely() {
        assertEquals(DefaultInboxFilter.PENDING, DefaultInboxFilter.fromPersisted("future-value"))
        assertEquals(PreferredExportFormat.ASK_EVERY_TIME, PreferredExportFormat.fromPersisted("future-value"))
        assertEquals(TemporaryExportRetention.TWENTY_FOUR_HOURS, TemporaryExportRetention.fromPersisted("future-value"))
        assertEquals(DefaultInboxFilter.PENDING, DefaultInboxFilter.fromPersisted(null))
        assertEquals(PreferredExportFormat.ASK_EVERY_TIME, PreferredExportFormat.fromPersisted(null))
        assertEquals(TemporaryExportRetention.TWENTY_FOUR_HOURS, TemporaryExportRetention.fromPersisted(null))
    }

    @Test
    fun noOrdinalPersistence() {
        assertEquals(
            listOf("pending", "reviewed", "archived", "all"),
            DefaultInboxFilter.entries.map { it.persistedValue }
        )
        assertEquals(
            listOf("ask_every_time", "json", "plain_text"),
            PreferredExportFormat.entries.map { it.persistedValue }
        )
        assertEquals(
            listOf("one_hour", "twenty_four_hours", "seven_days"),
            TemporaryExportRetention.entries.map { it.persistedValue }
        )
    }

    @Test
    fun persistedValuesDoNotDependOnEnumNames() {
        // Enum ordinals/names must never be the persisted contract.
        assertEquals("pending", DefaultInboxFilter.PENDING.persistedValue)
        assertEquals("ask_every_time", PreferredExportFormat.ASK_EVERY_TIME.persistedValue)
        assertEquals("twenty_four_hours", TemporaryExportRetention.TWENTY_FOUR_HOURS.persistedValue)
    }
}
