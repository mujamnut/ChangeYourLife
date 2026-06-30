package com.changeyourlife.cyl.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.core.network.toBackendConnectionMessage
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.Workspace
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AiBlockContext
import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatActionValidationMetadata
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiStatus
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatTableColumn
import com.changeyourlife.cyl.presentation.ai.AiChatActionMetadata
import com.changeyourlife.cyl.presentation.ai.AiChatActionMetadataItem
import com.changeyourlife.cyl.presentation.ai.AiChatActionValidationIssue
import com.changeyourlife.cyl.presentation.ai.AiChatMode
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.ai.AiPageActionExecutor
import com.changeyourlife.cyl.presentation.ai.toPageTableColumnFromAi
import com.changeyourlife.cyl.presentation.ai.toRoleContentPairs
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val pageRepository: PageRepository,
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val aiRepository: AiRepository,
    private val aiPageActionExecutor: AiPageActionExecutor,
    private val chatHistoryRepository: ChatHistoryRepository,
) : ViewModel() {
    private val workspaceFormState = MutableStateFlow(WorkspaceFormState())
    private val activeChatSessionId = MutableStateFlow<String?>(null)
    private val activeWorkspaceChatScope: Flow<String> = workspaceRepository.observeActiveWorkspaceId()
        .map { workspaceId ->
            homeChatScopeId(
                workspaceId
                    ?.takeIf { it.isNotBlank() }
                    ?: CylDefaults.DefaultWorkspaceId,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val chatSessions: Flow<List<ChatSession>> = activeWorkspaceChatScope
        .flatMapLatest { scopeId -> chatHistoryRepository.observeSessions(scopeId) }

    private val effectiveChatSessionId: Flow<String?> = combine(
        chatSessions,
        activeChatSessionId,
    ) { sessions, selectedSessionId ->
        selectedSessionId
            ?.takeUnless { sessionId -> sessionId == DraftHomeChatSessionId }
            ?.takeIf { sessionId -> sessions.any { session -> session.id == sessionId } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val chatMessages: Flow<List<AiChatMessage>> = effectiveChatSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                chatHistoryRepository.observeMessages(sessionId)
                    .map { messages -> messages.toAiChatMessages() }
            }
    }
    private val isAiGeneratingChat = MutableStateFlow(false)
    private val aiChatError = MutableStateFlow<String?>(null)
    private val aiChatMode = MutableStateFlow(AiChatMode.Planning)
    private val aiModelLabel = MutableStateFlow("AI model")
    private val searchQuery = MutableStateFlow("")
    private val chatHistorySearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val chatHistorySearchMessages: Flow<List<ChatMessage>> = activeWorkspaceChatScope
        .flatMapLatest { scopeId -> chatHistoryRepository.observeMessagesForScope(scopeId) }

    private val chatSessionPreviews: Flow<Map<String, ChatSessionPreview>> = combine(
        chatSessions,
        chatHistorySearchMessages,
    ) { sessions, messages ->
        sessions.toChatSessionPreviews(messages)
    }

    private val chatMetaState: Flow<HomeChatMetaState> = combine(
        chatSessions,
        chatSessionPreviews,
        effectiveChatSessionId,
    ) { sessions, previews, activeSessionId ->
        HomeChatMetaState(
            sessions = sessions,
            previews = previews,
            activeSessionId = activeSessionId,
        )
    }

    private val chatState: Flow<HomeChatState> = combine(
        chatMessages,
        chatMetaState,
        isAiGeneratingChat,
        aiChatError,
        aiChatMode,
    ) { messages, meta, isGenerating, error, mode ->
        HomeChatState(
            messages = messages,
            sessions = meta.sessions,
            previews = meta.previews,
            activeSessionId = meta.activeSessionId,
            isGenerating = isGenerating,
            error = error,
            mode = mode,
        )
    }

    private val chatHistorySearchResults: Flow<List<ChatHistorySearchResult>> = combine(
        chatSessions,
        chatHistorySearchMessages,
        chatHistorySearchQuery,
    ) { sessions, messages, query ->
        sessions.toChatHistorySearchResults(messages, query)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dashboardState: Flow<HomeUiState> = combine(
        workspaceRepository.observeWorkspaces(),
        workspaceRepository.observeActiveWorkspaceId(),
    ) { workspaces, activeWorkspaceId ->
        val selectedWorkspace = workspaces.firstOrNull { workspace -> workspace.id == activeWorkspaceId }
            ?: workspaces.firstOrNull { workspace -> workspace.id == CylDefaults.DefaultWorkspaceId }
            ?: workspaces.firstOrNull()

        WorkspaceDashboardState(
            workspaces = workspaces,
            activeWorkspace = selectedWorkspace,
        )
    }.flatMapLatest { workspaceState ->
        val activeWorkspace = workspaceState.activeWorkspace
        if (activeWorkspace == null) {
            flowOf(
                HomeUiState(
                    isLoading = false,
                    workspaces = workspaceState.workspaces,
                ),
            )
        } else {
            combine(
                pageRepository.observePages(activeWorkspace.id),
                pageRepository.observeDeletedPages(activeWorkspace.id),
                taskRepository.observeOpenTaskCount(activeWorkspace.id),
                taskRepository.observeOpenTasks(activeWorkspace.id),
                reminderRepository.observePendingReminders(activeWorkspace.id),
            ) { pages, deletedPages, openTaskCount, openTasks, reminders ->
                val sortedPages = pages.sortedByDescending { page -> page.updatedAt }
                HomeUiState(
                    isLoading = false,
                    activeWorkspaceId = activeWorkspace.id,
                    workspaceName = activeWorkspace.name,
                    workspaceCount = workspaceState.workspaces.size,
                    workspaces = workspaceState.workspaces,
                    pageCount = pages.size,
                    openTaskCount = openTaskCount,
                    pendingReminderCount = reminders.size,
                    allPages = sortedPages,
                    recentPages = sortedPages.take(5),
                    deletedPages = deletedPages,
                    openTasks = openTasks,
                    reminders = reminders,
                )
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dashboardState,
        workspaceFormState,
        chatState,
        chatHistorySearchQuery,
        chatHistorySearchResults,
    ) { dashboard, form, chat, chatSearchQuery, chatSearchResults ->
        dashboard.copy(
            isCreateWorkspaceDialogVisible = form.isCreateWorkspaceDialogVisible,
            newWorkspaceName = form.newWorkspaceName,
            canCreateWorkspace = form.newWorkspaceName.isNotBlank(),
            chatMessages = chat.messages,
            chatSessions = chat.sessions,
            chatSessionPreviews = chat.previews,
            activeChatSessionId = chat.activeSessionId,
            isAiGeneratingChat = chat.isGenerating,
            aiChatError = chat.error,
            aiChatMode = chat.mode,
            chatHistorySearchQuery = chatSearchQuery,
            chatHistorySearchResults = chatSearchResults,
        )
    }.let { baseState ->
        combine(baseState, searchQuery, aiModelLabel) { state, query, modelLabel ->
            state.copy(
                searchQuery = query,
                searchResults = state.allPages.toSearchResults(query),
                aiModelLabel = modelLabel,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            workspaceRepository.ensureDefaultWorkspace()
            startFreshHomeChatSessionOnLaunch()
            refreshAiStatus()
        }
    }

    private suspend fun refreshAiStatus() {
        aiRepository.status()
            .onSuccess { status ->
                aiModelLabel.value = status.toDisplayModelLabel()
            }
            .onFailure {
                aiModelLabel.value = "AI model"
            }
    }

    suspend fun createQuickPage(): Page {
        workspaceRepository.ensureDefaultWorkspace()
        val workspaceId = workspaceRepository.getActiveWorkspaceId()
            ?.takeIf { it.isNotBlank() }
            ?: CylDefaults.DefaultWorkspaceId
        return pageRepository.createPage(
            workspaceId = workspaceId,
            title = "Untitled page",
        )
    }

    fun createQuickPage(onCreated: (Page) -> Unit) {
        viewModelScope.launch {
            onCreated(createQuickPage())
        }
    }

    suspend fun createModulePage(type: PageModuleType): Page {
        workspaceRepository.ensureDefaultWorkspace()
        val workspaceId = workspaceRepository.getActiveWorkspaceId()
            ?.takeIf { it.isNotBlank() }
            ?: CylDefaults.DefaultWorkspaceId
        return pageRepository.createPage(
            workspaceId = workspaceId,
            title = PageModuleTemplates.defaultTitle(type),
            content = PageModuleTemplates.contentFor(type),
        )
    }

    fun createModulePage(
        type: PageModuleType,
        onCreated: (Page) -> Unit,
    ) {
        viewModelScope.launch {
            onCreated(createModulePage(type))
        }
    }

    fun addBlockToPage(
        pageId: String,
        type: PageBlockType,
        onAdded: (pageId: String, blockId: String) -> Unit,
    ) {
        viewModelScope.launch {
            val page = pageRepository.getPage(pageId) ?: return@launch
            val block = PageBlockCodec.newBlock(type)
            val updatedPage = appendBlockToPage(page, block)
            pageRepository.upsertPage(updatedPage)
            onAdded(page.id, block.id)
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            pageRepository.deletePage(pageId)
        }
    }

    fun restorePage(pageId: String) {
        viewModelScope.launch {
            pageRepository.restorePage(pageId)
        }
    }

    fun deletePagePermanently(pageId: String) {
        viewModelScope.launch {
            pageRepository.deletePagePermanently(pageId)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        viewModelScope.launch {
            workspaceRepository.setActiveWorkspace(workspaceId)
        }
    }

    fun showCreateWorkspace() {
        workspaceFormState.update {
            it.copy(isCreateWorkspaceDialogVisible = true)
        }
    }

    fun hideCreateWorkspace() {
        workspaceFormState.update {
            it.copy(
                isCreateWorkspaceDialogVisible = false,
                newWorkspaceName = "",
            )
        }
    }

    fun updateNewWorkspaceName(name: String) {
        workspaceFormState.update {
            it.copy(newWorkspaceName = name)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    fun updateChatHistorySearchQuery(query: String) {
        chatHistorySearchQuery.value = query
    }

    fun clearChatHistorySearchQuery() {
        chatHistorySearchQuery.value = ""
    }

    fun updateAiChatMode(mode: AiChatMode) {
        aiChatMode.value = mode
    }

    fun createWorkspace() {
        val name = workspaceFormState.value.newWorkspaceName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            workspaceRepository.createWorkspace(name)
            workspaceFormState.update {
                it.copy(
                    isCreateWorkspaceDialogVisible = false,
                    newWorkspaceName = "",
                )
            }
        }
    }

    fun completeTask(task: TaskItem) {
        viewModelScope.launch {
            completeTaskAndReminder(task)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun sendChatMessage(
        prompt: String,
        mentionedPageIds: List<String> = emptyList(),
        mode: AiChatMode = aiChatMode.value,
    ) {
        sendChatMessageScoped(
            prompt = prompt,
            mentionedPageIds = mentionedPageIds,
            mode = mode,
            attachedPageId = null,
        )
    }

    fun sendChatMessage(
        prompt: String,
        mentionedPageIds: List<String>,
        mode: AiChatMode,
        attachedPageId: String?,
    ) {
        sendChatMessageScoped(
            prompt = prompt,
            mentionedPageIds = mentionedPageIds,
            mode = mode,
            attachedPageId = attachedPageId,
        )
    }

    private fun sendChatMessageScoped(
        prompt: String,
        mentionedPageIds: List<String>,
        mode: AiChatMode,
        attachedPageId: String?,
    ) {
        if (prompt.isBlank() || isAiGeneratingChat.value) return

        isAiGeneratingChat.value = true
        viewModelScope.launch {
            aiChatError.value = null

            val workspaceId = workspaceRepository.getActiveWorkspaceId()
                ?.takeIf { it.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId
            val scopeId = homeChatScopeId(workspaceId)
            val session = resolveActiveChatSession(scopeId)
            val currentMessages = chatHistoryRepository.observeMessages(session.id)
                .first()
                .toAiChatMessages()
            val userMessage = AiChatMessage(role = "user", content = prompt)
            val updatedMessages = currentMessages + userMessage
            val savedUserMessage = chatHistoryRepository.appendMessage(
                sessionId = session.id,
                role = userMessage.role,
                content = userMessage.content,
            )
            val pages = pageRepository.observePages(workspaceId)
                .first()
                .sortedByDescending { page -> page.updatedAt }
            val textMentionedPageIds = pages
                .findMentionedPages(prompt)
                .map { page -> page.id }
            val normalizedMentionedPageIds = (mentionedPageIds + textMentionedPageIds + listOfNotNull(attachedPageId))
                .filter { pageId -> pageId.isNotBlank() }
                .distinct()
            val attachedPage = attachedPageId
                ?.takeIf { pageId -> pageId.isNotBlank() }
                ?.let { pageId -> pages.firstOrNull { page -> page.id == pageId } }
            val scopedTargetPage = attachedPage
                ?: normalizedMentionedPageIds
                    .singleOrNull()
                    ?.let { pageId -> pages.firstOrNull { page -> page.id == pageId } }
            val explicitlyMentionedPages = pages.findPagesByIds(normalizedMentionedPageIds)
            val pageContext = pages
                .withMentionedPagesFirst(normalizedMentionedPageIds)
                .map { page -> page.toAiPageContext() }
            val openTasks = taskRepository.observeOpenTasks(workspaceId)
                .first()
            val taskContext = openTasks.map { task -> task.id to task.title }
            val allChatSessions = chatHistoryRepository.observeSessions(scopeId).first()
            val allChatMessages = chatHistoryRepository.observeMessagesForScope(scopeId)
                .first()
                .filterNot { message -> message.id == savedUserMessage.id }
            if (mode == AiChatMode.Planning) {
                buildHomeAiSearchReply(
                    prompt = prompt,
                    pages = pages,
                    tasks = openTasks,
                    chatSessions = allChatSessions,
                    chatMessages = allChatMessages,
                )?.let { searchReply ->
                    isAiGeneratingChat.value = false
                    chatHistoryRepository.appendMessage(
                        sessionId = session.id,
                        role = "assistant",
                        content = searchReply.content,
                        pageLinks = searchReply.pageLinks.toDomainChatPageLinks(),
                    )
                    return@launch
                }
            }

            val messagesForAi = currentMessages + userMessage.copy(
                content = prompt.withMentionContext(explicitlyMentionedPages),
            )
            aiRepository.chatWithActions(messagesForAi.toRoleContentPairs(), pages = pageContext, tasks = taskContext)
                .onSuccess { result ->
                    isAiGeneratingChat.value = false
                    val assistantReply = result.reply.ifBlank {
                        "I received your message, but the AI returned an empty reply."
                    }.sanitizeAiUserVisibleText()
                    val localActions = if (mode == AiChatMode.Planning) {
                        emptyList()
                    } else {
                        recoverPageWriteActions(
                            prompt = prompt,
                            pages = pages,
                            assistantReply = assistantReply,
                            mentionedPageIds = normalizedMentionedPageIds,
                        )
                    }
                    val actionsToExecute = when (mode) {
                        AiChatMode.Planning -> emptyList()
                        AiChatMode.Edit -> localActions.ifEmpty {
                            result.actions.filterNot { action -> action.isUnsafeQualitativeRename() }
                        }
                        AiChatMode.Auto -> result.actions
                            .filterNot { action -> action.isUnsafeQualitativeRename() }
                            .repairedWithLocalPlan(prompt, localActions)
                    }
                    val actionResults = if (actionsToExecute.isEmpty()) {
                        HomeAiExecutionResult()
                    } else if (scopedTargetPage != null) {
                        executePageScopedActions(scopedTargetPage, actionsToExecute)
                    } else {
                        HomeAiExecutionResult()
                    }
                    val replyWithResults = listOf(
                        assistantReply,
                        actionResults.messages.joinToString("\n"),
                    )
                        .filter { message -> message.isNotBlank() }
                        .joinToString("\n\n")
                    val actionMetadata = ChatActionMetadata(
                        mode = mode.name,
                        schemaName = result.schemaName,
                        schemaVersion = result.schemaVersion,
                        proposedActions = result.actions.map { action -> action.toMetadataItem() },
                        executedActions = actionsToExecute.map { action -> action.toMetadataItem() },
                        executionMessages = actionResults.messages,
                        validationIssues = result.validationIssues.map { issue ->
                            ChatActionValidationMetadata(
                                actionIndex = issue.actionIndex,
                                field = issue.field,
                                code = issue.code,
                                message = issue.message,
                            )
                        } + actionResults.validationIssues,
                    )
                    chatHistoryRepository.appendMessage(
                        sessionId = session.id,
                        role = "assistant",
                        content = replyWithResults.sanitizeAiUserVisibleText(),
                        pageLinks = actionResults.pageLinks.toDomainChatPageLinks(),
                        actionMetadata = actionMetadata,
                    )
                }
                .onFailure { error ->
                    isAiGeneratingChat.value = false
                    val message = error.toAiChatErrorMessage().sanitizeAiUserVisibleText()
                    aiChatError.value = message
                    chatHistoryRepository.appendMessage(
                        sessionId = session.id,
                        role = "assistant",
                        content = message,
                    )
                }
        }
    }

    private suspend fun executePageScopedActions(
        page: Page,
        actions: List<ChatAction>,
    ): HomeAiExecutionResult {
        return runCatching {
            val supportedActions = actions
                .map { action ->
                    action.copy(
                        targetTitle = action.targetTitle.ifBlank { page.title },
                    )
                }
                .filter { action -> aiPageActionExecutor.supports(action) }
            if (supportedActions.isEmpty()) {
                HomeAiExecutionResult()
            } else {
                val execution = aiPageActionExecutor.executeOnPage(
                    page = page,
                    title = page.title,
                    document = PageBlockCodec.decodeDocument(page.content),
                    actions = supportedActions,
                )
                val didUpdatePage = execution.updatedTitle != null || execution.updatedDocument != null
                val updatedPage = if (didUpdatePage) {
                    page.copy(
                        title = execution.updatedTitle ?: page.title,
                        content = execution.updatedDocument?.let(PageBlockCodec::encodeDocument) ?: page.content,
                        updatedAt = System.currentTimeMillis(),
                    ).also { updatedPage -> pageRepository.upsertPage(updatedPage) }
                } else {
                    page
                }

                val pageLinks = buildList {
                    if (didUpdatePage) add(updatedPage.toChatPageLink())
                    addAll(execution.pageLinks)
                    addAll(execution.createdPages.map { createdPage -> createdPage.toChatPageLink() })
                }.distinctBy { link -> "${link.pageId}:${link.targetType}:${link.targetId}" }

                HomeAiExecutionResult(
                    messages = execution.messages.ifEmpty {
                        if (didUpdatePage) {
                            listOf("Done: Updated ${updatedPage.title.ifBlank { "Untitled page" }}")
                        } else {
                            emptyList()
                        }
                    },
                    pageLinks = pageLinks,
                    validationIssues = execution.validationIssues.map { issue ->
                        ChatActionValidationMetadata(
                            actionIndex = issue.actionIndex,
                            field = issue.field,
                            code = issue.code,
                            message = issue.message,
                        )
                    },
                )
            }
        }.getOrElse { error ->
            HomeAiExecutionResult(
                messages = listOf(error.toAiExecutionErrorMessage().sanitizeAiUserVisibleText()),
            )
        }
    }

    private suspend fun completeTaskAndReminder(task: TaskItem) {
        taskRepository.upsertTask(
            task.copy(
                isCompleted = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val now = System.currentTimeMillis()
        reminderRepository.getReminderForTask(task.id)?.let { reminder ->
            reminderRepository.upsertReminder(
                reminder.copy(
                    isDone = true,
                    updatedAt = now,
                    deletedAt = now,
                ),
            )
        }
    }

    private fun appendBlockToPage(page: Page, blockType: String, content: String): Page {
        val document = PageBlockCodec.decodeDocument(page.content)
        val block = PageBlockCodec.newBlock(blockType.toPageBlockType()).copy(text = content)
        return appendBlockToPage(page, block, document)
    }

    private fun appendBlockToPage(page: Page, block: PageBlock): Page {
        return appendBlockToPage(
            page = page,
            block = block,
            document = PageBlockCodec.decodeDocument(page.content),
        )
    }

    private fun appendBlockToPage(
        page: Page,
        block: PageBlock,
        document: PageBlockDocument,
    ): Page {
        return page.copy(
            content = PageBlockCodec.encodeDocument(
                document.copy(blocks = document.blocks + block),
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun String.toPageBlockType(): PageBlockType {
        return when (trim().lowercase()) {
            "heading" -> PageBlockType.Heading
            "todo" -> PageBlockType.Todo
            "bullet" -> PageBlockType.Bullet
            "quote" -> PageBlockType.Quote
            "divider" -> PageBlockType.Divider
            "media", "file", "image", "video", "attachment" -> PageBlockType.MediaFile
            else -> PageBlockType.Text
        }
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
            else -> listOf(emptyMap())
        }
        return rowMaps.map { values -> columns.newRow(values) }
    }

    private fun List<PageTableColumn>.newRow(valuesByColumnName: Map<String, String>): PageTableRow {
        val valuesByNormalizedName = valuesByColumnName.entries.associate { entry ->
            entry.key.normalizedAiKey() to entry.value
        }
        return PageBlockCodec.newTableRow(this).copy(
            cells = associate { column ->
                column.id to valuesByNormalizedName[column.name.normalizedAiKey()].orEmpty()
            },
        )
    }

    private fun String.toPageTableColumnType(): PageTableColumnType {
        return when (normalizedAiKey()) {
            "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
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

    private fun String.toPageTableRollupAggregation(): PageTableRollupAggregation {
        return when (normalizedAiKey()) {
            "sum", "total" -> PageTableRollupAggregation.Sum
            "average", "avg", "mean" -> PageTableRollupAggregation.Average
            "min", "minimum", "lowest" -> PageTableRollupAggregation.Min
            "max", "maximum", "highest" -> PageTableRollupAggregation.Max
            else -> PageTableRollupAggregation.Count
        }
    }

    private fun String.normalizedAiKey(): String {
        return trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun recoverPageWriteActions(
        prompt: String,
        pages: List<Page>,
        assistantReply: String,
        mentionedPageIds: List<String> = emptyList(),
    ): List<ChatAction> {
        val hasSelectedMention = mentionedPageIds.isNotEmpty()
        if (!prompt.looksLikePageMutationRequest() && !(hasSelectedMention && prompt.looksLikePageActionIntent())) {
            return emptyList()
        }
        val targetPage = pages.findPagesByIds(mentionedPageIds).firstOrNull()
            ?: pages.findMentionedPage(prompt)
            ?: pages.singleOrNull()
            ?: return emptyList()

        val recoveredActions = prompt.recoverStructuredActions(targetPage)
        if (recoveredActions.isNotEmpty()) return recoveredActions

        if (!prompt.looksLikePageWriteRequest()) return emptyList()
        val content = assistantReply.asWritablePageContent()
            .ifBlank { prompt.extractWriteFallbackContent(targetPage.title) }
        if (content.isBlank()) return emptyList()

        return listOf(
            ChatAction(
                type = "APPEND_BLOCK",
                title = "",
                targetTitle = targetPage.title,
                blockType = prompt.inferWriteBlockType(),
                content = content,
            ),
        )
    }

    private fun recoverPageStructuredActions(
        prompt: String,
        pages: List<Page>,
        mentionedPageIds: List<String> = emptyList(),
    ): List<ChatAction> {
        val hasSelectedMention = mentionedPageIds.isNotEmpty()
        if (!prompt.looksLikePageMutationRequest() && !(hasSelectedMention && prompt.looksLikePageActionIntent())) {
            return emptyList()
        }
        val targetPage = pages.findPagesByIds(mentionedPageIds).firstOrNull()
            ?: pages.findMentionedPage(prompt)
            ?: pages.singleOrNull()
            ?: return emptyList()
        return prompt.recoverStructuredActions(targetPage)
    }

    private fun List<ChatAction>.repairedWithLocalPlan(
        prompt: String,
        localActions: List<ChatAction>,
    ): List<ChatAction> {
        if (isEmpty()) return localActions
        if (localActions.isEmpty()) return this

        val hasConflictingAction = any { action -> action.conflictsWithPrompt(prompt) }
        val localCoversMoreSegments = prompt.actionSegments().size > size && localActions.size > size
        return if (hasConflictingAction || localCoversMoreSegments) {
            localActions
        } else {
            this
        }
    }

    private fun ChatAction.isUnsafeQualitativeRename(): Boolean {
        val normalizedType = type.trim().uppercase()
        if (normalizedType !in setOf("RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE")) return false
        val requestedTitle = title
            .ifBlank { value }
            .ifBlank { content }
            .ifBlank { newColumnName }
        return requestedTitle.isQualitativeTableTitleRequest()
    }

    private fun ChatAction.conflictsWithPrompt(prompt: String): Boolean {
        val normalizedType = type.trim().uppercase()
        val visiblePrompt = prompt.substringBefore("CYL_MENTION_CONTEXT:").trim()
        return when {
            normalizedType in setOf("CREATE_DATABASE", "CREATE_TABLE") &&
                visiblePrompt.looksLikeTableRowRequest() -> true

            normalizedType == "ADD_TABLE_ROW" &&
                visiblePrompt.looksLikeTableRowRequest() &&
                !visiblePrompt.looksLikeTaskRowRequest() &&
                cellValues.keys.any { key -> key.equals("Task", ignoreCase = true) } -> true

            normalizedType == "DELETE_BLOCK" && visiblePrompt.requestsAllBlocksDeletion() -> true

            else -> false
        }
    }

    private fun List<Page>.findPagesByIds(pageIds: List<String>): List<Page> {
        if (pageIds.isEmpty()) return emptyList()
        return pageIds.mapNotNull { pageId ->
            firstOrNull { page -> page.id == pageId }
        }.distinctBy { page -> page.id }
    }

    private fun List<Page>.withMentionedPagesFirst(pageIds: List<String>): List<Page> {
        val mentionedPages = findPagesByIds(pageIds)
        if (mentionedPages.isEmpty()) return this
        val mentionedIds = mentionedPages.map { page -> page.id }.toSet()
        return mentionedPages + filterNot { page -> page.id in mentionedIds }
    }

    private fun String.withMentionContext(pages: List<Page>): String {
        if (pages.isEmpty()) return this
        val context = pages.joinToString(separator = "\n") { page ->
            "- @${page.title.ifBlank { "Untitled page" }}"
        }
        return """
            $this

            CYL_MENTION_CONTEXT:
            The user selected these page mentions from the chat UI. Treat them as exact target pages for create/update/delete actions. Do not mention internal IDs in your reply:
            $context
        """.trimIndent()
    }

    private fun List<Page>.findMentionedPage(prompt: String): Page? {
        return findMentionedPages(prompt).firstOrNull()
    }

    private fun List<Page>.findMentionedPages(prompt: String): List<Page> {
        val normalizedPrompt = prompt.lowercase()
        val pagesWithTitle = filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }
        pagesWithTitle.firstOrNull { page ->
            normalizedPrompt.contains("@${page.title.lowercase()}")
        }?.let { return listOf(it) }

        val mention = Regex("@([^\\n,.;:]+)")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mention.isBlank()) return emptyList()
        return pagesWithTitle.filter { page ->
            val title = page.title.lowercase()
            title == mention ||
                title.startsWith(mention) ||
                mention.startsWith(title) ||
                title.contains(mention)
        }.take(1)
    }

    private fun String.looksLikePageWriteRequest(): Boolean {
        val value = lowercase()
        val hasWriteIntent = listOf(
            "write",
            "tulis",
            "catat",
            "masukkan",
            "insert",
            "append",
            "add note",
            "tambah nota",
            "nota",
            "note",
            "isi",
            "content",
            "memo",
            "buat isi",
            "draft",
            "karangan",
        ).any { token -> value.contains(token) }
        if (!hasWriteIntent) return false

        val destructiveOrTableIntent = listOf(
            "delete",
            "hapus",
            "buang",
            "rename",
            "table",
            "database",
            "row",
            "column",
            "property",
        ).any { token -> value.contains(token) }
        return !destructiveOrTableIntent
    }

    private fun String.looksLikePageMutationRequest(): Boolean {
        val value = lowercase()
        return looksLikePageWriteRequest() ||
            listOf("property", "properties", "prop").any { token -> value.contains(token) } ||
            listOf("block", "heading", "todo", "quote", "divider", "media", "file").any { token -> value.contains(token) } &&
            listOf(
                "ubah",
                "tukar",
                "edit",
                "update",
                "ganti",
                "padam",
                "buang",
                "hapus",
                "delete",
                "remove",
            ).any { token -> value.contains(token) }
    }

    private fun String.looksLikePageActionIntent(): Boolean {
        val value = lowercase()
        val mutationWords = listOf(
            "buat",
            "create",
            "cipta",
            "tambah",
            "add",
            "masukkan",
            "letak",
            "write",
            "tulis",
            "catat",
            "ubah",
            "tukar",
            "edit",
            "update",
            "ganti",
            "padam",
            "buang",
            "hapus",
            "delete",
            "remove",
        ).any { token -> value.contains(token) }
        val pageObjectWords = listOf(
            "property",
            "properties",
            "prop",
            "block",
            "blok",
            "table",
            "database",
            "row",
            "column",
            "task",
            "todo",
            "reminder",
            "nota",
            "note",
        ).any { token -> value.contains(token) }
        return mutationWords || pageObjectWords || looksLikePageWriteRequest() || looksLikeTableRowRequest()
    }

    private fun String.recoverPropertyAction(targetPage: Page): ChatAction? {
        val value = lowercase()
        val mentionsProperty = listOf("property", "properties", "prop").any { token -> value.contains(token) }
        if (!mentionsProperty) return null

        val actionType = when {
            listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) } -> "DELETE_PROPERTY"
            listOf("ubah", "tukar", "edit", "update", "set", "jadikan").any { token -> value.contains(token) } -> "UPDATE_PROPERTY"
            listOf("tambah", "add", "create", "buat", "masukkan", "letak").any { token -> value.contains(token) } -> "ADD_PROPERTY"
            else -> return null
        }
        val propertyName = extractPropertyName(targetPage.title)
        if (propertyName.isBlank()) return null

        return ChatAction(
            type = actionType,
            title = "",
            targetTitle = targetPage.title,
            propertyName = propertyName,
            propertyType = inferPropertyType(),
            value = extractPropertyValue(targetPage.title),
        )
    }

    private fun String.recoverBlockMutationAction(targetPage: Page): ChatAction? {
        val value = lowercase()
        val hasBlockIntent = listOf("block", "heading", "tajuk", "todo", "quote", "petikan", "divider", "media", "file")
            .any { token -> value.contains(token) }
        if (!hasBlockIntent) return null

        val isDelete = listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) }
        if (isDelete) {
            if (requestsAllBlocksDeletion()) {
                return ChatAction(
                    type = "DELETE_ALL_BLOCKS",
                    title = "",
                    targetTitle = targetPage.title,
                )
            }
            val blockText = extractBlockReference(targetPage.title)
            return ChatAction(
                type = "DELETE_BLOCK",
                title = "",
                targetTitle = targetPage.title,
                blockType = inferWriteBlockType(),
                blockText = blockText,
                content = blockText,
            )
        }

        val isUpdate = listOf("ubah", "tukar", "edit", "update", "ganti", "jadikan").any { token -> value.contains(token) }
        if (!isUpdate) return null
        val replacement = splitBlockReplacement(targetPage.title) ?: return null
        return ChatAction(
            type = "UPDATE_BLOCK",
            title = "",
            targetTitle = targetPage.title,
            blockType = inferWriteBlockType(),
            blockText = replacement.first,
            content = replacement.second,
        )
    }

    private fun String.recoverDatabaseAction(targetPage: Page): ChatAction? {
        val value = lowercase()
        if (looksLikeTableRowRequest()) return null
        val isTableContext = looksLikeTableContextOnlyRequest()
        val hasDatabaseIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasDatabaseIntent && !isTableContext) return null
        if (isTableContext && !hasDatabaseIntent && targetPage.hasAnyTable()) return null
        val isCreate = listOf("buat", "create", "cipta", "tambah", "add", "masukkan").any { token ->
            value.contains(token)
        } || isTableContext
        if (!isCreate) return null
        val tableTitle = extractTableTitle(targetPage.title)
            .ifBlank { "Table" }
        return ChatAction(
            type = "CREATE_DATABASE",
            title = "",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(value),
        )
    }

    private fun String.recoverImplicitDatabaseAction(targetPage: Page): ChatAction {
        val tableTitle = when {
            looksLikeExpenseText() -> targetPage.title.ifBlank { "Belanja" }
            else -> targetPage.title.ifBlank { "Table" }
        }
        return ChatAction(
            type = "CREATE_DATABASE",
            title = "",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(lowercase()),
        )
    }

    private fun String.recoverWriteAction(targetPage: Page): ChatAction? {
        if (!looksLikePageWriteRequest()) return null
        val content = extractWriteFallbackContent(targetPage.title).ifBlank { return null }
        return ChatAction(
            type = "APPEND_BLOCK",
            title = "",
            targetTitle = targetPage.title,
            blockType = inferWriteBlockType(),
            content = content,
        )
    }

    private fun String.recoverStructuredActions(targetPage: Page): List<ChatAction> {
        val actions = mutableListOf<ChatAction>()
        var createdTableTitle: String? = null
        actionSegments().forEach { segment ->
            val rowAction = segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
            if (rowAction == null &&
                segment.looksLikeTableRowRequest() &&
                createdTableTitle.isNullOrBlank() &&
                !targetPage.hasAnyTable()
            ) {
                val tableAction = segment.recoverImplicitDatabaseAction(targetPage)
                createdTableTitle = tableAction.tableTitle
                actions += tableAction
                segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
                    ?.let { action -> actions += action }
                return@forEach
            }

            val action = segment.recoverBlockMutationAction(targetPage)
                ?: rowAction
                ?: segment.recoverPropertyAction(targetPage)
                ?: segment.recoverTableRenameAction(targetPage)
                ?: segment.recoverDatabaseAction(targetPage)
                ?: segment.takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage)
            if (action != null) {
                actions += action
                if (action.type.equals("CREATE_DATABASE", ignoreCase = true)) {
                    createdTableTitle = action.tableTitle.takeIf { title -> title.isNotBlank() }
                }
            }
        }
        return if (actions.isNotEmpty()) {
            actions
        } else {
            listOfNotNull(
                recoverBlockMutationAction(targetPage)
                    ?: recoverTableRowAction(targetPage)
                    ?: recoverPropertyAction(targetPage)
                    ?: recoverTableRenameAction(targetPage)
                    ?: recoverDatabaseAction(targetPage)
                    ?: takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage),
            )
        }
    }

    private fun String.recoverTableRenameAction(targetPage: Page): ChatAction? {
        val value = lowercase()
        val hasTableIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasTableIntent || !targetPage.hasAnyTable()) return null
        val hasRenameIntent = listOf("rename", "ubah nama", "tukar nama", "ganti nama", "change name", "jadikan nama")
            .any { token -> value.contains(token) } ||
            (
                listOf("ubah", "tukar", "ganti", "jadikan", "change", "set").any { token -> value.contains(token) } &&
                    listOf("nama", "name", "title").any { token -> value.contains(token) }
                )
        if (!hasRenameIntent) return null
        val newTitle = extractNewTableTitle(targetPage.title).ifBlank { return null }
        return ChatAction(
            type = "RENAME_TABLE",
            title = newTitle,
            targetTitle = targetPage.title,
        )
    }

    private fun String.recoverTableRowAction(
        targetPage: Page,
        fallbackTableTitle: String? = null,
    ): ChatAction? {
        if (!looksLikeTableRowRequest()) return null
        val document = PageBlockCodec.decodeDocument(targetPage.content)
        val tableTitle = document.blocks
            .firstOrNull { block -> block.type == PageBlockType.DatabaseTable }
            ?.table
            ?.title
            ?.takeIf { title -> title.isNotBlank() }
            ?: fallbackTableTitle.orEmpty()
        if (tableTitle.isBlank() && document.blocks.none { block -> block.type == PageBlockType.DatabaseTable }) {
            return null
        }

        val rowPrompt = bestTableRowSegment()
        val rowText = rowPrompt.extractTableRowText(targetPage.title)
        if (rowText.isBlank()) return null
        val amount = rowText.extractMoneyAmount()
        val rowTitle = rowText.removeMoneyAmount().removeDateRequestWords().ifBlank { rowText }
        val dateValue = rowPrompt.inferredDateValue()
        val cellValues = buildMap {
            put("Name", rowTitle)
            put("Item", rowTitle)
            amount?.let { value ->
                put("Amount", value)
                put("Jumlah", value)
                put("Harga", value)
                put("Price", value)
                put("Cost", value)
                put("Total", value)
            }
            dateValue?.let { value ->
                put("Date", value)
                put("Tarikh", value)
            }
            if (amount != null && !rowText.equals(rowTitle, ignoreCase = true)) {
                put("Notes", rowText)
            }
        }
        return ChatAction(
            type = "ADD_TABLE_ROW",
            title = "",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            rowTitle = rowTitle,
            cellValues = cellValues,
        )
    }

    private fun String.asWritablePageContent(): String {
        val value = trim()
        if (value.isBlank()) return ""
        val lower = value.lowercase()
        val isGenericFailure = listOf(
            "i can't",
            "i cannot",
            "cannot",
            "can't",
            "tidak dapat",
            "tak dapat",
            "maaf",
            "sorry",
            "please try again",
            "couldn't convert",
            "belum dapat tukar",
            "tukar arahan",
        ).any { token -> lower.contains(token) }
        if (isGenericFailure) return ""
        return value
    }

    private fun String.isBackendActionFallback(): Boolean {
        val value = lowercase()
        return listOf(
            "belum dapat tukar arahan",
            "tukar arahan itu kepada tindakan",
            "couldn't convert that into a cyl action",
            "could not convert that into a cyl action",
            "try again with the page",
            "mention page with @",
        ).any { token -> value.contains(token) }
    }

    private fun String.recoveredActionReply(): String {
        val value = lowercase()
        val usesMalay = listOf(
            "saya",
            "awak",
            "tolong",
            "boleh",
            "nak",
            "buat",
            "tulis",
            "catat",
            "tambah",
            "masukkan",
            "ubah",
            "tukar",
            "padam",
            "buang",
            "hapus",
            "dekat",
            "dalam",
            "sini",
        ).any { token -> value.contains(token) }
        return if (usesMalay) {
            "Siap - saya buat perubahan itu."
        } else {
            "Done - I made that change."
        }
    }

    private fun String.extractWriteFallbackContent(pageTitle: String): String {
        return removeMentionedPage(pageTitle)
            .replace(Regex("(?i)\\b(tolong|please|buat|create|write|tulis|catat|masukkan|insert|append|draft|nota|note|memo|isi|content|buat isi|tambah nota|add note)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.extractTableTitle(pageTitle: String): String {
        return removeMentionedPage(pageTitle)
            .replace(
                Regex("(?i)\\b(buat|create|cipta|tambah|add|masukkan|letak|untuk|catat|rekod|record|table|database|jadual|dalam|dekat|di|page|ini|sini)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.extractNewTableTitle(pageTitle: String): String {
        val cleaned = removeMentionedPage(pageTitle)
            .replace(Regex("(?i)\\b(ubah|tukar|ganti|jadikan|change|set|rename|nama|name|title|table|database|jadual)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val afterMarker = listOf(
            "dengan nama ",
            "kepada ",
            "menjadi ",
            "jadikan ",
            "dengan ",
            "jadi ",
            "to ",
            "into ",
            "as ",
            " dengan nama ",
            " kepada ",
            " menjadi ",
            " jadikan ",
            " dengan ",
            " jadi ",
            " to ",
            " into ",
            " as ",
        ).firstNotNullOfOrNull { marker ->
            cleaned.substringAfter(marker, missingDelimiterValue = "")
                .takeIf { value -> value.isNotBlank() }
        } ?: cleaned

        val title = afterMarker
            .replace(Regex("(?i)\\b(yang|baru|new)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\s+(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\b\\s*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '"', '\'')
        return title.takeUnless { candidate -> candidate.isQualitativeTableTitleRequest() }.orEmpty()
    }

    private fun String.isQualitativeTableTitleRequest(): Boolean {
        val normalized = lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isBlank()) return true
        val descriptorWords = setOf(
            "sesuai",
            "sensuai",
            "sesual",
            "sensual",
            "appropriate",
            "suitable",
            "better",
            "nice",
            "good",
            "bagus",
            "kemas",
            "cantik",
            "proper",
            "short",
            "simple",
            "ringkas",
            "pendek",
        )
        return normalized.split(Regex("\\s+")).all { word -> word in descriptorWords }
    }

    private fun Page.hasAnyTable(): Boolean {
        return PageBlockCodec.decodeDocument(content)
            .blocks
            .any { block -> block.type == PageBlockType.DatabaseTable }
    }

    private fun String.defaultRecoveredTableColumns(promptValue: String): List<ChatTableColumn> {
        return if (looksLikeExpenseText() || promptValue.looksLikeExpenseText()) {
            listOf(
                ChatTableColumn(name = "Name", type = "Text"),
                ChatTableColumn(name = "Amount", type = "Number"),
                ChatTableColumn(name = "Date", type = "Date"),
                ChatTableColumn(name = "Notes", type = "Text"),
            )
        } else {
            listOf(
                ChatTableColumn(name = "Name", type = "Text"),
                ChatTableColumn(name = "Status", type = "Status"),
                ChatTableColumn(name = "Notes", type = "Text"),
            )
        }
    }

    private fun String.looksLikeExpenseText(): Boolean {
        val value = lowercase()
        return extractMoneyAmount() != null ||
            listOf("duit", "belanja", "expense", "expenses", "spend", "makan", "ringgit", "rm", "harga", "jumlah")
                .any { token -> value.contains(token) }
    }

    private fun ChatAction.normalizedForPrompt(prompt: String, mentionedPage: Page?): ChatAction {
        val normalizedType = type.trim().uppercase()
        return when {
            normalizedType == "DELETE_BLOCK" && prompt.requestsAllBlocksDeletion() -> {
                copy(
                    type = "DELETE_ALL_BLOCKS",
                    blockId = "",
                    blockText = "",
                    content = "",
                    blockType = "",
                )
            }
            normalizedType in setOf("CREATE_DATABASE", "CREATE_TABLE") && prompt.looksLikeTableRowRequest() && mentionedPage != null -> {
                prompt.recoverTableRowAction(mentionedPage) ?: copy(type = "ADD_TABLE_ROW")
            }
            normalizedType == "ADD_TABLE_ROW" &&
                prompt.looksLikeTableRowRequest() &&
                !prompt.looksLikeTaskRowRequest() &&
                mentionedPage != null -> {
                prompt.recoverTableRowAction(mentionedPage) ?: this
            }
            else -> this
        }
    }

    private fun String.requestsAllBlocksDeletion(): Boolean {
        val value = lowercase()
        val hasDeleteIntent = listOf("padam", "buang", "hapus", "delete", "remove", "clear", "kosongkan")
            .any { token -> value.contains(token) }
        val hasAllIntent = listOf("semua", "all", "every", "keseluruhan", "seluruh")
            .any { token -> value.contains(token) }
        val hasBlockIntent = listOf("block", "blok", "blocks")
            .any { token -> value.contains(token) }
        return hasDeleteIntent && hasAllIntent && hasBlockIntent
    }

    private fun String.looksLikeTableRowRequest(): Boolean {
        val value = lowercase()
        val hasRowIntent = listOf("row", "baris", "rekod", "record")
            .any { token -> value.contains(token) }
        val hasAddIntent = listOf("tambah", "add", "masukkan", "letak", "insert", "create", "catat")
            .any { token -> value.contains(token) }
        val hasCreateTableIntent = listOf("table", "database", "jadual")
            .any { token -> value.contains(token) } &&
            listOf("buat", "create", "cipta")
                .any { token -> value.contains(token) }
        val isExplicitNoteWrite = listOf("nota", "note", "memo", "isi", "content").any { token ->
            value.contains(token)
        } && looksLikePageWriteRequest()
        val hasExpenseDataHint = extractMoneyAmount() != null ||
            listOf("makan", "ringgit", "rm", "harga", "jumlah", "tarikh", "hari ini", "harini", "today")
            .any { token -> value.contains(token) }
        if (isExplicitNoteWrite) return false
        if (hasCreateTableIntent && !hasRowIntent) return false
        return (hasRowIntent && hasAddIntent) || (hasExpenseDataHint && !hasCreateTableIntent)
    }

    private fun String.looksLikeTaskRowRequest(): Boolean {
        val value = lowercase()
        return listOf("task", "todo", "reminder", "ingatkan", "deadline", "due", "appointment", "jadualkan")
            .any { token -> value.contains(token) }
    }

    private fun String.looksLikeTableContextOnlyRequest(): Boolean {
        val value = lowercase()
        val hasContextIntent = listOf("catat", "rekod", "record", "track", "tracking").any { token ->
            value.contains(token)
        } &&
            listOf("duit", "belanja", "expense", "expenses", "spending").any { token ->
                value.contains(token)
            } &&
            listOf("bulanan", "monthly", "bulan", "harian", "daily").any { token ->
                value.contains(token)
            }
        return hasContextIntent && !looksLikeTableRowRequest()
    }

    private fun String.actionSegments(): List<String> {
        val splitText = replace(Regex("(?i)\\b(lepas tu|selepas tu|pastu|astu|then|next)\\b"), "\n")
            .replace(
                Regex("(?i)\\s*,\\s*(?=(dan\\s+)?(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
            .replace(
                Regex("(?i)\\b(dan|and)\\s+(?=(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
        return splitText
            .lineSequence()
            .map { segment -> segment.trim(' ', ',', '.', ';', ':', '-') }
            .filter { segment -> segment.isNotBlank() }
            .toList()
            .ifEmpty { listOf(trim()) }
    }

    private fun String.bestTableRowSegment(): String {
        return actionSegments()
            .lastOrNull { segment -> segment.looksLikeTableRowRequest() }
            ?: this
    }

    private fun String.extractTableRowText(pageTitle: String): String {
        return removeMentionedPage(pageTitle)
            .replace(
                Regex("(?i)\\b(tambah|add|masukkan|letak|insert|create|buat|catat|satu|1|row|baris|rekod|record|dalam|dekat|di|ke|to|into|table|database|jadual|page|ini|tersebut|yang|saya|guna|pakai|beli|belikan|buy|purchase|purchased|spent|spend|use|used|untuk|tu|dah|sekali)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.extractMoneyAmount(): String? {
        val match = Regex("(?i)(?:rm\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:ringgit|rm)?")
            .find(this)
            ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.takeIf { value -> value.isNotBlank() }
    }

    private fun String.removeMoneyAmount(): String {
        return replace(Regex("(?i)\\b(?:rm\\s*)?\\d+(?:[.,]\\d+)?\\s*(?:ringgit|rm)?\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')
    }

    private fun String.removeDateRequestWords(): String {
        return replace(Regex("(?i)\\b(hari\\s*ini|harini|today|tarikh|date|sekali)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')
    }

    private fun String.inferredDateValue(): String? {
        val value = lowercase()
        val wantsToday = listOf("hari ini", "harini", "today").any { token -> value.contains(token) }
        val wantsDate = wantsToday || listOf("tarikh", "date").any { token -> value.contains(token) }
        return if (wantsDate) LocalDate.now().toString() else null
    }

    private fun String.inferWriteBlockType(): String {
        val value = lowercase()
        return when {
            value.contains("heading") || value.contains("tajuk") -> "Heading"
            value.contains("bullet") || value.contains("list") || value.contains("senarai") -> "Bullet"
            value.contains("quote") || value.contains("petikan") -> "Quote"
            value.contains("todo") || value.contains("checklist") -> "Todo"
            value.contains("divider") || value.contains("garisan") || value.contains("line") -> "Divider"
            value.contains("media") || value.contains("file") || value.contains("gambar") -> "MediaFile"
            else -> "Text"
        }
    }

    private fun String.extractPropertyName(pageTitle: String): String {
        val typeWords = setOf(
            "text",
            "number",
            "nombor",
            "select",
            "multiselect",
            "status",
            "date",
            "tarikh",
            "person",
            "files",
            "media",
            "checkbox",
            "url",
            "email",
            "phone",
            "telefon",
            "formula",
            "relation",
            "rollup",
            "button",
            "place",
            "tempat",
            "id",
        )
        val cleaned = removeMentionedPage(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(tambah|add|create|buat|masukkan|letak|ubah|tukar|edit|update|set|jadikan|padam|buang|hapus|delete|remove|property|properties|prop|dalam|dekat|di|page|ini|sini)\\b",
                ),
                " ",
            )
            .replace(Regex("(?i)\\b(type|jenis|as|sebagai|kepada|ke|value|nilai|with|dengan)\\b.*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

        return cleaned
            .split(" ")
            .filter { token -> token.isNotBlank() && token.lowercase() !in typeWords }
            .joinToString(" ")
            .trim()
    }

    private fun String.extractPropertyValue(pageTitle: String): String {
        val withoutMention = removeMentionedPage(pageTitle)
        val match = Regex("(?i)\\b(value|nilai|kepada|ke|as|sebagai|dengan)\\b\\s+(.+)$")
            .find(withoutMention)
            ?: return ""
        return match.groupValues.getOrNull(2)
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.inferPropertyType(): String {
        val value = lowercase()
        return when {
            value.contains("number") || value.contains("nombor") || value.contains("jumlah") || value.contains("harga") -> "Number"
            value.contains("multi-select") || value.contains("multiselect") || value.contains("multi select") -> "MultiSelect"
            value.contains("select") -> "Select"
            value.contains("status") -> "Status"
            value.contains("date") || value.contains("tarikh") || value.contains("deadline") || value.contains("due") -> "Date"
            value.contains("person") || value.contains("orang") -> "Person"
            value.contains("file") || value.contains("media") || value.contains("gambar") -> "FilesMedia"
            value.contains("checkbox") || value.contains("check") || value.contains("tick") || value.contains("siap") -> "Checkbox"
            value.contains("url") || value.contains("link") -> "Url"
            value.contains("email") -> "Email"
            value.contains("phone") || value.contains("telefon") -> "Phone"
            value.contains("formula") || value.contains("kira") -> "Formula"
            value.contains("relation") || value.contains("hubungan") -> "Relation"
            value.contains("rollup") -> "Rollup"
            value.contains("button") -> "Button"
            value.contains("place") || value.contains("tempat") || value.contains("lokasi") -> "Place"
            value.contains("id") -> "Id"
            else -> "Text"
        }
    }

    private fun String.extractBlockReference(pageTitle: String): String {
        return removeMentionedPage(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(padam|buang|hapus|delete|remove|block|heading|tajuk|todo|quote|petikan|divider|media|file|dalam|dekat|di|page|ini|sini)\\b",
                ),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.splitBlockReplacement(pageTitle: String): Pair<String, String>? {
        val cleaned = removeMentionedPage(pageTitle)
            .replace(
                Regex("(?i)\\b(ubah|tukar|edit|update|ganti|jadikan|block|heading|tajuk|todo|quote|petikan|divider|media|file|dalam|dekat|di|page|ini|sini)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val parts = Regex("(?i)\\s+\\b(kepada|ke|jadi|menjadi|dengan|to|into|with)\\b\\s+")
            .split(cleaned, limit = 2)
        if (parts.size != 2) return null
        val from = parts[0].trim(' ', '-', ':')
        val to = parts[1].trim(' ', '-', ':')
        if (from.isBlank() || to.isBlank()) return null
        return from to to
    }

    private fun String.removeMentionedPage(pageTitle: String): String {
        return replace("@$pageTitle", "", ignoreCase = true)
            .replace(Regex("@[^\\s]+"), " ")
    }

    private suspend fun findMentionedPage(
        workspaceId: String,
        prompt: String,
        mentionedPageIds: List<String> = emptyList(),
    ): Page? {
        val pages = pageRepository.observePages(workspaceId).first()
        pages.findPagesByIds(mentionedPageIds).firstOrNull()?.let { page ->
            return page
        }
        val normalizedPrompt = prompt.lowercase()
        val pagesWithTitle = pages
            .filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }
        pagesWithTitle.firstOrNull { page ->
            normalizedPrompt.contains("@${page.title.lowercase()}")
        }?.let { return it }

        val mention = Regex("@([^\\n,.;:]+)")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mention.isBlank()) return null
        return pagesWithTitle.firstOrNull { page ->
            val title = page.title.lowercase()
            title == mention ||
                title.startsWith(mention) ||
                mention.startsWith(title) ||
                title.contains(mention)
        }
    }

    private suspend fun resolveTargetPage(
        workspaceId: String,
        action: ChatAction,
        mentionedPage: Page?,
    ): Page? {
        val targetTitle = action.targetTitle.trim().removePrefix("@")
        return if (targetTitle.isNotBlank()) {
            findPageByTitle(workspaceId, targetTitle)
        } else {
            mentionedPage
        }
    }

    private suspend fun resolveTaskTablePage(
        workspaceId: String,
        action: ChatAction,
        mentionedPage: Page?,
    ): Page {
        resolveTargetPage(workspaceId, action, mentionedPage)?.let { page -> return page }
        findPageByTitle(workspaceId, "Tasks")?.let { page -> return page }
        return pageRepository.createPage(
            workspaceId = workspaceId,
            title = "Tasks",
            content = PageBlockCodec.encodeDocument(PageBlockDocument()),
        )
    }

    private fun updatePageDocument(
        page: Page,
        transform: (PageBlockDocument) -> PageBlockDocument,
    ): Page {
        val document = PageBlockCodec.decodeDocument(page.content)
        return page.copy(
            content = PageBlockCodec.encodeDocument(transform(document)),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun updatePageTable(
        page: Page,
        action: ChatAction,
        transform: (PageBlock) -> PageBlock,
    ): HomeTableUpdateResult {
        val document = PageBlockCodec.decodeDocument(page.content)
        val tableBlock = document.blocks.findMatchingTable(action)
            ?: error("Could not find table: ${action.tableTitle.ifBlank { action.blockId.ifBlank { "first table" } }}")
        val updatedBlocks = document.blocks.map { block ->
            if (block.id == tableBlock.id) transform(block) else block
        }
        val updatedBlock = updatedBlocks.firstOrNull { block -> block.id == tableBlock.id }
            ?: tableBlock
        return HomeTableUpdateResult(
            page = page.copy(
                content = PageBlockCodec.encodeDocument(document.copy(blocks = updatedBlocks)),
                updatedAt = System.currentTimeMillis(),
            ),
            tableTitle = updatedBlock.table.title,
        )
    }

    private fun List<PageBlock>.findMatchingTable(action: ChatAction): PageBlock? {
        if (action.blockId.isNotBlank()) {
            firstOrNull { block ->
                block.id == action.blockId && block.type == PageBlockType.DatabaseTable
            }?.let { return it }
        }
        val tableName = action.tableTitle.ifBlank { action.title }
        if (tableName.isNotBlank()) {
            firstOrNull { block ->
                block.type == PageBlockType.DatabaseTable &&
                    block.table.title.equals(tableName, ignoreCase = true)
            }?.let { return it }
            firstOrNull { block ->
                block.type == PageBlockType.DatabaseTable &&
                    block.table.title.contains(tableName, ignoreCase = true)
            }?.let { return it }
        }
        return filter { block -> block.type == PageBlockType.DatabaseTable }
            .singleOrNull()
            ?: firstOrNull { block -> block.type == PageBlockType.DatabaseTable }
    }

    private fun PageBlockDocument.upsertProperty(
        name: String,
        type: PagePropertyType,
        value: String,
    ): PageBlockDocument {
        val existing = properties.firstOrNull { property ->
            property.name.equals(name, ignoreCase = true)
        }
        return if (existing == null) {
            copy(
                properties = properties + PageBlockCodec.newProperty(type, name).copy(value = value),
            )
        } else {
            copy(
                properties = properties.map { property ->
                    if (property.id == existing.id) {
                        property.copy(
                            type = type,
                            value = value.ifBlank { property.value },
                        )
                    } else {
                        property
                    }
                },
            )
        }
    }

    private fun PageBlockDocument.deletePropertyByName(propertyName: String): PageBlockDocument {
        val normalized = propertyName.normalizedAiKey()
        val updatedProperties = properties.filterNot { property ->
            property.name.normalizedAiKey() == normalized
        }
        if (updatedProperties.size == properties.size) {
            error("Could not find property: $propertyName")
        }
        return copy(properties = updatedProperties)
    }

    private fun PageTable.newRowFromAction(action: ChatAction): PageTableRow {
        val title = action.rowTitle
            .ifBlank { action.title }
            .ifBlank { action.content }
        val values = action.cellValues.toMutableMap()
        val firstColumn = columns.firstOrNull()
        if (title.isNotBlank() && firstColumn != null) {
            val hasFirstColumnValue = values.keys.any { key ->
                key.normalizedAiKey() == firstColumn.name.normalizedAiKey()
            }
            if (!hasFirstColumnValue) {
                values[firstColumn.name] = title
            }
        }
        return columns.newRow(values)
    }

    private fun PageBlock.updateCellByNames(
        rowId: String,
        rowTitle: String,
        columnId: String,
        columnName: String,
        value: String,
    ): PageBlock {
        val column = table.findColumn(columnId = columnId, columnName = columnName)
            ?: error("Could not find column: ${columnName.ifBlank { columnId }}")
        val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
            ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
        return copy(
            table = table.copy(
                rows = table.rows.map { existingRow ->
                    if (existingRow.id == row.id) {
                        existingRow.copy(cells = existingRow.cells + (column.id to value))
                    } else {
                        existingRow
                    }
                },
            ),
        )
    }

    private fun PageBlock.deleteTableRow(
        rowId: String,
        rowTitle: String,
    ): PageBlock {
        val row = table.findRow(rowId = rowId, rowTitle = rowTitle)
            ?: error("Could not find row: ${rowTitle.ifBlank { rowId }}")
        return copy(
            table = table.copy(
                rows = table.rows.filterNot { existingRow -> existingRow.id == row.id },
            ),
        )
    }

    private fun PageTable.findColumn(
        columnId: String = "",
        columnName: String,
    ): PageTableColumn? {
        if (columnId.isNotBlank()) {
            columns.firstOrNull { column -> column.id == columnId }?.let { return it }
        }
        if (columnName.isBlank()) return null
        val normalized = columnName.normalizedAiKey()
        return columns.firstOrNull { column -> column.name.normalizedAiKey() == normalized }
            ?: columns.firstOrNull { column ->
                val current = column.name.normalizedAiKey()
                current.contains(normalized) || normalized.contains(current)
            }
    }

    private fun PageTable.findRow(
        rowId: String = "",
        rowTitle: String,
    ): PageTableRow? {
        if (rowId.isNotBlank()) {
            rows.firstOrNull { row -> row.id == rowId }?.let { return it }
        }
        if (rowTitle.isBlank()) return null
        val titleColumn = columns.firstOrNull()
        return rows.firstOrNull { row ->
            row.cellText(titleColumn).equals(rowTitle, ignoreCase = true)
        } ?: rows.firstOrNull { row ->
            val cellText = row.cellText(titleColumn)
            cellText.isNotBlank() &&
                (
                    cellText.contains(rowTitle, ignoreCase = true) ||
                        rowTitle.contains(cellText, ignoreCase = true)
                    )
        }
    }

    private fun PageTableRow.cellText(column: PageTableColumn?): String {
        return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
    }

    private fun String.toPagePropertyType(): PagePropertyType {
        return when (normalizedAiKey()) {
            "summarize", "summary" -> PagePropertyType.Summarize
            "translate", "translation" -> PagePropertyType.Translate
            "number" -> PagePropertyType.Number
            "select" -> PagePropertyType.Select
            "multiselect" -> PagePropertyType.MultiSelect
            "status" -> PagePropertyType.Status
            "date" -> PagePropertyType.Date
            "person", "people" -> PagePropertyType.Person
            "filesmedia", "filemedia", "filesandmedia", "attachment", "attachments" -> PagePropertyType.FilesMedia
            "checkbox", "check" -> PagePropertyType.Checkbox
            "url", "link" -> PagePropertyType.Url
            "email" -> PagePropertyType.Email
            "phone", "telephone" -> PagePropertyType.Phone
            "formula" -> PagePropertyType.Formula
            "relation" -> PagePropertyType.Relation
            "rollup" -> PagePropertyType.Rollup
            "createdtime" -> PagePropertyType.CreatedTime
            "createdby" -> PagePropertyType.CreatedBy
            "lasteditedtime" -> PagePropertyType.LastEditedTime
            "lasteditedby" -> PagePropertyType.LastEditedBy
            "button" -> PagePropertyType.Button
            "place", "location", "map" -> PagePropertyType.Place
            "id" -> PagePropertyType.Id
            else -> PagePropertyType.Text
        }
    }

    private suspend fun findPageByTitle(workspaceId: String, title: String): Page? {
        if (title.isBlank()) return null
        val pages = pageRepository.observePages(workspaceId).first()
        return pages.firstOrNull { page -> page.title.equals(title, ignoreCase = true) }
            ?: pages.firstOrNull { page -> page.title.contains(title, ignoreCase = true) }
    }

    private suspend fun findTaskByTitle(workspaceId: String, title: String): TaskItem? {
        if (title.isBlank()) return null
        val tasks = taskRepository.observeOpenTasks(workspaceId).first()
        return tasks.firstOrNull { task -> task.title.equals(title, ignoreCase = true) }
            ?: tasks.firstOrNull { task -> task.title.contains(title, ignoreCase = true) }
    }

    fun clearChatHistory() {
        aiChatError.value = null
        viewModelScope.launch {
            val workspaceId = workspaceRepository.getActiveWorkspaceId()
                ?.takeIf { it.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId
            val scopeId = homeChatScopeId(workspaceId)
            val sessionId = activePersistedChatSessionId(scopeId) ?: return@launch
            chatHistoryRepository.clearMessages(sessionId)
            chatHistoryRepository.deleteSession(sessionId)
            activeChatSessionId.value = DraftHomeChatSessionId
        }
    }

    fun createNewChatSession() {
        activeChatSessionId.value = DraftHomeChatSessionId
        aiChatError.value = null
    }

    fun selectChatSession(sessionId: String) {
        activeChatSessionId.value = sessionId
        aiChatError.value = null
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            chatHistoryRepository.deleteSession(sessionId)
            if (activeChatSessionId.value == sessionId) {
                activeChatSessionId.value = DraftHomeChatSessionId
            }
            aiChatError.value = null
        }
    }

    fun clearAiChatError() {
        aiChatError.value = null
    }

    private suspend fun startFreshHomeChatSessionOnLaunch() {
        val workspaceId = workspaceRepository.getActiveWorkspaceId()
            ?.takeIf { it.isNotBlank() }
            ?: CylDefaults.DefaultWorkspaceId
        chatHistoryRepository.deleteEmptySessions(homeChatScopeId(workspaceId))
        activeChatSessionId.value = DraftHomeChatSessionId
        aiChatError.value = null
    }

    private fun homeChatScopeId(workspaceId: String): String {
        return "home:$workspaceId"
    }

    private suspend fun resolveActiveChatSession(scopeId: String): ChatSession {
        val sessions = chatHistoryRepository.observeSessions(scopeId).first()
        val session = activeChatSessionId.value
            ?.takeUnless { selectedSessionId -> selectedSessionId == DraftHomeChatSessionId }
            ?.let { selectedSessionId -> sessions.firstOrNull { session -> session.id == selectedSessionId } }
            ?: chatHistoryRepository.createSession(scopeId)
        activeChatSessionId.value = session.id
        return session
    }

    private suspend fun activePersistedChatSessionId(scopeId: String): String? {
        val selectedSessionId = activeChatSessionId.value
            ?.takeUnless { sessionId -> sessionId == DraftHomeChatSessionId }
            ?: return null
        val sessions = chatHistoryRepository.observeSessions(scopeId).first()
        return selectedSessionId.takeIf { sessionId -> sessions.any { session -> session.id == sessionId } }
    }

    private fun List<ChatMessage>.toAiChatMessages(): List<AiChatMessage> {
        return map { message ->
            AiChatMessage(
                id = message.id,
                role = message.role,
                content = message.content,
                pageLinks = message.pageLinks.map { link ->
                    AiChatPageLink(
                        pageId = link.pageId,
                        title = link.title,
                        targetType = link.targetType,
                        targetId = link.targetId,
                    )
                },
                actionMetadata = message.actionMetadata?.let { metadata ->
                    AiChatActionMetadata(
                        mode = metadata.mode,
                        schemaName = metadata.schemaName,
                        schemaVersion = metadata.schemaVersion,
                        proposedActions = metadata.proposedActions.map { action ->
                            AiChatActionMetadataItem(
                                type = action.type,
                                target = action.target,
                            )
                        },
                        executedActions = metadata.executedActions.map { action ->
                            AiChatActionMetadataItem(
                                type = action.type,
                                target = action.target,
                            )
                        },
                        executionMessages = metadata.executionMessages,
                        validationIssues = metadata.validationIssues.map { issue ->
                            AiChatActionValidationIssue(
                                actionIndex = issue.actionIndex,
                                field = issue.field,
                                code = issue.code,
                                message = issue.message,
                            )
                        },
                    )
                },
            )
        }
    }

    private fun List<AiChatPageLink>.toDomainChatPageLinks(): List<ChatPageLink> {
        return map { link ->
            ChatPageLink(
                pageId = link.pageId,
                title = link.title,
                targetType = link.targetType,
                targetId = link.targetId,
            )
        }
    }

    private fun ChatAction.toMetadataItem(): ChatActionMetadataItem {
        val target = listOf(
            targetTitle,
            title,
            tableTitle,
            rowTitle,
            newRowTitle,
            columnName,
            newColumnName,
            propertyName,
            blockText,
            content,
        )
            .map { value -> value.trim() }
            .filter { value -> value.isNotBlank() }
            .distinct()
            .joinToString(" / ")
            .take(120)
        return ChatActionMetadataItem(
            type = type,
            target = target,
        )
    }

    private fun Page.toAiPageContext(): AiPageContext {
        val document = PageBlockCodec.decodeDocument(content)
        val propertyContexts = document.properties.map { property ->
            AiBlockContext(
                id = property.id,
                type = "Property",
                text = "${property.name} (${property.type.name}) = ${property.value.ifBlank { "empty" }}",
                path = "property:${property.name}",
            )
        }
        return AiPageContext(
            id = id,
            title = title.ifBlank { "Untitled page" },
            blocks = (propertyContexts + document.blocks.toAiBlockContexts()).take(140),
        )
    }

    private fun List<PageBlock>.toAiBlockContexts(
        pathPrefix: String = "",
        tableBlockId: String = "",
        tableTitle: String = "",
        rowId: String = "",
        rowTitle: String = "",
    ): List<AiBlockContext> {
        return flatMapIndexed { index, block ->
            val path = if (pathPrefix.isBlank()) "${index + 1}" else "$pathPrefix.${index + 1}"
            val currentTableTitle = if (block.type == PageBlockType.DatabaseTable) {
                block.table.title
            } else {
                tableTitle
            }
            val blockContext = listOf(
                AiBlockContext(
                    id = block.id,
                    type = block.type.name,
                    text = block.contextText(),
                    path = path,
                    tableTitle = currentTableTitle,
                    tableBlockId = tableBlockId,
                    rowId = rowId,
                    rowTitle = rowTitle,
                    rowBlockId = if (rowId.isNotBlank()) block.id else "",
                    isChecked = if (block.type == PageBlockType.Todo) block.isChecked else null,
                ),
            ) + block.children.toAiBlockContexts(
                pathPrefix = path,
                tableBlockId = tableBlockId,
                tableTitle = currentTableTitle,
                rowId = rowId,
                rowTitle = rowTitle,
            )
            val rowBlockContexts = if (block.type == PageBlockType.DatabaseTable) {
                val titleColumn = block.table.columns.firstOrNull()
                block.table.rows.take(20).flatMap { row ->
                    val rowLabel = row.cellText(titleColumn).ifBlank { row.id }
                    row.blocks.toAiBlockContexts(
                        pathPrefix = "$path.row:${row.id}",
                        tableBlockId = block.id,
                        tableTitle = block.table.title,
                        rowId = row.id,
                        rowTitle = rowLabel,
                    )
                }
            } else {
                emptyList()
            }
            blockContext + rowBlockContexts
        }
    }

    private fun PageBlock.contextText(): String {
        if (type == PageBlockType.DatabaseTable) {
            val columns = table.columns.joinToString { column -> column.aiContextText() }
            val rows = table.rows.take(12).joinToString(separator = "; ") { row ->
                val cells = table.columns.joinToString { column ->
                    "${column.name}=${row.cells[column.id].orEmpty()}"
                }
                val rowBlocks = row.blocks.take(6).joinToString(separator = " | ") { rowBlock ->
                    "${rowBlock.id}:${rowBlock.type.name}:${rowBlock.text.ifBlank { "empty" }}"
                }.ifBlank { "none" }
                "${row.id}[$cells; rowBlocks=$rowBlocks]"
            }
            return "title=${table.title}; ${table.contextStateText()}; columns=$columns; rows=$rows".take(1_800)
        }
        val mediaText = mediaAttachments.joinToString(separator = ", ") { attachment ->
            attachment.name
        }
        return listOf(text, mediaText)
            .filter { value -> value.isNotBlank() }
            .joinToString(separator = " | ")
            .ifBlank { "empty" }
            .take(600)
    }
}

private fun List<Page>.toSearchResults(query: String): List<PageSearchResult> {
    val terms = query.trim()
        .split(Regex("\\s+"))
        .filter { term -> term.isNotBlank() }
    if (terms.isEmpty()) return emptyList()

    return asSequence()
        .mapNotNull { page ->
            val candidates = page.searchCandidates()
            val haystack = candidates.joinToString(separator = "\n") { candidate -> candidate.text }
            if (terms.all { term -> haystack.contains(term, ignoreCase = true) }) {
                val bestCandidate = candidates
                    .map { candidate -> candidate to candidate.text.searchScore(terms) }
                    .filter { (_, score) -> score > 0 }
                    .sortedWith(
                        compareByDescending<Pair<PageSearchCandidate, Int>> { (_, score) -> score }
                            .thenBy { (candidate, _) -> candidate.targetType }
                            .thenBy { (candidate, _) -> candidate.text },
                    )
                    .firstOrNull()
                    ?.first
                    ?: PageSearchCandidate(
                        text = "Title: ${page.title.ifBlank { "Untitled page" }}",
                        targetType = SearchTargetPageTitle,
                        targetId = "",
                    )
                PageSearchResult(
                    page = page,
                    snippet = bestCandidate.text.compactLine().ifBlank { "Title match" },
                    targetType = bestCandidate.targetType,
                    targetId = bestCandidate.targetId,
                )
            } else {
                null
            }
        }
        .take(30)
        .toList()
}

private fun List<ChatSession>.toChatHistorySearchResults(
    messages: List<ChatMessage>,
    query: String,
): List<ChatHistorySearchResult> {
    return toChatHistorySearchResults(messages = messages, terms = query.searchTerms())
}

private fun List<ChatSession>.toChatSessionPreviews(
    messages: List<ChatMessage>,
): Map<String, ChatSessionPreview> {
    val messagesBySession = messages.groupBy { message -> message.sessionId }
    return associate { session ->
        val sessionMessages = messagesBySession[session.id].orEmpty()
        val lastMessage = sessionMessages.maxByOrNull { message -> message.createdAt }
        session.id to ChatSessionPreview(
            lastMessage = lastMessage?.toPreviewText().orEmpty(),
            messageCount = sessionMessages.size,
        )
    }
}

private fun ChatMessage.toPreviewText(): String {
    val speaker = if (role.equals("user", ignoreCase = true)) "You" else "CYL"
    return "$speaker: ${content.compactLine()}".take(96)
}

private fun List<ChatSession>.toChatHistorySearchResults(
    messages: List<ChatMessage>,
    terms: List<String>,
): List<ChatHistorySearchResult> {
    if (terms.isEmpty()) return emptyList()
    val messagesBySession = messages.groupBy { message -> message.sessionId }
    return mapNotNull { session ->
        val candidates = buildList {
            add(ChatSearchCandidate(text = "Chat title: ${session.title}", createdAt = session.updatedAt))
            messagesBySession[session.id].orEmpty().forEach { message ->
                add(
                    ChatSearchCandidate(
                        text = "${message.role}: ${message.content}",
                        createdAt = message.createdAt,
                    ),
                )
            }
        }
        val haystack = candidates.joinToString(separator = "\n") { candidate -> candidate.text }
        if (!terms.all { term -> haystack.contains(term, ignoreCase = true) }) {
            null
        } else {
            val matches = candidates
                .map { candidate -> candidate to candidate.text.searchScore(terms) }
                .filter { (_, score) -> score > 0 }
            val bestCandidate = matches
                .sortedWith(
                    compareByDescending<Pair<ChatSearchCandidate, Int>> { (_, score) -> score }
                        .thenByDescending { (candidate, _) -> candidate.createdAt },
                )
                .firstOrNull()
                ?.first
            val snippet = bestCandidate
                ?.text
                ?.removePrefix("user: ")
                ?.removePrefix("assistant: ")
                ?.removePrefix("Chat title: ")
                ?.compactLine()
                .orEmpty()
                .ifBlank { session.title.ifBlank { "New chat" } }
            ChatHistorySearchResult(
                session = session,
                snippet = snippet,
                matchCount = matches.size,
                lastMatchedAt = matches.maxOfOrNull { (candidate, _) -> candidate.createdAt }
                    ?: session.updatedAt,
            )
        }
    }
        .sortedWith(
            compareByDescending<ChatHistorySearchResult> { result -> result.lastMatchedAt }
                .thenBy { result -> result.session.title },
        )
        .take(30)
}

private fun buildHomeAiSearchReply(
    prompt: String,
    pages: List<Page>,
    tasks: List<TaskItem>,
    chatSessions: List<ChatSession>,
    chatMessages: List<ChatMessage>,
): HomeAiSearchReply? {
    if (!prompt.isReadOnlySearchRequest()) return null
    val terms = prompt.searchTerms()
    if (terms.isEmpty()) {
        return HomeAiSearchReply(
            content = "Boleh. Beritahu keyword atau data apa yang awak nak saya cari.",
        )
    }

    val pageMatches = pages.flatMap { page ->
        page.searchCandidates().mapNotNull { candidate ->
            val score = candidate.text.searchScore(terms)
            if (score <= 0) {
                null
            } else {
                AiSearchHit(
                    title = page.title.ifBlank { "Untitled page" },
                    detail = candidate.text.compactLine(),
                    score = score,
                    pageLink = page.toChatPageLink(
                        targetType = candidate.targetType,
                        targetId = candidate.targetId,
                    ),
                )
            }
        }
    }
    val taskMatches = tasks.mapNotNull { task ->
        val line = "Task: ${task.title}"
        val score = line.searchScore(terms)
        if (score <= 0) null else AiSearchHit(title = "Tasks", detail = line, score = score)
    }
    val chatMatches = chatSessions.toChatHistorySearchResults(chatMessages, terms)
        .map { result ->
            AiSearchHit(
                title = "Chat: ${result.session.title.ifBlank { "New chat" }}",
                detail = result.snippet,
                score = result.matchCount,
            )
        }
    val matches = (pageMatches + taskMatches + chatMatches)
        .sortedWith(compareByDescending<AiSearchHit> { it.score }.thenBy { it.title })
        .take(8)

    if (matches.isEmpty()) {
        return HomeAiSearchReply(
            content = "Saya tak jumpa padanan untuk `${terms.joinToString(" ")}` dalam pages, tables, rows, tasks, atau chat history yang ada.",
        )
    }

    return HomeAiSearchReply(
        content = buildString {
            appendLine("Saya jumpa ${matches.size} padanan teratas:")
            matches.forEachIndexed { index, hit ->
                appendLine("${index + 1}. ${hit.title} - ${hit.detail}")
            }
        }.trim(),
        pageLinks = matches.mapNotNull { hit -> hit.pageLink }.distinctBy { link -> link.pageId },
    )
}

private fun Page.searchLines(): List<String> {
    val document = PageBlockCodec.decodeDocument(content)
    val propertyLines = document.properties.map { property ->
        "Property ${property.name} (${property.type.name}) ${property.value}"
    }
    return propertyLines + document.blocks.flatMap { block -> block.searchLines() }
}

private fun Page.searchCandidates(): List<PageSearchCandidate> {
    val document = PageBlockCodec.decodeDocument(content)
    val title = title.ifBlank { "Untitled page" }
    return buildList {
        add(
            PageSearchCandidate(
                text = "Title: $title",
                targetType = SearchTargetPageTitle,
                targetId = "",
            ),
        )
        document.properties.forEach { property ->
            add(
                PageSearchCandidate(
                    text = "Property ${property.name} (${property.type.name}) ${property.value}",
                    targetType = SearchTargetProperty,
                    targetId = property.id,
                ),
            )
        }
        document.blocks.forEach { block ->
            addAll(block.searchCandidates())
        }
    }
}

private fun PageBlock.searchCandidates(): List<PageSearchCandidate> {
    val selfCandidates = when (type) {
        PageBlockType.DatabaseTable -> table.searchCandidates(tableBlockId = id)
        PageBlockType.MediaFile -> mediaAttachments.map { attachment ->
            PageSearchCandidate(
                text = "File ${attachment.name} ${attachment.mimeType}",
                targetType = SearchTargetBlock,
                targetId = id,
            )
        }.ifEmpty {
            listOf(
                PageSearchCandidate(
                    text = "Media file",
                    targetType = SearchTargetBlock,
                    targetId = id,
                ),
            )
        }
        PageBlockType.Divider -> listOf(
            PageSearchCandidate(
                text = "Divider",
                targetType = SearchTargetBlock,
                targetId = id,
            ),
        )
        else -> listOf(
            PageSearchCandidate(
                text = "${type.name}: ${text.ifBlank { "empty" }}",
                targetType = SearchTargetBlock,
                targetId = id,
            ),
        )
    }
    return selfCandidates + children.flatMap { child -> child.searchCandidates() }
}

private fun PageTable.searchCandidates(tableBlockId: String): List<PageSearchCandidate> {
    val columnCandidates = columns.map { column ->
        PageSearchCandidate(
            text = "Column ${column.name} ${column.type.name} ${column.formula}",
            targetType = SearchTargetBlock,
            targetId = tableBlockId,
        )
    }
    val rowCandidates = rows.flatMap { row ->
        val cells = columns.mapNotNull { column ->
            row.cells[column.id]
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> "${column.name}: $value" }
        }
        val rowTitle = row.cellText(columns.firstOrNull()).ifBlank { row.id }
        listOf(
            PageSearchCandidate(
                text = "Row $rowTitle ${cells.joinToString(separator = "; ")}",
                targetType = SearchTargetRow,
                targetId = row.id,
            ),
        ) + row.blocks.flatMap { block ->
            block.searchCandidates().map { candidate ->
                candidate.copy(
                    targetType = SearchTargetRowBlock,
                    targetId = candidate.targetId,
                )
            }
        }
    }
    return listOf(
        PageSearchCandidate(
            text = "Table $title ${view.name}",
            targetType = SearchTargetBlock,
            targetId = tableBlockId,
        ),
    ) + columnCandidates + rowCandidates
}

private data class AiSearchHit(
    val title: String,
    val detail: String,
    val score: Int,
    val pageLink: AiChatPageLink? = null,
)

private data class HomeAiSearchReply(
    val content: String,
    val pageLinks: List<AiChatPageLink> = emptyList(),
)

private data class PageSearchCandidate(
    val text: String,
    val targetType: String,
    val targetId: String,
)

private data class ChatSearchCandidate(
    val text: String,
    val createdAt: Long,
)

private const val SearchTargetPageTitle = "title"
private const val SearchTargetProperty = "property"
private const val SearchTargetBlock = "block"
private const val SearchTargetRow = "row"
private const val SearchTargetRowBlock = "row_block"
private const val DraftHomeChatSessionId = "draft-home-chat"

private fun String.isReadOnlySearchRequest(): Boolean {
    val text = lowercase()
    val searchWords = listOf(
        "cari",
        "search",
        "find",
        "lookup",
        "senarai",
        "list",
        "tunjuk",
        "show",
        "apa",
        "mana",
        "berapa",
        "count",
        "kira",
        "ringkas",
        "summarize",
    )
    val mutationWords = listOf(
        "buat",
        "create",
        "tambah",
        "add",
        "ubah",
        "tukar",
        "change",
        "update",
        "edit",
        "delete",
        "padam",
        "remove",
        "rename",
        "complete",
        "mark",
        "sort",
        "filter",
        "group",
        "set",
        "jadikan",
    )
    return searchWords.any { word -> text.contains(word) } &&
        mutationWords.none { word -> text.contains(word) }
}

private fun String.searchTerms(): List<String> {
    val stopWords = setOf(
        "ai",
        "aku",
        "awak",
        "boleh",
        "can",
        "cari",
        "search",
        "find",
        "lookup",
        "senarai",
        "list",
        "tunjuk",
        "show",
        "apa",
        "mana",
        "berapa",
        "count",
        "kira",
        "ringkas",
        "summarize",
        "dalam",
        "dekat",
        "di",
        "page",
        "pages",
        "table",
        "row",
        "rows",
        "chat",
        "history",
        "sejarah",
        "mesej",
        "message",
        "messages",
        "data",
        "item",
        "rekod",
        "record",
        "records",
        "yang",
        "dan",
        "atau",
        "the",
        "a",
        "an",
        "to",
        "for",
        "of",
        "in",
        "on",
        "me",
        "please",
        "tolong",
    )
    return lowercase()
        .split(Regex("[^a-z0-9@]+"))
        .map { it.trim('@') }
        .filter { term -> term.length >= 2 && term !in stopWords }
        .distinct()
}

private fun String.searchScore(terms: List<String>): Int {
    val text = lowercase()
    return terms.count { term -> text.contains(term) }
}

private fun PageBlock.searchLines(): List<String> {
    val selfLines = when (type) {
        PageBlockType.DatabaseTable -> table.searchLines()
        PageBlockType.MediaFile -> mediaAttachments.map { attachment ->
            "File ${attachment.name} ${attachment.mimeType}"
        }.ifEmpty { listOf("Media file") }
        PageBlockType.Divider -> listOf("Divider")
        else -> listOf(text)
    }
    return selfLines.filter { line -> line.isNotBlank() } +
        children.flatMap { child -> child.searchLines() }
}

private fun PageTable.searchLines(): List<String> {
    val columnLines = columns.map { column ->
        "Column ${column.name} ${column.type.name} ${column.formula}"
    }
    val rowLines = rows.flatMap { row ->
        val cells = columns.mapNotNull { column ->
            row.cells[column.id]
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> "${column.name}: $value" }
        }
        val rowTitle = row.cellText(columns.firstOrNull()).ifBlank { row.id }
        listOf("Row $rowTitle ${cells.joinToString(separator = "; ")}") +
            row.blocks.flatMap { block -> block.searchLines() }
    }
    return listOf("Table $title ${view.name}") + columnLines + rowLines
}

private fun String.compactLine(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(180)
}

private fun PageTableRow.cellText(column: PageTableColumn?): String {
    return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
}

private fun PageTable.contextStateText(): String {
    val sortText = if (sort.columnId.isBlank()) "none" else "${sort.columnId}:${sort.direction.name}"
    val filterText = if (filter.columnId.isBlank() || filter.query.isBlank()) {
        "none"
    } else {
        "${filter.columnId}:${filter.query}"
    }
    val groupText = groupByColumnId.ifBlank { "none" }
    val viewConfigText = listOf(
        "calendarDate=${viewConfig.calendarDateColumnId.ifBlank { "auto" }}",
        "timelineStart=${viewConfig.timelineStartColumnId.ifBlank { "auto" }}",
        "timelineEnd=${viewConfig.timelineEndColumnId.ifBlank { "none" }}",
        "dashboardMetric=${viewConfig.dashboardMetricColumnId.ifBlank { "auto" }}",
        "dashboardGroup=${viewConfig.dashboardGroupColumnId.ifBlank { "auto" }}",
    ).joinToString(",")
    return "sort=$sortText; filter=$filterText; groupBy=$groupText; viewConfig=$viewConfigText"
}

private fun PageTableColumn.aiContextText(): String {
    val config = when (type) {
        PageTableColumnType.Formula -> " formula=${formula.ifBlank { "none" }}"
        PageTableColumnType.Relation -> " relationTargetTableId=${relationTargetTableId.ifBlank { "none" }}"
        PageTableColumnType.Rollup -> {
            " rollupRelationColumnId=${rollupRelationColumnId.ifBlank { "none" }} " +
                "rollupTargetColumnId=${rollupTargetColumnId.ifBlank { "none" }} " +
                "rollupAggregation=${rollupAggregation.name}"
        }
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Status,
        PageTableColumnType.Date,
        PageTableColumnType.Checkbox,
        PageTableColumnType.FilesMedia,
        -> ""
    }
    return "$id:$name:${type.name}$config"
}

private data class WorkspaceDashboardState(
    val workspaces: List<Workspace>,
    val activeWorkspace: Workspace?,
)

private data class WorkspaceFormState(
    val isCreateWorkspaceDialogVisible: Boolean = false,
    val newWorkspaceName: String = "",
)

private data class HomeChatMetaState(
    val sessions: List<ChatSession> = emptyList(),
    val previews: Map<String, ChatSessionPreview> = emptyMap(),
    val activeSessionId: String? = null,
)

private data class HomeChatState(
    val messages: List<AiChatMessage> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val previews: Map<String, ChatSessionPreview> = emptyMap(),
    val activeSessionId: String? = null,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val mode: AiChatMode = AiChatMode.Planning,
)

private fun AiStatus.toDisplayModelLabel(): String {
    val cleanModel = model.trim()
    if (cleanModel.isNotBlank()) return cleanModel

    return when {
        mode.equals("sandbox", ignoreCase = true) -> "Sandbox AI"
        provider.isNotBlank() -> provider.trim()
        else -> "AI model"
    }
}

private fun String.sanitizeAiUserVisibleText(): String {
    return lineSequence()
        .map { line ->
            line
                .replace(Regex("\\s*[\"']?\\b(pageId|blockId|rowId|columnId|targetId|workspaceId|user_id|provider_id|request_id|id)\\b[\"']?\\s*[:=]\\s*[\"']?[A-Za-z0-9._:-]{6,}[\"']?"), "")
                .replace(Regex("\\s*\\((page|block|row|column|target)?\\s*id\\s*[:=]\\s*[A-Za-z0-9._:-]{6,}\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"), "")
                .replace(Regex("\\s+([,.;:])"), "$1")
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        }
        .filterNot { line -> line.equals("CYL_MENTION_CONTEXT:", ignoreCase = true) }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun String.toUserActionLabel(): String {
    return when (trim().uppercase()) {
        "DELETE_BLOCK", "DELETE_ALL_BLOCKS" -> "Delete block"
        "UPDATE_BLOCK", "EDIT_BLOCK" -> "Update block"
        "APPEND_BLOCK", "APPEND_PAGE_BLOCK", "ADD_BLOCK" -> "Add block"
        "ADD_TABLE_ROW" -> "Add row"
        "UPDATE_TABLE_ROW", "RENAME_TABLE_ROW" -> "Update row"
        "DELETE_TABLE_ROW" -> "Delete row"
        "ADD_TABLE_COLUMN" -> "Add property"
        "RENAME_TABLE_COLUMN", "UPDATE_TABLE_COLUMN" -> "Update property"
        "DELETE_TABLE_COLUMN" -> "Delete property"
        "RENAME_TABLE", "RENAME_DATABASE", "UPDATE_TABLE_TITLE" -> "Rename table"
        "CREATE_DATABASE", "CREATE_TABLE" -> "Create table"
        "UPDATE_TABLE_CELL" -> "Update cell"
        "ADD_PROPERTY", "UPDATE_PROPERTY" -> "Update property"
        "DELETE_PROPERTY" -> "Delete property"
        "CREATE_PAGE" -> "Create page"
        "UPDATE_PAGE" -> "Update page"
        "DELETE_PAGE" -> "Delete page"
        else -> split('_')
            .filter { part -> part.isNotBlank() }
            .joinToString(" ") { part -> part.lowercase().replaceFirstChar(Char::titlecase) }
            .ifBlank { "Action" }
    }
}

private fun Throwable.toAiChatErrorMessage(): String {
    return if (this is HttpException && code() == 401) {
        "Your session expired after the backend change. Please log in again."
    } else {
        "I couldn't reach the CYL backend: ${toBackendConnectionMessage()}"
    }
}

private fun Throwable.toAiExecutionErrorMessage(): String {
    val detail = localizedMessage?.takeIf { message -> message.isNotBlank() }
        ?: "AI edit failed before it could update the page."
    return "Failed: $detail"
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val activeWorkspaceId: String = CylDefaults.DefaultWorkspaceId,
    val workspaceName: String = CylDefaults.DefaultWorkspaceName,
    val workspaceCount: Int = 0,
    val workspaces: List<Workspace> = emptyList(),
    val pageCount: Int = 0,
    val openTaskCount: Int = 0,
    val pendingReminderCount: Int = 0,
    val allPages: List<Page> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val deletedPages: List<Page> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<PageSearchResult> = emptyList(),
    val openTasks: List<TaskItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val isCreateWorkspaceDialogVisible: Boolean = false,
    val newWorkspaceName: String = "",
    val canCreateWorkspace: Boolean = false,
    val chatMessages: List<AiChatMessage> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val chatSessionPreviews: Map<String, ChatSessionPreview> = emptyMap(),
    val activeChatSessionId: String? = null,
    val chatHistorySearchQuery: String = "",
    val chatHistorySearchResults: List<ChatHistorySearchResult> = emptyList(),
    val isAiGeneratingChat: Boolean = false,
    val aiChatError: String? = null,
    val aiChatMode: AiChatMode = AiChatMode.Planning,
    val aiModelLabel: String = "AI model",
)

private data class HomeAiExecutionResult(
    val messages: List<String> = emptyList(),
    val pageLinks: List<AiChatPageLink> = emptyList(),
    val validationIssues: List<ChatActionValidationMetadata> = emptyList(),
)

private data class HomeTableUpdateResult(
    val page: Page,
    val tableTitle: String,
)

data class PageSearchResult(
    val page: Page,
    val snippet: String,
    val targetType: String = "",
    val targetId: String = "",
)

data class ChatHistorySearchResult(
    val session: ChatSession,
    val snippet: String,
    val matchCount: Int,
    val lastMatchedAt: Long,
)

data class ChatSessionPreview(
    val lastMessage: String = "",
    val messageCount: Int = 0,
)

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
