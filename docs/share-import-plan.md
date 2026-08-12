# CYL Share Import Plan

Status: Phase A through Phase E implemented. Phase F hardening remains.

Last reviewed: 2026-08-12

## Objective

Allow CYL to receive text, URLs, images, PDFs, and selected files from other Android apps through the system Sharesheet. Incoming content must be previewed and confirmed before it changes a page or enters an AI chat.

The implementation must remain reliable across cold start, warm start, process recreation, offline use, sync, and a second device.

## Current Audit

The supported share flow is now implemented through explicit domain boundaries:

1. `MainActivity` accepts supported cold-start and warm-start share intents and delegates them to durable intake.
2. `ShareIntentParser`, `IncomingShareCoordinator`, Room drafts, and the dedicated import route prevent raw intent payloads from becoming navigation state.
3. Images, PDFs, and selected files are copied into app-private generic content assets before page or AI use.
4. Page import commits through the page repository with revision checks and queues asset upload separately.
5. `Ask AI` is an explicit handoff. Send approval is stamped with source, source reference, and approval time before the backend accepts an attachment.
6. Composer file IO, bounded reads, hashing, image validation, and thumbnail generation live outside Compose UI.
7. The backend validates actual attachment payload sizes, extracts selectable PDF text per page, and renders only scanned/insufficient pages for bounded LM Studio OCR before sanitized text is passed to OpenRouter.

The remaining work is Phase F operational hardening and broader device/cross-device verification.

## Product Scope

### Initial supported input

- `text/plain`
- `text/html`
- Shared URLs
- `image/jpeg`
- `image/png`
- `image/webp`
- `image/gif`
- `application/pdf`
- `ACTION_SEND_MULTIPLE` for supported images and PDFs

Do not register `*/*` in the initial release. CYL should only appear for content it can validate, store, preview, and reopen.

### Initial limits

- Maximum 10 shared items per intent
- Maximum 100 MB total staged content
- Maximum 15 MB per image
- Maximum 50 MB per PDF
- Maximum 1 MB shared text
- Unknown file size is allowed only while streaming with an enforced byte limit

These are share-staging/product limits. The narrower AI handoff policy is enforced independently by the backend: at most three PDFs, 8 MiB decoded per PDF, 24 MiB decoded across PDFs, 40 inspected pages per PDF, and a 40 MiB authenticated AI request body. Limits must be centralized in the relevant domain policy rather than duplicated in UI and backend code.

### Later support

- Office documents
- CSV/JSON/Markdown as imported text or files
- Audio and video media blocks
- Broader OCR beyond the bounded AI attachment pipeline
- Broader document AI analysis
- Direct Share shortcuts

## User Experience

### Entry flow

1. The user taps Share in another app and chooses CYL.
2. CYL immediately stages readable content into app-owned private storage.
3. CYL opens a dedicated `Import to CYL` page.
4. The page shows item previews, validation status, and recoverable errors.
5. The user chooses `New page`, `Existing page`, or `Ask AI`.
6. The user can change the title, choose a destination, and remove unwanted items.
7. Nothing changes in CYL until the user confirms.

### Content mapping

- Shared plain text becomes a Text block.
- A single URL becomes a WebBookmark block.
- Safe HTML becomes CYL rich text after sanitization; raw HTML is never canonical storage.
- Images and PDFs become MediaFile attachments.
- Shared text plus files becomes a Text block followed by a MediaFile block.
- Multiple files are imported as one ordered media group unless the user removes or reorders items.
- The shared subject is a suggested page title, never an unconditional overwrite.

### Authentication behavior

Incoming content must be staged before authentication routing because the sender's URI grant can be temporary. If the user is signed out, retain the staged draft in private storage, complete login, then continue to the import page.

### AI privacy boundary

Importing to a page does not send the content to an AI provider. Content is sent to AI only when the user explicitly selects `Ask AI` or attaches it from the AI composer.

## Target Architecture

```text
Android Intent
  -> ShareIntentParser
  -> IncomingShareCoordinator
  -> StageSharedContentUseCase
  -> IncomingShareDraftRepository
  -> ShareImportViewModel
  -> ImportSharedContentUseCase
  -> ContentAssetRepository
  -> PageRepository / PageMutationUseCase
```

