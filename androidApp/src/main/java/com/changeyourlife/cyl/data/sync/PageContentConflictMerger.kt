package com.changeyourlife.cyl.data.sync

import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.PageTableCellEntity
import com.changeyourlife.cyl.data.local.entity.PageTableColumnEntity
import com.changeyourlife.cyl.data.local.entity.PageTableEntity
import com.changeyourlife.cyl.data.local.entity.PageTableRowEntity
import com.changeyourlife.cyl.data.local.entity.SyncStatus
import com.changeyourlife.cyl.data.local.mapper.toDocument
import com.changeyourlife.cyl.data.local.model.PageContentSnapshot
import com.changeyourlife.cyl.domain.model.PageContentCodec

internal object PageContentConflictMerger {
    fun merge(
        localPage: PageEntity,
        localSnapshot: PageContentSnapshot,
        remotePage: PageEntity,
        remoteSnapshot: PageContentSnapshot,
        now: Long,
    ): PageEntity? {
        val baseUpdatedAt = localPage.remoteUpdatedAt.takeIf { timestamp -> timestamp > 0L }
            ?: return null
        if (!canMergePageShell(localPage, remotePage, baseUpdatedAt)) return null
        if (hasContentConflict(localSnapshot, remoteSnapshot, baseUpdatedAt)) return null

        val mergedSnapshot = PageContentSnapshot(
            blocks = mergeEntities(
                local = localSnapshot.blocks,
                remote = remoteSnapshot.blocks,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { block -> block.id },
                isChanged = { block, base -> block.changedAfter(base) },
            ).sortedWith(compareBy<PageBlockEntity> { block -> block.parentBlockId.orEmpty() }.thenBy { block -> block.sortOrder }),
            properties = mergeEntities(
                local = localSnapshot.properties,
                remote = remoteSnapshot.properties,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { property -> property.id },
                isChanged = { property, base -> property.changedAfter(base) },
            ).sortedBy { property -> property.sortOrder },
            tables = mergeEntities(
                local = localSnapshot.tables,
                remote = remoteSnapshot.tables,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { table -> table.id },
                isChanged = { table, base -> table.changedAfter(base) },
            ),
            columns = mergeEntities(
                local = localSnapshot.columns,
                remote = remoteSnapshot.columns,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { column -> column.id },
                isChanged = { column, base -> column.changedAfter(base) },
            ).sortedWith(compareBy<PageTableColumnEntity> { column -> column.tableId }.thenBy { column -> column.sortOrder }),
            rows = mergeEntities(
                local = localSnapshot.rows,
                remote = remoteSnapshot.rows,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { row -> row.id },
                isChanged = { row, base -> row.changedAfter(base) },
            ).sortedWith(compareBy<PageTableRowEntity> { row -> row.tableId }.thenBy { row -> row.sortOrder }),
            cells = mergeEntities(
                local = localSnapshot.cells,
                remote = remoteSnapshot.cells,
                baseUpdatedAt = baseUpdatedAt,
                keyOf = { cell -> cell.cellKey() },
                isChanged = { cell, base -> cell.changedAfter(base) },
            ).sortedWith(compareBy<PageTableCellEntity> { cell -> cell.rowId }.thenBy { cell -> cell.columnId }),
        )
        val mergedUpdatedAt = maxOf(now, localPage.updatedAt, remotePage.updatedAt)
        return localPage.copy(
            parentPageId = mergeScalar(
                local = localPage.parentPageId,
                remote = remotePage.parentPageId,
                localChanged = localPage.updatedAt > baseUpdatedAt,
                remoteChanged = remotePage.updatedAt > baseUpdatedAt,
            ) ?: localPage.parentPageId,
            title = mergeScalar(
                local = localPage.title,
                remote = remotePage.title,
                localChanged = localPage.updatedAt > baseUpdatedAt,
                remoteChanged = remotePage.updatedAt > baseUpdatedAt,
            ) ?: localPage.title,
            sortOrder = mergeScalar(
                local = localPage.sortOrder,
                remote = remotePage.sortOrder,
                localChanged = localPage.updatedAt > baseUpdatedAt,
                remoteChanged = remotePage.updatedAt > baseUpdatedAt,
            ) ?: localPage.sortOrder,
            deletedAt = mergeScalar(
                local = localPage.deletedAt,
                remote = remotePage.deletedAt,
                localChanged = localPage.changedAfter(baseUpdatedAt),
                remoteChanged = remotePage.changedAfter(baseUpdatedAt),
            ),
            content = PageContentCodec.encodeDocument(mergedSnapshot.toDocument()),
            updatedAt = mergedUpdatedAt,
            syncStatus = SyncStatus.PendingPush,
            remoteUpdatedAt = remotePage.updatedAt,
            lastSyncedAt = now,
        )
    }

