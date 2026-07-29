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
- `InboxViewModel`: Injected into `InboxScreen`. Owns inbox filtering, search, and status mutation coordination (mark reviewed, archive, restore, delete).
- `CaptureDetailViewModel`: Injected into `CaptureDetailScreen`. Owns capture observation by ID, note editing, status transitions, soft deletion, and unsaved-changes protection.

The composable observes `uiState` and renders the appropriate screen. No Room
access or domain construction happens in the composable layer.

## Navigation

Navigation Compose provides two routes:

- `inbox` — start destination, renders `InboxScreen`
- `detail/{captureId}` — renders `CaptureDetailScreen`, receives only the capture ID

Only the capture ID is passed as a navigation argument. The detail screen
observes the capture from Room by ID, keeping Room as the single source of
truth. Navigation is defined in `AppRoutes` in `MainActivity.kt`.

`MainActivity` hosts a `NavHost` with both routes. The `InboxScreen` callback
`onCaptureSelected` triggers navigation to `detail/{captureId}`, replacing the
Phase 4 placeholder. Back navigation restores the inbox with its natural
Navigation Compose state preservation.

## Inbox and Search

The `InboxScreen` provides a view of all non-deleted captures. It supports:
- **Filtering**: By status (Pending, Reviewed, Archived) or All.
- **Search**: Case-insensitive search across original content, primary URL, note, and source label.
- **Observation**: Uses Room Flows to provide a live-updating list that reacts to external changes and internal actions.

The `InboxViewModel` combines filter and search states with repository flows to produce the final `InboxUiState`. Search results are filtered by the active status selection to ensure consistency.

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
emits the current item immediately, then emits updated values after any Room
change (note edits, status transitions, soft deletion).

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
for UI updates.

### Soft deletion

Delete triggers a confirmation dialog. On confirmation, `repository.softDelete`
is called. After success, the ViewModel signals navigation back to the inbox.
The deleted item no longer appears under any inbox filter.

### Typed messages

`CaptureDetailMessage` is a sealed interface with typed success and failure
cases. The Compose layer maps messages to `strings.xml` in a `LaunchedEffect`.
No Android resource IDs exist in the ViewModel.

### Missing/deleted capture handling

If the observed `CaptureItem` is null (invalid ID or soft-deleted while
viewing), the screen shows a stable `Capture not found` state with a message
that the capture may have been removed. The user can navigate back to the inbox.

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| Epoch milliseconds for timestamps | Avoids `java.time` dependency complexity for Milestone 1 |
| UUIDs for capture IDs | Locally generated, globally unique, no server needed |
| JSON for detected URLs list | Handles arbitrary URLs without delimiter-escaping issues |
| No destructive migration | Preserves user data across schema changes |
| Separate domain model from entity | Allows the domain to evolve independently of the database schema |
