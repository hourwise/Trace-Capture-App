package uk.co.pcgsoft.tracecapture.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemEntity
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemFactory
import uk.co.pcgsoft.tracecapture.data.local.CaptureValidator
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus

/**
 * Focused repository-level tests for `getActiveByIds` chunking and ordering.
 * The Room SQL itself (deleted-at filtering, unknown-ID exclusion, no duplicate
 * rows from duplicate binds) is covered by the instrumented DAO tests; the chunk
 * boundary against a real Room database is covered in `CaptureItemDaoTest`.
 */
class RoomCaptureRepositoryTest {

    private val dao = mockk<CaptureItemDao>(relaxed = true)
    private val repository = RoomCaptureRepository(dao, CaptureValidator(), CaptureItemFactory())

    @Test
    fun `empty ids return empty list without querying dao`() = runTest {
        val result = repository.getActiveByIds(emptySet())
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { dao.getActiveByIds(any()) }
    }

    @Test
    fun `single id queries exactly one chunk`() = runTest {
        coEvery { dao.getActiveByIds(listOf("id-1")) } returns listOf(entity("id-1", 1_000L))

        val result = repository.getActiveByIds(setOf("id-1"))

        assertEquals(listOf("id-1"), result.map { it.id })
        coVerify(exactly = 1) { dao.getActiveByIds(listOf("id-1")) }
    }

    @Test
    fun `900 ids fit in a single chunk`() = runTest {
        val ids = (1..900).map { "id-$it" }
        val captured = mutableListOf<List<String>>()
        coEvery { dao.getActiveByIds(any()) } coAnswers {
            val chunk = firstArg<List<String>>()
            captured += chunk
            chunk.map { entity(it, it.removePrefix("id-").toLong()) }
        }

        val result = repository.getActiveByIds(ids.toSet())

        assertEquals(900, result.size)
        assertEquals(listOf(900), captured.map { it.size })
    }

    @Test
    fun `901 ids are split into two chunks`() = runTest {
        val ids = (1..901).map { "id-$it" }
        val captured = mutableListOf<List<String>>()
        coEvery { dao.getActiveByIds(any()) } coAnswers {
            val chunk = firstArg<List<String>>()
            captured += chunk
            chunk.map { entity(it, it.removePrefix("id-").toLong()) }
        }

        val result = repository.getActiveByIds(ids.toSet())

        assertEquals(901, result.size)
        assertEquals(listOf(900, 1), captured.map { it.size })
    }

    @Test
    fun `1801 ids are split into three chunks`() = runTest {
        val ids = (1..1801).map { "id-$it" }
        val captured = mutableListOf<List<String>>()
        coEvery { dao.getActiveByIds(any()) } coAnswers {
            val chunk = firstArg<List<String>>()
            captured += chunk
            chunk.map { entity(it, it.removePrefix("id-").toLong()) }
        }

        val result = repository.getActiveByIds(ids.toSet())

        assertEquals(1801, result.size)
        assertEquals(listOf(900, 900, 1), captured.map { it.size })
    }

    @Test
    fun `duplicate input ids never duplicate results`() = runTest {
        // Even if the DAO returns one row per unique id across the chunk, the result
        // must contain each capture exactly once.
        coEvery { dao.getActiveByIds(any()) } coAnswers {
            firstArg<List<String>>().distinct().map { entity(it, 1_000L) }
        }

        val input = listOf("a", "b", "a", "c", "b", "a")
        val result = repository.getActiveByIds(input.toSet())

        assertEquals(3, result.size)
        assertEquals(setOf("a", "b", "c"), result.map { it.id }.toSet())
    }

    @Test
    fun `deleted and unknown ids are never re-added`() = runTest {
        // The DAO is authoritative for the deleted_at IS NULL + IN filter; the
        // repository must not invent rows for missing ids.
        coEvery { dao.getActiveByIds(any()) } returns listOf(entity("known", 1_000L))

        val result = repository.getActiveByIds(setOf("known", "deleted", "unknown"))

        assertEquals(listOf("known"), result.map { it.id })
    }

    @Test
    fun `results are ordered newest-first with deterministic id fallback`() = runTest {
        val entities = listOf(
            entity("old", 1_000L),
            entity("new", 3_000L),
            entity("mid", 2_000L),
            entity("tie-a", 2_000L),
            entity("tie-b", 2_000L)
        )
        coEvery { dao.getActiveByIds(any()) } returns entities

        val result = repository.getActiveByIds(setOf("old", "new", "mid", "tie-a", "tie-b"))

        // newest first, then id DESC for equal timestamps
        assertEquals(listOf("new", "tie-b", "tie-a", "mid", "old"), result.map { it.id })
    }

    private fun entity(id: String, createdAt: Long) = CaptureItemEntity(
        id = id,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
        originalContent = id,
        captureType = CaptureType.TEXT,
        status = CaptureStatus.PENDING,
        primaryUrl = null,
        detectedUrlsJson = null,
        sourcePackageName = null,
        sourceLabel = null,
        note = null,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )
}
