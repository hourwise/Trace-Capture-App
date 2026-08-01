package uk.co.pcgsoft.tracecapture.export

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtcTimestampFormatter @Inject constructor() {

    fun format(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    fun formatOrNull(epochMillis: Long?): String? = epochMillis?.let { format(it) }
}
