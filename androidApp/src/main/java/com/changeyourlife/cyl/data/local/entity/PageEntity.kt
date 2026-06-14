package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["parentPageId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val parentPageId: String?,
    val title: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

