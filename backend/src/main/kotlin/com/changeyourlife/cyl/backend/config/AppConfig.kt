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
    val openAiApiKey: String?,
    val openAiModel: String,
    val openAiVisionModels: List<String>,
    val glmApiKey: String?,
    val geminiApiKey: String?,
    val openRouterApiKey: String?,
    val openRouterModel: String,
    val openRouterVisionModels: List<String>,
) {
    companion object {
        private const val DefaultOpenAiModel = "gpt-4o-mini"
        private val DefaultOpenAiVisionModels = listOf("gpt-4o-mini")
        private const val DefaultOpenRouterModel = "openai/gpt-oss-20b:free"
        private val DefaultOpenRouterVisionModels = listOf(
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-3-4b-it:free",
            "google/gemini-2.0-flash-exp:free",
        )

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            return AppConfig(
                database = DatabaseConfig.fromEnvironment(environment),
                jwt = JwtConfig.fromEnvironment(environment),
                email = EmailConfig.fromEnvironment(environment),
                openAiApiKey = loadApiKey(
                    environment = environment,
                    envNames = listOf("OPENAI_API_KEY"),
                    propNames = listOf("openai.api.key", "OPENAI_API_KEY"),
                ),
                openAiModel = loadSetting(
                    environment = environment,
                    envNames = listOf("OPENAI_MODEL"),
                    propNames = listOf("openai.model", "OPENAI_MODEL"),
                ) ?: DefaultOpenAiModel,
                openAiVisionModels = loadSetting(
                    environment = environment,
                    envNames = listOf("OPENAI_VISION_MODELS", "OPENAI_VISION_MODEL"),
                    propNames = listOf(
                        "openai.vision.models",
                        "OPENAI_VISION_MODELS",
                        "openai.vision.model",
                        "OPENAI_VISION_MODEL",
                    ),
                )?.toModelList()
                    ?.takeIf { models -> models.isNotEmpty() }
                    ?: DefaultOpenAiVisionModels,
                glmApiKey = loadApiKey(
                    environment = environment,
                    envNames = listOf("GLM_API_KEY"),
                    propNames = listOf("glm.api.key", "GLM_API_KEY"),
                ),
                geminiApiKey = loadApiKey(
                    environment = environment,
                    envNames = listOf("GEMINI_API_KEY"),
                    propNames = listOf("gemini.api.key", "GEMINI_API_KEY"),
                ),
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
            )
        }

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
