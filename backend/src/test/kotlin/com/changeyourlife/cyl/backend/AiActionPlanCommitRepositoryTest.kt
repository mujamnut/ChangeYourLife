package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.data.InMemoryContentRepository
import com.changeyourlife.cyl.backend.domain.AiActionPlanCommitCommand
import com.changeyourlife.cyl.backend.domain.AiActionPlanCommitResult
import com.changeyourlife.cyl.backend.domain.AiActionPlanPageMutation
import com.changeyourlife.cyl.backend.domain.AiActionPlanPageOperation
import com.changeyourlife.cyl.backend.domain.PageMutationResult
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AiActionPlanCommitRepositoryTest {
    @Test
    fun retryReplaysReceiptWithoutApplyingPageMutationTwice() = runBlocking {
        val repository = preparedRepository()
        val first = repository.createPage("page-1", "Before one")
        val second = repository.createPage("page-2", "Before two")
        val command = commitCommand(
            idempotencyKey = "request-123",
            fingerprint = "fingerprint-a",
            mutations = listOf(
                first.upsertMutation("After one"),
                second.upsertMutation("After two"),
            ),
        )

        val committed = assertIs<AiActionPlanCommitResult.Committed>(
            repository.commitAiActionPlan(TestUserId, command),
        )
        assertTrue(!committed.replayed)
        assertEquals(listOf(2L, 2L), committed.receipt.pages.map(PageRecord::revision))

        val replayed = assertIs<AiActionPlanCommitResult.Committed>(
            repository.commitAiActionPlan(TestUserId, command),
        )
        assertTrue(replayed.replayed)
        assertEquals(committed.receipt, replayed.receipt)
        assertEquals(2L, repository.getPage(TestUserId, first.id)?.revision)

        assertIs<AiActionPlanCommitResult.IdempotencyConflict>(
            repository.commitAiActionPlan(
                TestUserId,
                command.copy(requestFingerprint = "fingerprint-b"),
            ),
        )
    }

    @Test
    fun staleMutationRejectsWholePlanWithoutPartialWrite() = runBlocking {
        val repository = preparedRepository()
        val first = repository.createPage("page-1", "Before one")
        val second = repository.createPage("page-2", "Before two")
        val result = repository.commitAiActionPlan(
            userId = TestUserId,
            command = commitCommand(
                idempotencyKey = "request-456",
                fingerprint = "fingerprint-c",
                mutations = listOf(
                    first.upsertMutation("Must roll back"),
                    second.upsertMutation("Stale").copy(expectedRevision = second.revision + 1L),
                ),
            ),
        )

        val conflict = assertIs<AiActionPlanCommitResult.RevisionConflict>(result)
        assertEquals(second.id, conflict.pageId)
        assertEquals("Before one", repository.getPage(TestUserId, first.id)?.title)
        assertEquals(first.revision, repository.getPage(TestUserId, first.id)?.revision)
    }

    @Test
    fun upsertCannotTargetParentScheduledForPermanentDelete() = runBlocking {
        val repository = preparedRepository()
        val parent = repository.createPage("parent", "Parent").let { page ->
            repository.upsertPage(
                userId = TestUserId,
                page = page.copy(
                    deletedAt = 20L,
                    updatedAt = 20L,
                    revision = page.revision,
                ),
            ).let { result -> assertIs<PageMutationResult.Applied>(result).page }
        }
        val child = repository.createPage("child", "Child")
        val result = repository.commitAiActionPlan(
            userId = TestUserId,
            command = commitCommand(
                idempotencyKey = "request-parent-delete",
                fingerprint = "fingerprint-parent-delete",
                mutations = listOf(
                    AiActionPlanPageMutation(
                        operation = AiActionPlanPageOperation.PERMANENT_DELETE,
                        pageId = parent.id,
                        expectedRevision = parent.revision,
                    ),
                    child.upsertMutation("Child after").copy(
                        page = child.copy(
                            parentPageId = parent.id,
                            title = "Child after",
                        ),
                    ),
                ),
            ),
        )

        val rejected = assertIs<AiActionPlanCommitResult.Rejected>(result)
        assertEquals("parent_scheduled_for_deletion", rejected.code)
        assertEquals("Child", repository.getPage(TestUserId, child.id)?.title)
        assertEquals(parent.revision, repository.getPage(TestUserId, parent.id)?.revision)
    }

    private suspend fun preparedRepository(): InMemoryContentRepository {
        return InMemoryContentRepository().also { repository ->
            repository.upsertWorkspace(
                WorkspaceRecord(
                    id = TestWorkspaceId,
                    userId = TestUserId,
                    name = "Test workspace",
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                ),
            )
        }
    }

    private suspend fun InMemoryContentRepository.createPage(
        pageId: String,
        title: String,
    ): PageRecord {
        val result = upsertPage(
            userId = TestUserId,
            page = PageRecord(
                id = pageId,
                workspaceId = TestWorkspaceId,
                parentPageId = null,
                title = title,
                content = """{"version":1,"blocks":[]}""",
                sortOrder = 0,
                createdAt = 10L,
                updatedAt = 10L,
                deletedAt = null,
            ),
        )
        return assertIs<PageMutationResult.Applied>(result).page
    }

    private fun PageRecord.upsertMutation(title: String): AiActionPlanPageMutation {
        return AiActionPlanPageMutation(
            operation = AiActionPlanPageOperation.UPSERT,
            pageId = id,
            expectedRevision = revision,
            page = copy(title = title, updatedAt = updatedAt + 10L),
        )
    }

    private fun commitCommand(
        idempotencyKey: String,
        fingerprint: String,
        mutations: List<AiActionPlanPageMutation>,
    ): AiActionPlanCommitCommand {
        return AiActionPlanCommitCommand(
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
            auditId = "audit-$idempotencyKey",
            workspaceId = TestWorkspaceId,
            actionCount = mutations.size,
            mutations = mutations,
            committedAt = 100L,
        )
    }

    private companion object {
        const val TestUserId = "user-1"
        const val TestWorkspaceId = "workspace-1"
    }
}
