package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_assets",
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
            childColumns = ["ownerPageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workspaceId"]),
        Index(value = ["ownerPageId"]),
        Index(value = ["status", "updatedAt"]),
        Index(value = ["deletedAt"]),
        Index(value = ["sha256"]),
        Index(value = ["remoteAssetId"], unique = true),
    ],
)
data class PageAssetEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val ownerPageId: String?,
    val kind: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val localPath: String?,
    val remoteAssetId: String?,
    val status: String,
    val progressPercent: Int,
    val errorCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
