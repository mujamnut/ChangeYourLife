package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.AiActionPlanPageOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class AiActionPlanPageMutationTest {
    @Test
    fun builderUsesOriginalRevisionAndCollapsesDeletedPageTree() {
        val root = page(id = "root", title = "Before", revision = 7L)
        val child = page(id = "child", title = "Child", revision = 3L, parentPageId = root.id)
        val grandchild = page(
            id = "grandchild",
            title = "Grandchild",
            revision = 2L,
            parentPageId = child.id,
        )
        val created = page(id = "created", title = "Created", revision = 0L)

        val mutations = buildAiActionPlanPageMutations(
            beforePages = listOf(root, child, grandchild),
            afterPages = listOf(
                root.copy(title = "After", updatedAt = root.updatedAt + 1L),
                created,
            ),
        )

        val rootMutation = requireNotNull(
            mutations.firstOrNull { mutation -> mutation.pageId == root.id },
        )
        assertEquals(AiActionPlanPageOperation.Upsert, rootMutation.operation)
        assertEquals(7L, rootMutation.expectedRevision)
        assertEquals(7L, rootMutation.page?.revision)

        val createdMutation = requireNotNull(
            mutations.firstOrNull { mutation -> mutation.pageId == created.id },
        )
        assertEquals(AiActionPlanPageOperation.Upsert, createdMutation.operation)
        assertEquals(0L, createdMutation.expectedRevision)

        val deleteMutations = mutations.filter { mutation ->
            mutation.operation == AiActionPlanPageOperation.PermanentDelete
        }
        assertEquals(listOf(child.id), deleteMutations.map { mutation -> mutation.pageId })
        assertEquals(child.revision, deleteMutations.single().expectedRevision)
    }

    private fun page(
        id: String,
        title: String,
        revision: Long,
        parentPageId: String? = null,
    ): Page {
        return Page(
            id = id,
            workspaceId = "workspace-1",
            parentPageId = parentPageId,
            title = title,
            content = """{"version":1,"blocks":[]}""",
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 2L,
            deletedAt = null,
            revision = revision,
        )
    }
}