### Android entry point

`MainActivity` responsibilities:

- Accept the initial intent in `onCreate()`.
- Accept additional intents in `onNewIntent()`.
- Assign or preserve an internal share-event ID.
- Pass the intent to `IncomingShareCoordinator`.
- Never decode images, read PDFs, upload files, or mutate pages directly.

Use `singleTop` only after its navigation/task behavior is verified. Always call `setIntent()` when accepting a new intent.

### Intent parser

`ShareIntentParser` must handle:

- `Intent.EXTRA_TEXT`
- `Intent.EXTRA_HTML_TEXT`
- `Intent.EXTRA_SUBJECT`
- `Intent.EXTRA_STREAM`
- `ClipData`
- `ACTION_SEND_MULTIPLE`
- A sender that declares the wrong MIME type
- Null or inaccessible URIs
- A screenshot containing both an image and source URL

The parser produces metadata only. It does not perform blocking IO.

### Incoming share coordinator

`IncomingShareCoordinator` is an application-scoped handoff mechanism with a `StateFlow` of pending share events. It deduplicates the same Activity intent across recomposition or recreation without permanently blocking the user from sharing the same file again later.

### Durable draft storage

Add normalized Room entities for incoming drafts and their items. Draft metadata and staged file paths must survive process death and authentication. Temporary drafts are removed after cancellation, completion, or an expiry window.

Suggested states:

- `RECEIVED`
- `VALIDATING`
- `STAGED`
- `IMPORTING`
- `UPLOAD_QUEUED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

## Generic Content Asset Foundation

### Problem to remove

A page must not use an external `content://` URI as its durable or synced asset identity.

### Canonical model

Introduce a stable content asset ID. Page blocks and FilesMedia cells reference that ID. Device-specific local paths and backend storage keys live in an asset repository, not in page JSON.

Suggested asset metadata:

- `id`
- `workspaceId`
- `ownerPageId`
- `displayName`
- `mimeType`
- `sizeBytes`
- `sha256`
- `localPath`
- `remoteAssetId`
- `status`
- `createdAt`
- `updatedAt`
- `deletedAt`

Suggested asset states:

- `LOCAL_READY`
- `UPLOAD_QUEUED`
- `UPLOADING`
- `REMOTE_READY`
- `DOWNLOAD_REQUIRED`
- `RETRYABLE_FAILURE`
- `PERMANENT_FAILURE`
- `DELETED`

### Android database

- Add `PageAssetEntity` and `PageAssetDao`.
- Raise the Room schema from version 20 to 21.
- Export and verify the Room schema.
- Add indexes for workspace, page, remote ID, SHA-256, status, and cleanup.
- Keep backward decoding for existing `PageMediaAttachment.uri` values.
- Migrate legacy attachments lazily when they are opened or edited.

### Local staging

- Copy incoming streams to app-owned private storage on `Dispatchers.IO`.
- Stream through a bounded buffer; do not call unrestricted `readBytes()`.
- Calculate SHA-256 while copying.
- Stop immediately when a limit is exceeded.
- Sanitize filenames and never derive a filesystem path directly from sender metadata.
- Delete partial files on error or cancellation.
- Generate image thumbnails off the main thread.
- Do not fully decode a PDF merely to show its import preview.

## Backend and Sync

### Storage transport

Refactor the existing R2 client into a generic private-object storage transport. Voice notes and page assets may share the transport, credentials, and signing implementation, but retain separate domain services and validation policies.

Do not weaken the existing voice-note endpoint to accept arbitrary files.

### Backend persistence

Add migration `V17__content_assets.sql` with ownership, workspace, storage key, metadata, hash, status, idempotency key, and lifecycle timestamps.

Required backend operations:

- Create upload intent
- Verify completed upload
- Return short-lived signed download URL
- Soft delete an asset
- Retry storage deletion
- Clean orphaned uploads
- Enforce user/workspace ownership on every operation

### Upload behavior

- Use presigned upload URLs.
- Use WorkManager for retryable uploads.
- Persist progress and retry state.
- Verify byte count, MIME type, and SHA-256 before marking the asset ready.
- Refresh expired upload URLs automatically.
- Never store permanent public R2 URLs in page content.

