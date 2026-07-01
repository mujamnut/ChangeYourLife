package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatTableColumn

object AiMarkdownTableActionRecovery {
    fun recover(
        prompt: String,
        reply: String,
        targetPageTitle: String? = null,
    ): List<ChatAction> {
        if (!prompt.looksLikeCreateTableRequest()) return emptyList()
        val table = reply.extractFirstMarkdownTable() ?: return emptyList()
        if (table.headers.size < 2 || table.rows.isEmpty()) return emptyList()

        val tableTitle = prompt.extractRequestedTableTitle()
            .ifBlank { reply.extractMarkdownHeadingTitle() }
            .ifBlank { "AI Table" }
        val columns = table.headers.map { header ->
            ChatTableColumn(
                name = header.ifBlank { "Column" },
                type = header.inferColumnType(),
            )
        }
        val tableRows = table.rows.map { row ->
            table.headers.mapIndexedNotNull { index, header ->
                val value = row.getOrNull(index).orEmpty()
                header.takeIf { it.isNotBlank() }?.let { it to value }
            }.toMap()
        }.filter { row -> row.values.any { value -> value.isNotBlank() } }
        if (tableRows.isEmpty()) return emptyList()

        val target = targetPageTitle.orEmpty().trim()
        val actionType = if (target.isBlank()) "CREATE_PAGE" else "CREATE_DATABASE"
        return listOf(
            ChatAction(
                type = actionType,
                title = if (actionType == "CREATE_PAGE") tableTitle else "",
                targetTitle = target,
                tableTitle = tableTitle,
                tableView = "Table",
                tableColumns = columns,
                tableRows = tableRows,
            ),
        )
    }
}

private data class MarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

private fun String.looksLikeCreateTableRequest(): Boolean {
    val value = withoutMentionContext().lowercase()
    val hasCreateIntent = listOf("buat", "buatkan", "create", "cipta", "tambah", "add").any { token ->
        value.contains(token)
    }
    val hasTableIntent = listOf("jadual", "table", "database", "tracker", "tracking", "rekod", "record").any { token ->
        value.contains(token)
    }
    return hasCreateIntent && hasTableIntent
}

private fun String.extractRequestedTableTitle(): String {
    return withoutMentionContext()
        .replace(Regex("@[^\\s,.;:]+"), " ")
        .replace(
            Regex(
                "(?i)\\b(tolong|please|buatkan|buat|create|cipta|tambah|add|jadual|table|database|baru|new|untuk|for|punya|dengan|with|dalam|dekat|di|ke|page|halaman|ini|sini|tersebut)\\b",
            ),
            " ",
        )
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', ':', ',', '.', '"', '\'')
        .toReadableTitle()
}

private fun String.extractMarkdownHeadingTitle(): String {
    return lineSequence()
        .map { line -> line.trim() }
        .firstOrNull { line -> line.startsWith("#") }
        ?.replace(Regex("^#+\\s*"), "")
        ?.removeMarkdownMarkup()
        ?.replace(Regex("\\([^)]*\\)"), " ")
        ?.replace(Regex("(?i)^jadual\\s+"), "")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.toReadableTitle()
        .orEmpty()
}

private fun String.extractFirstMarkdownTable(): MarkdownTable? {
    val lines = lineSequence().map { line -> line.trim() }.toList()
    val headerIndex = lines.indices.firstOrNull { index ->
        lines[index].isMarkdownTableRow() &&
            lines.getOrNull(index + 1)?.isMarkdownSeparatorRow() == true
    } ?: return null

    val headers = lines[headerIndex]
        .splitMarkdownTableRow()
        .map { cell -> cell.cleanMarkdownCell() }
        .filter { header -> header.isNotBlank() }
    if (headers.size < 2) return null

    val rows = lines.drop(headerIndex + 2)
        .takeWhile { line -> line.isMarkdownTableRow() }
        .map { line ->
            val cells = line.splitMarkdownTableRow().map { cell -> cell.cleanMarkdownCell() }
            headers.indices.map { index -> cells.getOrNull(index).orEmpty() }
        }
        .filter { row -> row.any { cell -> cell.isNotBlank() } }
    return MarkdownTable(headers = headers, rows = rows)
}

private fun String.isMarkdownTableRow(): Boolean {
    val value = trim()
    return value.startsWith("|") && value.endsWith("|") && value.count { char -> char == '|' } >= 2
}

private fun String.isMarkdownSeparatorRow(): Boolean {
    val value = trim()
    if (!value.isMarkdownTableRow()) return false
    return value.all { char -> char == '|' || char == '-' || char == ':' || char.isWhitespace() }
}

private fun String.splitMarkdownTableRow(): List<String> =
    trim()
        .trim('|')
        .split("|")
        .map { cell -> cell.trim() }

private fun String.cleanMarkdownCell(): String {
    return replace(Regex("(?i)<br\\s*/?>"), "; ")
        .replace(Regex("<[^>]+>"), " ")
        .removeMarkdownMarkup()
        .replace("•", "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '*')
}

private fun String.removeMarkdownMarkup(): String =
    replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace(Regex("\\[(.*?)]\\([^)]*\\)"), "$1")

private fun String.inferColumnType(): String {
    return when (normalizedAiKey()) {
        "number", "nombor", "count", "amount", "jumlah", "harga", "price", "cost", "total" -> "Number"
        "status", "state", "phase", "fasa" -> "Status"
        "date", "tarikh", "deadline", "due", "time", "waktu", "masa" -> "Date"
        "done", "complete", "completed", "checkbox", "siap" -> "Checkbox"
        else -> "Text"
    }
}

private fun String.normalizedAiKey(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
        .trim()

private fun String.toReadableTitle(): String =
    split(Regex("\\s+"))
        .filter { word -> word.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { char -> char.titlecase() }
        }

private fun String.withoutMentionContext(): String =
    substringBefore("CYL_MENTION_CONTEXT:").trim()
