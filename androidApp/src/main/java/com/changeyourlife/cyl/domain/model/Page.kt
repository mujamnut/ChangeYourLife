package com.changeyourlife.cyl.domain.model

data class Page(
    val id: String,
    val workspaceId: String,
    val parentPageId: String?,
    val title: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val revision: Long = 0L,
)
