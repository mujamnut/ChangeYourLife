package com.changeyourlife.cyl.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.changeyourlife.cyl.data.local.CylDatabase
import com.changeyourlife.cyl.data.local.dao.AiActionLogDao
import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.ReminderDao
import com.changeyourlife.cyl.data.local.dao.SyncTombstoneDao
import com.changeyourlife.cyl.data.local.dao.TaskDao
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CylDatabase {
        return Room.databaseBuilder(
            context,
            CylDatabase::class.java,
            "cyl.db",
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .addMigrations(MIGRATION_10_11)
            .addMigrations(MIGRATION_11_12)
            .build()
    }

    @Provides
    fun provideWorkspaceDao(database: CylDatabase): WorkspaceDao {
        return database.workspaceDao()
    }

    @Provides
    fun providePageDao(database: CylDatabase): PageDao {
        return database.pageDao()
    }

    @Provides
    fun providePageContentDao(database: CylDatabase): PageContentDao {
        return database.pageContentDao()
    }

    @Provides
    fun provideTaskDao(database: CylDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideReminderDao(database: CylDatabase): ReminderDao {
        return database.reminderDao()
    }

    @Provides
    fun provideChatMessageDao(database: CylDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideSyncTombstoneDao(database: CylDatabase): SyncTombstoneDao {
        return database.syncTombstoneDao()
    }

    @Provides
    fun provideAiActionLogDao(database: CylDatabase): AiActionLogDao {
        return database.aiActionLogDao()
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pages` (
                    `id` TEXT NOT NULL,
                    `workspaceId` TEXT NOT NULL,
                    `parentPageId` TEXT,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workspaceId`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_workspaceId` ON `pages` (`workspaceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_parentPageId` ON `pages` (`parentPageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_updatedAt` ON `pages` (`updatedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tasks` (
                    `id` TEXT NOT NULL,
                    `workspaceId` TEXT NOT NULL,
                    `pageId` TEXT,
                    `title` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `dueAt` INTEGER,
                    `priority` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workspaceId`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_workspaceId` ON `tasks` (`workspaceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_pageId` ON `tasks` (`pageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_updatedAt` ON `tasks` (`updatedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminders` (
                    `id` TEXT NOT NULL,
                    `workspaceId` TEXT NOT NULL,
                    `pageId` TEXT,
                    `taskId` TEXT,
                    `title` TEXT NOT NULL,
                    `remindAt` INTEGER NOT NULL,
                    `isDone` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`workspaceId`) REFERENCES `workspaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_workspaceId` ON `reminders` (`workspaceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_pageId` ON `reminders` (`pageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_taskId` ON `reminders` (`taskId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_remindAt` ON `reminders` (`remindAt`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `scopeId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `pageLinksJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_scopeId` ON `chat_messages` (`scopeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_sessions` (
                    `id` TEXT NOT NULL,
                    `scopeId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_scopeId` ON `chat_sessions` (`scopeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_updatedAt` ON `chat_sessions` (`updatedAt`)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO `chat_sessions` (`id`, `scopeId`, `title`, `createdAt`, `updatedAt`, `deletedAt`)
                SELECT `scopeId`, `scopeId`, 'Previous chat', MIN(`createdAt`), MAX(`createdAt`), NULL
                FROM `chat_messages`
                GROUP BY `scopeId`
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `workspaces` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `pages` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'Synced'")
            db.execSQL("ALTER TABLE `pages` ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `pages` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_blocks` (
                    `id` TEXT NOT NULL,
                    `pageId` TEXT NOT NULL,
                    `parentBlockId` TEXT,
                    `type` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `richTextJson` TEXT NOT NULL,
                    `mediaJson` TEXT NOT NULL,
                    `isChecked` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `metadataJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_blocks_pageId_sortOrder` ON `page_blocks` (`pageId`, `sortOrder`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_blocks_parentBlockId` ON `page_blocks` (`parentBlockId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_properties` (
                    `id` TEXT NOT NULL,
                    `pageId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `metadataJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_properties_pageId_sortOrder` ON `page_properties` (`pageId`, `sortOrder`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_tables` (
                    `id` TEXT NOT NULL,
                    `pageId` TEXT NOT NULL,
                    `blockId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `view` TEXT NOT NULL,
                    `viewConfigJson` TEXT NOT NULL,
                    `sortJson` TEXT NOT NULL,
                    `filterJson` TEXT NOT NULL,
                    `groupByColumnId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`blockId`) REFERENCES `page_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_tables_pageId` ON `page_tables` (`pageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_tables_blockId` ON `page_tables` (`blockId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_table_columns` (
                    `id` TEXT NOT NULL,
                    `tableId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `configJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`tableId`) REFERENCES `page_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_table_columns_tableId_sortOrder` ON `page_table_columns` (`tableId`, `sortOrder`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_table_rows` (
                    `id` TEXT NOT NULL,
                    `tableId` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `contentJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`tableId`) REFERENCES `page_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_table_rows_tableId_sortOrder` ON `page_table_rows` (`tableId`, `sortOrder`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `page_table_cells` (
                    `rowId` TEXT NOT NULL,
                    `columnId` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `valueJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`rowId`, `columnId`),
                    FOREIGN KEY(`rowId`) REFERENCES `page_table_rows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`columnId`) REFERENCES `page_table_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_table_cells_columnId` ON `page_table_cells` (`columnId`)")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `actionMetadataJson` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_tombstones` (
                    `id` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_tombstones_entityType_entityId`
                ON `sync_tombstones` (`entityType`, `entityId`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_sync_tombstones_createdAt`
                ON `sync_tombstones` (`createdAt`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_action_logs` (
                    `auditId` TEXT NOT NULL,
                    `requestMessageId` TEXT NOT NULL,
                    `responseMessageId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `workspaceId` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `schemaName` TEXT NOT NULL,
                    `schemaVersion` INTEGER NOT NULL,
                    `proposedActionsJson` TEXT NOT NULL,
                    `executedActionsJson` TEXT NOT NULL,
                    `validationIssuesJson` TEXT NOT NULL,
                    `executionMessagesJson` TEXT NOT NULL,
                    `undoState` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`auditId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_sessionId_createdAt`
                ON `ai_action_logs` (`sessionId`, `createdAt`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_requestMessageId`
                ON `ai_action_logs` (`requestMessageId`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_responseMessageId`
                ON `ai_action_logs` (`responseMessageId`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_workspaceId_createdAt`
                ON `ai_action_logs` (`workspaceId`, `createdAt`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ai_action_logs` ADD COLUMN `undoCommandsJson` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ai_action_logs` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `ai_action_logs` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
            db.execSQL("ALTER TABLE `ai_action_logs` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PendingPush'")
            db.execSQL("ALTER TABLE `ai_action_logs` ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `ai_action_logs` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_workspaceId_updatedAt`
                ON `ai_action_logs` (`workspaceId`, `updatedAt`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_logs_syncStatus`
                ON `ai_action_logs` (`syncStatus`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PendingPush'")
            db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `chat_messages` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PendingPush'")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `remoteUpdatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_chat_sessions_syncStatus`
                ON `chat_sessions` (`syncStatus`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_chat_messages_updatedAt`
                ON `chat_messages` (`updatedAt`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_chat_messages_syncStatus`
                ON `chat_messages` (`syncStatus`)
                """.trimIndent(),
            )
        }
    }
}
