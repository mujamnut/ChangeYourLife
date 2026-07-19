package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.CylAiAction
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.repository.ChatAction

internal data class AiPageActionDomainRequest(
    val engine: AiPageActionMutationEngine,
    val page: Page,
    val title: String,
    val document: PageBlockDocument,
    val action: ChatAction,
    val contractAction: CylAiAction,
    val hasPendingDocumentChanges: Boolean,
)

internal interface AiPageActionDomainExecutor {
    val domain: AiActionExecutionDomain

    suspend fun execute(request: AiPageActionDomainRequest): AiPageActionExecutionResult
}

internal abstract class DelegatingAiPageActionDomainExecutor(
    final override val domain: AiActionExecutionDomain,
) : AiPageActionDomainExecutor {
    final override suspend fun execute(
        request: AiPageActionDomainRequest,
    ): AiPageActionExecutionResult {
        check(request.contractAction.domain.toExecutionDomain() == domain) {
            "Action ${request.action.type} does not belong to ${domain.id}."
        }
        return request.engine.executeOnPage(
            page = request.page,
            title = request.title,
            document = request.document,
            actions = listOf(request.action),
            hasPendingDocumentChanges = request.hasPendingDocumentChanges,
        )
    }
}

internal object AiPageActionDomainExecutorRegistry {
    private val executors: Map<AiActionExecutionDomain, AiPageActionDomainExecutor> = listOf(
        AiPageDomainActionExecutor,
        AiBlockDomainActionExecutor,
        AiPropertyDomainActionExecutor,
        AiDatabaseDomainActionExecutor,
        AiColumnDomainActionExecutor,
        AiRowDomainActionExecutor,
        AiRowContentDomainActionExecutor,
        AiCellDomainActionExecutor,
        AiTaskDomainActionExecutor,
        AiReminderDomainActionExecutor,
    ).associateBy(AiPageActionDomainExecutor::domain)

    fun executorFor(action: ChatAction): AiPageActionDomainExecutor? =
        executors[AiActionExecutionRegistry.domainFor(action.normalizedExecutionType())]

    fun executorFor(action: CylAiAction): AiPageActionDomainExecutor? =
        executors[action.domain.toExecutionDomain()]
}
