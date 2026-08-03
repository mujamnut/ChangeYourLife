package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "incoming_share_drafts",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index(value = ["status", "updatedAt"]),
        Index(value = ["expiresAt"]),
    ],
)
data class IncomingShareDraftEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val action: String,
    val subject: String,
    val status: String,
    val errorCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
)

@Entity(
    tableName = "incoming_share_items",
    foreignKeys = [
        ForeignKey(
            entity = IncomingShareDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["draftId", "position"], unique = true),
        Index(value = ["status"]),
    ],
)
data class IncomingShareItemEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val position: Int,
    val kind: String,
    val sourceUri: String?,
    val text: String?,
    val html: String?,
    val displayName: String,
    val declaredMimeType: String,
    val stagedPath: String?,
    val resolvedMimeType: String,
    val assetKind: String?,
    val sizeBytes: Long,
    val sha256: String,
    val status: String,
    val errorCode: String?,
)
