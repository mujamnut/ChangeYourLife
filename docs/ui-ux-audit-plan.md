# CYL UI/UX Audit Plan

Audit date: 2026-07-02

This document is a focused UI/UX audit before the next large refactor. The goal is to make ChangeYourLife feel clean, quiet, and Notion-like while keeping the current editor, table, AI, and sync foundations.

## Verdict

The app is functionally stronger than before, but the UI still feels assembled feature-by-feature. The main issue is not color or one component. The main issue is hierarchy: too many surfaces compete at once, and many actions are visible before the user needs them.

Before a major code refactor, CYL needs a product-level UI direction:

- Page editor should feel like a clean document.
- Database table should feel like a spreadsheet/database, not a form inside a document.
- AI should feel like one assistant surface, not a separate tool attached everywhere.
- Search, create, and AI entry points should stay predictable across home and page.
- Secondary actions should move into contextual menus/sheets.

## Skill-Based Re-Audit

Source guidance:

- `frontend-design-direction`: choose a specific product direction before polishing UI.
- `liquid-glass-design`: use glass as a high-value chrome/material treatment, not as decoration everywhere.

Important session note:

- These two skills are installed locally, but Codex must be restarted before they appear in the active skill list automatically. This audit reads their installed `SKILL.md` files directly.

### Product Direction

Purpose:

- CYL is a mobile-first personal workspace for life planning, notes, databases, tasks, budgeting, habits, and AI-assisted editing.
- The interface must support repeated daily use, fast scanning, and confident editing.

Audience:

- A user who opens the app many times per day to capture, search, edit, and ask AI to operate on pages/tables.
- This user needs low friction, not marketing-style visual drama.

Tone:

- Calm, dense, utilitarian, warm, and quietly premium.
- It should feel closer to a working notebook/database than a dashboard or landing page.

Memorable detail:

- One persistent bottom command bar: search, AI, create.
- This can become CYL's signature "workspace command surface" across home and page.

Constraints:

- Android Jetpack Compose, Material 3, existing CYL theme tokens.
- Not iOS SwiftUI, so Liquid Glass should be adapted as a direction, not copied as an API.
- Avoid feature churn while `PageEditorRoute.kt` and `HomeRoute.kt` are still large.

### Liquid Glass Decision For CYL

Do not apply full glass styling to the whole app.

Use glass-inspired chrome only for:

- bottom command bar
- top tab/profile chrome
- AI chat header/composer
- modal bottom sheet headers
- floating contextual toolbars

Do not use glass on:

- text editor blocks
- table cells
- table headers
- search result rows
- long content lists
- page body backgrounds

Reason:

- CYL is a productivity/editor app. Text and table content must stay readable, stable, and plain.
- Liquid/glass effects are best used to separate floating controls from content, not to decorate content itself.

### Updated Visual Principles

1. Content is matte

Editor text, table cells, search results, and row properties should use solid surfaces, dividers, and typography. No glass treatment.

2. Chrome can be glass-like

Persistent controls can use elevated translucent-looking surfaces, soft borders, and restrained shadow. This gives premium polish without reducing readability.

3. One visual hierarchy

Home, page, AI, and search should share the same command/chrome language. The user should not feel like each screen was designed separately.

4. Tables stay industrial

Tables should be dense, rectilinear, and stable. Use grid rhythm, column type icons, and clear hit targets. Avoid rounded card treatment inside the grid.

5. AI is a layer, not a destination

AI should feel like a command layer that can attach to any page or table. It should not introduce a second competing UI system.

6. Sheets are decision surfaces

Sheets should have one clear job: create, edit, pick, confirm, or inspect. If a sheet starts becoming a full screen with multiple unrelated actions, split or simplify it.

### Re-Audit Findings

Home:

- Direction is mostly right after moving profile actions to a bottom sheet.
- The top tab chrome can be refined further into one quieter shared component, not manually styled per screen.
- Recent cards still risk making home feel like a dashboard. Page rows should remain the primary object.

Page editor:

- The current direction is correct: text-like blocks must stay plain.
- The block render path now separates plain text rows from structured containers, which reduces the chance of card-like behavior returning accidentally.
- Next UI polish should continue by tightening database/table and row-page interaction details rather than adding more persistent controls.

Table:

