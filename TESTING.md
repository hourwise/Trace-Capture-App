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

src/androidTest/             # Instrumented tests (Android device/emulator)
  uk.co.pcgsoft.tracecapture.data.local
    CaptureItemDaoTest       # Room database operations with in-memory DB
  uk.co.pcgsoft.tracecapture.capture
    ShareReceiverActivityTest # Quick-capture UI rendering (Hilt + Compose)
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

- Inbox Compose rendering (Phase 4)
- Capture detail editing (Phase 5)
- Export (Phase 6)
- Settings (Phase 7)

These will be added in their respective phases.

## Coverage expectations

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

## Test guidelines

1. Tests must not depend on the order of other tests
2. Each test should verify one behaviour
3. Database tests must close the in-memory database after execution
4. Use meaningful test data that reflects real content (URLs, social media source labels)
5. Do not write placeholder or empty test methods
