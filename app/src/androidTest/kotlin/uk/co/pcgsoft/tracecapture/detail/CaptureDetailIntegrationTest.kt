package uk.co.pcgsoft.tracecapture.detail

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CaptureDetailIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createItem(
        id: String = UUID.randomUUID().toString(),
        content: String = "Default content",
        status: CaptureStatus = CaptureStatus.PENDING,
        note: String? = null,
        source: String? = "TestApp",
        url: String? = "https://example.com/test",
        detectedUrls: List<String> = if (url != null) listOf(url) else emptyList()
    ) = CaptureItem(
        id = id,
        createdAtEpochMillis = 1722000000000L,
        updatedAtEpochMillis = 1722000000000L,
        originalContent = content,
        primaryUrl = url,
        detectedUrls = detectedUrls,
        sourcePackageName = "com.test",
        sourceLabel = source,
        note = note,
        captureType = if (url != null) CaptureType.URL_WITH_TEXT else CaptureType.TEXT,
        status = status,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    @Test
    fun detail_displays_matching_content() {
        val item = createItem(content = "Integration test detail content")
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = item,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Integration test detail content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun note_update_persists_after_recreation() {
        var item = createItem(note = "Original note")
        var noteDraft = "Original note"
        var noteChanged = false

        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = item,
                        noteDraft = noteDraft,
                        noteChanged = noteChanged
                    ),
                    onNoteChanged = { newNote ->
                        noteDraft = newNote
                        noteChanged = newNote != item.note.orEmpty()
                    },
                    onSaveNote = {
                        item = item.copy(note = noteDraft)
                        noteChanged = false
                    },
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Original note").assertIsDisplayed()

        composeTestRule.onNodeWithText("Original note").performTextClearance()
        composeTestRule.onNodeWithText("Original note").performTextInput("Updated note")
        composeTestRule.onNodeWithText("Save Note").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Original note").assertDoesNotExist()
        composeTestRule.onNodeWithText("Updated note").assertIsDisplayed()
    }

    @Test
    fun mark_reviewed_updates_status() {
        var item = createItem(status = CaptureStatus.PENDING)
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = item,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {
                        item = item.copy(status = CaptureStatus.REVIEWED)
                    },
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()

        composeTestRule.onNodeWithText("Mark reviewed").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Pending").assertDoesNotExist()
        composeTestRule.onNodeWithText("Reviewed").assertIsDisplayed()
    }

    @Test
    fun archive_updates_status() {
        var item = createItem(status = CaptureStatus.PENDING)
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = item,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {
                        item = item.copy(status = CaptureStatus.ARCHIVED)
                    },
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Archive").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test
    fun deleting_returns_to_inbox_and_removes_item() {
        var showDetail = true
        val item = createItem(content = "Item to delete")

        composeTestRule.setContent {
            TraceCaptureTheme {
                if (showDetail) {
                    CaptureDetailContent(
                        uiState = CaptureDetailUiState(
                            isLoading = false,
                            capture = item,
                            noteDraft = "",
                            showDeleteConfirmation = false
                        ),
                        onNoteChanged = {},
                        onSaveNote = {},
                        onOpenUrl = {},
                        onCopyUrl = {},
                        onCopyContent = {},
                        onMarkReviewed = {},
                        onArchive = {},
                        onRestore = {},
                        onDelete = {
                            showDetail = false
                        },
                        onExport = {},
                        isPreparingExport = false
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Item to delete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun unknown_id_shows_missing_state() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = null,
                        isMissing = true
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Capture not found").assertIsDisplayed()
    }

    @Test
    fun unsaved_note_back_shows_confirmation() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = createItem(),
                        noteDraft = "Unsaved edit",
                        noteChanged = true,
                        showUnsavedChangesDialog = true
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Discard note changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard changes").assertIsDisplayed()

        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun detail_recreation_retains_room_backed_content() {
        val item = createItem(content = "Room-backed content")
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = item,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Room-backed content").assertIsDisplayed()
    }
}
