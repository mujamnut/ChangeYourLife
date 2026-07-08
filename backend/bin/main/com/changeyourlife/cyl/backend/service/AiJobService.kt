package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiJobService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatActionJobs = ConcurrentHashMap<String, AiChatActionsJob>()

    fun createChatActionsJob(
        ownerId: String,
        work: suspend () -> ChatWithActionsResponse,
    ): AiChatActionsJob {
        val now = currentTimeMillis()
        cleanup(now)

        val job = AiChatActionsJob(
            jobId = UUID.randomUUID().toString(),
            ownerId = ownerId,
            status = AiJobStatus.Queued,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        chatActionJobs[job.jobId] = job

        scope.launch {
            updateJob(job.jobId) { existing ->
                existing.copy(
                    status = AiJobStatus.Running,
                    updatedAtEpochMillis = currentTimeMillis(),
                )
            }

            try {
                val result = work()
                updateJob(job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Succeeded,
                        result = result,
                        error = "",
                        updatedAtEpochMillis = currentTimeMillis(),
                    )
                }
            } catch (error: CancellationException) {
                updateJob(job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Failed,
                        error = "AI job was cancelled.",
                        updatedAtEpochMillis = currentTimeMillis(),
                    )
                }
                throw error
            } catch (error: Throwable) {
                updateJob(job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Failed,
                        error = error.toJobMessage(),
                        updatedAtEpochMillis = currentTimeMillis(),
                    )
                }
            }
        }

        return job
    }

    fun getChatActionsJob(ownerId: String, jobId: String): AiChatActionsJob? {
        cleanup(currentTimeMillis())
        return chatActionJobs[jobId]?.takeIf { job -> job.ownerId == ownerId }
    }

    private fun updateJob(jobId: String, update: (AiChatActionsJob) -> AiChatActionsJob) {
        chatActionJobs.computeIfPresent(jobId) { _, existing -> update(existing) }
    }

    private fun cleanup(now: Long) {
        chatActionJobs.entries.removeIf { (_, job) ->
            now - job.createdAtEpochMillis > JobTtlMillis
        }
        if (chatActionJobs.size <= MaxRetainedJobs) return

        chatActionJobs.values
            .sortedBy { job -> job.createdAtEpochMillis }
            .take(chatActionJobs.size - MaxRetainedJobs)
            .forEach { job -> chatActionJobs.remove(job.jobId) }
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun Throwable.toJobMessage(): String {
        val text = message.orEmpty().ifBlank { this::class.simpleName.orEmpty() }
        return text.take(MaxErrorLength).ifBlank { "AI job failed before it could complete." }
    }

    private companion object {
        private const val JobTtlMillis = 30L * 60L * 1000L
        private const val MaxRetainedJobs = 256
        private const val MaxErrorLength = 500
    }
}

data class AiChatActionsJob(
    val jobId: String,
    val ownerId: String,
    val status: AiJobStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val result: ChatWithActionsResponse? = null,
    val error: String = "",
)

enum class AiJobStatus(val wireValue: String) {
    Queued("queued"),
    Running("running"),
    Succeeded("succeeded"),
    Failed("failed"),
}
