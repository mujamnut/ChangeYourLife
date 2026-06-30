package com.changeyourlife.cyl.data.remote.ai

import com.changeyourlife.cyl.core.di.NetworkModule
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class AiDtoJsonTest {
    @Test
    fun networkJsonCoercesNullActionFieldsToDefaults() {
        val response = NetworkModule.provideJson().decodeFromString<ChatWithActionsResponseDto>(
            """
                {
                  "reply": "ok",
                  "actions": [
                    {
                      "type": "ADD_TABLE_ROW",
                      "title": null,
                      "targetTitle": null,
                      "cellValues": null
                    }
                  ],
                  "validationIssues": null
                }
            """.trimIndent(),
        )

        val action = response.actions.single()
        assertEquals("ok", response.reply)
        assertEquals("ADD_TABLE_ROW", action.type)
        assertEquals("", action.title)
        assertEquals("", action.targetTitle)
        assertEquals(emptyMap<String, String>(), action.cellValues)
        assertEquals(emptyList<AiActionValidationIssueDto>(), response.validationIssues)
    }
}
