package com.changeyourlife.cyl.core.di

import com.changeyourlife.cyl.data.repository.AuthRepositoryImpl
import com.changeyourlife.cyl.data.repository.ChatHistoryRepositoryImpl
import com.changeyourlife.cyl.data.repository.PageRepositoryImpl
import com.changeyourlife.cyl.data.repository.ReminderRepositoryImpl
import com.changeyourlife.cyl.data.repository.SyncStatusRepositoryImpl
import com.changeyourlife.cyl.data.repository.TaskRepositoryImpl
import com.changeyourlife.cyl.data.repository.WorkspaceRepositoryImpl
import com.changeyourlife.cyl.data.repository.AiRepositoryImpl
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.SyncStatusRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.repository.AiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        implementation: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        implementation: WorkspaceRepositoryImpl,
    ): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindPageRepository(
        implementation: PageRepositoryImpl,
    ): PageRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        implementation: TaskRepositoryImpl,
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(
        implementation: ReminderRepositoryImpl,
    ): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        implementation: AiRepositoryImpl,
    ): AiRepository

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(
        implementation: ChatHistoryRepositoryImpl,
    ): ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSyncStatusRepository(
        implementation: SyncStatusRepositoryImpl,
    ): SyncStatusRepository
}
