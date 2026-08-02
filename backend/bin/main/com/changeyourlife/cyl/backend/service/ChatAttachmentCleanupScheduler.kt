package com.changeyourlife.cyl.backend.service

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger

class ChatAttachmentCleanupScheduler(
    private val service: ChatAttachmentService,
    private val intervalMillis: Long,
    private val logger: Logger,
) : Closeable {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                runCleanup()
                delay(intervalMillis)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun runCleanup() {
        try {
            val summary = service.cleanupOrphans()
            if (summary.orphanedRecords > 0 || summary.pendingStorageDeletes > 0 || summary.failures > 0) {
                logger.info(
                    "Voice attachment cleanup: orphaned={}, pendingDeletes={}, deletedObjects={}, failures={}",
                    summary.orphanedRecords,
                    summary.pendingStorageDeletes,
                    summary.deletedObjects,
                    summary.failures,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn("Voice attachment cleanup failed: {}", failure::class.simpleName)
        }
    }
}
