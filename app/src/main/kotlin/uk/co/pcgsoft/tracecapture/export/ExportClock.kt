package uk.co.pcgsoft.tracecapture.export

import javax.inject.Inject
import javax.inject.Singleton

interface ExportClock {
    fun nowEpochMillis(): Long
}

@Singleton
class SystemExportClock @Inject constructor() : ExportClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
