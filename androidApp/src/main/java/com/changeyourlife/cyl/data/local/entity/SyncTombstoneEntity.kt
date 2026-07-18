package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_tombstones",
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        Index(value = ["createdAt"]),
    ],
)
data class SyncTombstoneEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val createdAt: Long,
    val expectedRevision: Long = 0L,
)

object SyncTombstoneType {
    const val PagePermanentDelete = "PagePermanentDelete"
}
