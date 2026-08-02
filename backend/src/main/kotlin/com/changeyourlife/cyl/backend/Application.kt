package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.AppConfig
import com.changeyourlife.cyl.backend.data.InMemoryAiActionLogRepository
import com.changeyourlife.cyl.backend.data.InMemoryAiJobRepository
import com.changeyourlife.cyl.backend.data.InMemoryAiSkillSyncRepository
import com.changeyourlife.cyl.backend.data.InMemoryChatSyncRepository
import com.changeyourlife.cyl.backend.data.InMemoryChatAttachmentRepository
import com.changeyourlife.cyl.backend.data.InMemoryContentRepository
import com.changeyourlife.cyl.backend.data.InMemoryUserRepository
import com.changeyourlife.cyl.backend.data.PostgresAiActionLogRepository
import com.changeyourlife.cyl.backend.data.PostgresAiJobRepository
import com.changeyourlife.cyl.backend.data.PostgresAiSkillSyncRepository
import com.changeyourlife.cyl.backend.data.PostgresChatSyncRepository
import com.changeyourlife.cyl.backend.data.PostgresChatAttachmentRepository
import com.changeyourlife.cyl.backend.data.PostgresContentRepository
import com.changeyourlife.cyl.backend.data.PostgresUserRepository
import com.changeyourlife.cyl.backend.domain.AiActionLogRepository
import com.changeyourlife.cyl.backend.domain.AiSkillSyncRepository
import com.changeyourlife.cyl.backend.domain.ChatSyncRepository
import com.changeyourlife.cyl.backend.domain.ChatAttachmentRepository
import com.changeyourlife.cyl.backend.domain.ContentRepository
import com.changeyourlife.cyl.backend.domain.UserRepository
import com.changeyourlife.cyl.backend.plugins.configureAuthentication
import com.changeyourlife.cyl.backend.plugins.configureDatabase
import com.changeyourlife.cyl.backend.plugins.configureHTTP
import com.changeyourlife.cyl.backend.plugins.configureMonitoring
import com.changeyourlife.cyl.backend.plugins.configureRouting
import com.changeyourlife.cyl.backend.plugins.configureSerialization
import com.changeyourlife.cyl.backend.service.AiJobService
import com.changeyourlife.cyl.backend.service.AiService
import com.changeyourlife.cyl.backend.service.EmailService
import com.changeyourlife.cyl.backend.service.JwtService
import com.changeyourlife.cyl.backend.service.WebSearchService
import com.changeyourlife.cyl.backend.service.ChatAttachmentCleanupScheduler
import com.changeyourlife.cyl.backend.service.ChatAttachmentLimits
import com.changeyourlife.cyl.backend.service.ChatAttachmentService
import com.changeyourlife.cyl.backend.storage.R2VoiceAssetStorage
import com.changeyourlife.cyl.backend.storage.UnavailableVoiceAssetStorage
import com.changeyourlife.cyl.backend.domain.VoiceAssetStorage
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val developmentMode = System.getenv("KTOR_DEVELOPMENT")?.toBooleanStrictOrNull() ?: true

    embeddedServer(
        factory = Netty,
        rootConfig = serverConfig {
            this.developmentMode = developmentMode
            watchPaths = if (developmentMode) listOf("classes", "resources") else emptyList()
            module {
                module()
            }
        },
        configure = {
            connectors.add(
                EngineConnectorBuilder().apply {
                    this.host = host
                    this.port = port
                },
            )
        },
    ).start(wait = true)
}

