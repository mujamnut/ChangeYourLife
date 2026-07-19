package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.data.InMemoryAiJobRepository
import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobPhases
import com.changeyourlife.cyl.backend.domain.AiJobStatus
import com.changeyourlife.cyl.backend.domain.AiIdempotencyConflictException
import com.changeyourlife.cyl.backend.model.ai.AiDiagnostics
import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.service.AiJobService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AiJobServiceTest {
    @Test
    fun chatActionsJobCompletesWithResult() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())

            val created = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-completes",
                requestFingerprint = "fingerprint-completes",
            ) { _ ->
                ChatWithActionsResponse(reply = "Done")
            }

            val completed = service.awaitJob(ownerId = "owner-a", jobId = created.jobId) { job ->
                job.status == AiJobStatus.Succeeded
            }

            assertEquals(AiJobStatus.Succeeded, completed.status)
            assertEquals("Done", completed.result?.reply)
            assertEquals("", completed.error)
        }
    }

    @Test
    fun chatActionsJobIsOwnerScoped() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())

            val created = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-owner-scope",
                requestFingerprint = "fingerprint-owner-scope",
            ) { _ ->
                ChatWithActionsResponse(reply = "Private")
            }

            assertNull(service.getChatActionsJob(ownerId = "owner-b", jobId = created.jobId))
            assertNotNull(service.getChatActionsJob(ownerId = "owner-a", jobId = created.jobId))
        }
    }

    @Test
    fun chatActionsJobStoresFailureState() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())

            val created = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-failure",
                requestFingerprint = "fingerprint-failure",
            ) { _ ->
                error("Provider failed")
            }

            val failed = service.awaitJob(ownerId = "owner-a", jobId = created.jobId) { job ->
                job.status == AiJobStatus.Failed
            }

            assertEquals(AiJobStatus.Failed, failed.status)
            assertTrue(failed.error.contains("Provider failed"), failed.error)
            assertNull(failed.result)
        }
    }

    @Test
    fun chatActionsJobPersistsProgressPhaseAndDiagnostics() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())

            val created = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-progress",
                requestFingerprint = "fingerprint-progress",
                diagnostics = AiDiagnostics(imageCount = 1, visionAttempted = true),
            ) { progress ->
                progress(
                    AiJobPhases.VisionProcessing,
                    AiDiagnostics(
                        phase = AiJobPhases.VisionProcessing,
                        imageCount = 1,
                        visionAttempted = true,
                        visionProvider = "lmstudio",
                        visionModel = "qwen/qwen3.5-9b",
                    ),
                )
                delay(80)
                ChatWithActionsResponse(
                    reply = "Done",
                    diagnostics = AiDiagnostics(
                        imageCount = 1,
                        visionAttempted = true,
                        visionProvider = "lmstudio",
                        visionModel = "qwen/qwen3.5-9b",
                        visionStatus = "succeeded",
                    ),
                )
            }

            val inProgress = service.awaitJob(ownerId = "owner-a", jobId = created.jobId) { job ->
                job.phase == AiJobPhases.VisionProcessing
            }
            assertEquals(AiJobStatus.Running, inProgress.status)
            assertEquals("lmstudio", inProgress.diagnostics.visionProvider)

            val completed = service.awaitJob(ownerId = "owner-a", jobId = created.jobId) { job ->
                job.status == AiJobStatus.Succeeded
            }
            assertEquals(AiJobPhases.Succeeded, completed.phase)
            assertEquals("succeeded", completed.diagnostics.visionStatus)
        }
    }

    @Test
    fun duplicateIdempotencyKeyReturnsExistingJobAndRunsWorkOnce() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())
            var invocationCount = 0

            val first = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-idempotent",
                requestFingerprint = "fingerprint-idempotent",
            ) { _ ->
                invocationCount += 1
                delay(50)
                ChatWithActionsResponse(reply = "Done")
            }
            val replay = service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-idempotent",
                requestFingerprint = "fingerprint-idempotent",
            ) { _ ->
                invocationCount += 1
                ChatWithActionsResponse(reply = "Duplicate")
            }

            assertEquals(first.jobId, replay.jobId)
            service.awaitJob(ownerId = "owner-a", jobId = first.jobId) { job ->
                job.status == AiJobStatus.Succeeded
            }
            assertEquals(1, invocationCount)
        }
    }

    @Test
    fun duplicateIdempotencyKeyRejectsDifferentRequestFingerprint() {
        runBlocking {
            val service = AiJobService(InMemoryAiJobRepository())
            service.createChatActionsJob(
                ownerId = "owner-a",
                idempotencyKey = "request-conflict",
                requestFingerprint = "fingerprint-a",
            ) { _ ->
                ChatWithActionsResponse(reply = "First")
            }

            val error = runCatching {
                service.createChatActionsJob(
                    ownerId = "owner-a",
                    idempotencyKey = "request-conflict",
                    requestFingerprint = "fingerprint-b",
                ) { _ ->
                    ChatWithActionsResponse(reply = "Second")
                }
            }.exceptionOrNull()

            assertTrue(error is AiIdempotencyConflictException)
        }
    }

    private suspend fun AiJobService.awaitJob(
        ownerId: String,
        jobId: String,
        predicate: (AiChatActionsJob) -> Boolean,
    ): AiChatActionsJob {
        repeat(50) {
            val job = getChatActionsJob(ownerId = ownerId, jobId = jobId)
            if (job != null && predicate(job)) return job
            delay(20)
        }
        fail("Timed out waiting for AI job $jobId.")
    }
}
