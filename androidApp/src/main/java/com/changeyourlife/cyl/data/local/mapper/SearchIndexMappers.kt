package com.changeyourlife.cyl.data.local.mapper

import com.changeyourlife.cyl.data.local.entity.SearchIndexEntity
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType

fun SearchIndexEntity.toDomain(
    score: Int = 0,
    matchedTerms: List<String> = emptyList(),
    snippetOverride: String? = null,
): SearchResult =
    SearchResult(
        target = SearchTarget(
            type = targetType.toSearchTargetType(),
            workspaceId = workspaceId,
            pageId = pageId,
            blockId = blockId,
            tableBlockId = tableBlockId,
            rowId = rowId,
            columnId = columnId,
            propertyId = propertyId,
            chatSessionId = chatSessionId,
            chatMessageId = chatMessageId,
        ),
        title = title,
        subtitle = subtitle,
        snippet = snippetOverride ?: snippet,
        score = score,
        updatedAt = updatedAt,
        matchedTerms = matchedTerms,
    )

private fun String.toSearchTargetType(): SearchTargetType =
    SearchTargetType.entries.firstOrNull { type -> type.name == this } ?: SearchTargetType.Page
