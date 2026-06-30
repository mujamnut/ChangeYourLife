package com.changeyourlife.cyl.data.sync

import android.util.Log
import com.changeyourlife.cyl.domain.model.SyncRunState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class BackgroundSyncQueue @Inject constructor(
    private val syncCoordinator: SessionSyncCoordinator,
    private val persistentSyncScheduler: PersistentSyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<SyncOperation>(capacity = Channel.UNLIMITED)
    private var retryJob: Job? = null
    private val _state = MutableStateFlow(SyncRunState())

    val state: StateFlow<SyncRunState> = _state.asStateFlow()

    init {
        scope.launch {
            for (operation in operations) {
                _state.update { current ->
                    current.copy(
                        isSyncing = true,
                        activeOperationName = operation.name,
                    )
                }
                runCatching {
                    operation.block(syncCoordinator)
                    _state.update {
                        SyncRunState(
                            lastCompletedAt = System.currentTimeMillis(),
                        )
                    }
                }.onFailure { error ->
                    Log.w(Tag, "Background sync operation failed: ${operation.name}", error)
                    _state.update { current ->
                        current.copy(
                            isSyncing = false,
                            activeOperationName = "",
                            lastErrorMessage = error.toUserFacingSyncMessage(),
                        )
                    }
                    if (operation.scheduleRetry) {
                        persistentSyncScheduler.scheduleRetry()
                    }
                }
                if (operation.scheduleRetry) {
                    schedulePendingRetry()
                }
            }
        }
    }

    fun enqueue(
        name: String,
        persistForRetry: Boolean = true,
        block: suspend SessionSyncCoordinator.() -> Unit,
    ) {
        val operation = SyncOperation(name = name, block = block)
        if (persistForRetry) {
            persistentSyncScheduler.scheduleRetry()
        }
        if (!operations.trySend(operation).isSuccess) {
            scope.launch { operations.send(operation) }
        }
    }

    fun retryPendingSoon() {
        persistentSyncScheduler.scheduleSoon()
        schedulePendingRetry()
    }

    fun syncSessionSoon() {
        persistentSyncScheduler.scheduleSoon()
        enqueue("syncSession") {
            syncAfterAuth()
        }
    }

    private fun schedulePendingRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            for (delayMs in RetryDelaysMs) {
                if (delayMs > 0) delay(delayMs)
                operations.send(
                    SyncOperation(
                        name = "pushPendingChanges",
                        scheduleRetry = false,
                    ) {
                        pushPendingChanges()
                    },
                )
            }
        }
    }

    private fun Throwable.toUserFacingSyncMessage(): String {
        val root = generateSequence(this) { error -> error.cause }.last()
        return root.localizedMessage
            ?.takeIf { message -> message.isNotBlank() }
            ?: root.javaClass.simpleName
    }

    private data class SyncOperation(
        val name: String,
        val scheduleRetry: Boolean = true,
        val block: suspend SessionSyncCoordinator.() -> Unit,
    )

    private companion object {
        const val Tag = "CYLSyncQueue"
        val RetryDelaysMs = listOf(0L, 1_000L, 5_000L, 15_000L)
    }
}
