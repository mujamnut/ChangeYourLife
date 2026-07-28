package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.AiUndoCommandSummary
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.deleteCreatedPageUndo
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.AiAppliedActionClaimResult
import com.changeyourlife.cyl.domain.repository.AiAppliedActionLedgerRepository
import com.changeyourlife.cyl.domain.repository.AiAppliedActionRecord
import com.changeyourlife.cyl.domain.repository.AiAppliedActionState
import com.changeyourlife.cyl.domain.repository.NoOpAiAppliedActionLedgerRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.usecase.ApplyAiUndoCommandsUseCase
import com.changeyourlife.cyl.domain.usecase.ReconcileTableDateRemindersUseCase
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import com.changeyourlife.cyl.presentation.ai.toPageTableColumnFromAi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject

class AiActionExecutionUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val aiPageActionExecutor: AiPageActionExecutor,
    private val reconcileTableDateRemindersUseCase: ReconcileTableDateRemindersUseCase,
    private val applyAiUndoCommandsUseCase: ApplyAiUndoCommandsUseCase,
    private val appliedActionLedgerRepository: AiAppliedActionLedgerRepository =
        NoOpAiAppliedActionLedgerRepository,
) {
    suspend fun execute(
        workspaceId: String,
        scopedTargetPage: Page?,
        actions: List<ChatAction>,
    ): AiActionExecutionResult {
        return executeCandidates(
            workspaceId = workspaceId,
            scopedTargetPage = scopedTargetPage,
            actions = actions.mapIndexed { index, action ->
                AiActionExecutionCandidate(
                    originalIndex = index,
                    action = action,
                )
            },
        )
    }

    suspend fun executeCandidates(
        workspaceId: String,
        scopedTargetPage: Page?,
        actions: List<AiActionExecutionCandidate>,
        idempotencyKey: String = "",
        auditId: String = "",
    ): AiActionExecutionResult {
        if (actions.isEmpty()) return AiActionExecutionResult()
        val requestKey = idempotencyKey.trim()
        if (requestKey.isBlank()) {
            return executeClaimedCandidates(
                workspaceId = workspaceId,
                scopedTargetPage = scopedTargetPage,
                actions = actions,
            )
        }

        val requestAuditId = auditId.ifBlank { "ai-action:$requestKey" }
        val claimedActions = mutableListOf<AiActionExecutionCandidate>()
        val replayedActionIndexes = mutableListOf<Int>()
        val ledgerIssues = mutableListOf<ChatActionValidationMetadata>()
        actions.forEach { candidate ->
            val now = System.currentTimeMillis()
            val actionKey = requestKey.toActionIdempotencyKey(candidate.originalIndex)
            when (
                val claim = appliedActionLedgerRepository.claim(
                    AiAppliedActionRecord(
                        idempotencyKey = actionKey,
                        requestMessageId = requestKey,
                        workspaceId = workspaceId,
                        auditId = requestAuditId,
                        actionIndex = candidate.originalIndex,
                        actionType = candidate.action.type,
                        actionFingerprint = candidate.action.ledgerFingerprint(),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            ) {
                AiAppliedActionClaimResult.Acquired -> claimedActions += candidate
                is AiAppliedActionClaimResult.Existing -> {
                    when (claim.record.state) {
                        AiAppliedActionState.Applied -> {
                            replayedActionIndexes += candidate.originalIndex
                        }

                        AiAppliedActionState.Claimed -> {
                            ledgerIssues += candidate.toLedgerIssue(
                                code = "idempotent_action_in_progress",
                                message = "This AI action is already claimed and will not be executed twice.",
                            )
                        }

                        AiAppliedActionState.Rejected,
                        AiAppliedActionState.Failed
                        -> ledgerIssues += candidate.toLedgerIssue(
                            code = "previous_action_${claim.record.state.wireValue.lowercase()}",
                            message = claim.record.failure.ifBlank {
                                "This AI action was already processed and was not applied."
                            },
                        )
                    }
                }
                is AiAppliedActionClaimResult.Conflict -> {
                    ledgerIssues += candidate.toLedgerIssue(
                        code = "idempotency_conflict",
                        message = "The idempotency key was already used for a different AI action.",
                    )
                }
            }
        }

        if (
            claimedActions.isNotEmpty() &&
            (ledgerIssues.isNotEmpty() || replayedActionIndexes.isNotEmpty())
        ) {
            val reason = "The complete AI plan could not be claimed atomically, so no remaining action was executed."
            val now = System.currentTimeMillis()
            claimedActions.forEach { candidate ->
                runCatching {
                    appliedActionLedgerRepository.markFailed(
                        idempotencyKey = requestKey.toActionIdempotencyKey(candidate.originalIndex),
                        reason = reason,
                        updatedAt = now,
                    )
                }
            }
            return AiActionExecutionResult(
                validationIssues = ledgerIssues + claimedActions.map { candidate ->
                    candidate.toLedgerIssue(
                        code = "atomic_plan_claim_incomplete",
                        message = reason,
                    )
                },
                executedActionIndexes = replayedActionIndexes.distinct(),
            )
        }

        if (claimedActions.isEmpty()) {
            return AiActionExecutionResult(
                validationIssues = ledgerIssues,
                executedActionIndexes = replayedActionIndexes.distinct(),
            )
        }

        val execution = executeClaimedCandidates(
            workspaceId = workspaceId,
            scopedTargetPage = scopedTargetPage,
            actions = claimedActions,
        )
        val executedIndexes = execution.executedActionIndexes.toSet()
        claimedActions.forEach { candidate ->
            val actionKey = requestKey.toActionIdempotencyKey(candidate.originalIndex)
            val now = System.currentTimeMillis()
            val issue = execution.validationIssues
                .firstOrNull { validation -> validation.actionIndex == candidate.originalIndex }
            when {
                candidate.originalIndex in executedIndexes -> {
                    runCatching {
                        appliedActionLedgerRepository.markApplied(actionKey, now)
                    }
                }
                issue != null -> {
                    runCatching {
                        appliedActionLedgerRepository.markRejected(
                            idempotencyKey = actionKey,
                            reason = issue.message,
                            updatedAt = now,
                        )
                    }
                }
                else -> {
                    runCatching {
                        appliedActionLedgerRepository.markFailed(
                            idempotencyKey = actionKey,
                            reason = execution.messages.joinToString(" | ").ifBlank {
                                "The executor returned without applying this action."
                            },
                            updatedAt = now,
                        )
                    }
                }
            }
        }

        return execution.copy(
            validationIssues = ledgerIssues + execution.validationIssues,
            executedActionIndexes = (
                replayedActionIndexes +
                    execution.executedActionIndexes
                ).distinct(),
        )
    }

    private suspend fun executeClaimedCandidates(
        workspaceId: String,
        scopedTargetPage: Page?,
        actions: List<AiActionExecutionCandidate>,
    ): AiActionExecutionResult {
        val execution = executeCandidatePlan(
            workspaceId = workspaceId,
            scopedTargetPage = scopedTargetPage,
            actions = actions,
        )
        if (
            !execution.hasPlanFailure() ||
            execution.executedActionIndexes.isEmpty() && execution.undoCommands.isEmpty()
        ) {
            return execution
        }
        if (execution.undoCommands.isEmpty()) {
            return execution.copy(
                validationIssues = execution.validationIssues + atomicPlanIssue(
                    code = "atomic_rollback_unavailable",
                    message = "The AI plan failed, but no rollback payload was available.",
                ),
            )
        }

        val rollback = withContext(NonCancellable) {
            runCatching {
                applyAiUndoCommandsUseCase(
                    undoCommands = execution.undoCommands,
                    fallbackPageId = scopedTargetPage?.id.orEmpty(),
                )
            }
        }.getOrElse { error ->
            return execution.copy(
                validationIssues = execution.validationIssues + atomicPlanIssue(
                    code = "atomic_rollback_failed",
                    message = error.message
                        ?.let { detail -> "The AI plan failed and rollback crashed: $detail" }
                        ?: "The AI plan failed and rollback crashed.",
                ),
            )
        }
        if (!rollback.succeeded) {
            return execution.copy(
                validationIssues = execution.validationIssues + atomicPlanIssue(
                    code = "atomic_rollback_failed",
                    message = rollback.failures.joinToString(
                        separator = " ",
                        prefix = "The AI plan failed and rollback was incomplete: ",
                    ) { failure -> failure.message },
                ),
            )
        }

        return execution.copy(
            messages = execution.messages.filterNot { message ->
                message.trimStart().startsWith("Done:", ignoreCase = true)
            },
            pageLinks = emptyList(),
            validationIssues = execution.validationIssues + atomicPlanIssue(
                code = "atomic_plan_rolled_back",
                message = "One action failed, so every change from this AI plan was rolled back.",
            ),
            undoCommands = emptyList(),
            executedActionIndexes = emptyList(),
        )
    }

    private suspend fun executeCandidatePlan(
        workspaceId: String,
        scopedTargetPage: Page?,
        actions: List<AiActionExecutionCandidate>,
    ): AiActionExecutionResult {
        val globalActions = actions.filter { candidate -> candidate.action.isHomeScopedAction() }
        val pageActions = actions.filterNot { candidate -> candidate.action.isHomeScopedAction() }
        val globalResult = executeHomeScopedActions(workspaceId, globalActions)
        if (globalResult.hasPlanFailure()) return globalResult
        val pageResult = if (pageActions.isEmpty()) {
            AiActionExecutionResult()
        } else {
            executeTargetedPageActions(
                workspaceId = workspaceId,
                actions = pageActions,
                defaultPage = scopedTargetPage,
            )
        }
        return globalResult + pageResult
    }

    private suspend fun executeHomeScopedActions(
        workspaceId: String,
        actions: List<AiActionExecutionCandidate>,
    ): AiActionExecutionResult {
        if (actions.isEmpty()) return AiActionExecutionResult()
        val messages = mutableListOf<String>()
        val pageLinks = mutableListOf<AiChatPageLink>()
        val validationIssues = mutableListOf<ChatActionValidationMetadata>()
        val undoCommands = mutableListOf<AiUndoCommandSummary>()
        val executedActionIndexes = mutableListOf<Int>()

        for (candidate in actions) {
            val outcome = runCatching {
                val action = candidate.action
                when (action.type.normalizedActionType()) {
                    "CREATE_PAGE",
                    "CREATE_DATABASE",
                    "CREATE_TABLE",
                    -> {
                        val pageTitle = action.homePageTitle()
                        val created = pageRepository.createPage(
                            workspaceId = workspaceId,
                            title = pageTitle,
                            content = action.toCreatedPageContent(),
                        )
                        pageLinks += created.toChatPageLink()
                        undoCommands += deleteCreatedPageUndo(
                            actionIndex = candidate.originalIndex,
                            pageId = created.id,
                        )
                        executedActionIndexes += candidate.originalIndex
                        reconcileTableDateRemindersUseCase.scheduleAll(
                            page = created,
                            document = PageBlockCodec.decodeDocument(created.content),
                        )
                        "Done: Created page ${created.title.ifBlank { "Untitled page" }}"
                    }

                    else -> error("Unsupported home action type: ${action.type}")
                }
            }
            outcome.onSuccess(messages::add)
            outcome.onFailure { error ->
                validationIssues += candidate.executionIssue(error)
                messages += error.toAiExecutionErrorMessage()
            }
            if (outcome.isFailure) break
        }

        return AiActionExecutionResult(
            messages = messages,
            pageLinks = pageLinks,
            validationIssues = validationIssues,
            undoCommands = undoCommands,
            executedActionIndexes = executedActionIndexes,
        )
    }

    private suspend fun executePageScopedActions(
        page: Page,
        actions: List<AiActionExecutionCandidate>,
    ): AiActionExecutionResult {
        val resolvedCandidates = actions.map { candidate ->
            AiActionExecutionCandidate(
                originalIndex = candidate.originalIndex,
                action = candidate.action.copy(
                    targetTitle = candidate.action.targetTitle.ifBlank { page.title },
                ),
            )
        }
        val supportedCandidates = mutableListOf<AiActionExecutionCandidate>()
        val unsupportedIssues = mutableListOf<ChatActionValidationMetadata>()
        resolvedCandidates.forEach { candidate ->
            if (aiPageActionExecutor.supports(candidate.action)) {
                supportedCandidates += candidate
            } else {
                val trace = AiActionExecutionRegistry.trace(candidate.originalIndex, candidate.action)
                unsupportedIssues += ChatActionValidationMetadata(
                    actionIndex = candidate.originalIndex,
                    actionType = trace.actionType,
                    actionDomain = trace.domain.id,
                    field = "type",
                    code = "unsupported_action_type",
                    message = "Unsupported action type: ${candidate.action.type}",
                )
            }
        }
        if (unsupportedIssues.isNotEmpty()) {
            return AiActionExecutionResult(validationIssues = unsupportedIssues)
        }
        if (supportedCandidates.isEmpty()) return AiActionExecutionResult()

        val execution = runCatching {
            aiPageActionExecutor.executeOnPage(
                page = page,
                title = page.title,
                document = PageBlockCodec.decodeDocument(page.content),
                actions = supportedCandidates.map { candidate -> candidate.action },
            )
        }.getOrElse { error ->
            return AiActionExecutionResult(
                messages = listOf(error.toAiExecutionErrorMessage()),
                validationIssues = listOf(supportedCandidates.first().executionIssue(error)),
            )
        }

        val didUpdatePage = execution.updatedTitle != null || execution.updatedDocument != null
        val previousDocument = PageBlockCodec.decodeDocument(page.content)
        val currentDocument = execution.updatedDocument ?: previousDocument
        var updatedPage = page
        var persistenceIssue: ChatActionValidationMetadata? = null
        if (didUpdatePage) {
            val nextPage = page.copy(
                title = execution.updatedTitle ?: page.title,
                content = PageBlockCodec.encodeDocument(currentDocument),
                updatedAt = System.currentTimeMillis(),
            )
            runCatching {
                pageRepository.upsertPage(nextPage)
                reconcileTableDateRemindersUseCase(
                    previousPage = page,
                    currentPage = nextPage,
                    previousDocument = previousDocument,
                    currentDocument = currentDocument,
                )
            }.onSuccess {
                updatedPage = nextPage
            }.onFailure { error ->
                val failedCandidate = execution.executedActionIndexes
                    .lastOrNull()
                    ?.let(supportedCandidates::getOrNull)
                    ?: supportedCandidates.first()
                persistenceIssue = failedCandidate.executionIssue(
                    error = error,
                    code = "page_commit_failed",
                )
            }
        }

        val pageLinks = buildList {
            if (didUpdatePage && persistenceIssue == null) add(updatedPage.toChatPageLink())
            addAll(execution.pageLinks)
            addAll(execution.createdPages.map { createdPage -> createdPage.toChatPageLink() })
        }.distinctBy { link -> "${link.pageId}:${link.targetType}:${link.targetId}" }
        val messages = execution.messages.ifEmpty {
            if (didUpdatePage && persistenceIssue == null) {
                listOf("Done: Updated ${updatedPage.title.ifBlank { "Untitled page" }}")
            } else {
                emptyList()
            }
        } + listOfNotNull(persistenceIssue?.message)

        return AiActionExecutionResult(
            messages = messages,
            pageLinks = pageLinks,
            validationIssues = execution.validationIssues.map { issue ->
                ChatActionValidationMetadata(
                    actionIndex = issue.actionIndex?.let { index ->
                        supportedCandidates.getOrNull(index)?.originalIndex ?: index
                    },
                    actionType = issue.actionType,
                    actionDomain = issue.actionDomain,
                    field = issue.field,
                    code = issue.code,
                    message = issue.message,
                )
            } + listOfNotNull(persistenceIssue),
            undoCommands = execution.undoCommands.map { command ->
                command.copy(
                    actionIndex = supportedCandidates.getOrNull(command.actionIndex)?.originalIndex
                        ?: command.actionIndex,
                )
            },
            executedActionIndexes = execution.executedActionIndexes.mapNotNull { index ->
                supportedCandidates.getOrNull(index)?.originalIndex
            },
        )
    }

    private suspend fun executeTargetedPageActions(
        workspaceId: String,
        actions: List<AiActionExecutionCandidate>,
        defaultPage: Page?,
    ): AiActionExecutionResult {
        val pages = pageRepository.observePages(workspaceId).first()
        val needsDeletedPages = actions.any { candidate ->
            candidate.action.type.normalizedActionType() in DeletedPageActionTypes
        }
        val deletedPages = if (needsDeletedPages) {
            pageRepository.observeDeletedPages(workspaceId).first()
        } else {
            emptyList()
        }
        val canonicalDefaultPage = defaultPage?.let { page ->
            (pages + deletedPages).firstOrNull { candidate -> candidate.id == page.id } ?: page
        }
        val targetPagesById = linkedMapOf<String, Page>()
        val groupedActions = linkedMapOf<String, MutableList<AiActionExecutionCandidate>>()
        val validationIssues = mutableListOf<ChatActionValidationMetadata>()

        actions.forEach { candidate ->
            val actionType = candidate.action.type.normalizedActionType()
            val candidatePages = if (actionType in DeletedPageActionTypes) deletedPages else pages
            val eligibleDefaultPage = canonicalDefaultPage?.takeIf { page ->
                candidatePages.any { candidatePage -> candidatePage.id == page.id }
            }
            val targetTitle = candidate.action.targetTitle.trim()
            val resolution = if (targetTitle.isBlank()) {
                eligibleDefaultPage
                    ?.let { page -> TargetPageResolution.Found(page) }
                    ?: TargetPageResolution.Missing
            } else {
                val matchesDefaultPage = eligibleDefaultPage
                    ?.let { page ->
                        AiPageTargetResolver.resolveExactTarget(
                            pages = listOf(page),
                            rawTitle = targetTitle,
                        )
                    }
                if (matchesDefaultPage is TargetPageResolution.Found) {
                    matchesDefaultPage
                } else {
                    AiPageTargetResolver.resolveExactTarget(candidatePages, targetTitle)
                }
            }

            if (targetTitle.isBlank() && eligibleDefaultPage == null) {
                validationIssues += candidate.targetPageIssue(
                    code = "target_page_required",
                    message = "This action needs a page target. Mention a page with @ or open the page before asking AI to edit it.",
                )
                return@forEach
            }

            when (resolution) {
                is TargetPageResolution.Found -> {
                    targetPagesById[resolution.page.id] = resolution.page
                    groupedActions.getOrPut(resolution.page.id) { mutableListOf() } += candidate
                }
                TargetPageResolution.Ambiguous -> {
                    validationIssues += candidate.targetPageIssue(
                        code = "target_page_ambiguous",
                        message = "More than one page matches '$targetTitle'. Mention the exact page from the picker before editing.",
                    )
                }
                TargetPageResolution.Missing -> {
                    validationIssues += candidate.targetPageIssue(
                        code = "target_page_not_found",
                        message = "I could not find an exact page named '$targetTitle'. Mention the page with @ before editing.",
                    )
                }
            }
        }

        if (validationIssues.isNotEmpty()) {
            return AiActionExecutionResult(validationIssues = validationIssues)
        }

        var result = AiActionExecutionResult()
        for (entry in groupedActions.entries) {
            val pageResult = executePageScopedActions(
                page = requireNotNull(targetPagesById[entry.key]),
                actions = entry.value,
            )
            result += pageResult
            if (pageResult.hasPlanFailure()) break
        }
        return result
    }
}

private fun String.toActionIdempotencyKey(actionIndex: Int): String {
    return "$this:action:$actionIndex"
}

private fun ChatAction.ledgerFingerprint(): String {
    val canonicalAction = copy(
        cellValues = cellValues.toSortedMap(),
        tableRows = tableRows.map { row -> row.toSortedMap() },
    )
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonicalAction.toString().toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun AiActionExecutionCandidate.toLedgerIssue(
    code: String,
    message: String,
): ChatActionValidationMetadata {
    val trace = AiActionExecutionRegistry.trace(originalIndex, action)
    return ChatActionValidationMetadata(
        actionIndex = originalIndex,
        actionType = trace.actionType,
        actionDomain = trace.domain.id,
        field = "idempotencyKey",
        code = code,
        message = message,
    )
}

private fun AiActionExecutionCandidate.targetPageIssue(
    code: String,
    message: String,
): ChatActionValidationMetadata {
    val trace = AiActionExecutionRegistry.trace(originalIndex, action)
    return ChatActionValidationMetadata(
        actionIndex = originalIndex,
        actionType = trace.actionType,
        actionDomain = trace.domain.id,
        field = "targetTitle",
        code = code,
        message = message,
    )
}

private fun AiActionExecutionCandidate.executionIssue(
    error: Throwable,
    code: String = "execution_failed",
): ChatActionValidationMetadata {
    val trace = AiActionExecutionRegistry.trace(originalIndex, action)
    return ChatActionValidationMetadata(
        actionIndex = originalIndex,
        actionType = trace.actionType,
        actionDomain = trace.domain.id,
        field = "type",
        code = code,
        message = error.message ?: "Action failed before it could be committed.",
    )
}

private fun atomicPlanIssue(
    code: String,
    message: String,
): ChatActionValidationMetadata {
    return ChatActionValidationMetadata(
        actionIndex = null,
        actionType = "ATOMIC_PLAN",
        actionDomain = "plan",
        field = "actions",
        code = code,
        message = message,
    )
}

data class AiActionExecutionResult(
    val messages: List<String> = emptyList(),
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val validationIssues: List<ChatActionValidationMetadata> = emptyList(),
    val undoCommands: List<AiUndoCommandSummary> = emptyList(),
    val executedActionIndexes: List<Int> = emptyList(),
)

private fun AiActionExecutionResult.hasPlanFailure(): Boolean {
    return validationIssues.isNotEmpty()
}

operator fun AiActionExecutionResult.plus(other: AiActionExecutionResult): AiActionExecutionResult {
    return AiActionExecutionResult(
        messages = messages + other.messages,
        pageLinks = (pageLinks + other.pageLinks).distinctBy { link ->
            "${link.pageId}:${link.targetType}:${link.targetId}"
        },
        validationIssues = validationIssues + other.validationIssues,
        undoCommands = undoCommands + other.undoCommands,
        executedActionIndexes = (executedActionIndexes + other.executedActionIndexes).distinct(),
    )
}

private fun ChatAction.isHomeScopedAction(): Boolean {
    val actionType = type.normalizedActionType()
    return actionType == "CREATE_PAGE" ||
        (actionType in setOf("CREATE_DATABASE", "CREATE_TABLE") && targetTitle.isBlank())
}

private val DeletedPageActionTypes = setOf("RESTORE_PAGE", "DELETE_PAGE_PERMANENTLY")

private fun ChatAction.toCreatedPageContent(): String {
    val actionType = type.normalizedActionType()
    val moduleType = requestedModuleType(actionType)
    if (moduleType != null) return PageModuleTemplates.contentFor(moduleType)
    val blocks = buildList {
        if (tableTitle.isNotBlank() || tableColumns.isNotEmpty() || tableRows.isNotEmpty() || cellValues.isNotEmpty()) {
            add(toDatabaseBlock())
        }
        if (content.isNotBlank()) {
            add(PageBlockCodec.newBlock(PageBlockType.Text).copy(text = content.trim()))
        }
    }
    return PageBlockCodec.encodeDocument(PageBlockDocument(blocks = blocks))
}

private fun ChatAction.homePageTitle(): String {
    return title
        .ifBlank { tableTitle }
        .ifBlank { content }
        .ifBlank { "Untitled page" }
}

private fun ChatAction.requestedModuleType(actionType: String): PageModuleType? {
    val normalizedActionType = actionType.replace("_", "")
    val isModuleAction = normalizedActionType.startsWith("CREATE") &&
        (
            normalizedActionType.contains("MODULE") ||
                normalizedActionType.contains("TRACKER") ||
                normalizedActionType.contains("PLANNER")
            )
    if (isModuleAction) {
        return PageModuleTemplates.fromActionFields(
            moduleType,
            type,
            title,
            tableTitle,
            content,
            blockType,
        ) ?: error("Missing module type. Use Goal, Habit, Travel, or Budget.")
    }

    if (actionType != "CREATE_PAGE") return null
    if (moduleType.isNotBlank()) {
        return PageModuleType.from(moduleType)
    }
    val looksLikeModulePage = title.looksLikeModuleTitle() ||
        tableTitle.looksLikeModuleTitle() ||
        content.looksLikeModuleTitle()
    if (!looksLikeModulePage) return null
    return PageModuleTemplates.fromActionFields(title, tableTitle, content)
}

private fun String.looksLikeModuleTitle(): Boolean {
    val value = trim().lowercase()
    if (value.isBlank()) return false
    return value.contains("module") ||
        value.contains("tracker") ||
        value.contains("planner") ||
        value.contains("itinerary")
}

private fun ChatAction.toDatabaseBlock(): PageBlock {
    val tableName = tableTitle
        .ifBlank { title }
        .ifBlank { content }
        .ifBlank { "AI database" }
    val columns = buildTableColumns()
    val rows = buildTableRows(columns)

    return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
        table = PageTable(
            title = tableName,
            view = tableView.toPageTableView(),
            columns = columns,
            rows = rows,
        ),
    )
}

private fun ChatAction.buildTableColumns(): List<PageTableColumn> {
    val fromAction = tableColumns.mapNotNull { column ->
        val name = column.name.trim()
        if (name.isBlank()) {
            null
        } else {
            column.toPageTableColumnFromAi()
        }
    }
    if (fromAction.isNotEmpty()) return fromAction

    val keys = (tableRows.flatMap { row -> row.keys } + cellValues.keys)
        .map { key -> key.trim() }
        .filter { key -> key.isNotBlank() }
        .distinctBy { key -> key.normalizedAiKey() }
    if (keys.isNotEmpty()) {
        return keys.map { key -> PageBlockCodec.newTableColumn(key, key.inferTableColumnType()) }
    }

    return listOf(
        PageBlockCodec.newTableColumn("Name"),
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status),
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date),
    )
}

