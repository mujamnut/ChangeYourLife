package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import kotlinx.coroutines.flow.Flow

interface ContentAssetRepository {
    fun observeByPage(pageId: String): Flow<List<ContentAsset>>

    suspend fun getById(assetId: String): ContentAsset?

    suspend fun getPendingUploads(): List<ContentAsset>

    suspend fun upsert(asset: ContentAsset)

    suspend fun transitionStatus(
        assetId: String,
        expectedStatuses: Set<ContentAssetStatus>,
        nextStatus: ContentAssetStatus,
        progressPercent: Int,
        errorCode: String?,
        remoteAssetId: String?,
        updatedAt: Long,
    ): Boolean

    suspend fun markDeleted(
        assetId: String,
        deletedAt: Long,
    ): Boolean

    suspend fun getCleanupCandidates(deletedBefore: Long): List<ContentAsset>

    suspend fun deleteRecord(assetId: String)
}
