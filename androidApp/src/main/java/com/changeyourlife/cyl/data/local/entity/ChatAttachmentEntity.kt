package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_attachments",
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["sessionId"]),
        Index(value = ["status"]),
        Index(value = ["remoteAssetId"], unique = true),
    ],
)
data class ChatAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String?,
    val sessionId: String,
    val kind: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val sha256: String,
    val localPath: String?,
    val remoteAssetId: String?,
    val waveformJson: String,
    val transcript: String?,
    val language: String?,
    val status: String,
    val progressPercent: Int,
    val aiJobId: String?,
    val errorCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
