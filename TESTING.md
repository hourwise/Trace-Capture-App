# TRACE Capture — Testing

## Test structure

```
src/test/                    # Unit tests (JVM, no Android dependency)
  uk.co.pcgsoft.tracecapture.data.local
    CaptureItemMapperTest    # Entity ↔ domain mapping
    CaptureItemFactoryTest   # Factory defaults, UUID, timestamps
    CaptureValidatorTest     # Validation rules
  uk.co.pcgsoft.tracecapture.capture
    UrlExtractorTest         # URL extraction, normalisation, classification, punctuation trimming
    ShareIntentParserTest    # Intent parsing: CharSequence, structured rejection reasons
    SharedCaptureProcessorTest # Orchestration: structured Ready/Rejected results
    ShareCaptureViewModelTest   # ViewModel: intent processing, save, duplicate, note, retry
  uk.co.pcgsoft.tracecapture.inbox
    InboxViewModelTest       # ViewModel: filtering, search, status actions, soft delete
  uk.co.pcgsoft.tracecapture.detail
    CaptureDetailViewModelTest # ViewModel: capture loading, note editing, status actions, delete, unsaved changes

src/androidTest/             # Instrumented tests (Android device/emulator)
  uk.co.pcgsoft.tracecapture.data.local
    CaptureItemDaoTest       # Room database operations with in-memory DB
    CaptureItemDaoObserveByIdTest # observeById Flow tests: initial, updates, null, soft delete
  uk.co.pcgsoft.tracecapture.capture
    ShareReceiverActivityTest # Quick-capture UI rendering (Hilt + Compose)
  uk.co.pcgsoft.tracecapture.inbox
    InboxScreenTest          # Inbox UI rendering, search, filters, actions (Hilt + Compose)
  uk.co.pcgsoft.tracecapture.detail
    CaptureDetailScreenTest  # Detail UI rendering, note, status actions, delete, unsaved changes
    CaptureDetailIntegrationTest # Detail navigation, state, updates (Hilt + Compose)
```

## Running tests

```powershell
# Unit tests
.\gradlew.bat test

# Instrumented tests (requires emulator or device)
.\gradlew.bat connectedDebugAndroidTest
```

### Running a single test class

```powershell
.\gradlew.bat test --tests "uk.co.pcgsoft.tracecapture.data.local.CaptureValidatorTest"
```

## Unit tests

| Test class | What it covers |
|---|---|
| `CaptureItemMapperTest` | Entity ↔ domain mapping: round-trip correctness, null/archived/deleted/duplicate fields |
| `CaptureItemFactoryTest` | Factory: UUID generation, timestamp population, default status/syncStatus, field preservation |
| `CaptureValidatorTest` | Validation: blank content, max length (content, note, URLs), blank URLs, URL deduplication, trimming |
| `ShareIntentParserTest` | CharSequence extra, structured rejection reasons (6 reasons), success cases |
| `SharedCaptureProcessorTest` | Structured Ready/Rejected results, all rejection propagations |
| `ShareCaptureViewModelTest` | Intent processing, note editing (2000-char enforcement), save workflow, duplicate detection (newest selected, text-only skips), repeated-save guard, failure/retry, domain properties of saved item |
| `InboxViewModelTest` | Default filter, filter changes, search functionality, status mutations (mark reviewed, archive, restore), soft delete confirmation, action progress guard, error handling, live Room updates |

## Database tests

`CaptureItemDaoTest` uses `Room.inMemoryDatabaseBuilder` to create an
ephemeral database for each test. Tests cover:

- Insert and read
- Update note
- Mark reviewed
- Archive (status + timestamp)
- Restore to pending
- Soft delete
- Inbox excludes deleted records
- Pending / reviewed / archived filters
- Search by URL, note, original content, source label
- Search excludes soft-deleted records
- Exact URL duplicate lookup
- Duplicate URLs allowed as separate records
- Newest-first ordering
- `insertAll` batch insertion
- `update` existing item
- `count` accuracy
- Empty duplicate results
- Soft-deleted items hidden from inbox but retrievable by ID

## What is not yet tested

- Export (Phase 6)
- Settings (Phase 7)

These will be added in their respective phases.

## Coverage expectations

Phase 4 covered:
- Inbox observation from Room (Flow-based)
- Filtering by status (Pending, Reviewed, Archived, All)
- Text search across content, URLs, notes, and labels
- Capture card rendering with UK date/time and source labels
- Status actions: mark reviewed, archive, restore
- Soft deletion with user confirmation
- URL interactions: external opening and clipboard copy
- Empty states and initial loading
- Phase 3 message cleanup

