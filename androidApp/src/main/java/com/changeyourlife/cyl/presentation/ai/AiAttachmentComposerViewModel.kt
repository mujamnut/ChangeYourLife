package com.changeyourlife.cyl.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.changeyourlife.cyl.aicontract.CYL_MAX_AI_ATTACHMENTS
import com.changeyourlife.cyl.domain.model.AiAttachment
import com.changeyourlife.cyl.domain.model.AiAttachmentPreparationError
import com.changeyourlife.cyl.domain.model.AiAttachmentSources
import com.changeyourlife.cyl.core.constants.CylDefaults
import com.changeyourlife.cyl.domain.model.PrepareAiAttachmentRequest
import com.changeyourlife.cyl.domain.model.PrepareAiAttachmentResult
import com.changeyourlife.cyl.domain.repository.AiCameraCaptureGateway
import com.changeyourlife.cyl.domain.repository.AiCameraCaptureTarget
import com.changeyourlife.cyl.domain.repository.WorkspaceRepository
import com.changeyourlife.cyl.domain.usecase.ai.DiscardAiAttachmentUseCase
import com.changeyourlife.cyl.domain.usecase.ai.PrepareAiAttachmentUseCase
import com.changeyourlife.cyl.domain.usecase.ai.PrepareIncomingShareForAiResult
import com.changeyourlife.cyl.domain.usecase.ai.PrepareIncomingShareForAiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiAttachmentComposerViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val prepareAttachment: PrepareAiAttachmentUseCase,
    private val prepareIncomingShare: PrepareIncomingShareForAiUseCase,
    private val discardAttachment: DiscardAiAttachmentUseCase,
    private val cameraGateway: AiCameraCaptureGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(AiAttachmentComposerState())
    private val eventChannel = Channel<AiAttachmentComposerEvent>(Channel.BUFFERED)
    private val preparedDraftIds = mutableSetOf<String>()
    private val preparingDraftIds = mutableSetOf<String>()
    private var pendingCameraTarget: AiCameraCaptureTarget? = null

    val state: StateFlow<AiAttachmentComposerState> = _state.asStateFlow()
    val events = eventChannel.receiveAsFlow()

    fun prepareUri(
        sourceUri: String,
        fallbackName: String,
        declaredMimeType: String = "",
        source: String = AiAttachmentSources.ComposerPicker,
        imageOnly: Boolean = false,
    ) {
        if (sourceUri.isBlank()) return
        viewModelScope.launch {
            prepareAndAppend(
                sourceUri = sourceUri,
                fallbackName = fallbackName,
                declaredMimeType = declaredMimeType,
                source = source,
                imageOnly = imageOnly,
            )
        }
    }

    fun prepareUris(
        sourceUris: List<String>,
        fallbackName: String,
        source: String,
        imageOnly: Boolean,
    ) {
        sourceUris.filter(String::isNotBlank).forEachIndexed { index, uri ->
            prepareUri(
                sourceUri = uri,
                fallbackName = if (sourceUris.size == 1) fallbackName else "$fallbackName ${index + 1}",
                source = source,
                imageOnly = imageOnly,
            )
        }
    }

    fun prepareIncomingShare(draftId: String) {
        if (
            draftId.isBlank() ||
            draftId in preparedDraftIds ||
            !preparingDraftIds.add(draftId)
        ) return
        viewModelScope.launch {
            setPreparing(true)
            try {
                val workspaceId = activeWorkspaceId()
                when (val result = prepareIncomingShare(workspaceId, draftId)) {
                    is PrepareIncomingShareForAiResult.Success -> {
                        val availableSlots = availableSlots()
                        val accepted = result.attachments.take(availableSlots)
                        _state.update { current ->
                            current.copy(
                                attachments = (current.attachments + accepted).distinctBy(AiAttachment::id),
                                lastPreparedIncomingShareDraftId = draftId,
                            )
                        }
                        preparedDraftIds += draftId
                        val skipped = result.skippedItemCount + (result.attachments.size - accepted.size)
                        if (skipped > 0) {
                            showError("$skipped shared item(s) could not be attached.")
                        }
                    }
                    is PrepareIncomingShareForAiResult.Rejected -> {
                        showError(result.detail.ifBlank { result.error.userMessage() })
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showError(AiAttachmentPreparationError.UNKNOWN.userMessage())
            } finally {
                preparingDraftIds -= draftId
                setPreparing(false)
            }
        }
    }

    fun consumePreparedIncomingShare(draftId: String) {
        _state.update { current ->
            if (current.lastPreparedIncomingShareDraftId == draftId) {
                current.copy(lastPreparedIncomingShareDraftId = null)
            } else {
                current
            }
        }
    }

    fun requestCameraCapture() {
        viewModelScope.launch {
            pendingCameraTarget?.let { target -> cameraGateway.discard(target) }
            val target = cameraGateway.createTarget()
            if (target == null) {
                showError("Camera storage is unavailable.")
                return@launch
            }
            pendingCameraTarget = target
            eventChannel.send(AiAttachmentComposerEvent.LaunchCamera(target.uri))
        }
    }

    fun onCameraCaptureResult(success: Boolean) {
        val target = pendingCameraTarget ?: return
        pendingCameraTarget = null
        viewModelScope.launch {
            try {
                if (success) {
                    prepareAndAppend(
                        sourceUri = target.uri,
                        fallbackName = "Camera.jpg",
                        declaredMimeType = "image/jpeg",
                        source = AiAttachmentSources.ComposerCamera,
                        imageOnly = true,
                    )
                }
            } finally {
                cameraGateway.discard(target)
            }
        }
    }

    fun removeAttachment(attachmentId: String) {
        val attachment = _state.value.attachments.firstOrNull { item -> item.id == attachmentId } ?: return
        _state.update { current ->
            current.copy(attachments = current.attachments.filterNot { item -> item.id == attachmentId })
        }
        viewModelScope.launch {
            runCatching { discardAttachment(attachment) }
        }
    }

    fun clearAfterSend() {
        _state.update { current -> current.copy(attachments = emptyList()) }
        preparedDraftIds.clear()
    }

    fun discardComposer() {
        val discarded = _state.value.attachments
        _state.update {
            AiAttachmentComposerState(errorVersion = it.errorVersion)
        }
        preparedDraftIds.clear()
        viewModelScope.launch {
            discarded.forEach { attachment -> runCatching { discardAttachment(attachment) } }
            pendingCameraTarget?.let { target -> cameraGateway.discard(target) }
            pendingCameraTarget = null
        }
    }

    fun consumeError(version: Long) {
        _state.update { current ->
            if (current.errorVersion == version) current.copy(errorMessage = null) else current
        }
    }

    private suspend fun prepareAndAppend(
        sourceUri: String,
        fallbackName: String,
        declaredMimeType: String,
        source: String,
        imageOnly: Boolean,
    ) {
        if (availableSlots() <= 0) {
            showError("You can attach up to $CYL_MAX_AI_ATTACHMENTS files.")
            return
        }
        setPreparing(true)
        try {
            when (
                val result = prepareAttachment(
                    PrepareAiAttachmentRequest(
                        workspaceId = activeWorkspaceId(),
                        sourceUri = sourceUri,
                        fallbackName = fallbackName,
                        declaredMimeType = declaredMimeType,
                        source = source,
                        imageOnly = imageOnly,
                    ),
                )
            ) {
                is PrepareAiAttachmentResult.Success -> {
                    if (availableSlots() <= 0) {
                        discardAttachment(result.attachment)
                        showError("You can attach up to $CYL_MAX_AI_ATTACHMENTS files.")
                    } else {
                        _state.update { current ->
                            current.copy(
                                attachments = (current.attachments + result.attachment)
                                    .distinctBy(AiAttachment::id),
                            )
                        }
                    }
                }
                is PrepareAiAttachmentResult.Rejected -> {
                    showError(result.detail.ifBlank { result.error.userMessage() })
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            showError(AiAttachmentPreparationError.UNKNOWN.userMessage())
        } finally {
            setPreparing(false)
        }
    }

    private suspend fun activeWorkspaceId(): String = workspaceRepository.getActiveWorkspaceId()
        ?.takeIf(String::isNotBlank)
        ?: CylDefaults.DefaultWorkspaceId

    private fun availableSlots(): Int =
        (CYL_MAX_AI_ATTACHMENTS - _state.value.attachments.size).coerceAtLeast(0)

    private fun setPreparing(started: Boolean) {
        _state.update { current ->
            current.copy(
                preparingCount = (current.preparingCount + if (started) 1 else -1).coerceAtLeast(0),
            )
        }
    }

    private fun showError(message: String) {
        _state.update { current ->
            current.copy(
                errorMessage = message,
                errorVersion = current.errorVersion + 1,
            )
        }
    }
}

data class AiAttachmentComposerState(
    val attachments: List<AiAttachment> = emptyList(),
    val preparingCount: Int = 0,
    val errorMessage: String? = null,
    val errorVersion: Long = 0L,
    val lastPreparedIncomingShareDraftId: String? = null,
) {
    val isPreparing: Boolean get() = preparingCount > 0
}

sealed interface AiAttachmentComposerEvent {
    data class LaunchCamera(val uri: String) : AiAttachmentComposerEvent
}

private fun AiAttachmentPreparationError.userMessage(): String = when (this) {
    AiAttachmentPreparationError.INVALID_REQUEST -> "That attachment is invalid."
    AiAttachmentPreparationError.SOURCE_UNAVAILABLE -> "That attachment is no longer available."
    AiAttachmentPreparationError.PERMISSION_DENIED -> "CYL cannot read that attachment."
    AiAttachmentPreparationError.EMPTY_FILE -> "That attachment is empty."
    AiAttachmentPreparationError.TOO_LARGE -> "That attachment is too large for AI."
    AiAttachmentPreparationError.UNSUPPORTED_TYPE -> "This file type is not readable by AI yet."
    AiAttachmentPreparationError.IMAGE_REQUIRED -> "Only images can be pasted here."
    AiAttachmentPreparationError.CORRUPT_CONTENT -> "That attachment appears to be corrupt."
    AiAttachmentPreparationError.STORAGE_UNAVAILABLE -> "Attachment storage is unavailable."
    AiAttachmentPreparationError.PERSISTENCE_FAILED -> "The attachment could not be saved."
    AiAttachmentPreparationError.DRAFT_NOT_FOUND -> "This shared draft is no longer available."
    AiAttachmentPreparationError.DRAFT_NOT_READY -> "The shared content is still being prepared."
    AiAttachmentPreparationError.TOO_MANY_ATTACHMENTS ->
        "You can attach up to $CYL_MAX_AI_ATTACHMENTS files."
    AiAttachmentPreparationError.UNKNOWN -> "The attachment could not be prepared."
}
