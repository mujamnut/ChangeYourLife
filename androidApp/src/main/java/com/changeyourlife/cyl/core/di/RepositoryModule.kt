package com.changeyourlife.cyl.core.di

import com.changeyourlife.cyl.data.repository.AuthRepositoryImpl
import com.changeyourlife.cyl.data.repository.AiActionLogRepositoryImpl
import com.changeyourlife.cyl.data.repository.AiAppliedActionLedgerRepositoryImpl
import com.changeyourlife.cyl.data.repository.ChatHistoryRepositoryImpl
import com.changeyourlife.cyl.data.repository.ChatAttachmentRepositoryImpl
import com.changeyourlife.cyl.data.repository.ContentAssetRepositoryImpl
import com.changeyourlife.cyl.data.asset.AndroidContentAssetLocalStore
import com.changeyourlife.cyl.data.attachment.BackgroundChatAttachmentUploadScheduler
import com.changeyourlife.cyl.data.remote.attachment.HttpChatAttachmentUploadGateway
import com.changeyourlife.cyl.data.repository.PageRepositoryImpl
import com.changeyourlife.cyl.data.repository.ReminderRepositoryImpl
import com.changeyourlife.cyl.data.repository.SearchRepositoryImpl
import com.changeyourlife.cyl.data.repository.SyncStatusRepositoryImpl
import com.changeyourlife.cyl.data.repository.TaskRepositoryImpl
import com.changeyourlife.cyl.data.repository.WorkspaceRepositoryImpl
import com.changeyourlife.cyl.data.repository.AiRepositoryImpl
import com.changeyourlife.cyl.data.repository.AiSkillRepositoryImpl
import com.changeyourlife.cyl.data.search.ChatSearchIndexUpdater
import com.changeyourlife.cyl.data.search.SearchIndexRebuilder
import com.changeyourlife.cyl.data.sync.BackgroundChatSyncScheduler
import com.changeyourlife.cyl.data.sync.ChatSyncScheduler
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.repository.AiAppliedActionLedgerRepository
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.repository.ContentAssetRepository
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadGateway
import com.changeyourlife.cyl.domain.repository.ChatAttachmentUploadScheduler
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.SearchRepository
import com.changeyourlife.cyl.domain.repository.SyncStatusRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiSkillRepository
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
    abstract fun bindSearchRepository(
        implementation: SearchRepositoryImpl,
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        implementation: AiRepositoryImpl,
    ): AiRepository

    @Binds
    @Singleton
    abstract fun bindAiSkillRepository(
        implementation: AiSkillRepositoryImpl,
    ): AiSkillRepository

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(
        implementation: ChatHistoryRepositoryImpl,
    ): ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindChatAttachmentRepository(
        implementation: ChatAttachmentRepositoryImpl,
    ): ChatAttachmentRepository

    @Binds
    @Singleton
    abstract fun bindContentAssetRepository(
        implementation: ContentAssetRepositoryImpl,
    ): ContentAssetRepository

    @Binds
    @Singleton
    abstract fun bindContentAssetLocalStore(
        implementation: AndroidContentAssetLocalStore,
    ): ContentAssetLocalStore

    @Binds
    @Singleton
    abstract fun bindChatAttachmentUploadGateway(
        implementation: HttpChatAttachmentUploadGateway,
    ): ChatAttachmentUploadGateway

    @Binds
    @Singleton
    abstract fun bindChatAttachmentUploadScheduler(
        implementation: BackgroundChatAttachmentUploadScheduler,
    ): ChatAttachmentUploadScheduler

    @Binds
    @Singleton
    abstract fun bindChatSyncScheduler(
        implementation: BackgroundChatSyncScheduler,
    ): ChatSyncScheduler

    @Binds
    @Singleton
    abstract fun bindChatSearchIndexUpdater(
        implementation: SearchIndexRebuilder,
    ): ChatSearchIndexUpdater

    @Binds
    @Singleton
    abstract fun bindAiActionLogRepository(
        implementation: AiActionLogRepositoryImpl,
    ): AiActionLogRepository

    @Binds
    @Singleton
    abstract fun bindAiAppliedActionLedgerRepository(
        implementation: AiAppliedActionLedgerRepositoryImpl,
    ): AiAppliedActionLedgerRepository

    @Binds
    @Singleton
    abstract fun bindSyncStatusRepository(
        implementation: SyncStatusRepositoryImpl,
    ): SyncStatusRepository
}
