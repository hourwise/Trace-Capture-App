package uk.co.pcgsoft.tracecapture.export

import org.junit.Test
import org.junit.Assert.assertEquals

class ExportFormatTest {

    @Test
    fun `JSON has correct mimeType`() {
        assertEquals("application/json", ExportFormat.JSON.mimeType)
    }

    @Test
    fun `JSON has correct extension`() {
        assertEquals(".json", ExportFormat.JSON.extension)
    }

    @Test
    fun `PLAIN_TEXT has correct mimeType`() {
        assertEquals("text/plain", ExportFormat.PLAIN_TEXT.mimeType)
    }

    @Test
    fun `PLAIN_TEXT has correct extension`() {
        assertEquals(".txt", ExportFormat.PLAIN_TEXT.extension)
    }
}
