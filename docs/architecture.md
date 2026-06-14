# CYL Architecture Notes

## Product Direction

ChangeYourLife starts as a personal productivity app with workspaces, pages, notes, tasks, and offline storage. AI, realtime sync, and collaboration are later phases.

## Initial Modules

```txt
androidApp
|-- data          Local database, network DTOs, repositories
|-- domain        Use cases and app models
|-- presentation  Screens, navigation, ViewModels
|-- core          Shared utilities and dependency injection

backend
|-- plugins       Ktor feature configuration
|-- routes        HTTP routes
|-- model         Request and response models
```

## Early Data Direction

- Android should write to Room first for an offline-first user experience.
- Backend APIs should be shaped around future sync, but Phase 1 should keep sync simple.
- Records that will sync later should include `id`, `createdAt`, `updatedAt`, and eventually `deletedAt` plus `syncStatus`.
- Workspace selection is local-first for now. Pages, tasks, and reminders carry `workspaceId` so they can sync cleanly later.
- Pages already support `parentPageId` for basic hierarchy before the full block editor exists.
- Page content now stores a serialized block document for the Android MVP. This keeps Room schema stable while the block model is still evolving.
- Subpage properties are stored in the same serialized page document as blocks, so the Android Room page schema does not need a migration for the current MVP.
- Database table blocks are currently embedded inside the page block document. Table, list, board, calendar, gallery, timeline, and dashboard views now reuse the same row/column data. Future map, feed, richer charting, and automation views can build on the same model before it is promoted into a dedicated synced collection table.

## Current Boundary

This foundation includes local-first productivity flows, basic authentication, an MVP block editor, and an embedded table database block with Table/List/Board/Calendar/Gallery/Timeline/Dashboard rendering. It intentionally does not include AI, realtime sync, collaboration, map/feed database views, or advanced rich-text block editing yet.
