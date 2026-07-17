package com.changeyourlife.cyl.backend.model.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchListResponse(
    val results: List<SearchResultDto>,
    val nextCursor: String? = null,
)

@Serializable
data class SearchResultDto(
    val targetType: String,
    val workspaceId: String,
    val pageId: String = "",
    val blockId: String = "",
    val tableBlockId: String = "",
    val rowId: String = "",
    val columnId: String = "",
    val propertyId: String = "",
    val chatSessionId: String = "",
    val chatMessageId: String = "",
    val title: String,
    val subtitle: String = "",
    val snippet: String = "",
    val score: Int = 0,
    val updatedAt: Long = 0L,
)
