package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobPhases
import com.changeyourlife.cyl.backend.domain.AiJobRepository
import com.changeyourlife.cyl.backend.domain.AiJobStatus
import com.changeyourlife.cyl.backend.model.ai.AiDiagnostics
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

typealias AiJobProgressSink = suspend (phase: String, diagnostics: AiDiagnostics?) -> Unit

class AiJobService(
    private val repository: AiJobRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            repository.markActiveJobsInterrupted(
                now = currentTimeMillis(),
                message = InterruptedJobMessage,
            )
            cleanup(currentTimeMillis())
        }
    }

    suspend fun createChatActionsJob(
        ownerId: String,
        diagnostics: AiDiagnostics = AiDiagnostics(),
        work: suspend (AiJobProgressSink) -> ChatWithActionsResponse,
    ): AiChatActionsJob {
        val now = currentTimeMillis()
        cleanup(now)

        val job = repository.upsert(
            AiChatActionsJob(
                jobId = UUID.randomUUID().toString(),
                ownerId = ownerId,
                status = AiJobStatus.Queued,
                phase = AiJobPhases.Queued,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                diagnostics = diagnostics.copy(phase = AiJobPhases.Queued),
            ),
        )

        scope.launch {
            updateJob(ownerId = ownerId, jobId = job.jobId) { existing ->
                existing.copy(
                    status = AiJobStatus.Running,
                    phase = AiJobPhases.Running,
                    updatedAtEpochMillis = currentTimeMillis(),
                    diagnostics = existing.diagnostics.copy(phase = AiJobPhases.Running),
                )
            }

            val progress: AiJobProgressSink = { phase, progressDiagnostics ->
                updateJob(ownerId = ownerId, jobId = job.jobId) { existing ->
                    val diagnosticsToStore = (progressDiagnostics ?: existing.diagnostics).copy(phase = phase)
                    existing.copy(
                        status = AiJobStatus.Running,
                        phase = phase,
                        diagnostics = diagnosticsToStore,
                        updatedAtEpochMillis = currentTimeMillis(),
                    )
                }
            }

            try {
                val result = work(progress)
                updateJob(ownerId = ownerId, jobId = job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Succeeded,
                        phase = AiJobPhases.Succeeded,
                        result = result,
                        error = "",
                        updatedAtEpochMillis = currentTimeMillis(),
                        diagnostics = result.diagnostics.copy(phase = AiJobPhases.Succeeded),
                    )
                }
            } catch (error: CancellationException) {
                updateJob(ownerId = ownerId, jobId = job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Failed,
                        phase = AiJobPhases.Failed,
                        error = "AI job was cancelled.",
                        updatedAtEpochMillis = currentTimeMillis(),
                        diagnostics = existing.diagnostics.copy(
                            phase = AiJobPhases.Failed,
                            warning = "AI job was cancelled.",
                        ),
                    )
                }
                throw error
            } catch (error: Throwable) {
                updateJob(ownerId = ownerId, jobId = job.jobId) { existing ->
                    existing.copy(
                        status = AiJobStatus.Failed,
                        phase = AiJobPhases.Failed,
                        error = error.toJobMessage(),
                        updatedAtEpochMillis = currentTimeMillis(),
                        diagnostics = existing.diagnostics.copy(
                            phase = AiJobPhases.Failed,
                            warning = error.toJobMessage(),
                        ),
                    )
                }
            }
        }

        return job
    }

    suspend fun getChatActionsJob(ownerId: String, jobId: String): AiChatActionsJob? {
        cleanup(currentTimeMillis())
        return repository.get(ownerId = ownerId, jobId = jobId)
    }

    private suspend fun updateJob(
        ownerId: String,
        jobId: String,
        update: (AiChatActionsJob) -> AiChatActionsJob,
    ) {
        val existing = repository.get(ownerId = ownerId, jobId = jobId) ?: return
        repository.upsert(update(existing))
    }

    private suspend fun cleanup(now: Long) {
        repository.cleanup(
            now = now,
            ttlMillis = JobTtlMillis,
            maxRetainedJobs = MaxRetainedJobs,
        )
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun Throwable.toJobMessage(): String {
        val text = message.orEmpty().ifBlank { this::class.simpleName.orEmpty() }
        return text.take(MaxErrorLength).ifBlank { "AI job failed before it could complete." }
    }

    private companion object {
        private const val InterruptedJobMessage =
            "AI backend restarted before this job completed. Please retry the request."
        private const val JobTtlMillis = 24L * 60L * 60L * 1000L
        private const val MaxRetainedJobs = 1000
        private const val MaxErrorLength = 500
    }
}
