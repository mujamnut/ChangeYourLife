package com.changeyourlife.cyl.domain.repository

import com.changeyourlife.cyl.domain.model.TaskItem
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeOpenTasks(): Flow<List<TaskItem>>

    fun observeOpenTasks(workspaceId: String): Flow<List<TaskItem>>

    fun observeTask(taskId: String): Flow<TaskItem?>

    fun observeOpenTaskCount(): Flow<Int>

    fun observeOpenTaskCount(workspaceId: String): Flow<Int>

    suspend fun getTask(taskId: String): TaskItem?

    suspend fun upsertTask(task: TaskItem)

    suspend fun createTask(
        workspaceId: String,
        title: String,
        notes: String = "",
        dueAt: Long? = null,
        priority: Int = 0,
        pageId: String? = null,
    ): TaskItem
}
