package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
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
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["pageId"]),
        Index(value = ["taskId"]),
        Index(value = ["remindAt"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val pageId: String?,
    val taskId: String?,
    val title: String,
    val remindAt: Long,
    val isDone: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

