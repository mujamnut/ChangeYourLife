package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.domain.repository.AiErrorKind
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AiErrorMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun mapsOpenRouterRateLimitPayloadToRateLimitedError() {
        val error = AiErrorMapper.fromThrowable(
            error = httpException(
                status = 429,
                body = """
                    {
                      "error": {
                        "message": "Provider returned error",
                        "code": 429,
                        "metadata": {
                          "raw": "openai/gpt-oss-120b:free is temporarily rate-limited upstream.",
                          "provider_name": "OpenInference"
                        }
                      }
                    }
                """.trimIndent(),
            ),
            json = json,
        )

        assertEquals(AiErrorKind.RateLimited, error.aiError.kind)
        assertEquals(429, error.aiError.httpStatus)
        assertTrue(error.aiError.retryable)
        assertFalse(error.aiError.userMessage.contains("openai/gpt", ignoreCase = true))
        assertFalse(error.aiError.userMessage.contains("metadata", ignoreCase = true))
    }

    @Test
    fun mapsNestedProvider503ToModelUnavailableError() {
        val error = AiErrorMapper.fromThrowable(
            error = httpException(
                status = 500,
                body = """
                    {
                      "error": "Backend AI request failed with status code 503. Response: {\"error\":{\"code\":503,\"message\":\"This model is currently experiencing high demand. Please try again later.\"}}"
                    }
                """.trimIndent(),
            ),
            json = json,
        )

        assertEquals(AiErrorKind.ModelUnavailable, error.aiError.kind)
        assertEquals(503, error.aiError.httpStatus)
        assertTrue(error.aiError.retryable)
        assertFalse(error.aiError.userMessage.contains("Backend AI request failed", ignoreCase = true))
    }

    @Test
    fun mapsUnknownHostToBackendUnreachableError() {
        val error = AiErrorMapper.fromThrowable(
            error = UnknownHostException("changeyourlife.onrender.com"),
            json = json,
        )

        assertEquals(AiErrorKind.BackendUnreachable, error.aiError.kind)
        assertTrue(error.aiError.retryable)
    }

    @Test
    fun mapsUnauthorizedToSessionExpiredError() {
        val error = AiErrorMapper.fromThrowable(
            error = httpException(
                status = 401,
                body = """{"error":{"code":"UNAUTHORIZED","message":"Invalid token"}}""",
            ),
            json = json,
        )

        assertEquals(AiErrorKind.Unauthorized, error.aiError.kind)
        assertEquals("Your session expired. Please log in again.", error.aiError.userMessage)
    }

    @Test
    fun mapsEmptyResponseToEmptyResponseError() {
        val error = AiErrorMapper.emptyResponse("chat")

        assertEquals(AiErrorKind.EmptyResponse, error.aiError.kind)
        assertTrue(error.aiError.retryable)
    }

    @Test
    fun mapsNullAssistantContentPayloadToEmptyResponseError() {
        val error = AiErrorMapper.fromThrowable(
            error = IllegalStateException(
                """
                    Unexpected JSON token at offset 403: Expected string literal but 'null' literal was found.
                    JSON input: {"choices":[{"message":{"role":"assistant","content": null,"refusal":null}}]}
                """.trimIndent(),
            ),
            json = json,
        )

        assertEquals(AiErrorKind.EmptyResponse, error.aiError.kind)
        assertTrue(error.aiError.retryable)
    }

    private fun httpException(status: Int, body: String): HttpException {
        return HttpException(
            Response.error<Unit>(
                status,
                body.toResponseBody("application/json".toMediaType()),
            ),
        )
    }
}
