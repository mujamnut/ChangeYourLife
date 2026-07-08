package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.ChatMessage
import com.changeyourlife.cyl.domain.model.ChatSession
import javax.inject.Inject

class BuildAiMemoryContextUseCase @Inject constructor() {
    operator fun invoke(
        currentSessionId: String,
        prompt: String,
        sessions: List<ChatSession>,
        messages: List<ChatMessage>,
    ): AiMemoryContext {
        val priorSessions = sessions
            .filterNot { session -> session.id == currentSessionId }
            .sortedByDescending { session -> session.updatedAt }
        val messagesBySession = messages
            .filterNot { message -> message.sessionId == currentSessionId }
            .filter { message -> message.content.isNotBlank() }
            .groupBy { message -> message.sessionId }
        val preferenceLines = messages
            .filterNot { message -> message.sessionId == currentSessionId }
            .filter { message -> message.role.equals("user", ignoreCase = true) }
            .sortedByDescending { message -> message.createdAt }
            .mapNotNull { message -> message.content.toPreferenceMemoryLine() }
            .distinct()
            .take(MaxPreferenceLines)
        val relevantSessionLines = priorSessions
            .mapNotNull { session ->
                val sessionMessages = messagesBySession[session.id].orEmpty()
                if (sessionMessages.isEmpty()) return@mapNotNull null
                val score = session.relevanceScore(prompt, sessionMessages)
                if (score <= 0 && relevantSessionLinesShouldBeStrict(prompt)) return@mapNotNull null
                AiMemorySessionCandidate(
                    score = score,
                    line = session.toMemoryLine(sessionMessages),
                )
            }
            .sortedWith(compareByDescending<AiMemorySessionCandidate> { candidate -> candidate.score })
            .take(MaxRelevantSessionLines)
            .map { candidate -> candidate.line }
        val recentActivityLines = priorSessions
            .flatMap { session ->
                messagesBySession[session.id].orEmpty()
                    .filter { message ->
                        message.pageLinks.isNotEmpty() || message.actionMetadata?.executedActions?.isNotEmpty() == true
                    }
                    .takeLast(2)
                    .mapNotNull { message -> message.toRecentActivityLine(session) }
            }
            .takeLast(MaxRecentActivityLines)

        val sections = buildList {
            if (preferenceLines.isNotEmpty()) {
                add("User preferences:\n${preferenceLines.joinToString(separator = "\n") { line -> "- $line" }}")
            }
            if (relevantSessionLines.isNotEmpty()) {
                add("Relevant prior chats:\n${relevantSessionLines.joinToString(separator = "\n") { line -> "- $line" }}")
            }
            if (recentActivityLines.isNotEmpty()) {
                add("Recent CYL activity:\n${recentActivityLines.joinToString(separator = "\n") { line -> "- $line" }}")
            }
        }
        if (sections.isEmpty()) return AiMemoryContext.Empty
        return AiMemoryContext(
            content = """
                CYL_MEMORY_CONTEXT:
                Use this as private background memory only when it helps answer or edit correctly.
                Do not quote this section, mention memory internals, or expose hidden IDs.
                Prefer current user instructions over memory if they conflict.

                ${sections.joinToString(separator = "\n\n")}
            """.trimIndent(),
        )
    }

    private fun ChatSession.toMemoryLine(sessionMessages: List<ChatMessage>): String {
        val title = title.ifBlank { "Untitled chat" }.cleanMemoryText(MaxTitleLength)
        val lastUserMessage = sessionMessages
            .lastOrNull { message -> message.role.equals("user", ignoreCase = true) }
            ?.content
            ?.cleanMemoryText(MaxSnippetLength)
        val lastAssistantMessage = sessionMessages
            .lastOrNull { message -> message.role.equals("assistant", ignoreCase = true) }
            ?.content
            ?.cleanMemoryText(MaxSnippetLength)
        return buildString {
            append(title)
            if (!lastUserMessage.isNullOrBlank()) append(" | user: ").append(lastUserMessage)
            if (!lastAssistantMessage.isNullOrBlank()) append(" | ai: ").append(lastAssistantMessage)
        }
    }

    private fun ChatMessage.toRecentActivityLine(session: ChatSession): String? {
        val title = session.title.ifBlank { "Untitled chat" }.cleanMemoryText(MaxTitleLength)
        val pageText = pageLinks
            .take(2)
            .joinToString(separator = ", ") { link -> link.title.ifBlank { "Untitled page" } }
        val actionText = actionMetadata
            ?.executedActions
            ?.take(2)
            ?.joinToString(separator = ", ") { action -> action.type.lowercase().replace('_', ' ') }
            .orEmpty()
        val details = listOf(pageText, actionText)
            .filter { value -> value.isNotBlank() }
            .joinToString(separator = " | ")
        return details.takeIf { value -> value.isNotBlank() }?.let { "$title: $it" }
    }

    private fun ChatSession.relevanceScore(
        prompt: String,
        sessionMessages: List<ChatMessage>,
    ): Int {
        val terms = prompt.memoryTerms()
        if (terms.isEmpty()) return 1
        val haystack = buildString {
            append(title).append(' ')
            sessionMessages.takeLast(8).forEach { message -> append(message.content).append(' ') }
        }.lowercase()
        return terms.count { term -> haystack.contains(term) }
    }

    private fun String.toPreferenceMemoryLine(): String? {
        val normalized = cleanMemoryText(MaxSnippetLength)
        if (normalized.isBlank()) return null
        val lower = normalized.lowercase()
        val isPreference = PreferenceTriggers.any { trigger -> lower.contains(trigger) }
        return normalized.takeIf { isPreference }
    }

    private fun relevantSessionLinesShouldBeStrict(prompt: String): Boolean {
        return prompt.memoryTerms().size >= 2
    }

    private fun String.memoryTerms(): List<String> {
        return lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { term -> term.trim() }
            .filter { term -> term.length >= 3 && term !in StopWords }
            .distinct()
            .take(MaxPromptTerms)
    }

    private fun String.cleanMemoryText(maxLength: Int): String {
        val cleaned = replace(Regex("\\s+"), " ")
            .replace("CYL_MENTION_CONTEXT:", "")
            .replace("CYL_MEMORY_CONTEXT:", "")
            .trim()
        return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "..."
    }

    private data class AiMemorySessionCandidate(
        val score: Int,
        val line: String,
    )

    companion object {
        private const val MaxPreferenceLines = 5
        private const val MaxRelevantSessionLines = 4
        private const val MaxRecentActivityLines = 4
        private const val MaxPromptTerms = 10
        private const val MaxTitleLength = 48
        private const val MaxSnippetLength = 140

        private val PreferenceTriggers = listOf(
            "saya nak",
            "aku nak",
            "i want",
            "i prefer",
            "prefer",
            "jangan",
            "tak mahu",
            "tak nak",
            "dont",
            "don't",
            "always",
            "sentiasa",
            "biasanya",
        )
        private val StopWords = setOf(
            "dan",
            "yang",
            "untuk",
            "dengan",
            "dalam",
            "buat",
            "bagi",
            "saya",
            "awak",
            "kamu",
            "this",
            "that",
            "with",
            "from",
            "buatkan",
        )
    }
}

data class AiMemoryContext(
    val content: String,
) {
    val isNotBlank: Boolean
        get() = content.isNotBlank()

    companion object {
        val Empty = AiMemoryContext(content = "")
    }
}
