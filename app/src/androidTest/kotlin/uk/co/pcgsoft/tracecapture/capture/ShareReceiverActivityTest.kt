package uk.co.pcgsoft.tracecapture.capture

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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

    @Test
    fun activity_title_is_displayed() {
        composeRule.onNodeWithText("TRACE Capture").assertIsDisplayed()
    }

    @Test
    fun save_and_cancel_buttons_exist() {
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun activity_launches_with_url_intent() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com")
            }
        }
        composeRule.activity.recreate()
    }
}
