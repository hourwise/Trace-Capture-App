package uk.co.pcgsoft.tracecapture.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.ui.theme.TraceCaptureTheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appInfo = SettingsAppInfo(
        appName = "TRACE Capture",
        versionName = "1.0.0",
        versionCode = 1,
        packageName = "uk.co.pcgsoft.tracecapture"
    )

    private class Callbacks {
        var navigateBack = false
        var defaultFilterClicks = 0
        var formatClicks = 0
        var retentionClicks = 0
        var deleteFilesClicks = 0
        var privacyClicks = 0
        var licencesClicks = 0
        var resetClicks = 0
        var exitToggle: Boolean? = null
        var confirmResetToggle: Boolean? = null
        var selectedDefaultFilter: DefaultInboxFilter? = null
        var selectedFormat: PreferredExportFormat? = null
        var selectedRetention: TemporaryExportRetention? = null
        var deleteFilesConfirmed = false
        var resetConfirmed = false
        var dialogDismissed = false
    }

    private fun setContent(
        uiState: SettingsUiState = SettingsUiState(isLoading = false),
        callbacks: Callbacks = Callbacks()
    ): Callbacks {
        composeTestRule.setContent {
            TraceCaptureTheme {
                SettingsContent(
                    uiState = uiState,
                    appInfo = appInfo,
                    onNavigateBack = { callbacks.navigateBack = true },
                    onDefaultInboxFilterClick = { callbacks.defaultFilterClicks++ },
                    onPreferredExportFormatClick = { callbacks.formatClicks++ },
                    onTemporaryExportRetentionClick = { callbacks.retentionClicks++ },
                    onDeleteTemporaryFilesClick = { callbacks.deleteFilesClicks++ },
                    onPrivacyAndDataClick = { callbacks.privacyClicks++ },
                    onLicencesClick = { callbacks.licencesClicks++ },
                    onResetClick = { callbacks.resetClicks++ },
                    onExitSelectionToggle = { callbacks.exitToggle = it },
                    onConfirmBeforeResetToggle = { callbacks.confirmResetToggle = it },
                    onDefaultInboxFilterSelected = { callbacks.selectedDefaultFilter = it },
                    onPreferredExportFormatSelected = { callbacks.selectedFormat = it },
                    onTemporaryExportRetentionSelected = { callbacks.selectedRetention = it },
                    onDeleteTemporaryFilesConfirmed = { callbacks.deleteFilesConfirmed = true },
                    onResetConfirmed = { callbacks.resetConfirmed = true },
                    onDialogDismissed = { callbacks.dialogDismissed = true },
                    onMessageShown = {}
                )
            }
        }
        return callbacks
    }

    @Test
    fun settingsTitle_displayed() {
        setContent()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun loadingState_showsProgressNotRows() {
        setContent(uiState = SettingsUiState(isLoading = true))
        composeTestRule.onNodeWithText("Default inbox filter").assertDoesNotExist()
    }

    @Test
    fun backNavigation_invokesCallback() {
        val callbacks = setContent()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(callbacks.navigateBack)
    }

    @Test
    fun allSettingRows_displayed() {
        setContent()
        composeTestRule.onNodeWithText("Default inbox filter").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preferred export format").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exit selection after successful export").assertIsDisplayed()
        composeTestRule.onNodeWithText("Temporary shared-file retention").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage temporary export files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
        composeTestRule.onNodeWithText("Package").assertIsDisplayed()
        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Open-source licences"))
        composeTestRule.onNodeWithText("Open-source licences").assertIsDisplayed()
        lazyColumn.performScrollToNode(hasText("Privacy and data information"))
        composeTestRule.onNodeWithText("Privacy and data information").assertIsDisplayed()
        lazyColumn.performScrollToNode(hasText("Reset settings to defaults"))
        composeTestRule.onNodeWithText("Reset settings to defaults").assertIsDisplayed()
    }

    @Test
    fun currentValues_displayed() {
        setContent(
            uiState = SettingsUiState(
                isLoading = false,
                settings = SettingsDefaults.value.copy(
                    defaultInboxFilter = DefaultInboxFilter.ALL,
                    preferredExportFormat = PreferredExportFormat.JSON,
                    temporaryExportRetention = TemporaryExportRetention.ONE_HOUR
                )
            )
        )
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("JSON").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 hour").assertIsDisplayed()
    }

    @Test
    fun versionAndBuildInfo_displayed() {
        setContent()
        composeTestRule.onNodeWithText("1.0.0 (1)").assertIsDisplayed()
    }

    @Test
    fun defaultInboxFilterDialog_showsSelectionAndSavesOnSelect() {
        val callbacks = setContent()
        composeTestRule.onNodeWithText("Default inbox filter").performClick()
        composeTestRule.onNodeWithText("Pending").assertIsSelected()
        composeTestRule.onNodeWithText("Reviewed").assertIsNotSelected()

        composeTestRule.onNodeWithText("Reviewed").performClick()
        assertTrue(callbacks.selectedDefaultFilter == DefaultInboxFilter.REVIEWED)
    }

    @Test
    fun preferredExportFormatDialog_showsSelection() {
        val callbacks = setContent(
            uiState = SettingsUiState(
                isLoading = false,
                settings = SettingsDefaults.value.copy(preferredExportFormat = PreferredExportFormat.PLAIN_TEXT)
            )
        )
        composeTestRule.onNodeWithText("Preferred export format").performClick()
        composeTestRule.onNodeWithText("Plain text").assertIsSelected()
        composeTestRule.onNodeWithText("Ask every time").assertIsNotSelected()

        composeTestRule.onNodeWithText("Ask every time").performClick()
        assertTrue(callbacks.selectedFormat == PreferredExportFormat.ASK_EVERY_TIME)
    }

    @Test
    fun temporaryRetentionDialog_showsSelection() {
        val callbacks = setContent()
        composeTestRule.onNodeWithText("Temporary shared-file retention").performClick()
        composeTestRule.onNodeWithText("24 hours").assertIsSelected()

        composeTestRule.onNodeWithText("7 days").performClick()
        assertTrue(callbacks.selectedRetention == TemporaryExportRetention.SEVEN_DAYS)
    }

    @Test
    fun switches_invokeCallbacks() {
        val callbacks = setContent(
            uiState = SettingsUiState(
                isLoading = false,
                settings = SettingsDefaults.value.copy(
                    exitSelectionAfterSuccessfulExport = true,
                    confirmBeforeReset = true
                )
            )
        )
        composeTestRule.onNodeWithText("Exit selection after successful export").performClick()
        assertTrue(callbacks.exitToggle == false)

        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Confirm before reset"))
        composeTestRule.onNodeWithText("Confirm before reset").performClick()
        assertTrue(callbacks.confirmResetToggle == false)
    }

    @Test
    fun deleteTemporaryFilesConfirmation_showsBodyAndConfirms() {
        val callbacks = setContent()
        composeTestRule.onNodeWithText("Manage temporary export files").performClick()
        composeTestRule.onNodeWithText("Delete temporary export files?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()
        assertTrue(callbacks.deleteFilesConfirmed)
    }

    @Test
    fun privacyAndDataDialog_opens() {
        val callbacks = setContent()
        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Privacy and data information"))
        composeTestRule.onNodeWithText("Privacy and data information").performClick()
        composeTestRule.onNodeWithText("Privacy and local data").assertIsDisplayed()
        assertTrue(callbacks.privacyClicks == 1)
    }

    @Test
    fun resetConfirmationEnabled_showsDialog() {
        val callbacks = setContent(
            uiState = SettingsUiState(
                isLoading = false,
                settings = SettingsDefaults.value.copy(confirmBeforeReset = true)
            )
        )
        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Reset settings to defaults"))
        composeTestRule.onNodeWithText("Reset settings to defaults").performClick()
        composeTestRule.onNodeWithText("Reset settings?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset settings to defaults").performClick()
        assertTrue(callbacks.resetConfirmed)
    }

    @Test
    fun resetConfirmationDisabled_resetsImmediately() {
        val callbacks = setContent(
            uiState = SettingsUiState(
                isLoading = false,
                settings = SettingsDefaults.value.copy(confirmBeforeReset = false)
            )
        )
        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Reset settings to defaults"))
        composeTestRule.onNodeWithText("Reset settings to defaults").performClick()
        assertTrue(callbacks.resetClicks == 1)
        assertTrue(callbacks.resetConfirmed == false)
    }

    @Test
    fun licenceDialog_opens() {
        setContent()
        val lazyColumn = composeTestRule.onNode(hasScrollAction())
        lazyColumn.performScrollToNode(hasText("Open-source licences"))
        composeTestRule.onNodeWithText("Open-source licences").performClick()
        composeTestRule.onNodeWithText("AndroidX", substring = true).assertIsDisplayed()
    }
}
