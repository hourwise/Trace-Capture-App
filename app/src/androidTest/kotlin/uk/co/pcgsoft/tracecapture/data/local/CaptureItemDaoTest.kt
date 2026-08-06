package uk.co.pcgsoft.tracecapture.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.data.repository.RoomCaptureRepository
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CaptureItemDaoTest {

    private lateinit var database: TraceCaptureDatabase
    private lateinit var dao: CaptureItemDao

    private val factory = CaptureItemFactory()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TraceCaptureDatabase::class.java
        ).build()
        dao = database.captureItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createItem(
        id: String = "id-${UUID.randomUUID()}",
        content: String = "content",
        url: String? = null,
        note: String? = null,
        detectedUrls: List<String> = if (url != null) listOf(url) else emptyList(),
        type: CaptureType = if (url != null) CaptureType.URL_WITH_TEXT else CaptureType.TEXT
    ) = factory.create(
        originalContent = content,
        primaryUrl = url,
        detectedUrls = detectedUrls,
        sourcePackageName = null,
        sourceLabel = null,
        note = note,
        captureType = type
    )

    @Test
    fun insertAndRead() = runTest {
        val item = createItem(id = "test-1")
        dao.insert(item.toEntity())
        val loaded = dao.getById("test-1")
        assertNotNull(loaded)
        assertEquals(item.originalContent, loaded!!.originalContent)
    }

    @Test
    fun updateNote() = runTest {
        val item = createItem(id = "test-2")
        dao.insert(item.toEntity())
        dao.updateNote("test-2", "updated note", System.currentTimeMillis())
        val loaded = dao.getById("test-2")
        assertEquals("updated note", loaded!!.note)
    }

    @Test
    fun markReviewed() = runTest {
        val item = createItem(id = "test-3")
        dao.insert(item.toEntity())
        dao.markReviewed("test-3", System.currentTimeMillis())
        val loaded = dao.getById("test-3")
        assertEquals(CaptureStatus.REVIEWED, loaded!!.status)
    }

    @Test
    fun archiveSetsStatusAndArchivedAt() = runTest {
        val item = createItem(id = "test-4")
        dao.insert(item.toEntity())
        dao.archive("test-4", System.currentTimeMillis())
        val loaded = dao.getById("test-4")
        assertEquals(CaptureStatus.ARCHIVED, loaded!!.status)
        assertNotNull(loaded.archivedAtEpochMillis)
    }

    @Test
    fun restoreFromArchive() = runTest {
        val item = createItem(id = "test-5")
        dao.insert(item.toEntity())
        dao.archive("test-5", System.currentTimeMillis())
        dao.restoreToPending("test-5", System.currentTimeMillis())
        val loaded = dao.getById("test-5")
        assertEquals(CaptureStatus.PENDING, loaded!!.status)
        assertNull(loaded.archivedAtEpochMillis)
    }

    @Test
    fun softDelete() = runTest {
        val item = createItem(id = "test-6")
        dao.insert(item.toEntity())
        dao.softDelete("test-6", System.currentTimeMillis())
        val loaded = dao.getById("test-6")
        assertNotNull(loaded!!.deletedAtEpochMillis)
    }

    @Test
    fun inboxExcludesDeletedRecords() = runTest {
        val active = createItem(id = "active-1")
        val deleted = createItem(id = "deleted-1")
        dao.insertAll(listOf(active.toEntity(), deleted.toEntity()))
        dao.softDelete("deleted-1", System.currentTimeMillis())

        val inbox = dao.observeInbox().first()
        assertEquals(1, inbox.size)
        assertEquals("active-1", inbox[0].id)
    }

    @Test
    fun pendingFilter() = runTest {
        val pending = createItem(id = "p1")
        val reviewed = createItem(id = "r1")
        dao.insertAll(listOf(pending.toEntity(), reviewed.toEntity()))
        dao.markReviewed("r1", System.currentTimeMillis())

        val pendingItems = dao.observeByStatus(CaptureStatus.PENDING).first()
        assertEquals(1, pendingItems.size)
        assertEquals("p1", pendingItems[0].id)
    }

    @Test
    fun reviewedFilter() = runTest {
        val pending = createItem(id = "p2")
        val reviewed = createItem(id = "r2")
        dao.insertAll(listOf(pending.toEntity(), reviewed.toEntity()))
        dao.markReviewed("r2", System.currentTimeMillis())

        val reviewedItems = dao.observeByStatus(CaptureStatus.REVIEWED).first()
        assertEquals(1, reviewedItems.size)
        assertEquals("r2", reviewedItems[0].id)
    }

    @Test
    fun archivedFilter() = runTest {
        val item = createItem(id = "a1")
        dao.insert(item.toEntity())
        dao.archive("a1", System.currentTimeMillis())

        val archivedItems = dao.observeByStatus(CaptureStatus.ARCHIVED).first()
        assertEquals(1, archivedItems.size)
        assertEquals("a1", archivedItems[0].id)
    }

    @Test
    fun searchByUrl() = runTest {
        val item = createItem(id = "search-url", url = "https://example.com/article")
        dao.insert(item.toEntity())

        val results = dao.search("example.com/article").first()
        assertTrue(results.isNotEmpty())
        assertEquals("search-url", results[0].id)
    }

    @Test
    fun searchByNote() = runTest {
        val item = createItem(id = "search-note", content = "some content", note = "special note")
        dao.insert(item.toEntity())

        val results = dao.search("special note").first()
        assertTrue(results.isNotEmpty())
        assertEquals("search-note", results[0].id)
    }

    @Test
    fun searchByOriginalContent() = runTest {
        val item = createItem(id = "search-content", content = "unique article content")
        dao.insert(item.toEntity())

        val results = dao.search("unique article").first()
        assertTrue(results.isNotEmpty())
        assertEquals("search-content", results[0].id)
    }

    @Test
    fun searchExcludesSoftDeletedRecords() = runTest {
        val item = createItem(id = "search-deleted", content = "searchable text")
        dao.insert(item.toEntity())
        dao.softDelete("search-deleted", System.currentTimeMillis())

        val results = dao.search("searchable").first()
        assertTrue(results.isEmpty())
    }

    @Test
    fun exactUrlDuplicateLookup() = runTest {
        val url = "https://example.com/share"
        dao.insert(createItem(id = "orig", url = url).toEntity())
        dao.insert(createItem(id = "dup", url = url).toEntity())

        val duplicates = dao.findExactUrlDuplicates(url, excludingId = "orig")
        assertEquals(1, duplicates.size)
        assertEquals("dup", duplicates[0].id)
    }

    @Test
    fun duplicateUrlsAllowedAsSeparateRecords() = runTest {
        val url = "https://example.com/duplicate-allowed"
        dao.insert(createItem(id = "first", url = url).toEntity())
        dao.insert(createItem(id = "second", url = url).toEntity())

        val count = dao.count()
        assertEquals(2, count)
    }

    @Test
    fun orderingIsNewestFirst() = runTest {
        val older = createItem(id = "older").copy(createdAtEpochMillis = 1000L)
        val newer = createItem(id = "newer").copy(createdAtEpochMillis = 2000L)
        dao.insert(older.toEntity())
        dao.insert(newer.toEntity())

        val inbox = dao.observeInbox().first()
        assertEquals("newer", inbox[0].id)
        assertEquals("older", inbox[1].id)
    }

    @Test
    fun insertAllInsertsMultipleItems() = runTest {
        val items = listOf(
            createItem(id = "multi-1").toEntity(),
            createItem(id = "multi-2").toEntity()
        )
        dao.insertAll(items)

        assertEquals(2, dao.count())
    }

    @Test
    fun updateExistingItem() = runTest {
        val item = createItem(id = "update-test", content = "original")
        dao.insert(item.toEntity())
        val updated = item.copy(originalContent = "modified")
        dao.update(updated.toEntity())

        val loaded = dao.getById("update-test")
        assertEquals("modified", loaded!!.originalContent)
    }

    @Test
    fun countReturnsCorrectValue() = runTest {
        assertEquals(0, dao.count())
        dao.insert(createItem(id = "count-1").toEntity())
        assertEquals(1, dao.count())
        dao.insert(createItem(id = "count-2").toEntity())
        assertEquals(2, dao.count())
    }

    @Test
    fun searchBySourceLabel() = runTest {
        val entity = createItem(id = "label-search").toEntity().copy(
            sourceLabel = "MyApp"
        )
        dao.insert(entity)

        val results = dao.search("MyApp").first()
        assertTrue(results.isNotEmpty())
        assertEquals("label-search", results[0].id)
    }

    @Test
    fun findExactUrlDuplicatesReturnsEmptyWhenNoneExist() = runTest {
        val results = dao.findExactUrlDuplicates("https://nonexistent.com")
        assertTrue(results.isEmpty())
    }

    @Test
    fun softDeletedItemsHiddenFromInboxButRetrievableById() = runTest {
        val item = createItem(id = "hidden-check")
        dao.insert(item.toEntity())
        dao.softDelete("hidden-check", System.currentTimeMillis())

        val inbox = dao.observeInbox().first()
        assertTrue(inbox.none { it.id == "hidden-check" })

        val direct = dao.getById("hidden-check")
        assertNotNull(direct)
    }

    @Test
    fun activeByIdsReturnsOnlyKnownActiveItemsInNewestFirstOrder() = runTest {
        val older = createItem(id = "active-older").copy(createdAtEpochMillis = 1000L)
        val newer = createItem(id = "active-newer").copy(createdAtEpochMillis = 2000L)
        val deleted = createItem(id = "active-deleted").copy(createdAtEpochMillis = 3000L)
        dao.insertAll(listOf(older, newer, deleted).map { it.toEntity() })
        dao.softDelete(deleted.id, System.currentTimeMillis())

        val result = dao.getActiveByIds(listOf("missing", "active-older", "active-deleted", "active-newer"))

        assertEquals(listOf("active-newer", "active-older"), result.map { it.id })
    }

    @Test
    fun activeByIdsWithEmptyIdsReturnsEmpty() = runTest {
        assertTrue(dao.getActiveByIds(emptyList()).isEmpty())
    }

    @Test
    fun getActiveByIdsAcrossChunkBoundaryThroughRepository() = runTest {
        // 1801 ids must be split into 900/900/1 chunks below the SQLite bind limit
        // and reassembled in deterministic newest-first order.
        val ids = (1..1801).map { "chunk-$it" }
        val entities = ids.map { id ->
            createItem(id = id)
                .copy(createdAtEpochMillis = id.removePrefix("chunk-").toLong())
                .toEntity()
        }
        dao.insertAll(entities)

        val repository = RoomCaptureRepository(dao, CaptureValidator(), CaptureItemFactory())
        val result = repository.getActiveByIds(ids.toSet())

        assertEquals(1801, result.size)
        assertEquals(ids.toSet(), result.map { it.id }.toSet())
        assertEquals("chunk-1801", result.first().id)
        assertEquals("chunk-1", result.last().id)
    }
}
