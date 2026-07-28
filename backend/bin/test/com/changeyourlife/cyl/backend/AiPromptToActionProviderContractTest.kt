package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.backend.model.ai.ChatMessage
import com.changeyourlife.cyl.backend.service.AiService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AiPromptToActionProviderContractTest(
    private val regressionCase: AiPromptActionRegressionCase,
) {
    @Test
    fun recordedProviderResponseCrossesTheRealPlannerBoundary() = runBlocking {
        var providerMessages = emptyList<ChatMessage>()
        var providerJsonMode = false
        var providerTemperature = Double.NaN
        val service = AiService(
            openRouterApiKey = "regression-only-key",
            completionProvider = { messages, jsonMode, temperature ->
                providerMessages = messages
                providerJsonMode = jsonMode
                providerTemperature = temperature
                regressionCase.providerReply
            },
        )

        val result = service.chatWithActions(
            messages = regressionCase.messages,
            pages = regressionCase.pages,
            clientDate = "2026-07-28",
            clientTimezone = "Asia/Kuala_Lumpur",
        )

        assertTrue(providerJsonMode, "${regressionCase.id} did not request structured JSON.")
        assertEquals(0.15, providerTemperature, absoluteTolerance = 0.0001)
        assertEquals("system", providerMessages.first().role)
        assertTrue(
            providerMessages.first().content.contains("Return ONLY one valid JSON object"),
            "${regressionCase.id} did not receive the action planner system prompt.",
        )
        assertTrue(
            providerMessages.first().content.contains("CYL_ACTION_SCHEMA"),
            "${regressionCase.id} did not receive the shared action contract.",
        )
        regressionCase.expectedActions.forEach { expected ->
            assertTrue(
                providerMessages.first().content.contains(expected.type),
                "${regressionCase.id} uses ${expected.type}, but the provider prompt did not advertise it.",
            )
            assertTrue(
                expected.type in AiActionContractSchema.supportedTypes,
                "${regressionCase.id} references an action outside the shared contract.",
            )
        }
        assertEquals(
            regressionCase.messages,
            providerMessages.drop(1),
            "${regressionCase.id} lost or reordered conversation turns before the provider call.",
        )
        assertEquals(AiService.AiActionSource.Model, result.source)
        regressionCase.assertResult(result)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<AiPromptActionRegressionCase>> =
            AiPromptActionRegressionCorpus.cases.map { regressionCase ->
                arrayOf(regressionCase)
            }
    }
}
