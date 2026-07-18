package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.changeyourlife.cyl.backend.data.PostgresContentRepository
import com.changeyourlife.cyl.backend.data.PostgresPageContentProjectionBackfill
import com.changeyourlife.cyl.backend.database.DatabaseFactory
import com.changeyourlife.cyl.backend.domain.ContentSearchQuery
import com.changeyourlife.cyl.backend.domain.PageMutationResult
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class PostgresContentRepositoryIntegrationTest {
    @Test
    fun postgresRepositoryKeepsClientWorkspaceIdsScopedPerUser() = runBlocking {
        val config = testDatabaseConfig()
        assumeTrue(
            "Set CYL_TEST_DATABASE_URL to run PostgreSQL integration tests.",
            config.isConfigured,
        )

        val firstUserId = "test-user-${UUID.randomUUID()}"
        val secondUserId = "test-user-${UUID.randomUUID()}"
        val sharedClientWorkspaceId = "local-default-workspace"

        val dataSource = DatabaseFactory.createDataSource(config)
        try {
            DatabaseFactory.migrate(dataSource)
            dataSource.createTestUser(firstUserId, "first-$firstUserId@example.test")
            dataSource.createTestUser(secondUserId, "second-$secondUserId@example.test")

            val repository = PostgresContentRepository(dataSource)
            assertTrue(dataSource.tableExists("page_blocks"))
            assertTrue(dataSource.tableExists("page_properties"))
            assertTrue(dataSource.tableExists("page_tables"))
            assertTrue(dataSource.tableExists("page_table_columns"))
            assertTrue(dataSource.tableExists("page_table_rows"))
            assertTrue(dataSource.tableExists("page_table_cells"))

            val firstWorkspace = repository.upsertWorkspace(
                WorkspaceRecord(
                    id = sharedClientWorkspaceId,
                    userId = firstUserId,
                    name = "First workspace",
                    createdAt = 100L,
                    updatedAt = 100L,
                    deletedAt = null,
                ),
            )
            val secondWorkspace = repository.upsertWorkspace(
                WorkspaceRecord(
                    id = sharedClientWorkspaceId,
                    userId = secondUserId,
                    name = "Second workspace",
                    createdAt = 200L,
                    updatedAt = 200L,
                    deletedAt = null,
                ),
            )

            assertNotNull(firstWorkspace)
            assertNotNull(secondWorkspace)
            assertEquals(sharedClientWorkspaceId, firstWorkspace.id)
            assertEquals(sharedClientWorkspaceId, secondWorkspace.id)
            assertEquals("First workspace", repository.listWorkspaces(firstUserId).single().name)
            assertEquals("Second workspace", repository.listWorkspaces(secondUserId).single().name)

            val firstPage = repository.upsertPage(
                userId = firstUserId,
                page = PageRecord(
                    id = "page-${UUID.randomUUID()}",
                    workspaceId = sharedClientWorkspaceId,
                    parentPageId = null,
                    title = "Private page",
                    content = """{"version":1,"blocks":[]}""",
                    sortOrder = 0,
                    createdAt = 300L,
                    updatedAt = 300L,
                    deletedAt = null,
                ),
            ).appliedPage()

            assertEquals(firstPage.id, repository.listPages(firstUserId, sharedClientWorkspaceId).single().id)
            assertTrue(repository.listPages(secondUserId, sharedClientWorkspaceId).isEmpty())
            assertNull(repository.getPage(secondUserId, firstPage.id))
        } finally {
            dataSource.deleteTestUser(firstUserId)
            dataSource.deleteTestUser(secondUserId)
            dataSource.close()
        }
    }

    @Test
    fun postgresRepositoryProjectsAndReplacesSearchablePageContent() = runBlocking {
        val config = testDatabaseConfig()
        assumeTrue(
            "Set CYL_TEST_DATABASE_URL to run PostgreSQL integration tests.",
            config.isConfigured,
        )

        val userId = "test-user-${UUID.randomUUID()}"
        val workspaceId = "projection-workspace-${UUID.randomUUID()}"
        val pageId = "projection-page-${UUID.randomUUID()}"
        val tableId = "projection-table-${UUID.randomUUID()}"
        val nameColumnId = "projection-name-${UUID.randomUUID()}"
        val amountColumnId = "projection-amount-${UUID.randomUUID()}"
        val rowId = "projection-row-${UUID.randomUUID()}"
        val dataSource = DatabaseFactory.createDataSource(config)
        try {
            DatabaseFactory.migrate(dataSource)
            dataSource.createTestUser(userId, "$userId@example.test")
            val repository = PostgresContentRepository(dataSource)
            assertNotNull(
                repository.upsertWorkspace(
                    WorkspaceRecord(
                        id = workspaceId,
                        userId = userId,
                        name = "Projection workspace",
                        createdAt = 100,
                        updatedAt = 100,
                        deletedAt = null,
                    ),
                ),
            )

            val initialContent = databasePageContent(
                tableId = tableId,
                nameColumnId = nameColumnId,
                amountColumnId = amountColumnId,
                rowId = rowId,
                rowName = "Fuel",
                rowNote = "Motorcycle receipt",
            )
            val initialPage = repository.upsertPage(
                    userId = userId,
                    page = PageRecord(
                        id = pageId,
                        workspaceId = workspaceId,
                        parentPageId = null,
                        title = "July budget",
                        content = initialContent,
                        sortOrder = 0,
                        createdAt = 200,
                        updatedAt = 200,
                        deletedAt = null,
                    ),
                ).appliedPage()

            val staleWrite = repository.upsertPage(
                userId = userId,
                page = initialPage.copy(
                    title = "Stale overwrite",
                    revision = 0L,
                ),
            )
            val staleConflict = assertIs<PageMutationResult.Conflict>(staleWrite)
            assertEquals(initialPage.revision, staleConflict.currentPage.revision)
            assertEquals("July budget", staleConflict.currentPage.title)

            val fuelResults = repository.search(
                userId = userId,
                query = ContentSearchQuery(
                    workspaceId = workspaceId,
                    query = "Fuel",
                    scopes = setOf("Cell"),
                    limit = 10,
                ),
            )
            assertEquals(listOf(rowId), fuelResults.map { result -> result.rowId })
            assertEquals(listOf(nameColumnId), fuelResults.map { result -> result.columnId })
            assertEquals(
                listOf(tableId),
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Transactions",
                        scopes = setOf("Table"),
                        limit = 10,
                    ),
                ).map { result -> result.tableBlockId },
            )

            val replacedPage = repository.upsertPage(
                    userId = userId,
                    page = PageRecord(
                        id = pageId,
                        workspaceId = workspaceId,
                        parentPageId = null,
                        title = "July budget",
                        content = databasePageContent(
                            tableId = tableId,
                            nameColumnId = nameColumnId,
                            amountColumnId = amountColumnId,
                            rowId = rowId,
                            rowName = "Transport",
                            rowNote = "Bus receipt",
                        ),
                        sortOrder = 0,
                        createdAt = 200,
                        updatedAt = 300,
                        deletedAt = null,
                        revision = initialPage.revision,
                    ),
                ).appliedPage()

            assertTrue(
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Fuel",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).isEmpty(),
            )
            assertEquals(
                listOf(rowId),
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Transport",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).map { result -> result.rowId },
            )

            val cellUpdatedPage = repository.updatePageTableCellValue(
                    userId = userId,
                    pageId = pageId,
                    rowId = rowId,
                    columnId = nameColumnId,
                    value = "Bus",
                    valueJson = null,
                    expectedRevision = replacedPage.revision,
                    updatedAt = 400,
                ).appliedPage()
            assertTrue(
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Transport",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).isEmpty(),
            )
            assertEquals(
                listOf(rowId),
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Bus",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).map { result -> result.rowId },
            )

            val deletedPage = repository.softDeletePage(
                userId = userId,
                pageId = pageId,
                expectedRevision = cellUpdatedPage.revision,
                deletedAt = 500,
            ).appliedPage()
            assertTrue(
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Bus",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).isEmpty(),
            )

            repository.restorePage(
                userId = userId,
                pageId = pageId,
                expectedRevision = deletedPage.revision,
                restoredAt = 600,
            ).appliedPage()
            assertEquals(
                listOf(rowId),
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "Bus",
                        scopes = setOf("Cell"),
                        limit = 10,
                    ),
                ).map { result -> result.rowId },
            )
        } finally {
            dataSource.deleteTestUser(userId)
            dataSource.close()
        }
    }

    @Test
    fun postgresBackfillBuildsProjectionForPreExistingPage() = runBlocking {
        val config = testDatabaseConfig()
        assumeTrue(
            "Set CYL_TEST_DATABASE_URL to run PostgreSQL integration tests.",
            config.isConfigured,
        )

        val userId = "test-user-${UUID.randomUUID()}"
        val workspaceId = "backfill-workspace-${UUID.randomUUID()}"
        val pageId = "backfill-page-${UUID.randomUUID()}"
        val blockId = "backfill-block-${UUID.randomUUID()}"
        val dataSource = DatabaseFactory.createDataSource(config)
        try {
            DatabaseFactory.migrate(dataSource)
            dataSource.createTestUser(userId, "$userId@example.test")
            val repository = PostgresContentRepository(dataSource)
            assertNotNull(
                repository.upsertWorkspace(
                    WorkspaceRecord(
                        id = workspaceId,
                        userId = userId,
                        name = "Backfill workspace",
                        createdAt = 100,
                        updatedAt = 100,
                        deletedAt = null,
                    ),
                ),
            )
            dataSource.insertPageWithoutProjection(
                userId = userId,
                workspaceId = workspaceId,
                pageId = pageId,
                content = """
                    {
                      "version": 1,
                      "blocks": [
                        {
                          "id": "$blockId",
                          "type": "Text",
                          "text": "Backfilled chicken schedule"
                        }
                      ]
                    }
                """.trimIndent(),
            )

            assertEquals(1, PostgresPageContentProjectionBackfill(dataSource).run())
            assertEquals(
                listOf(blockId),
                repository.search(
                    userId = userId,
                    query = ContentSearchQuery(
                        workspaceId = workspaceId,
                        query = "chicken",
                        scopes = setOf("Block"),
                        limit = 10,
                    ),
                ).map { result -> result.blockId },
            )
            assertEquals(0, PostgresPageContentProjectionBackfill(dataSource).run())
        } finally {
            dataSource.deleteTestUser(userId)
            dataSource.close()
        }
    }

    private fun testDatabaseConfig(): DatabaseConfig {
        val parsed = parseTestDatabaseUrl(System.getenv("CYL_TEST_DATABASE_URL"))
        return DatabaseConfig(
            jdbcUrl = parsed.jdbcUrl,
            username = System.getenv("CYL_TEST_DATABASE_USER")?.takeIf { value -> value.isNotBlank() }
                ?: parsed.username,
            password = System.getenv("CYL_TEST_DATABASE_PASSWORD")?.takeIf { value -> value.isNotBlank() }
                ?: parsed.password,
            maxPoolSize = 2,
        )
    }

    private fun parseTestDatabaseUrl(rawUrl: String?): ParsedTestDatabaseUrl {
        if (rawUrl.isNullOrBlank()) return ParsedTestDatabaseUrl(jdbcUrl = null, username = null, password = null)
        if (rawUrl.startsWith("jdbc:postgresql://")) {
            return ParsedTestDatabaseUrl(jdbcUrl = rawUrl, username = null, password = null)
        }
        val uri = URI(rawUrl)
        val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
        val username = userInfo.getOrNull(0)?.decodeUrl()
        val password = userInfo.getOrNull(1)?.decodeUrl()
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.let { query -> "?$query" }.orEmpty()
        return ParsedTestDatabaseUrl(
            jdbcUrl = "jdbc:postgresql://${uri.host}$port${uri.path}$query",
            username = username,
            password = password,
        )
    }

    private fun String.decodeUrl(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8)
    }

    private data class ParsedTestDatabaseUrl(
        val jdbcUrl: String?,
        val username: String?,
        val password: String?,
    )
}

