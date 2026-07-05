package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostgresContentRepository(
    private val dataSource: DataSource,
) : ContentRepository {
    override suspend fun listWorkspaces(userId: String, includeDeleted: Boolean): List<WorkspaceRecord> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT COALESCE(client_id, id) AS id,
                           user_id,
                           name,
                           created_at,
                           updated_at,
                           deleted_at
                    FROM workspaces
                    WHERE user_id = ?
                      AND (? OR deleted_at IS NULL)
                    ORDER BY updated_at DESC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.setBoolean(2, includeDeleted)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toWorkspaceRecord())
                        }
                    }
                }
            }
        }

    override suspend fun upsertWorkspace(workspace: WorkspaceRecord): WorkspaceRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO workspaces (id, user_id, client_id, name, created_at, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, client_id) DO UPDATE SET
                        name = EXCLUDED.name,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at
                    RETURNING COALESCE(client_id, id) AS id,
                              user_id,
                              name,
                              created_at,
                              updated_at,
                              deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, workspace.userId)
                    statement.setString(3, workspace.id)
                    statement.setString(4, workspace.name)
                    statement.setLong(5, workspace.createdAt)
                    statement.setLong(6, workspace.updatedAt)
                    statement.setNullableLong(7, workspace.deletedAt)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toWorkspaceRecord() else null
                    }
                }
            }
        }

    override suspend fun softDeleteWorkspace(userId: String, workspaceId: String, deletedAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val serverWorkspaceId = connection.resolveWorkspaceServerId(userId, workspaceId)
                        ?: return@withContext false
                    val updated = connection.prepareStatement(
                        """
                        UPDATE workspaces
                        SET deleted_at = ?, updated_at = ?
                        WHERE id = ? AND user_id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, deletedAt)
                        statement.setLong(2, deletedAt)
                        statement.setString(3, serverWorkspaceId)
                        statement.setString(4, userId)
                        statement.executeUpdate()
                    }
                    if (updated > 0) {
                        connection.prepareStatement(
                            """
                            UPDATE pages
                            SET deleted_at = ?, updated_at = ?
                            WHERE workspace_id = ?
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setLong(1, deletedAt)
                            statement.setLong(2, deletedAt)
                            statement.setString(3, serverWorkspaceId)
                            statement.executeUpdate()
                        }
                    }
                    connection.commit()
                    updated > 0
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                }
            }
        }

    override suspend fun listPages(
        userId: String,
        workspaceId: String,
        includeDeleted: Boolean,
    ): List<PageRecord> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT pages.id,
                       COALESCE(workspaces.client_id, workspaces.id) AS workspace_id,
                       pages.parent_page_id,
                       pages.title,
                       pages.content,
                       pages.sort_order,
                       pages.created_at,
                       pages.updated_at,
                       pages.deleted_at
                FROM pages
                INNER JOIN workspaces ON workspaces.id = pages.workspace_id
                WHERE workspaces.user_id = ?
                  AND COALESCE(workspaces.client_id, workspaces.id) = ?
                  AND (? OR pages.deleted_at IS NULL)
                ORDER BY pages.sort_order ASC, pages.updated_at DESC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, workspaceId)
                statement.setBoolean(3, includeDeleted)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toPageRecord())
                    }
                }
            }
        }
    }

    override suspend fun getPage(userId: String, pageId: String, includeDeleted: Boolean): PageRecord? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT pages.id,
                           COALESCE(workspaces.client_id, workspaces.id) AS workspace_id,
                           pages.parent_page_id,
                           pages.title,
                           pages.content,
                           pages.sort_order,
                           pages.created_at,
                           pages.updated_at,
                           pages.deleted_at
                    FROM pages
                    INNER JOIN workspaces ON workspaces.id = pages.workspace_id
                    WHERE workspaces.user_id = ?
                      AND pages.id = ?
                      AND (? OR pages.deleted_at IS NULL)
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.setString(2, pageId)
                    statement.setBoolean(3, includeDeleted)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toPageRecord() else null
                    }
                }
            }
        }

    override suspend fun upsertPage(userId: String, page: PageRecord): PageRecord? =
        withContext(Dispatchers.IO) {
            val serverWorkspaceId = resolveWorkspaceServerId(userId, page.workspaceId) ?: return@withContext null
            val existingWorkspaceId = pageWorkspaceServerId(page.id)
            if (existingWorkspaceId != null && !workspaceServerIdBelongsToUser(userId, existingWorkspaceId)) return@withContext null
            if (page.parentPageId != null) {
                val parentWorkspaceId = pageWorkspaceServerId(page.parentPageId) ?: return@withContext null
                if (parentWorkspaceId != serverWorkspaceId) return@withContext null
            }

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO pages (
                        id, workspace_id, parent_page_id, title, content, sort_order,
                        created_at, updated_at, deleted_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        workspace_id = EXCLUDED.workspace_id,
                        parent_page_id = EXCLUDED.parent_page_id,
                        title = EXCLUDED.title,
                        content = EXCLUDED.content,
                        sort_order = EXCLUDED.sort_order,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at
                    RETURNING id, workspace_id, parent_page_id, title, content, sort_order,
                              created_at, updated_at, deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, page.id)
                    statement.setString(2, serverWorkspaceId)
                    statement.setString(3, page.parentPageId)
                    statement.setString(4, page.title)
                    statement.setString(5, page.content)
                    statement.setInt(6, page.sortOrder)
                    statement.setLong(7, page.createdAt)
                    statement.setLong(8, page.updatedAt)
                    statement.setNullableLong(9, page.deletedAt)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toPageRecord(workspaceId = page.workspaceId) else null
                    }
                }
            }
        }

    override suspend fun updatePageBlockText(
        userId: String,
        pageId: String,
        blockId: String,
        text: String,
        updatedAt: Long,
    ): PageRecord? = updatePageContent(userId, pageId, updatedAt) { content ->
        PageContentJsonMutator.updateBlockText(
            content = content,
            blockId = blockId,
            text = text,
        )
    }

    override suspend fun updatePagePropertyValue(
        userId: String,
        pageId: String,
        propertyId: String,
        propertyName: String,
        value: String,
        updatedAt: Long,
    ): PageRecord? = updatePageContent(userId, pageId, updatedAt) { content ->
        PageContentJsonMutator.updatePropertyValue(
            content = content,
            propertyId = propertyId,
            propertyName = propertyName,
            value = value,
        )
    }

    override suspend fun updatePageTableCellValue(
        userId: String,
        pageId: String,
        rowId: String,
        columnId: String,
        value: String,
        valueJson: kotlinx.serialization.json.JsonObject?,
        updatedAt: Long,
    ): PageRecord? = updatePageContent(userId, pageId, updatedAt) { content ->
        PageContentJsonMutator.updateTableCellValue(
            content = content,
            rowId = rowId,
            columnId = columnId,
            value = value,
            valueJson = valueJson,
        )
    }

    override suspend fun softDeletePage(userId: String, pageId: String, deletedAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            updatePageTreeDeletion(
                userId = userId,
                pageId = pageId,
                deletedAt = deletedAt,
                restoredAt = null,
            )
        }

    override suspend fun restorePage(userId: String, pageId: String, restoredAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            updatePageTreeDeletion(
                userId = userId,
                pageId = pageId,
                deletedAt = null,
                restoredAt = restoredAt,
            )
        }

    override suspend fun deletePagePermanently(userId: String, pageId: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    DELETE FROM pages
                    WHERE (id = ? OR parent_page_id = ?)
                      AND workspace_id IN (
                          SELECT id FROM workspaces WHERE user_id = ?
                      )
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, pageId)
                    statement.setString(2, pageId)
                    statement.setString(3, userId)
                    statement.executeUpdate() > 0
                }
            }
        }

    private fun updatePageTreeDeletion(
        userId: String,
        pageId: String,
        deletedAt: Long?,
        restoredAt: Long?,
    ): Boolean {
        val updatedAt = restoredAt ?: deletedAt ?: System.currentTimeMillis()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE pages
                SET deleted_at = ?, updated_at = ?
                WHERE (id = ? OR parent_page_id = ?)
                  AND workspace_id IN (
                      SELECT id FROM workspaces WHERE user_id = ?
                  )
                """.trimIndent(),
            ).use { statement ->
                statement.setNullableLong(1, deletedAt)
                statement.setLong(2, updatedAt)
                statement.setString(3, pageId)
                statement.setString(4, pageId)
                statement.setString(5, userId)
                return statement.executeUpdate() > 0
            }
        }
    }

    private fun resolveWorkspaceServerId(userId: String, workspaceId: String): String? {
        dataSource.connection.use { connection ->
            return connection.resolveWorkspaceServerId(userId, workspaceId)
        }
    }

    private suspend fun updatePageContent(
        userId: String,
        pageId: String,
        updatedAt: Long,
        transform: (String) -> String?,
    ): PageRecord? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val currentContent = connection.selectOwnedPageContent(
                userId = userId,
                pageId = pageId,
            ) ?: return@withContext null
            val updatedContent = transform(currentContent) ?: return@withContext null

            connection.prepareStatement(
                """
                WITH updated AS (
                    UPDATE pages
                    SET content = ?, updated_at = ?
                    WHERE id = ?
                      AND deleted_at IS NULL
                      AND workspace_id IN (
                          SELECT id FROM workspaces WHERE user_id = ?
                      )
                    RETURNING *
                )
                SELECT updated.id,
                       COALESCE(workspaces.client_id, workspaces.id) AS workspace_id,
                       updated.parent_page_id,
                       updated.title,
                       updated.content,
                       updated.sort_order,
                       updated.created_at,
                       updated.updated_at,
                       updated.deleted_at
                FROM updated
                INNER JOIN workspaces ON workspaces.id = updated.workspace_id
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, updatedContent)
                statement.setLong(2, updatedAt)
                statement.setString(3, pageId)
                statement.setString(4, userId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toPageRecord() else null
                }
            }
        }
    }

    private fun workspaceServerIdBelongsToUser(userId: String, workspaceServerId: String): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT 1 FROM workspaces WHERE id = ? AND user_id = ? LIMIT 1",
            ).use { statement ->
                statement.setString(1, workspaceServerId)
                statement.setString(2, userId)
                statement.executeQuery().use { resultSet ->
                    return resultSet.next()
                }
            }
        }
    }

    private fun pageWorkspaceServerId(pageId: String): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT workspace_id FROM pages WHERE id = ? LIMIT 1",
            ).use { statement ->
                statement.setString(1, pageId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.getString("workspace_id") else null
                }
            }
        }
    }
}

