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
- Android repository sekarang local-first untuk refresh juga: screen baca Room dahulu, kemudian refresh/push pending berjalan melalui `BackgroundSyncQueue`.
- Login/register tidak lagi block pada full sync; token disimpan dahulu dan session sync berjalan di background.
- App startup sekarang trigger background session sync bila token masih ada, jadi pending local changes dari run sebelum ini akan cuba dihantar selepas restart.
- Permanent page delete sekarang ada `sync_tombstones` Room table supaya operasi delete yang gagal boleh retry selepas app restart.
- Permanent page delete sekarang idempotent di Android: remote `404` dianggap selesai dan tombstone dibersihkan.
- Android Room sudah naik ke version 8 untuk tombstone table dan schema export `8.json` sudah dijana.
- Android sekarang guna WorkManager (`CylSyncWorker`) sebagai persistent retry backup untuk pending sync bila app process dibunuh atau network cuma tersedia kemudian.
- `BackgroundSyncQueue` expose run state (`isSyncing`, last error, last completed) dan schedule WorkManager retry untuk local mutations.
- Home top bar ada sync status icon kecil dengan pending/conflict/error summary dan `Retry sync`.
- Sync status repository sudah gabungkan pending workspace/page/tombstone count, conflict count, dan queue run state sebagai `SyncOverview`.

### Belum Kukuh

- Sync sekarang local-first best-effort dengan metadata conflict, belum full source-of-truth.
- Mutation bercampur atau edge case kompleks masih boleh fallback ke full document save.
- Page content JSON masih canonical; normalized projection sudah ada dan repository boleh mutate beberapa field direct, tapi editor/API belum fully pindah ke projection flow.
- Conflict resolver sudah ada untuk whole page, belum granular per block/table row.
- Default workspace id masih legacy local id di Android, tapi backend sekarang map sebagai client id per user.
- WorkManager sekarang retry pending push/delete, tapi belum ada periodic pull/background refresh untuk remote changes yang dibuat dari device lain.
- Sync error state bergantung pada queue/worker; beberapa coordinator path masih swallow network failure dan hanya kelihatan sebagai pending count.

### Next Work

- Seterusnya masuk Milestone 2: AI Action Core. Untuk sync nanti, tambah periodic remote pull dan granular conflict resolver.

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
- AI action schema dan Android executor sekarang support media attachment payload (`mediaUri`, `mediaName`, `mediaMimeType`, `mediaSizeBytes`) untuk `MediaFile` block.
- Android AI executor regression test cover create media block dengan attachment payload dan reject `MediaFile` block tanpa `mediaUri`.
- Backend schema validation sekarang reject `UPDATE_FORMULA_COLUMN` tanpa `formula`, `value`, atau `content`.
- Android AI executor sekarang validate formula syntax asas: column reference `{Name}`, nombor, operator `+ - * /`, kurungan, dan reject self-reference.
- Formula update sekarang boleh guna payload `formula`, `value`, atau `content`, jadi action model yang letak formula dalam `value` masih berfungsi.
- Hidden mention context dari Android sekarang hantar page id bersama title, tetapi sanitizer tetap buang id teknikal jika model echo balik.
- Backend chat action planner sekarang boleh pilih deterministic prompt recovery berbanding model action bila prompt jelas bercanggah, contoh row request yang model jadikan create table sahaja.
- Backend legacy JSON recovery sekarang abaikan `null` dan internal row id supaya model reply seperti `{"page":"@Budget Tracker","action":"update","data":[...]}` jadi row action bersih.
- Android network JSON parser sekarang coerce `null` kepada default DTO value supaya AI/backend response yang longgar tidak terus crash client parse.
- Backend action result selector sekarang explicit dan unit-tested: prompt recovery boleh menang apabila model action cuma create table, salah isi `Task`, atau miss arahan multi-step.
- Backend action selection sudah dipisahkan ke `AiActionPlanner`, jadi keputusan pilih model result vs deterministic prompt recovery tidak lagi bercampur dalam `AiService`.
- Backend ada regression test khusus untuk `AiActionPlanner` supaya bug row request yang model jadikan create table atau letak arahan penuh dalam cell `Task` tidak berulang.
- Backend schema validation sudah dipisahkan ke `AiActionSchemaValidator`, jadi supported action/required-field rule boleh diuji tanpa instantiate AI provider service.
- Backend model reply/legacy JSON recovery sudah dipisahkan ke `AiModelActionNormalizer`, jadi `AiService` tidak lagi memegang parsing JSON model, legacy `{page, action, data}` recovery, dan sanitizer `null`/internal id.
- Backend ada regression test khusus untuk `AiActionSchemaValidator` bagi action type normalization, unsupported action, media block tanpa URI, dan formula column tanpa formula.
- Backend deterministic prompt recovery Malay/English sudah dipisahkan ke `AiPromptActionRecovery`, jadi `AiService` tidak lagi memegang parser arahan page/block/property/table row secara langsung.
- Backend ada regression test khusus untuk `AiPromptActionRecovery` bagi delete semua block dan expense row Malay supaya parser boleh direfactor tanpa kehilangan behavior.
- Android Home AI sekarang tidak lagi execute deterministic local action bila backend result kosong; `Edit` dan `Auto` hanya execute `actions` yang datang dari backend.
- Android masih ada guard kecil untuk skip unsafe qualitative table rename, dan guard itu direkod sebagai validation issue dalam `Action details`.
- Android AI action execution decision sudah diextract ke `AiActionExecutionPolicy`, jadi `HomeViewModel` tidak lagi pegang local fallback/recovery rule.
- Android regression test cover policy: planning tidak execute, backend kosong tidak invent action, backend action execute, vague rename ditolak, concrete rename dibenarkan.
- Android ada regression test untuk AI DTO JSON null coercion supaya field `title`, `targetTitle`, `cellValues`, dan `validationIssues` null tidak mematikan chat.

### Belum Kukuh

- Action schema sudah ada type/required-field validation dan semantic target validation asas, tapi semantic validation masih belum lengkap untuk semua edge case.
- AI text/JSON fallback, model JSON normalizer, schema validator, planner selector, dan prompt recovery sudah dipisahkan dari `AiService`.
- Android execution path sudah backend-only dan parser fallback lama sudah dibuang dari `HomeViewModel`, tapi `HomeViewModel` masih besar dan page AI flow belum diextract sebagai use-case penuh.
- Belum semua action diuji untuk kombinasi multi-step yang sangat panjang dan ambiguous.

### Next Work

- Tambah regression test untuk multi-step prompt yang panjang: create table + set formula + add rows + sort/filter dalam satu arahan.
- Extract page AI execution orchestration dari `HomeViewModel` ke use-case supaya chat/session, execution, dan metadata lebih mudah diuji end-to-end.

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
