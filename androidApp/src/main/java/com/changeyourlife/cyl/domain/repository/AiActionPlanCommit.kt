package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.aicontract.AiActionWire
import com.changeyourlife.cyl.domain.model.Page

enum class AiActionPlanPageOperation {
    Upsert,
    PermanentDelete,
}

data class AiActionPlanPageMutation(
    val operation: AiActionPlanPageOperation,
    val pageId: String,
    val expectedRevision: Long,
    val page: Page? = null,
)

data class AiActionPlanCommit(
    val idempotencyKey: String,
    val auditId: String,
    val workspaceId: String,
    val actions: List<AiActionWire>,
    val mutations: List<AiActionPlanPageMutation>,
)

sealed interface AiActionPlanRemoteCommitResult {
    data class Committed(
        val replayed: Boolean,
        val pages: List<Page>,
        val permanentlyDeletedPageIds: List<String>,
    ) : AiActionPlanRemoteCommitResult

    data object NotSupported : AiActionPlanRemoteCommitResult
}
