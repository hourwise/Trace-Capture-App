package uk.co.pcgsoft.tracecapture.export.file

import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.export.ExportFormat
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportFileNameFactory @Inject constructor() {

    fun create(
        captures: List<CaptureItem>,
        exportedAtEpochMillis: Long,
        format: ExportFormat
    ): String {
        val stamp = timestamp(exportedAtEpochMillis)
        if (captures.size == 1) {
            val slug = slugFor(captures.first())
            if (slug.isNotEmpty()) {
                return "trace-capture-$slug-$stamp${format.extension}"
            }
        }
        return "trace-capture-$stamp${format.extension}"
    }

    private fun timestamp(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .format(TIMESTAMP_FORMATTER)
    }

    private fun slugFor(capture: CaptureItem): String {
        val raw = capture.primaryUrl?.let { hostOf(it) } ?: capture.sourceLabel
        return slugify(raw ?: "").take(MAX_SLUG_LENGTH)
    }

    private fun hostOf(url: String): String? {
        return try {
            URI(url).host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun slugify(value: String): String {
        return value.lowercase(Locale.ROOT)
            .map { ch -> if (ch.isLetterOrDigit() && ch.code < 128) ch else '-' }
            .joinToString("")
            .replace(MULTIPLE_SEPARATORS, "-")
            .trim('-')
    }

    private companion object {
        const val MAX_SLUG_LENGTH = 24
        val MULTIPLE_SEPARATORS = Regex("-+")
        val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS", Locale.ROOT)
    }
}
