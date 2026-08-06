package uk.co.pcgsoft.tracecapture.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import uk.co.pcgsoft.tracecapture.inbox.InboxFilter

class SettingsMappingTest {

    @Test
    fun defaultInboxFilterMapsToInboxFilter() {
        assertEquals(InboxFilter.PENDING, DefaultInboxFilter.PENDING.toInboxFilter())
        assertEquals(InboxFilter.REVIEWED, DefaultInboxFilter.REVIEWED.toInboxFilter())
        assertEquals(InboxFilter.ARCHIVED, DefaultInboxFilter.ARCHIVED.toInboxFilter())
        assertEquals(InboxFilter.ALL, DefaultInboxFilter.ALL.toInboxFilter())
    }

    @Test
    fun askEveryTimeMapsToNullFormat() {
        assertNull(PreferredExportFormat.ASK_EVERY_TIME.toExportFormat())
    }

    @Test
    fun concreteFormatsMapToExportFormat() {
        assertEquals(ExportFormat.JSON, PreferredExportFormat.JSON.toExportFormat())
        assertEquals(ExportFormat.PLAIN_TEXT, PreferredExportFormat.PLAIN_TEXT.toExportFormat())
    }

    @Test
    fun retentionMillisMapsCorrectly() {
        assertEquals(60L * 60 * 1000, TemporaryExportRetention.ONE_HOUR.retentionMillis)
        assertEquals(24L * 60 * 60 * 1000, TemporaryExportRetention.TWENTY_FOUR_HOURS.retentionMillis)
        assertEquals(7L * 24 * 60 * 60 * 1000, TemporaryExportRetention.SEVEN_DAYS.retentionMillis)
    }
}
