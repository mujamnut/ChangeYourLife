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
    private val debouncedOperations = mutableMapOf<String, Job>()
    private var retryJob: Job? = null
    private var foregroundRefreshJob: Job? = null
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

    fun enqueuePendingPushDebounced() {
        enqueueDebounced(
            name = "pushPendingChangesDebounced",
            delayMs = RemoteMutationDebounceMs,
        ) {
            pushPendingChanges()
        }
    }

    fun retryPendingSoon() {
        persistentSyncScheduler.schedulePendingSoon()
        schedulePendingRetry()
    }

    fun syncSessionSoon() {
        persistentSyncScheduler.scheduleSessionSoon()
        enqueue("syncSession") {
            syncAfterAuth()
        }
    }

    fun ensurePeriodicPullScheduled() {
        persistentSyncScheduler.schedulePeriodicPull()
    }

    fun startForegroundRefreshLoop() {
        if (foregroundRefreshJob?.isActive == true) return
        enqueue(
            name = "foregroundSessionSync",
            persistForRetry = false,
        ) {
            syncAfterAuth()
        }
        foregroundRefreshJob = scope.launch {
            while (true) {
                delay(ForegroundRefreshIntervalMs)
                operations.send(
                    SyncOperation(
                        name = "foregroundSessionSync",
                        scheduleRetry = false,
                    ) {
                        syncAfterAuth()
                    },
                )
            }
        }
    }

    fun stopForegroundRefreshLoop() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
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

    private fun enqueueDebounced(
        name: String,
        delayMs: Long,
        persistForRetry: Boolean = true,
        block: suspend SessionSyncCoordinator.() -> Unit,
    ) {
        if (persistForRetry) {
            persistentSyncScheduler.scheduleRetry()
        }
        val operation = SyncOperation(name = name, scheduleRetry = persistForRetry, block = block)
        synchronized(debouncedOperations) {
            debouncedOperations.remove(name)?.cancel()
            val job = scope.launch {
                delay(delayMs.coerceAtLeast(0L))
                synchronized(debouncedOperations) {
                    if (debouncedOperations[name] != coroutineContext[Job]) return@launch
                    debouncedOperations.remove(name)
                }
                operations.send(operation)
            }
            debouncedOperations[name] = job
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
        const val ForegroundRefreshIntervalMs = 120_000L
        const val RemoteMutationDebounceMs = 4_000L
        val RetryDelaysMs = listOf(0L, 1_000L, 5_000L, 15_000L)
    }
}
