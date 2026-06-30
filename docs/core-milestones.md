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
- Backend Postgres ada `client_id` untuk workspace supaya `local-default-workspace` tidak conflict antara user.
- Sync ownership test sudah cover user lain tidak boleh baca page/workspace user pertama.
- Android Room destructive migration fallback sudah dibuang dan migration path `1 -> 6` sudah lengkap.
- Android Room workspace/page ada sync metadata: `syncStatus`, `remoteUpdatedAt`, `lastSyncedAt`.
- Login/register trigger full best-effort sync: push local pending/legacy data, pull remote workspace/page, kemudian merge.
- Workspace/page repository sekarang guna satu `SessionSyncCoordinator`, bukan sync code berulang di setiap repository.
- Page editor sudah tunjuk sync conflict dan beri pilihan `Keep mine` atau `Use server`.
- Android ada instrumentation migration test untuk Room `4 -> 5`.
- Sync selepas login sekarang pull remote dahulu, merge sebagai authoritative, kemudian push local `PendingPush` yang bukan conflict.
- Backend ada Postgres integration test gated by `CYL_TEST_DATABASE_URL` untuk verify `client_id` workspace per user dan page ownership isolation.
- Backend ada Flyway migration `V4__page_content_projection.sql` untuk block, property, table, column, row, dan cell projection.
- Android Room sudah naik ke version 7: version 6 tambah normalized page content tables, version 7 tambah chat action metadata.
- Android ada `PageContentDao` dan projection mapper yang pecahkan `PageBlockDocument` kepada block/property/table/row/cell entities.
- Page save/sync sekarang tulis projection table di sebelah `pages.content`, termasuk fallback untuk legacy plain text content.
- Android ada unit test untuk page content projection mapper dan instrumentation migration test `5 -> 6`.
- `PageContentCodec` sudah dipindahkan ke domain layer supaya data/repository boleh encode/decode content tanpa import presentation UI.
- Projection table sekarang boleh rebuild balik `PageBlockDocument`, jadi mutation granular boleh regenerate JSON semasa transition.
- `PageRepository` sudah ada foundation direct mutation untuk block text, property value, dan table cell value melalui projection tables.
- AI action executor sudah mula guna direct mutation untuk property value, block text, dan table cell bila target id/nama cukup jelas.
- Backend ada granular `PATCH` endpoint untuk block text, property value, dan table cell; Android sync coordinator guna endpoint ini dan fallback ke full page push kalau perlu.
- Backend ada granular endpoint untuk create/delete/reorder block, property, table column, dan table row; Android `SyncApi` sudah expose contract ini.
- `PageRepository` sekarang ada granular method untuk add/delete/reorder block, property, table column, dan table row.
- Android `SessionSyncCoordinator` sudah sync create/delete/reorder granular ke endpoint baru dengan fallback full page push untuk payload kompleks.
- AI action executor sudah guna repository granular untuk add/delete/reorder block/property/table column/table row bila target jelas dan tiada pending document mutation dalam action batch.
- Manual page editor sudah guna repository granular untuk add/delete/move block, add/delete property, add/insert/delete table column, dan add/delete table row.
- Autosave manual untuk block text, property value, dan table cell sekarang cuba granular save bila diff hanya satu target; kalau diff bercampur atau rich text berubah, fallback ke full document save.
- Backend dan Android sync sekarang ada granular patch untuk table metadata: title, view, view config, sort, filter, dan group.
- Backend dan Android sync sekarang ada granular patch untuk column metadata: rename, type, date settings, formula, relation, dan rollup config.
- Backend dan Android sync sekarang ada granular block patch untuk rich text spans, media attachment metadata, dan todo checked state.
- Duplicate table column dengan copied cell values sudah guna granular create column payload `cellValues`.
- Row page content sudah ada granular row patch untuk blocks, dan manual row page block edit/add/delete guna path ini.
- Android ada AI executor regression test untuk pastikan add block, delete block, move table column, dan move table row guna repository granular, bukan full page save.
- Android migration test sekarang cover upgrade penuh Room `1 -> 7` supaya legacy workspace survive dan semua current tables wujud.
- GitHub Actions CI sudah run backend tests dengan PostgreSQL service dan Android unit/androidTest compile checks.