- The grid is moving in the right direction, but it still needs a dedicated table token system: row height, header height, border color, selected/hover/active state, property icon size.
- Table should not inherit generic card/sheet spacing too often.
- A true table search control is now present as transient table-local UI state.
- Row reorder now supports long-press drag with stable row keys and visible drag feedback; keep manual move actions in the row sheet as backup.
- Newly created databases now start with only the `Name` property and no fake blank row; the empty grid uses a compact starter state for first item/property actions.
- Starter empty state now keeps property creation only at the right-side add-column control. The new-property sheet accepts optional names and falls back to the selected type label.
- Edit-property sheet now uses a compact property header, inline name editor, type chip, custom 56dp action rows, and a disabled AI Autofill row until that feature is wired.

Row page:

- The row page is closer to a database item now.
- Row block actions now use the same focused toolbar pattern as normal page blocks.
- Remaining issue: the property list can still be tightened further with stronger value alignment and better empty states.

AI chat:

- Header direction is now stronger: mode/model is the main surface.
- Composer still looks more like a form field than CYL's command surface.
- Action details are still technical when expanded; they should be readable as user-facing results first, debug details second.

Search:

- Home search now fits the list-first direction.
- Next step is result quality and target highlighting, not more visual decoration.

Theme/chrome:

- CYL's current palette is restrained and appropriate. It is not a one-note purple/blue generated UI.
- The warm green primary works, but secondary/tertiary colors should be used sparingly for status and metadata, not large surfaces.
- `CylBottomCommandBar` should become the central place to implement any glass-inspired chrome treatment.

### Concrete Design-System Next Steps

1. Add CYL chrome primitives

- `CylFloatingChromeSurface`
- `CylChromePill`
- `CylSheetHeader`
- `CylInlineStatusChip`

These should use existing Material 3 colors first:

- `surfaceContainer`
- `surfaceContainerHigh`
- subtle border via alpha
- optional soft shadow only for floating command surfaces

2. Add table primitives

- `TableGridTokens`
- `TableHeaderSurface`
- `TableCellSurface`
- `TableActionCell`
- `TablePropertyIcon`

These should avoid glass and keep stable dimensions.

3. Add AI composer primitive

- `CylAiComposer`

It should visually match the bottom command bar more than `OutlinedTextField`.

4. Extract row page sections

- `TableRowPageHeader`
- `TableRowPropertyList`
- `TableRowContentEditor`
- `TableRowBlockActionHost`

5. Visual QA checklist before each UI merge

- Does this surface compete with the bottom command bar?
- Is content plain and readable?
- Is glass/chrome reserved for controls only?
- Is the primary workflow visible without explanatory text?
- Are all icon controls at least 40-44dp touch targets?
- Does the table still scan like a grid?
- Does AI feel like one assistant across home and page?

## Current Surface Audit

### Home

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/home/HomeRoute.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/components/CylChromeBars.kt`

Findings:

- The top profile button, tab pill, and sync button are visually heavy as a row of large rounded surfaces.
- `HomeHeader` uses three competing controls in one horizontal line: profile, tab selector, sync status.
- `HomeBottomBar` is useful and should remain the stable entry point for search, AI, and create.
- Recent cards and page rows still create mixed visual language: cards above, rows below.
- Trash is correctly separate now, but it still uses a generic page-like screen structure.

Decision:

- Keep the bottom command bar.
- Simplify top header into: profile, three-tab switcher, subtle sync indicator.
- Reduce visual weight of sync unless there is pending/error/conflict.
- Keep page rows as the main home object. Recents should be quieter or optional.
- Do not keep sync as a standalone Home header button. Move sync detail into the profile bottom sheet; only surface sync urgently if there is an error/conflict in a later pass.
- Profile actions should open from a bottom sheet on mobile, not a small dropdown menu.
- Section-level create actions should use an icon-only `+` with a 44dp hit area. For example, `Private` should not show text `New`.

### Page Editor

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorRoute.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/RichTextBlockEditor.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorBlocks.kt`

Findings:

- Page blocks now render through `PlainTextBlockRow` or `StructuredBlockContainer` instead of one card-like wrapper.
- The fixed 30dp block handle has been removed from page blocks.
- Top bar no longer shows permanent debug-like block count/status text.
- Bottom bar remains the main command surface for search, AI, create, and focused block tools.
- Search highlight and structured-block active state are handled without adding visible handles to every block.

Decision:

