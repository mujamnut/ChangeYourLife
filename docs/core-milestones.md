# CYL Core Milestones

Dokumen ini ialah checklist sementara untuk susun kerja core ChangeYourLife (CYL).
Padam fail ini bila milestone utama sudah siap dan sudah dipindahkan ke dokumentasi kekal.

## Status Ringkas

- Done: asas Android, backend Ktor, auth, Room DB, AI chat/action, table editor, chat history, Render/Aiven setup.
- Not solid yet: sync sebenar, AI action contract, table typed data, editor architecture, search index, media cloud, production safety.

## Milestone 1: Data Sync Core

Goal: jadikan backend + Aiven sebagai source of truth, bukan sekadar login/AI.

Status: in progress. Backend API asas dan Android local-first sync sudah dibuat.

### Sudah Ada

- Backend ada Postgres/Flyway migration untuk `users`, `workspaces`, `pages`, `tasks`, `reminders`, `password_reset_codes`.
- Android ada Room local DB untuk workspace, page, task, reminder, chat.
- Trash/restore page local sudah ada.
- Backend ada authenticated `/api/v1/workspaces` dan `/api/v1/pages`.
- Android ada `SyncApi` untuk workspace/page.
- `WorkspaceRepositoryImpl` dan `PageRepositoryImpl` sudah cuba refresh/push remote secara best-effort.
- Backend sync route ada regression test untuk workspace/page create, list, delete, restore.

### Belum Kukuh

- Sync sekarang local-first best-effort, belum full source-of-truth.
- Backend belum ada API khusus untuk table/row/property; page content masih sync sebagai JSON.
- Page content masih JSON text, belum ada sync conflict strategy.
- Room masih guna `fallbackToDestructiveMigration(true)`.
- Default workspace id masih legacy local id; perlu strategy user-scoped workspace id untuk multi-user production.

### Next Work

- Tambah conflict metadata: `syncStatus`, `remoteUpdatedAt`, atau version.
- Buat full pull/push strategy selepas login.
- Pastikan default workspace id tidak conflict antara user.
- Tambah backend ownership test untuk user lain tidak boleh akses workspace/page.
- Buang destructive migration sebelum production.

## Milestone 2: AI Action Core

Goal: AI faham arahan Malay/English dan execute action secara konsisten.

### Sudah Ada

- Satu AI flow digunakan di home/page.
- Page context boleh attach secara hidden bila user chat dari page.
- Ada mode `Planning`, `Edit`, `Auto`.
- Backend ada action recovery test untuk beberapa arahan Malay.

### Belum Kukuh

- Action schema masih terlalu longgar.
- AI kadang balas JSON/text tapi action tidak execute.
- Recovery logic masih banyak dan ad hoc.
- Belum semua action diuji: block, property, row, column, date, formula, relation, rollup.

### Next Work

- Tetapkan satu `CYL_ACTION_SCHEMA` rasmi.
- Backend wajib pulangkan `{ reply, actions }`.
- Android hanya execute action yang valid.
- Tambah validation error yang jelas.
- Simpan executed action dalam chat metadata.
- Tambah regression test untuk arahan multi-step Malay.

## Milestone 3: Editor/Page Core

Goal: page editor stabil, senang maintain, dan tidak semua logic duduk dalam UI.

### Sudah Ada

- Block text, heading, todo, bullet, quote, divider, media, database table.
- Row page dan basic undo sudah ada.
- Table view: table/list/board/calendar/gallery/timeline/dashboard.

### Belum Kukuh

- `PageEditorRoute.kt` terlalu besar.
- `PageEditorViewModel.kt` terlalu besar.
- Mutation logic bercampur dengan UI state.
- Undo belum jelas cover semua AI/table action.

### Next Work

- Extract `PageMutationUseCase`.
- Extract `TableMutationUseCase`.
- Extract `AiActionExecutionUseCase`.
- Jadikan UI hanya render state dan dispatch event.
- Tambah unit test untuk mutation tanpa Compose UI.

