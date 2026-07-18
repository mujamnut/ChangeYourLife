package com.changeyourlife.cyl.backend.data

import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.ContentSearchQuery
import com.changeyourlife.cyl.backend.domain.ContentSearchResult
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
    private val projectionWriter = PostgresPageContentProjectionWriter()

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

    override suspend fun search(userId: String, query: ContentSearchQuery): List<ContentSearchResult> =
        withContext(Dispatchers.IO) {
            val rawQuery = query.query.trim()
            if (rawQuery.isBlank()) return@withContext emptyList()
            val candidateSql = buildSearchCandidateSql(query.scopes)
            if (candidateSql.isBlank()) return@withContext emptyList()

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    WITH input AS (
                        SELECT plainto_tsquery('simple', ?) AS ts_query,
                               lower(?) AS raw_query
                    ),
                    owned_pages AS (
                        SELECT pages.*,
                               COALESCE(workspaces.client_id, workspaces.id) AS client_workspace_id
                        FROM pages
                        INNER JOIN workspaces ON workspaces.id = pages.workspace_id
                        WHERE workspaces.user_id = ?
                          AND COALESCE(workspaces.client_id, workspaces.id) = ?
                          AND pages.deleted_at IS NULL
                          AND workspaces.deleted_at IS NULL
                    )
                    SELECT *
                    FROM (
                        $candidateSql
                    ) AS candidates
                    ORDER BY score DESC, updated_at DESC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, rawQuery)
                    statement.setString(2, rawQuery.lowercase())
                    statement.setString(3, userId)
                    statement.setString(4, query.workspaceId)
                    statement.setInt(5, query.limit)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) add(resultSet.toContentSearchResult())
                        }
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

            val saved = dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val persisted = connection.prepareStatement(
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
                        WHERE pages.updated_at < EXCLUDED.updated_at
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
                            if (resultSet.next()) {
                                resultSet.toPageRecord(workspaceId = page.workspaceId)
                            } else {
                                null
                            }
                        }
                    }
                    persisted?.let { savedPage ->
                        projectionWriter.replace(
                            connection = connection,
                            source = savedPage.toProjectionSource(),
                        )
                    }
                    connection.commit()
                    persisted
                } catch (error: Throwable) {
                    runCatching { connection.rollback() }
                    throw error
                }
            }
            saved ?: getPage(userId = userId, pageId = page.id, includeDeleted = true)
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
            connection.autoCommit = false
            try {
                val updatedPages = connection.prepareStatement(
                    """
                    UPDATE pages
                    SET deleted_at = ?, updated_at = ?
                    WHERE (id = ? OR parent_page_id = ?)
                      AND workspace_id IN (
                          SELECT id FROM workspaces WHERE user_id = ?
                      )
                    RETURNING id, content, created_at, updated_at, deleted_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setNullableLong(1, deletedAt)
                    statement.setLong(2, updatedAt)
                    statement.setString(3, pageId)
                    statement.setString(4, pageId)
                    statement.setString(5, userId)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.toProjectionSource())
                            }
                        }
                    }
                }
                updatedPages.forEach { source ->
                    projectionWriter.replace(connection, source)
                }
                connection.commit()
                return updatedPages.isNotEmpty()
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
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
            connection.autoCommit = false
            try {
                val currentContent = connection.selectOwnedPageContent(
                    userId = userId,
                    pageId = pageId,
                ) ?: run {
                    connection.rollback()
                    return@withContext null
                }
                val updatedContent = transform(currentContent) ?: run {
                    connection.rollback()
                    return@withContext null
                }
                val saved = connection.prepareStatement(
                    """
                    WITH updated AS (
                        UPDATE pages
                        SET content = ?, updated_at = GREATEST(?, updated_at + 1)
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
                saved?.let { savedPage ->
                    projectionWriter.replace(
                        connection = connection,
                        source = savedPage.toProjectionSource(),
                    )
                }
                connection.commit()
                saved
            } catch (error: Throwable) {
                runCatching { connection.rollback() }
                throw error
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
        FOR UPDATE OF pages
        """.trimIndent(),
    ).use { statement ->
        statement.setString(1, userId)
        statement.setString(2, pageId)
        statement.executeQuery().use { resultSet ->
            return if (resultSet.next()) resultSet.getString("content") else null
        }
    }
}

private val DefaultBackendSearchScopes = setOf("Page", "Block", "Table", "Row", "Property", "Column", "Cell")

private fun buildSearchCandidateSql(scopes: Set<String>): String {
    val requestedScopes = scopes.ifEmpty { DefaultBackendSearchScopes }
    return buildList {
        if (requestedScopes.containsSearchScope("Page")) add(PageSearchCandidateSql)
        if (requestedScopes.containsSearchScope("Block")) add(BlockSearchCandidateSql)
        if (requestedScopes.containsSearchScope("Property")) add(PropertySearchCandidateSql)
        if (requestedScopes.containsSearchScope("Table")) add(TableSearchCandidateSql)
        if (requestedScopes.containsSearchScope("Column")) add(ColumnSearchCandidateSql)
        if (requestedScopes.containsSearchScope("Row")) add(RowSearchCandidateSql)
        if (requestedScopes.containsSearchScope("Cell")) add(CellSearchCandidateSql)
    }.joinToString(separator = "\nUNION ALL\n")
}

private fun Set<String>.containsSearchScope(scope: String): Boolean =
    any { requested -> requested.equals(scope, ignoreCase = true) }

private const val EmptySearchField = "''::text"

private val PageSearchCandidateSql = """
SELECT 'Page'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       $EmptySearchField AS block_id,
       $EmptySearchField AS table_block_id,
       $EmptySearchField AS row_id,
       $EmptySearchField AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       COALESCE(NULLIF(p.title, ''), 'Untitled page') AS title,
       'Page'::text AS subtitle,
       left(regexp_replace(COALESCE(p.content, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', COALESCE(p.title, '') || ' ' || COALESCE(p.content, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(COALESCE(p.title, '')) = input.raw_query THEN 300 ELSE 0 END
           + CASE WHEN lower(COALESCE(p.title, '')) LIKE '%' || input.raw_query || '%' THEN 120 ELSE 0 END
       ) AS score,
       p.updated_at AS updated_at
FROM owned_pages p
CROSS JOIN input
WHERE to_tsvector('simple', COALESCE(p.title, '') || ' ' || COALESCE(p.content, '')) @@ input.ts_query
   OR lower(COALESCE(p.title, '') || ' ' || COALESCE(p.content, '')) LIKE '%' || input.raw_query || '%'
""".trimIndent()

private val BlockSearchCandidateSql = """
SELECT 'Block'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       b.id AS block_id,
       CASE WHEN b.type = 'Table' THEN b.id ELSE $EmptySearchField END AS table_block_id,
       $EmptySearchField AS row_id,
       $EmptySearchField AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       COALESCE(NULLIF(left(b.text, 80), ''), b.type) AS title,
       COALESCE(NULLIF(p.title, ''), 'Untitled page') || ' / Block' AS subtitle,
       left(regexp_replace(COALESCE(b.text, '') || ' ' || COALESCE(b.metadata_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', COALESCE(b.text, '') || ' ' || COALESCE(b.metadata_json, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(COALESCE(b.text, '')) LIKE '%' || input.raw_query || '%' THEN 80 ELSE 0 END
       ) AS score,
       b.updated_at AS updated_at
FROM page_blocks b
INNER JOIN owned_pages p ON p.id = b.page_id
CROSS JOIN input
WHERE b.deleted_at IS NULL
  AND (
      to_tsvector('simple', COALESCE(b.text, '') || ' ' || COALESCE(b.metadata_json, '')) @@ input.ts_query
      OR lower(COALESCE(b.text, '') || ' ' || COALESCE(b.metadata_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

private val PropertySearchCandidateSql = """
SELECT 'Property'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       $EmptySearchField AS block_id,
       $EmptySearchField AS table_block_id,
       $EmptySearchField AS row_id,
       $EmptySearchField AS column_id,
       pr.id AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       pr.name AS title,
       COALESCE(NULLIF(p.title, ''), 'Untitled page') || ' / Property' AS subtitle,
       left(regexp_replace(pr.type || ' ' || COALESCE(pr.value, '') || ' ' || COALESCE(pr.metadata_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', pr.name || ' ' || COALESCE(pr.value, '') || ' ' || COALESCE(pr.metadata_json, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(pr.name) LIKE '%' || input.raw_query || '%' THEN 100 ELSE 0 END
       ) AS score,
       pr.updated_at AS updated_at
FROM page_properties pr
INNER JOIN owned_pages p ON p.id = pr.page_id
CROSS JOIN input
WHERE pr.deleted_at IS NULL
  AND (
      to_tsvector('simple', pr.name || ' ' || COALESCE(pr.value, '') || ' ' || COALESCE(pr.metadata_json, '')) @@ input.ts_query
      OR lower(pr.name || ' ' || COALESCE(pr.value, '') || ' ' || COALESCE(pr.metadata_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

private val TableSearchCandidateSql = """
SELECT 'Table'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       t.block_id AS block_id,
       t.block_id AS table_block_id,
       $EmptySearchField AS row_id,
       $EmptySearchField AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       COALESCE(NULLIF(t.title, ''), 'Table') AS title,
       COALESCE(NULLIF(p.title, ''), 'Untitled page') || ' / Database' AS subtitle,
       left(regexp_replace(t.view || ' ' || COALESCE(t.view_config_json, '') || ' ' || COALESCE(t.sort_json, '') || ' ' || COALESCE(t.filter_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', COALESCE(t.title, '') || ' ' || COALESCE(t.view_config_json, '') || ' ' || COALESCE(t.filter_json, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(COALESCE(t.title, '')) LIKE '%' || input.raw_query || '%' THEN 100 ELSE 0 END
       ) AS score,
       t.updated_at AS updated_at
FROM page_tables t
INNER JOIN owned_pages p ON p.id = t.page_id
CROSS JOIN input
WHERE t.deleted_at IS NULL
  AND (
      to_tsvector('simple', COALESCE(t.title, '') || ' ' || COALESCE(t.view_config_json, '') || ' ' || COALESCE(t.filter_json, '')) @@ input.ts_query
      OR lower(COALESCE(t.title, '') || ' ' || COALESCE(t.view_config_json, '') || ' ' || COALESCE(t.filter_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

private val ColumnSearchCandidateSql = """
SELECT 'Column'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       t.block_id AS block_id,
       t.block_id AS table_block_id,
       $EmptySearchField AS row_id,
       c.id AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       c.name AS title,
       COALESCE(NULLIF(t.title, ''), 'Table') || ' / Column' AS subtitle,
       left(regexp_replace(c.type || ' ' || COALESCE(c.config_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', c.name || ' ' || c.type || ' ' || COALESCE(c.config_json, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(c.name) LIKE '%' || input.raw_query || '%' THEN 100 ELSE 0 END
       ) AS score,
       c.updated_at AS updated_at
FROM page_table_columns c
INNER JOIN page_tables t ON t.id = c.table_id
INNER JOIN owned_pages p ON p.id = t.page_id
CROSS JOIN input
WHERE c.deleted_at IS NULL
  AND t.deleted_at IS NULL
  AND (
      to_tsvector('simple', c.name || ' ' || c.type || ' ' || COALESCE(c.config_json, '')) @@ input.ts_query
      OR lower(c.name || ' ' || c.type || ' ' || COALESCE(c.config_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

private val RowSearchCandidateSql = """
SELECT 'Row'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       t.block_id AS block_id,
       t.block_id AS table_block_id,
       r.id AS row_id,
       $EmptySearchField AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       COALESCE(NULLIF(left(r.metadata_json, 80), ''), 'Row') AS title,
       COALESCE(NULLIF(t.title, ''), 'Table') || ' / Row' AS subtitle,
       left(regexp_replace(COALESCE(r.metadata_json, '') || ' ' || COALESCE(r.content_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (ts_rank_cd(to_tsvector('simple', COALESCE(r.metadata_json, '') || ' ' || COALESCE(r.content_json, '')), input.ts_query) * 1000)::integer AS score,
       r.updated_at AS updated_at
FROM page_table_rows r
INNER JOIN page_tables t ON t.id = r.table_id
INNER JOIN owned_pages p ON p.id = t.page_id
CROSS JOIN input
WHERE r.deleted_at IS NULL
  AND t.deleted_at IS NULL
  AND (
      to_tsvector('simple', COALESCE(r.metadata_json, '') || ' ' || COALESCE(r.content_json, '')) @@ input.ts_query
      OR lower(COALESCE(r.metadata_json, '') || ' ' || COALESCE(r.content_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

private val CellSearchCandidateSql = """
SELECT 'Cell'::text AS target_type,
       p.client_workspace_id AS workspace_id,
       p.id AS page_id,
       t.block_id AS block_id,
       t.block_id AS table_block_id,
       cell.row_id AS row_id,
       cell.column_id AS column_id,
       $EmptySearchField AS property_id,
       $EmptySearchField AS chat_session_id,
       $EmptySearchField AS chat_message_id,
       COALESCE(NULLIF(left(cell.value, 80), ''), c.name) AS title,
       COALESCE(NULLIF(t.title, ''), 'Table') || ' / ' || c.name AS subtitle,
       left(regexp_replace(COALESCE(cell.value, '') || ' ' || COALESCE(cell.value_json, ''), '[[:space:]]+', ' ', 'g'), 240) AS snippet,
       (
           (ts_rank_cd(to_tsvector('simple', COALESCE(cell.value, '') || ' ' || COALESCE(cell.value_json, '')), input.ts_query) * 1000)::integer
           + CASE WHEN lower(COALESCE(cell.value, '')) LIKE '%' || input.raw_query || '%' THEN 100 ELSE 0 END
       ) AS score,
       cell.updated_at AS updated_at
FROM page_table_cells cell
INNER JOIN page_table_rows r ON r.id = cell.row_id
INNER JOIN page_table_columns c ON c.id = cell.column_id
INNER JOIN page_tables t ON t.id = r.table_id
INNER JOIN owned_pages p ON p.id = t.page_id
CROSS JOIN input
WHERE cell.deleted_at IS NULL
  AND r.deleted_at IS NULL
  AND c.deleted_at IS NULL
  AND t.deleted_at IS NULL
  AND (
      to_tsvector('simple', COALESCE(cell.value, '') || ' ' || COALESCE(cell.value_json, '')) @@ input.ts_query
      OR lower(COALESCE(cell.value, '') || ' ' || COALESCE(cell.value_json, '')) LIKE '%' || input.raw_query || '%'
  )
""".trimIndent()

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

private fun ResultSet.toProjectionSource(): PageProjectionSource {
    return PageProjectionSource(
        pageId = getString("id"),
        content = getString("content"),
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deletedAt = getNullableLong("deleted_at"),
    )
}

private fun PageRecord.toProjectionSource(): PageProjectionSource {
    return PageProjectionSource(
        pageId = id,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun ResultSet.toContentSearchResult(): ContentSearchResult {
    return ContentSearchResult(
        targetType = getString("target_type"),
        workspaceId = getString("workspace_id"),
        pageId = getString("page_id"),
        blockId = getString("block_id"),
        tableBlockId = getString("table_block_id"),
        rowId = getString("row_id"),
        columnId = getString("column_id"),
        propertyId = getString("property_id"),
        chatSessionId = getString("chat_session_id"),
        chatMessageId = getString("chat_message_id"),
        title = getString("title"),
        subtitle = getString("subtitle"),
        snippet = getString("snippet"),
        score = getInt("score"),
        updatedAt = getLong("updated_at"),
    )
}

private fun ResultSet.getNullableLong(columnLabel: String): Long? {
    val value = getLong(columnLabel)
    return if (wasNull()) null else value
}
