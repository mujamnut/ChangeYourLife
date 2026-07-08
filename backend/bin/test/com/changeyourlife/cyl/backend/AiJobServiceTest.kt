package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.data.InMemoryAiJobRepository
import com.changeyourlife.cyl.backend.domain.AiChatActionsJob
import com.changeyourlife.cyl.backend.domain.AiJobPhases
import com.changeyourlife.cyl.backend.domain.AiJobStatus
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

            val created = service.createChatActionsJob(ownerId = "owner-a") { _ ->
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

            val created = service.createChatActionsJob(ownerId = "owner-a") { _ ->
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

            val created = service.createChatActionsJob(ownerId = "owner-a") { _ ->
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
