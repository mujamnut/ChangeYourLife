package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.service.AiService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredActionTransportTest {
    @Test
    fun plannerUsesNativeFunctionCallingBeforeJsonFallbacks() = runBlocking {
        val requestBodies = Collections.synchronizedList(mutableListOf<String>())
        withAiServer { exchange ->
            requestBodies += exchange.requestBody.bufferedReader().use { reader -> reader.readText() }
            exchange.respond(
                status = 200,
                body = toolCallResponse(
                    arguments = """
                        {"reply":"Siap.","actions":[{"type":"CREATE_PAGE","title":"Budget"}]}
                    """.trimIndent(),
                ),
            )
        }.use { endpoint ->
            val result = service(endpoint).chatWithActions(
                messages = listOf(ChatMessage(role = "user", content = "buat page Budget")),
            )

            assertEquals(listOf("CREATE_PAGE"), result.actions.map { action -> action.type })
            assertEquals(1, requestBodies.size)
            val request = Json.parseToJsonElement(requestBodies.single()).jsonObject
            assertTrue(request.containsKey("tools"))
            assertTrue(request.containsKey("tool_choice"))
            assertFalse(request.containsKey("response_format"))
        }
    }

    @Test
    fun plannerFallsBackFromToolToJsonSchemaThenJsonObject() = runBlocking {
        val requestBodies = Collections.synchronizedList(mutableListOf<String>())
        val requestCount = AtomicInteger(0)
        withAiServer { exchange ->
            val body = exchange.requestBody.bufferedReader().use { reader -> reader.readText() }
            requestBodies += body
            when (requestCount.incrementAndGet()) {
                1 -> exchange.respond(400, """{"error":"tools are not supported"}""")
                2 -> exchange.respond(400, """{"error":"json_schema is not supported"}""")
                else -> exchange.respond(
                    200,
                    contentResponse(
                        """{"reply":"Siap.","actions":[{"type":"CREATE_PAGE","title":"Budget"}]}""",
                    ),
                )
            }
        }.use { endpoint ->
            val result = service(endpoint).chatWithActions(
                messages = listOf(ChatMessage(role = "user", content = "buat page Budget")),
            )

            assertEquals(listOf("CREATE_PAGE"), result.actions.map { action -> action.type })
            assertEquals(3, requestBodies.size)
            val requests = requestBodies.map { body ->
                Json.parseToJsonElement(body).jsonObject
            }
            assertTrue(requests[0].containsKey("tools"))
            assertEquals(
                "json_schema",
                requests[1]["response_format"]?.jsonObject?.get("type").toString().trim('"'),
            )
            assertEquals(
                "json_object",
                requests[2]["response_format"]?.jsonObject?.get("type").toString().trim('"'),
            )
        }
    }

    private fun service(endpoint: String): AiService = AiService(
        lmStudioBaseUrl = endpoint,
        lmStudioModel = "test-model",
    )

    private fun withAiServer(
        handler: (HttpExchange) -> Unit,
    ): RunningAiServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions", handler)
        server.start()
        return RunningAiServer(server)
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { output -> output.write(bytes) }
    }

    private fun toolCallResponse(arguments: String): String {
        val escaped = Json.encodeToString(arguments)
        return """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call-1",
                    "type": "function",
                    "function": {
                      "name": "submit_cyl_response",
                      "arguments": $escaped
                    }
                  }]
                }
              }]
            }
        """.trimIndent()
    }

    private fun contentResponse(content: String): String {
        val escaped = Json.encodeToString(content)
        return """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": $escaped
                }
              }]
            }
        """.trimIndent()
    }

    private class RunningAiServer(
        private val server: HttpServer,
    ) : AutoCloseable {
        val endpoint: String
            get() = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }

    private inline fun <T> RunningAiServer.use(block: (String) -> T): T {
        return try {
            block(endpoint)
        } finally {
            close()
        }
    }
}