private fun PageMutationResult.appliedPage(): PageRecord {
    return assertIs<PageMutationResult.Applied>(this).page
}

private fun databasePageContent(
    tableId: String,
    nameColumnId: String,
    amountColumnId: String,
    rowId: String,
    rowName: String,
    rowNote: String,
): String {
    return """
        {
          "version": 1,
          "blocks": [
            {
              "id": "$tableId",
              "type": "DatabaseTable",
              "table": {
                "title": "Transactions",
                "view": "Table",
                "columns": [
                  {
                    "id": "$nameColumnId",
                    "name": "Name",
                    "type": "Text"
                  },
                  {
                    "id": "$amountColumnId",
                    "name": "Amount",
                    "type": "Number"
                  }
                ],
                "rows": [
                  {
                    "id": "$rowId",
                    "cells": {
                      "$nameColumnId": "$rowName",
                      "$amountColumnId": "5"
                    },
                    "cellValues": {
                      "$nameColumnId": {
                        "type": "Text",
                        "text": "$rowName"
                      },
                      "$amountColumnId": {
                        "type": "Number",
                        "number": "5"
                      }
                    },
                    "blocks": [
                      {
                        "id": "$rowId-note",
                        "type": "Text",
                        "text": "$rowNote"
                      }
                    ]
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()
}

private fun javax.sql.DataSource.createTestUser(userId: String, email: String) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
            VALUES (?, ?, 'test-password-hash', 'Integration Test', 1, 1)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, email)
            statement.executeUpdate()
        }
    }
}

private fun javax.sql.DataSource.deleteTestUser(userId: String) {
    connection.use { connection ->
        connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
            statement.setString(1, userId)
            statement.executeUpdate()
        }
    }
}

private fun javax.sql.DataSource.insertPageWithoutProjection(
    userId: String,
    workspaceId: String,
    pageId: String,
    content: String,
) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO pages (
                id, workspace_id, parent_page_id, title, content, sort_order,
                created_at, updated_at, deleted_at, content_projection_updated_at
            )
            SELECT ?, workspaces.id, NULL, 'Backfill page', ?, 0, 200, 200, NULL, NULL
            FROM workspaces
            WHERE workspaces.user_id = ?
              AND COALESCE(workspaces.client_id, workspaces.id) = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, pageId)
            statement.setString(2, content)
            statement.setString(3, userId)
            statement.setString(4, workspaceId)
            assertEquals(1, statement.executeUpdate())
        }
    }
}

private fun javax.sql.DataSource.tableExists(tableName: String): Boolean {
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                    AND table_name = ?
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { result ->
                return result.next() && result.getBoolean(1)
            }
        }
    }
}
