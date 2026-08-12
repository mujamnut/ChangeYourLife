package com.changeyourlife.cyl.backend.config

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Properties

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val email: EmailConfig,
    val lmStudioBaseUrl: String?,
    val lmStudioApiKey: String?,
    val lmStudioModel: String,
    val lmStudioVisionModels: List<String>,
    val openRouterApiKey: String?,
    val openRouterModel: String,
    val openRouterVisionModels: List<String>,
    val webSearch: WebSearchConfig,
    val aiTimeouts: AiTimeoutConfig = AiTimeoutConfig(),
    val voiceNotes: VoiceNoteConfig = VoiceNoteConfig(),
    val contentAssets: ContentAssetConfig = ContentAssetConfig(),
) {
    companion object {
        private const val DefaultLmStudioModel = "qwen/qwen3.5-9b"
        private val DefaultLmStudioVisionModels = listOf("qwen/qwen3.5-9b")
        private const val DefaultOpenRouterModel = "openai/gpt-oss-20b:free"
        private val DefaultOpenRouterVisionModels = listOf(
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-3-4b-it:free",
        )

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            val r2 = loadR2Config(environment)
            return AppConfig(
                database = DatabaseConfig.fromEnvironment(environment),
                jwt = JwtConfig.fromEnvironment(environment),
                email = EmailConfig.fromEnvironment(environment),
                lmStudioBaseUrl = loadSetting(
                    environment = environment,
                    envNames = listOf("LMSTUDIO_BASE_URL", "LM_STUDIO_BASE_URL"),
                    propNames = listOf("lmstudio.base.url", "LMSTUDIO_BASE_URL", "LM_STUDIO_BASE_URL"),
                ),
                lmStudioApiKey = loadApiKey(
                    environment = environment,
                    envNames = listOf("LMSTUDIO_API_KEY", "LM_STUDIO_API_KEY"),
                    propNames = listOf("lmstudio.api.key", "LMSTUDIO_API_KEY", "LM_STUDIO_API_KEY"),
                ),
                lmStudioModel = loadSetting(
                    environment = environment,
                    envNames = listOf("LMSTUDIO_MODEL", "LM_STUDIO_MODEL"),
                    propNames = listOf("lmstudio.model", "LMSTUDIO_MODEL", "LM_STUDIO_MODEL"),
                ) ?: DefaultLmStudioModel,
                lmStudioVisionModels = loadSetting(
                    environment = environment,
                    envNames = listOf("LMSTUDIO_VISION_MODELS", "LMSTUDIO_VISION_MODEL", "LM_STUDIO_VISION_MODEL"),
                    propNames = listOf(
                        "lmstudio.vision.models",
                        "LMSTUDIO_VISION_MODELS",
                        "lmstudio.vision.model",
                        "LMSTUDIO_VISION_MODEL",
                        "LM_STUDIO_VISION_MODEL",
                    ),
                )?.toModelList()
                    ?.takeIf { models -> models.isNotEmpty() }
                    ?: DefaultLmStudioVisionModels,
                openRouterApiKey = loadApiKey(
                    environment = environment,
                    envNames = listOf("OPENROUTER_API_KEY"),
                    propNames = listOf("openrouter.api.key", "OPENROUTER_API_KEY"),
                ),
                openRouterModel = loadSetting(
                    environment = environment,
                    envNames = listOf("OPENROUTER_MODEL"),
                    propNames = listOf("openrouter.model", "OPENROUTER_MODEL"),
                ) ?: DefaultOpenRouterModel,
                openRouterVisionModels = loadSetting(
                    environment = environment,
                    envNames = listOf("OPENROUTER_VISION_MODELS", "OPENROUTER_VISION_MODEL", "AI_VISION_MODEL"),
                    propNames = listOf(
                        "openrouter.vision.models",
                        "OPENROUTER_VISION_MODELS",
                        "openrouter.vision.model",
                        "OPENROUTER_VISION_MODEL",
                        "AI_VISION_MODEL",
                    ),
                )?.toModelList()
                    ?.takeIf { models -> models.isNotEmpty() }
                    ?: DefaultOpenRouterVisionModels,
                webSearch = WebSearchConfig(
                    enabled = loadSetting(
                        environment = environment,
                        envNames = listOf("WEB_SEARCH_ENABLED", "AI_WEB_SEARCH_ENABLED"),
                        propNames = listOf("web.search.enabled", "WEB_SEARCH_ENABLED", "AI_WEB_SEARCH_ENABLED"),
                    )?.toBooleanStrictOrNull() ?: true,
                    jinaApiKey = loadApiKey(
                        environment = environment,
                        envNames = listOf("JINA_API_KEY"),
                        propNames = listOf("jina.api.key", "JINA_API_KEY"),
                    ),
                    exaApiKey = loadApiKey(
                        environment = environment,
                        envNames = listOf("EXA_API_KEY"),
                        propNames = listOf("exa.api.key", "EXA_API_KEY"),
                    ),
                    tavilyApiKey = loadApiKey(
                        environment = environment,
                        envNames = listOf("TAVILY_API_KEY"),
                        propNames = listOf("tavily.api.key", "TAVILY_API_KEY"),
                    ),
                    timeoutMs = loadSetting(
                        environment = environment,
                        envNames = listOf("WEB_SEARCH_TIMEOUT_MS"),
                        propNames = listOf("web.search.timeout.ms", "WEB_SEARCH_TIMEOUT_MS"),
                    )?.toLongOrNull()?.coerceIn(2_000L, 30_000L) ?: 12_000L,
                    cacheTtlSeconds = loadSetting(
                        environment = environment,
                        envNames = listOf("WEB_SEARCH_CACHE_TTL_SECONDS"),
                        propNames = listOf("web.search.cache.ttl.seconds", "WEB_SEARCH_CACHE_TTL_SECONDS"),
                    )?.toLongOrNull()?.coerceIn(60L, 86_400L) ?: 900L,
                ),
                aiTimeouts = loadAiTimeoutConfig(environment),
                voiceNotes = loadVoiceNoteConfig(environment, r2),
                contentAssets = loadContentAssetConfig(environment, r2),
            )
        }

        private fun loadAiTimeoutConfig(environment: Map<String, String>): AiTimeoutConfig {
            val jobDeadlineMs = loadSetting(
                environment = environment,
                envNames = listOf("AI_JOB_DEADLINE_MS"),
                propNames = listOf("ai.job.deadline.ms", "AI_JOB_DEADLINE_MS"),
            )?.toLongOrNull()?.coerceIn(30_000L, 600_000L)
                ?: AiTimeoutConfig.DefaultJobDeadlineMs
            val finalizationReserveMs = loadSetting(
                environment = environment,
                envNames = listOf("AI_FINALIZATION_RESERVE_MS"),
                propNames = listOf("ai.finalization.reserve.ms", "AI_FINALIZATION_RESERVE_MS"),
            )?.toLongOrNull()
                ?.coerceIn(1_000L, minOf(60_000L, jobDeadlineMs - 1_000L))
                ?: AiTimeoutConfig.DefaultFinalizationReserveMs
                    .coerceAtMost(jobDeadlineMs - 1_000L)
            return AiTimeoutConfig(
                jobDeadlineMs = jobDeadlineMs,
                connectTimeoutMs = loadSetting(
                    environment = environment,
                    envNames = listOf("AI_CONNECT_TIMEOUT_MS"),
                    propNames = listOf("ai.connect.timeout.ms", "AI_CONNECT_TIMEOUT_MS"),
                )?.toLongOrNull()?.coerceIn(1_000L, 30_000L)
                    ?: AiTimeoutConfig.DefaultConnectTimeoutMs,
                lmStudioRequestTimeoutMs = loadSetting(
                    environment = environment,
                    envNames = listOf("LMSTUDIO_REQUEST_TIMEOUT_MS", "LM_STUDIO_REQUEST_TIMEOUT_MS"),
                    propNames = listOf(
                        "lmstudio.request.timeout.ms",
                        "LMSTUDIO_REQUEST_TIMEOUT_MS",
                        "LM_STUDIO_REQUEST_TIMEOUT_MS",
                    ),
                )?.toLongOrNull()?.coerceIn(5_000L, jobDeadlineMs)
                    ?: AiTimeoutConfig.DefaultLmStudioRequestTimeoutMs.coerceAtMost(jobDeadlineMs),
                openRouterRequestTimeoutMs = loadSetting(
                    environment = environment,
                    envNames = listOf("OPENROUTER_REQUEST_TIMEOUT_MS"),
                    propNames = listOf("openrouter.request.timeout.ms", "OPENROUTER_REQUEST_TIMEOUT_MS"),
                )?.toLongOrNull()?.coerceIn(5_000L, jobDeadlineMs)
                    ?: AiTimeoutConfig.DefaultOpenRouterRequestTimeoutMs.coerceAtMost(jobDeadlineMs),
                finalizationReserveMs = finalizationReserveMs,
            )
        }

        private fun loadVoiceNoteConfig(
            environment: Map<String, String>,
            r2: R2Config,
        ): VoiceNoteConfig {
            return VoiceNoteConfig(
                enabled = loadSetting(
                    environment = environment,
                    envNames = listOf("VOICE_NOTE_ENABLED"),
                    propNames = listOf("voice.note.enabled", "VOICE_NOTE_ENABLED"),
                )?.toBooleanStrictOrNull() ?: false,
                maxBytes = loadSetting(
                    environment = environment,
                    envNames = listOf("VOICE_MAX_BYTES"),
                    propNames = listOf("voice.max.bytes", "VOICE_MAX_BYTES"),
                )?.toLongOrNull()?.coerceIn(1_048_576L, 104_857_600L) ?: 10_485_760L,
                maxDurationMs = loadSetting(
                    environment = environment,
                    envNames = listOf("VOICE_MAX_DURATION_SECONDS"),
                    propNames = listOf("voice.max.duration.seconds", "VOICE_MAX_DURATION_SECONDS"),
                )?.toLongOrNull()?.coerceIn(5L, 1_800L)?.times(1_000L) ?: 300_000L,
                uploadUrlTtlMillis = loadSetting(
                    environment = environment,
                    envNames = listOf("R2_SIGNED_URL_TTL_SECONDS"),
                    propNames = listOf("r2.signed.url.ttl.seconds", "R2_SIGNED_URL_TTL_SECONDS"),
                )?.toLongOrNull()?.coerceIn(30L, 3_600L)?.times(1_000L) ?: 300_000L,
                playbackUrlTtlMillis = loadSetting(
                    environment = environment,
                    envNames = listOf("R2_PLAYBACK_URL_TTL_SECONDS"),
                    propNames = listOf("r2.playback.url.ttl.seconds", "R2_PLAYBACK_URL_TTL_SECONDS"),
                )?.toLongOrNull()?.coerceIn(30L, 3_600L)?.times(1_000L) ?: 300_000L,
                orphanTtlMillis = loadSetting(
                    environment = environment,
                    envNames = listOf("VOICE_ORPHAN_TTL_HOURS"),
                    propNames = listOf("voice.orphan.ttl.hours", "VOICE_ORPHAN_TTL_HOURS"),
                )?.toLongOrNull()?.coerceIn(1L, 168L)?.times(60L * 60L * 1_000L)
                    ?: 24L * 60L * 60L * 1_000L,
                cleanupIntervalMillis = loadSetting(
                    environment = environment,
                    envNames = listOf("VOICE_CLEANUP_INTERVAL_SECONDS"),
                    propNames = listOf("voice.cleanup.interval.seconds", "VOICE_CLEANUP_INTERVAL_SECONDS"),
                )?.toLongOrNull()?.coerceIn(60L, 86_400L)?.times(1_000L) ?: 3_600_000L,
                r2 = r2,
            )
        }

        private fun loadContentAssetConfig(
            environment: Map<String, String>,
            r2: R2Config,
        ): ContentAssetConfig = ContentAssetConfig(
            enabled = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_ENABLED"),
                propNames = listOf("content.asset.enabled", "CONTENT_ASSET_ENABLED"),
            )?.toBooleanStrictOrNull() ?: r2.isConfigured,
            maxImageBytes = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_MAX_IMAGE_BYTES"),
                propNames = listOf("content.asset.max.image.bytes", "CONTENT_ASSET_MAX_IMAGE_BYTES"),
            )?.toLongOrNull()?.coerceIn(1_048_576L, 104_857_600L) ?: 15_728_640L,
            maxPdfBytes = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_MAX_PDF_BYTES"),
                propNames = listOf("content.asset.max.pdf.bytes", "CONTENT_ASSET_MAX_PDF_BYTES"),
            )?.toLongOrNull()?.coerceIn(1_048_576L, 524_288_000L) ?: 52_428_800L,
            maxTextBytes = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_MAX_TEXT_BYTES"),
                propNames = listOf("content.asset.max.text.bytes", "CONTENT_ASSET_MAX_TEXT_BYTES"),
            )?.toLongOrNull()?.coerceIn(65_536L, 10_485_760L) ?: 1_048_576L,
            uploadUrlTtlMillis = loadSetting(
                environment = environment,
                envNames = listOf("R2_SIGNED_URL_TTL_SECONDS"),
                propNames = listOf("r2.signed.url.ttl.seconds", "R2_SIGNED_URL_TTL_SECONDS"),
            )?.toLongOrNull()?.coerceIn(30L, 3_600L)?.times(1_000L) ?: 300_000L,
            downloadUrlTtlMillis = loadSetting(
                environment = environment,
                envNames = listOf("R2_DOWNLOAD_URL_TTL_SECONDS", "R2_PLAYBACK_URL_TTL_SECONDS"),
                propNames = listOf(
                    "r2.download.url.ttl.seconds",
                    "R2_DOWNLOAD_URL_TTL_SECONDS",
                    "r2.playback.url.ttl.seconds",
                    "R2_PLAYBACK_URL_TTL_SECONDS",
                ),
            )?.toLongOrNull()?.coerceIn(30L, 3_600L)?.times(1_000L) ?: 300_000L,
            orphanTtlMillis = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_ORPHAN_TTL_HOURS"),
                propNames = listOf("content.asset.orphan.ttl.hours", "CONTENT_ASSET_ORPHAN_TTL_HOURS"),
            )?.toLongOrNull()?.coerceIn(1L, 168L)?.times(60L * 60L * 1_000L)
                ?: 24L * 60L * 60L * 1_000L,
            cleanupIntervalMillis = loadSetting(
                environment = environment,
                envNames = listOf("CONTENT_ASSET_CLEANUP_INTERVAL_SECONDS"),
                propNames = listOf(
                    "content.asset.cleanup.interval.seconds",
                    "CONTENT_ASSET_CLEANUP_INTERVAL_SECONDS",
                ),
            )?.toLongOrNull()?.coerceIn(60L, 86_400L)?.times(1_000L) ?: 3_600_000L,
            r2 = r2,
        )

        private fun loadR2Config(environment: Map<String, String>): R2Config {
            val accountId = environment.backendSecret("R2_ACCOUNT_ID")
            return R2Config(
                endpoint = environment.backendSecret("R2_ENDPOINT")
                    ?: accountId?.let { value -> "https://$value.r2.cloudflarestorage.com" },
                bucket = environment.backendSecret("R2_BUCKET"),
                accessKeyId = environment.backendSecret("R2_ACCESS_KEY_ID"),
                secretAccessKey = environment.backendSecret("R2_SECRET_ACCESS_KEY"),
            )
        }

        private fun Map<String, String>.backendSecret(name: String): String? =
            get(name)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")?.takeIf(String::isNotBlank)

        private fun String.toModelList(): List<String> {
            return split(',', ';', '|')
                .map { model -> model.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { model -> model.isNotBlank() }
                .distinct()
        }

        private fun loadApiKey(
            environment: Map<String, String>,
            envNames: List<String>,
            propNames: List<String>,
        ): String? {
            for (name in envNames) {
                val value = environment[name]
                if (!value.isNullOrBlank()) {
                    return value.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            val filesToTry = listOf(
                File("local.properties"),
                File("../local.properties"),
                File("backend/local.properties"),
            )
            for (file in filesToTry) {
                if (file.exists()) {
                    try {
                        val properties = Properties()
                        file.inputStream().use { properties.load(it) }
                        for (prop in propNames) {
                            val value = properties.getProperty(prop)
                            if (!value.isNullOrBlank()) {
                                return value.trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            return null
        }

        private fun loadSetting(
            environment: Map<String, String>,
            envNames: List<String>,
            propNames: List<String>,
        ): String? {
            for (name in envNames) {
                val value = environment[name]
                if (!value.isNullOrBlank()) {
                    return value.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            val filesToTry = listOf(
                File("local.properties"),
                File("../local.properties"),
                File("backend/local.properties"),
            )
            for (file in filesToTry) {
                if (file.exists()) {
                    try {
                        val properties = Properties()
                        file.inputStream().use { properties.load(it) }
                        for (prop in propNames) {
                            val value = properties.getProperty(prop)
                            if (!value.isNullOrBlank()) {
                                return value.trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            return null
        }
    }
}

data class VoiceNoteConfig(
    val enabled: Boolean = false,
    val maxBytes: Long = 10_485_760L,
    val maxDurationMs: Long = 300_000L,
    val uploadUrlTtlMillis: Long = 300_000L,
    val playbackUrlTtlMillis: Long = 300_000L,
    val orphanTtlMillis: Long = 86_400_000L,
    val cleanupIntervalMillis: Long = 3_600_000L,
    val r2: R2Config = R2Config(),
)

data class ContentAssetConfig(
    val enabled: Boolean = false,
    val maxImageBytes: Long = 15_728_640L,
    val maxPdfBytes: Long = 52_428_800L,
    val maxTextBytes: Long = 1_048_576L,
    val uploadUrlTtlMillis: Long = 300_000L,
    val downloadUrlTtlMillis: Long = 300_000L,
    val orphanTtlMillis: Long = 86_400_000L,
    val cleanupIntervalMillis: Long = 3_600_000L,
    val r2: R2Config = R2Config(),
)

data class R2Config(
    val endpoint: String? = null,
    val bucket: String? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
) {
    val isConfigured: Boolean = listOf(endpoint, bucket, accessKeyId, secretAccessKey)
        .all { value -> !value.isNullOrBlank() }
}

data class WebSearchConfig(
    val enabled: Boolean = true,
    val jinaApiKey: String? = null,
    val exaApiKey: String? = null,
    val tavilyApiKey: String? = null,
    val timeoutMs: Long = 12_000L,
    val cacheTtlSeconds: Long = 900L,
)

data class AiTimeoutConfig(
    val jobDeadlineMs: Long = DefaultJobDeadlineMs,
    val connectTimeoutMs: Long = DefaultConnectTimeoutMs,
    val lmStudioRequestTimeoutMs: Long = DefaultLmStudioRequestTimeoutMs,
    val openRouterRequestTimeoutMs: Long = DefaultOpenRouterRequestTimeoutMs,
    val finalizationReserveMs: Long = DefaultFinalizationReserveMs,
) {
    init {
        require(jobDeadlineMs > 0L) { "AI job deadline must be positive." }
        require(connectTimeoutMs > 0L) { "AI connect timeout must be positive." }
        require(lmStudioRequestTimeoutMs > 0L) { "LM Studio request timeout must be positive." }
        require(openRouterRequestTimeoutMs > 0L) { "OpenRouter request timeout must be positive." }
        require(finalizationReserveMs >= 0L) { "AI finalization reserve cannot be negative." }
        require(finalizationReserveMs < jobDeadlineMs) {
            "AI finalization reserve must be shorter than the job deadline."
        }
    }

    companion object {
        const val DefaultJobDeadlineMs = 180_000L
        const val DefaultConnectTimeoutMs = 5_000L
        const val DefaultLmStudioRequestTimeoutMs = 90_000L
        const val DefaultOpenRouterRequestTimeoutMs = 60_000L
        const val DefaultFinalizationReserveMs = 10_000L
    }
}

data class EmailConfig(
    val resendApiKey: String?,
    val from: String?,
    val replyTo: String?,
    val appName: String,
) {
    val isConfigured: Boolean = !resendApiKey.isNullOrBlank() && !from.isNullOrBlank()

    companion object {
        fun fromEnvironment(environment: Map<String, String>): EmailConfig {
            return EmailConfig(
                resendApiKey = loadEmailSetting(
                    environment = environment,
                    envNames = listOf("RESEND_API_KEY"),
                    propNames = listOf("RESEND_API_KEY", "resend.api.key"),
                ),
                from = loadEmailSetting(
                    environment = environment,
                    envNames = listOf("EMAIL_FROM"),
                    propNames = listOf("EMAIL_FROM", "email.from"),
                ),
                replyTo = loadEmailSetting(
                    environment = environment,
                    envNames = listOf("EMAIL_REPLY_TO"),
                    propNames = listOf("EMAIL_REPLY_TO", "email.replyTo"),
                ),
                appName = loadEmailSetting(
                    environment = environment,
                    envNames = listOf("EMAIL_APP_NAME"),
                    propNames = listOf("EMAIL_APP_NAME", "email.appName"),
                ) ?: "ChangeYourLife",
            )
        }

        private fun loadEmailSetting(
            environment: Map<String, String>,
            envNames: List<String>,
            propNames: List<String>,
        ): String? {
            for (name in envNames) {
                val value = environment[name]
                if (!value.isNullOrBlank()) {
                    return value.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            val filesToTry = listOf(
                File("local.properties"),
                File("../local.properties"),
                File("backend/local.properties"),
            )
            for (file in filesToTry) {
                if (file.exists()) {
                    try {
                        val properties = Properties()
                        file.inputStream().use { properties.load(it) }
                        for (prop in propNames) {
                            val value = properties.getProperty(prop)
                            if (!value.isNullOrBlank()) {
                                return value.trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore local config read failures.
                    }
                }
            }
            return null
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String?,
    val username: String?,
    val password: String?,
    val maxPoolSize: Int,
) {
    val isConfigured: Boolean = !jdbcUrl.isNullOrBlank()

    companion object {
        fun fromEnvironment(environment: Map<String, String>): DatabaseConfig {
            val rawDatabaseUrl = loadDatabaseSetting(
                environment = environment,
                envNames = listOf("DATABASE_URL"),
                propNames = listOf("DATABASE_URL", "database.url"),
            )
            val parsed = parseDatabaseUrl(rawDatabaseUrl)
            return DatabaseConfig(
                jdbcUrl = parsed.jdbcUrl,
                username = loadDatabaseSetting(
                    environment = environment,
                    envNames = listOf("DATABASE_USER"),
                    propNames = listOf("DATABASE_USER", "database.user"),
                ) ?: parsed.username,
                password = loadDatabaseSetting(
                    environment = environment,
                    envNames = listOf("DATABASE_PASSWORD"),
                    propNames = listOf("DATABASE_PASSWORD", "database.password"),
                ) ?: parsed.password,
                maxPoolSize = loadDatabaseSetting(
                    environment = environment,
                    envNames = listOf("DATABASE_MAX_POOL_SIZE"),
                    propNames = listOf("DATABASE_MAX_POOL_SIZE", "database.maxPoolSize"),
                )?.toIntOrNull() ?: 5,
            )
        }

        private fun loadDatabaseSetting(
            environment: Map<String, String>,
            envNames: List<String>,
            propNames: List<String>,
        ): String? {
            for (name in envNames) {
                val value = environment[name]
                if (!value.isNullOrBlank()) {
                    return value.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            val filesToTry = listOf(
                File("local.properties"),
                File("../local.properties"),
                File("backend/local.properties"),
            )
            for (file in filesToTry) {
                if (file.exists()) {
                    try {
                        val properties = Properties()
                        file.inputStream().use { properties.load(it) }
                        for (prop in propNames) {
                            val value = properties.getProperty(prop)
                            if (!value.isNullOrBlank()) {
                                return value.trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            return null
        }

        private fun parseDatabaseUrl(rawUrl: String?): ParsedDatabaseUrl {
            if (rawUrl.isNullOrBlank()) {
                return ParsedDatabaseUrl(null, null, null)
            }

            if (rawUrl.startsWith("jdbc:postgresql://")) {
                return ParsedDatabaseUrl(rawUrl, null, null)
            }

            val uri = URI(rawUrl)
            val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
            val username = userInfo.getOrNull(0)?.decodeUrl()
            val password = userInfo.getOrNull(1)?.decodeUrl()
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            val jdbcUrl = "jdbc:postgresql://${uri.host}$port${uri.path}$query"

            return ParsedDatabaseUrl(jdbcUrl, username, password)
        }

        private fun String.decodeUrl(): String {
            return URLDecoder.decode(this, StandardCharsets.UTF_8)
        }
    }
}

private data class ParsedDatabaseUrl(
    val jdbcUrl: String?,
    val username: String?,
    val password: String?,
)

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
    val expiresInMillis: Long,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String>): JwtConfig {
            return JwtConfig(
                issuer = loadJwtSetting(
                    environment = environment,
                    envNames = listOf("JWT_ISSUER"),
                    propNames = listOf("JWT_ISSUER", "jwt.issuer"),
                ) ?: "cyl-backend",
                audience = loadJwtSetting(
                    environment = environment,
                    envNames = listOf("JWT_AUDIENCE"),
                    propNames = listOf("JWT_AUDIENCE", "jwt.audience"),
                ) ?: "cyl-android",
                realm = loadJwtSetting(
                    environment = environment,
                    envNames = listOf("JWT_REALM"),
                    propNames = listOf("JWT_REALM", "jwt.realm"),
                ) ?: "cyl",
                secret = loadJwtSetting(
                    environment = environment,
                    envNames = listOf("JWT_SECRET"),
                    propNames = listOf("JWT_SECRET", "jwt.secret"),
                ) ?: "dev-only-change-me",
                expiresInMillis = loadJwtSetting(
                    environment = environment,
                    envNames = listOf("JWT_EXPIRES_IN_MILLIS"),
                    propNames = listOf("JWT_EXPIRES_IN_MILLIS", "jwt.expiresInMillis"),
                )?.toLongOrNull()
                    ?: 7L * 24L * 60L * 60L * 1_000L,
            )
        }

        private fun loadJwtSetting(
            environment: Map<String, String>,
            envNames: List<String>,
            propNames: List<String>,
        ): String? {
            for (name in envNames) {
                val value = environment[name]
                if (!value.isNullOrBlank()) {
                    return value.trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
            val filesToTry = listOf(
                File("local.properties"),
                File("../local.properties"),
                File("backend/local.properties"),
            )
            for (file in filesToTry) {
                if (file.exists()) {
                    try {
                        val properties = Properties()
                        file.inputStream().use { properties.load(it) }
                        for (prop in propNames) {
                            val value = properties.getProperty(prop)
                            if (!value.isNullOrBlank()) {
                                return value.trim().removeSurrounding("\"").removeSurrounding("'")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            return null
        }
    }
}
