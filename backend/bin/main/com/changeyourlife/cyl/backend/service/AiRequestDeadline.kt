package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.config.AiTimeoutConfig
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class AiHttpRequestBudget(
    val timeout: Duration,
    val isDeadlineBound: Boolean,
    val deadline: AiRequestDeadline? = null,
)

internal class AiRequestDeadline private constructor(
    private val deadlineNanos: Long,
    private val finalizationReserveMs: Long,
    private val nanoTime: () -> Long,
) {
    fun requestBudget(provider: String, providerLimitMs: Long): AiHttpRequestBudget {
        require(providerLimitMs > 0L) { "Provider request timeout must be positive." }
        val availableMs = availableRemoteWorkMillis()
        if (availableMs <= 0L) {
            throw AiDeadlineExceededException(
                "AI job deadline exhausted before the $provider request could start.",
            )
        }
        return AiHttpRequestBudget(
            timeout = Duration.ofMillis(minOf(providerLimitMs, availableMs)),
            isDeadlineBound = availableMs <= providerLimitMs,
            deadline = this,
        )
    }

    fun requestTimeout(provider: String, providerLimitMs: Long): Duration =
        requestBudget(provider, providerLimitMs).timeout

    fun requireRetryDelay(provider: String, delayMs: Long) {
        require(delayMs >= 0L) { "AI retry delay cannot be negative." }
        if (delayMs == 0L) return
        if (delayMs >= availableRemoteWorkMillis()) {
            throw AiDeadlineExceededException(
                "AI job deadline cannot fit another $provider retry.",
            )
        }
    }

    internal fun availableRemoteWorkMillis(): Long =
        (remainingMillis() - finalizationReserveMs).coerceAtLeast(0L)

    private fun remainingMillis(): Long =
        ((deadlineNanos - nanoTime()).coerceAtLeast(0L) / NanosPerMillisecond)

    companion object {
        private const val NanosPerMillisecond = 1_000_000L

        fun start(
            config: AiTimeoutConfig,
            nanoTime: () -> Long = System::nanoTime,
        ): AiRequestDeadline {
            val startedAt = nanoTime()
            val jobBudgetNanos = Math.multiplyExact(config.jobDeadlineMs, NanosPerMillisecond)
            return AiRequestDeadline(
                deadlineNanos = startedAt + jobBudgetNanos,
                finalizationReserveMs = config.finalizationReserveMs,
                nanoTime = nanoTime,
            )
        }
    }
}

internal class AiDeadlineExceededException(message: String) : Exception(message)

internal fun <T> HttpClient.sendWithinAiBudget(
    request: HttpRequest,
    bodyHandler: HttpResponse.BodyHandler<T>,
    budget: AiHttpRequestBudget,
    provider: String,
): HttpResponse<T> {
    val future = sendAsync(request, bodyHandler)
    return future.awaitWithinAiBudget(budget, provider)
}

internal fun <T> Future<HttpResponse<T>>.awaitWithinAiBudget(
    budget: AiHttpRequestBudget,
    provider: String,
): HttpResponse<T> = try {
    get(budget.timeout.toMillis(), TimeUnit.MILLISECONDS)
} catch (error: TimeoutException) {
    cancel(true)
    throw budget.timeoutFailure(provider)
} catch (error: ExecutionException) {
    val cause = error.cause ?: error
    if (cause is HttpTimeoutException && budget.isExpiredDeadlineTimeout(cause)) {
        throw AiDeadlineExceededException(
            "AI job deadline exhausted while waiting for the $provider response.",
        )
    }
    when (cause) {
        is Exception -> throw cause
        is Error -> throw cause
        else -> throw error
    }
} catch (error: InterruptedException) {
    cancel(true)
    Thread.currentThread().interrupt()
    throw error
}

private fun AiHttpRequestBudget.timeoutFailure(provider: String): Exception =
    if (isDeadlineBound) {
        AiDeadlineExceededException(
            "AI job deadline exhausted while waiting for the $provider response.",
        )
    } else {
        HttpTimeoutException(
            "$provider request exceeded its ${timeout.toMillis()} ms timeout.",
        )
    }

private fun AiHttpRequestBudget.isExpiredDeadlineTimeout(error: HttpTimeoutException): Boolean {
    if (!isDeadlineBound) return false
    if (error !is HttpConnectTimeoutException) return true
    return deadline?.availableRemoteWorkMillis() == 0L
}

internal fun Throwable.rethrowIfAiWorkMustStop() {
    rethrowIfAiExecutionCancelled()
    if (this is AiDeadlineExceededException) throw this
}

internal fun Throwable.rethrowIfAiExecutionCancelled() {
    when (this) {
        is InterruptedException -> {
            Thread.currentThread().interrupt()
            throw this
        }
        is CancellationException -> throw this
    }
}
