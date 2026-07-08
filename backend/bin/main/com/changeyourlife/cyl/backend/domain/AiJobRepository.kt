package com.changeyourlife.cyl.backend.domain

import com.changeyourlife.cyl.backend.model.ai.AiDiagnostics
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse

interface AiJobRepository {
    suspend fun upsert(job: AiChatActionsJob): AiChatActionsJob
    suspend fun get(ownerId: String, jobId: String): AiChatActionsJob?
    suspend fun markActiveJobsInterrupted(now: Long, message: String)
    suspend fun cleanup(now: Long, ttlMillis: Long, maxRetainedJobs: Int)
}

data class AiChatActionsJob(
    val jobId: String,
    val ownerId: String,
    val status: AiJobStatus,
    val phase: String = AiJobPhases.Queued,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val result: ChatWithActionsResponse? = null,
    val error: String = "",
    val diagnostics: AiDiagnostics = AiDiagnostics(),
)

enum class AiJobStatus(val wireValue: String) {
    Queued("queued"),
    Running("running"),
    Succeeded("succeeded"),
    Failed("failed"),
}

object AiJobPhases {
    const val Queued = "queued"
    const val Running = "running"
    const val VisionProcessing = "vision_processing"
    const val Planning = "planning"
    const val ExecutingAction = "executing_action"
    const val Succeeded = "succeeded"
    const val Failed = "failed"
}
