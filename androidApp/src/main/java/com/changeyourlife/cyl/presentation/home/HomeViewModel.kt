package com.changeyourlife.cyl.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.core.network.toBackendConnectionMessage
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageContentCodec
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.model.SyncOverview
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.Workspace
import com.changeyourlife.cyl.domain.model.isActive
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AiBlockContext
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.SyncStatusRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiException
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.repository.AiStatus
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.usecase.ApplyAiActionUndoUseCase
import com.changeyourlife.cyl.presentation.ai.AiActionExecutionUseCase
import com.changeyourlife.cyl.presentation.ai.AiActionLogFactory
import com.changeyourlife.cyl.presentation.ai.AiChatActionOrchestrator
import com.changeyourlife.cyl.presentation.ai.AiChatMessageMapper
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.ai.AiPageTargetResolver
import com.changeyourlife.cyl.presentation.ai.toRoleContentPairs
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val pageRepository: PageRepository,
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val aiRepository: AiRepository,
    private val aiActionExecutionUseCase: AiActionExecutionUseCase,
    private val applyAiActionUndoUseCase: ApplyAiActionUndoUseCase,
    private val aiActionLogRepository: AiActionLogRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val syncStatusRepository: SyncStatusRepository,
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
                combine(
                    chatHistoryRepository.observeMessages(sessionId),
                    aiActionLogRepository.observeBySession(sessionId),
                ) { messages, actionLogs ->
                    AiChatMessageMapper.toAiChatMessages(
                        messages = messages,
                        actionLogs = actionLogs,
                    )
                }
            }
        }

    private val isAiGeneratingChat = MutableStateFlow(false)
    private val aiChatError = MutableStateFlow<String?>(null)
    private val aiStatusState = MutableStateFlow(AiStatus())
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
    ) { messages, meta, isGenerating, error ->
        HomeChatState(
            messages = messages,
            sessions = meta.sessions,
            previews = meta.previews,
            activeSessionId = meta.activeSessionId,
            isGenerating = isGenerating,
            error = error,
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
        syncStatusRepository.observeOverview(),
    ) { dashboard, form, chat, syncOverview ->
        dashboard.copy(
            isCreateWorkspaceDialogVisible = form.isCreateWorkspaceDialogVisible,
            newWorkspaceName = form.newWorkspaceName,
            canCreateWorkspace = form.newWorkspaceName.isNotBlank(),
            syncOverview = syncOverview,
            chatMessages = chat.messages,
            chatSessions = chat.sessions,
            chatSessionPreviews = chat.previews,
            activeChatSessionId = chat.activeSessionId,
            isAiGeneratingChat = chat.isGenerating,
            aiChatError = chat.error,
        )
    }.let { baseState ->
        combine(baseState, chatHistorySearchQuery, chatHistorySearchResults) { state, chatSearchQuery, chatSearchResults ->
            state.copy(
                chatHistorySearchQuery = chatSearchQuery,
                chatHistorySearchResults = chatSearchResults,
            )
        }
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
                aiStatusState.value = status
                aiModelLabel.value = status.toDisplayModelLabel()
            }
            .onFailure {
                aiStatusState.value = AiStatus()
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
            title = "",
            content = PageContentCodec.encode(listOf(PageContentCodec.newBlock(PageBlockType.Text))),
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

    fun retrySyncNow() {
        syncStatusRepository.retryNow()
    }

    fun updateChatHistorySearchQuery(query: String) {
        chatHistorySearchQuery.value = query
    }

    fun clearChatHistorySearchQuery() {
        chatHistorySearchQuery.value = ""
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
        images: List<AiImageAttachment> = emptyList(),
    ) {
        sendChatMessageScoped(
            prompt = prompt,
            mentionedPageIds = mentionedPageIds,
            attachedPageId = null,
            images = images,
        )
    }

    fun sendChatMessage(
        prompt: String,
        mentionedPageIds: List<String>,
        attachedPageId: String?,
        images: List<AiImageAttachment> = emptyList(),
    ) {
        sendChatMessageScoped(
            prompt = prompt,
            mentionedPageIds = mentionedPageIds,
            attachedPageId = attachedPageId,
            images = images,
        )
    }

    fun undoAiAction(
        auditId: String,
        pageId: String,
    ) {
        if (auditId.isBlank() || pageId.isBlank()) return
        viewModelScope.launch {
            aiChatError.value = null
            val workspaceId = workspaceRepository.getActiveWorkspaceId()
                ?.takeIf { it.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId
            val session = resolveActiveChatSession(homeChatScopeId(workspaceId))
            val result = applyAiActionUndoUseCase(auditId = auditId, pageId = pageId)
            chatHistoryRepository.appendMessage(
                sessionId = session.id,
                role = "assistant",
                content = result.message,
            )
            if (!result.changed) {
                aiChatError.value = result.message
            }
        }
    }

    private fun sendChatMessageScoped(
        prompt: String,
        mentionedPageIds: List<String>,
        attachedPageId: String?,
        images: List<AiImageAttachment>,
    ) {
        if ((prompt.isBlank() && images.isEmpty()) || isAiGeneratingChat.value) return

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
                .let(AiChatMessageMapper::toAiChatMessages)
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
            if (images.isEmpty()) {
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
            aiRepository.chatWithActions(
                messages = messagesForAi.toRoleContentPairs(),
                pages = pageContext,
                tasks = taskContext,
                images = images,
            )
                .onSuccess { result ->
                    isAiGeneratingChat.value = false
                    val orchestration = AiChatActionOrchestrator.orchestrate(
                        workspaceId = workspaceId,
                        scopedTargetPage = scopedTargetPage,
                        prompt = prompt,
                        backendResult = result,
                        requestMessageId = savedUserMessage.id,
                        provider = aiStatusState.value.provider,
                        model = aiStatusState.value.model,
                    ) { targetWorkspaceId, targetPage, actions ->
                        aiActionExecutionUseCase.executeCandidates(
                            workspaceId = targetWorkspaceId,
                            scopedTargetPage = targetPage,
                            actions = actions,
                        )
                    }
                    val assistantMessage = chatHistoryRepository.appendMessage(
                        sessionId = session.id,
                        role = "assistant",
                        content = orchestration.reply.sanitizeAiUserVisibleText(),
                        pageLinks = orchestration.pageLinks.toDomainChatPageLinks(),
                        actionMetadata = orchestration.actionMetadata,
                    )
                    AiActionLogFactory.fromMetadata(
                        sessionId = session.id,
                        workspaceId = workspaceId,
                        responseMessageId = assistantMessage.id,
                        metadata = orchestration.actionMetadata,
                        undoCommands = orchestration.undoCommands,
                    )?.let { actionLog ->
                        aiActionLogRepository.upsert(actionLog)
                    }
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
            "- @${page.title.ifBlank { "Untitled page" }} id=${page.id}"
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
        return AiPageTargetResolver.findMentionedPages(
            prompt = prompt,
            pages = this,
        )
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
        PageBlockType.DatabaseTable,
        PageBlockType.Table,
        -> table.searchCandidates(tableBlockId = id)
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
        PageBlockType.DatabaseTable,
        PageBlockType.Table,
        -> table.searchLines()
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
    val filterText = if (!filter.isActive()) {
        "none"
    } else {
        "${filter.columnId}:${filter.operator.name}:${filter.query}"
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
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
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
    return (this as? AiException)?.aiError?.userMessage
        ?: "I couldn't reach the CYL backend: ${toBackendConnectionMessage()}"
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
    val aiModelLabel: String = "AI model",
    val syncOverview: SyncOverview = SyncOverview(),
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
