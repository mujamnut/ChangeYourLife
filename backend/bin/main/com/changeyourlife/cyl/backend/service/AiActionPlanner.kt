package com.changeyourlife.cyl.backend.service

class AiActionPlanner {
    fun selectActionResult(
        prompt: String,
        modelResult: AiService.AiActionResult?,
        promptResult: AiService.AiActionResult?,
    ): AiService.AiActionResult? {
        if (promptResult == null) return modelResult

        if (modelResult == null) {
            if (promptResult.isCreativeCreationFallback()) return null
            return promptResult.copy(
                validationIssues = promptResult.validationIssues,
            )
        }

        if (modelResult.actions.isEmpty()) return modelResult

        if (promptResult.shouldRepairClearlyMalformedModelResult(modelResult, prompt)) {
            return promptResult.copy(
                validationIssues = modelResult.validationIssues + promptResult.validationIssues,
            )
        }

        return modelResult
    }

    private fun AiService.AiActionResult.isCreativeCreationFallback(): Boolean =
        actions.any { action ->
            action.type.normalizedActionType() in setOf(
                "CREATE_PAGE",
                "CREATE_SUBPAGE",
                "CREATE_DATABASE",
                "CREATE_TABLE",
            )
        }

    private fun AiService.AiActionResult.shouldRepairClearlyMalformedModelResult(
        modelResult: AiService.AiActionResult,
        prompt: String,
    ): Boolean {
        if (actions.isEmpty()) return false
        if (modelResult.actions.isEmpty()) return true
        val visiblePrompt = prompt.withoutMentionContext()
        val promptTypes = actions.map { action -> action.type.normalizedActionType() }.toSet()
        val modelTypes = modelResult.actions.map { action -> action.type.normalizedActionType() }.toSet()
        val promptLooksLikeRowData = visiblePrompt.looksLikeTableRowRequest() ||
            visiblePrompt.extractMoneyAmount() != null
        if (promptLooksLikeRowData &&
            "ADD_TABLE_ROW" in promptTypes &&
            modelTypes.any { type -> type in setOf("CREATE_DATABASE", "CREATE_TABLE") } &&
            "ADD_TABLE_ROW" !in modelTypes
        ) {
            return true
        }
        if (visiblePrompt.looksLikeTableRowRequest() &&
            "ADD_TABLE_ROW" in promptTypes &&
            modelResult.actions.any { action ->
                action.type.normalizedActionType() == "ADD_TABLE_ROW" &&
                    action.cellValues.keys.any { key -> key.equals("Task", ignoreCase = true) }
            }
        ) {
            return true
        }
        return false
    }

    private fun String.withoutMentionContext(): String =
        substringBefore("CYL_MENTION_CONTEXT:").trim()

    private fun String.looksLikeTableRowRequest(): Boolean {
        val value = lowercase()
        val hasRowIntent = listOf("row", "baris", "rekod", "record")
            .any { token -> value.contains(token) }
        val hasAddIntent = listOf("tambah", "add", "masukkan", "letak", "insert", "create", "catat")
            .any { token -> value.contains(token) }
        val hasCreateTableIntent = listOf("table", "database", "jadual")
            .any { token -> value.contains(token) } &&
            listOf("buat", "create", "cipta")
                .any { token -> value.contains(token) }
        val isExplicitNoteWrite = listOf("nota", "note", "memo", "isi", "content").any { token ->
            value.contains(token)
        } && looksLikePageWriteRequest()
        val hasExpenseDataHint = extractMoneyAmount() != null ||
            listOf("makan", "ringgit", "rm", "harga", "jumlah", "tarikh", "hari ini", "harini", "today")
                .any { token -> value.contains(token) }
        if (isExplicitNoteWrite) return false
        if (hasCreateTableIntent && !hasRowIntent) return false
        return (hasRowIntent && hasAddIntent) || (hasExpenseDataHint && !hasCreateTableIntent)
    }

    private fun String.looksLikePageWriteRequest(): Boolean {
        val value = lowercase()
        val hasWriteIntent = listOf(
            "tulis",
            "write",
            "karang",
            "draft",
            "buat nota",
            "make note",
            "add note",
            "isi page",
            "content page",
        ).any { token -> value.contains(token) }
        val hasPageIntent = listOf("page", "halaman", "nota", "note", "content", "isi").any { token ->
            value.contains(token)
        }
        return hasWriteIntent && hasPageIntent
    }

    private fun String.extractMoneyAmount(): String? {
        val match = Regex("(?i)(?:rm\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:ringgit|rm)?")
            .find(this)
            ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.takeIf { value -> value.isNotBlank() }
    }

    private fun String.actionSegments(): List<String> {
        val splitText = replace(Regex("(?i)\\b(lepas tu|selepas tu|pastu|astu|then|next)\\b"), "\n")
            .replace(
                Regex("(?i)\\s*,\\s*(?=(dan\\s+)?(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
            .replace(
                Regex("(?i)\\b(dan|and)\\s+(?=(tu\\s+dah\\s+)?(untuk\\s+)?(tambah|add|masukkan|letak|buat|create|padam|buang|delete|hapus|catat|row|baris|rekod|makan|duit|belanja|harga|jumlah|ringgit|rm|tarikh|hari))"),
                "\n",
            )
        return splitText
            .lineSequence()
            .map { segment -> segment.trim(' ', ',', '.', ';', ':', '-') }
            .filter { segment -> segment.isNotBlank() }
            .toList()
            .ifEmpty { listOf(trim()) }
    }

    private fun String.normalizedActionType(): String =
        trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
}
