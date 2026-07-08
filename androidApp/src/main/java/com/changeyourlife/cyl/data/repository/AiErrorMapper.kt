package com.changeyourlife.cyl.data.repository

import com.changeyourlife.cyl.core.network.isDnsResolutionFailure
import com.changeyourlife.cyl.domain.repository.AiError
import com.changeyourlife.cyl.domain.repository.AiErrorKind
import com.changeyourlife.cyl.domain.repository.AiException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

internal object AiErrorMapper {
    private val nestedStatusRegex = Regex("""status code\s+(\d{3})""", RegexOption.IGNORE_CASE)
    private val explicitCodeRegex = Regex("""["']?code["']?\s*[:=]\s*["']?([A-Z0-9_ -]{3,})""", RegexOption.IGNORE_CASE)
    private val nullContentRegex = Regex(""""content"\s*:\s*null""", RegexOption.IGNORE_CASE)

    fun fromThrowable(error: Throwable, json: Json): AiException {
        if (error is AiException) return error

        if (error.isDnsResolutionFailure()) {
            return AiException(
                AiError(
                    kind = AiErrorKind.BackendUnreachable,
                    userMessage = "Cannot resolve CYL backend host. Check your internet or DNS, then try again.",
                    developerMessage = error.toString(),
                    retryable = true,
                ),
                error,
            )
        }

        if (error is SocketTimeoutException) {
            return AiException(
                AiError(
                    kind = AiErrorKind.ProviderError,
                    userMessage = "AI is still processing or timed out. Try again, or use a smaller/faster model.",
                    developerMessage = error.toString(),
                    retryable = true,
                ),
                error,
            )
        }

        if (error is IOException) {
            return AiException(
                AiError(
                    kind = AiErrorKind.BackendUnreachable,
                    userMessage = "Cannot reach CYL backend. Check your connection and backend URL, then try again.",
                    developerMessage = error.toString(),
                    retryable = true,
                ),
                error,
            )
        }

        if (error is HttpException) {
            val rawBody = error.response()?.errorBody()?.string().orEmpty()
            return classify(
                httpStatus = error.code(),
                raw = rawBody.ifBlank { error.message() },
                json = json,
                cause = error,
            )
        }

        return classify(
            httpStatus = null,
            raw = error.message.orEmpty(),
            json = json,
            cause = error,
        )
    }

    fun emptyResponse(operation: String): AiException {
        return AiException(
            AiError(
                kind = AiErrorKind.EmptyResponse,
                userMessage = "AI returned an empty response. Please try again.",
                developerMessage = "AI returned an empty response for $operation.",
                retryable = true,
            ),
        )
    }

