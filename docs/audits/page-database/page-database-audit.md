# CYL Page Database Audit

Audit date: 2026-07-12

Scope:

- Page database surface only.
- Includes page route, table toolbar, table grid, cell editors, row sheet, property config, sync/conflict behavior, and performance risks.
- Excludes Home, AI chat, login, and general rich-text editor except where they affect database pages.

Skills used:

- `frontend-design-direction`
- `make-interfaces-feel-better`
- `compose-multiplatform-patterns`

## Product Direction

Database pages should feel like a focused spreadsheet/database surface, not like a table card embedded inside a note.

The right direction for CYL:

- Plain page background.
- Page title/header above.
- Database toolbar below title.
- Grid owns the working area.
- Row page opens as a focused sheet.
- Bottom app command bar stays behind page work and should not compete with table controls.

## Current Strengths

### Full database mode

`PageEditorRoute.kt` now has a separate `hasDatabaseBlock` branch. This is the correct product shape.

Why it matters:

- Database page no longer renders inside the normal document `LazyColumn`.
- The database can own the main work area.
- This avoids document-body scroll competing with table row scroll.

### One-scroll table body

`TableGridEditor` uses:

- static table toolbar/header area
- `LazyColumn` for rows in full-page mode
- stable row keys
- `contentType` for rows/add row/group headers

This is the right implementation shape for smooth large tables.

### Column windowing exists

Large tables no longer need to render every column in every visible row. `TableColumnRenderWindow` renders visible columns plus buffer.

This is important because database lag usually comes from row count multiplied by column count.

### Property config is persisted

`PageTableColumnConfig` already has:

- `isHidden`
- `isRequired`
- `wrapContent`
- `widthDp`
- `defaultValue`
- `description`

This is a good foundation for a real database.

### Granular sync exists

`PageEditorViewModel` has granular pending saves for:

- table cell value
- table patch
- table column patch
- table row patch

This is better than saving the whole page for every cell edit.

## Main Findings

### 1. Database full mode still shares too much generic page machinery

The route has a full database branch, but the table still passes through a very broad page/block path before reaching the table editor.

Risk:

- Page editor focus state can leak into database behavior.
- Generic block concepts can accidentally affect grid touch/scroll behavior.
- Future page polish can regress database pages.

Recommended fix:

- Create `PageDatabaseScreen` or `DatabasePageSurface`.
- Route full database pages directly into this composable.
- Keep `PageEditorBlock` for inline/non-full blocks only.

Priority: High.

### 2. Table toolbar is functional, but hierarchy is not fully settled

Current toolbar contains:

- view selector
- sync chip
- search
- table controls
- active control chips

This works, but the hierarchy can still feel busy.

Recommended layout:

- Row 1: view/database selector left, subtle sync state right.
- Row 2 only appears when needed: search field or active filter/sort/group chips.
- Sort/filter/group should live in one compact control sheet, not compete as permanent surface.

Priority: Medium.

### 3. Row gestures are powerful but discoverability is weak

Rows support long-press drag and stationary long-press action sheet.

Risk:

- User may not know drag exists.
- Long press can conflict with cell editing, especially text/date/select cells.
- Manual move actions in the sheet are useful backup, but the primary gesture needs clearer feedback.

Recommended fix:

- On long press, show subtle row lift immediately.
- During drag, show insertion target line.
- Keep stationary action sheet delay slightly longer than drag-start threshold.
- Avoid triggering row gesture when an interactive cell child has consumed tap/press.

Priority: High for feel, Medium for MVP.

### 4. Cell editing is lightweight but not yet fully database-grade

Strength:

- Text cells are display-first, edit-on-tap. This avoids always-active `BasicTextField` lag.

Risks:

- Text editing state is local per cell. If row list recycles while editing, focus can reset.
- Number cells currently use text path with numeric keyboard, but validation/display formatting is still thin.
- Empty cells are visually clean now, but required fields need a subtle empty/error state when user leaves row/saves.

Recommended fix:

- Track active editing cell at table level: `rowId + columnId`.
- Add per-type validation UI:
  - number parse warning
  - required empty warning
  - date invalid warning
- Keep empty display blank unless field is required or focused.

Priority: High.

### 5. Select/status dropdown is compact, but option management needs one stronger flow

Strength:

- Cell dropdown no longer wastes space with icons.
- Options are stored on column config.

Risks:

- Cell dropdown can choose existing options, but creating/editing options is separate in property sheet.
- AI can create options, but user flow should be just as clear.

Recommended fix:

