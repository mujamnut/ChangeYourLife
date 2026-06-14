package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.core.notifications.ReminderScheduler
import com.changeyourlife.cyl.data.local.dao.ReminderDao
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler,
) : ReminderRepository {
    override fun observePendingReminders(): Flow<List<Reminder>> {
        return reminderDao.observePendingReminders()
            .map { reminders -> reminders.map { it.toDomain() } }
    }

    override fun observePendingReminders(workspaceId: String): Flow<List<Reminder>> {
        return reminderDao.observePendingReminders(workspaceId)
            .map { reminders -> reminders.map { it.toDomain() } }
    }

    override fun observePendingReminderCount(): Flow<Int> {
        return reminderDao.observePendingReminderCount()
    }

    override fun observePendingReminderCount(workspaceId: String): Flow<Int> {
        return reminderDao.observePendingReminderCount(workspaceId)
    }

    override suspend fun getReminderForTask(taskId: String): Reminder? {
        return reminderDao.getReminderForTask(taskId)?.toDomain()
    }

    override suspend fun upsertReminder(reminder: Reminder) {
        reminderDao.upsertReminder(reminder.toEntity())
        reminderScheduler.schedule(reminder)
    }

    override suspend fun createReminder(
        workspaceId: String,
        title: String,
        remindAt: Long,
        pageId: String?,
        taskId: String?,
        id: String?,
    ): Reminder {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = id ?: UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            pageId = pageId,
            taskId = taskId,
            title = title,
            remindAt = remindAt,
            isDone = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        upsertReminder(reminder)
        return reminder
    }
}
