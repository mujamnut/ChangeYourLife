package com.changeyourlife.cyl.domain.repository

enum class AiErrorKind {
    RateLimited,
    ModelUnavailable,
    BackendUnreachable,
    Unauthorized,
    Forbidden,
    BackendUnavailable,
    EmptyResponse,
    ActionFailed,
    ProviderError,
    Unknown,
}

data class AiError(
    val kind: AiErrorKind,
    val userMessage: String,
    val developerMessage: String = userMessage,
    val httpStatus: Int? = null,
    val providerCode: String = "",
    val retryable: Boolean = false,
)

class AiException(
    val aiError: AiError,
    cause: Throwable? = null,
) : RuntimeException(aiError.developerMessage, cause)