### Belum Kukuh

- Sync sekarang local-first best-effort dengan metadata conflict, belum full source-of-truth.
- Mutation bercampur atau edge case kompleks masih boleh fallback ke full document save.
- Page content JSON masih canonical; normalized projection sudah ada dan repository boleh mutate beberapa field direct, tapi editor/API belum fully pindah ke projection flow.
- Conflict resolver sudah ada untuk whole page, belum granular per block/table row.
- Default workspace id masih legacy local id di Android, tapi backend sekarang map sebagai client id per user.

### Next Work

- Seterusnya masuk Milestone 2: AI Action Core.

## Milestone 2: AI Action Core

Goal: AI faham arahan Malay/English dan execute action secara konsisten.

### Sudah Ada

- Satu AI flow digunakan di home/page.
- Page context boleh attach secara hidden bila user chat dari page.
- Ada mode `Planning`, `Edit`, `Auto`.
- Backend ada action recovery test untuk beberapa arahan Malay.
- Backend `chat-actions` response sekarang bawa `CYL_ACTION_SCHEMA` metadata (`schemaName`, `schemaVersion`) bersama `reply`, `actions`, dan `validationIssues`.
- Backend validate action type sebelum hantar ke Android; unsupported/blank action tidak jadi executable action.
- Backend validate required field untuk action utama: block, property, table, column, row, cell, sort, filter, dan group.
- Validation issue sekarang ada `actionIndex`, `field`, `code`, dan `message`, jadi action yang ditolak boleh dikesan tanpa execute separuh jalan.
- Android DTO/domain sudah simpan schema metadata dan validation issues dari backend.
- Backend regression test cover unsupported model action dan route contract schema response.
- Backend regression test sudah cover arahan multi-step Malay: padam block + tambah row, buat table + tambah row, dan elak prompt penuh masuk sebagai row.
- Assistant chat message sekarang simpan action metadata: mode, schema, proposed actions, executed actions, execution messages, dan validation issues.
- Chat sheet boleh buka `Action details` pada mesej assistant untuk lihat proposed/applied/rejected/result tanpa memaparkan id teknikal dalam bubble utama.
- Android AI executor sekarang buat semantic validation sebelum execute: block/property/table/row/column target mesti wujud untuk update/delete/move/sort/filter/group/cell/row-page actions.
- Semantic validation issue dari executor disimpan dalam chat action metadata supaya rejected action boleh dilihat balik di `Action details`.
- Android AI executor regression test cover missing block/column/view-config target supaya action tidak mutate page bila target tidak sah.
- Android AI executor sekarang validate relation target table dan rollup target table/column sebelum simpan column config.
- Android AI executor sekarang validate formula reference `{Column}` supaya formula tidak rujuk column yang tidak wujud.
- Android AI executor sekarang validate Date cell untuk `UPDATE_TABLE_CELL` dan `ADD_TABLE_ROW`, serta reject `CREATE_REMINDER` tanpa tarikh/masa.
- Android AI executor regression test sekarang cover row page nested block missing target supaya delete/update dalam row tidak mutate bila block tidak wujud.

### Belum Kukuh

- Action schema sudah ada type/required-field validation dan semantic target validation asas, tapi semantic validation masih belum lengkap untuk semua edge case.
- AI kadang balas JSON/text tapi action tidak execute.
- Recovery logic masih banyak dan ad hoc.
- Belum semua action diuji: formula evaluator penuh dan media attachment payload.

### Next Work

- Luaskan semantic validation untuk config kompleks: media attachment payload dan formula evaluator penuh.
- Tambah regression test untuk lebih banyak action: media attachment payload dan formula edge cases.

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
