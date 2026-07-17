package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatMessageAttachment
import com.changeyourlife.cyl.domain.model.ChatPageLink
import com.changeyourlife.cyl.domain.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistorySearchTest {
    @Test
    fun blankQueryReturnsNoResults() {
        val results = buildChatHistorySearchResults(
            sessions = listOf(session(id = "s1", title = "Budget")),
            messages = listOf(message(sessionId = "s1", content = "Food expense")),
            query = "   ",
        )

        assertEquals(emptyList<ChatHistorySearchResult>(), results)
    }

    @Test
    fun searchesMessageContentAndReturnsSessionSnippet() {
        val results = buildChatHistorySearchResults(
            sessions = listOf(
                session(id = "budget", title = "Budget Tracker", updatedAt = 10L),
                session(id = "ideas", title = "Ideas", updatedAt = 20L),
            ),
            messages = listOf(
                message(sessionId = "budget", content = "Saya beli makeup 29 ringgit hari ini.", createdAt = 30L),
                message(sessionId = "ideas", content = "Meeting notes only.", createdAt = 40L),
            ),
            query = "makeup",
        )

        assertEquals(listOf("budget"), results.map { result -> result.session.id })
        assertEquals("You: Saya beli makeup 29 ringgit hari ini.", results.single().snippet)
    }

    @Test
    fun queryTermsCanMatchAcrossTitleMessageAttachmentAndPageLink() {
        val results = buildChatHistorySearchResults(
            sessions = listOf(session(id = "s1", title = "Monthly Expenses")),
            messages = listOf(
                message(
                    sessionId = "s1",
                    content = "Need extract receipt values",
                    attachments = listOf(
                        ChatMessageAttachment(
                            id = "a1",
                            name = "fuel-receipt.png",
                            mimeType = "image/png",
                            kind = "image",
                            sizeBytes = 12L,
                        ),
                    ),
                    pageLinks = listOf(ChatPageLink(pageId = "p1", title = "July Budget")),
                ),
            ),
            query = "monthly fuel budget",
        )

        assertEquals(listOf("s1"), results.map { result -> result.session.id })
    }

    @Test
    fun titleMatchesRankAboveOlderMessageOnlyMatches() {
        val results = buildChatHistorySearchResults(
            sessions = listOf(
                session(id = "title", title = "Fuel Budget", updatedAt = 10L),
                session(id = "message", title = "Old chat", updatedAt = 100L),
            ),
            messages = listOf(
                message(sessionId = "message", content = "fuel budget discussion", createdAt = 100L),
            ),
            query = "fuel budget",
        )

        assertEquals("title", results.first().session.id)
        assertTrue(results.first().snippet.contains("Fuel Budget"))
    }

    private fun session(
        id: String,
        title: String,
        updatedAt: Long = 1L,
    ): ChatSession {
        return ChatSession(
            id = id,
            scopeId = "home:workspace",
            title = title,
            createdAt = 0L,
            updatedAt = updatedAt,
        )
    }

    private fun message(
        sessionId: String,
        content: String,
        createdAt: Long = 1L,
        attachments: List<ChatMessageAttachment> = emptyList(),
        pageLinks: List<ChatPageLink> = emptyList(),
    ): ChatMessage {
        return ChatMessage(
            id = "m-$sessionId-$createdAt",
            sessionId = sessionId,
            role = "user",
            content = content,
            pageLinks = pageLinks,
            attachments = attachments,
            createdAt = createdAt,
        )
    }
}
