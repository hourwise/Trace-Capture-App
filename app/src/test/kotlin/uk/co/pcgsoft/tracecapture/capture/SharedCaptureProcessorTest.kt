package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.domain.CaptureType

class SharedCaptureProcessorTest {

    private val intentParser = mockk<ShareIntentParser>()
    private val urlExtractor = mockk<UrlExtractor>()
    private val sourceResolver = mockk<SourceApplicationResolver>()
    private val processor = SharedCaptureProcessorImpl(intentParser, urlExtractor, sourceResolver)

    @Test
    fun `process returns draft when all components succeed`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Success(
            SharedContent(textContent = "Check https://example.com", sourcePackageHint = "com.example.app")
        )
        every { urlExtractor.extractUrls("Check https://example.com") } returns UrlExtractionResult(
            urls = listOf("https://example.com"),
            primaryUrl = "https://example.com",
            captureType = CaptureType.URL_WITH_TEXT
        )
        every { sourceResolver.resolve("com.example.app") } returns SourceApplicationInfo(
            packageName = "com.example.app",
            displayLabel = "Example App"
        )

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Ready)
        val draft = (result as SharedCaptureResult.Ready).draft
        assertEquals("Check https://example.com", draft.originalContent)
        assertEquals("https://example.com", draft.primaryUrl)
        assertEquals(listOf("https://example.com"), draft.detectedUrls)
        assertEquals("com.example.app", draft.sourcePackageName)
        assertEquals("Example App", draft.sourceLabel)
        assertEquals(CaptureType.URL_WITH_TEXT, draft.captureType)
    }

    @Test
    fun `process returns rejected when parser rejects`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Rejected(ShareRejectionReason.UNSUPPORTED_ACTION)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Rejected)
        assertEquals(ShareRejectionReason.UNSUPPORTED_ACTION, (result as SharedCaptureResult.Rejected).reason)
    }

    @Test
    fun `process rejects on unsupported mime`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Rejected(ShareRejectionReason.UNSUPPORTED_MIME_TYPE)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Rejected)
        assertEquals(ShareRejectionReason.UNSUPPORTED_MIME_TYPE, (result as SharedCaptureResult.Rejected).reason)
    }

    @Test
    fun `process rejects on missing content`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Rejected(ShareRejectionReason.MISSING_CONTENT)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Rejected)
        assertEquals(ShareRejectionReason.MISSING_CONTENT, (result as SharedCaptureResult.Rejected).reason)
    }

    @Test
    fun `process rejects on blank content`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Rejected(ShareRejectionReason.BLANK_CONTENT)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Rejected)
        assertEquals(ShareRejectionReason.BLANK_CONTENT, (result as SharedCaptureResult.Rejected).reason)
    }

    @Test
    fun `process rejects on content too long`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Rejected(ShareRejectionReason.CONTENT_TOO_LONG)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Rejected)
        assertEquals(ShareRejectionReason.CONTENT_TOO_LONG, (result as SharedCaptureResult.Rejected).reason)
    }

    @Test
    fun `process returns draft with null source when resolver returns null`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Success(
            SharedContent(textContent = "hello", sourcePackageHint = null)
        )
        every { urlExtractor.extractUrls("hello") } returns UrlExtractionResult(
            urls = emptyList(),
            primaryUrl = null,
            captureType = CaptureType.TEXT
        )
        every { sourceResolver.resolve(null) } returns SourceApplicationInfo(null, null)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Ready)
        val draft = (result as SharedCaptureResult.Ready).draft
        assertEquals("hello", draft.originalContent)
    }

    @Test
    fun `process passes through multiple URLs`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentParseResult.Success(
            SharedContent(textContent = "https://a.com https://b.com", sourcePackageHint = null)
        )
        every { urlExtractor.extractUrls("https://a.com https://b.com") } returns UrlExtractionResult(
            urls = listOf("https://a.com", "https://b.com"),
            primaryUrl = "https://a.com",
            captureType = CaptureType.MULTIPLE_URLS
        )
        every { sourceResolver.resolve(null) } returns SourceApplicationInfo(null, null)

        val result = processor.process(intent)
        assertTrue(result is SharedCaptureResult.Ready)
        val draft = (result as SharedCaptureResult.Ready).draft
        assertEquals(2, draft.detectedUrls.size)
        assertEquals(CaptureType.MULTIPLE_URLS, draft.captureType)
    }
}
