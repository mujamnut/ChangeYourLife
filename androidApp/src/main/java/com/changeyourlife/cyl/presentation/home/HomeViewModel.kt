package com.changeyourlife.cyl.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.core.network.toBackendConnectionMessage
import com.changeyourlife.cyl.domain.model.AiSearchContext
import com.changeyourlife.cyl.domain.model.AiSkill
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
import com.changeyourlife.cyl.domain.model.SearchQuery
import com.changeyourlife.cyl.domain.model.SearchResult
import com.changeyourlife.cyl.domain.model.SearchTarget
import com.changeyourlife.cyl.domain.model.SearchTargetType
import com.changeyourlife.cyl.domain.model.SyncOverview
import com.changeyourlife.cyl.domain.model.TaskItem
import com.changeyourlife.cyl.domain.model.Workspace
import com.changeyourlife.cyl.domain.model.isActive
import com.changeyourlife.cyl.domain.repository.AuthRepository
import com.changeyourlife.cyl.domain.repository.AiBlockContext
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatMessageAttachment
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import com.changeyourlife.cyl.domain.model.MentionCandidate
import com.changeyourlife.cyl.domain.model.MentionQuery
import com.changeyourlife.cyl.domain.repository.ChatHistoryRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import com.changeyourlife.cyl.domain.repository.SyncStatusRepository
import com.changeyourlife.cyl.domain.repository.TaskRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.repository.AiPageContext
import com.changeyourlife.cyl.domain.repository.AiRepository
import com.changeyourlife.cyl.domain.repository.AiSkillRepository
import com.changeyourlife.cyl.domain.repository.AiException
import com.changeyourlife.cyl.domain.repository.AiImageAttachment
import com.changeyourlife.cyl.domain.repository.AiStatus
import com.changeyourlife.cyl.domain.repository.AiActionLogRepository
import com.changeyourlife.cyl.domain.usecase.ApplyAiActionUndoUseCase
import com.changeyourlife.cyl.domain.usecase.BuildAiMemoryContextUseCase
import com.changeyourlife.cyl.domain.usecase.BuildAiSearchContextUseCase
import com.changeyourlife.cyl.domain.usecase.BuildAiSkillContextUseCase
import com.changeyourlife.cyl.domain.usecase.ResolveMentionUseCase
import com.changeyourlife.cyl.domain.usecase.SearchWorkspaceUseCase
import com.changeyourlife.cyl.presentation.ai.AiActionExecutionUseCase
import com.changeyourlife.cyl.presentation.ai.AiActionLogFactory
import com.changeyourlife.cyl.presentation.ai.AiChatActionOrchestrator
import com.changeyourlife.cyl.presentation.ai.AiChatMessageMapper
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.ai.ChatHistorySearchResult
import com.changeyourlife.cyl.presentation.ai.buildChatHistorySearchResults
import com.changeyourlife.cyl.presentation.ai.toRoleContentPairs
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import com.changeyourlife.cyl.presentation.page.PageModuleTemplates
import com.changeyourlife.cyl.presentation.page.PageModuleType
import com.changeyourlife.cyl.presentation.page.PageTableReference
import com.changeyourlife.cyl.presentation.page.tableReferences
import com.changeyourlife.cyl.data.local.session.AppThemeMode
import com.changeyourlife.cyl.data.local.session.SyncSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val pageRepository: PageRepository,
    private val taskRepository: TaskRepository,
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val aiRepository: AiRepository,
    private val aiSkillRepository: AiSkillRepository,
    private val aiActionExecutionUseCase: AiActionExecutionUseCase,
    private val applyAiActionUndoUseCase: ApplyAiActionUndoUseCase,
    private val buildAiMemoryContextUseCase: BuildAiMemoryContextUseCase,
    private val buildAiSearchContextUseCase: BuildAiSearchContextUseCase,
    private val buildAiSkillContextUseCase: BuildAiSkillContextUseCase,
    private val searchWorkspaceUseCase: SearchWorkspaceUseCase,
    private val resolveMentionUseCase: ResolveMentionUseCase,
    private val aiActionLogRepository: AiActionLogRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncSettingsStore: SyncSettingsStore,
    private val backgroundSyncQueue: com.changeyourlife.cyl.data.sync.BackgroundSyncQueue,
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
    private val activeWorkspaceSkills: Flow<List<AiSkill>> = workspaceRepository.observeActiveWorkspaceId()
        .map { workspaceId ->
            workspaceId
                ?.takeIf { id -> id.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId
        }
        .flatMapLatest(aiSkillRepository::observeSkills)

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
    private val searchScope = MutableStateFlow(HomeSearchScope.All)
    private val aiMentionQuery = MutableStateFlow("")
    private val chatHistorySearchQuery = MutableStateFlow("")
    private val aiSkillError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workspaceSearchResults: Flow<List<HomeSearchResult>> = combine(
        workspaceRepository.observeActiveWorkspaceId(),
        searchQuery,
        searchScope,
    ) { workspaceId, query, scope ->
        HomeSearchRequest(
            workspaceId = workspaceId
                ?.takeIf { id -> id.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId,
            query = query,
            scope = scope,
        )
    }.mapLatest { request ->
        if (request.query.isBlank()) {
            emptyList()
        } else {
            searchWorkspaceUseCase(
                SearchQuery(
                    workspaceId = request.workspaceId,
                    query = request.query,
                    scopes = request.scope.targetTypes,
                    limit = HomeSearchResultLimit,
                ),
            )
                .mapNotNull { result -> result.toHomeSearchResult() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val aiMentionCandidates: Flow<List<MentionCandidate>> = combine(
        workspaceRepository.observeActiveWorkspaceId(),
        aiMentionQuery,
    ) { workspaceId, query ->
        MentionQuery(
            workspaceId = workspaceId
                ?.takeIf { id -> id.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId,
            query = query,
        )
    }.mapLatest { query ->
        resolveMentionUseCase(query)
    }

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
        buildChatHistorySearchResults(
            sessions = sessions,
            messages = messages,
            query = query,
        )
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
            val pagesOverview = pageRepository.observePages(activeWorkspace.id)
                .mapLatest { pages ->
                    val sortedPages = pages.sortedByDescending { page -> page.updatedAt }
                    HomePagesOverview(
                        sortedPages = sortedPages,
                        tableReferences = buildPageTableReferences(sortedPages),
                    )
                }
            combine(
                pagesOverview,
                pageRepository.observeDeletedPages(activeWorkspace.id),
                taskRepository.observeOpenTaskCount(activeWorkspace.id),
                taskRepository.observeOpenTasks(activeWorkspace.id),
                reminderRepository.observePendingReminders(activeWorkspace.id),
            ) { pagesOverview, deletedPages, openTaskCount, openTasks, reminders ->
                HomeUiState(
                    isLoading = false,
                    activeWorkspaceId = activeWorkspace.id,
                    workspaceName = activeWorkspace.name,
                    workspaceCount = workspaceState.workspaces.size,
                    workspaces = workspaceState.workspaces,
                    pageCount = pagesOverview.sortedPages.size,
                    openTaskCount = openTaskCount,
                    pendingReminderCount = reminders.size,
                    allPages = pagesOverview.sortedPages,
                    allTableReferences = pagesOverview.tableReferences,
                    recentPages = pagesOverview.sortedPages.take(5),
                    deletedPages = deletedPages,
                    openTasks = openTasks,
                    reminders = reminders,
                )
            }
        }
    }

    private suspend fun buildPageTableReferences(pages: List<Page>): List<PageTableReference> =
        withContext(Dispatchers.Default) {
            pages
                .asSequence()
                .filter { page -> page.deletedAt == null }
                .flatMap { page ->
                    PageBlockCodec.decodeDocument(page.content)
                        .blocks
                        .tableReferences()
                        .asSequence()
                        .map { reference ->
                            reference.copy(
                                pageId = page.id,
                                pageTitle = page.title.ifBlank { "Untitled page" },
                            )
                        }
                }
                .distinctBy { reference -> reference.blockId }
                .toList()
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
        combine(
            baseState,
            chatHistorySearchQuery,
            chatHistorySearchResults,
            syncSettingsStore.isAutoSyncEnabled,
            syncSettingsStore.themeMode
        ) { state, chatSearchQuery, chatSearchResults, isAutoSyncEnabled, themeMode ->
            state.copy(
                chatHistorySearchQuery = chatSearchQuery,
                chatHistorySearchResults = chatSearchResults,
                isAutoSyncEnabled = isAutoSyncEnabled,
                themeMode = themeMode,
            )
        }
    }.let { baseState ->
        combine(baseState, searchQuery, searchScope, workspaceSearchResults) { state, query, scope, searchResults ->
            state.copy(
                searchQuery = query,
                searchScope = scope,
                searchResults = searchResults,
            )
        }
    }.let { baseState ->
        combine(baseState, aiModelLabel, aiStatusState) { state, modelLabel, aiStatus ->
            state.copy(
                aiModelLabel = modelLabel,
                aiVisionStatusLabel = aiStatus.toVisionStatusLabel(),
                aiVisionPipelineLabel = aiStatus.toVisionPipelineLabel(),
            )
        }
    }.let { baseState ->
        combine(baseState, activeWorkspaceSkills, aiSkillError, aiMentionCandidates) { state, skills, skillError, mentionCandidates ->
            state.copy(
                aiSkills = skills,
                aiSkillError = skillError,
                aiMentionCandidates = mentionCandidates,
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

    fun updateSearchScope(scope: HomeSearchScope) {
        searchScope.value = scope
    }

    fun updateAiMentionQuery(query: String) {
        aiMentionQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    fun retrySyncNow() {
        syncStatusRepository.retryNow()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        syncSettingsStore.setAutoSyncEnabled(enabled)
    }

    fun setThemeMode(mode: AppThemeMode) {
        syncSettingsStore.setThemeMode(mode)
    }

    fun syncNow() {
        backgroundSyncQueue.syncSessionSoon()
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

    fun saveAiSkill(
        skillId: String?,
        name: String,
        whenToUse: String,
        instructions: String,
        isEnabled: Boolean,
    ) {
        val normalizedName = name.trim().take(MaxAiSkillNameChars)
        val normalizedWhenToUse = whenToUse.trim().take(MaxAiSkillWhenToUseChars)
        val normalizedInstructions = instructions.trim().take(MaxAiSkillInstructionsChars)
        if (normalizedName.isBlank() || normalizedWhenToUse.isBlank() || normalizedInstructions.isBlank()) return

        viewModelScope.launch {
            aiSkillError.value = null
            runCatching {
                val workspaceId = workspaceRepository.getActiveWorkspaceId()
                    ?.takeIf { id -> id.isNotBlank() }
                    ?: CylDefaults.DefaultWorkspaceId
                val existing = skillId
                    ?.let { id ->
                        aiSkillRepository.observeSkills(workspaceId).first()
                            .firstOrNull { skill -> skill.id == id }
                    }
                val now = System.currentTimeMillis()
                aiSkillRepository.upsertSkill(
                    AiSkill(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        workspaceId = workspaceId,
                        name = normalizedName,
                        whenToUse = normalizedWhenToUse,
                        instructions = normalizedInstructions,
                        isEnabled = isEnabled,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }.onFailure {
                aiSkillError.value = "Could not save this skill. Please try again."
            }
        }
    }

    fun deleteAiSkill(skillId: String) {
        if (skillId.isBlank()) return
        viewModelScope.launch {
            aiSkillError.value = null
            runCatching {
                val workspaceId = workspaceRepository.getActiveWorkspaceId()
                    ?.takeIf { id -> id.isNotBlank() }
                    ?: CylDefaults.DefaultWorkspaceId
                aiSkillRepository.deleteSkill(workspaceId = workspaceId, skillId = skillId)
            }.onFailure {
                aiSkillError.value = "Could not delete this skill. Please try again."
            }
        }
    }

    fun setAiSkillEnabled(skillId: String, enabled: Boolean) {
        if (skillId.isBlank()) return
        viewModelScope.launch {
            aiSkillError.value = null
            runCatching {
                val workspaceId = workspaceRepository.getActiveWorkspaceId()
                    ?.takeIf { id -> id.isNotBlank() }
                    ?: CylDefaults.DefaultWorkspaceId
                aiSkillRepository.setSkillEnabled(
                    workspaceId = workspaceId,
                    skillId = skillId,
                    enabled = enabled,
                )
            }.onFailure {
                aiSkillError.value = "Could not update this skill. Please try again."
            }
        }
    }

    fun clearAiSkillError() {
        aiSkillError.value = null
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
            val visiblePrompt = prompt.trim()
            val requestPrompt = visiblePrompt.ifBlank {
                if (images.isNotEmpty()) ImageOnlyAiRequestPrompt else visiblePrompt
            }

            val workspaceId = workspaceRepository.getActiveWorkspaceId()
                ?.takeIf { it.isNotBlank() }
                ?: CylDefaults.DefaultWorkspaceId
            val scopeId = homeChatScopeId(workspaceId)
            val session = resolveActiveChatSession(scopeId)
            val currentMessages = chatHistoryRepository.observeMessages(session.id)
                .first()
                .let(AiChatMessageMapper::toAiChatMessages)
            val userMessage = AiChatMessage(role = "user", content = visiblePrompt)
            val messageAttachments = images.map { image ->
                ChatMessageAttachment(
                    id = UUID.randomUUID().toString(),
                    name = image.name.ifBlank {
                        if (image.kind == "text") "Attached file" else "Attached image"
                    },
                    mimeType = image.mimeType,
                    kind = image.kind,
                    sizeBytes = image.sizeBytes.coerceAtLeast(0L),
                    previewDataUrl = image.previewDataUrl,
                )
            }
            val savedUserMessage = chatHistoryRepository.appendMessage(
                sessionId = session.id,
                role = userMessage.role,
                content = userMessage.content,
                attachments = messageAttachments,
            )
            val pages = pageRepository.observePages(workspaceId)
                .first()
                .sortedByDescending { page -> page.updatedAt }
            val normalizedMentionedPageIds = (mentionedPageIds + listOfNotNull(attachedPageId))
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
            val searchContext = if (images.isEmpty() && !requestPrompt.referencesCurrentAttachment()) {
                buildAiSearchContextUseCase(
                    workspaceId = workspaceId,
                    prompt = requestPrompt,
                    currentPageId = scopedTargetPage?.id.orEmpty(),
                    limit = HomeAiSearchContextLimit,
                )
            } else {
                AiSearchContext.Empty
            }

            val memoryContext = buildAiMemoryContextUseCase(
                currentSessionId = session.id,
                prompt = requestPrompt,
                sessions = allChatSessions,
                messages = allChatMessages,
            )
            val memoryMessages = if (memoryContext.isNotBlank) {
                listOf(AiChatMessage(role = "system", content = memoryContext.content))
            } else {
                emptyList()
            }
            val skillContext = buildAiSkillContextUseCase(
                aiSkillRepository.observeSkills(workspaceId).first(),
            )
            val skillMessages = skillContext
                .takeIf { context -> context.isNotBlank() }
                ?.let { context -> listOf(AiChatMessage(role = "system", content = context)) }
                .orEmpty()
            val searchMessages = if (searchContext.isNotBlank) {
                listOf(AiChatMessage(role = "system", content = searchContext.content))
            } else {
                emptyList()
            }
            val searchPageLinks = searchContext.results.toAiSearchPageLinks()
            val messagesForAi = skillMessages + memoryMessages + searchMessages +
                currentMessages.filter { message -> message.content.isNotBlank() } +
                userMessage.copy(
                    content = requestPrompt.withMentionContext(explicitlyMentionedPages),
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
                        prompt = visiblePrompt,
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
                        pageLinks = (orchestration.pageLinks + searchPageLinks)
                            .distinctBy { link -> "${link.pageId}:${link.targetType}:${link.targetId}" }
                            .toDomainChatPageLinks(),
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

private fun SearchResult.toHomeSearchResult(): HomeSearchResult? {
    val routeTarget = target.toPageRouteTarget()
    val pageId = target.pageId.takeIf { id -> id.isNotBlank() }
        ?: target.chatSessionId.takeIf { id -> target.type == SearchTargetType.Chat && id.isNotBlank() }
        ?: return null
    return HomeSearchResult(
        id = id,
        pageId = pageId,
        title = title.ifBlank { "Untitled page" },
        subtitle = subtitle,
        snippet = snippet.compactLine(),
        targetLabel = target.type.searchLabel(),
        targetType = routeTarget.type,
        targetId = routeTarget.id,
        chatSessionId = target.chatSessionId,
        chatMessageId = target.chatMessageId,
        score = score,
        updatedAt = updatedAt,
    )
}

private fun List<SearchResult>.toAiSearchPageLinks(): List<AiChatPageLink> =
    mapNotNull { result ->
        val pageId = result.target.pageId.takeIf { id -> id.isNotBlank() } ?: return@mapNotNull null
        val routeTarget = result.target.toPageRouteTarget()
        AiChatPageLink(
            pageId = pageId,
            title = result.title.ifBlank { result.subtitle.ifBlank { "Search result" } },
            targetType = routeTarget.type,
            targetId = routeTarget.id,
        )
    }
        .distinctBy { link -> "${link.pageId}:${link.targetType}:${link.targetId}" }

private fun SearchTarget.toPageRouteTarget(): HomeSearchRouteTarget = when (type) {
    SearchTargetType.Page -> HomeSearchRouteTarget(SearchTargetPageTitle, "")
    SearchTargetType.Block -> HomeSearchRouteTarget(SearchTargetBlock, blockId)
    SearchTargetType.Table -> HomeSearchRouteTarget(SearchTargetBlock, tableBlockId.ifBlank { blockId })
    SearchTargetType.Row -> HomeSearchRouteTarget(SearchTargetRow, rowId)
    SearchTargetType.Cell -> HomeSearchRouteTarget(SearchTargetRow, rowId)
    SearchTargetType.Column -> HomeSearchRouteTarget(SearchTargetBlock, tableBlockId.ifBlank { blockId })
    SearchTargetType.Property -> HomeSearchRouteTarget(SearchTargetPageTitle, "")
    SearchTargetType.Chat -> HomeSearchRouteTarget(SearchTargetChat, chatMessageId)
}

private fun SearchTargetType.searchLabel(): String = when (this) {
    SearchTargetType.Page -> "Page"
    SearchTargetType.Block -> "Block"
    SearchTargetType.Table -> "Database"
    SearchTargetType.Row -> "Row"
    SearchTargetType.Property -> "Property"
    SearchTargetType.Column -> "Column"
    SearchTargetType.Cell -> "Cell"
    SearchTargetType.Chat -> "Chat"
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

private const val SearchTargetPageTitle = "title"
private const val SearchTargetBlock = "block"
private const val SearchTargetRow = "row"
const val SearchTargetChat = "chat"
private const val HomeSearchResultLimit = 40
private const val HomeAiSearchContextLimit = 8
private const val DraftHomeChatSessionId = "draft-home-chat"
private const val ImageOnlyAiRequestPrompt =
    "Describe the attached image or file and extract the useful visible content."
private const val MaxAiSkillNameChars = 64
private const val MaxAiSkillWhenToUseChars = 320
private const val MaxAiSkillInstructionsChars = 2_000

private fun String.referencesCurrentAttachment(): Boolean {
    val text = lowercase()
    val attachmentWords = listOf(
        "gambar",
        "image",
        "imej",
        "photo",
        "foto",
        "screenshot",
        "tangkapan skrin",
        "lampiran",
        "attachment",
        "attached",
        "file",
        "dokumen",
        "document",
        "pdf",
    )
    if (attachmentWords.none { word -> text.contains(word) }) return false

    val currentAttachmentHints = listOf(
        "ini",
        "itu",
        "tersebut",
        "this",
        "that",
        "attached",
        "dilampir",
        "lampirkan",
        "upload",
        "uploaded",
        "paste",
        "pasted",
        "masukkan",
        "dalam gambar",
        "pada gambar",
        "dari gambar",
        "daripada gambar",
        "from the image",
        "in the image",
        "this file",
        "this document",
    )
    val analysisWords = listOf(
        "apa",
        "baca",
        "read",
        "lihat",
        "nampak",
        "see",
        "describe",
        "terangkan",
        "extract",
        "ocr",
        "scan",
        "teks",
        "text",
        "tulisan",
        "isi",
        "kandungan",
    )
    return currentAttachmentHints.any { hint -> text.contains(hint) } ||
        analysisWords.any { word -> text.contains(word) }
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

private data class HomePagesOverview(
    val sortedPages: List<Page>,
    val tableReferences: List<PageTableReference>,
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

private fun AiStatus.toVisionStatusLabel(): String {
    val model = lmStudioVisionModels
        .split(',')
        .map { value -> value.trim() }
        .firstOrNull { value -> value.isNotBlank() }
        ?: ""
    return when {
        model.isNotBlank() -> model
        visionPipelineVersion.isNotBlank() -> "Configured"
        else -> ""
    }
}

private fun AiStatus.toVisionPipelineLabel(): String {
    val parts = buildList {
        if (visionPipelineVersion.isNotBlank()) add(visionPipelineVersion)
        if (visionMaxImageDimension > 0) add("${visionMaxImageDimension}px")
        if (visionMaxImageBytes > 0) add("${visionMaxImageBytes / 1024}KB")
    }
    return parts.joinToString(" · ")
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
        .filterNot { line -> line.equals("CYL_SEARCH_CONTEXT:", ignoreCase = true) }
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
    val allTableReferences: List<PageTableReference> = emptyList(),
    val recentPages: List<Page> = emptyList(),
    val deletedPages: List<Page> = emptyList(),
    val searchQuery: String = "",
    val searchScope: HomeSearchScope = HomeSearchScope.All,
    val searchResults: List<HomeSearchResult> = emptyList(),
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
    val aiVisionStatusLabel: String = "",
    val aiVisionPipelineLabel: String = "",
    val aiSkills: List<AiSkill> = emptyList(),
    val aiSkillError: String? = null,
    val aiMentionCandidates: List<MentionCandidate> = emptyList(),
    val syncOverview: SyncOverview = SyncOverview(),
    val isAutoSyncEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

enum class HomeSearchScope(
    val label: String,
    val targetTypes: Set<SearchTargetType>,
) {
    All(
        label = "All",
        targetTypes = SearchTargetType.defaultSearchScopes() + SearchTargetType.Chat,
    ),
    Pages(
        label = "Pages",
        targetTypes = setOf(SearchTargetType.Page),
    ),
    Blocks(
        label = "Blocks",
        targetTypes = setOf(SearchTargetType.Block),
    ),
    Tables(
        label = "Tables",
        targetTypes = setOf(SearchTargetType.Table, SearchTargetType.Column),
    ),
    Rows(
        label = "Rows",
        targetTypes = setOf(SearchTargetType.Row, SearchTargetType.Cell),
    ),
    Properties(
        label = "Properties",
        targetTypes = setOf(SearchTargetType.Property, SearchTargetType.Column),
    ),
    Chats(
        label = "Chats",
        targetTypes = setOf(SearchTargetType.Chat),
    ),
}

private data class HomeTableUpdateResult(
    val page: Page,
    val tableTitle: String,
)

data class HomeSearchResult(
    val id: String,
    val pageId: String,
    val title: String,
    val subtitle: String,
    val snippet: String,
    val targetLabel: String,
    val targetType: String = "",
    val targetId: String = "",
    val chatSessionId: String = "",
    val chatMessageId: String = "",
    val score: Int = 0,
    val updatedAt: Long = 0,
)

private data class HomeSearchRequest(
    val workspaceId: String,
    val query: String,
    val scope: HomeSearchScope,
)

private data class HomeSearchRouteTarget(
    val type: String,
    val id: String,
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
