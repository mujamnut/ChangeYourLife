package com.changeyourlife.cyl.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.changeyourlife.cyl.data.local.CylDatabase
import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.ReminderDao
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
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(true)
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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
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
}
