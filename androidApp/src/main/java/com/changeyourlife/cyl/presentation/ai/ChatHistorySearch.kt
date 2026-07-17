package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatSession

data class ChatHistorySearchResult(
    val session: ChatSession,
    val snippet: String,
    val matchCount: Int,
    val lastMatchedAt: Long,
)

internal fun buildChatHistorySearchResults(
    sessions: List<ChatSession>,
    messages: List<ChatMessage>,
    query: String,
    limit: Int = 30,
): List<ChatHistorySearchResult> {
    val terms = query.chatSearchTerms()
    if (terms.isEmpty()) return emptyList()

    val normalizedQuery = query.compactChatLine().lowercase()
    val messagesBySession = messages.groupBy { message -> message.sessionId }

    return sessions.mapNotNull { session ->
        val candidates = buildList {
            add(
                ChatSearchCandidate(
                    text = session.title,
                    source = ChatSearchSource.Title,
                    createdAt = session.updatedAt,
                ),
            )
            messagesBySession[session.id].orEmpty().forEach { message ->
                val attachmentText = message.attachments.joinToString(separator = " ") { attachment -> attachment.name }
                val pageLinkText = message.pageLinks.joinToString(separator = " ") { link -> link.title }
                add(
                    ChatSearchCandidate(
                        text = listOf(message.content, attachmentText, pageLinkText)
                            .filter { value -> value.isNotBlank() }
                            .joinToString(separator = " "),
                        source = if (message.role.equals("user", ignoreCase = true)) {
                            ChatSearchSource.UserMessage
                        } else {
                            ChatSearchSource.AssistantMessage
                        },
                        createdAt = message.createdAt,
                    ),
                )
            }
        }

        val searchableText = candidates.joinToString(separator = "\n") { candidate -> candidate.text }
        if (!terms.all { term -> searchableText.contains(term, ignoreCase = true) }) {
            return@mapNotNull null
        }

        val matches = candidates
            .map { candidate -> candidate to candidate.score(terms, normalizedQuery) }
            .filter { (_, score) -> score > 0 }

        val bestCandidate = matches
            .sortedWith(
                compareByDescending<Pair<ChatSearchCandidate, Int>> { (_, score) -> score }
                    .thenByDescending { (candidate, _) -> candidate.createdAt },
            )
            .firstOrNull()
            ?.first

        val snippet = bestCandidate
            ?.toSnippet()
            .orEmpty()
            .ifBlank { session.title.ifBlank { "New chat" } }

        ChatSearchRankedResult(
            result = ChatHistorySearchResult(
                session = session,
                snippet = snippet,
                matchCount = matches.size,
                lastMatchedAt = matches.maxOfOrNull { (candidate, _) -> candidate.createdAt }
                    ?: session.updatedAt,
            ),
            score = matches.sumOf { (_, score) -> score },
        )
    }
        .sortedWith(
            compareByDescending<ChatSearchRankedResult> { ranked -> ranked.score }
                .thenByDescending { ranked -> ranked.result.lastMatchedAt }
                .thenBy { ranked -> ranked.result.session.title.lowercase() },
        )
        .take(limit)
        .map { ranked -> ranked.result }
}

private data class ChatSearchRankedResult(
    val result: ChatHistorySearchResult,
    val score: Int,
)

private data class ChatSearchCandidate(
    val text: String,
    val source: ChatSearchSource,
    val createdAt: Long,
)

private enum class ChatSearchSource {
    Title,
    UserMessage,
    AssistantMessage,
}

private fun ChatSearchCandidate.score(terms: List<String>, normalizedQuery: String): Int {
    val normalizedText = text.lowercase()
    var score = 0
    if (normalizedQuery.isNotBlank() && normalizedText.contains(normalizedQuery)) {
        score += 8
    }
    terms.forEach { term ->
        when {
            normalizedText == term -> score += 6
            normalizedText.startsWith(term) -> score += 4
            normalizedText.contains(term) -> score += 2
        }
    }
    if (source == ChatSearchSource.Title && score > 0) {
        score += 4
    }
    return score
}

private fun ChatSearchCandidate.toSnippet(): String {
    val prefix = when (source) {
        ChatSearchSource.Title -> ""
        ChatSearchSource.UserMessage -> "You: "
        ChatSearchSource.AssistantMessage -> "CYL: "
    }
    return (prefix + text.compactChatLine()).take(120)
}

private fun String.chatSearchTerms(): List<String> {
    val stopWords = setOf(
        "ai",
        "chat",
        "history",
        "sejarah",
        "mesej",
        "message",
        "messages",
        "cari",
        "search",
        "find",
        "tunjuk",
        "show",
        "dalam",
        "dekat",
        "di",
        "yang",
        "dan",
        "atau",
        "the",
        "a",
        "an",
        "to",
        "for",
        "of",
        "in",
        "on",
    )
    return lowercase()
        .split(Regex("[^a-z0-9@]+"))
        .map { it.trim('@') }
        .filter { term -> term.length >= 2 && term !in stopWords }
        .distinct()
}

private fun String.compactChatLine(): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .take(180)
}
