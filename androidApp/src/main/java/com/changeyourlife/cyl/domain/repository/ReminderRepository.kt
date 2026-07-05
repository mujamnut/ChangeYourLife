package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observePendingReminders(): Flow<List<Reminder>>

    fun observePendingReminders(workspaceId: String): Flow<List<Reminder>>

    fun observePendingReminderCount(): Flow<Int>

    fun observePendingReminderCount(workspaceId: String): Flow<Int>

    suspend fun getReminderForTask(taskId: String): Reminder?

    suspend fun upsertReminder(reminder: Reminder)

    suspend fun reschedulePendingReminders()

    suspend fun createReminder(
        workspaceId: String,
        title: String,
        remindAt: Long,
        pageId: String? = null,
        taskId: String? = null,
        id: String? = null,
    ): Reminder
}
