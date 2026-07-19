package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.AiAppliedActionDao
import com.changeyourlife.cyl.data.local.entity.AiAppliedActionEntity
import com.changeyourlife.cyl.domain.repository.AiAppliedActionClaimResult
import com.changeyourlife.cyl.domain.repository.AiAppliedActionLedgerRepository
import com.changeyourlife.cyl.domain.repository.AiAppliedActionRecord
import com.changeyourlife.cyl.domain.repository.AiAppliedActionState
import javax.inject.Inject

class AiAppliedActionLedgerRepositoryImpl @Inject constructor(
    private val dao: AiAppliedActionDao,
) : AiAppliedActionLedgerRepository {
    override suspend fun claim(record: AiAppliedActionRecord): AiAppliedActionClaimResult {
        val inserted = dao.insertIfAbsent(record.toEntity()) != IgnoredInsertRowId
        if (inserted) return AiAppliedActionClaimResult.Acquired

        val existing = dao.getByIdempotencyKey(record.idempotencyKey)
            ?: dao.getByRequestAction(record.requestMessageId, record.actionIndex)
        val existingRecord = existing?.toDomain()
        return if (
            existingRecord != null &&
            existingRecord.idempotencyKey == record.idempotencyKey &&
            existingRecord.actionFingerprint == record.actionFingerprint
        ) {
            AiAppliedActionClaimResult.Existing(existingRecord)
        } else {
            AiAppliedActionClaimResult.Conflict(existingRecord)
        }
    }

    override suspend fun markApplied(idempotencyKey: String, updatedAt: Long) {
        transition(
            idempotencyKey = idempotencyKey,
            state = AiAppliedActionState.Applied,
            failure = "",
            updatedAt = updatedAt,
        )
    }

    override suspend fun markRejected(idempotencyKey: String, reason: String, updatedAt: Long) {
        transition(
            idempotencyKey = idempotencyKey,
            state = AiAppliedActionState.Rejected,
            failure = reason,
            updatedAt = updatedAt,
        )
    }

    override suspend fun markFailed(idempotencyKey: String, reason: String, updatedAt: Long) {
        transition(
            idempotencyKey = idempotencyKey,
            state = AiAppliedActionState.Failed,
            failure = reason,
            updatedAt = updatedAt,
        )
    }

    private suspend fun transition(
        idempotencyKey: String,
        state: AiAppliedActionState,
        failure: String,
        updatedAt: Long,
    ) {
        dao.transitionClaimed(
            idempotencyKey = idempotencyKey,
            newState = state.wireValue,
            failure = failure.take(MaxFailureLength),
            updatedAt = updatedAt,
            claimedState = AiAppliedActionState.Claimed.wireValue,
        )
    }

    private companion object {
        private const val IgnoredInsertRowId = -1L
        private const val MaxFailureLength = 500
    }
}

private fun AiAppliedActionRecord.toEntity(): AiAppliedActionEntity {
    return AiAppliedActionEntity(
        idempotencyKey = idempotencyKey,
        requestMessageId = requestMessageId,
        workspaceId = workspaceId,
        auditId = auditId,
        actionIndex = actionIndex,
        actionType = actionType,
        actionFingerprint = actionFingerprint,
        state = state.wireValue,
        failure = failure,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun AiAppliedActionEntity.toDomain(): AiAppliedActionRecord {
    return AiAppliedActionRecord(
        idempotencyKey = idempotencyKey,
        requestMessageId = requestMessageId,
        workspaceId = workspaceId,
        auditId = auditId,
        actionIndex = actionIndex,
        actionType = actionType,
        actionFingerprint = actionFingerprint,
        state = AiAppliedActionState.entries.firstOrNull { candidate -> candidate.wireValue == state }
            ?: AiAppliedActionState.Failed,
        failure = failure,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
