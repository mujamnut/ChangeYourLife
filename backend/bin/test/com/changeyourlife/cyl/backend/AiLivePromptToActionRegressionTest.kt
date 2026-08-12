package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.config.AppConfig
import com.changeyourlife.cyl.backend.service.AiService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

class AiLivePromptToActionRegressionTest {
    @Test
    fun configuredProviderHandlesCriticalMalayActionCorpus() = runBlocking {
        assumeTrue(
            "Set CYL_RUN_LIVE_AI_REGRESSION=true to run real-provider action regressions.",
            liveRegressionEnabled(),
        )
        val config = AppConfig.fromEnvironment()
        val service = AiService(
            lmStudioBaseUrl = config.lmStudioBaseUrl,
            lmStudioApiKey = config.lmStudioApiKey,
            lmStudioModel = config.lmStudioModel,
            lmStudioVisionModels = config.lmStudioVisionModels,
            openRouterApiKey = config.openRouterApiKey,
            openRouterModel = config.openRouterModel,
            openRouterVisionModels = config.openRouterVisionModels,
            timeoutConfig = config.aiTimeouts,
        )
        assumeTrue(
            "Configure OPENROUTER_API_KEY before running live action regressions.",
            service.textProviderLabel == "openrouter",
        )

        AiPromptActionRegressionCorpus.liveCases.forEach { regressionCase ->
            assertLiveCase(
                service = service,
                regressionCase = regressionCase,
                maxAttempts = liveRegressionAttempts(),
            )
        }
    }

    private suspend fun assertLiveCase(
        service: AiService,
        regressionCase: AiPromptActionRegressionCase,
        maxAttempts: Int,
    ) {
        var lastFailure: AssertionError? = null
        repeat(maxAttempts) {
            val result = service.chatWithActions(
                messages = regressionCase.messages,
                pages = regressionCase.pages,
                clientDate = "2026-07-28",
                clientTimezone = "Asia/Kuala_Lumpur",
            )
            try {
                assertEquals(
                    expected = AiService.AiActionSource.Model,
                    actual = result.source,
                    message = "${regressionCase.id} was handled by fallback instead of the configured model.",
                )
                regressionCase.assertResult(result)
                return
            } catch (failure: AssertionError) {
                lastFailure = failure
            }
        }
        throw AssertionError(
            "Live provider failed ${regressionCase.id} after $maxAttempts attempt(s).",
            lastFailure,
        )
    }

    private fun liveRegressionEnabled(): Boolean =
        readFlag("CYL_RUN_LIVE_AI_REGRESSION")

    private fun liveRegressionAttempts(): Int =
        readSetting("CYL_LIVE_AI_REGRESSION_ATTEMPTS")
            ?.toIntOrNull()
            ?.coerceIn(1, 3)
            ?: 2

    private fun readFlag(name: String): Boolean =
        readSetting(name)?.equals("true", ignoreCase = true) == true

    private fun readSetting(name: String): String? =
        System.getenv(name)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: System.getProperty(name)
                ?.trim()
                ?.takeIf(String::isNotBlank)
}
