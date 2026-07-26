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

`ShareCaptureViewModel` is a Hilt ViewModel injected into
`ShareReceiverActivity`. It owns:
- The `ShareCaptureUiState` flow (Loading → Ready/Invalid → Saved/Failed)
- Intent processing (via `SharedCaptureProcessor`)
- Note editing state
- Duplicate lookup (async, non-blocking)
- Save coordination (factory → repository)
- Retry logic after failure

The composable observes `uiState` and renders the appropriate screen. No Room
access or domain construction happens in the composable layer.

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

## Key design decisions

| Decision | Rationale |
|----------|-----------|
| Epoch milliseconds for timestamps | Avoids `java.time` dependency complexity for Milestone 1 |
| UUIDs for capture IDs | Locally generated, globally unique, no server needed |
| JSON for detected URLs list | Handles arbitrary URLs without delimiter-escaping issues |
| No destructive migration | Preserves user data across schema changes |
| Separate domain model from entity | Allows the domain to evolve independently of the database schema |