- Text-like blocks should not be rendered through `Card`.
- Block actions should live in the keyboard/focused toolbar, not in a visible handle beside every block.
- Page top bar should be quieter: back, title only if needed, sync icon/status hidden unless not clean.
- Page top bar can show a small sync/status icon on the right side of the header. Tapping it opens sync detail; text like block count should not be permanently visible.
- Bottom command bar remains the stable search/AI/create entry point.
- Rich text toolbar stays above keyboard.

### Table

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorRoute.kt`

Relevant components:

- `DatabaseTableBlockEditor`
- `TableToolbar`
- `TableViewSelector`
- `TableGridEditor`
- `TableHeaderRow`
- `TableColumnEditSheet`
- `NewTableColumnSheet`

Findings:

- Table UI has many features, but hierarchy is not clean yet.
- `TableToolbar` has title/view selector, active controls, sort/filter/group, and config controls close together.
- `TableViewSelector` is functionally close to the requested direction, but it is visually thick at 48dp and shows title plus view in a stacked label.
- `TableHeaderCell` uses large body text and dropdown icon in every header, making headers feel noisy.
- Add column is correct as the last header cell, but it needs stronger spreadsheet visual alignment.
- Property creation sheet is close to Notion style, but the empty-name warning adds noise. The better interaction is disabled type rows until name exists, with subtle input focus.
- Row page sheet is moving away from a form layout: top buttons and permanent row block delete icons have been removed, but property alignment still needs a later pass.

Decision:

- Treat table as its own dense surface.
- Make table toolbar a single compact row: view/table label, search icon, filter icon, sort icon, add dropdown.
- Move active sort/filter/group summary into a compact chip row only when active.
- Header tap opens property sheet. Header should show icon + name only; dropdown indicator can be subtle or hidden.
- Row page sheet should look like a page: title, properties, then content. Remove permanent "Row" and "Property" buttons from the top; move them into contextual plus/menu.

### Database Property Deep Audit

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/domain/model/PageBlock.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/domain/usecase/TableMutationUseCase.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorTableBlock.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorTableRowProperties.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorViewModel.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/data/repository/PageRepositoryImpl.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/data/sync/SessionSyncCoordinator.kt`
- `backend/src/main/kotlin/com/changeyourlife/cyl/backend/routes/ContentRoutes.kt`

Scope:

- In CYL, Notion-style database properties are `PageTableColumn`.
- Legacy/page-level properties are `PageProperty`. These should not drive the database UI.
- Row values are still stored in two layers:
  - `PageTableRow.cells: Map<String, String>` for display/backward compatibility.
  - `PageTableRow.cellValues: Map<String, PageTableCellValue>` for typed data.

What is already solid:

