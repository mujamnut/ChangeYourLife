package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.PageAssetDao
import com.changeyourlife.cyl.data.local.entity.PageAssetEntity
import com.changeyourlife.cyl.domain.model.ContentAsset
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.ContentAssetStatus
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContentAssetRepositoryImpl @Inject constructor(
    private val dao: PageAssetDao,
) : ContentAssetRepository {
    override fun observeByPage(pageId: String): Flow<List<ContentAsset>> =
        dao.observeByPage(pageId).map { entities -> entities.map(::toDomain) }

    override suspend fun getById(assetId: String): ContentAsset? =
        dao.getById(assetId)?.let(::toDomain)

    override suspend fun upsert(asset: ContentAsset) {
        dao.upsert(asset.toEntity())
    }

    override suspend fun transitionStatus(
        assetId: String,
        expectedStatuses: Set<ContentAssetStatus>,
        nextStatus: ContentAssetStatus,
        progressPercent: Int,
        errorCode: String?,
        remoteAssetId: String?,
        updatedAt: Long,
    ): Boolean {
        if (expectedStatuses.isEmpty()) return false
        return dao.transitionStatus(
            assetId = assetId,
            expectedStatuses = expectedStatuses.map(ContentAssetStatus::wireValue),
            nextStatus = nextStatus.wireValue,
            progressPercent = progressPercent.coerceIn(0, 100),
            errorCode = errorCode,
            remoteAssetId = remoteAssetId,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun markDeleted(assetId: String, deletedAt: Long): Boolean =
        dao.markDeleted(
            assetId = assetId,
            deletedStatus = ContentAssetStatus.DELETED.wireValue,
            deletedAt = deletedAt,
        ) > 0

    override suspend fun getCleanupCandidates(deletedBefore: Long): List<ContentAsset> =
        dao.getCleanupCandidates(deletedBefore).map(::toDomain)

    override suspend fun deleteRecord(assetId: String) {
        dao.deleteById(assetId)
    }

    private fun toDomain(entity: PageAssetEntity): ContentAsset = ContentAsset(
        id = entity.id,
        workspaceId = entity.workspaceId,
        ownerPageId = entity.ownerPageId,
        kind = ContentAssetKind.fromWireValue(entity.kind),
        displayName = entity.displayName,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        sha256 = entity.sha256,
        localPath = entity.localPath,
        remoteAssetId = entity.remoteAssetId,
        status = ContentAssetStatus.fromWireValue(entity.status),
        progressPercent = entity.progressPercent.coerceIn(0, 100),
        errorCode = entity.errorCode,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        deletedAt = entity.deletedAt,
    )

    private fun ContentAsset.toEntity(): PageAssetEntity = PageAssetEntity(
        id = id,
        workspaceId = workspaceId,
        ownerPageId = ownerPageId,
        kind = kind.wireValue,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        localPath = localPath,
        remoteAssetId = remoteAssetId,
        status = status.wireValue,
        progressPercent = progressPercent.coerceIn(0, 100),
        errorCode = errorCode,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
