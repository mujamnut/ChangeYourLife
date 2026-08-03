package com.changeyourlife.cyl.backend.service

import io.ktor.util.logging.Logger
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ContentAssetCleanupScheduler(
    private val service: ContentAssetService,
    intervalMillis: Long,
    private val logger: Logger,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "content-asset-cleanup").apply { isDaemon = true }
    }
    private val interval = intervalMillis.coerceAtLeast(60_000L)

    fun start() {
        executor.scheduleWithFixedDelay(
            { scope.launch { runCleanup() } },
            interval,
            interval,
            TimeUnit.MILLISECONDS,
        )
    }

    private suspend fun runCleanup() {
        runCatching { service.cleanupOrphans() }
            .onSuccess { summary ->
                if (summary.orphanedRecords > 0 || summary.pendingStorageDeletes > 0) {
                    logger.info(
                        "Content asset cleanup: orphaned={}, pendingDeletes={}, deleted={}, failures={}",
                        summary.orphanedRecords,
                        summary.pendingStorageDeletes,
                        summary.deletedObjects,
                        summary.failures,
                    )
                }
            }
            .onFailure { failure -> logger.warn("Content asset cleanup failed", failure) }
    }

    override fun close() {
        executor.shutdownNow()
        scope.cancel()
    }
}
