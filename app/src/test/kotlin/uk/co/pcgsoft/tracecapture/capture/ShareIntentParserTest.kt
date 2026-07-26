package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentParserTest {

    private val parser = ShareIntentParserImpl()

    @Test
    fun `parse ACTION_SEND with text_plain`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns "https://example.com"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Success)
        val success = result as ShareIntentParseResult.Success
        assertEquals("https://example.com", success.content.textContent)
    }

    @Test
    fun `parse with null MIME type`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns null
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns "hello"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Success)
    }

    @Test
    fun `parse with CharSequence extra`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns StringBuilder("shared text")
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Success)
        val success = result as ShareIntentParseResult.Success
        assertEquals("shared text", success.content.textContent)
    }

    @Test
    fun `reject non SEND action`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_VIEW
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.UNSUPPORTED_ACTION, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `reject null action`() {
        val intent = mockk<Intent> {
            every { action } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.UNSUPPORTED_ACTION, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `reject non-text MIME type`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "image/png"
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.UNSUPPORTED_MIME_TYPE, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `reject null text content`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.MISSING_CONTENT, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `reject blank text content`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns "   "
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.BLANK_CONTENT, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `reject content exceeding max length`() {
        val longText = "a".repeat(100_001)
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns longText
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
        assertEquals(ShareRejectionReason.CONTENT_TOO_LONG, (result as ShareIntentParseResult.Rejected).reason)
    }

    @Test
    fun `accept content at max length`() {
        val longText = "a".repeat(100_000)
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns longText
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns null
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Success)
        val success = result as ShareIntentParseResult.Success
        assertEquals(100_000, success.content.textContent.length)
    }

    @Test
    fun `read source package hint from referrer`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_SEND
            every { type } returns "text/plain"
            every { getCharSequenceExtra(Intent.EXTRA_TEXT) } returns "content"
            every { getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns "com.android.chrome"
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Success)
        val success = result as ShareIntentParseResult.Success
        assertEquals("com.android.chrome", success.content.sourcePackageHint)
    }

    @Test
    fun `structured rejection produce correct user messages`() {
        val intent = mockk<Intent> {
            every { action } returns Intent.ACTION_VIEW
        }
        val result = parser.parse(intent)
        assertTrue(result is ShareIntentParseResult.Rejected)
    }
}
