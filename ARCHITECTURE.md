# TRACE Capture — Architecture

## Overview

TRACE Capture is a local-first Android application for capturing URLs and text
via the system Share menu. Captures are stored locally in a Room database and
may later be synchronised with the TRACE Manifest editorial queue.

## Modules

Single-module project with clear package boundaries:

```
uk.co.pcgsoft.tracecapture
├── capture/          # Share intent receiver, ViewModel, intent parsing, URL extraction, quick-capture UI
├── data/
│   ├── local/        # Room database, DAO, entities, type converters, mapper
│   ├── repository/   # CaptureRepository interface + Room implementation
│   └── export/       # Export logic (Phase 6)
├── domain/           # Pure domain models, no Android dependencies
├── detail/           # Capture detail screen (Phase 5)
├── inbox/            # Inbox screen (Phase 4)
├── settings/         # Settings (Phase 7)
├── sync/             # Future TRACE sync API boundary (Phase 6+)
└── ui/               # Shared UI components, theme
```

## Data flow

```
Share menu → ShareReceiverActivity
                │  onNewIntent replaces draft
                ▼
         ShareCaptureViewModel  (Hilt, viewModelScope)
           ├── SharedCaptureProcessor
           │     ├── ShareIntentParser      (CharSequence, structured results)
           │     ├── UrlExtractor           (find, normalise, classify URLs)
           │     └── SourceApplicationResolver  (resolve sending app)
           │     ↓
           │   CaptureDraft (transient, not persisted directly)
           │
           ├── CaptureRepository.findExactUrlDuplicates  (non-blocking warning)
           │
           └── On Save:
                CaptureDraft → CaptureItemFactory → CaptureRepository.save()
                                                           ↓
                                                       Room DAO
                                                           ↓
                                                   TraceCaptureDatabase
```

## Explicit confirmation before persistence

TRACE Capture always shows a preview screen before saving. The user must
explicitly press Save. No capture is stored in Room without explicit
confirmation. A future settings phase may add configurable quick-save.

## Capture workflow

1. User shares text/URL from another app → `ShareReceiverActivity` launches
2. `ShareCaptureViewModel.processIntent()` runs the processor pipeline
3. Structured results: `SharedCaptureResult.Ready` or `.Rejected`
4. On `Ready`: UI shows preview with source label, primary domain, content
   preview, note field, duplicate warning (if applicable), Save/Cancel buttons
5. On Save: `CaptureItemFactory` creates a domain `CaptureItem` (PENDING,
   LOCAL_ONLY, UUID), then `CaptureRepository.save()` persists via Room
6. Success → brief "Saved to TRACE Pending" → activity finishes
7. Failure → error card with Retry
8. Cancel → no database write, activity finishes

## ViewModel ownership

- `ShareCaptureViewModel`: Injected into `ShareReceiverActivity`. Owns intent processing, note editing, and save coordination for new captures.
- `InboxViewModel`: Injected into `InboxScreen`. Owns inbox filtering, search, status mutation coordination (mark reviewed, archive, restore, delete), and ID-based selection state.
- `InboxExportViewModel`: Injected into `InboxScreen`. Owns bulk export workflow state while reusing the Phase 6A export coordinator, file writer, and share-file manager.
- `CaptureDetailViewModel`: Injected into `CaptureDetailScreen`. Owns capture observation by ID, note editing, status transitions, soft deletion, and unsaved-changes protection.
- `SettingsViewModel`: Injected into `SettingsScreen`. Owns device-local settings reading/writing, choice dialogs, temporary-file deletion, reset, and typed messages. It has no Android context dependency.

The composable observes `uiState` and renders the appropriate screen. No Room
access or domain construction happens in the composable layer.

## Navigation

Navigation Compose provides three routes:

- `inbox` — start destination, renders `InboxScreen`
- `detail/{captureId}` — renders `CaptureDetailScreen`, receives only the capture ID
- `settings` — renders `SettingsScreen` (Phase 7), accessed from the inbox top app bar

