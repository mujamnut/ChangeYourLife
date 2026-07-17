package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_index",
    indices = [
        Index(value = ["workspaceId", "targetType", "deletedAt"]),
        Index(value = ["workspaceId", "updatedAt"]),
        Index(value = ["pageId"]),
        Index(value = ["blockId"]),
        Index(value = ["tableBlockId"]),
        Index(value = ["rowId"]),
        Index(value = ["columnId"]),
        Index(value = ["propertyId"]),
        Index(value = ["chatSessionId"]),
        Index(value = ["chatMessageId"]),
    ],
)
data class SearchIndexEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val targetType: String,
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
    val normalizedText: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)