    private fun canMergePageShell(
        localPage: PageEntity,
        remotePage: PageEntity,
        baseUpdatedAt: Long,
    ): Boolean {
        val localChanged = localPage.updatedAt > baseUpdatedAt || localPage.changedAfter(baseUpdatedAt)
        val remoteChanged = remotePage.updatedAt > baseUpdatedAt || remotePage.changedAfter(baseUpdatedAt)
        if (!localChanged || !remoteChanged) return true
        return localPage.parentPageId == remotePage.parentPageId &&
            localPage.title == remotePage.title &&
            localPage.sortOrder == remotePage.sortOrder &&
            localPage.deletedAt == remotePage.deletedAt
    }

    private fun hasContentConflict(
        local: PageContentSnapshot,
        remote: PageContentSnapshot,
        baseUpdatedAt: Long,
    ): Boolean {
        return hasEntityConflict(local.blocks, remote.blocks, baseUpdatedAt, { block -> block.id }, { block, base -> block.changedAfter(base) }, { block -> block.createdAt }) ||
            hasEntityConflict(local.properties, remote.properties, baseUpdatedAt, { property -> property.id }, { property, base -> property.changedAfter(base) }, { property -> property.createdAt }) ||
            hasEntityConflict(local.tables, remote.tables, baseUpdatedAt, { table -> table.id }, { table, base -> table.changedAfter(base) }, { table -> table.createdAt }) ||
            hasEntityConflict(local.columns, remote.columns, baseUpdatedAt, { column -> column.id }, { column, base -> column.changedAfter(base) }, { column -> column.createdAt }) ||
            hasEntityConflict(local.rows, remote.rows, baseUpdatedAt, { row -> row.id }, { row, base -> row.changedAfter(base) }, { row -> row.createdAt }) ||
            hasEntityConflict(local.cells, remote.cells, baseUpdatedAt, { cell -> cell.cellKey() }, { cell, base -> cell.changedAfter(base) }, { cell -> cell.createdAt })
    }

    private fun <T> hasEntityConflict(
        local: List<T>,
        remote: List<T>,
        baseUpdatedAt: Long,
        keyOf: (T) -> String,
        isChanged: (T, Long) -> Boolean,
        createdAt: (T) -> Long,
    ): Boolean {
        val localByKey = local.associateBy(keyOf)
        val remoteByKey = remote.associateBy(keyOf)
        val localChangedKeys = local.filter { item -> isChanged(item, baseUpdatedAt) }.map(keyOf).toSet()
        val remoteChangedKeys = remote.filter { item -> isChanged(item, baseUpdatedAt) }.map(keyOf).toSet()
        if (localChangedKeys.intersect(remoteChangedKeys).isNotEmpty()) return true

        val localChangedExistingMissingRemotely = localChangedKeys.any { key ->
            val item = localByKey[key] ?: return@any false
            createdAt(item) <= baseUpdatedAt && remoteByKey[key] == null
        }
        if (localChangedExistingMissingRemotely) return true

        return remoteChangedKeys.any { key ->
            val item = remoteByKey[key] ?: return@any false
            createdAt(item) <= baseUpdatedAt && localByKey[key] == null
        }
    }

    private fun <T> mergeEntities(
        local: List<T>,
        remote: List<T>,
        baseUpdatedAt: Long,
        keyOf: (T) -> String,
        isChanged: (T, Long) -> Boolean,
    ): List<T> {
        val merged = remote.associateBy(keyOf).toMutableMap()
        local
            .filter { item -> isChanged(item, baseUpdatedAt) }
            .forEach { item -> merged[keyOf(item)] = item }
        return merged.values.toList()
    }

    private fun <T> mergeScalar(
        local: T,
        remote: T,
        localChanged: Boolean,
        remoteChanged: Boolean,
    ): T? {
        if (local == remote) return local
        return when {
            localChanged && !remoteChanged -> local
            !localChanged && remoteChanged -> remote
            else -> null
        }
    }

    private fun PageEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageBlockEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PagePropertyEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageTableEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageTableColumnEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageTableRowEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageTableCellEntity.changedAfter(baseUpdatedAt: Long): Boolean {
        return updatedAt > baseUpdatedAt || deletedAt?.let { timestamp -> timestamp > baseUpdatedAt } == true
    }

    private fun PageTableCellEntity.cellKey(): String {
        return "$rowId::$columnId"
    }
}
