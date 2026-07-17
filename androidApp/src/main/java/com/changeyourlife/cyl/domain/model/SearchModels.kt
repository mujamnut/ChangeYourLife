package com.changeyourlife.cyl.domain.model

enum class SearchTargetType {
    Page,
    Block,
    Table,
    Row,
    Property,
    Column,
    Cell,
    Chat,

    ;

    companion object {
        fun defaultSearchScopes(): Set<SearchTargetType> = setOf(
            Page,
            Block,
            Table,
            Row,
            Property,
            Column,
            Cell,
        )

        fun aiContextScopes(): Set<SearchTargetType> = defaultSearchScopes()
    }
}

data class SearchTarget(
    val type: SearchTargetType,
    val workspaceId: String = "",
    val pageId: String = "",
    val blockId: String = "",
    val tableBlockId: String = "",
    val rowId: String = "",
    val columnId: String = "",
    val propertyId: String = "",
    val chatSessionId: String = "",
    val chatMessageId: String = "",
) {
    val key: String
        get() = listOf(
            type.name,
            workspaceId,
            pageId,
            blockId,
            tableBlockId,
            rowId,
            columnId,
            propertyId,
            chatSessionId,
            chatMessageId,
        ).joinToString(separator = ":")

    val isPageScoped: Boolean
        get() = pageId.isNotBlank()
}

data class SearchResult(
    val target: SearchTarget,
    val title: String,
    val subtitle: String = "",
    val snippet: String = "",
    val score: Int = 0,
    val updatedAt: Long = 0,
    val matchedTerms: List<String> = emptyList(),
) {
    val id: String
        get() = target.key
}

data class SearchQuery(
    val workspaceId: String,
    val query: String,
    val scopes: Set<SearchTargetType> = SearchTargetType.defaultSearchScopes(),
    val limit: Int = DefaultSearchLimit,
    val currentPageId: String = "",
    val includeDeleted: Boolean = false,
) {
    val normalizedQuery: String
        get() = query.normalizedSearchText()

    val hasQuery: Boolean
        get() = normalizedQuery.isNotBlank()

    fun normalized(): SearchQuery = copy(
        query = normalizedQuery,
        scopes = scopes.ifEmpty { SearchTargetType.defaultSearchScopes() },
        limit = limit.coerceIn(MinSearchLimit, MaxSearchLimit),
    )

    companion object {
        const val DefaultSearchLimit = 25
        private const val MinSearchLimit = 1
        private const val MaxSearchLimit = 100
    }
}

data class MentionQuery(
    val workspaceId: String,
    val query: String,
    val currentPageId: String = "",
    val limit: Int = DefaultMentionLimit,
) {
    val normalizedQuery: String
        get() = query.removePrefix("@").normalizedSearchText()

    fun toSearchQuery(): SearchQuery = SearchQuery(
        workspaceId = workspaceId,
        query = normalizedQuery,
        scopes = setOf(SearchTargetType.Page),
        limit = limit.coerceIn(1, MaxMentionLimit),
        currentPageId = currentPageId,
    )

    companion object {
        const val DefaultMentionLimit = 8
        private const val MaxMentionLimit = 25
    }
}

data class MentionCandidate(
    val pageId: String,
    val title: String,
    val subtitle: String = "",
    val score: Int = 0,
    val updatedAt: Long = 0,
) {
    val label: String
        get() = if (title.startsWith("@")) title else "@$title"
}

data class AiSearchContext(
    val content: String,
    val results: List<SearchResult> = emptyList(),
) {
    val isNotBlank: Boolean
        get() = content.isNotBlank()

    companion object {
        val Empty = AiSearchContext(content = "")
    }
}

fun String.normalizedSearchText(): String =
    trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
