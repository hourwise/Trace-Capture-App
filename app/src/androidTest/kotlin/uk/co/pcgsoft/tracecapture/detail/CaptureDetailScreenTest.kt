package uk.co.pcgsoft.tracecapture.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class CaptureDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleCapture = CaptureItem(
        id = "test-1",
        createdAtEpochMillis = 1722000000000L,
        updatedAtEpochMillis = 1722000000000L,
        originalContent = "Original shared content for testing",
        primaryUrl = "https://example.com/article",
        detectedUrls = listOf(
            "https://example.com/article",
            "https://example.com/related"
        ),
        sourcePackageName = "com.example",
        sourceLabel = "Chrome",
        note = "Existing editorial note",
        captureType = CaptureType.URL_WITH_TEXT,
        status = CaptureStatus.PENDING,
        syncStatus = SyncStatus.LOCAL_ONLY,
        duplicateOfId = null,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null
    )

    @Test
    fun loading_state() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(isLoading = true),
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

        composeTestRule.onNodeWithText("Capture Detail").assertDoesNotExist()
    }

    @Test
    fun missing_state() {
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
        composeTestRule.onNodeWithText("It may have been removed.").assertIsDisplayed()
    }

    @Test
    fun displays_full_content() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Existing editorial note"
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

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("From: Chrome").assertIsDisplayed()
        composeTestRule.onNodeWithText("Original shared content for testing").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://example.com/article").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 detected URLs").assertIsDisplayed()
        composeTestRule.onNodeWithText("Existing editorial note").assertIsDisplayed()
    }

    @Test
    fun displays_source_label() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
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

        composeTestRule.onNodeWithText("From: Chrome").assertIsDisplayed()
    }

    @Test
    fun url_fallback_source() {
        val noSource = sampleCapture.copy(sourceLabel = null)
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = noSource,
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

        composeTestRule.onNodeWithText("From: example.com").assertIsDisplayed()
    }

    @Test
    fun text_capture_fallback() {
        val noSourceNoUrl = sampleCapture.copy(
            sourceLabel = null,
            primaryUrl = null,
            detectedUrls = emptyList(),
            captureType = CaptureType.TEXT
        )
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = noSourceNoUrl,
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
    }

    @Test
    fun status_label_displayed() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
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

        composeTestRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun note_field_initialized_from_draft() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Existing editorial note"
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

        composeTestRule.onNodeWithText("Existing editorial note").assertIsDisplayed()
    }

    @Test
    fun save_note_disabled_when_unchanged() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Existing editorial note",
                        noteChanged = false
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

        composeTestRule.onNodeWithText("Save Note").assertIsNotEnabled()
    }

    @Test
    fun save_note_enabled_when_dirty() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Modified note",
                        noteChanged = true
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

        composeTestRule.onNodeWithText("Save Note").assertIsDisplayed()
    }

    @Test
    fun open_url_callback_invoked() {
        var openedUrl: String? = null
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = { openedUrl = it },
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

        composeTestRule.onNodeWithText("Open URL").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun copy_url_callback_invoked() {
        var copiedUrl: String? = null
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = { copiedUrl = it },
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

        composeTestRule.onNodeWithText("Copy URL").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun mark_reviewed_callback_invoked() {
        var reviewed = false
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = { reviewed = true },
                    onArchive = {},
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Mark reviewed").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun archive_callback_invoked() {
        var archived = false
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = { archived = true },
                    onRestore = {},
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Archive").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun restore_callback_invoked_when_archived() {
        var restored = false
        val archivedCapture = sampleCapture.copy(status = CaptureStatus.ARCHIVED)
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = archivedCapture,
                        noteDraft = ""
                    ),
                    onNoteChanged = {},
                    onSaveNote = {},
                    onOpenUrl = {},
                    onCopyUrl = {},
                    onCopyContent = {},
                    onMarkReviewed = {},
                    onArchive = {},
                    onRestore = { restored = true },
                    onDelete = {},
                    onExport = {},
                    isPreparingExport = false
                )
            }
        }

        composeTestRule.onNodeWithText("Restore to Pending").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restore to Pending").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun delete_confirmation_shown() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "",
                        showDeleteConfirmation = true
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

        composeTestRule.onNodeWithText("Original shared content for testing", substring = true).assertIsDisplayed()
    }

    @Test
    fun unsaved_changes_dialog_shown() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Changed",
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
        composeTestRule.onNodeWithText("Discard changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
    }

    @Test
    fun multiple_detected_urls_displayed() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
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

        composeTestRule.onNodeWithText("https://example.com/article").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://example.com/related").assertIsDisplayed()
    }

    @Test
    fun character_count_displayed() {
        composeTestRule.setContent {
            TraceCaptureTheme {
                CaptureDetailContent(
                    uiState = CaptureDetailUiState(
                        isLoading = false,
                        capture = sampleCapture,
                        noteDraft = "Hello"
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

        composeTestRule.onNodeWithText("5 / 2,000").assertIsDisplayed()
    }
}
