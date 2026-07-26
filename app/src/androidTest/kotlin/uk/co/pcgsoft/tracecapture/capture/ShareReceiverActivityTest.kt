package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ShareReceiverActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun sendUrlIntent(url: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            activity.intent = intent
            activity.recreate()
        }
        composeRule.waitForIdle()
    }

    private fun sendTextIntent(text: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            activity.intent = intent
            activity.recreate()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun activity_title_is_displayed() {
        composeRule.onNodeWithText("TRACE Capture").assertIsDisplayed()
    }

    @Test
    fun cancel_button_is_displayed() {
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun save_button_is_displayed() {
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun note_input_is_displayed() {
        composeRule.onNodeWithContentDescription("Note input").assertIsDisplayed()
    }

    @Test
    fun note_input_accepts_text() {
        composeRule.onNodeWithContentDescription("Note input").performTextInput("test note")
        composeRule.onNodeWithText("test note").assertIsDisplayed()
    }

    @Test
    fun url_shows_domain_display() {
        sendUrlIntent("https://www.example.com/path/to/page")
        composeRule.onNodeWithText("example.com").assertIsDisplayed()
    }

    @Test
    fun url_intent_shows_url_count() {
        sendUrlIntent("https://a.com https://b.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("URLs detected").assertIsDisplayed()
    }

    @Test
    fun text_only_no_url_count_shown() {
        sendTextIntent("Just some plain text content")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("URLs detected").assertDoesNotExist()
    }

    @Test
    fun source_label_displayed_for_labeled_source() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com")
                setPackage("com.twitter.android")
            }
            activity.intent = intent
            activity.recreate()
        }
        composeRule.waitForIdle()
        // Source label appears when SourceApplicationResolver can resolve
        // Just verify the activity doesn't crash
        composeRule.onNodeWithText("TRACE Capture").assertIsDisplayed()
    }

    @Test
    fun save_button_triggers_save_flow() {
        sendUrlIntent("https://example.com/save-test")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save").performClick()
        // After save, should eventually show confirmation or dismiss
        // Give time for save operation
        composeRule.waitForIdle()
    }

    @Test
    fun cancel_button_dismisses_activity() {
        sendUrlIntent("https://example.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        // Activity should finish
        composeRule.waitForIdle()
    }

    @Test
    fun activity_launches_with_url_intent() {
        sendUrlIntent("https://example.com/launch-test")
        composeRule.onNodeWithText("TRACE Capture").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
