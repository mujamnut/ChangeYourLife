package com.changeyourlife.cyl.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.data.share.IncomingShareCoordinator
import com.changeyourlife.cyl.domain.model.IncomingShareDraft
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import com.changeyourlife.cyl.domain.repository.PageRepository
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.usecase.share.CancelIncomingShareDraftUseCase
import com.changeyourlife.cyl.domain.usecase.share.ImportSharedContentError
import com.changeyourlife.cyl.domain.usecase.share.ImportSharedContentRequest
import com.changeyourlife.cyl.domain.usecase.share.ImportSharedContentResult
import com.changeyourlife.cyl.domain.usecase.share.ImportSharedContentUseCase
import com.changeyourlife.cyl.domain.usecase.share.RemoveIncomingShareItemUseCase
import com.changeyourlife.cyl.domain.usecase.share.RetryIncomingShareItemUseCase
import com.changeyourlife.cyl.domain.usecase.share.SharedImportDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: IncomingShareDraftRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val pageRepository: PageRepository,
    private val importSharedContent: ImportSharedContentUseCase,
    private val removeItem: RemoveIncomingShareItemUseCase,
    private val retryItem: RetryIncomingShareItemUseCase,
    private val cancelDraft: CancelIncomingShareDraftUseCase,
    coordinator: IncomingShareCoordinator,
) : ViewModel() {
    private val draftId: String = savedStateHandle.get<String>(DraftIdArgument).orEmpty()
    private val _uiState = MutableStateFlow(ShareImportUiState())
    private val eventChannel = Channel<ShareImportEvent>(Channel.BUFFERED)
    private var appliedSuggestedTitle = false

    val uiState: StateFlow<ShareImportUiState> = _uiState
    val events = eventChannel.receiveAsFlow()

    init {
        coordinator.consumeNavigation(draftId)
        if (draftId.isBlank()) {
            _uiState.update { state ->
                state.copy(isLoading = false, errorMessage = "This shared draft is invalid.")
            }
        } else {
            observeDraft()
            observeDestinationPages()
        }
    }

    fun setDestinationMode(mode: ShareImportDestinationMode) {
        _uiState.update { state -> state.copy(destinationMode = mode, errorMessage = null) }
    }

    fun setTitle(value: String) {
        appliedSuggestedTitle = true
        _uiState.update { state -> state.copy(title = value.take(MaxTitleLength), errorMessage = null) }
    }

    fun selectPage(pageId: String) {
        _uiState.update { state -> state.copy(selectedPageId = pageId, errorMessage = null) }
    }

    fun remove(itemId: String) {
        if (itemId.isBlank() || _uiState.value.isBusy) return
        viewModelScope.launch {
            setItemBusy(itemId, true)
            try {
                if (!removeItem(draftId, itemId)) {
                    showSafeError("The shared item could not be removed.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showSafeError("The shared item could not be removed.")
            } finally {
                setItemBusy(itemId, false)
            }
        }
    }

    fun retry(itemId: String) {
        if (itemId.isBlank() || _uiState.value.isBusy) return
        viewModelScope.launch {
            setItemBusy(itemId, true)
            try {
                if (!retryItem(draftId, itemId)) {
                    showSafeError("The shared item is still unavailable.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showSafeError("The shared item is still unavailable.")
            } finally {
                setItemBusy(itemId, false)
            }
        }
    }

    fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm || state.isBusy) return
        val workspaceId = state.workspaceId
        val destination = when (state.destinationMode) {
            ShareImportDestinationMode.NewPage -> SharedImportDestination.NewPage(
                workspaceId = workspaceId,
                title = state.title,
            )
            ShareImportDestinationMode.ExistingPage -> SharedImportDestination.ExistingPage(
                pageId = state.selectedPageId,
            )
        }
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isImporting = true, errorMessage = null) }
            try {
                when (
                    val result = importSharedContent(
                        ImportSharedContentRequest(
                            draftId = draftId,
                            destination = destination,
                        ),
                    )
                ) {
                    is ImportSharedContentResult.Success -> {
                        eventChannel.send(ShareImportEvent.Imported(result.pageId))
                    }
                    is ImportSharedContentResult.Rejected -> {
                        _uiState.update { current ->
                            current.copy(
                                isImporting = false,
                                errorMessage = result.error.userMessage(),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update { current ->
                    current.copy(
                        isImporting = false,
                        errorMessage = "The import could not be completed. Your draft is still available.",
                    )
                }
            }
        }
    }

    fun askAi() {
        val state = _uiState.value
        if (!state.canAskAi || state.isBusy) return
        viewModelScope.launch {
            eventChannel.send(ShareImportEvent.AskAi(draftId))
        }
    }

    fun cancel() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isCancelling = true, errorMessage = null) }
            val cancelled = try {
                cancelDraft(draftId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (cancelled) {
                eventChannel.send(ShareImportEvent.Cancelled)
            } else {
                _uiState.update { current ->
                    current.copy(
                        isCancelling = false,
                        errorMessage = "This import can no longer be cancelled.",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(errorMessage = null) }
    }

    private fun observeDraft() {
        viewModelScope.launch {
            draftRepository.observe(draftId).collect { draft ->
                _uiState.update { state ->
                    val suggested = if (!appliedSuggestedTitle && draft != null) {
                        draft.suggestedTitle()
                    } else {
                        state.title
                    }
                    if (draft != null && !appliedSuggestedTitle) appliedSuggestedTitle = true
                    state.copy(
                        draft = draft,
                        title = suggested,
                        isLoading = false,
                        errorMessage = when {
                            draft == null -> "This shared draft is no longer available."
                            draft.status == IncomingShareDraftStatus.CANCELLED -> "This import was cancelled."
                            draft.status == IncomingShareDraftStatus.FAILED && draft.items.none {
                                item -> item.status == IncomingShareItemStatus.STAGED
                            } -> "None of the shared items could be prepared."
                            else -> state.errorMessage
                        },
                    )
                }
            }
        }
    }

    private fun observeDestinationPages() {
        viewModelScope.launch {
            workspaceRepository.ensureDefaultWorkspace()
            val workspaceId = workspaceRepository.getActiveWorkspaceId().orEmpty()
            if (workspaceId.isBlank()) {
                _uiState.update { state ->
                    state.copy(errorMessage = "A destination workspace is unavailable.")
                }
                return@launch
            }
            _uiState.update { state -> state.copy(workspaceId = workspaceId) }
            pageRepository.observePages(workspaceId).collect { pages ->
                _uiState.update { state ->
                    val selected = state.selectedPageId.takeIf { selectedId ->
                        pages.any { page -> page.id == selectedId }
                    }.orEmpty()
                    state.copy(
                        pages = pages,
                        selectedPageId = selected,
                    )
                }
            }
        }
    }

    private fun setItemBusy(itemId: String, busy: Boolean) {
        _uiState.update { state ->
            state.copy(
                busyItemIds = if (busy) state.busyItemIds + itemId else state.busyItemIds - itemId,
            )
        }
    }

    private fun showSafeError(message: String) {
        _uiState.update { state -> state.copy(errorMessage = message) }
    }
}

data class ShareImportUiState(
    val isLoading: Boolean = true,
    val draft: IncomingShareDraft? = null,
    val workspaceId: String = "",
    val title: String = "",
    val destinationMode: ShareImportDestinationMode = ShareImportDestinationMode.NewPage,
    val pages: List<Page> = emptyList(),
    val selectedPageId: String = "",
    val busyItemIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val isCancelling: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleItems get() = draft?.items.orEmpty().filter { item -> item.status != IncomingShareItemStatus.REMOVED }
    val stagedItemCount get() = visibleItems.count { item -> item.status == IncomingShareItemStatus.STAGED }
    val isBusy get() = isImporting || isCancelling || busyItemIds.isNotEmpty()
    val canConfirm get() =
        draft?.status == IncomingShareDraftStatus.STAGED &&
            stagedItemCount > 0 &&
            workspaceId.isNotBlank() &&
            when (destinationMode) {
                ShareImportDestinationMode.NewPage -> title.isNotBlank()
                ShareImportDestinationMode.ExistingPage -> selectedPageId.isNotBlank()
            }
    val canAskAi get() =
        draft?.status == IncomingShareDraftStatus.STAGED &&
            stagedItemCount > 0 &&
            workspaceId.isNotBlank()
}

enum class ShareImportDestinationMode {
    NewPage,
    ExistingPage,
}

sealed interface ShareImportEvent {
    data class Imported(val pageId: String) : ShareImportEvent
    data class AskAi(val draftId: String) : ShareImportEvent
    data object Cancelled : ShareImportEvent
}

private fun IncomingShareDraft.suggestedTitle(): String {
    val fromSubject = subject.trim().lineSequence().firstOrNull().orEmpty()
    if (fromSubject.isNotBlank()) return fromSubject.take(MaxTitleLength)
    val fromText = items.firstNotNullOfOrNull { item ->
        item.text
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull(String::isNotBlank)
    }.orEmpty()
    return fromText.take(MaxTitleLength).ifBlank { "Imported content" }
}

private fun ImportSharedContentError.userMessage(): String = when (this) {
    ImportSharedContentError.DRAFT_NOT_FOUND -> "This shared draft is no longer available."
    ImportSharedContentError.DRAFT_NOT_READY -> "The shared content is still being prepared."
    ImportSharedContentError.DESTINATION_NOT_FOUND -> "The selected destination no longer exists."
    ImportSharedContentError.REVISION_CONFLICT ->
        "That page changed while this import was open. Review it and try again."
    ImportSharedContentError.EMPTY_CONTENT -> "There is no supported content left to import."
    ImportSharedContentError.INVALID_ASSET -> "One of the prepared files is no longer valid."
    ImportSharedContentError.UNKNOWN -> "The import could not be completed. Your draft is still available."
}

internal const val DraftIdArgument = "draftId"
private const val MaxTitleLength = 160
