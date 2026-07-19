package com.changeyourlife.cyl.domain.repository

data class AiAppliedActionRecord(
    val idempotencyKey: String,
    val requestMessageId: String,
    val workspaceId: String,
    val auditId: String,
    val actionIndex: Int,
    val actionType: String,
    val actionFingerprint: String,
    val state: AiAppliedActionState = AiAppliedActionState.Claimed,
    val failure: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

enum class AiAppliedActionState(val wireValue: String) {
    Claimed("Claimed"),
    Applied("Applied"),
    Rejected("Rejected"),
    Failed("Failed"),
}

sealed interface AiAppliedActionClaimResult {
    data object Acquired : AiAppliedActionClaimResult
    data class Existing(val record: AiAppliedActionRecord) : AiAppliedActionClaimResult
    data class Conflict(val record: AiAppliedActionRecord?) : AiAppliedActionClaimResult
}

interface AiAppliedActionLedgerRepository {
    suspend fun claim(record: AiAppliedActionRecord): AiAppliedActionClaimResult
    suspend fun markApplied(idempotencyKey: String, updatedAt: Long)
    suspend fun markRejected(idempotencyKey: String, reason: String, updatedAt: Long)
    suspend fun markFailed(idempotencyKey: String, reason: String, updatedAt: Long)
}

object NoOpAiAppliedActionLedgerRepository : AiAppliedActionLedgerRepository {
    override suspend fun claim(record: AiAppliedActionRecord): AiAppliedActionClaimResult {
        return AiAppliedActionClaimResult.Acquired
    }

    override suspend fun markApplied(idempotencyKey: String, updatedAt: Long) = Unit

    override suspend fun markRejected(idempotencyKey: String, reason: String, updatedAt: Long) = Unit

    override suspend fun markFailed(idempotencyKey: String, reason: String, updatedAt: Long) = Unit
}
