package uk.co.pcgsoft.tracecapture.settings

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.BuildConfig
import uk.co.pcgsoft.tracecapture.MainActivity
import uk.co.pcgsoft.tracecapture.data.local.CaptureItemDao
import uk.co.pcgsoft.tracecapture.data.repository.CaptureRepository
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.export.share.ExportShareFileManager
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: CaptureRepository

    @Inject
    lateinit var dao: CaptureItemDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var shareFileManager: ExportShareFileManager

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            dao.deleteAll()
            settingsRepository.resetToDefaults()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dao.deleteAll()
            settingsRepository.resetToDefaults()
        }
    }

    @Test
    fun openSettingsFromInboxAndReturn() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        pressBack()
        composeTestRule.onNodeWithText("Captures").assertIsDisplayed()
    }

    @Test
    fun appVersionDisplayed() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule
            .onNodeWithText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .assertIsDisplayed()
    }

    @Test
    fun settingsPersistAcrossActivityRecreation() = runBlocking {
        settingsRepository.setDefaultInboxFilter(DefaultInboxFilter.ALL)

        recreateActivity()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Default inbox filter").assertIsDisplayed()
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
    }

    @Test
    fun preferredExportFormatAffectsDetailFlow() = runBlocking {
        settingsRepository.setPreferredExportFormat(PreferredExportFormat.JSON)
        val content = "Detail export ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
        composeTestRule.onNodeWithText(content).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Export capture").performClick()
        composeTestRule.waitForIdle()

        // Format chooser skipped; the Save/Share dialog appears directly with JSON.
        composeTestRule.onNodeWithText("Save file").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plain text").assertDoesNotExist()
    }

    @Test
    fun preferredExportFormatAffectsBulkExport() = runBlocking {
        settingsRepository.setPreferredExportFormat(PreferredExportFormat.JSON)
        val content = "Bulk export ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))

        composeTestRule.onNodeWithText("Select").performClick()
        composeTestRule.onNodeWithText(content).performClick()
        composeTestRule.onNodeWithText("Export selected").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Save file").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plain text").assertDoesNotExist()
    }

    @Test
    fun defaultInboxFilterOnFreshNavigation() = runBlocking {
        settingsRepository.setDefaultInboxFilter(DefaultInboxFilter.REVIEWED)
        val content = "Reviewed default ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content, status = CaptureStatus.REVIEWED))

        // Recreate the activity so a fresh InboxViewModel applies the stored default.
        recreateActivity()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
    }

    @Test
    fun temporaryFileDeletionLimitedToExportCache() = runBlocking {
        val cacheDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "exports"
        )
        cacheDir.mkdirs()
        val shareFile = File(cacheDir, "trace-capture-share.json").apply { writeText("{}") }
        val unrelated = File(cacheDir, "other-file.bin").apply { writeText("x") }

        val count = shareFileManager.deleteAllTemporaryExports()

        assertTrue("expected 1 deleted, got $count", count == 1)
        assertTrue("dedicated export file should be deleted", !shareFile.exists())
        assertTrue("unrelated cache file must remain", unrelated.exists())
    }

    @Test
    fun resetDoesNotDeleteCaptures() = runBlocking {
        val content = "Keep me ${uniqueId()}"
        repository.save(createItem(id = uniqueId(), content = content))
        settingsRepository.setPreferredExportFormat(PreferredExportFormat.JSON)

        settingsRepository.resetToDefaults()

        composeTestRule.onNodeWithText(content).assertIsDisplayed()
    }

    private fun recreateActivity() {
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
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
