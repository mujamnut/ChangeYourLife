package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.config.AiTimeoutConfig
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiRequestDeadlineTest {
    @Test
    fun requestTimeoutUsesEachProviderCapWhileTheFullBudgetIsAvailable() {
        val clock = FakeNanoClock()
        val deadline = AiRequestDeadline.start(TimeoutConfig, clock::nanoTime)

        assertEquals(
            Duration.ofMillis(TimeoutConfig.lmStudioRequestTimeoutMs),
            deadline.requestTimeout("lmstudio", TimeoutConfig.lmStudioRequestTimeoutMs),
        )
        assertEquals(
            Duration.ofMillis(TimeoutConfig.openRouterRequestTimeoutMs),
            deadline.requestTimeout("openrouter", TimeoutConfig.openRouterRequestTimeoutMs),
        )
    }

    @Test
    fun providerFallbackUsesTheSharedRemainingBudgetAndStopsAtTheReserve() {
        val clock = FakeNanoClock()
        val deadline = AiRequestDeadline.start(TimeoutConfig, clock::nanoTime)

        clock.advanceMillis(135_000L)
        assertEquals(
            Duration.ofMillis(35_000L),
            deadline.requestTimeout("lmstudio", TimeoutConfig.lmStudioRequestTimeoutMs),
        )
        assertEquals(
            Duration.ofMillis(35_000L),
            deadline.requestTimeout("openrouter", TimeoutConfig.openRouterRequestTimeoutMs),
        )

        clock.advanceMillis(34_999L)
        assertEquals(
            Duration.ofMillis(1L),
            deadline.requestTimeout("openrouter", TimeoutConfig.openRouterRequestTimeoutMs),
        )

        clock.advanceMillis(1L)
        val error = assertFailsWith<AiDeadlineExceededException> {
            deadline.requestTimeout("openrouter", TimeoutConfig.openRouterRequestTimeoutMs)
        }
        assertContains(error.message.orEmpty(), "openrouter")
    }

    @Test
    fun retryDelayMustLeaveTimeForAnotherRemoteAttempt() {
        val clock = FakeNanoClock()
        val deadline = AiRequestDeadline.start(TimeoutConfig, clock::nanoTime)
        clock.advanceMillis(169_100L)

        deadline.requireRetryDelay("lmstudio", 899L)
        val error = assertFailsWith<AiDeadlineExceededException> {
            deadline.requireRetryDelay("lmstudio", 900L)
        }
        assertContains(error.message.orEmpty(), "lmstudio")
    }

    @Test
    fun providerCapTimeoutRemainsEligibleForFallbackAndCancelsTheRequest() {
        val future = AlwaysTimeoutFuture<HttpResponse<String>>()

        assertFailsWith<HttpTimeoutException> {
            future.awaitWithinAiBudget(
                budget = AiHttpRequestBudget(
                    timeout = Duration.ofMillis(90_000L),
                    isDeadlineBound = false,
                ),
                provider = "lmstudio",
            )
        }

        assertTrue(future.isCancelled)
    }

    @Test
    fun connectTimeoutOnlyBecomesDeadlineFailureWhenTheSharedBudgetIsExpired() {
        val clock = FakeNanoClock()
        val deadline = AiRequestDeadline.start(TimeoutConfig, clock::nanoTime)
        clock.advanceMillis(135_000L)
        val budget = deadline.requestBudget("lmstudio", TimeoutConfig.lmStudioRequestTimeoutMs)

        assertFailsWith<HttpConnectTimeoutException> {
            FailedFuture<HttpResponse<String>>(HttpConnectTimeoutException("connect timeout"))
                .awaitWithinAiBudget(budget, "lmstudio")
        }

        clock.advanceMillis(35_000L)
        assertFailsWith<AiDeadlineExceededException> {
            FailedFuture<HttpResponse<String>>(HttpConnectTimeoutException("connect timeout"))
                .awaitWithinAiBudget(budget, "lmstudio")
        }
    }

    private class FakeNanoClock {
        private var nowNanos: Long = 1_000_000_000L

        fun nanoTime(): Long = nowNanos

        fun advanceMillis(milliseconds: Long) {
            nowNanos += milliseconds * NanosPerMillisecond
        }
    }

    private class AlwaysTimeoutFuture<T> : Future<T> {
        private var cancelled = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelled = true
            return true
        }

        override fun isCancelled(): Boolean = cancelled

        override fun isDone(): Boolean = false

        override fun get(): T = error("Untimed get must not be used.")

        override fun get(timeout: Long, unit: TimeUnit): T = throw TimeoutException()
    }

    private class FailedFuture<T>(
        private val failure: Exception,
    ) : Future<T> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = true

        override fun get(): T = throw ExecutionException(failure)

        override fun get(timeout: Long, unit: TimeUnit): T = throw ExecutionException(failure)
    }

    private companion object {
        const val NanosPerMillisecond = 1_000_000L
        val TimeoutConfig = AiTimeoutConfig(
            jobDeadlineMs = 180_000L,
            connectTimeoutMs = 5_000L,
            lmStudioRequestTimeoutMs = 90_000L,
            openRouterRequestTimeoutMs = 60_000L,
            finalizationReserveMs = 10_000L,
        )
    }
}
