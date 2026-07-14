package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_skills",
    indices = [
        Index(value = ["workspaceId", "updatedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class AiSkillEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val whenToUse: String,
    val instructions: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PendingPush,
    val remoteUpdatedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)
