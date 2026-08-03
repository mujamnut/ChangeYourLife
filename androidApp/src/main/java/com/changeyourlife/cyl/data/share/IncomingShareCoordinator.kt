package com.changeyourlife.cyl.data.share

import android.content.Intent
import com.changeyourlife.cyl.domain.model.IncomingShareDraftSeed
import com.changeyourlife.cyl.domain.model.IncomingShareDraftStatus
import com.changeyourlife.cyl.domain.model.IncomingShareErrorCode
import com.changeyourlife.cyl.domain.model.IncomingShareItem
import com.changeyourlife.cyl.domain.model.IncomingShareItemStatus
import com.changeyourlife.cyl.domain.model.IncomingShareLimits
import com.changeyourlife.cyl.domain.repository.IncomingShareDraftRepository
import com.changeyourlife.cyl.domain.repository.ContentAssetLocalStore
import com.changeyourlife.cyl.domain.usecase.asset.StageIncomingShareItemUseCase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class IncomingShareCoordinator @Inject constructor(
    private val parser: ShareIntentParser,
    private val repository: IncomingShareDraftRepository,
    private val stageItem: StageIncomingShareItemUseCase,
    private val localStore: ContentAssetLocalStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pendingDraftId = MutableStateFlow<String?>(null)
    private val _intakeError = MutableStateFlow<IncomingShareErrorCode?>(null)

    val pendingDraftId: StateFlow<String?> = _pendingDraftId.asStateFlow()
    val intakeError: StateFlow<IncomingShareErrorCode?> = _intakeError.asStateFlow()

    init {
        scope.launch {
            repository.getPendingDrafts(System.currentTimeMillis())
                .lastOrNull()
                ?.let { draft ->
                    if (draft.status == IncomingShareDraftStatus.IMPORTING) {
                        repository.transitionDraft(
                            draftId = draft.id,
                            expectedStatuses = setOf(IncomingShareDraftStatus.IMPORTING),
                            nextStatus = IncomingShareDraftStatus.STAGED,
                            errorCode = InterruptedImportError,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    _pendingDraftId.value = draft.id
                }
        }
    }

    fun accept(intent: Intent): Boolean {
        val parsed = parser.parse(intent)
        if (parsed is ShareIntentParseResult.Failure) {
            if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                _intakeError.value = parsed.error
            }
            return false
        }
        parsed as ShareIntentParseResult.Success
        val eventId = intent.getStringExtra(InternalShareEventIdExtra)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { id ->
                intent.putExtra(InternalShareEventIdExtra, id)
            }
        _intakeError.value = null
        scope.launch { persistAndStage(eventId, parsed) }
        return true
    }

    fun consumeNavigation(draftId: String) {
        if (_pendingDraftId.value == draftId) _pendingDraftId.value = null
    }

    fun clearError() {
        _intakeError.value = null
    }

    private suspend fun persistAndStage(
        eventId: String,
        parsed: ShareIntentParseResult.Success,
    ) {
        try {
            val existing = repository.getByEventId(eventId)
            val draft = existing ?: createDraft(eventId, parsed)
            _pendingDraftId.value = draft.id
            if (draft.status in TerminalOrReadyDraftStatuses) return

            repository.transitionDraft(
                draftId = draft.id,
                expectedStatuses = setOf(
                    IncomingShareDraftStatus.RECEIVED,
                    IncomingShareDraftStatus.VALIDATING,
                ),
                nextStatus = IncomingShareDraftStatus.VALIDATING,
                updatedAt = System.currentTimeMillis(),
            )
            var totalBytes = draft.items
                .filter { item -> item.status == IncomingShareItemStatus.STAGED }
                .sumOf(IncomingShareItem::sizeBytes)
            for (item in draft.items.sortedBy(IncomingShareItem::position)) {
                if (item.status == IncomingShareItemStatus.STAGED || item.status == IncomingShareItemStatus.REMOVED) {
                    continue
                }
                val latestDraft = repository.get(draft.id) ?: return
                if (latestDraft.status == IncomingShareDraftStatus.CANCELLED) return
                val staging = item.copy(
                    status = IncomingShareItemStatus.STAGING,
                    errorCode = null,
                )
                repository.updateItem(staging)
                val staged = stageItem(
                    item = staging,
                    remainingTotalBytes = (IncomingShareLimits.MAX_TOTAL_BYTES - totalBytes).coerceAtLeast(0L),
                )
                val latestAfterStaging = repository.get(draft.id) ?: return
                val wasRemoved = latestAfterStaging.items
                    .firstOrNull { latestItem -> latestItem.id == item.id }
                    ?.status == IncomingShareItemStatus.REMOVED
                if (latestAfterStaging.status == IncomingShareDraftStatus.CANCELLED || wasRemoved) {
                    staged.stagedPath?.let { path -> localStore.delete(path) }
                    if (latestAfterStaging.status == IncomingShareDraftStatus.CANCELLED) return
                    continue
                }
                repository.updateItem(staged)
                if (staged.status == IncomingShareItemStatus.STAGED) totalBytes += staged.sizeBytes
            }

            val current = repository.get(draft.id) ?: return
            val stagedCount = current.items.count { item -> item.status == IncomingShareItemStatus.STAGED }
            val failedCount = current.items.count { item -> item.status == IncomingShareItemStatus.FAILED }
            repository.transitionDraft(
                draftId = draft.id,
                expectedStatuses = setOf(IncomingShareDraftStatus.VALIDATING),
                nextStatus = if (stagedCount > 0) {
                    IncomingShareDraftStatus.STAGED
                } else {
                    IncomingShareDraftStatus.FAILED
                },
                errorCode = when {
                    stagedCount == 0 -> IncomingShareErrorCode.UNKNOWN.wireValue
                    failedCount > 0 -> IncomingShareErrorCode.PARTIAL_FAILURE.wireValue
                    else -> null
                },
                updatedAt = System.currentTimeMillis(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _intakeError.value = IncomingShareErrorCode.PERSISTENCE_FAILED
        }
    }

    private suspend fun createDraft(
        eventId: String,
        parsed: ShareIntentParseResult.Success,
    ) = run {
        val now = System.currentTimeMillis()
        val draftId = UUID.randomUUID().toString()
        repository.createOrGet(
            IncomingShareDraftSeed(
                id = draftId,
                eventId = eventId,
                action = parsed.action,
                subject = parsed.subject,
                status = IncomingShareDraftStatus.RECEIVED,
                items = parsed.items.mapIndexed { index, item ->
                    IncomingShareItem(
                        id = UUID.randomUUID().toString(),
                        draftId = draftId,
                        position = index,
                        kind = item.kind,
                        sourceUri = item.sourceUri,
                        text = item.text,
                        html = item.html,
                        displayName = item.displayName,
                        declaredMimeType = item.declaredMimeType,
                        status = IncomingShareItemStatus.RECEIVED,
                    )
                },
                createdAt = now,
                updatedAt = now,
                expiresAt = now + IncomingShareLimits.DRAFT_TTL_MILLIS,
            ),
        )
    }
}

private val TerminalOrReadyDraftStatuses = setOf(
    IncomingShareDraftStatus.STAGED,
    IncomingShareDraftStatus.UPLOAD_QUEUED,
    IncomingShareDraftStatus.COMPLETED,
    IncomingShareDraftStatus.FAILED,
    IncomingShareDraftStatus.CANCELLED,
)

private const val InternalShareEventIdExtra = "com.changeyourlife.cyl.extra.SHARE_EVENT_ID"
private const val InterruptedImportError = "import_interrupted"
