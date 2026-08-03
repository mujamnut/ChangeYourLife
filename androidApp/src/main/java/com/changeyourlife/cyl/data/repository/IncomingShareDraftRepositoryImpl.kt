package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.IncomingShareDao
import com.changeyourlife.cyl.data.local.entity.IncomingShareDraftEntity
import com.changeyourlife.cyl.data.local.entity.IncomingShareItemEntity
import com.changeyourlife.cyl.domain.model.ContentAssetKind
import com.changeyourlife.cyl.domain.model.IncomingShareDraft
import com.changeyourlife.cyl.domain.model.IncomingShareDraftSeed
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemKind
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class IncomingShareDraftRepositoryImpl @Inject constructor(
    private val dao: IncomingShareDao,
) : IncomingShareDraftRepository {
    override suspend fun createOrGet(seed: IncomingShareDraftSeed): IncomingShareDraft {
        dao.insertDraftWithItems(seed.toEntity(), seed.items.map(IncomingShareItem::toEntity))
        return getByEventId(seed.eventId)
            ?: error("Incoming share draft could not be persisted.")
    }

    override suspend fun get(draftId: String): IncomingShareDraft? {
        val draft = dao.getDraft(draftId) ?: return null
        return draft.toDomain(dao.getItems(draftId).map(IncomingShareItemEntity::toDomain))
    }

    override suspend fun getByEventId(eventId: String): IncomingShareDraft? {
        val draft = dao.getDraftByEventId(eventId) ?: return null
        return draft.toDomain(dao.getItems(draft.id).map(IncomingShareItemEntity::toDomain))
    }

    override fun observe(draftId: String): Flow<IncomingShareDraft?> = combine(
        dao.observeDraftEntity(draftId),
        dao.observeItems(draftId),
    ) { draft, items -> draft?.toDomain(items.map(IncomingShareItemEntity::toDomain)) }

    override suspend fun updateItem(item: IncomingShareItem) {
        dao.updateItem(item.toEntity())
    }

    override suspend fun transitionDraft(
        draftId: String,
        expectedStatuses: Set<IncomingShareDraftStatus>,
        nextStatus: IncomingShareDraftStatus,
        errorCode: String?,
        updatedAt: Long,
    ): Boolean {
        if (expectedStatuses.isEmpty()) return false
        return dao.transitionDraft(
            draftId = draftId,
            expectedStatuses = expectedStatuses.map(IncomingShareDraftStatus::wireValue),
            status = nextStatus.wireValue,
            errorCode = errorCode,
            updatedAt = updatedAt,
        ) > 0
    }

    override suspend fun getPendingDrafts(now: Long): List<IncomingShareDraft> =
        dao.getPendingDrafts(PendingDraftStatuses.map(IncomingShareDraftStatus::wireValue), now)
            .mapNotNull { entity -> get(entity.id) }

    override suspend fun getExpiredDrafts(now: Long, limit: Int): List<IncomingShareDraft> =
        dao.getExpiredDrafts(now, limit).mapNotNull { entity -> get(entity.id) }

    override suspend fun deleteDraft(draftId: String) {
        dao.deleteDraft(draftId)
    }
}

private fun IncomingShareDraftSeed.toEntity() = IncomingShareDraftEntity(
    id = id,
    eventId = eventId,
    action = action,
    subject = subject,
    status = status.wireValue,
    errorCode = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
)

private fun IncomingShareDraftEntity.toDomain(items: List<IncomingShareItem>) = IncomingShareDraft(
    id = id,
    eventId = eventId,
    action = action,
    subject = subject,
    status = IncomingShareDraftStatus.fromWireValue(status),
    items = items,
    errorCode = errorCode,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
)

private fun IncomingShareItem.toEntity() = IncomingShareItemEntity(
    id = id,
    draftId = draftId,
    position = position,
    kind = kind.wireValue,
    sourceUri = sourceUri,
    text = text,
    html = html,
    displayName = displayName,
    declaredMimeType = declaredMimeType,
    stagedPath = stagedPath,
    resolvedMimeType = resolvedMimeType,
    assetKind = assetKind?.wireValue,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    status = status.wireValue,
    errorCode = errorCode,
)

private fun IncomingShareItemEntity.toDomain() = IncomingShareItem(
    id = id,
    draftId = draftId,
    position = position,
    kind = IncomingShareItemKind.fromWireValue(kind),
    sourceUri = sourceUri,
    text = text,
    html = html,
    displayName = displayName,
    declaredMimeType = declaredMimeType,
    stagedPath = stagedPath,
    resolvedMimeType = resolvedMimeType,
    assetKind = assetKind?.let(ContentAssetKind::fromWireValue),
    sizeBytes = sizeBytes,
    sha256 = sha256,
    status = IncomingShareItemStatus.fromWireValue(status),
    errorCode = errorCode,
)

private val PendingDraftStatuses = setOf(
    IncomingShareDraftStatus.RECEIVED,
    IncomingShareDraftStatus.VALIDATING,
    IncomingShareDraftStatus.STAGED,
    IncomingShareDraftStatus.IMPORTING,
)