fun Application.module(
    appConfig: AppConfig = AppConfig.fromEnvironment(),
    userRepositoryOverride: UserRepository? = null,
    contentRepositoryOverride: ContentRepository? = null,
    aiActionLogRepositoryOverride: AiActionLogRepository? = null,
    chatSyncRepositoryOverride: ChatSyncRepository? = null,
    aiSkillSyncRepositoryOverride: AiSkillSyncRepository? = null,
    chatAttachmentRepositoryOverride: ChatAttachmentRepository? = null,
    voiceAssetStorageOverride: VoiceAssetStorage? = null,
) {
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureAuthentication(appConfig.jwt)

    val dataSource = configureDatabase(appConfig.database)
    val userRepository = userRepositoryOverride
        ?: dataSource?.let { PostgresUserRepository(it) }
        ?: InMemoryUserRepository()
    val contentRepository = contentRepositoryOverride
        ?: dataSource?.let { PostgresContentRepository(it) }
        ?: InMemoryContentRepository()
    val aiActionLogRepository = aiActionLogRepositoryOverride
        ?: dataSource?.let { PostgresAiActionLogRepository(it) }
        ?: InMemoryAiActionLogRepository()
    val chatSyncRepository = chatSyncRepositoryOverride
        ?: dataSource?.let { PostgresChatSyncRepository(it) }
        ?: InMemoryChatSyncRepository()
    val aiSkillSyncRepository = aiSkillSyncRepositoryOverride
        ?: dataSource?.let { PostgresAiSkillSyncRepository(it) }
        ?: InMemoryAiSkillSyncRepository(contentRepository)
    val chatAttachmentRepository = chatAttachmentRepositoryOverride
        ?: dataSource?.let { PostgresChatAttachmentRepository(it) }
        ?: InMemoryChatAttachmentRepository()

    val aiService = AiService(
        lmStudioBaseUrl = appConfig.lmStudioBaseUrl,
        lmStudioApiKey = appConfig.lmStudioApiKey,
        lmStudioModel = appConfig.lmStudioModel,
        lmStudioVisionModels = appConfig.lmStudioVisionModels,
        glmApiKey = appConfig.glmApiKey,
        geminiApiKey = appConfig.geminiApiKey,
        openRouterApiKey = appConfig.openRouterApiKey,
        openRouterModel = appConfig.openRouterModel,
        openRouterVisionModels = appConfig.openRouterVisionModels,
        webSearchService = WebSearchService(appConfig.webSearch),
    )
    environment.log.info(
        "AI provider initialized: provider=${aiService.activeProvider}, model=${aiService.activeModel}",
    )
    environment.log.info(
        "AI vision initialized: pipeline=${aiService.visionPipelineVersion}, maxDimension=${aiService.visionMaxImageDimension}, maxBytes=${aiService.visionMaxImageBytes}, lmStudioVisionModels=${aiService.lmStudioVisionModelLabel}",
    )
    val aiJobRepository = dataSource?.let { PostgresAiJobRepository(it) }
        ?: InMemoryAiJobRepository()
    val aiJobService = AiJobService(aiJobRepository)
    val emailService = EmailService(appConfig.email)
    environment.log.info("Email provider initialized: resendConfigured=${appConfig.email.isConfigured}")
    val voiceAssetStorage = voiceAssetStorageOverride ?: appConfig.voiceNotes.r2
        .takeIf { config -> appConfig.voiceNotes.enabled && config.isConfigured }
        ?.let { config ->
            R2VoiceAssetStorage(
                endpoint = checkNotNull(config.endpoint),
                bucket = checkNotNull(config.bucket),
                accessKeyId = checkNotNull(config.accessKeyId),
                secretAccessKey = checkNotNull(config.secretAccessKey),
            )
        }
        ?: UnavailableVoiceAssetStorage(featureEnabled = appConfig.voiceNotes.enabled)
    val chatAttachmentService = ChatAttachmentService(
        repository = chatAttachmentRepository,
        storage = voiceAssetStorage,
        featureEnabled = appConfig.voiceNotes.enabled,
        limits = ChatAttachmentLimits(
            maxDurationMs = appConfig.voiceNotes.maxDurationMs,
            maxBytes = appConfig.voiceNotes.maxBytes,
            uploadUrlTtlMillis = appConfig.voiceNotes.uploadUrlTtlMillis,
            playbackUrlTtlMillis = appConfig.voiceNotes.playbackUrlTtlMillis,
            orphanTtlMillis = appConfig.voiceNotes.orphanTtlMillis,
        ),
    )
    val attachmentCleanupScheduler = appConfig.voiceNotes.enabled
        .takeIf { enabled -> enabled && (appConfig.voiceNotes.r2.isConfigured || voiceAssetStorageOverride != null) }
        ?.let {
            ChatAttachmentCleanupScheduler(
                service = chatAttachmentService,
                intervalMillis = appConfig.voiceNotes.cleanupIntervalMillis,
                logger = environment.log,
            ).also(ChatAttachmentCleanupScheduler::start)
        }
    environment.log.info(
        "Voice-note asset foundation initialized: enabled=${appConfig.voiceNotes.enabled}, storageConfigured=${appConfig.voiceNotes.r2.isConfigured || voiceAssetStorageOverride != null}",
    )
    monitor.subscribe(ApplicationStopped) {
        attachmentCleanupScheduler?.close()
        (voiceAssetStorage as? AutoCloseable)?.close()
    }

    configureRouting(
        userRepository = userRepository,
        contentRepository = contentRepository,
        aiActionLogRepository = aiActionLogRepository,
        chatSyncRepository = chatSyncRepository,
        aiSkillSyncRepository = aiSkillSyncRepository,
        jwtService = JwtService(appConfig.jwt),
        databaseConfigured = dataSource != null,
        aiService = aiService,
        aiJobService = aiJobService,
        passwordResetEmailSender = emailService,
        chatAttachmentService = chatAttachmentService,
    )
}
