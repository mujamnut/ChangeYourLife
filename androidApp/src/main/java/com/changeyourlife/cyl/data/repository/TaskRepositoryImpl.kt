package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.data.local.dao.TaskDao
import com.changeyourlife.cyl.data.local.mapper.toDomain
import com.changeyourlife.cyl.data.local.mapper.toEntity
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.repository.TaskRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
) : TaskRepository {
    override fun observeOpenTasks(): Flow<List<TaskItem>> {
        return taskDao.observeOpenTasks()
            .map { tasks -> tasks.map { it.toDomain() } }
    }

    override fun observeOpenTasks(workspaceId: String): Flow<List<TaskItem>> {
        return taskDao.observeOpenTasks(workspaceId)
            .map { tasks -> tasks.map { it.toDomain() } }
    }

    override fun observeTask(taskId: String): Flow<TaskItem?> {
        return taskDao.observeTask(taskId)
            .map { task -> task?.toDomain() }
    }

    override fun observeOpenTaskCount(): Flow<Int> {
        return taskDao.observeOpenTaskCount()
    }

    override fun observeOpenTaskCount(workspaceId: String): Flow<Int> {
        return taskDao.observeOpenTaskCount(workspaceId)
    }

    override suspend fun getTask(taskId: String): TaskItem? {
        return taskDao.getTask(taskId)?.toDomain()
    }

    override suspend fun upsertTask(task: TaskItem) {
        taskDao.upsertTask(task.toEntity())
    }

    override suspend fun createTask(
        workspaceId: String,
        title: String,
        notes: String,
        dueAt: Long?,
        priority: Int,
        pageId: String?,
    ): TaskItem {
        val now = System.currentTimeMillis()
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            pageId = pageId,
            title = title,
            notes = notes,
            isCompleted = false,
            dueAt = dueAt,
            priority = priority,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        taskDao.upsertTask(task.toEntity())
        return task
    }
}