### Sync ordering

Page blocks reference stable asset IDs. Local page editing may complete before upload, but sync must preserve the dependency:

1. Create local asset record.
2. Commit the page mutation locally.
3. Queue asset upload.
4. Sync asset metadata and page content in deterministic order.
5. A second device displays `Uploading` or `Unavailable` until the remote asset is ready.

If a page mutation fails, the staged asset remains unattached and is removed by orphan cleanup.

## Page Import Mutation

`ImportSharedContentUseCase` must use `PageRepository` and `PageMutationUseCase`, the same mutation boundary used by manual editing and AI actions.

For a new page:

- Create the page and initial document in one logical operation.
- Roll back or trash the incomplete page if content commit fails.

For an existing page:

- Load the latest revision.
- Build all imported blocks.
- Commit the document atomically.
- On revision conflict, reload and ask the user to retry rather than silently overwriting edits.

Importing multiple items must not leave half of the block structure committed.

## Navigation

Add a dedicated route such as `share/import/{draftId}`. Route arguments carry only the draft ID, never raw text, base64 data, filenames, or content URIs.

`CylNavHost` observes a staged draft and navigates only after authentication is ready. Consuming a navigation event must be idempotent.

## Error and Recovery Behavior

Handle these cases explicitly:

- Unsupported MIME type
- Sender reports the wrong MIME type
- URI access denied or revoked
- Unknown or excessive size
- Too many items
- Corrupt image or PDF
- Disk full
- Process killed while staging
- User signs in after sharing
- Destination page deleted
- Page revision conflict
- Offline upload
- Expired upload URL
- Backend or R2 unavailable
- App restarted during upload

Recoverable failures expose Retry. Permanent failures explain the rejected item. Valid items remain removable, but page import remains an explicit final confirmation.

## Security and Privacy

- Do not trust MIME type, filename, size, or URI scheme from the sender.
- Accept only `content://` and explicitly supported safe sources.
- Never execute or render active file content as HTML or script.
- Sanitize imported HTML into CYL rich-text spans.
- Do not log shared text, URI values, file paths, or extracted document content.
- Store files privately and serve remote files through short-lived signed URLs.
- Use `Content-Disposition: attachment` for arbitrary downloadable files.
- Do not send imported content to AI without explicit user intent.
- Remove stale drafts and orphaned local/remote assets.

## Implementation Order

### Current checkpoint

- [x] Content asset domain model and repository boundaries
- [x] Room `page_assets` entity, DAO, migration 20 to 21, and exported schema
- [x] App-private bounded stream copy with MIME inspection and SHA-256
- [x] Atomic staging cleanup when metadata persistence fails
- [x] Soft-delete cleanup use case with path-boundary enforcement
- [x] Backward-compatible `assetId` field and legacy URI migration use case
- [x] Generic private R2 transport with unchanged voice-note validation
- [x] Backend content-asset migration, ownership checks, idempotency, verification, and cleanup
- [x] Android typed upload gateway, persisted progress, and WorkManager retry/resume
- [x] Stable owner-scoped asset identity and lazy signed-URL hydration for another device
- [x] Resolve imported page media through stable asset IDs, private local files, and signed remote URLs
- [x] Durable incoming share drafts and Android intent intake
- [x] Share import UI and atomic page mutation
- [x] Explicit Incoming Share to AI handoff with auditable approval metadata
- [x] Generic image/text/PDF attachment preparation outside Compose UI
- [x] Backend attachment validation and bounded selectable-text PDF extraction
- [x] Bounded scanned/mixed-PDF rendering and LM Studio OCR routing, with sanitized text-only OpenRouter handoff

The manifest advertises only the MIME types listed in the initial scope. Imported media uses the
generic asset path. Files selected directly inside the legacy page/table picker remain backward
compatible with persisted document URIs until that picker is migrated to the generic staging flow.

### Phase A: Asset foundation

1. Define content asset domain models and repository contracts.
2. Add Room asset entities, DAO, migration 20 to 21, and local repository.
3. Add bounded stream staging and cleanup use cases.
4. Add backward compatibility for legacy media URIs.

