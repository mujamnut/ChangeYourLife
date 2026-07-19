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
    fun migrate1To12_preservesLegacyWorkspaceAndCreatesCurrentSchema() {
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
            12,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4,
            DatabaseModule.MIGRATION_4_5,
            DatabaseModule.MIGRATION_5_6,
            DatabaseModule.MIGRATION_6_7,
            DatabaseModule.MIGRATION_7_8,
            DatabaseModule.MIGRATION_8_9,
            DatabaseModule.MIGRATION_9_10,
            DatabaseModule.MIGRATION_10_11,
            DatabaseModule.MIGRATION_11_12,
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
        assertTrue(database.tableExists("sync_tombstones"))
        assertTrue(database.tableExists("ai_action_logs"))
        assertTrue(database.columnExists("chat_messages", "actionMetadataJson"))
        assertTrue(database.columnExists("ai_action_logs", "undoCommandsJson"))
        assertTrue(database.columnExists("ai_action_logs", "updatedAt"))
        assertTrue(database.columnExists("ai_action_logs", "syncStatus"))
        assertTrue(database.columnExists("ai_action_logs", "remoteUpdatedAt"))
        assertTrue(database.columnExists("ai_action_logs", "lastSyncedAt"))
        assertTrue(database.columnExists("chat_sessions", "syncStatus"))
        assertTrue(database.columnExists("chat_sessions", "remoteUpdatedAt"))
        assertTrue(database.columnExists("chat_sessions", "lastSyncedAt"))
        assertTrue(database.columnExists("chat_messages", "updatedAt"))
        assertTrue(database.columnExists("chat_messages", "syncStatus"))
        assertTrue(database.columnExists("chat_messages", "remoteUpdatedAt"))
        assertTrue(database.columnExists("chat_messages", "lastSyncedAt"))

        database.close()
    }

    @Test
    fun migrate11To12AddsSyncMetadataToChatHistoryTables() {
        helper.createDatabase(TEST_DATABASE, 11).apply {
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
                VALUES ('session-1', 'home:workspace-1', 'Budget chat', 1000, 2000, NULL)
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
                    `actionMetadataJson`,
                    `createdAt`
                )
                VALUES ('message-1', 'session-1', 'assistant', 'Done', '[]', '', 3000)
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            12,
            true,
            DatabaseModule.MIGRATION_11_12,
        )

        assertTrue(database.columnExists("chat_sessions", "syncStatus"))
        assertTrue(database.columnExists("chat_sessions", "remoteUpdatedAt"))
        assertTrue(database.columnExists("chat_sessions", "lastSyncedAt"))
        assertTrue(database.columnExists("chat_messages", "updatedAt"))
        assertTrue(database.columnExists("chat_messages", "syncStatus"))
        assertTrue(database.columnExists("chat_messages", "remoteUpdatedAt"))
        assertTrue(database.columnExists("chat_messages", "lastSyncedAt"))

        database.query("SELECT * FROM `chat_sessions` WHERE `id` = 'session-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Budget chat", cursor.stringValue("title"))
            assertEquals("PendingPush", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
        }
        database.query("SELECT * FROM `chat_messages` WHERE `id` = 'message-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Done", cursor.stringValue("content"))
            assertEquals(3000L, cursor.longValue("updatedAt"))
            assertEquals("PendingPush", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
        }

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

    @Test
    fun migrate8To9AddsAiActionLogTable() {
        helper.createDatabase(TEST_DATABASE, 8).apply {
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            DatabaseModule.MIGRATION_8_9,
        )

        assertTrue(database.tableExists("ai_action_logs"))
        assertTrue(database.columnExists("ai_action_logs", "auditId"))
        assertTrue(database.columnExists("ai_action_logs", "requestMessageId"))
        assertTrue(database.columnExists("ai_action_logs", "responseMessageId"))
        assertTrue(database.columnExists("ai_action_logs", "undoState"))

        database.close()
    }

    @Test
    fun migrate9To10AddsUndoCommandsJsonToAiActionLogTable() {
        helper.createDatabase(TEST_DATABASE, 9).apply {
            execSQL(
                """
                INSERT INTO `ai_action_logs` (
                    `auditId`,
                    `requestMessageId`,
                    `responseMessageId`,
                    `sessionId`,
                    `workspaceId`,
                    `mode`,
                    `provider`,
                    `model`,
                    `schemaName`,
                    `schemaVersion`,
                    `proposedActionsJson`,
                    `executedActionsJson`,
                    `validationIssuesJson`,
                    `executionMessagesJson`,
                    `undoState`,
                    `createdAt`
                )
                VALUES (
                    'audit-1',
                    'request-1',
                    'response-1',
                    'session-1',
                    'workspace-1',
                    'Edit',
                    'openrouter',
                    'openai/gpt-oss-20b:free',
                    'CYL_ACTION_SCHEMA',
                    2,
                    '[]',
                    '[]',
                    '[]',
                    '[]',
                    'PendingCommandLink',
                    3000
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            10,
            true,
            DatabaseModule.MIGRATION_9_10,
        )

        assertTrue(database.columnExists("ai_action_logs", "undoCommandsJson"))
        database.query("SELECT `undoCommandsJson` FROM `ai_action_logs` WHERE `auditId` = 'audit-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[]", cursor.stringValue("undoCommandsJson"))
        }

        database.close()
    }

    @Test
    fun migrate10To11AddsSyncMetadataToAiActionLogTable() {
        helper.createDatabase(TEST_DATABASE, 10).apply {
            execSQL(
                """
                INSERT INTO `ai_action_logs` (
                    `auditId`,
                    `requestMessageId`,
                    `responseMessageId`,
                    `sessionId`,
                    `workspaceId`,
                    `mode`,
                    `provider`,
                    `model`,
                    `schemaName`,
                    `schemaVersion`,
                    `proposedActionsJson`,
                    `executedActionsJson`,
                    `validationIssuesJson`,
                    `executionMessagesJson`,
                    `undoCommandsJson`,
                    `undoState`,
                    `createdAt`
                )
                VALUES (
                    'audit-1',
                    'request-1',
                    'response-1',
                    'session-1',
                    'workspace-1',
                    'Edit',
                    'openrouter',
                    'openai/gpt-oss-20b:free',
                    'CYL_ACTION_SCHEMA',
                    2,
                    '[]',
                    '[]',
                    '[]',
                    '[]',
                    '[{"commandType":"DeleteBlock"}]',
                    'Available',
                    3000
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            11,
            true,
            DatabaseModule.MIGRATION_10_11,
        )

        assertTrue(database.columnExists("ai_action_logs", "updatedAt"))
        assertTrue(database.columnExists("ai_action_logs", "syncStatus"))
        assertTrue(database.columnExists("ai_action_logs", "remoteUpdatedAt"))
        assertTrue(database.columnExists("ai_action_logs", "lastSyncedAt"))
        database.query("SELECT * FROM `ai_action_logs` WHERE `auditId` = 'audit-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3000L, cursor.longValue("updatedAt"))
            assertEquals("PendingPush", cursor.stringValue("syncStatus"))
            assertEquals(0L, cursor.longValue("remoteUpdatedAt"))
            assertEquals(0L, cursor.longValue("lastSyncedAt"))
            assertEquals("""[{"commandType":"DeleteBlock"}]""", cursor.stringValue("undoCommandsJson"))
        }

        database.close()
    }

    @Test
    fun migrate14To15AddsSyncedAiSkillsTable() {
        helper.createDatabase(TEST_DATABASE, 14).apply {
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            15,
            true,
            DatabaseModule.MIGRATION_14_15,
        )

        assertTrue(database.tableExists("ai_skills"))
        assertTrue(database.columnExists("ai_skills", "workspaceId"))
        assertTrue(database.columnExists("ai_skills", "whenToUse"))
        assertTrue(database.columnExists("ai_skills", "instructions"))
        assertTrue(database.columnExists("ai_skills", "deletedAt"))
        assertTrue(database.columnExists("ai_skills", "syncStatus"))
        assertTrue(database.columnExists("ai_skills", "remoteUpdatedAt"))
        assertTrue(database.columnExists("ai_skills", "lastSyncedAt"))

        database.close()
    }

    @Test
    fun migrate17To18AddsAppliedAiActionLedger() {
        helper.createDatabase(TEST_DATABASE, 17).apply {
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            18,
            true,
            DatabaseModule.MIGRATION_17_18,
        )

        assertTrue(database.tableExists("applied_ai_actions"))
        assertTrue(database.columnExists("applied_ai_actions", "idempotencyKey"))
        assertTrue(database.columnExists("applied_ai_actions", "actionFingerprint"))
        assertTrue(database.columnExists("applied_ai_actions", "state"))

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