- Add property sheet exists and the property name is optional. If user selects a type without typing a name, the selected type label becomes the default name.
- Rename, change type, date settings, formula config, relation target, rollup config, duplicate, insert, delete, sort, filter, and group are wired through `PageEditorViewModel` into `TableMutationUseCase`.
- Column changes sync through `PageRepositoryImpl.updateTableColumn`, `SessionSyncCoordinator.pushPageTableColumnPatch`, and backend `ContentRoutes`.
- Type changes coerce existing cell values and update `cellValues`.
- Deleting a column removes the cell values, clears sort/filter/group references, and removes rollup references to that column.
- Row sheet property labels now open the same edit-property sheet as table headers.
- Done: `PageTableColumnConfig` now exists as the canonical per-property config container.
- Done: Status properties now keep their options in column config instead of relying only on a hardcoded UI list.
- Done: Status options can be edited from the property sheet and sync through the granular table-column create/patch API.
- Done: unsupported property sheet actions like hidden AI Autofill, Hide, and Unwrap have been removed from the main sheet until they have real behavior.
- Done: table and row-sheet property values now read from typed `PageTableRow.cellValues` first, with `cells` as backward-compatible fallback.
- Done: Files/media typed values now populate `PageTableCellValue.files` from stored attachment JSON.
- Done: delete-property now asks for confirmation when the property contains values, and delete is disabled for the last remaining property.
- Done: formula configuration now validates `{Property}` references and blocks save when a referenced property does not exist.
- Done: Select and Multi-select are now first-class `PageTableColumnType` values instead of falling back to Text/Status.
- Done: Select, Multi-select, and Status share one option model with order and color, and the option sheet can edit names and cycle colors.
- Done: table cells and row-sheet properties now use choice dropdowns for Select/Multi-select/Status instead of raw text input.
- Done: AI/table type inference now recognizes select, multi-select, tags, labels, options, and choices.
- Done: edit-property `Details` sheet now exposes description and default value.
- Done: new rows and newly added properties apply typed default values from property config.
- Done: Relation cells now open a real row picker bottom sheet with search, multi-select checkboxes, row labels, and non-ID display.
- Done: Relation row picker now has selected-only filtering, selected count, clearer clear/save behavior, and same-page target databases can create a new row from the picker.
- Done: Relation cells now write typed `relationRowIds` directly through `TableMutationUseCase.updateRelationCell`; comma-string storage remains only as sync/backward-compatible display value.
- Done: changing a Relation target clears old row links and clears dependent Rollup target properties so stale IDs do not survive silently.
- Done: Rollup now validates relation dependency at mutation time and ignores non-relation columns as invalid dependencies.
- Done: Rollup display now has clearer incomplete-config states, counts related rows, and numeric aggregations ignore non-number values instead of treating them as zero.
- Done: Formula/Rollup computed cells now use graph/path-based dependency detection, so direct and indirect circular dependencies return `Circular dependency` instead of relying on a shallow depth guard.
- Done: Rollup config sheet now shows warnings for invalid/mismatched target property choices and includes a live preview from the first row.
- Done: Relation/Rollup UI now distinguishes unconfigured targets from missing cross-page/source databases with `Missing source` / `Missing property` states without destructively clearing cross-page references during sync delay.
- Done: page content normalization now repairs duplicate/blank table row IDs across the whole document, which protects relation picker keys and the projected row/cell tables.
- Done: backend table JSON mutation now rejects invalid Formula/Relation/Rollup patch combinations, clears stale relation cells when a target changes, and generates a new row ID if a duplicate row create request reaches the server.
- Done: added unit coverage for status config normalization, select config normalization, files/media typed value round-trip, and multi-select normalization.
- Done: added regression coverage for formula circular dependency detection, missing rollup source states, typed relation writes, stale relation cleanup, duplicate row ID repair, and backend property validation.
- Verification note: Android/backend compile passes. Focused Android unit tests for table mutation/content codec and focused backend mutator tests pass.

Core gaps:

- Property configuration still lacks persisted hidden state, wrap state, column width, and required field.
- `PageTableColumnType` still lacks several property types already expected from the Notion-like flow: Person, URL, Email, Phone, Button, Place, Created time/by, Last edited time/by, and ID.
- Row property editors now use typed relation writes, but several other property types still write through string-first paths.
- Formula is still a basic arithmetic engine. It still needs richer functions, type-aware errors, and preview per row.
- Relation and Rollup are now solid for MVP data integrity: typed writes, graph-based circular dependency guard, missing-source states, and backend validation are in place. Remaining later work is advanced sync conflict UX for two devices editing the same database source at the same time.
- Files/media is still stored/displayed as a string-like cell in some places; it should be a typed list of attachments end to end.
- Hide and unwrap content appear in the property sheet, but they currently dismiss the sheet without persistent behavior.
- AI Autofill appears as disabled UI. It should stay hidden or become a real property action later.

Add property flow audit:

- Current flow is close: header add-cell opens `NewTableColumnSheet`, user can type name, then tap type.
- Good behavior: empty name falls back to selected type label.
- Weakness: every type is shown in one long list. This will get noisy once more property types are added.
- Needed: searchable grouped type picker:
  - Basic: Text, Number, Date, Checkbox, URL, Email, Phone
  - Choice: Select, Multi-select, Status
  - Advanced: Formula, Relation, Rollup, Button
  - Metadata: Created time/by, Last edited time/by, ID
  - Media/Location: Files & media, Place
- Needed: if user creates a config-heavy property like Select, Status, Formula, Relation, or Rollup, immediately open the config detail after creation.

Edit property flow audit:

- Current sheet has the right direction, but too many actions are always visible.
- Rename and type should stay at the top.
- Type-specific settings should be visible only when useful:
  - Date: date format, time format, timezone, reminder, clear behavior.
  - Select/Multi-select/Status: options, colors, default.
  - Formula: formula editor, preview, validation error.
  - Relation: target database, limit/sync direction.
  - Rollup: relation, target property, aggregation, empty/error state.
  - Files/media: allowed media type and attachment behavior.
- View controls should be separate from property settings:
  - Filter, Sort, Group, Calculate belong to the current view, not the property itself.
- Unsupported actions should not be visible. Hide, unwrap, and AI Autofill should either be implemented or removed until ready.
- Delete property needs a confirmation if the column contains values.