## Milestone 4: Typed Table Core

Goal: table behave macam Notion/Excel, bukan semua cell sebagai text biasa.

### Sudah Ada

- Column type model: text, number, status, date, files, checkbox, formula, relation, rollup.
- Date picker UI sudah ada.
- Header property bottom sheet sudah ada.

### Belum Kukuh

- Cell value masih `String`.
- Date/reminder belum end-to-end kuat.
- Formula/relation/rollup belum production-grade.
- AI kadang salah guna table/task intent.

### Next Work

- Buat typed cell model.
- Normalize date/time storage.
- Sambungkan date column kepada reminder scheduler.
- Validate number/status/checkbox input.
- Tambah formula evaluator minimum.
- Tambah relation/rollup resolver minimum.

## Milestone 5: Search And Mention Core

Goal: search jadi navigasi utama untuk page, block, table, row, property, dan AI context.

### Sudah Ada

- Search page sudah ada.
- Mention page di chat sudah ada.
- Chat boleh link ke page.

### Belum Kukuh

- Search belum indexed.
- Belum search semua block/table row/property dengan kuat.
- Mention resolver masih bergantung UI/client context.

### Next Work

- Buat `SearchRepository`.
- Index local page/block/table/row/property.
- Result boleh navigate dan highlight target.
- AI gunakan search index yang sama.
- Kemudian tambah backend full-text search.

## Milestone 6: Chat History Core

Goal: chat history bukan sekadar message list, tapi audit trail untuk AI work.

### Sudah Ada

- Chat session local sudah ada.
- Empty session handling sudah diperbaiki.
- Page links boleh disimpan dalam message.

### Belum Kukuh

- Chat belum sync backend.
- Belum simpan model/mode/action metadata lengkap.
- Belum ada action audit untuk undo/debug.

### Next Work

- Simpan AI model, mode, action summary, execution result.
- Tambah action log per message.
- Hubungkan undo kepada action log.
- Tambah chat search.

## Milestone 7: Media/File Core

Goal: file/media boleh upload, preview, sync, delete, dan restore dengan betul.

### Sudah Ada

- Basic media/file block sudah ada.

### Belum Kukuh

- Belum ada cloud upload.
- Belum ada backend file metadata.
- Belum ada file size/type validation.
- Belum ada delete lifecycle.

### Next Work

- Pilih storage provider.
- Tambah backend file metadata table.
- Tambah upload/download API.
- Tambah Android file picker/upload progress.
- Tambah preview dan delete behavior.

## Milestone 8: Backend/API Production Core

Goal: backend selamat dan predictable untuk mobile app.

### Sudah Ada

- Auth routes.
- AI routes.
- Health route.
- Docker/Render setup.
- Flyway migration.

### Belum Kukuh

- Belum ada `/api/v1`.
- Error response belum standard semua route.
- Belum ada rate limit.
- CORS masih `anyHost()`.
- Belum ada OpenAPI/contract docs.

### Next Work

- Tambah API versioning.
- Standardkan error envelope.
- Tambah rate limit auth/AI.
- Ketatkan CORS untuk production.
- Tambah API docs.

## Milestone 9: Testing And Regression Core

Goal: bug AI/editor/database tidak berulang setiap kali tambah feature.

### Sudah Ada

- Backend AI action recovery tests.
- Auth basic tests.
- Health/status tests.

### Belum Kukuh

- Android unit/instrumented tests belum cukup.
- Belum ada sync tests.
- Belum ada editor mutation tests.
- Belum ada CI required checks.

### Next Work

- Test table mutation tanpa UI.
- Test AI action executor.
- Test Room migration.
- Test backend page sync API.
- Tambah CI untuk backend test + Android compile.

## Recommended Order

1. Data Sync Core.
2. AI Action Core.
3. Editor/Page Core.
4. Typed Table Core.
5. Search And Mention Core.
6. Chat History Core.
7. Media/File Core.
8. Backend/API Production Core.
9. Testing And Regression Core.
