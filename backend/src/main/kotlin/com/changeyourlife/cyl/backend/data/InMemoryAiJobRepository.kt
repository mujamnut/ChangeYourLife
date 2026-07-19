package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobClaim
import com.changeyourlife.cyl.backend.domain.AiIdempotencyConflictException
import com.changeyourlife.cyl.backend.domain.AiJobRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryAiJobRepository : AiJobRepository {
    private val jobs = ConcurrentHashMap<String, AiChatActionsJob>()
    private val jobIdsByIdempotencyKey = ConcurrentHashMap<String, String>()
    private val claimLock = Any()

    override suspend fun claim(job: AiChatActionsJob): AiJobClaim = synchronized(claimLock) {
        val lookupKey = job.idempotencyLookupKey()
        val existing = jobIdsByIdempotencyKey[lookupKey]
            ?.let(jobs::get)
        if (existing != null) {
            if (existing.requestFingerprint != job.requestFingerprint) {
                throw AiIdempotencyConflictException()
            }
            AiJobClaim(job = existing, isNew = false)
        } else {
            jobs[job.jobId] = job
            jobIdsByIdempotencyKey[lookupKey] = job.jobId
            AiJobClaim(job = job, isNew = true)
        }
    }

    override suspend fun upsert(job: AiChatActionsJob): AiChatActionsJob {
        jobs[job.jobId] = job
        return job
    }

    override suspend fun get(ownerId: String, jobId: String): AiChatActionsJob? {
        return jobs[jobId]?.takeIf { job -> job.ownerId == ownerId }
    }

    override suspend fun markActiveJobsInterrupted(now: Long, message: String) = Unit

    override suspend fun cleanup(now: Long, ttlMillis: Long, maxRetainedJobs: Int) {
        jobs.values
            .filter { job -> now - job.createdAtEpochMillis > ttlMillis }
            .forEach(::removeJob)
        if (jobs.size <= maxRetainedJobs) return

        jobs.values
            .sortedBy { job -> job.createdAtEpochMillis }
            .take(jobs.size - maxRetainedJobs)
            .forEach(::removeJob)
    }

    private fun removeJob(job: AiChatActionsJob) {
        jobs.remove(job.jobId, job)
        jobIdsByIdempotencyKey.remove(job.idempotencyLookupKey(), job.jobId)
    }
}

private fun AiChatActionsJob.idempotencyLookupKey(): String = "$ownerId:$idempotencyKey"