Only the capture ID is passed as a navigation argument. The detail screen
observes the capture from Room by ID, keeping Room as the single source of
truth. Navigation is defined in `AppRoutes` in `MainActivity.kt`; the settings
destination uses `SettingsRoute.route` from the `settings` package.

`AppRoutes.detail()` URI-encodes IDs before putting them in the route. The
detail ViewModel accepts only non-blank IDs from `SavedStateHandle`; a missing,
blank, or otherwise malformed argument does not start repository observation
and renders the existing Capture Not Found screen with Back still available.

`MainActivity` hosts a `NavHost` with all three routes. The `InboxScreen`
callbacks trigger navigation to `detail/{captureId}` and to `settings`
(`launchSingleTop`, so no duplicate Settings destinations). Back navigation
returns to the inbox with its natural Navigation Compose state preservation.

## Inbox and Search

The `InboxScreen` provides a view of all non-deleted captures. It supports:
- **Filtering**: By status (Pending, Reviewed, Archived) or All.
- **Search**: Case-insensitive search across original content, primary URL, note, and source label.
- **Observation**: Uses Room Flows to provide a live-updating list that reacts to external changes and internal actions.

The `InboxViewModel` combines filter and search states with repository flows to produce the final `InboxUiState`. Search results are filtered by the active status selection to ensure consistency.

### Inbox selection (Phase 6B)

Selection belongs to `InboxViewModel` and stores only `Set<String>` capture IDs in
`InboxSelectionState`. The explicit Select action and card long-press enter
selection mode. Inactive card taps navigate to detail; active card taps toggle the
ID. Per-item overflow menus are hidden while selection is active. Back and the
contextual app-bar back action clear IDs without changing the current filter or
search query.

Select all operates on the current `uiState.captures` only, so it respects the
active status filter and debounced search result. It becomes Clear all when every
visible result is selected.

Selection reconciliation has exactly one authoritative path. The Room/filter/
search flows emit only the visible captures (a pure projection — no selection
mutation happens inside the Room `map`). The single `combine(baseUiState,
_selection)` then: derives the visible IDs, reconciles the persisted selection
against them, stores the reconciled result once, and renders UI state from it.
There is no write-back from rendered state, so there is no two-way feedback loop
and no emission storm. Reconciliation is skipped while loading so the initial
empty emission can never clobber a selection restored from `SavedStateHandle`.
IDs that move outside a filter, are deleted, or disappear are removed exactly
once per Room emission. If the visible result becomes empty, selection mode
exits.

`SavedStateHandle` persists selection mode and up to 500 IDs. A larger selection
keeps contextual mode after recreation but restores with no IDs to avoid a
`TransactionTooLargeException`. Only a `Boolean` and an `ArrayList<String>` of
IDs ever enter `SavedStateHandle` — never whole `CaptureItem` objects. Stale
IDs restored from a previous session are removed after the first real Room
emission, and a restored empty selection never enables export.

At export time, `InboxExportViewModel` resolves IDs through
`CaptureRepository.getActiveByIds()` rather than trusting card snapshots. The
Room implementation excludes deleted rows and uses one `IN` query per 900-ID
chunk, keeping each query below SQLite's bind-parameter limit (selections above
900 IDs are chunked and reassembled, and duplicate input IDs never duplicate
results). The export ViewModel treats the visible inbox ID order
(`visibleOrderIds`) as authoritative for the on-screen list, so JSON arrays and
plain-text numbering match the screen order; repository return order never
changes it, missing IDs are excluded and reported for reconciliation without
shifting the remaining order, and equal timestamps fall back to a deterministic
newest-first, then ID-descending, order only for IDs absent from the visible
order. Phase 6B includes export only; there are no bulk status, archive, or
delete actions.

## Capture Actions

