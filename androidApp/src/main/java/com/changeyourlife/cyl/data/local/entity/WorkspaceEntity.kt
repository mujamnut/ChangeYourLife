package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = SyncStatus.Synced,
    val remoteUpdatedAt: Long = updatedAt,
    val lastSyncedAt: Long = 0L,
)

object SyncStatus {
    const val Synced = "Synced"
    const val PendingPush = "PendingPush"
    const val Conflict = "Conflict"
}
