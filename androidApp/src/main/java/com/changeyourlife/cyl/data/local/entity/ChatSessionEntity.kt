package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["scopeId"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val scopeId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String = SyncStatus.PendingPush,
    val remoteUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)