Captures in the inbox can be managed via an overflow menu:
- **Mark Reviewed**: Moves a capture from Pending to Reviewed status.
- **Archive**: Moves a capture to Archived status.
- **Restore**: Moves a Reviewed or Archived capture back to Pending.
- **Delete**: Soft-deletes the capture after user confirmation.
- **Open URL**: Opens the primary URL in a compatible external application.
- **Copy URL**: Copies the primary URL to the system clipboard with user confirmation.

All status changes and deletions are handled through the `CaptureRepository`, ensuring Room remains the single source of truth.

## Room as local source of truth

The Room database (`trace-capture.db`) is the single source of truth for all
captured items. All UI screens read from the database via the repository layer.
No other storage mechanism is used for captures in Milestone 1.

## Repository abstraction

The UI layer depends on `CaptureRepository` — never on the Room DAO directly.
This provides:

- A clean API boundary for testing
- The ability to swap the Room implementation for a fake or in-memory version
- A future path to add server sync transparently below the repository

The Room DAO is annotated with `@Dao` and injected via Hilt. The repository
wraps DAO calls, applies validation, and maps between Room entities and domain
models.

## Soft deletion

Items are never permanently deleted from the database by user actions — they
are "soft deleted" by setting `deletedAtEpochMillis`. The inbox queries
exclude soft-deleted records (`WHERE deleted_at IS NULL`). A future phase may
add permanent cleanup.

`observeById(id)` has the same active-capture contract: it emits only a
non-deleted capture, and emits `null` once that capture is soft-deleted.
`getById(id)` intentionally remains a direct lookup so repository or future
maintenance work can still inspect a soft-deleted row when needed.

## Duplicate detection: warning, not rejection

Duplicate detection runs automatically after a successful parse when
`primaryUrl` is not null. `CaptureRepository.findExactUrlDuplicates()` is
called in `viewModelScope` — failure does not block saving.

When one or more existing captures share the same `primaryUrl`, the UI shows
the newest capture's date with UK-locale formatting. The user may still save
another copy. Identical URLs are never silently discarded or merged.

No URL uniqueness constraint exists at the database level. The duplicate
detection interface is designed to be supplemented by server-side detection in
a future milestone.

## Hilt dependency injection

- `TraceCaptureDatabase` — provided as a singleton (`DataModule`)
- `CaptureItemDao` — extracted from the database (`DataModule`)
- `CaptureRepository` — bound to `RoomCaptureRepository` (`DataModule`)
- `CaptureItemFactory` — injectable utility
- `CaptureValidator` — injectable validation logic
- `ShareIntentParser` — bound to `ShareIntentParserImpl` (`CaptureModule`)
- `UrlExtractor` — bound to `UrlExtractorImpl` (`CaptureModule`)
- `SourceApplicationResolver` — bound to `SourceApplicationResolverImpl` (`CaptureModule`)
- `SharedCaptureProcessor` — bound to `SharedCaptureProcessorImpl` (`CaptureModule`)
- `ShareCaptureViewModel` — `@HiltViewModel`, injected into `ShareReceiverActivity`
- `CaptureModule` in the `capture` package provides all Phase 2–3 bindings

## Future sync boundary

All networking is behind the `CaptureSubmissionRequest` /
`CaptureSubmissionResult` interfaces in the `sync/` package. Milestone 1 does
not implement any network calls. The local database remains authoritative until
the server acknowledges a capture submission.

## Capture detail screen

The `CaptureDetailScreen` loads a capture by ID through `CaptureDetailViewModel`,
which uses `CaptureRepository.observeById(id)` — a Flow-based observation that
emits the current active item, then emits updated values after any Room change.
The ViewModel remains in Loading until the first observation emission. That
first emission either loads the capture or produces the stable Capture Not
Found state when no active capture exists.

### Note edit and save lifecycle

1. On load, the note draft is initialised from the stored `CaptureItem.note`.
2. Edits update the draft locally; `noteChanged` is derived by comparing the
   draft to the stored note.
3. Save calls `repository.updateNote(id, note)`. Blank/whitespace notes are
   saved as `null`.
4. After a successful save, Room observation becomes authoritative — the Flow
   emits the updated item and `noteChanged` resets.