    private fun classify(
        httpStatus: Int?,
        raw: String,
        json: Json,
        cause: Throwable?,
    ): AiException {
        val parsed = parseErrorPayload(raw = raw, json = json)
        val combined = (listOf(raw) + parsed.parts).joinToString(separator = "\n")
        val lower = combined.lowercase()
        val nestedStatus = parsed.statuses.firstOrNull()
            ?: nestedStatusRegex.find(combined)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val effectiveStatus = when {
            httpStatus != null && httpStatus >= 500 && nestedStatus != null -> nestedStatus
            else -> httpStatus ?: nestedStatus
        }
        val providerCode = parsed.providerCode.ifBlank {
            explicitCodeRegex.find(combined)?.groupValues?.getOrNull(1).orEmpty().trim()
        }

        val aiError = when {
            effectiveStatus == 401 || lower.contains("no active auth session") -> {
                AiError(
                    kind = AiErrorKind.Unauthorized,
                    userMessage = "Your session expired. Please log in again.",
                    developerMessage = combined.ifBlank { "AI request unauthorized." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                )
            }
            effectiveStatus == 403 -> {
                AiError(
                    kind = AiErrorKind.Forbidden,
                    userMessage = "You do not have permission to use AI. Please log in again.",
                    developerMessage = combined.ifBlank { "AI request forbidden." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                )
            }
            effectiveStatus == 429 || lower.contains("rate-limited") || lower.contains("rate limited") -> {
                AiError(
                    kind = AiErrorKind.RateLimited,
                    userMessage = "AI is rate-limited right now. Please wait a moment and try again.",
                    developerMessage = combined.ifBlank { "AI provider returned rate limit." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = true,
                )
            }
            lower.contains("empty response") || lower.contains("empty reply") || nullContentRegex.containsMatchIn(combined) -> {
                AiError(
                    kind = AiErrorKind.EmptyResponse,
                    userMessage = "AI returned an empty response. Please try again.",
                    developerMessage = combined.ifBlank { "AI returned an empty response." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = true,
                )
            }
            isModelUnavailable(effectiveStatus, lower) -> {
                AiError(
                    kind = AiErrorKind.ModelUnavailable,
                    userMessage = "This AI model is temporarily unavailable or overloaded. Try again soon or switch model.",
                    developerMessage = combined.ifBlank { "AI model unavailable." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = true,
                )
            }
            isAiTimeout(effectiveStatus, lower) -> {
                AiError(
                    kind = AiErrorKind.ProviderError,
                    userMessage = "AI is still processing or the tunnel timed out. Try again, or use a smaller/faster LM Studio model.",
                    developerMessage = combined.ifBlank { "AI request timed out." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = true,
                )
            }
            effectiveStatus == 502 || effectiveStatus == 503 || effectiveStatus == 504 -> {
                AiError(
                    kind = AiErrorKind.BackendUnavailable,
                    userMessage = "CYL backend is temporarily unavailable. Please try again shortly.",
                    developerMessage = combined.ifBlank { "CYL backend unavailable." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = true,
                )
            }
            lower.contains("action failed") || lower.contains("failed before it could update") -> {
                AiError(
                    kind = AiErrorKind.ActionFailed,
                    userMessage = "AI understood the request, but the app could not apply the change.",
                    developerMessage = combined.ifBlank { "AI action failed." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = false,
                )
            }
            isProviderError(effectiveStatus, lower) -> {
                AiError(
                    kind = AiErrorKind.ProviderError,
                    userMessage = "The AI provider returned an error. Please try again shortly.",
                    developerMessage = combined.ifBlank { "AI provider error." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = effectiveStatus == null || effectiveStatus >= 500,
                )
            }
            else -> {
                AiError(
                    kind = AiErrorKind.Unknown,
                    userMessage = "AI request failed. Please try again.",
                    developerMessage = combined.ifBlank { "Unknown AI error." },
                    httpStatus = effectiveStatus,
                    providerCode = providerCode,
                    retryable = effectiveStatus == null || effectiveStatus >= 500,
                )
            }
        }

        return AiException(aiError, cause)
    }

    private fun isModelUnavailable(status: Int?, lower: String): Boolean {
        return (status == 503 && lower.contains("model")) ||
            lower.contains("model is currently experiencing high demand") ||
            lower.contains("model is temporarily unavailable") ||
            lower.contains("temporarily unavailable upstream") ||
            lower.contains("status_unavailable") ||
            lower.contains("unavailable") && lower.contains("openrouter") ||
            lower.contains("unavailable") && lower.contains("openinference")
    }

    private fun isProviderError(status: Int?, lower: String): Boolean {
        return (status != null && status >= 500) ||
            lower.contains("provider returned error") ||
            lower.contains("backend ai request failed") ||
            lower.contains("openrouter") ||
            lower.contains("openinference")
    }

    private fun isAiTimeout(status: Int?, lower: String): Boolean {
        return status == 504 ||
            status == 524 ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("request timeout") ||
            lower.contains("gateway timeout") ||
            lower.contains("cloudflare")
    }

    private fun parseErrorPayload(raw: String, json: Json): ParsedAiErrorPayload {
        val parts = mutableListOf<String>()
        val statuses = mutableListOf<Int>()
        val codes = mutableListOf<String>()

        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        if (root != null) {
            collectErrorFields(
                element = root,
                key = null,
                json = json,
                parts = parts,
                statuses = statuses,
                codes = codes,
            )
        }

        statuses += nestedStatusRegex.findAll(raw)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
        codes += explicitCodeRegex.findAll(raw)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.trim() }

        return ParsedAiErrorPayload(
            parts = parts.distinct(),
            statuses = statuses.distinct(),
            providerCode = codes.firstOrNull { code -> code.isNotBlank() }.orEmpty(),
        )
    }

    private fun collectErrorFields(
        element: JsonElement,
        key: String?,
        json: Json,
        parts: MutableList<String>,
        statuses: MutableList<Int>,
        codes: MutableList<String>,
        depth: Int = 0,
    ) {
        if (depth > MaxJsonDepth || element is JsonNull) return

        when (element) {
            is JsonObject -> {
                element.forEach { (childKey, value) ->
                    collectErrorFields(
                        element = value,
                        key = childKey,
                        json = json,
                        parts = parts,
                        statuses = statuses,
                        codes = codes,
                        depth = depth + 1,
                    )
                }
            }
            is JsonArray -> {
                element.forEach { value ->
                    collectErrorFields(
                        element = value,
                        key = key,
                        json = json,
                        parts = parts,
                        statuses = statuses,
                        codes = codes,
                        depth = depth + 1,
                    )
                }
            }
            is JsonPrimitive -> {
                val value = element.contentOrNull ?: element.toString()
                val normalizedKey = key.orEmpty().lowercase()
                if (normalizedKey in ErrorFieldKeys || ErrorFieldKeys.any { field -> normalizedKey.contains(field) }) {
                    parts += value
                    value.toIntOrNull()?.takeIf { status -> status in 100..599 }?.let(statuses::add)
                    if (normalizedKey.contains("code")) codes += value
                    runCatching { json.parseToJsonElement(value) }
                        .getOrNull()
                        ?.let { nested ->
                            collectErrorFields(
                                element = nested,
                                key = key,
                                json = json,
                                parts = parts,
                                statuses = statuses,
                                codes = codes,
                                depth = depth + 1,
                            )
                        }
                }
            }
        }
    }

    private const val MaxJsonDepth = 8

    private val ErrorFieldKeys = setOf(
        "error",
        "message",
        "raw",
        "code",
        "status",
        "statuscode",
        "provider_name",
        "provider",
        "refusal",
    )
}

private data class ParsedAiErrorPayload(
    val parts: List<String>,
    val statuses: List<Int>,
    val providerCode: String,
)