Row sheet property flow audit:

- Row sheet now feels closer to a database item, but the property editor should become typed-first.
- Text/Number should validate immediately and keep cursor/input behavior stable.
- Date tap already opens a date editor. It needs typed storage as the source of truth, not string storage first.
- Checkbox is usable, but should show a compact checkbox-only control instead of repeating `Checked`/`Empty` text if the row is dense.
- Relation should open a row picker from the target database, not accept/display raw row IDs.
- Rollup and Formula should be read-only with a clear computed/error state.
- Add property in row sheet should reuse the same new-property sheet and return focus to the new property value.

Backend/data audit:

- Column patch API already carries the core fields: name, type, date settings, formula, relation, rollup.
- Database projection tables now support typed cell values and row metadata, which is the correct base for future property work.
- Missing backend-level shape: a single serialized `configJson`/column config object for property-specific settings. Adding fields one by one to `PageTableColumn` will become brittle.
- Missing validation: backend should reject invalid property config combinations, for example Rollup without a valid Relation column.
- Missing query layer: relation/rollup/search over typed values should eventually use the projection tables, not only parse page JSON.

UI/UX decisions:

- Property sheet should be a focused decision surface, not a feature list.
- The first row should be icon + editable property name + type chip.
- The next section should be type-specific settings only.
- View actions should be grouped separately and only shown when they affect the current database view.
- Destructive actions stay at the bottom.
- Every row stays 48-56dp with a 44dp touch target.
- Avoid nested cards inside sheets. Use grouped plain surfaces with dividers.

Recommended implementation order:

1. Done: add `PageTableColumnConfig` as one typed config object for option sets, description, default value, and future property settings.
2. Done: make the edit-property sheet capability-aware enough to hide unsupported actions and show type-specific settings.
3. Done: add proper Select, Multi-select, and Status option models with color and order.
4. Done: convert table/row property editors to typed-first reads with backward-compatible string fallback.
5. Done: build relation row picker and make relation display labels, not IDs.
6. Done: build formula validation and computed/error display.
7. Done: build rollup evaluation with relation dependency checks.
8. Done: add delete-property confirmation when data exists.
9. Done: add tests for property mutation, type conversion, relation config, rollup config, and typed value mapping.
10. Only after that, add advanced property types like Person, Button, Place, created/edited metadata, and ID.

### AI Chat

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/ai/AiChatSheet.kt`

Findings:

- AI chat is now unified behaviorally, but the sheet header is still busy: title, mode/model selector, attached page label, New, Clear, Close.
- Messages have labels "You" and "CYL AI" on every bubble. This adds repetition.
- Action details are valuable but visually technical if expanded in the main chat stream.
- Input uses full `OutlinedTextField`, which feels heavier than the home bottom ask field.

Decision:

- Header should be compact like ChatGPT: model/mode dropdown as the main title row, close icon, optional new chat icon.
- Attached page should be a small context chip, not a full text line.
- Message role labels should only appear if needed. Bubble alignment already explains role.
- Action details should be collapsed by default into one small status row.
- Input should look like one rounded composer with send icon inside or attached.

### Search

Files:

- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/home/HomeRoute.kt`
- `androidApp/src/main/java/com/changeyourlife/cyl/presentation/page/PageEditorRoute.kt`

Findings:

- Home search is a dedicated route, which is better than a centered overlay.
- Page search is still a sheet, which is acceptable, but should look like quick find.
- Search result cards still use card surfaces; search should be fast and list-like.

Decision:

- Home search: full screen, list-first, no decorative card.
- Page search: bottom sheet quick find with compact rows and direct jump.
- AI should use the same search model later, but UI search should stay manual and predictable.

## Design Rules For CYL

Use these rules before doing any UI refactor:

1. Plain document first

Text blocks should render as text on the page. Do not put text blocks inside cards or framed containers.

2. One persistent command bar

Search, AI, and create can remain in the bottom command bar on home and page. Do not duplicate them in the top bar.

3. Contextual actions only

Move, delete, turn into, insert, property edit, and row actions should appear on focus, long press, header tap, or menu tap.

4. Tables are dense

Tables should use borders, dividers, and stable cell dimensions. Avoid card-heavy table surfaces.

5. Sheets are for decisions

Bottom sheets should be used for creating, editing, choosing, and confirming. They should not become mini full pages with too many unrelated actions.

6. Reduce labels

