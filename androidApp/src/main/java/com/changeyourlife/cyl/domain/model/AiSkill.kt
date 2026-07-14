package com.changeyourlife.cyl.domain.model

data class AiSkill(
    val id: String,
    val workspaceId: String,
    val name: String,
    val whenToUse: String,
    val instructions: String,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
