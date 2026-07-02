# CYL Rich Editor Research

Research date: 2026-07-02

This note collects implementation ideas from primary docs for Tiptap, ProseMirror, Lexical, BlockNote, Slate, and Plate. CYL remains a native Android/Compose editor; we borrow architecture and UX patterns, not web editor source code.

## Sources Read

- Tiptap overview, extension model, custom menus, suggestion utility, bubble menu, placeholder:
  - https://tiptap.dev/docs/editor/getting-started/overview
  - https://tiptap.dev/docs/editor/core-concepts/extensions
  - https://tiptap.dev/docs/editor/getting-started/style-editor/custom-menus
  - https://tiptap.dev/docs/editor/api/utilities/suggestion
  - https://tiptap.dev/docs/editor/extensions/functionality/bubble-menu
  - https://tiptap.dev/docs/editor/extensions/functionality/placeholder
- ProseMirror guide:
  - https://prosemirror.net/docs/guide/
- Lexical editor state and commands:
  - https://lexical.dev/docs/concepts/editor-state
  - https://lexical.dev/docs/concepts/commands
- BlockNote intro, document structure, UI components, content manipulation, paste handling:
  - https://www.blocknotejs.org/docs
  - https://www.blocknotejs.org/docs/foundations/document-structure
  - https://www.blocknotejs.org/docs/react/components
  - https://www.blocknotejs.org/docs/reference/editor/manipulating-content
  - https://www.blocknotejs.org/docs/reference/editor/paste-handling
- Slate interfaces and transforms:
  - https://docs.slatejs.org/concepts/01-interfaces
  - https://docs.slatejs.org/concepts/04-transforms
- Plate intro and slash command:
  - https://platejs.org/docs
  - https://platejs.org/docs/slash-command

## Principles To Adopt

1. Canonical document model, not HTML

ProseMirror and Lexical both avoid treating HTML as the source of truth. CYL should keep canonical `PageBlock` + `PageTextSpan` + typed table model. HTML/Markdown should remain import/export formats only.

2. All edits go through commands/mutations

ProseMirror uses transactions, Lexical uses command dispatch/update, Slate uses transforms. CYL should treat every edit as an `EditorCommand` or domain mutation:

- text update
- format span
- insert/split/delete/move block
- change block type
- add/remove/update property
- update typed table cell
- AI action

3. Block-first document structure

BlockNote models a document as blocks with `id`, `type`, `props`, `content`, and `children`. CYL already has page blocks and row-page blocks; next work should strengthen:

- stable block ids
- optional children/nesting
- props per block type
- table content as its own typed structure
- inline rich content inside text-like blocks

4. Suggestion/command palette as editor primitive

Tiptap Suggestion, BlockNote suggestion menus, and Plate slash command all treat trigger menus as a reusable primitive:

- trigger character: `/`, `@`, maybe `+`
- query text after trigger filters items
- item groups
- keyboard navigation
- Escape closes
- Enter selects
- contextual enable/disable, e.g. no slash in code block

CYL should keep `RichTextCommandPalette`, but extend it into an editor-level primitive with keyboard selection, groups, and context filters.

5. Separate inline toolbar, slash menu, side menu, and link toolbar

BlockNote separates UI components:

- formatting toolbar for selected text
- suggestion menu for trigger characters
- link toolbar for link hover/selection
- side menu/drag handle for block-level actions

CYL should not put all actions in one toolbar. Use the right surface:

- selected text: compact formatting toolbar above keyboard
- empty/current block: slash menu
- block row: small side handle or long-press menu
- link span: link edit sheet
- table header: property bottom sheet

6. Placeholder is state-aware

Tiptap supports placeholders per node. CYL should use contextual placeholders:

- first empty page block: "Write, type / for blocks, or ask AI"
- heading block: "Heading"
- todo block: "To-do"
- quote block: "Quote"
- table empty row: "Item"

Do not show noisy helper text everywhere; show only current/empty target.

7. Table cells are not the same as rich text blocks

BlockNote table cells contain inline content, but CYL wants Notion-like typed database fields. Keep table cells typed:

- date opens date picker
- number opens numeric keyboard
- select opens option picker
- files opens media picker
- checkbox toggles directly

Rich text command palette should not hijack table cells unless the column type is rich text.

8. AI should operate on the same mutation model

Do not let AI write raw UI text or raw HTML. AI should produce structured editor actions:

- create page/block/table
- insert rows/properties
- format spans
- update typed property/cell
- create views

Then Android applies the same mutation path as manual edits, with undo.

