package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.ChatActionMetadata
import com.changeyourlife.cyl.domain.model.ChatActionMetadataItem
import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAiMemoryContextUseCaseTest {
    private val useCase = BuildAiMemoryContextUseCase()

    @Test
    fun buildsMemoryFromPriorSessionsWithoutDuplicatingCurrentSession() {
        val sessions = listOf(
            chatSession(id = "current", title = "Current chat", updatedAt = 3_000L),
            chatSession(id = "budget", title = "Budget Tracker", updatedAt = 2_000L),
            chatSession(id = "chicken", title = "Penjagaan Ayam", updatedAt = 1_000L),
        )
        val messages = listOf(
            chatMessage(
                id = "current-user",
                sessionId = "current",
                role = "user",
                content = "Current session content must not be repeated.",
                createdAt = 3_000L,
            ),
            chatMessage(
                id = "budget-user",
                sessionId = "budget",
                role = "user",
                content = "Saya nak budget category Food, Fuel, Makeup dan status Planned, Paid.",
                createdAt = 2_000L,
            ),
            chatMessage(
                id = "budget-ai",
                sessionId = "budget",
                role = "assistant",
                content = "Created Budget Tracker database.",
                pageLinks = listOf(ChatPageLink(pageId = "page-budget", title = "Budget Tracker")),
                actionMetadata = ChatActionMetadata(
                    executedActions = listOf(ChatActionMetadataItem(type = "CREATE_DATABASE", target = "Budget Tracker")),
                ),
                createdAt = 2_100L,
            ),
            chatMessage(
                id = "chicken-user",
                sessionId = "chicken",
                role = "user",
                content = "Buat jadual penjagaan ayam setiap hari.",
                createdAt = 1_000L,
            ),
        )

        val memory = useCase(
            currentSessionId = "current",
            prompt = "Tambah expense makeup ke budget",
            sessions = sessions,
            messages = messages,
        )

        assertTrue(memory.content.contains("CYL_MEMORY_CONTEXT"))
        assertTrue(memory.content.contains("Budget Tracker"))
        assertTrue(memory.content.contains("Food, Fuel, Makeup"))
        assertTrue(memory.content.contains("create database"))
        assertFalse(memory.content.contains("Current session content"))
    }

    @Test
    fun returnsEmptyMemoryWhenNoUsefulPriorContextExists() {
        val memory = useCase(
            currentSessionId = "current",
            prompt = "hello",
            sessions = listOf(chatSession(id = "current", title = "Current")),
            messages = emptyList(),
        )

        assertFalse(memory.isNotBlank)
    }

    private fun chatSession(
        id: String,
        title: String,
        updatedAt: Long = 1_000L,
    ): ChatSession {
        return ChatSession(
            id = id,
            scopeId = "home:workspace",
            title = title,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
    }

    private fun chatMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        pageLinks: List<ChatPageLink> = emptyList(),
        actionMetadata: ChatActionMetadata? = null,
        createdAt: Long = 1_000L,
    ): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            pageLinks = pageLinks,
            actionMetadata = actionMetadata,
            createdAt = createdAt,
        )
    }
}
