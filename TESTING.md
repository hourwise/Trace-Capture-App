# TRACE Capture — Testing

## Test structure

```
src/test/                    # Unit tests (JVM, no Android dependency)
  uk.co.pcgsoft.tracecapture.data.local
    CaptureItemMapperTest    # Entity ↔ domain mapping
    CaptureItemFactoryTest   # Factory defaults, UUID, timestamps
    CaptureValidatorTest     # Validation rules

src/androidTest/             # Instrumented tests (Android device/emulator)
  uk.co.pcgsoft.tracecapture.data.local
    CaptureItemDaoTest       # Room database operations with in-memory DB
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

- Share intent reception (Phase 2)
- Quick-capture UI (Phase 3)
- Inbox Compose rendering (Phase 4)
- Capture detail editing (Phase 5)
- Export (Phase 6)
- Settings (Phase 7)
- Integration tests at the Compose level

These will be added in their respective phases.

## Coverage expectations

Milestone 1 targets unit test coverage for:
- Domain logic (100% of validation paths)
- Mapping (all entity ↔ domain fields)
- Factory (all defaults and edge cases)
- DAO (all CRUD operations, filters, search, duplicate detection)

## Test guidelines

1. Tests must not depend on the order of other tests
2. Each test should verify one behaviour
3. Database tests must close the in-memory database after execution
4. Use meaningful test data that reflects real content (URLs, social media source labels)
5. Do not write placeholder or empty test methods
