package com.changeyourlife.cyl.presentation.ai

data class AiChatMessage(
    val role: String,
    val content: String,
    val pageLinks: List<AiChatPageLink> = emptyList(),
)

data class AiChatPageLink(
    val pageId: String,
    val title: String,
    val targetType: String = "",
    val targetId: String = "",
)

fun List<AiChatMessage>.toRoleContentPairs(): List<Pair<String, String>> {
    return map { message -> message.role to message.content }
}
