package uk.co.pcgsoft.tracecapture.export.share

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.pcgsoft.tracecapture.settings.FakeSettingsRepository
import uk.co.pcgsoft.tracecapture.settings.SettingsDefaults
import uk.co.pcgsoft.tracecapture.settings.SettingsRepository
import uk.co.pcgsoft.tracecapture.settings.TemporaryExportRetention
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExportCacheRetentionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = 2_000_000_000_000L
    private val oneHour = 60L * 60 * 1000
    private val oneDay = 24L * oneHour

    private fun createFile(dir: File, name: String, lastModified: Long): File {
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(1))
        file.setLastModified(lastModified)
        return file
    }

    @Test
    fun oneHourCutoff() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-old.json", now - 2 * oneHour)
        createFile(dir, "trace-capture-new.json", now - 30 * 60 * 1000)

        val deleted = cleaner.cleanup(dir, maxAgeMillis = oneHour, now = now)

        assertEquals(1, deleted)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun twentyFourHourCutoff() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-2d.json", now - 2 * oneDay)
        createFile(dir, "trace-capture-1h.json", now - oneHour)

        val deleted = cleaner.cleanup(dir, maxAgeMillis = oneDay, now = now)

        assertEquals(1, deleted)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun sevenDayCutoff() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-8d.json", now - 8 * oneDay)
        createFile(dir, "trace-capture-2d.json", now - 2 * oneDay)

        val deleted = cleaner.cleanup(dir, maxAgeMillis = 7 * oneDay, now = now)

        assertEquals(1, deleted)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun cleanupWithCurrentRetentionUsesSetting() = runTest {
        val repo = FakeSettingsRepository(
            SettingsDefaults.value.copy(temporaryExportRetention = TemporaryExportRetention.ONE_HOUR)
        )
        val cleaner = ExportCacheCleaner(repo)
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-45m.json", now - 45 * 60 * 1000)
        createFile(dir, "trace-capture-90m.json", now - 90 * 60 * 1000)

        val deleted = cleaner.cleanupWithCurrentRetention(dir, now = now)

        assertEquals(1, deleted)
    }

    @Test
    fun invalidSettingFallsBackTo24h() = runTest {
        val failingRepo = mockk<SettingsRepository>()
        coEvery { failingRepo.settings } returns flow { throw IOException("boom") }
        val cleaner = ExportCacheCleaner(failingRepo)
        val dir = tempFolder.newFolder()
        // A 2-day-old file: deleted by the 24h fallback even though a 7-day policy would keep it.
        createFile(dir, "trace-capture-2d.json", now - 2 * oneDay)

        val deleted = cleaner.cleanupWithCurrentRetention(dir, now = now)

        assertEquals(1, deleted)
    }

    @Test
    fun onlyDedicatedExportFilesConsidered() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-export.json", now - 10 * oneDay)
        createFile(dir, "other-cache-file.bin", now - 10 * oneDay)
        createFile(dir, "user-document.txt", now - 10 * oneDay)

        val deleted = cleaner.cleanup(dir, maxAgeMillis = oneDay, now = now)

        assertEquals(1, deleted)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun youngerFilesRetained() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-young.json", now - 5 * 60 * 1000)

        val deleted = cleaner.cleanup(dir, maxAgeMillis = oneHour, now = now)

        assertEquals(0, deleted)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun deletionCountCorrect() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        repeat(5) { createFile(dir, "trace-capture-$it.json", now - 2 * oneDay) }

        val deleted = cleaner.cleanup(dir, maxAgeMillis = oneDay, now = now)

        assertEquals(5, deleted)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test
    fun deleteAllRemovesAllDedicatedExports() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-a.json", now)
        createFile(dir, "trace-capture-b.json", now)
        createFile(dir, "other.bin", now)

        val deleted = cleaner.deleteAll(dir)

        assertEquals(2, deleted)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun deleteAllDoesNotRemoveSavedDocuments() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val dir = tempFolder.newFolder()
        createFile(dir, "trace-capture-share.json", now)
        createFile(dir, "saved-document.json", now)
        createFile(dir, "saved-note.txt", now)

        val deleted = cleaner.deleteAll(dir)

        assertEquals(1, deleted)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun missingDirectoryReturnsZero() {
        val cleaner = ExportCacheCleaner(FakeSettingsRepository())
        val missing = File(tempFolder.root, "missing")
        assertEquals(0, cleaner.cleanup(missing, maxAgeMillis = oneDay, now = now))
        assertEquals(0, cleaner.deleteAll(missing))
    }
}
