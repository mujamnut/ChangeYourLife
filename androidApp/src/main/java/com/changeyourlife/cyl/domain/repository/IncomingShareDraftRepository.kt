package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.IncomingShareDraft
import com.changeyourlife.cyl.domain.model.IncomingShareDraftSeed
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import kotlinx.coroutines.flow.Flow

interface IncomingShareDraftRepository {
    suspend fun createOrGet(seed: IncomingShareDraftSeed): IncomingShareDraft

    suspend fun get(draftId: String): IncomingShareDraft?

    suspend fun getByEventId(eventId: String): IncomingShareDraft?

    fun observe(draftId: String): Flow<IncomingShareDraft?>

    suspend fun updateItem(item: IncomingShareItem)

    suspend fun transitionDraft(
        draftId: String,
        expectedStatuses: Set<IncomingShareDraftStatus>,
        nextStatus: IncomingShareDraftStatus,
        errorCode: String? = null,
        updatedAt: Long,
    ): Boolean

    suspend fun getPendingDrafts(now: Long): List<IncomingShareDraft>

    suspend fun getExpiredDrafts(now: Long, limit: Int = 100): List<IncomingShareDraft>

    suspend fun deleteDraft(draftId: String)
}
