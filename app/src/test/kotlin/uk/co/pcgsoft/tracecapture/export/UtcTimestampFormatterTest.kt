package uk.co.pcgsoft.tracecapture.export

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class UtcTimestampFormatterTest {

    private val formatter = UtcTimestampFormatter()

    @Test
    fun `formats epoch millis to correct UTC timestamp format`() {
        val timestamp = formatter.format(1609459200000L) // 2021-01-01T00:00:00Z
        assertEquals("2021-01-01T00:00:00Z", timestamp)
    }

    @Test
    fun `formats zero epoch`() {
        val timestamp = formatter.format(0L)
        assertEquals("1970-01-01T00:00:00Z", timestamp)
    }

    @Test
    fun `formats recent timestamp correctly`() {
        val timestamp = formatter.format(1640995200000L) // 2022-01-01T00:00:00Z
        assertEquals("2022-01-01T00:00:00Z", timestamp)
    }

    @Test
    fun `formatOrNull returns formatted string when value is present`() {
        val timestamp = formatter.formatOrNull(1609459200000L)
        assertEquals("2021-01-01T00:00:00Z", timestamp)
    }

    @Test
    fun `formatOrNull returns null when value is null`() {
        val timestamp = formatter.formatOrNull(null)
        assertNull(timestamp)
    }
}
