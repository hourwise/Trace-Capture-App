package uk.co.pcgsoft.tracecapture.export

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadableUtcDateFormatter @Inject constructor() {

    fun format(epochMillis: Long): String {
        val dateTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime()
        return dateTime.format(FORMATTER) + " UTC"
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.UK)
    }
}