## UI/UX Blueprint For CYL

### Block Editor

- Plain page surface, no card around text blocks.
- Each block has stable height behavior; text edits should not shift surrounding controls unexpectedly.
- Show block handle only on focus/long press:
  - drag/move
  - duplicate
  - delete
  - turn into
- Enter splits block and focuses new block.
- Backspace on empty block deletes and focuses previous block.
- Long press block opens block actions; do not clutter top bar.

### Command Palette

Upgrade `RichTextCommandPalette` to support:

- grouped sections: Basic, Lists, Media, Database, AI, Page
- selected index state
- keyboard navigation: up/down/enter/escape
- aliases: `/h1`, `/ul`, `/todo`, `/db`, `/ai`
- context filters:
  - no slash in code block
  - show property commands only inside table/row-page context
  - show AI commands only when backend/model available
- mobile placement:
  - above keyboard when possible
  - otherwise bottom sheet style if menu is tall

### Formatting Toolbar

Keep toolbar compact and icon-first:

- bold, italic, underline, strike
- code
- link
- text color/highlight
- block type shortcut
- undo/redo

Behavior:

- active state reflects selection
- collapsed selection stores typing style
- selection toolbar must not break keyboard focus

### Link Editing

Dedicated link sheet/toolbar:

- shows current URL
- edit/remove/copy/open actions
- validation for URL/email/phone later
- mention pages use hidden id metadata, not plain fragile text only

### Paste / Import

Already implemented direction is correct:

- canonical CYL storage remains block/span model
- parse HTML/Markdown into blocks/spans
- table cells typed policy stays separate

Next improvements:

- import nested lists
- import task/checklist state more reliably
- import table HTML into typed table when user chooses table paste
- export CYL blocks to Markdown/HTML for sharing

## Architecture Target

Recommended modules:

- `EditorDocument`: canonical page/blocks/tables/properties snapshot
- `EditorSelection`: block id + offset/range + optional table cell/property target
- `EditorCommand`: user/AI/manual command
- `EditorTransaction`: command result with before/after, affected ids, focus target
- `EditorCommandRegistry`: slash/mention/block commands with aliases and context filters
- `EditorSuggestionController`: reusable trigger/query/selection state
- `EditorMutationUseCase`: applies commands to model and returns transaction
- `EditorUndoManager`: command-based undo/redo

Composable layer should render state and dispatch events only.

## Priority For Next Build

1. Done: turn `RichTextCommandPalette` into `EditorSuggestionController` for slash and mention suggestions.
2. Done: add grouped command sections and selected item state for keyboard navigation.
3. Done: add block side handle / focused block action menu for turn-into, insert above/below, add inside, move, and delete.
4. Done: add contextual placeholder system for first page block, focused text block, row-page notes, heading/list/todo/quote, and media captions.
5. Done: add link edit sheet actions for apply, remove, open, and copy, with selected-link detection and URL scheme normalization.
6. Done: add `EditorCommandRegistry` so slash menu, suggestion UI, block picker, keyboard block toolbar, block action menu, and rich text toolbar resolve command/action entries from one registry.
7. Done: add nested block indent/outdent through `EditorCommand.MoveBlockToParent`, `PageMutationUseCase`, root page UI, row-page table UI, undo command payloads, and regression tests.
8. Done: add `PageDocumentExporter` for Markdown/HTML export from canonical CYL `PageBlockDocument`, including rich text spans, nested children, media, tables, todos, lists, quotes, and dividers.

## Verification

- `./gradlew.bat :androidApp:compileDebugKotlin --no-daemon`
- `./gradlew.bat :androidApp:testDebugUnitTest --tests "com.changeyourlife.cyl.domain.model.EditorCommandExecutorTest" --tests "com.changeyourlife.cyl.domain.usecase.PageMutationUseCaseTest" --tests "com.changeyourlife.cyl.domain.usecase.TableMutationUseCaseTest" --tests "com.changeyourlife.cyl.domain.model.PageDocumentExporterTest" --tests "com.changeyourlife.cyl.presentation.page.RichTextSlashCommandParserTest" --tests "com.changeyourlife.cyl.presentation.page.EditorSuggestionControllerTest" --tests "com.changeyourlife.cyl.presentation.page.RichTextCommandPaletteTest" --tests "com.changeyourlife.cyl.presentation.page.EditorPlaceholderPolicyTest" --tests "com.changeyourlife.cyl.presentation.page.RichTextLinkPolicyTest" --no-daemon`
