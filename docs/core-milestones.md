# CYL Core Milestones

Dokumen ini ialah checklist sementara untuk susun kerja core ChangeYourLife (CYL).
Padam fail ini bila milestone utama sudah siap dan sudah dipindahkan ke dokumentasi kekal.

## Status Ringkas

- Done: asas Android, backend Ktor, auth, Room DB, AI chat/action, table editor, chat history, Render/Aiven setup.
- Not solid yet: table typed data, editor architecture, search index, media cloud, production safety, FCM/realtime push notifications.

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
- Backend ada Flyway migration `V5__ai_action_logs.sql` dan authenticated `/api/v1/ai-action-logs` untuk sync audit log AI per user/workspace.
- Android Room sudah naik ke version 11: `ai_action_logs` ada `updatedAt`, `syncStatus`, `remoteUpdatedAt`, dan `lastSyncedAt`.
- Android `SessionSyncCoordinator` sekarang pull/push `ai_action_logs` bersama workspace/page sync, termasuk retry pending log dan merge `undoState`.
- Sync status repository sekarang kira pending/conflict `ai_action_logs`, jadi audit log AI tidak senyap bila belum sync.
- Backend route test cover audit log upsert/list, `updatedAfter`, dan user isolation.
- Backend ada Flyway migration `V6__chat_history_sync.sql` dan authenticated `/api/v1/chat-sessions` + `/api/v1/chat-messages` untuk sync conversation history per user.
- Backend route test cover chat session/message upsert, list, `updatedAfter`, soft delete, dan user isolation.
- Android Room sudah naik ke version 12: `chat_sessions` dan `chat_messages` ada `syncStatus`, `remoteUpdatedAt`, `lastSyncedAt`, serta `chat_messages.updatedAt`.
- Android `SessionSyncCoordinator` sekarang pull/push chat sessions/messages untuk home chat scope bersama workspace/page/action-log sync.
- Chat history repository sekarang mark session/message sebagai `PendingPush` dan enqueue background sync setiap kali chat dibuat atau mesej ditambah.
- Sync status repository sekarang kira pending/conflict chat session/message.
- Android migration test cover `11 -> 12` untuk chat sync metadata.
- Android WorkManager sekarang ada periodic session sync setiap 30 minit ketika network connected untuk pull remote workspace/page/chat/action-log changes dan push pending local changes.
- `CylSyncWorker` sekarang ada dua mode: `pendingPush` untuk retry local mutation dan `sessionSync` untuk full pull+push background refresh.
- App foreground sekarang ada refresh loop setiap 2 minit: bila user buka app, CYL buat full session sync lebih cepat daripada periodic worker; bila app background, loop dihentikan.
- Page sync sekarang ada granular auto-merge conflict resolver untuk perubahan page content yang tidak bertindih: contoh device A ubah block, device B ubah property/row/cell lain, app merge dan push semula tanpa paksa user pilih whole-page conflict.
- Granular resolver masih jatuh ke conflict manual bila field/page item sama diubah di dua tempat, atau bila remote delete bertembung dengan local update.

### Belum Kukuh

- Sync sekarang local-first best-effort dengan metadata conflict, belum full source-of-truth.
- Mutation bercampur atau edge case kompleks masih boleh fallback ke full document save.
- Page content JSON masih canonical; normalized projection sudah ada dan repository boleh mutate beberapa field direct, tapi editor/API belum fully pindah ke projection flow.
- Conflict resolver sudah ada untuk whole page, belum granular per block/table row.
- Default workspace id masih legacy local id di Android, tapi backend sekarang map sebagai client id per user.
- WorkManager sekarang retry pending push/delete, periodic pull background refresh, dan foreground refresh loop; belum ada FCM/realtime push notification dari server.
- Sync error state bergantung pada queue/worker; beberapa coordinator path masih swallow network failure dan hanya kelihatan sebagai pending count.
- Chat session/message sudah sync backend, periodic pull, dan foreground refresh loop, tetapi belum ada push notification sebenar ketika app tidak aktif.

