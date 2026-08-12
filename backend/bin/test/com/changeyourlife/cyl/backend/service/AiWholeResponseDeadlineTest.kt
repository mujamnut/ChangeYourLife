package com.changeyourlife.cyl.backend.service

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiWholeResponseDeadlineTest {
    @Test
    fun deadlineCancelsAResponseWhoseBodyStallsAfterHeaders() {
        val releaseBody = CountDownLatch(1)
        val firstBodyByteSent = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/stall") { exchange ->
                exchange.sendResponseHeaders(200, 2L)
                try {
                    exchange.responseBody.write('a'.code)
                    exchange.responseBody.flush()
                    firstBodyByteSent.countDown()
                    releaseBody.await(ServerStallMs, TimeUnit.MILLISECONDS)
                    runCatching {
                        exchange.responseBody.write('b'.code)
                        exchange.responseBody.flush()
                    }
                } finally {
                    exchange.close()
                }
            }
            start()
        }

        try {
            val budget = AiHttpRequestBudget(
                timeout = Duration.ofMillis(RequestDeadlineMs),
                isDeadlineBound = true,
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${server.address.port}/stall"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build()
            val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())

            assertTrue(firstBodyByteSent.await(1L, TimeUnit.SECONDS))

            val elapsedMs = measureTimeMillis {
                assertFailsWith<AiDeadlineExceededException> {
                    future.awaitWithinAiBudget(
                        budget = budget,
                        provider = "test-provider",
                    )
                }
            }
            assertTrue(elapsedMs < ServerStallMs)
        } finally {
            releaseBody.countDown()
            server.stop(0)
        }
    }

    private companion object {
        const val RequestDeadlineMs = 200L
        const val ServerStallMs = 2_000L
    }
}
