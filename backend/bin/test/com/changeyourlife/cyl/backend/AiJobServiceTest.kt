package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.ChatWithActionsResponse
import com.changeyourlife.cyl.backend.service.AiChatActionsJob
import com.changeyourlife.cyl.backend.service.AiJobService
import com.changeyourlife.cyl.backend.service.AiJobStatus
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
            val service = AiJobService()

            val created = service.createChatActionsJob(ownerId = "owner-a") {
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
            val service = AiJobService()

            val created = service.createChatActionsJob(ownerId = "owner-a") {
                ChatWithActionsResponse(reply = "Private")
            }

            assertNull(service.getChatActionsJob(ownerId = "owner-b", jobId = created.jobId))
            assertNotNull(service.getChatActionsJob(ownerId = "owner-a", jobId = created.jobId))
        }
    }

    @Test
    fun chatActionsJobStoresFailureState() {
        runBlocking {
            val service = AiJobService()

            val created = service.createChatActionsJob(ownerId = "owner-a") {
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
