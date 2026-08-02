package uk.co.pcgsoft.tracecapture.export

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class ReadableUtcDateFormatterTest {

    private val formatter = ReadableUtcDateFormatter()

    @Test
    fun `formats epoch millis to readable UTC date format`() {
        val formatted = formatter.format(1609459200000L) // 2021-01-01T00:00:00Z
        assertTrue(formatted.contains("2021"))
        assertTrue(formatted.contains("January"))
        assertTrue(formatted.contains("UTC"))
    }

    @Test
    fun `handles zero epoch`() {
        val formatted = formatter.format(0L)
        assertTrue(formatted.contains("1970"))
        assertTrue(formatted.contains("UTC"))
    }

    @Test
    fun `handles different timestamps with consistent format`() {
        val formatted1 = formatter.format(1640995200000L) // 2022-01-01T00:00:00Z
        val formatted2 = formatter.format(1672531200000L) // 2023-01-01T00:00:00Z

        // Both should end with UTC
        assertTrue(formatted1.endsWith("UTC"))
        assertTrue(formatted2.endsWith("UTC"))

        // Format includes day, month, year, and time
        assertTrue(formatted1.contains("2022"))
        assertTrue(formatted1.contains("January"))
        assertTrue(formatted2.contains("2023"))
        assertTrue(formatted2.contains("January"))
    }

    @Test
    fun `produces consistent output for same timestamp`() {
        val timestamp = 1609459200000L
        val first = formatter.format(timestamp)
        val second = formatter.format(timestamp)

        assertEquals(first, second)
    }

    @Test
    fun `formats include hour and minute`() {
        val formatted = formatter.format(1609462800000L) // 2021-01-01T01:00:00Z
        // Should contain time in HH:mm format
        assertTrue(formatted.matches(Regex(".*\\d{2}:\\d{2}.*")))
    }
}