5. Save failure preserves the draft so the user can retry.

### Unsaved-changes handling

When the user attempts to navigate away with unsaved note edits, the ViewModel
shows a confirmation dialog ("Discard note changes?"). This applies to system
Back, top-app-bar Back, and any explicit navigation-back action. If the note
is unchanged, navigation proceeds without a dialog.

### Status transitions

The detail screen shows status-specific action buttons matching the inbox
behaviour. All actions use existing `CaptureRepository` methods
(`markReviewed`, `archive`, `restoreToPending`) and rely on Room observation
for UI updates. The ViewModel validates this transition matrix before calling
the repository; invalid or no-op actions do not produce success feedback.

| From | Allowed transitions |
|---|---|
| Pending | Reviewed, Archived |
| Reviewed | Pending, Archived |
| Archived | Pending |

### Soft deletion

Delete triggers a confirmation dialog. On confirmation, `repository.softDelete`
is called. After success, the ViewModel emits one consumed back-navigation
request and the typed Capture Removed message. The deleted item no longer
appears under any inbox filter or `observeById` result.

### Export lifecycle

The detail screen includes an Export button that becomes available when the
capture's note is in a valid state (no unsaved changes required). Export
follows a two-step flow:

1. **Format selection**: User chooses JSON or Plain Text format via dialog.
2. **Save/Share choice**: User chooses to save to a document or share directly.

On Save, a document picker (`CreateDocument` contract) opens and
`pendingDocument` is held until the picker callback receives a URI. If the URI
is valid, the export content is written to it via `FileProvider`.

On Share, the export content is written to a cache file, wrapped with
`FileProvider.getUriForFile()`, and `pendingShare` is held until the Activity
result returns. The result only reports that the share chooser opened; the app
never claims the share was delivered. Dismissing the chooser keeps the
selection and clears `pendingShare` without an error.

Both `pendingDocument` and `pendingShare` are one-time consumables: they are
marked consumed before launching and cleared after the picker/activity result,
preventing stale re-exports or duplicate pickers if the screen is restored or
rotated. Only one pending operation is active at a time.

### Typed messages

`CaptureDetailMessage` is a sealed interface with typed success and failure
cases. The Compose layer maps messages to `strings.xml` in a `LaunchedEffect`.
No Android resource IDs exist in the ViewModel.

### Missing/deleted capture handling

An invalid ID or a first `null` observation shows the stable `Capture not
found` state, and Back remains available. If an already loaded capture later
emits `null` (for example, an external soft-delete), the ViewModel preserves
the visible capture and unsaved note draft long enough to emit the typed
Capture Removed message and one back-navigation request. It does not replace
that state with a generic Missing screen before navigation.


## Export design (Phase 6)

Export allows users to download a capture in JSON or plain-text format, via
save-to-file or share-to-app.

### Export pipeline

The export flow is coordinated by `CaptureExportViewModel` and consists of:

1. **`DefaultExportCoordinator.prepareExport()`**: Takes a list of captures,
   validates size limits, formats the output, and returns either `Success` or
   `Failure` with details.

2. **Formatters**: Both `JsonCaptureExportFormatter` and
   `TextCaptureExportFormatter` implement `CaptureExportFormatter` and delegate
   to `CaptureExportMapper` to build consistent document objects.

3. **File writing**: `AndroidExportFileWriter` writes prepared content to a
   user-selected URI via `ContentResolver.openOutputStream()`, running on
   `Dispatchers.IO`.

4. **Share file management**: `AndroidExportShareFileManager` writes to app
   cache, generates a `FileProvider` URI, and manages cache cleanup via
   `ExportCacheCleaner`.

### Validation and error handling

- **Empty captures**: `ExportFailure.EmptySelection`
- **Too many captures**: `ExportFailure.TooManyCaptures` (default: 10,000)
- **Output size**: `ExportFailure.OutputTooLarge` (default: 50 MB)
- **Formatting error**: `ExportFailure.FormattingFailed()`
- **File write error**: `FileWriteResult.Failure()`