private fun ChatAction.buildTableRows(columns: List<PageTableColumn>): List<PageTableRow> {
    val rowMaps = when {
        tableRows.isNotEmpty() -> tableRows
        cellValues.isNotEmpty() -> listOf(cellValues)
        rowTitle.isNotBlank() || content.isNotBlank() -> listOf(mapOf(columns.first().name to rowTitle.ifBlank { content }))
        else -> emptyList()
    }
    return rowMaps
        .filter { values -> values.values.any { value -> value.isNotBlank() } }
        .map { values -> columns.newRow(values) }
}

private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
    val valuesByNormalizedName = valuesByColumnName.entries.associate { entry ->
        entry.key.normalizedAiKey() to entry.value
    }
    val cellsByColumnId = associate { column ->
        column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty()
    }
    return PageBlockCodec.newTableRow(this).copy(
        cells = cellsByColumnId,
        cellValues = associate { column ->
            val displayValue = cellsByColumnId[column.id].orEmpty()
            column.id to column.toTypedCellValue(displayValue)
        },
    )
}

private fun String.toPageTableColumnType(): PageTableColumnType {
    return when (normalizedAiKey()) {
        "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
        "select", "option", "choice" -> PageTableColumnType.Select
        "multiselect", "multi select", "multi-select", "tags", "tag", "labels", "label" -> PageTableColumnType.MultiSelect
        "status", "stage", "state", "phase" -> PageTableColumnType.Status
        "date", "day", "deadline", "due", "time", "calendar" -> PageTableColumnType.Date
        "file", "files", "media", "attachment", "attachments", "image", "photo", "video", "filesmedia", "filemedia" -> PageTableColumnType.FilesMedia
        "checkbox", "check", "done", "complete", "completed", "boolean" -> PageTableColumnType.Checkbox
        "formula", "calculation", "calculate", "computed" -> PageTableColumnType.Formula
        "relation", "related", "link", "linkedrow", "linkedrows" -> PageTableColumnType.Relation
        "rollup", "aggregate", "aggregation" -> PageTableColumnType.Rollup
        else -> PageTableColumnType.Text
    }
}

private fun String.inferTableColumnType(): PageTableColumnType {
    return toPageTableColumnType()
}

private fun String.toPageTableView(): PageTableView {
    return when (normalizedAiKey()) {
        "list" -> PageTableView.List
        "board", "kanban" -> PageTableView.Board
        "calendar" -> PageTableView.Calendar
        "gallery" -> PageTableView.Gallery
        "timeline" -> PageTableView.Timeline
        "dashboard", "chart", "charts" -> PageTableView.Dashboard
        else -> PageTableView.Table
    }
}

private fun String.normalizedActionType(): String =
    trim()
        .uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')

private fun String.normalizedAiKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")
}

private fun Page.toChatPageLink(
    targetType: String = "",
    targetId: String = "",
): AiChatPageLink {
    return AiChatPageLink(
        pageId = id,
        title = title.ifBlank { "Untitled page" },
        targetType = targetType,
        targetId = targetId,
    )
}

private fun Throwable.toAiExecutionErrorMessage(): String {
    val root = generateSequence(this) { error -> error.cause }.last()
    val detail = root.localizedMessage?.takeIf { message -> message.isNotBlank() }
        ?: "AI edit failed before it could update the page. (${root.javaClass.simpleName})"
    return "Failed: $detail"
}
