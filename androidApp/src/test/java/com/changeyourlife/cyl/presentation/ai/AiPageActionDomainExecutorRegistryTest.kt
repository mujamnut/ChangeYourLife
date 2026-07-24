package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.aicontract.AiActionContractSchema
import com.changeyourlife.cyl.domain.repository.ChatAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiPageActionDomainExecutorRegistryTest {
    @Test
    fun everySharedContractActionHasAnAndroidDomainExecutor() {
        assertEquals(73, AiActionContractSchema.supportedTypes.size)
        assertEquals(
            AiActionContractSchema.supportedTypes,
            AiActionExecutionRegistry.supportedActions,
        )

        AiActionContractSchema.supportedTypes.forEach { actionType ->
            val action = ChatAction(type = actionType, title = "Target")
            val executor = AiPageActionDomainExecutorRegistry.executorFor(action)

            assertNotNull("Missing executor for $actionType", executor)
            assertEquals(
                AiActionExecutionRegistry.domainFor(actionType),
                executor?.domain,
            )
        }
    }
}
