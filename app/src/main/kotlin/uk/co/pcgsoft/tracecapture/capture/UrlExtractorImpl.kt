package uk.co.pcgsoft.tracecapture.capture

import uk.co.pcgsoft.tracecapture.domain.CaptureType
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlExtractorImpl @Inject constructor() : UrlExtractor {

    companion object {
        private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '”', '’')
        private const val MAX_URLS = 25
    }

    override fun extractUrls(text: String): UrlExtractionResult {
        val rawMatches = URL_PATTERN.findAll(text).map { it.value }.toList()
        val normalised = rawMatches
            .map { trimTrailingPunctuation(it) }
            .mapNotNull { normaliseUrl(it) }
            .distinct()
            .take(MAX_URLS)

        val primaryUrl = normalised.firstOrNull()
        val captureType = classify(text, normalised)
        return UrlExtractionResult(
            urls = normalised,
            primaryUrl = primaryUrl,
            captureType = captureType
        )
    }

    internal fun trimTrailingPunctuation(candidate: String): String {
        var result = candidate
        while (result.isNotEmpty() && result.last() in TRAILING_PUNCTUATION) {
            result = result.dropLast(1)
        }
        return result
    }

    internal fun normaliseUrl(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val uri = URI(raw).normalize()
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host.isBlank()) return null
            val port = uri.port
            var path = uri.rawPath ?: ""
            val query = uri.rawQuery
            val fragment = uri.rawFragment
            val portPart = if (port > -1 && port != 80 && port != 443) ":$port" else ""
            var result = "$scheme://$host$portPart$path"
            if (!query.isNullOrBlank()) result += "?$query"
            if (!fragment.isNullOrBlank()) result += "#$fragment"
            result
        } catch (_: Exception) {
            null
        }
    }

    internal fun classify(text: String, urls: List<String>): CaptureType {
        return when {
            urls.isEmpty() -> if (text.isNotBlank()) CaptureType.TEXT else CaptureType.UNKNOWN
            urls.size > 1 -> CaptureType.MULTIPLE_URLS
            else -> {
                val trimmed = text.trim()
            val urlOnly = urls.single().let { raw ->
                trimmed == raw || trimTrailingPunctuation(trimmed) == raw
            }
                if (urlOnly) CaptureType.URL else CaptureType.URL_WITH_TEXT
            }
        }
    }

}
