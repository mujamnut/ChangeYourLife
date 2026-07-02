package com.changeyourlife.cyl.core.di

import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.ApplyAiActionUndoUseCase
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.TableMutationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideApplyEditorCommandUseCase(): ApplyEditorCommandUseCase {
        return ApplyEditorCommandUseCase()
    }

    @Provides
    fun provideTableMutationUseCase(
        applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    ): TableMutationUseCase {
        return TableMutationUseCase(applyEditorCommandUseCase)
    }

    @Provides
    fun providePageMutationUseCase(
        applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    ): PageMutationUseCase {
        return PageMutationUseCase(applyEditorCommandUseCase)
    }

    @Provides
    fun provideApplyAiActionUndoUseCase(
        aiActionLogRepository: AiActionLogRepository,
        pageRepository: PageRepository,
        applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    ): ApplyAiActionUndoUseCase {
        return ApplyAiActionUndoUseCase(
            aiActionLogRepository = aiActionLogRepository,
            pageRepository = pageRepository,
            applyEditorCommandUseCase = applyEditorCommandUseCase,
        )
    }
}