- In select/status cell picker, add a final row: `New option` when query does not match.
- In property sheet, keep full option manager for rename/color/delete/reorder.
- Use same option creation logic for AI and UI.

Priority: Medium.

### 6. Relation and rollup are powerful but heavy

Strength:

- Relation title cache exists.
- Relation picker supports selected rows and source table data.

Risks:

- Relation picker can still become heavy for large target tables.
- Rollup/formula display uses recursive evaluation. It has circular protection, but large tables with many formula/rollup cells can still cost a lot.

Recommended fix:

- Add lazy search in relation picker.
- Add memoized computed cell cache per visible table render:
  - key: rowId, columnId, table version/hash, references version/hash.
- Avoid computing formula/rollup for offscreen virtualized columns.

Priority: High for performance.

### 7. Width measurement is useful but still content-scan based

`tableColumnWidths` samples rows and calls `displayCellText`.

Strength:

- It avoids fixed ugly widths.

Risk:

- For formula/rollup/relation columns, width measuring can trigger expensive display text.
- It recomputes when rows/columns/references change.

Recommended fix:

- Prefer persisted `widthDp` after user resize.
- For auto width, sample only plain display fields cheaply.
- Cap expensive computed columns to type-based width unless user manually resizes.

Priority: Medium.

### 8. Sync/conflict UX exists but row-level confidence is incomplete

Strength:

- Database sync chip exists.
- Granular saves exist.
- Conflict merger handles table/column/row/cell entities.

Risks:

- User sees global DB state, but not which row/cell is saving/failed/conflicted.
- Required/invalid state and failed sync state are visually separate problems today.

Recommended fix:

- Add subtle cell/row state:
  - saving dot on active edited row
  - failed state icon at row edge
  - conflict row highlight with detail sheet
- Keep global sync chip for summary only.

Priority: Medium.

### 9. Row sheet is close, but should become a reusable database item editor

Current row sheet has properties and row page blocks.

Risks:

- It can drift from normal page editor behavior.
- Bottom controller/toolbar rules can regress because row page is not its own focused surface yet.

Recommended fix:

- Extract:
  - `DatabaseRowSheet`
  - `DatabaseRowPropertyList`
  - `DatabaseRowContentEditor`
  - `DatabaseRowToolbarHost`
- Keep row sheet full-height behavior, but its internal content should own one vertical scroll.

Priority: High.

### 10. Other database views are still second-class

Table view is strongest. List, board, calendar, gallery, timeline, dashboard exist, but table is the real mature surface.

Recommended rule:

- Keep non-table views optional and data-source driven.
- Do not polish all views equally yet.
- First make table view excellent, then make calendar/timeline/dashboard consume the same validated table config.

Priority: Low until table is excellent.

## Performance Audit

Good:

- Full-page table uses one vertical row `LazyColumn`.
- Rows have stable keys.
- Large dataset row lazy rendering is active in full-page mode.
- Column windowing exists.
- Always-on row graphics layer was removed.
- Dropdown menus compose only when expanded.
- Relation title cache exists.

Remaining risks:

- `visibleRows` still filters/sorts in composition via `remember`.
- `groupedRows` groups visible rows in composition.
- `tableColumnWidths` still does content sampling in composition.
- Formula/rollup display can be evaluated per visible computed cell.
- Table references from other pages can cause broad invalidation.

Recommended performance sequence:

1. Move table projection creation out of composables.
2. Build `TableRenderModel` in ViewModel or a pure use case on `Dispatchers.Default`.
3. Include visible rows, grouped rows, column widths, relation title cache, and computed display cache.
4. Compose only renders the prepared render model.
5. Keep cell edit local and granular, but update render model incrementally when possible.

## UI/UX Polish Priority

### P0

- Direct full-page database surface, not generic page/block route.
- Active editing cell state at table level.
- Row sheet extraction into a dedicated database item editor.
- Computed cell cache for formula/rollup/relation-heavy tables.

### P1

- Table toolbar hierarchy cleanup.
- Row drag feedback and insertion line.
- Required field visual state.
- Select/status quick option creation.
- Row/cell saving and failed indicators.

### P2

- Non-table database views polish.
- Better resize handles.
- Keyboard navigation/next-cell movement.
- Multi-select row actions.
- Bulk edit.

## Recommended Next Step

Start with P0 item 1:

Create a dedicated `PageDatabaseSurface` that owns:

- title/header slot
- database toolbar
- table grid
- row sheet host
- sync/conflict summary

Then move table render model preparation out of `PageEditorTableBlock.kt`.

This gives the biggest payoff because it separates database behavior from document editor behavior before more UI polish is added.
