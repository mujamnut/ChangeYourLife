package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.PageAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageAssetDao {
    @Query(
        """
        SELECT * FROM page_assets
        WHERE ownerPageId = :pageId AND deletedAt IS NULL
        ORDER BY createdAt ASC
        """,
    )
    fun observeByPage(pageId: String): Flow<List<PageAssetEntity>>

    @Query("SELECT * FROM page_assets WHERE id = :assetId LIMIT 1")
    suspend fun getById(assetId: String): PageAssetEntity?

    @Upsert
    suspend fun upsert(asset: PageAssetEntity)

    @Query(
        """
        UPDATE page_assets
        SET status = :nextStatus,
            progressPercent = :progressPercent,
            errorCode = :errorCode,
            remoteAssetId = CASE
                WHEN :remoteAssetId IS NULL THEN remoteAssetId
                ELSE :remoteAssetId
            END,
            updatedAt = :updatedAt
        WHERE id = :assetId
          AND deletedAt IS NULL
          AND status IN (:expectedStatuses)
        """,
    )
    suspend fun transitionStatus(
        assetId: String,
        expectedStatuses: List<String>,
        nextStatus: String,
        progressPercent: Int,
        errorCode: String?,
        remoteAssetId: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE page_assets
        SET status = :deletedStatus,
            progressPercent = 0,
            errorCode = NULL,
            deletedAt = :deletedAt,
            updatedAt = :deletedAt
        WHERE id = :assetId AND deletedAt IS NULL
        """,
    )
    suspend fun markDeleted(
        assetId: String,
        deletedStatus: String,
        deletedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM page_assets
        WHERE deletedAt IS NOT NULL AND deletedAt <= :deletedBefore
        ORDER BY deletedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getCleanupCandidates(
        deletedBefore: Long,
        limit: Int = 200,
    ): List<PageAssetEntity>

    @Query("DELETE FROM page_assets WHERE id = :assetId")
    suspend fun deleteById(assetId: String)
}
