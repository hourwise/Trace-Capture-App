package uk.co.pcgsoft.tracecapture.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.domain.CaptureType

class UrlExtractorTest {

    private val extractor = UrlExtractorImpl()

    @Test
    fun `extract single plain URL`() {
        val result = extractor.extractUrls("https://example.com/page")
        assertEquals(listOf("https://example.com/page"), result.urls)
        assertEquals("https://example.com/page", result.primaryUrl)
        assertEquals(CaptureType.URL, result.captureType)
    }

    @Test
    fun `extract URL with trailing period`() {
        val result = extractor.extractUrls("Visit https://example.com.")
        assertEquals(listOf("https://example.com"), result.urls)
        assertEquals(CaptureType.URL_WITH_TEXT, result.captureType)
    }

    @Test
    fun `extract URL with trailing comma`() {
        val result = extractor.extractUrls("See https://example.com, for details")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `extract URL with trailing closing parenthesis`() {
        val result = extractor.extractUrls("(check https://example.com)")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `extract URL with trailing exclamation`() {
        val result = extractor.extractUrls("Wow https://example.com!")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `extract URL with trailing question mark`() {
        val result = extractor.extractUrls("Did you see https://example.com?")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `extract multiple URLs from text`() {
        val result = extractor.extractUrls("https://a.com and https://b.com/path")
        assertEquals(listOf("https://a.com", "https://b.com/path"), result.urls)
        assertEquals(CaptureType.MULTIPLE_URLS, result.captureType)
    }

    @Test
    fun `dedup identical URLs`() {
        val result = extractor.extractUrls("https://example.com https://example.com")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `dedup after normalisation`() {
        val result = extractor.extractUrls("https://Example.com https://example.com")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `extract URL with query parameters`() {
        val result = extractor.extractUrls("https://example.com?q=hello&lang=en")
        assertEquals(listOf("https://example.com?q=hello&lang=en"), result.urls)
    }

    @Test
    fun `extract URL with fragment`() {
        val result = extractor.extractUrls("https://example.com#section")
        assertEquals(listOf("https://example.com#section"), result.urls)
    }

    @Test
    fun `strip empty fragment`() {
        val result = extractor.extractUrls("https://example.com#")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `strip empty query`() {
        val result = extractor.extractUrls("https://example.com?")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `lowercase scheme and host`() {
        val result = extractor.extractUrls("HTTPS://EXAMPLE.COM/Path")
        assertEquals(listOf("https://example.com/Path"), result.urls)
    }

    @Test
    fun `no URLs in plain text`() {
        val result = extractor.extractUrls("hello world this is plain text")
        assertTrue(result.urls.isEmpty())
        assertNull(result.primaryUrl)
        assertEquals(CaptureType.TEXT, result.captureType)
    }

    @Test
    fun `no URLs in empty string`() {
        val result = extractor.extractUrls("")
        assertTrue(result.urls.isEmpty())
        assertEquals(CaptureType.UNKNOWN, result.captureType)
    }

    @Test
    fun `no URLs in blank string`() {
        val result = extractor.extractUrls("   ")
        assertTrue(result.urls.isEmpty())
        assertEquals(CaptureType.UNKNOWN, result.captureType)
    }

    @Test
    fun `reject non-http scheme`() {
        val result = extractor.extractUrls("ftp://example.com")
        assertTrue(result.urls.isEmpty())
        assertEquals(CaptureType.TEXT, result.captureType)
    }

    @Test
    fun `reject javascript scheme`() {
        val result = extractor.extractUrls("javascript:void(0)")
        assertTrue(result.urls.isEmpty())
    }

    @Test
    fun `limit to 25 URLs`() {
        val urls = (1..30).joinToString(" ") { "https://example.com/$it" }
        val result = extractor.extractUrls(urls)
        assertEquals(25, result.urls.size)
    }

    @Test
    fun `URL with path`() {
        val result = extractor.extractUrls("https://example.com/a/b/c")
        assertEquals(listOf("https://example.com/a/b/c"), result.urls)
    }

    @Test
    fun `URL with port`() {
        val result = extractor.extractUrls("https://example.com:8080/path")
        assertEquals(listOf("https://example.com:8080/path"), result.urls)
    }

    @Test
    fun `URL with special chars in path`() {
        val result = extractor.extractUrls("https://example.com/path%20with%20spaces")
        assertEquals(listOf("https://example.com/path%20with%20spaces"), result.urls)
    }

    @Test
    fun `URL adjacent to Chinese text`() {
        val result = extractor.extractUrls("检查 https://example.com 的页面")
        assertEquals(listOf("https://example.com"), result.urls)
        assertEquals(CaptureType.URL_WITH_TEXT, result.captureType)
    }

    @Test
    fun `URL in angle brackets`() {
        val result = extractor.extractUrls("<https://example.com>")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `URL with trailing single quote`() {
        val result = extractor.extractUrls("check 'https://example.com' here")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `URL with trailing double quote`() {
        val result = extractor.extractUrls("check \"https://example.com\" here")
        assertEquals(listOf("https://example.com"), result.urls)
    }

    @Test
    fun `multiple URLs deduped preserving order`() {
        val result = extractor.extractUrls("https://c.com https://a.com https://b.com https://a.com")
        assertEquals(listOf("https://c.com", "https://a.com", "https://b.com"), result.urls)
    }

    @Test
    fun `single URL only text classifies as URL`() {
        val result = extractor.extractUrls("https://example.com")
        assertEquals(CaptureType.URL, result.captureType)
        assertEquals("https://example.com", result.primaryUrl)
    }

    @Test
    fun `URL with surrounding whitespace classifies as URL`() {
        val result = extractor.extractUrls("  https://example.com  ")
        assertEquals(CaptureType.URL, result.captureType)
    }

    @Test
    fun `URL with trailing period classifies as URL`() {
        val result = extractor.extractUrls("https://example.com.")
        assertEquals(CaptureType.URL, result.captureType)
        assertEquals("https://example.com", result.primaryUrl)
    }

    @Test
    fun `URL with text classifies as URL_WITH_TEXT`() {
        val result = extractor.extractUrls("Check this out: https://example.com")
        assertEquals(CaptureType.URL_WITH_TEXT, result.captureType)
    }

    @Test
    fun `trimTrailingPunctuation removes common punctuation`() {
        assertEquals("https://example.com", extractor.trimTrailingPunctuation("https://example.com."))
        assertEquals("https://example.com", extractor.trimTrailingPunctuation("https://example.com)"))
        assertEquals("https://example.com", extractor.trimTrailingPunctuation("https://example.com'"))
    }

    @Test
    fun `trimTrailingPunctuation leaves valid URLs unchanged`() {
        assertEquals("https://example.com/path", extractor.trimTrailingPunctuation("https://example.com/path"))
    }

    @Test
    fun `normaliseUrl returns null for blank input`() {
        assertNull(extractor.normaliseUrl(""))
    }

    @Test
    fun `normaliseUrl returns null for invalid URI`() {
        assertNull(extractor.normaliseUrl("https://"))
    }

    @Test
    fun `normaliseUrl returns null for non-http scheme`() {
        assertNull(extractor.normaliseUrl("ftp://example.com"))
    }

    @Test
    fun `normaliseUrl handles path`() {
        val result = extractor.normaliseUrl("https://Example.COM/PATH")
        assertEquals("https://example.com/PATH", result)
    }

    @Test
    fun `text with only non-http URL classifies as TEXT`() {
        val result = extractor.extractUrls("ftp://example.com/file.txt")
        assertEquals(CaptureType.TEXT, result.captureType)
    }

    @Test
    fun `text with no URLs and no meaningful content is UNKNOWN`() {
        val result = extractor.extractUrls("   ")
        assertEquals(CaptureType.UNKNOWN, result.captureType)
    }

    @Test
    fun `text with no URLs is TEXT`() {
        val result = extractor.extractUrls("hello world")
        assertEquals(CaptureType.TEXT, result.captureType)
    }

    @Test
    fun `result has correct primaryUrl when no URLs found`() {
        val result = extractor.extractUrls("just text")
        assertNull(result.primaryUrl)
    }

    @Test
    fun `result has correct primaryUrl as first URL`() {
        val result = extractor.extractUrls("https://first.com and https://second.com")
        assertEquals("https://first.com", result.primaryUrl)
    }
}
