package uk.co.pcgsoft.tracecapture.export

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ExportLimitsTest {

    @Test
    fun `default limits have reasonable maxCaptures`() {
        val limits = ExportLimits()
        assertEquals(10_000, limits.maxCaptures)
        assertTrue(limits.maxCaptures > 100)
    }

    @Test
    fun `default limits have reasonable maxOutputBytes`() {
        val limits = ExportLimits()
        val fiftyMegabytes = 50L * 1024 * 1024
        assertEquals(fiftyMegabytes, limits.maxOutputBytes)
        assertTrue(limits.maxOutputBytes > 1_000_000L)
    }

    @Test
    fun `can create limits with custom values`() {
        val customLimits = ExportLimits(maxCaptures = 100, maxOutputBytes = 1_000_000L)
        assertEquals(100, customLimits.maxCaptures)
        assertEquals(1_000_000L, customLimits.maxOutputBytes)
    }

    @Test
    fun `maxCaptures is reasonable for export use case`() {
        val limits = ExportLimits()
        // 10,000 captures should be sufficient for most use cases
        assertTrue(limits.maxCaptures >= 1000)
    }

    @Test
    fun `maxOutputBytes is reasonable for export use case`() {
        val limits = ExportLimits()
        // 50MB should be reasonable for JSON/text export
        assertTrue(limits.maxOutputBytes >= 10_000_000L)
    }
}
