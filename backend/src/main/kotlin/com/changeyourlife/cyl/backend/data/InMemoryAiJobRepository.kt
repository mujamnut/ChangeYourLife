package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryAiJobRepository : AiJobRepository {
    private val jobs = ConcurrentHashMap<String, AiChatActionsJob>()

    override suspend fun upsert(job: AiChatActionsJob): AiChatActionsJob {
        jobs[job.jobId] = job
        return job
    }

    override suspend fun get(ownerId: String, jobId: String): AiChatActionsJob? {
        return jobs[jobId]?.takeIf { job -> job.ownerId == ownerId }
    }

    override suspend fun markActiveJobsInterrupted(now: Long, message: String) = Unit

    override suspend fun cleanup(now: Long, ttlMillis: Long, maxRetainedJobs: Int) {
        jobs.entries.removeIf { (_, job) ->
            now - job.createdAtEpochMillis > ttlMillis
        }
        if (jobs.size <= maxRetainedJobs) return

        jobs.values
            .sortedBy { job -> job.createdAtEpochMillis }
            .take(jobs.size - maxRetainedJobs)
            .forEach { job -> jobs.remove(job.jobId) }
    }
}