### Next Work

- Untuk sync nanti, tambah FCM/realtime push-triggered refresh jika mahu perubahan dari device lain masuk segera semasa app tidak aktif.

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
- Chat history repository sekarang ada regression test untuk pastikan action metadata AI, page links, proposed/executed actions, validation issues, dan execution messages survive selepas repository/app recreation.
- Chat history repository juga ignore metadata JSON corrupt tanpa menjatuhkan mesej chat.
- Chat metadata domain-to-UI mapping sudah diextract ke `AiChatMessageMapper` dan ada regression test supaya action metadata yang persist dipaparkan semula dalam `AiChatMessage` selepas reload.
- Chat action metadata sekarang simpan `auditId`, `requestMessageId`, `executedAt`, `provider`, dan `model` dengan regression test serta backward-compatible default untuk metadata lama.
- Proposed/applied action metadata sekarang simpan `actionIndex`, termasuk regression test untuk multi-step prompt bila action awal ditolak tetapi action selepasnya tetap applied dengan index asal.
- Android Room sudah naik ke version 9 dengan `ai_action_logs` table untuk audit trail AI action; schema export dan migration `8 -> 9` sudah ditambah.
- `AiActionLogRepository` sudah ada untuk simpan/observe action log berdasarkan `auditId`, session, request message, response message, provider/model, actions, validation issue, result, dan undo state.
- Home AI flow sekarang tulis action log selepas assistant message disimpan, menggunakan `auditId` yang sama dengan chat metadata.
- Android Room sudah naik ke version 10 dan `ai_action_logs` sekarang simpan `undoCommandsJson` untuk link tindakan AI kepada command undo yang dihasilkan editor pipeline.
- `AiPageActionExecutor` sekarang mengumpul payload undo command untuk fallback mutation block/table yang melalui `ApplyEditorCommandUseCase`, termasuk snapshot `ReplaceTable` untuk row/column/cell/table metadata changes.
- `AiActionLogFactory` sekarang bezakan `PendingCommandLink` dengan `Available`: action log hanya dianggap undo-ready bila executor benar-benar pulangkan undo command.
- Android regression test cover action log `undoCommandsJson`, factory state `Available`, repository round-trip, Room migration `9 -> 10`, dan AI executor table row mutation yang menghasilkan undo command.
- `ApplyAiActionUndoUseCase` sudah boleh apply payload `undoCommandsJson` secara reverse kepada page content dan mark action log sebagai `Applied`.
- Chat `Action details` sekarang ada `Undo AI action` untuk mesej assistant yang ada applied action, audit id, dan page link.
- Home AI dan Page AI guna callback undo yang sama melalui shared `AiChatSheet`, jadi undo flow tidak duplicate antara home/page.
- Chat message UI sekarang combine `chat_messages` dengan `ai_action_logs`, jadi `undoState` dipaparkan secara reactive dan button undo hilang selepas action sudah `Applied`.
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
- Android chat action orchestration sudah diextract ke `AiChatActionOrchestrator`: ia urus proposed action, Planning/Edit/Auto mode, markdown table recovery, execution result, page links, dan action metadata.
- Android regression test sekarang cover flow mode: `Planning` rekod proposed action tanpa execute, `Edit` execute backend action, dan `Auto` recover markdown table lalu execute action.
- Home AI sekarang boleh execute global `CREATE_PAGE` action terus dari home chat dan simpan page link dalam jawapan AI.
- Backend `chat-actions` sekarang AI-first: live model diberi CYL action schema/context dan cuba hasilkan action JSON dahulu; prompt recovery hanya fallback bila model kosong/invalid/markdown.
- Backend prompt recovery masih wujud untuk direct unit recovery, tetapi `chat-actions` tidak lagi execute prompt-only creative creation seperti `CREATE_PAGE`, `CREATE_DATABASE`, atau `CREATE_TABLE` bila model tidak beri action valid.
- Backend prompt fallback masih boleh baiki action kecil/targeted seperti row/delete/update supaya AI yang tersalah target tidak terus merosakkan data.
- Mention/context page tetap menang untuk request table; prompt seperti `buat jadual ... dalam @Page` masih jadi page-scoped `CREATE_DATABASE`, bukan page baru.
- Android Home AI sekarang boleh treat global `CREATE_DATABASE`/`CREATE_TABLE` tanpa target sebagai create page berisi table, supaya response model lama tidak terus gagal di Home.
- Android Home AI ada safety net untuk convert markdown table daripada model kepada CYL action bila backend pulangkan `actions` kosong untuk prompt create table yang jelas.
- Android ada regression test untuk AI DTO JSON null coercion supaya field `title`, `targetTitle`, `cellValues`, dan `validationIssues` null tidak mematikan chat.
- Backend regression test lock behavior AI-first supaya prompt fallback tidak boleh overwrite model action yang valid, dan JSON chat `actions=[]` tidak dipaparkan mentah kepada user.
- Backend/Android sekarang sync `ai_action_logs`, jadi audit trail AI dan `undoState` boleh survive reinstall/multi-device.

