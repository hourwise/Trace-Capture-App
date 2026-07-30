package uk.co.pcgsoft.tracecapture.inbox

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.MainActivity
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InboxIntegrationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: CaptureRepository

    @Inject
    lateinit var dao: CaptureItemDao

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking { dao.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { dao.deleteAll() }
    }

    @Test
    fun pendingIsDefault() {
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("No pending captures").assertIsDisplayed()
    }

    @Test
    fun savedItem_appearsInList() = runBlocking {
        val content = "Saved item content ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
    }

    @Test
    fun search_findsMatchingData() = runBlocking {
        val content = "Unique banana ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()

        composeTestRule.onNodeWithText("Search captures").performTextInput("banana")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
    }

    @Test
    fun search_noMatch_showsEmptyState() = runBlocking {
        repository.save(createItem(id = uniqueId(), content = "Apple content"))

        composeTestRule.onNodeWithText("Search captures").performTextInput("zzznomatch")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No captures match your search").assertIsDisplayed()
    }

    @Test
    fun reviewedFilter_showsOnlyReviewed() = runBlocking {
        val pendingContent = "Pending item ${uniqueId()}"
        val reviewedContent = "Reviewed item ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = pendingContent, status = CaptureStatus.PENDING))
        repository.save(createItem(id = uniqueId(), content = reviewedContent, status = CaptureStatus.REVIEWED))

        composeTestRule.onNodeWithText(pendingContent).assertIsDisplayed()
        composeTestRule.onNodeWithText(reviewedContent).assertDoesNotExist()

        composeTestRule.onNodeWithText("Reviewed").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(reviewedContent).assertIsDisplayed()
        composeTestRule.onNodeWithText(pendingContent).assertDoesNotExist()
    }

    @Test
    fun allFilter_showsAllStatuses() = runBlocking {
        val pendingContent = "Pending item ${uniqueId()}"
        val reviewedContent = "Reviewed item ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = pendingContent, status = CaptureStatus.PENDING))
        repository.save(createItem(id = uniqueId(), content = reviewedContent, status = CaptureStatus.REVIEWED))

        composeTestRule.onNodeWithText("All").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(pendingContent).assertIsDisplayed()
        composeTestRule.onNodeWithText(reviewedContent).assertIsDisplayed()
    }

    @Test
    fun markReviewed_removesFromPending() = runBlocking {
        val content = "To review ${uniqueId()}"
        val id = uniqueId()
        repository.save(createItem(id = id, content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Capture actions").performClick()
        composeTestRule.onNodeWithText("Mark reviewed").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertDoesNotExist()
    }

    @Test
    fun archive_removesFromPending() = runBlocking {
        val content = "To archive ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Capture actions").performClick()
        composeTestRule.onNodeWithText("Archive").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertDoesNotExist()
    }

    @Test
    fun softDelete_removesFromAll() = runBlocking {
        val content = "To delete ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Capture actions").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("All").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertDoesNotExist()
    }

    @Test
    fun activityRecreation_stillObservesData() = runBlocking {
        val content = "Persisted item ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()

        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
    }

    private fun uniqueId(): String = UUID.randomUUID().toString().take(12)

    private fun createItem(
        id: String = uniqueId(),
        content: String = "Test content",
        status: CaptureStatus = CaptureStatus.PENDING
    ) = CaptureItem(
        id = id,
        createdAtEpochMillis = System.currentTimeMillis(),
        updatedAtEpochMillis = System.currentTimeMillis(),
        originalContent = content,
        primaryUrl = null,
        detectedUrls = emptyList(),
        sourcePackageName = null,
        sourceLabel = null,
        note = null,
        captureType = CaptureType.TEXT,
        status = status,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )
}
