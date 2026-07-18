package com.changeyourlife.cyl.data.sync

import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.model.PageContentSnapshot
import com.changeyourlife.cyl.domain.model.PageContentCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageContentConflictMergerTest {
    @Test
    fun mergesDisjointLocalAndRemoteContentChanges() {
        val localPage = page(updatedAt = 1500L, remoteUpdatedAt = 1000L, revision = 6L)
        val remotePage = page(
            updatedAt = 1700L,
            remoteUpdatedAt = 1700L,
            syncStatus = SyncStatus.Synced,
            revision = 7L,
        )
        val localSnapshot = snapshot(
            block = block(text = "local note", updatedAt = 1500L),
            property = property(value = "old", updatedAt = 1000L),
        )
        val remoteSnapshot = snapshot(
            block = block(text = "old note", updatedAt = 1000L),
            property = property(value = "remote date", updatedAt = 1700L),
        )

        val result = PageContentConflictMerger.merge(
            localPage = localPage,
            localSnapshot = localSnapshot,
            remotePage = remotePage,
            remoteSnapshot = remoteSnapshot,
            now = 2000L,
        )
        assertNotNull(result)
        val merged = result!!
        val document = PageContentCodec.decodeDocument(merged.content)

        assertEquals(SyncStatus.PendingPush, merged.syncStatus)
        assertEquals(7L, merged.revision)
        assertEquals(1700L, merged.remoteUpdatedAt)
        assertEquals("local note", document.blocks.single().text)
        assertEquals("remote date", document.properties.single().value)
    }

    @Test
    fun returnsNullWhenSameContentItemChangedLocallyAndRemotely() {
        val localPage = page(updatedAt = 1500L, remoteUpdatedAt = 1000L)
        val remotePage = page(updatedAt = 1700L, remoteUpdatedAt = 1700L, syncStatus = SyncStatus.Synced)

        val result = PageContentConflictMerger.merge(
            localPage = localPage,
            localSnapshot = snapshot(block = block(text = "local note", updatedAt = 1500L)),
            remotePage = remotePage,
            remoteSnapshot = snapshot(block = block(text = "remote note", updatedAt = 1700L)),
            now = 2000L,
        )

        assertNull(result)
    }

    @Test
    fun returnsNullWhenPageTitleChangedLocallyAndRemotely() {
        val result = PageContentConflictMerger.merge(
            localPage = page(title = "Local title", updatedAt = 1500L, remoteUpdatedAt = 1000L),
            localSnapshot = snapshot(block = block(text = "old note", updatedAt = 1000L)),
            remotePage = page(
                title = "Remote title",
                updatedAt = 1700L,
                remoteUpdatedAt = 1700L,
                syncStatus = SyncStatus.Synced,
            ),
            remoteSnapshot = snapshot(block = block(text = "old note", updatedAt = 1000L)),
            now = 2000L,
        )

        assertNull(result)
    }

    private fun page(
        title: String = "Budget",
        updatedAt: Long,
        remoteUpdatedAt: Long,
        syncStatus: String = SyncStatus.PendingPush,
        revision: Long = 0L,
    ): PageEntity {
        return PageEntity(
            id = PageId,
            workspaceId = "workspace-1",
            parentPageId = null,
            title = title,
            content = "",
            sortOrder = 0,
            createdAt = 500L,
            updatedAt = updatedAt,
            deletedAt = null,
            syncStatus = syncStatus,
            remoteUpdatedAt = remoteUpdatedAt,
            lastSyncedAt = 1000L,
            revision = revision,
        )
    }

    private fun snapshot(
        block: PageBlockEntity = block(),
        property: PagePropertyEntity = property(),
    ): PageContentSnapshot {
        return PageContentSnapshot(
            blocks = listOf(block),
            properties = listOf(property),
            tables = emptyList(),
            columns = emptyList(),
            rows = emptyList(),
            cells = emptyList(),
        )
    }

    private fun block(
        text: String = "old note",
        updatedAt: Long = 1000L,
    ): PageBlockEntity {
        return PageBlockEntity(
            id = "block-1",
            pageId = PageId,
            parentBlockId = null,
            type = "Text",
            text = text,
            sortOrder = 0,
            createdAt = 500L,
            updatedAt = updatedAt,
            deletedAt = null,
        )
    }

    private fun property(
        value: String = "old",
        updatedAt: Long = 1000L,
    ): PagePropertyEntity {
        return PagePropertyEntity(
            id = "property-1",
            pageId = PageId,
            name = "Date",
            type = "Text",
            value = value,
            sortOrder = 0,
            createdAt = 500L,
            updatedAt = updatedAt,
            deletedAt = null,
        )
    }

    private companion object {
        const val PageId = "page-1"
    }
}
