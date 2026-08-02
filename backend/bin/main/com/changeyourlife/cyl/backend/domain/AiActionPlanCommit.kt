package com.changeyourlife.cyl.backend.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AiActionPlanPageOperation {
    UPSERT,
    PERMANENT_DELETE,
}

data class AiActionPlanPageMutation(
    val operation: AiActionPlanPageOperation,
    val pageId: String,
    val expectedRevision: Long,
    val page: PageRecord? = null,
)

data class AiActionPlanCommitCommand(
    val idempotencyKey: String,
    val requestFingerprint: String,
    val auditId: String,
    val workspaceId: String,
    val actionCount: Int,
    val mutations: List<AiActionPlanPageMutation>,
    val committedAt: Long,
)

@Serializable
data class AiActionPlanCommitReceipt(
    val auditId: String,
    val workspaceId: String,
    val pages: List<PageRecord>,
    val permanentlyDeletedPageIds: List<String>,
    val actionCount: Int,
    val committedAt: Long,
)

sealed interface AiActionPlanCommitResult {
    data class Committed(
        val receipt: AiActionPlanCommitReceipt,
        val replayed: Boolean,
    ) : AiActionPlanCommitResult

    data class IdempotencyConflict(
        val existingFingerprint: String,
    ) : AiActionPlanCommitResult

    data class RevisionConflict(
        val pageId: String,
        val expectedRevision: Long,
        val currentPage: PageRecord,
    ) : AiActionPlanCommitResult

    data class NotFound(val pageId: String) : AiActionPlanCommitResult

    data class Forbidden(val pageId: String) : AiActionPlanCommitResult

    data class Rejected(
        val code: String,
        val message: String,
    ) : AiActionPlanCommitResult
}