### Export state management

`DetailExportState` holds:

- `pendingDocument`: One-time `CreateExportDocumentRequest` consumed by
  document picker callback.
- `pendingShare`: One-time `PreparedShareExport` consumed by share
  activity result.
- `isPreparing`: Flag showing that export formatting or writing is in
  progress.

Both `pending*` fields are cleared after their respective callback/result,
ensuring no stale re-exports on screen rotation or restoration.

### Bulk export (Phase 6B)

Bulk export follows the existing pipeline and does not introduce a second
formatter:

```text
selected IDs
  -> repository active-ID resolution
  -> visible-order reconciliation
  -> ExportCoordinator.prepareExport(..., SUPPLIED_CAPTURE_LIST)
  -> existing JSON/plain-text formatter
  -> existing SAF writer or FileProvider share manager
```

The bulk format chooser reports the selected count. Save uses
`ACTION_CREATE_DOCUMENT`, the existing MIME type and suggested extension, then
writes bytes through `ExportFileWriter`. Picker cancellation consumes the
pending request without an error and leaves selection intact. Share creates one
cache file and shares one `content://` FileProvider URI via `ACTION_SEND` with
read permission; the export is never copied into `EXTRA_TEXT`.

Document and share requests are marked consumed before launching so Activity
recreation does not launch a duplicate picker or Sharesheet. Successful save
exits selection mode once and shows "Export saved"; a share-chooser launch
exits selection mode once and shows the accurate "Share chooser opened" message
— the app never claims delivery. Picker/chooser dismissal and preparation or
write failures leave the selection and its IDs intact for retry. Repeated Save
taps cannot queue a second document request while one is preparing or pending.
Bulk exports retain schema version 1 and use the stable `supplied_capture_list`
source value.

### Document picker and share intents

- **Save to file**: `CreateDocument` activity contract receives a URI. If
  non-null and valid, `AndroidExportFileWriter.write()` is called. Result
  triggers success or failure message. Picker cancellation consumes the pending
  request without an error.
