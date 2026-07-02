package com.changeyourlife.cyl.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.changeyourlife.cyl.data.local.dao.AiActionLogDao
import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.PageContentDao
import com.changeyourlife.cyl.data.local.dao.ReminderDao
import com.changeyourlife.cyl.data.local.dao.SyncTombstoneDao
import com.changeyourlife.cyl.data.local.dao.TaskDao
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.local.entity.AiActionLogEntity
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.local.entity.PageBlockEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.ReminderEntity
import com.changeyourlife.cyl.data.local.entity.SyncTombstoneEntity
import com.changeyourlife.cyl.data.local.entity.PagePropertyEntity
import com.changeyourlife.cyl.data.local.entity.PageTableCellEntity
import com.changeyourlife.cyl.data.local.entity.PageTableColumnEntity
import com.changeyourlife.cyl.data.local.entity.PageTableEntity
import com.changeyourlife.cyl.data.local.entity.PageTableRowEntity
import com.changeyourlife.cyl.data.local.entity.TaskEntity
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        PageEntity::class,
        PageBlockEntity::class,
        PagePropertyEntity::class,
        PageTableEntity::class,
        PageTableColumnEntity::class,
        PageTableRowEntity::class,
        PageTableCellEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class,
        SyncTombstoneEntity::class,
        AiActionLogEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class CylDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun pageDao(): PageDao
    abstract fun pageContentDao(): PageContentDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun aiActionLogDao(): AiActionLogDao
}