Exit condition: a locally imported file survives app restart without depending on the sender's URI.

### Phase B: Backend asset lifecycle

1. Generalize the private R2 transport without changing voice-note behavior.
2. Add backend asset migration, repository, service, and routes.
3. Add Android upload gateway and WorkManager worker.
4. Add signed download and remote-open flow.

Exit condition: an imported attachment opens correctly on a second device.

### Phase C: Share intake

1. [x] Add `ShareIntentParser`.
2. [x] Add `IncomingShareCoordinator` and durable draft repository.
3. [x] Handle cold-start and warm-start intents.
4. [x] Add supported `ACTION_SEND` and `ACTION_SEND_MULTIPLE` filters last.

Exit condition: CYL appears only for supported types and never creates duplicate drafts.

### Phase D: Import UI and page mutation

1. [x] Add `ShareImportRoute` and ViewModel.
2. [x] Add preview, destination selection, title edit, remove, retry, cancel, and confirm.
3. [x] Map plain text, passive HTML, URLs, images, and PDFs into CYL blocks.
4. [x] Commit page content and asset metadata atomically through `PageRepository`.
5. [x] Reject stale existing-page imports using revision and local update-time checks.
6. [x] Finalize the draft and queue durable asset uploads in the same Room transaction.

Exit condition: new-page and existing-page imports are atomic and recoverable.

### Phase E: AI integration

1. [x] Extract the current chat attachment reader out of UI code.
2. [x] Reuse generic assets for image and text-file attachments.
3. [x] Add bounded per-page PDF text extraction as a separate backend capability.
4. [x] Keep `Ask AI` explicit and auditable.
5. [x] Persist only lightweight chat snapshots for asset-backed attachments; inline AI payloads are not synced.
6. [x] Render only scanned/insufficient PDF pages for bounded LM Studio OCR and pass only sanitized evidence to OpenRouter.

Exit condition: AI receives only supported, user-approved content; raw oversized files are rejected, visual data remains in the LM Studio stage, and attachment instructions cannot authorize a page mutation without the original user prompt.

### Phase F: Hardening

1. Add cleanup workers and orphan reconciliation.
2. Add telemetry without logging content.
3. Profile staging, thumbnails, scrolling, and upload memory.
4. Complete device and cross-device verification.

## Verification Matrix

### Unit tests

- Parse text, subject, HTML, URL, one stream, ClipData, and multiple streams.
- Reject missing, malformed, unsupported, and excessive input.
- MIME sniffing and filename sanitization.
- Size-limited streaming and SHA-256.
- Content mapping to page blocks.
- Intent deduplication and draft state transitions.

### Android integration tests

- Cold start from a share intent.
- Warm start through `onNewIntent()`.
- Rotation and process recreation without duplicate import.
- Share while signed out, then continue after login.
- Import into a new and existing page.
- Cancel and verify temporary file cleanup.
- Offline import followed by upload retry.

### Backend tests

- Authentication and workspace ownership.
- Idempotent upload intent creation.
- Size, MIME, and SHA mismatch rejection.
- Expired signed URLs.
- Orphan and deletion cleanup.
- Storage outage and retry behavior.

### Manual device matrix

- Chrome URL/text
- Gallery image and multiple images
- Android Files PDF
- WhatsApp image/text where available
- Gmail attachment where available
- Android API 26 and current target API 36
- Slow network, offline mode, app restart, and second-device download

## Definition of Done

The feature is complete only when all statements below are true:

- CYL appears only for supported share types.
- Cold and warm app launches receive the same content correctly.
- Rotation and process recreation do not duplicate a draft.
- Shared files remain available after restart and sender access loss.
- New-page and existing-page imports are atomic.
- Offline imports recover without user data loss.
- Synced assets open on another device.
- Unsupported, corrupt, or oversized items produce clear errors.
- Cancel removes temporary data.
- No file IO, hashing, thumbnail generation, or PDF work runs on the main thread.
- No shared content reaches AI without explicit approval.
- Existing voice-note upload behavior remains unchanged.

## References

- Android receive-sharing guide: https://developer.android.com/develop/ui/compose/sharing/receive
- Android document access and persisted URI guidance: https://developer.android.com/training/data-storage/shared/documents-files
