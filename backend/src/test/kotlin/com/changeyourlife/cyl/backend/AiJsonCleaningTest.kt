package com.changeyourlife.cyl.backend

import com.changeyourlife.cyl.backend.service.cleanAiJson
import kotlin.test.Test
import kotlin.test.assertEquals

class AiJsonCleaningTest {
    @Test
    fun `extracts fenced json object`() {
        val raw = """
            ```json
            {"reply":"Done","actions":[]}
            ```
        """.trimIndent()

        assertEquals(
            """{"reply":"Done","actions":[]}""",
            raw.cleanAiJson(),
        )
    }

    @Test
    fun `extracts json object from surrounding prose`() {
        val raw = """Sure, here is the action: {"reply":"Done","actions":[{"type":"APPEND_BLOCK","content":"Note"}]} Thanks."""

        assertEquals(
            """{"reply":"Done","actions":[{"type":"APPEND_BLOCK","content":"Note"}]}""",
            raw.cleanAiJson(),
        )
    }

    @Test
    fun `extracts json array for legacy list responses`() {
        val raw = """Tasks: ["Buy tickets","Pack bag"]"""

        assertEquals(
            """["Buy tickets","Pack bag"]""",
            raw.cleanAiJson(),
        )
    }
}