### Belum Kukuh

- Action schema sudah ada type/required-field validation dan semantic target validation asas, tapi semantic validation masih belum lengkap untuk semua edge case.
- AI text/JSON fallback, model JSON normalizer, schema validator, planner selector, dan prompt recovery sudah dipisahkan dari `AiService`.
- Android execution path sudah backend-only dan parser fallback lama sudah dibuang dari `HomeViewModel`; action execution orchestration sudah dipindahkan ke `AiActionExecutionUseCase`.
- Belum semua action diuji untuk kombinasi multi-step yang sangat panjang dan ambiguous.

### Next Work

- Tambah regression test untuk multi-step prompt yang panjang: create table + set formula + add rows + sort/filter dalam satu arahan.

## Milestone 3: Editor/Page Core

Goal: page editor stabil, senang maintain, dan tidak semua logic duduk dalam UI.

### Sudah Ada

- Block text, heading, todo, bullet, quote, divider, media, database table.
- Row page dan basic undo sudah ada.
- Table view: table/list/board/calendar/gallery/timeline/dashboard.
- Rich text span engine sudah dipisahkan ke domain layer (`RichTextSpanEngine`) dengan unit test untuk normalize, toggle style, dan text-change adjustment.
- Rich text Compose editor/toolbar sudah dipisahkan dari `PageEditorRoute` ke `CylRichTextBlockEditor`, jadi route tidak lagi simpan implementation rich text editor.
- `EditorCommand` + `EditorCommandExecutor` sudah ditambah di domain layer untuk update text, tukar block type, toggle todo, insert block, dan delete block.
- `EditorCommand` sekarang support move block dengan unit test untuk undo, nested sibling, dan boundary no-op.
- `EditorCommand` sekarang support replace media attachments dan replace table dengan undo.
- `PageEditorViewModel` sudah mula guna command executor untuk rich text/text update, todo toggle, block type change, add block, add child block, delete block, move block, media attachments, table metadata, table columns, table rows, table cells, dan row-page blocks.
- `ApplyEditorCommandUseCase` sudah ditambah sebagai single command pipeline yang boleh dipakai manual editor dan nanti AI edit flow.
- `PageEditorViewModel` tidak lagi panggil `EditorCommandExecutor` terus; semua command manual masuk melalui use-case.
- `AiPageActionExecutor` sekarang guna `ApplyEditorCommandUseCase` untuk fallback block/table mutation seperti insert/delete/update block, create table, dan replace table selepas semantic target validation lulus.
- `TableMutationUseCase` sudah ditambah untuk table title/view/sort/filter/group, column metadata/type/date/formula/relation/rollup, cell coercion, add/duplicate/delete column, add/delete row, dan row-page block mutation.
- `PageEditorViewModel` table editor sekarang mula delegate mutation utama kepada `TableMutationUseCase`, sementara ViewModel kekal urus queue save/granular repository.
- `PageMutationUseCase` sudah ditambah untuk block text/rich text, block type, todo, media attachment, add/delete/move block, dan page property mutation.
- `PageMutationUseCase` sekarang support replace satu block dengan beberapa block hasil paste, dengan block pertama kekal guna id asal supaya focus/save path tidak pecah.
- `PageEditorViewModel` block/property editor sekarang delegate command construction kepada `PageMutationUseCase`, sementara ViewModel kekal urus pending state, undo history, dan repository save.
- `AiActionExecutionUseCase` sudah ditambah untuk Home AI action execution: split home-scoped action vs page-scoped action, create page/table, call `AiPageActionExecutor`, persist updated page, collect page links, dan return validation issues.
- `HomeViewModel` tidak lagi inject/call `AiPageActionExecutor` terus untuk chat action execution; ia panggil `AiActionExecutionUseCase`.
- `PageEditorViewModel` tidak lagi ada legacy page AI executor, direct `AiPageActionExecutor`, direct `AiRepository`, atau tool lama `summarize/extract tasks/generate plan`.
- Helper AI lama dalam `PageEditorViewModel` sudah dibuang; page AI sekarang ikut shared chat/action flow yang sama dengan Home.
- `EditorCommandHistory` sudah ditambah untuk command-based undo/redo stack; `PageEditorViewModel` sekarang guna command undo untuk block-level, table metadata/column/row/cell, page property, dan row-page block command mutation, dengan snapshot fallback untuk path lama.
- `EditorCommand` sekarang support page property insert/replace/delete dengan undo yang restore posisi asal.
- Slash command parser sudah ada dengan unit test; rich text editor sekarang boleh guna `/text`, `/heading`, `/todo`, `/bullet`, `/quote`, `/divider`, `/media`, dan `/table` untuk tukar block type.
- Table row page blocks juga sudah ada type-change path supaya slash command di dalam row page boleh mengubah row block, bukan hanya block biasa.
- `PageTextSpan` sekarang support metadata rich text lebih luas: `code`, `linkUrl`, `color`, `highlight`, `mentionPageId`, dan `mentionLabel` dengan default backward-compatible.
- `RichTextSpanEngine` sekarang boleh normalize/merge metadata span, apply link/color/highlight/mention, dan toggle `Code`; ada regression test untuk metadata span, mention span, dan code format.
- `RichTextController` sudah ditambah untuk urus `TextFieldValue`, selection, active format, format toggle, link apply, mention replacement, dan text-change span adjustment di luar `PageEditorRoute`.
- `RichTextMentionParser` dan `RichTextPasteParser` sudah ada; paste parser boleh pecah markdown ringan kepada block text/heading/bullet/todo/quote serta inline `**bold**` dan `[link](url)`.
- Blank text block sekarang boleh menerima multi-line paste dan auto-create beberapa page block melalui editor mutation path, bukan simpan semua sebagai satu text block.
- Row-page blank text block dalam table sekarang juga boleh menerima multi-line paste dan auto-create beberapa row-content block, dengan table undo path yang restore state asal.
- Multi-line paste dalam non-empty selection sekarang preserve text/spans sebelum dan selepas selection, kemudian sisip block tambahan dari markdown ringan.
- Table cell text/number sengaja flatten multi-line paste kepada satu line supaya grid tidak pecah; media caption sengaja tidak auto-create block baru.
- Block editor sekarang ada mention picker `@Page` yang guna senarai page sebenar dan simpan mention sebagai span metadata, bukan teks kosong sahaja.
- Rich text toolbar sekarang support `B/I/U/S`, `Code`, link, text color swatch, dan highlight swatch dengan canonical spans yang tidak hilang bila user sambung menaip.
- Page editor utama sekarang render rich text toolbar di bottom keyboard area melalui shared toolbar state, bukan toolbar duplicate dalam setiap block page utama.
- Row-page dan media caption sekarang guna toolbar host yang sama, manakala table cell kekal guna typed cell editor tanpa rich toolbar.
- Prefix Notion-like asas sudah ada: `- ` jadi bullet, `[] ` / `[ ] ` jadi todo, `# ` jadi heading, dan `> ` jadi quote.
- AI action schema/backend prompt/Android executor sekarang support `FORMAT_BLOCK_TEXT` untuk format block text kepada bold/italic/underline/strikethrough/code/link/color/highlight, dengan undo command dan regression test.

