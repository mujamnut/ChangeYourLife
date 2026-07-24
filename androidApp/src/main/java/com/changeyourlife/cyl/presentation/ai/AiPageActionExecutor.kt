package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.usecase.ApplyEditorCommandUseCase
import com.changeyourlife.cyl.domain.usecase.PageMutationUseCase
import com.changeyourlife.cyl.domain.usecase.ScheduleTableDateReminderUseCase
import com.changeyourlife.cyl.domain.usecase.TableMutationUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import javax.inject.Inject

class AiPageActionExecutor @Inject constructor(
    private val pageRepository: PageRepository,
    pageMutationUseCase: PageMutationUseCase,
    tableMutationUseCase: TableMutationUseCase,
    scheduleTableDateReminderUseCase: ScheduleTableDateReminderUseCase,
) {
    private val mutationEngine = AiPageActionMutationEngine(
        pageRepository = pageRepository,
        pageMutationUseCase = pageMutationUseCase,
        tableMutationUseCase = tableMutationUseCase,
        scheduleTableDateReminderUseCase = scheduleTableDateReminderUseCase,
    )

    constructor(
        pageRepository: PageRepository,
        @Suppress("UNUSED_PARAMETER") taskRepository: TaskRepository,
        reminderRepository: ReminderRepository,
        applyEditorCommandUseCase: ApplyEditorCommandUseCase,
    ) : this(
        pageRepository = pageRepository,
        pageMutationUseCase = PageMutationUseCase(applyEditorCommandUseCase),
        tableMutationUseCase = TableMutationUseCase(applyEditorCommandUseCase),
        scheduleTableDateReminderUseCase = ScheduleTableDateReminderUseCase(reminderRepository),
    )

    fun supports(action: ChatAction): Boolean =
        AiPageActionDomainExecutorRegistry.executorFor(action) != null

    suspend fun executeOnPage(
        page: Page,
        title: String,
        document: PageBlockDocument,
        actions: List<ChatAction>,
    ): AiPageActionExecutionResult {
        var workingTitle = title.ifBlank { page.title }
        var workingDocument = document
        var titleChanged = false
        var documentChanged = false
        val messages = mutableListOf<String>()
        val validationIssues = mutableListOf<AiPageActionValidationIssue>()
        val createdPages = mutableListOf<Page>()
        val createdTasks = mutableListOf<TaskItem>()
        val createdReminders = mutableListOf<Reminder>()
        val pageLinks = mutableListOf<AiChatPageLink>()
        val undoCommands = mutableListOf<AiUndoCommandSummary>()
        val executedActionIndexes = mutableListOf<Int>()

        actions.forEachIndexed { actionIndex, action ->
            val trace = AiActionExecutionRegistry.trace(actionIndex, action)
            val contractResult = AiActionContractSchema.parse(
                actionIndex = actionIndex,
                payload = action.toContractWire(),
            )
            if (!contractResult.isValid) {
                validationIssues += contractResult.issues.map { issue ->
                    AiPageActionValidationIssue(
                        actionIndex = actionIndex,
                        actionType = trace.actionType,
                        actionDomain = trace.domain.id,
                        field = issue.field,
                        code = issue.code,
                        message = issue.message,
                    )
                }
                return@forEachIndexed
            }
            val contractAction = requireNotNull(contractResult.action)
            val domainExecutor = requireNotNull(
                AiPageActionDomainExecutorRegistry.executorFor(contractAction),
            ) {
                "No executor registered for ${contractAction.domain.wireValue}."
            }

            val workingPage = page.copy(
                title = workingTitle,
                content = PageBlockCodec.encodeDocument(workingDocument),
            )
            val result = domainExecutor.execute(
                AiPageActionDomainRequest(
                    engine = mutationEngine,
                    page = workingPage,
                    title = workingTitle,
                    document = workingDocument,
                    action = action,
                    contractAction = contractAction,
                    hasPendingDocumentChanges = documentChanged,
                ),
            )

            messages += result.messages
            createdPages += result.createdPages
            createdTasks += result.createdTasks
            createdReminders += result.createdReminders
            pageLinks += result.pageLinks
            validationIssues += result.validationIssues.map { issue ->
                issue.copy(
                    actionIndex = actionIndex,
                    actionType = issue.actionType.ifBlank { trace.actionType },
                    actionDomain = issue.actionDomain.ifBlank { trace.domain.id },
                )
            }
            undoCommands += result.undoCommands.map { command ->
                command.copy(actionIndex = actionIndex)
            }
            if (result.executedActionIndexes.isNotEmpty()) {
                executedActionIndexes += actionIndex
            }

            result.updatedTitle?.let { updatedTitle ->
                workingTitle = updatedTitle
                titleChanged = true
            }
            result.updatedDocument?.let { updatedDocument ->
                workingDocument = updatedDocument
                documentChanged = true
            }
        }

        return AiPageActionExecutionResult(
            messages = messages,
            updatedTitle = workingTitle.takeIf { titleChanged },
            updatedDocument = workingDocument.takeIf { documentChanged },
            createdPages = createdPages,
            createdTasks = createdTasks,
            createdReminders = createdReminders,
            pageLinks = pageLinks.distinctBy { link ->
                "${link.pageId}:${link.targetType}:${link.targetId}"
            },
            validationIssues = validationIssues,
            undoCommands = undoCommands,
            executedActionIndexes = executedActionIndexes.distinct(),
        )
    }
}
