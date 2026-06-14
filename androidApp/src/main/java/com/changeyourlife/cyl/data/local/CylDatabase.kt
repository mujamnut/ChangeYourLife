package com.changeyourlife.cyl.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.changeyourlife.cyl.data.local.dao.ChatMessageDao
import com.changeyourlife.cyl.data.local.dao.PageDao
import com.changeyourlife.cyl.data.local.dao.ReminderDao
import com.changeyourlife.cyl.data.local.dao.TaskDao
import com.changeyourlife.cyl.data.local.dao.WorkspaceDao
import com.changeyourlife.cyl.data.local.entity.ChatMessageEntity
import com.changeyourlife.cyl.data.local.entity.ChatSessionEntity
import com.changeyourlife.cyl.data.local.entity.PageEntity
import com.changeyourlife.cyl.data.local.entity.ReminderEntity
import com.changeyourlife.cyl.data.local.entity.TaskEntity
import com.changeyourlife.cyl.data.local.entity.WorkspaceEntity

@Database(
    entities = [
        WorkspaceEntity::class,
        PageEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        ChatMessageEntity::class,
        ChatSessionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CylDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun pageDao(): PageDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatMessageDao(): ChatMessageDao
}