Phase 3 targets unit test coverage for:
- CharSequence shared content
- Structured rejection reasons (6 paths)
- ViewModel: save workflow, save failure, Cancel does not save, repeated Save guard
- ViewModel: duplicate detection (found, not found, multiple → newest selected)
- ViewModel: text-only skips URL duplicate lookup
- ViewModel: note included in saved item, blank note becomes null, note-length enforcement
- ViewModel: retry after failure restores Ready state

Phase 2 covered:
- URL extraction (pattern matching, normalisation, classification, punctuation trimming, dedup, limit)
- Intent parsing (ACTION_SEND, MIME types, content length limits, null/blank content)
- Processor orchestration (composition of parser + extractor + resolver)

Phase 1 (Milestone 1) covered:
- Domain logic (100% of validation paths)
- Mapping (all entity ↔ domain fields)
- Factory (all defaults and edge cases)
- DAO (all CRUD operations, filters, search, duplicate detection)

## Manual device test checklist

Test sharing from each source below on a real Android phone:

| Source | Verify |
|--------|--------|
| Chrome | TRACE Capture appears in Share menu; URL preview is correct; source label (Chrome) present |
| Firefox | Same as Chrome |
| Reddit | Post link and text shared correctly |
| X (Twitter) | Tweet text and link extracted |
| LinkedIn | Post content shared correctly |
| YouTube | Video URL extracted; source label present |
| GitHub | Issue/PR link and title extracted |
| Gmail | Email body text shared correctly |
| Plain-text app | Text-only content renders preview |

For each source:

1. TRACE Capture appears in the Share menu
2. Content preview is sensible (domain, text, URL count)
3. URL extraction is correct (single/multiple/none)
4. Source label is present when Android exposes it
5. Save button persists the record; "Saved to TRACE Pending" shown
6. Activity closes after save confirmation
7. Cancel creates no database record
8. Sharing the same URL again shows duplicate warning
9. Reopening TRACE Capture later (Phase 4 inbox) can read the stored record

Do not add platform-specific scraping to fix unusual share text from any source.

## Phase 4 Manual Verification Checklist

1. **Quick-capture check**:
   - Share a link to TRACE Capture and Save it.
   - Confirm "Saved to TRACE Pending" snackbar appears.
   
2. **Inbox Observation**:
   - Open TRACE Capture normally.
   - Confirm the saved link appears under **Pending**.
   
3. **Filtering**:
   - Select **All**, **Reviewed**, and **Archived** chips.
   - Confirm the list updates appropriately (e.g., empty for Reviewed/Archived if nothing was moved yet).
   
4. **Search**:
   - Type part of the URL or content into the search bar.
   - Confirm the list filters correctly.
   - Clear search and confirm the full list for the active filter returns.
   
5. **Mark Reviewed**:
   - Tap overflow menu → **Mark reviewed**.
   - Confirm it disappears from **Pending** and appears under **Reviewed**.
   
6. **Archive and Restore**:
   - Move an item to **Archived**.
   - Move it back to **Pending** using **Restore**.
   
7. **URL Actions**:
   - Tap overflow menu → **Open URL**. Confirm external browser opens.
   - Tap overflow menu → **Copy URL**. Confirm "Link copied" snackbar and paste into another app.
   
8. **Soft Delete**:
   - Tap overflow menu → **Delete**.
   - Confirm dialog appears with "Remove this capture...".
   - Confirm deletion. Verify item disappears from all filters.
   
9. **Accessibility**:
   - Verify TalkBack announces card details and action menus.
   - Check touch target sizes for filter chips and overflow icons.
   - Test with large font scaling.

## Phase 5 Manual Verification Checklist

1. Save a text-only capture.
2. Save a single-URL capture.
3. Save a multiple-URL capture.
4. Open the inbox.
5. Tap each capture and verify the correct detail screen.
6. Verify full original text is visible.
7. Open each URL externally.
8. Copy a URL.
9. Edit and save a note.
10. Restart the app and verify the note remains.
11. Type a note, press Back and verify unsaved-change protection.
12. Mark a Pending item Reviewed.
13. Restore a Reviewed item to Pending.
14. Archive an item.
15. Restore an Archived item.
16. Delete an item from detail.
17. Confirm it disappears from All.
18. Rotate or recreate the Activity and confirm state remains valid.
19. Test large font scaling.
20. Test TalkBack navigation.

## Test guidelines

1. Tests must not depend on the order of other tests
2. Each test should verify one behaviour
3. Database tests must close the in-memory database after execution
4. Use meaningful test data that reflects real content (URLs, social media source labels)
5. Do not write placeholder or empty test methods
