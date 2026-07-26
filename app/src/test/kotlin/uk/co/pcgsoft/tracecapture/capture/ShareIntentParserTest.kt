package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentParserTest {

    private val parser = ShareIntentParserImpl()

    @Test
    fun `parse ACTION_SEND with text_plain`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns "https://example.com"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertNotNull(result)
        assertEquals("https://example.com", result?.textContent)
        assertNull(result?.sourcePackageHint)
    }

    @Test
    fun `parse with null MIME type`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns null
            every { getStringExtra(Intent.EXTRA_TEXT) } returns "hello"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertNotNull(result)
        assertEquals("hello", result?.textContent)
    }

    @Test
    fun `reject non SEND action`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_VIEW
        }
        assertNull(parser.parse(intent))
    }

    @Test
    fun `reject non-text MIME type`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "image/png"
        }
        assertNull(parser.parse(intent))
    }

    @Test
    fun `reject null text content`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns null
        }
        assertNull(parser.parse(intent))
    }

    @Test
    fun `reject blank text content`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns "   "
        }
        assertNull(parser.parse(intent))
    }

    @Test
    fun `read source package hint from referrer`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns "content"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns "com.android.chrome"
        }
        val result = parser.parse(intent)
        assertNotNull(result)
        assertEquals("com.android.chrome", result?.sourcePackageHint)
    }

    @Test
    fun `reject content exceeding max length`() {
        val longText = "a".repeat(100_001)
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns longText
        }
        assertNull(parser.parse(intent))
    }

    @Test
    fun `accept content at max length`() {
        val longText = "a".repeat(100_000)
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getStringExtra(Intent.EXTRA_TEXT) } returns longText
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertNotNull(result)
        assertEquals(100_000, result?.textContent?.length)
    }

    @Test
    fun `parse returns null for null action`() {
        val intent = mockk<Intent> {
            every { action } returns null
        }
        assertNull(parser.parse(intent))
    }
}
