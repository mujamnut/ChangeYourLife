package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.sql.PreparedStatement
import java.sql.ResultSet
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
                    SELECT id, user_id, name, created_at, updated_at, deleted_at
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
            val existingOwner = workspaceOwner(workspace.id)
            if (existingOwner != null && existingOwner != workspace.userId) return@withContext null

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO workspaces (id, user_id, name, created_at, updated_at, deleted_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        updated_at = EXCLUDED.updated_at,
                        deleted_at = EXCLUDED.deleted_at
                    RETURNING id, user_id, name, created_at, updated_at, deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, workspace.id)
                    statement.setString(2, workspace.userId)
                    statement.setString(3, workspace.name)
                    statement.setLong(4, workspace.createdAt)
                    statement.setLong(5, workspace.updatedAt)
                    statement.setNullableLong(6, workspace.deletedAt)
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
                    val updated = connection.prepareStatement(
                        """
                        UPDATE workspaces
                        SET deleted_at = ?, updated_at = ?
                        WHERE id = ? AND user_id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, deletedAt)
                        statement.setLong(2, deletedAt)
                        statement.setString(3, workspaceId)
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
                            statement.setString(3, workspaceId)
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
                       pages.workspace_id,
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
                  AND pages.workspace_id = ?
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
                           pages.workspace_id,
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
            if (!workspaceBelongsToUser(userId, page.workspaceId)) return@withContext null
            val existingWorkspaceId = pageWorkspace(page.id)
            if (existingWorkspaceId != null && !workspaceBelongsToUser(userId, existingWorkspaceId)) return@withContext null
            if (page.parentPageId != null) {
                val parentWorkspaceId = pageWorkspace(page.parentPageId) ?: return@withContext null
                if (parentWorkspaceId != page.workspaceId) return@withContext null
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
                    statement.setString(2, page.workspaceId)
                    statement.setString(3, page.parentPageId)
                    statement.setString(4, page.title)
                    statement.setString(5, page.content)
                    statement.setInt(6, page.sortOrder)
                    statement.setLong(7, page.createdAt)
                    statement.setLong(8, page.updatedAt)
                    statement.setNullableLong(9, page.deletedAt)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) resultSet.toPageRecord() else null
                    }
                }
            }
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

    private fun workspaceOwner(workspaceId: String): String? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT user_id FROM workspaces WHERE id = ? LIMIT 1",
            ).use { statement ->
                statement.setString(1, workspaceId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.getString("user_id") else null
                }
            }
        }
    }

    private fun workspaceBelongsToUser(userId: String, workspaceId: String): Boolean {
        return workspaceOwner(workspaceId) == userId
    }

    private fun pageWorkspace(pageId: String): String? {
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

private fun ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}
