# TRACE Capture — Architecture

## Overview

TRACE Capture is a local-first Android application for capturing URLs and text
via the system Share menu. Captures are stored locally in a Room database and
may later be synchronised with the TRACE Manifest editorial queue.

## Modules

Single-module project with clear package boundaries:

```
uk.co.pcgsoft.tracecapture
├── capture/          # Share intent receiver
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
Share menu → ShareReceiverActivity → CaptureItemFactory → CaptureRepository
                                                              ↓
                                                         Room DAO
                                                              ↓
                                                     TraceCaptureDatabase
```

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

Duplicate URL detection is local-only in Milestone 1. When a possible duplicate
is found, the UI shows a warning with the date of the earlier capture. The user
may still save a new copy. Identical URLs are never silently discarded.

The duplicate detection interface is designed to be supplemented by server-side
detection in a future milestone.

## Hilt dependency injection

- `TraceCaptureDatabase` — provided as a singleton
- `CaptureItemDao` — extracted from the database
- `CaptureRepository` — bound to `RoomCaptureRepository`
- `CaptureItemFactory` — injectable utility
- `CaptureValidator` — injectable validation logic

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
