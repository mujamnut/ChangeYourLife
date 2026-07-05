package com.changeyourlife.cyl.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.changeyourlife.cyl.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT * FROM reminders
        WHERE deletedAt IS NULL AND isDone = 0
        ORDER BY remindAt ASC
        """,
    )
    fun observePendingReminders(): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders
        WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND isDone = 0
        ORDER BY remindAt ASC
        """,
    )
    fun observePendingReminders(workspaceId: String): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders
        WHERE taskId = :taskId AND deletedAt IS NULL
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getReminderForTask(taskId: String): ReminderEntity?

    @Query("SELECT COUNT(*) FROM reminders WHERE deletedAt IS NULL AND isDone = 0")
    fun observePendingReminderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reminders WHERE workspaceId = :workspaceId AND deletedAt IS NULL AND isDone = 0")
    fun observePendingReminderCount(workspaceId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM reminders
        WHERE deletedAt IS NULL AND isDone = 0 AND remindAt > :now
        ORDER BY remindAt ASC
        """,
    )
    suspend fun getPendingRemindersAfter(now: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity)
}