Prefer icons and concise labels only where they clarify. Avoid repeated labels like "Properties", "Row", "Property", "CYL AI", and debug-like status text if context is already clear.

7. 44dp touch target

All important icon buttons should keep at least 44dp hit area. Visual icon can be smaller.

8. Stable spacing system

Use consistent spacing:

- Page horizontal padding: 20-22dp
- Dense table cell horizontal padding: 8-10dp
- Block vertical rhythm: 2-6dp for text blocks, 10-14dp for non-text blocks
- Sheet horizontal padding: 20dp
- Toolbar item size: 40-44dp

## Priority Plan

### Phase 1: Page Editor Clean Pass

Goal: make the main page feel like a document.

Tasks:

- Keep separate block render paths:
  - `PlainTextBlockRow` for text, heading, todo, bullet, numbered, quote.
  - `StructuredBlockContainer` for table, media, divider, special blocks.
- Remove fixed 30dp handle space from plain text blocks.
- Show block handle only on focus/long press as overlay or narrow leading affordance.
- Quiet `PageEditorTopBar`: back button + compact title/status, no block count unless debug.
- Keep bottom command bar consistent.
- Verify keyboard toolbar remains stable and does not cover text.

Acceptance:

- A page with only text blocks looks like a clean note.
- No visible card/frame around normal text blocks.
- Block actions are still reachable.
- Delete/indent/outdent/slash/mention still work.

Current progress:

- Done: `PageEditorRoute.kt` has been reduced to route/screen orchestration and split into focused surface files: `PageEditorChrome.kt`, `PageEditorBlocks.kt`, `PageEditorSearchProperties.kt`, `PageEditorTableBlock.kt`, `PageEditorTableRowPage.kt`, `PageEditorTableViews.kt`, and `PageEditorShared.kt`.
- Done: page top bar now uses a compact sync icon/sheet instead of permanent block-count/status text.
- Done: page top bar now uses a flatter document-style surface with quieter title and sync/back icon treatment.
- Done: page title editor is isolated into `PageTitleEditor` with larger document-title typography and softer placeholder.
- Done: text-like blocks are classified separately from table/media/divider blocks.
- Done: normal text, heading, todo, bullet, numbered, and quote blocks no longer keep a permanent 30dp handle column when inactive.
- Done: text-like blocks render through `PlainTextBlockRow`, not a `Card`, with tighter page rhythm.
- Done: visible block handles were removed from page blocks; focused-block actions now live in the toolbar above the keyboard.
- Done: structured blocks render through `StructuredBlockContainer`, with tap/long-press selection and no permanent handle.
- Done: page editor content now avoids card-in-card composition; block wrappers are plain `Box`/background surfaces instead of `Card`.
- Done: sync conflict banner also uses a plain clipped background instead of `Card`, so page editor has no remaining card container in normal page chrome.
- Done: page scaffold uses the same plain background as the editor body so normal blocks do not sit on a separate panel color.
- Done: subpage property editors are plain sections with dividers instead of card containers.
- Done: keyboard rich-text and block toolbars now reuse the shared floating chrome language.
- Done: keyboard rich-text toolbar now validates selection/span ranges before rendering to avoid crashes when the IME opens on a stale editor state.
- Done: rich clipboard paste detection is fail-safe, so clipboard/HTML parser errors cannot crash normal typing.
- Done: rich-text toolbar color parsing now uses safe RGB `Color(red, green, blue, alpha)` construction instead of packed `ULong`, fixing the keyboard-open crash from `RichTextSwatchButton` / `Color.copy()`.
- Done: new-block sheet is a compact command-style list instead of a heavy settings-style list.
- Done: the old `BlockEditorCard` wrapper has been replaced by `PageEditorBlock`, which routes to `PlainTextBlockRow` or `StructuredBlockContainer`.
- Done: structured blocks remain selectable without visible handles, so database/media/divider block actions are still reachable from the focused toolbar.

### Phase 2: Table Visual Pass

Goal: make table feel like database/spreadsheet.

Tasks:

- Compress `TableToolbar` into a single row:
  - table/view pill
  - search icon
  - sort icon
  - filter icon
  - group/settings icon
  - add button/dropdown
- Move active sort/filter/group summary into chips that appear only when active.
- Restyle `TableHeaderCell`:
  - smaller label typography
  - subtle dividers
  - stable width
  - hide/dropdown icon only on press or use subtle chevron.
