package com.changeyourlife.cyl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "applied_ai_actions",
    indices = [
        Index(value = ["requestMessageId", "actionIndex"], unique = true),
        Index(value = ["workspaceId", "updatedAt"]),
        Index(value = ["state"]),
    ],
)
data class AiAppliedActionEntity(
    @PrimaryKey val idempotencyKey: String,
    val requestMessageId: String,
    val workspaceId: String,
    val auditId: String,
    val actionIndex: Int,
    val actionType: String,
    val actionFingerprint: String,
    val state: String,
    val failure: String,
    val createdAt: Long,
    val updatedAt: Long,
)