private fun java.sql.Connection.resolveWorkspaceServerId(userId: String, workspaceId: String): String? {
    prepareStatement(
        """
        SELECT id
        FROM workspaces
        WHERE user_id = ?
          AND COALESCE(client_id, id) = ?
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, userId)
        statement.setString(2, workspaceId)
        statement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.getString("id") else null
        }
    }
}

private fun java.sql.Connection.selectOwnedPageContent(userId: String, pageId: String): String? {
    prepareStatement(
        """
        SELECT pages.content
        FROM pages
        INNER JOIN workspaces ON workspaces.id = pages.workspace_id
        WHERE workspaces.user_id = ?
          AND pages.id = ?
          AND pages.deleted_at IS NULL
        LIMIT 1
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, userId)
        statement.setString(2, pageId)
        statement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.getString("content") else null
        }
    }
}

private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setObject(index, null)
    } else {
        setLong(index, value)
    }
}

private fun ResultSet.toWorkspaceRecord(): WorkspaceRecord {
    return WorkspaceRecord(
        id = getString("id"),
        userId = getString("user_id"),
        name = getString("name"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getNullableLong("deleted_at"),
    )
}

private fun ResultSet.toPageRecord(): PageRecord {
    return PageRecord(
        id = getString("id"),
        workspaceId = getString("workspace_id"),
        parentPageId = getString("parent_page_id"),
        title = getString("title"),
        content = getString("content"),
        sortOrder = getInt("sort_order"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getNullableLong("deleted_at"),
    )
}

private fun ResultSet.toPageRecord(workspaceId: String): PageRecord {
    return PageRecord(
        id = getString("id"),
        workspaceId = workspaceId,
        parentPageId = getString("parent_page_id"),
        title = getString("title"),
        content = getString("content"),
        sortOrder = getInt("sort_order"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getNullableLong("deleted_at"),
    )
}

private fun ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}
