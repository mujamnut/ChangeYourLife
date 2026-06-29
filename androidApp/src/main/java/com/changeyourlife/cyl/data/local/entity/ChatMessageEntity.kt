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
    val createdAt: Long,
)