- Restyle `TableDataRow` and `TableCellEditor` for denser spreadsheet feel.
- Keep add row as the final row and add column as final column.

Acceptance:

- Table can be scanned quickly.
- Header tap clearly opens property edit.
- Add row/column placement feels like spreadsheet.
- No extra table title duplicated outside the view/table pill.

Current progress:

- Done: table/view selector is now a compact single-row pill with table title and view chip.
- Done: table/view selector and sort/filter/group icons are lighter pills with softer active states.
- Done: table headers use smaller typography, smaller property icons, and a quieter dropdown indicator.
- Done: table row/header heights are slightly denser for a spreadsheet-like rhythm.
- Done: empty table/list/calendar/gallery/timeline/dashboard states now render as quiet inline hints instead of framed cards.
- Done: sort/filter/group controls are tighter 40dp icon controls and active summaries are quieter chips.
- Done: added a true table-specific search control in the database toolbar.
- Done: table search is transient UI state, applies across table/list/board/calendar/gallery/timeline/dashboard views, and searches column display values plus row-page content.
- Done: row `OPEN` action is now embedded inside the first/name column instead of living in a separate Open column/header.
- Done: new blank databases now start with only the `Name` property, so Status/Date/etc. are opt-in properties.
- Done: table rows now support long-press drag reordering in the table view.
- Done: stationary long-press on a table row opens a row action sheet with edit properties, copy link, duplicate, move up/down, move to trash, and last-edited info.
- Still planned: row-level favourite, icon, and move-to-location need real row metadata/storage before those actions can be enabled safely.

### Phase 3: Row Page Sheet Clean Pass

Goal: row page should feel like opening a database item.

Tasks:

- Restyle `TableRowPageSheet`:
  - title at top
  - properties directly below
  - content editor below divider
- Remove top "Row" and "Property" buttons.
- Put add property behind a small plus/menu near property area.
- Put add row outside row page, not inside row page.
- Remove always-visible delete icon beside each row block; use block action menu instead.

Acceptance:

- Opening a row feels like a page, not a form.
- User can edit typed properties and content without visual clutter.
- Row content uses same block behavior as page content.

Current progress:

- Done: removed the large top `Row` and `Property` buttons from row page sheet.
- Done: add property is now a small icon action beside the properties heading.
- Done: removed always-visible row block delete icons.
- Done: row page blocks now track focused/selected block state and use the same compact keyboard block toolbar pattern as normal page blocks.
- Done: row page block toolbar supports change type, insert above/below, move up/down, indent/outdent, linked page creation, and delete.

### Phase 4: AI Chat Polish

Goal: make AI feel like one clean assistant.

Tasks:

- Compact AI header:
  - model/mode dropdown
  - context chip when inside a page
  - new chat icon
  - close icon
- Remove repeated role labels from every message.
- Make input composer match bottom command bar language.
- Keep action details collapsed and quiet.
- Keep long press copy.

Acceptance:

- AI chat feels like one assistant across home/page.
- User can see current mode/model without the header becoming crowded.
- Page context is visible but not noisy.

Current progress:

- Done: header now centers around the mode/model selector instead of a separate `CYL AI` title stack.
- Done: attached page context is a compact chip.
- Done: new/clear are icon actions.
- Done: repeated `You` / `CYL AI` labels were removed from each message.
- Still planned: make the composer fully match the bottom command bar visual language.

### Phase 5: Home/Search Polish

Goal: home should be calm and navigable.

Tasks:

- Reduce top header weight:
  - profile button
  - appbar tabs
  - no standalone sync button when clean
- Move profile menu to a bottom sheet with Trash, Sync status, Retry sync, and Logout.
- Use icon-only `+` for section create actions such as `Private`.
- Make recents less visually dominant or optional.
- Make page rows cleaner with icon, title, subtitle/status, menu.
- Search route becomes list-first with compact results.
- Trash remains a separate screen with simple back button and icon-only restore/delete actions.

Acceptance:

- Home does not look like a dashboard.
- Main actions remain predictable at bottom.
- Search feels like navigation, not a decorative page.

Current progress:

