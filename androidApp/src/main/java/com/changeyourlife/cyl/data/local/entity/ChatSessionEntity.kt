package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["scopeId"]),
        Index(value = ["updatedAt"]),
    ],
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val scopeId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