### Belum Kukuh

- `PageEditorRoute.kt` terlalu besar.
- `PageEditorViewModel.kt` masih besar, tapi legacy AI fallback sudah dibuang dan tinggal fokus editor/manual mutation.
- Mutation logic bercampur dengan UI state.
- Undo command sudah cover editor mutation utama dan basic AI action undo sudah tersambung dari chat action details dengan reactive `undoState`.
- Mention trigger dalam block editor dan row-page block sudah ada, tapi belum jadi command palette/editor-level penuh untuk semua context seperti table cell dan reusable picker global.
- Slash command masih basic; belum ada command untuk create linked page, insert block below/above, atau command yang bergantung kepada context table/property.
- Paste policy utama sudah settle untuk page block, row-page block, table cell, dan media caption; import HTML/clipboard rich content sebenar belum dibuat.

### Next Work

- Terus kecilkan baki table/helper manual dalam `PageEditorViewModel` dengan memindahkan lookup, queue save, dan granular save orchestration ke use-case kecil.
- Jadikan UI hanya render state dan dispatch event.
- Tambah unit test untuk mutation tanpa Compose UI.
- Extract slash command UI kepada editor command palette yang boleh juga dipakai untuk mention picker.
- Sambungkan slash command dan mention picker kepada editor event, bukan ad hoc UI state.
- Tambah import/export rich clipboard sebenar kemudian jika perlu, tanpa ubah canonical CYL block/span format.

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
- Action metadata AI sudah disimpan sebagai JSON di Room dan ada repository regression test untuk roundtrip selepas app/repository recreation.
- Persisted action metadata sudah ada mapper domain-ke-UI dengan regression test, jadi `Action details` boleh dibina semula selepas session reload.
- Setiap action metadata baru sudah ada audit id, user request message id, execution timestamp, provider, dan model sebenar.
- `ai_action_logs` local table dan repository sudah ada sebagai audit trail per message/action.
- Action log sekarang simpan `undoCommandsJson` dan `undoState = Available` bila AI mutation melalui command pipeline yang menghasilkan undo command.
- Chat action details boleh trigger basic `Undo AI action`; use-case apply payload undo ke page dan mark log `Applied`.
- Chat UI model sekarang observe `undoState`, jadi action yang sudah `Applied` tidak lagi tunjuk button undo.
- Action log sekarang sync ke backend melalui `/api/v1/ai-action-logs`, termasuk `undoCommandsJson`, `undoState`, provider/model, schema metadata, dan execution messages.
- Chat session dan chat message sekarang sync ke backend melalui `/api/v1/chat-sessions` dan `/api/v1/chat-messages`, jadi conversation history boleh survive reinstall bila backend token/database sama.

### Belum Kukuh

- Chat sudah ada backend sync untuk session/message.
- Model/mode/action metadata dalam `chat_messages` ikut sync sebagai JSON, dan action log juga sync backend.
- Undo AI action sudah boleh apply untuk payload command yang valid, UI observe `undoState`, state action log sync, dan conversation history boleh dipulihkan dari backend.
- Belum ada dedicated chat full-text search dan belum ada delete lifecycle message-level; clear chat sekarang bergantung pada soft-delete session.

### Next Work

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