- Done: added reusable chrome primitives for Home command/profile/tab surfaces.
- Done: Home profile and tab switcher now use the same floating chrome language as the bottom command bar.
- Done: Recents are smaller quick-access tiles, no longer large dashboard-like cards.
- Done: empty states in Home/Activity/Trash are plain centered/list-style states instead of decorative cards.
- Done: search input no longer sits inside an extra card wrapper.
- Done: search results are list rows with dividers, not cards.
- Done: empty search prompt is centered and plain.
- Done: page row action sheet no longer duplicates `Open page`; row tap remains the navigation gesture.
- Done: page row action sheet no longer exposes add-block shortcuts; block editing stays inside the page editor.
- Done: page rows, chat history rows, and trash rows now share a 64dp list rhythm, 40dp icon frame, and 44dp action hit areas.
- Done: chat history create action is icon-only like the Private section `+`, not a separate text button.
- Done: Trash restore/delete actions are compact icon actions inside the row, no longer a separate card action band.
- Done: database title/view dropdown now opens a full-height bottom sheet with a compact name editor, a single `New view` card containing all view choices, and a `New data source` section fed by database references from current/other loaded pages.
- Done: selecting a data source imports its properties, rows, row-page content, view config, group field, and source metadata into the current database with cloned IDs so it remains safe to edit.
- Done: open-row sheet now uses a full-height bottom sheet, scrollable content, fixed keyboard controller inside the sheet, and hides that controller whenever row editor focus is lost.
- Done: empty row-page content now behaves like the main page body: it stays visually blank, tapping the blank area creates/focuses the first text block, and the extra visible add-block row has been removed so row pages feel like notes, not cards.
- Done: plain text blocks now behave like a notepad in both pages and row sheets: pressing Enter inserts a new line inside the same text block instead of creating a separate block row, and text blocks use a one-line minimum height.
- Done: adding the first content block inside an empty row sheet no longer creates two rows/blocks. The table mutation now skips the synthetic fallback block for first insert, and repository inserts are idempotent by ID.
- Done: row sheet text fields no longer show `Add row notes`, and the bottom blank tap target that created another note block after content exists has been removed. Users continue downward with Enter inside the same note block.
- Done: empty row sheet content now shows a one-time `Enter text` hint only before the user's first tap. The hint is dismissed on focus even if the user types nothing, and it does not come back after focus is lost.
- Done: row sheet properties now render as a cleaner database-item list: each property uses a compact type icon, stable name column, plain inline value editor, subtle dividers, and quiet `Empty` states instead of noisy type labels and outlined fields.
- Done: row sheet now supports direct row-name editing from the sheet header. The title writes into the table's title column, uses a plain inline editor, and shows a quiet `Untitled row` placeholder when empty.
- Done: row sheet property labels/icons now open the same `Edit property` sheet as table headers, so users can rename, change type, configure date/formula/relation/rollup, duplicate, insert, delete, sort, filter, and group from inside the row sheet.
- Done: table dimensions and key state colors now have a dedicated `TableGridTokens` source, covering grid widths/heights, row/header colors, dragged/highlighted row backgrounds, dividers, and row-property dimensions.
- Done: row sheet title/property UI has been extracted into `PageEditorTableRowProperties.kt`, leaving `PageEditorTableRowPage.kt` closer to sheet orchestration and row content editing.

## Refactor After UX Direction

Only after Phase 1 and Phase 2 are visually accepted:

- Split `PageEditorRoute.kt` by surface:
  - `PageEditorScreen.kt`
  - `PageEditorTopBar.kt`
  - `PageEditorBottomBar.kt`
  - `BlockEditorList.kt`
  - `TableBlockEditor.kt`
  - `TableGridEditor.kt`
  - `TablePropertySheets.kt`
  - `TableRowPageSheet.kt`
- Split `HomeRoute.kt` similarly:
  - `HomeScreen.kt`
  - `HomeHeader.kt`
  - `HomeTabs.kt`
  - `HomePageList.kt`
  - `HomeSearchScreen.kt`
  - `TrashScreen.kt`
- Keep ViewModels unchanged during visual polish unless state shape blocks a cleaner UI.

## First Implementation Recommendation

Start with Phase 1: Page Editor Clean Pass.

Reason:

- It is the highest-frequency screen.
- It will expose reusable spacing and toolbar rules.
- It reduces visual clutter before table polish.
- It gives a clean base before large file refactor.

Suggested first PR/change batch:

1. Done: introduce `PlainTextBlockRow` and `StructuredBlockContainer`.
2. Done: route text-like blocks away from `Card`.
3. Done: remove permanent block handle and keep actions in focused toolbar.
4. Done: compile after the clean pass.
5. Manual QA still recommended on device: page with text, todo, bullet, database, media, and row page.
