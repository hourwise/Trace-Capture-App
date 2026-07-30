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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CaptureItemDaoObserveByIdTest {

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
        url: String? = null
    ) = factory.create(
        originalContent = content,
        primaryUrl = url,
        detectedUrls = if (url != null) listOf(url) else emptyList(),
        sourcePackageName = null,
        sourceLabel = null,
        note = null,
        captureType = if (url != null) CaptureType.URL_WITH_TEXT else CaptureType.TEXT
    )

    @Test
    fun observeById_emitsExistingItem() = runTest {
        val item = createItem(id = "obs-1")
        dao.insert(item.toEntity())

        val observed = dao.observeById("obs-1").first()
        assertNotNull(observed)
        assertEquals("obs-1", observed!!.id)
        assertEquals(item.originalContent, observed.originalContent)
    }

    @Test
    fun observeById_emitsNullForUnknownId() = runTest {
        val observed = dao.observeById("unknown-id").first()
        assertNull(observed)
    }

    @Test
    fun observeById_emitsUpdatedNote() = runTest {
        val item = createItem(id = "obs-note", content = "test")
        dao.insert(item.toEntity())

        dao.updateNote("obs-note", "Updated note", System.currentTimeMillis())

        val observed = dao.observeById("obs-note").first()
        assertEquals("Updated note", observed!!.note)
    }

    @Test
    fun observeById_emitsChangedStatus() = runTest {
        val item = createItem(id = "obs-status")
        dao.insert(item.toEntity())

        dao.markReviewed("obs-status", System.currentTimeMillis())

        val observed = dao.observeById("obs-status").first()
        assertEquals(CaptureStatus.REVIEWED, observed!!.status)
    }

    @Test
    fun observeById_emitsNullAfterSoftDelete() = runTest {
        val item = createItem(id = "obs-deleted")
        dao.insert(item.toEntity())

        assertNotNull(dao.observeById("obs-deleted").first())

        dao.softDelete("obs-deleted", System.currentTimeMillis())

        val after = dao.observeById("obs-deleted").first()
        assertNull(after)
    }

    @Test
    fun observeById_doesNotEmitOtherItems() = runTest {
        val item1 = createItem(id = "obs-target", content = "target")
        val item2 = createItem(id = "obs-other", content = "other")
        dao.insertAll(listOf(item1.toEntity(), item2.toEntity()))

        dao.updateNote("obs-other", "Changed", System.currentTimeMillis())

        val observed = dao.observeById("obs-target").first()
        assertEquals("target", observed!!.originalContent)
    }

    @Test
    fun observeById_emitsNullAfterDelete() = runTest {
        dao.insert(createItem(id = "obs-del").toEntity())
        assertNotNull(dao.observeById("obs-del").first())

        dao.deleteAll()

        val after = dao.observeById("obs-del").first()
        assertNull(after)
    }
}