- **Share**: `prepareShareExport()` creates a cache file with a `FileProvider`
  URI, holds `pendingShare`, and launches an `ACTION_SEND` chooser with a single
  attachment, read-only permission grant, and no export content in `EXTRA_TEXT`.
  The chooser result only confirms that the chooser opened ("Share chooser
  opened"); dismissal keeps the selection and clears `pendingShare` without
  claiming delivery.

### Limits and safety

Default limits are conservative:

- 10,000 captures per export (cumulative size varies)
- 50 MB max output bytes
- UTF-8 encoding for all text formats
- Cache-based sharing with automatic cleanup (`ExportCacheCleaner`, retention-driven)
- No server-side export; all work is local and ephemeral

## Settings (Phase 7)

All settings are local to the device. There are no accounts, login, cloud
synchronisation, server configuration, API keys, analytics, telemetry, remote
configuration, automatic publishing/upload, notifications, or background jobs.

### Storage architecture

Settings use AndroidX DataStore Preferences in one named file
(`trace_capture_settings`). `SettingsModule` provides a single
application-scoped `DataStore<Preferences>` and binds `SettingsRepository` to
`DataStoreSettingsRepository`. The repository interface is separated from the
DataStore implementation, and the ViewModel depends only on the interface —
application context is injected only into the DataStore boundary. Reads never
crash collection (failures emit safe defaults); writes return a typed
`SettingsWriteResult` instead of leaking exceptions. SharedPreferences is not
used.

### Preference keys, stable values and defaults

| Key | Persisted values | Default |
|---|---|---|
| `default_inbox_filter` | `pending`, `reviewed`, `archived`, `all` | `pending` |
| `preferred_export_format` | `ask_every_time`, `json`, `plain_text` | `ask_every_time` |
| `exit_selection_after_successful_export` | `true`/`false` | `true` |
| `temporary_export_retention` | `one_hour`, `twenty_four_hours`, `seven_days` | `twenty_four_hours` |
| `confirm_before_reset` | `true`/`false` | `true` |

Values are explicit stable strings (never ordinals or enum names). Unknown,
missing and malformed persisted values fall back safely to their defaults.
`SettingsDefaults.value` is the single authoritative defaults object.

### Default inbox filter

The stored default applies once when a fresh `InboxViewModel` is created.
Precedence: restored/current-session filter > explicit current-session choice
> stored default > Pending fallback. The default is read once (`.first()`), so
later DataStore emissions never overwrite an active session and there is no
preference-to-UI feedback loop. A malformed preference falls back to Pending.

### Preferred export format

The `preferred_export_format` preference drives both the detail
(`CaptureExportViewModel`) and bulk (`InboxExportViewModel`) export flows.
`ask_every_time` keeps the format chooser; `json` and `plain_text` skip it and
preselect the format in the Save/Share dialog. No export begins until Save or
Share is explicitly chosen, cancellation is always possible, and a preference
read failure falls back to Ask every time. The snapshot is read when the export
begins, so a mid-export setting change never alters the active operation.

### Exit selection after successful export

Bulk export only. When enabled (default), a successful Save or Share-chooser
launch exits selection mode. When disabled, selection, IDs, filter and search
are retained so the same set can be exported again. Failures and cancellation
always retain selection regardless of the preference. The snapshot is captured
at operation start. Detail export is unaffected.

### Temporary export retention and deletion

`temporary_export_retention` maps to 1 hour / 24 hours / 7 days and is read by
`ExportCacheCleaner.cleanupWithCurrentRetention()` before a new shared export
is created (the only safe lifecycle point — there is no WorkManager or
scheduled process). Cleanup and explicit deletion consider only files with the
`trace-capture-` prefix inside the dedicated export cache directory; unrelated
cache files and user-saved documents are never touched. An invalid preference
falls back to 24 hours. The Settings screen's "Delete temporary files now"
confirms first, runs through `ExportShareFileManager.deleteAllTemporaryExports()`
(no filesystem paths exposed to UI), and reports typed success/failure messages.

### Reset-to-defaults

Reset performs one atomic DataStore `clear()`. It restores all Phase 7
defaults, never deletes captures, never mutates the database, and never deletes
temporary files unless separately requested. When `confirm_before_reset` is
enabled a confirmation dialog explains that captures and exported files are
unaffected. The UI updates immediately from the DataStore flow.

### Privacy, data and app information

"Privacy and local data" explains (without legal claims): captures are stored
locally; text/links are received only on explicit share; the app does not
monitor the clipboard, use an accessibility service, or scrape other apps;
exports require explicit action; save uses a user-chosen document destination;
share uses a temporary cache file and the Android Sharesheet; there is no
automatic upload or server sync; deletion is a soft delete; and temporary files
are cleaned per the selected retention period. No encryption-at-rest claim is
made.

App information (name, version, build, package) comes from `BuildConfig` in
the composable layer — never hardcoded. A simple local licences screen lists
the framework categories used (AndroidX, Jetpack Compose, Room, Hilt, Kotlin
coroutines, Kotlin serialization) without fabricating licence text.

## Repository hygiene

Kotlin compiler diagnostics are local generated output. `.kotlin/` is excluded
from version control along with Gradle and Android Studio local build files.

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| Epoch milliseconds for timestamps | Avoids `java.time` dependency complexity for Milestone 1 |
| UUIDs for capture IDs | Locally generated, globally unique, no server needed |
| JSON for detected URLs list | Handles arbitrary URLs without delimiter-escaping issues |
| No destructive migration | Preserves user data across schema changes |
| Separate domain model from entity | Allows the domain to evolve independently of the database schema |

## Phase boundaries

Phase 7 settings, quick-save configuration, import, synchronisation, cloud
upload, and authentication remain deferred. Phase 6B adds export-only
selection, does not modify the Room schema, and requests no storage permission.
