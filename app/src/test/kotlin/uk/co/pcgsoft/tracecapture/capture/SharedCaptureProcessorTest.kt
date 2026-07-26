package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        every { intentParser.parse(intent) } returns ShareIntentResult(
            textContent = "Check https://example.com",
            sourcePackageHint = "com.example.app"
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

        val draft = processor.process(intent)
        assertNotNull(draft)
        assertEquals("Check https://example.com", draft?.originalContent)
        assertEquals("https://example.com", draft?.primaryUrl)
        assertEquals(listOf("https://example.com"), draft?.detectedUrls)
        assertEquals("com.example.app", draft?.sourcePackageName)
        assertEquals("Example App", draft?.sourceLabel)
        assertEquals(CaptureType.URL_WITH_TEXT, draft?.captureType)
    }

    @Test
    fun `process returns null when parser returns null`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns null

        assertNull(processor.process(intent))
    }

    @Test
    fun `process returns draft with null source when resolver receives null`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentResult(
            textContent = "hello",
            sourcePackageHint = null
        )
        every { urlExtractor.extractUrls("hello") } returns UrlExtractionResult(
            urls = emptyList(),
            primaryUrl = null,
            captureType = CaptureType.TEXT
        )
        every { sourceResolver.resolve(null) } returns SourceApplicationInfo(null, null)

        val draft = processor.process(intent)
        assertNotNull(draft)
        assertEquals("hello", draft?.originalContent)
        assertNull(draft?.sourcePackageName)
        assertNull(draft?.sourceLabel)
    }

    @Test
    fun `process passes through multiple URLs`() {
        val intent = mockk<Intent>()
        every { intentParser.parse(intent) } returns ShareIntentResult(
            textContent = "https://a.com https://b.com",
            sourcePackageHint = null
        )
        every { urlExtractor.extractUrls("https://a.com https://b.com") } returns UrlExtractionResult(
            urls = listOf("https://a.com", "https://b.com"),
            primaryUrl = "https://a.com",
            captureType = CaptureType.MULTIPLE_URLS
        )
        every { sourceResolver.resolve(null) } returns SourceApplicationInfo(null, null)

        val draft = processor.process(intent)
        assertNotNull(draft)
        assertEquals(2, draft?.detectedUrls?.size)
        assertEquals(CaptureType.MULTIPLE_URLS, draft?.captureType)
    }
}
