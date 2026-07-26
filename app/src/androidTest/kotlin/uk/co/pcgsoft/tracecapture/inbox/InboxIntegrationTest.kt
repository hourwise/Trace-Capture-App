package uk.co.pcgsoft.tracecapture.inbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.MainActivity
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
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

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun addCapture_appearsInList() = runBlocking {
        val testContent = "Integration Test Capture ${System.currentTimeMillis()}"
        val item = CaptureItem(
            id = "test_id",
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
            originalContent = testContent,
            primaryUrl = null,
            detectedUrls = emptyList(),
            sourcePackageName = null,
            sourceLabel = null,
            note = null,
            captureType = CaptureType.TEXT,
            status = CaptureStatus.PENDING,
            syncStatus = SyncStatus.LOCAL_ONLY,
            duplicateOfId = null,
            archivedAtEpochMillis = null,
            deletedAtEpochMillis = null
        )
        
        repository.save(item)
        
        composeTestRule.onNodeWithText(testContent).assertIsDisplayed()
    }
    
    @Test
    fun searchAndFilter_integration() = runBlocking {
        val content1 = "Apple"
        val content2 = "Banana"
        
        repository.save(createItem("id1", content1, CaptureStatus.PENDING))
        repository.save(createItem("id2", content2, CaptureStatus.REVIEWED))
        
        // Default is Pending
        composeTestRule.onNodeWithText(content1).assertIsDisplayed()
        composeTestRule.onNodeWithText(content2).assertDoesNotExist()
        
        // Search for Banana while in Pending
        composeTestRule.onNodeWithText("Search captures").performTextInput("Banana")
        composeTestRule.onNodeWithText(content1).assertDoesNotExist()
        composeTestRule.onNodeWithText(content2).assertDoesNotExist() // Wrong status
        
        // Switch to All
        composeTestRule.onNodeWithText("All").performClick()
        composeTestRule.onNodeWithText(content2).assertIsDisplayed()
        composeTestRule.onNodeWithText(content1).assertDoesNotExist()
    }

    private fun createItem(id: String, content: String, status: CaptureStatus) = CaptureItem(
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
