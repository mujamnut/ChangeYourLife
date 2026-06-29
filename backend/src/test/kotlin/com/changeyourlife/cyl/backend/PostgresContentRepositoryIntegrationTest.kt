package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.DatabaseConfig
import com.changeyourlife.cyl.backend.data.PostgresContentRepository
import com.changeyourlife.cyl.backend.database.DatabaseFactory
import com.changeyourlife.cyl.backend.domain.PageRecord
import com.changeyourlife.cyl.backend.domain.WorkspaceRecord
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
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
            )

            assertNotNull(firstPage)
            assertEquals(firstPage.id, repository.listPages(firstUserId, sharedClientWorkspaceId).single().id)
            assertTrue(repository.listPages(secondUserId, sharedClientWorkspaceId).isEmpty())
            assertNull(repository.getPage(secondUserId, firstPage.id))
        } finally {
            dataSource.deleteTestUser(firstUserId)
            dataSource.deleteTestUser(secondUserId)
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
