package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["scopeId"]),
        Index(value = ["createdAt"]),
        Index(value = ["updatedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val scopeId: String,
    val role: String,
    val content: String,
    val pageLinksJson: String,
    @ColumnInfo(defaultValue = "''")
    val actionMetadataJson: String,
    @ColumnInfo(defaultValue = "'[]'")
    val attachmentsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = SyncStatus.PendingPush,
    val remoteUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)
