package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.Page

data class EditorSuggestionQuery(
    val kind: RichTextCommandPaletteKind,
    val start: Int,
    val end: Int,
    val query: String,
)

data class EditorSuggestionState(
    val query: EditorSuggestionQuery,
    val items: List<RichTextCommandPaletteItem>,
    val selectedIndex: Int = 0,
) {
    val selectedItem: RichTextCommandPaletteItem?
        get() = if (items.isEmpty()) {
            null
        } else {
            items[selectedIndex.coerceIn(0, items.lastIndex)]
        }
}

object EditorSuggestionController {
    fun resolve(
        text: String,
        cursor: Int,
        mentionPages: List<Page>,
        context: EditorCommandContext = EditorCommandContext(),
        selectedIndex: Int = 0,
        enabledKinds: Set<RichTextCommandPaletteKind> = RichTextCommandPaletteKind.entries.toSet(),
    ): EditorSuggestionState? {
        if (RichTextCommandPaletteKind.Mention in enabledKinds) {
            val mentionQuery = RichTextMentionParser.activeQuery(text = text, cursor = cursor)
            if (mentionQuery != null) {
                val pages = matchingMentionPages(mentionPages, mentionQuery.query)
                val items = richTextMentionPaletteItems(pages)
                return items.toSuggestionState(
                    query = EditorSuggestionQuery(
                        kind = RichTextCommandPaletteKind.Mention,
                        start = mentionQuery.start,
                        end = mentionQuery.end,
                        query = mentionQuery.query,
                    ),
                    selectedIndex = selectedIndex,
                )
            }
        }

        if (RichTextCommandPaletteKind.Slash in enabledKinds) {
            val slashQuery = RichTextSlashCommandParser.activeQuery(text = text, cursor = cursor)
            if (slashQuery != null) {
                val entries = EditorCommandRegistry.matchingSlashCommands(
                    query = slashQuery.query,
                    context = context,
                )
                val items = editorSlashPaletteItems(entries)
                return items.toSuggestionState(
                    query = EditorSuggestionQuery(
                        kind = RichTextCommandPaletteKind.Slash,
                        start = slashQuery.start,
                        end = slashQuery.end,
                        query = slashQuery.query,
                    ),
                    selectedIndex = selectedIndex,
                )
            }
        }

        return null
    }

    fun moveSelection(
        state: EditorSuggestionState,
        delta: Int,
    ): EditorSuggestionState {
        if (state.items.isEmpty()) return state.copy(selectedIndex = 0)
        val nextIndex = (state.selectedIndex + delta).floorMod(state.items.size)
        return state.copy(selectedIndex = nextIndex)
    }

    fun matchingMentionPages(
        pages: List<Page>,
        query: String,
        limit: Int = 8,
    ): List<Page> {
        val normalized = query.trim()
        return pages
            .filter { page ->
                val title = page.title.ifBlank { "Untitled page" }
                normalized.isBlank() || title.contains(normalized, ignoreCase = true)
            }
            .take(limit)
    }
}

private fun List<RichTextCommandPaletteItem>.toSuggestionState(
    query: EditorSuggestionQuery,
    selectedIndex: Int,
): EditorSuggestionState? {
    if (isEmpty()) return null
    return EditorSuggestionState(
        query = query,
        items = this,
        selectedIndex = selectedIndex.coerceIn(indices),
    )
}

private fun Int.floorMod(size: Int): Int {
    return ((this % size) + size) % size
}
