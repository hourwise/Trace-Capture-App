package uk.co.pcgsoft.tracecapture.inbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.pcgsoft.tracecapture.domain.CaptureItem
import uk.co.pcgsoft.tracecapture.domain.CaptureStatus
import uk.co.pcgsoft.tracecapture.domain.CaptureType
import uk.co.pcgsoft.tracecapture.domain.SyncStatus
import uk.co.pcgsoft.tracecapture.ui.theme.TraceCaptureTheme

@RunWith(AndroidJUnit4::class)
class InboxScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testCaptures = listOf(
        CaptureItem(
            id = "1",
            originalContent = "Test Capture 1",
            captureType = CaptureType.TEXT,
            status = CaptureStatus.PENDING,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
            primaryUrl = null,
            detectedUrls = emptyList(),
            sourcePackageName = null,
            sourceLabel = null,
            note = null,
            syncStatus = SyncStatus.LOCAL_ONLY,
            duplicateOfId = null,
            archivedAtEpochMillis = null,
            deletedAtEpochMillis = null
        ),
        CaptureItem(
            id = "2",
            originalContent = "Test Capture 2",
            captureType = CaptureType.TEXT,
            status = CaptureStatus.REVIEWED,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
            primaryUrl = null,
            detectedUrls = emptyList(),
            sourcePackageName = null,
            sourceLabel = null,
            note = "Some note",
            syncStatus = SyncStatus.LOCAL_ONLY,
            duplicateOfId = null,
            archivedAtEpochMillis = null,
            deletedAtEpochMillis = null
        )
    )

    @Test
    fun inboxScreen_displaysTitle() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = InboxUiState(
                        isLoading = false,
                        captures = testCaptures,
                        filter = InboxFilter.ALL
                    ),
                    onCaptureSelected = {},
                    onFilterSelected = {},
                    onSearchQueryChanged = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                    onMessageShown = {},
                    onLinkCopied = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Captures").assertIsDisplayed()
    }

    @Test
    fun inboxScreen_displaysCaptures() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = InboxUiState(
                        isLoading = false,
                        captures = testCaptures,
                        filter = InboxFilter.ALL
                    ),
                    onCaptureSelected = {},
                    onFilterSelected = {},
                    onSearchQueryChanged = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                    onMessageShown = {},
                    onLinkCopied = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Test Capture 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Capture 2").assertIsDisplayed()
    }

    @Test
    fun searchBar_allowsInput() {
        val searchText = "test query"
        composeTestRule.setContent {
            var query by mutableStateOf("")
            TraceCaptureTheme {
                InboxContent(
                    uiState = InboxUiState(
                        isLoading = false,
                        searchQuery = query,
                        filter = InboxFilter.ALL
                    ),
                    onCaptureSelected = {},
                    onFilterSelected = {},
                    onSearchQueryChanged = { query = it },
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                    onMessageShown = {},
                    onLinkCopied = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Search captures").performTextInput(searchText)
        composeTestRule.onNodeWithText(searchText).assertIsDisplayed()
    }

    @Test
    fun filterChips_areDisplayed() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = InboxUiState(
                        isLoading = false,
                        captures = emptyList(),
                        filter = InboxFilter.PENDING
                    ),
                    onCaptureSelected = {},
                    onFilterSelected = {},
                    onSearchQueryChanged = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                    onMessageShown = {},
                    onLinkCopied = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reviewed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archived").assertIsDisplayed()
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
    }
}
