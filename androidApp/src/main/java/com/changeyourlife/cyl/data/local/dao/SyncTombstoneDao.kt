package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.SyncTombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncTombstoneDao {
    @Query(
        """
        SELECT * FROM sync_tombstones
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getPendingTombstones(): List<SyncTombstoneEntity>

    @Query("SELECT COUNT(*) FROM sync_tombstones")
    fun observePendingTombstoneCount(): Flow<Int>

    @Upsert
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity)

    @Query("DELETE FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteTombstone(entityType: String, entityId: String)
}
