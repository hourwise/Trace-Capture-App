package uk.co.pcgsoft.tracecapture.inbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
            createdAtEpochMillis = 1722000000000L, // Fixed date for testing
            updatedAtEpochMillis = 1722000000000L,
            originalContent = "Test Capture 1",
            captureType = CaptureType.TEXT,
            status = CaptureStatus.PENDING,
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
            createdAtEpochMillis = 1722000000000L,
            updatedAtEpochMillis = 1722000000000L,
            originalContent = "Test Capture 2",
            captureType = CaptureType.TEXT,
            status = CaptureStatus.REVIEWED,
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
    fun deleteDialog_showsPreview() {
        val itemToDelete = testCaptures[0]
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = InboxUiState(
                        isLoading = false,
                        captures = testCaptures,
                        filter = InboxFilter.ALL,
                        pendingDelete = itemToDelete
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

        // Verify dialog is shown with preview text
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Capture 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun selectionMode_showsContextualActionsAndSelectedCount() {
        var state by mutableStateOf(
            InboxUiState(
                isLoading = false,
                captures = testCaptures,
                filter = InboxFilter.ALL
            )
        )
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = state,
                    onCaptureSelected = { id ->
                        state = state.copy(
                            selection = state.selection.copy(
                                selectedIds = state.selection.selectedIds.toggleForTest(id)
                            )
                        )
                    },
                    onFilterSelected = {},
                    onSearchQueryChanged = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDeleteRequested = {},
                    onDeleteConfirmed = {},
                    onDeleteCancelled = {},
                    onMessageShown = {},
                    onLinkCopied = {},
                    onSelectionRequested = {
                        state = state.copy(selection = InboxSelectionState(isActive = true))
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Select").performClick()
        composeTestRule.onNodeWithText("Select all").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export selected").assertIsDisplayed()

        composeTestRule.onNodeWithText("Test Capture 1").performClick()
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Capture actions").assertDoesNotExist()
    }

    @Test
    fun longPress_entersSelectionModeAndSelectsCard() {
        var state by mutableStateOf(
            InboxUiState(
                isLoading = false,
                captures = testCaptures,
                filter = InboxFilter.ALL
            )
        )
        composeTestRule.setContent {
            TraceCaptureTheme {
                InboxContent(
                    uiState = state,
                    onCaptureSelected = {},
                    onCaptureLongPressed = { id ->
                        state = state.copy(
                            selection = InboxSelectionState(isActive = true, selectedIds = setOf(id))
                        )
                    },
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

        composeTestRule.onNodeWithText("Test Capture 1").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    private fun Set<String>.toggleForTest(id: String): Set<String> =
        if (contains(id)) this - id else this + id
}
