package com.changeyourlife.cyl.backend.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigAiTimeoutTest {
    @Test
    fun fromEnvironmentLoadsEveryAiTimeoutSetting() {
        val config = AppConfig.fromEnvironment(
            environment = mapOf(
                "AI_JOB_DEADLINE_MS" to "240000",
                "AI_CONNECT_TIMEOUT_MS" to "7000",
                "LMSTUDIO_REQUEST_TIMEOUT_MS" to "110000",
                "OPENROUTER_REQUEST_TIMEOUT_MS" to "55000",
                "AI_FINALIZATION_RESERVE_MS" to "12000",
            ),
        )

        assertEquals(
            AiTimeoutConfig(
                jobDeadlineMs = 240_000L,
                connectTimeoutMs = 7_000L,
                lmStudioRequestTimeoutMs = 110_000L,
                openRouterRequestTimeoutMs = 55_000L,
                finalizationReserveMs = 12_000L,
            ),
            config.aiTimeouts,
        )
    }
}
