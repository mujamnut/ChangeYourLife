package com.changeyourlife.cyl.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.changeyourlife.cyl.core.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CylDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CylDatabase::class.java,
    )

    @Test
    fun migrate1To7_preservesLegacyWorkspaceAndCreatesCurrentSchema() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO `workspaces` (`id`, `name`, `createdAt`, `updatedAt`)
                VALUES ('workspace-legacy', 'Legacy Space', 1000, 2000)
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4,
            DatabaseModule.MIGRATION_4_5,
            DatabaseModule.MIGRATION_5_6,
            DatabaseModule.MIGRATION_6_7,
        )

        database.query("SELECT * FROM `workspaces` WHERE `id` = 'workspace-legacy'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Space", cursor.stringValue("name"))
            assertEquals("Synced", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
        }
        assertTrue(database.tableExists("pages"))
        assertTrue(database.tableExists("tasks"))
        assertTrue(database.tableExists("reminders"))
        assertTrue(database.tableExists("chat_messages"))
        assertTrue(database.tableExists("chat_sessions"))
        assertTrue(database.tableExists("page_blocks"))
        assertTrue(database.tableExists("page_properties"))
        assertTrue(database.tableExists("page_tables"))
        assertTrue(database.tableExists("page_table_columns"))
        assertTrue(database.tableExists("page_table_rows"))
        assertTrue(database.tableExists("page_table_cells"))
        assertTrue(database.columnExists("chat_messages", "actionMetadataJson"))

        database.close()
    }

    @Test
    fun migrate4To5_preservesWorkspaceAndPageRowsAndAddsSyncMetadata() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                """
                INSERT INTO `workspaces` (`id`, `name`, `createdAt`, `updatedAt`)
                VALUES ('workspace-1', 'Personal', 1000, 2000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `pages` (
                    `id`,
                    `workspaceId`,
                    `parentPageId`,
                    `title`,
                    `content`,
                    `sortOrder`,
                    `createdAt`,
                    `updatedAt`,
                    `deletedAt`
                )
                VALUES (
                    'page-1',
                    'workspace-1',
                    NULL,
                    'Budget Tracker',
                    '{"blocks":[]}',
                    0,
                    3000,
                    4000,
                    NULL
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            DatabaseModule.MIGRATION_4_5,
        )

        database.query("SELECT * FROM `workspaces` WHERE `id` = 'workspace-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Personal", cursor.stringValue("name"))
            assertEquals("Synced", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
        }

        database.query("SELECT * FROM `pages` WHERE `id` = 'page-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Budget Tracker", cursor.stringValue("title"))
            assertEquals("Synced", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
        }

        database.close()
    }

    @Test
    fun migrate5To6_preservesPagesAndAddsPageContentProjectionTables() {
        helper.createDatabase(TEST_DATABASE, 5).apply {
            execSQL(
                """
                INSERT INTO `workspaces` (
                    `id`,
                    `name`,
                    `createdAt`,
                    `updatedAt`,
                    `syncStatus`,
                    `remoteUpdatedAt`,
                    `lastSyncedAt`
                )
                VALUES ('workspace-1', 'Personal', 1000, 2000, 'Synced', 2000, 2000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `pages` (
                    `id`,
                    `workspaceId`,
                    `parentPageId`,
                    `title`,
                    `content`,
                    `sortOrder`,
                    `createdAt`,
                    `updatedAt`,
                    `deletedAt`,
                    `syncStatus`,
                    `remoteUpdatedAt`,
                    `lastSyncedAt`
                )
                VALUES (
                    'page-1',
                    'workspace-1',
                    NULL,
                    'Budget Tracker',
                    '{"blocks":[]}',
                    0,
                    3000,
                    4000,
                    NULL,
                    'Synced',
                    4000,
                    4000
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            DatabaseModule.MIGRATION_5_6,
        )

        database.query("SELECT * FROM `pages` WHERE `id` = 'page-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Budget Tracker", cursor.stringValue("title"))
        }
        assertTrue(database.tableExists("page_blocks"))
        assertTrue(database.tableExists("page_properties"))
        assertTrue(database.tableExists("page_tables"))
        assertTrue(database.tableExists("page_table_columns"))
        assertTrue(database.tableExists("page_table_rows"))
        assertTrue(database.tableExists("page_table_cells"))

        database.close()
    }

    @Test
    fun migrate6To7_preservesChatMessagesAndAddsActionMetadata() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL(
                """
                INSERT INTO `chat_sessions` (
                    `id`,
                    `scopeId`,
                    `title`,
                    `createdAt`,
                    `updatedAt`,
                    `deletedAt`
                )
                VALUES ('session-1', 'workspace-1', 'Budget chat', 1000, 2000, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `chat_messages` (
                    `id`,
                    `scopeId`,
                    `role`,
                    `content`,
                    `pageLinksJson`,
                    `createdAt`
                )
                VALUES ('message-1', 'session-1', 'assistant', 'Done', '[]', 3000)
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            DatabaseModule.MIGRATION_6_7,
        )

        database.query("SELECT * FROM `chat_messages` WHERE `id` = 'message-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Done", cursor.stringValue("content"))
            assertEquals("", cursor.stringValue("actionMetadataJson"))
        }

        database.close()
    }

    private fun Cursor.stringValue(columnName: String): String {
        return getString(getColumnIndexOrThrow(columnName))
    }

    private fun Cursor.longValue(columnName: String): Long {
        return getLong(getColumnIndexOrThrow(columnName))
    }

    private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun SupportSQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }

    private companion object {
        const val TEST_DATABASE = "cyl-migration-test"
    }
}
