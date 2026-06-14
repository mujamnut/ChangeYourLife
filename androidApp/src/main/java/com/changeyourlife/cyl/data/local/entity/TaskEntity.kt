package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["pageId"]),
        Index(value = ["dueAt"]),
        Index(value = ["updatedAt"]),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val pageId: String?,
    val title: String,
    val notes: String,
    val isCompleted: Boolean,
    val dueAt: Long?,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

