package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class AiModelActionNormalizer(
    private val actionSchemaValidator: AiActionSchemaValidator = AiActionSchemaValidator(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    },
) {
    fun recoverActionFromModelReply(
        reply: String,
        prompt: String,
        pages: List<AiPageContext>,
    ): AiService.AiActionResult? {
        if (reply.isBlank()) return null
        val cleaned = reply.cleanAiJson()
        val jsonElement = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() ?: return null
        val jsonObject = jsonElement as? JsonObject ?: return null

        runCatching { json.decodeFromString<AiActionEnvelope>(cleaned) }
            .getOrNull()
            ?.takeIf { envelope -> envelope.actions.isNotEmpty() }
            ?.let { envelope ->
                val normalizedActions = envelope.actions.map { action -> action.normalizedJsonAction(pages, prompt) }
                val validation = actionSchemaValidator.validate(normalizedActions)
                return AiService.AiActionResult(
                    reply = envelope.reply.ifBlank {
                        validation.actions.ifEmpty { normalizedActions }.recoveredReplyForPrompt(prompt)
                    },
                    actions = validation.actions,
                    validationIssues = validation.issues,
                )
            }

        runCatching { json.decodeFromString<AiService.AiActionItem>(cleaned) }
            .getOrNull()
            ?.takeIf { action -> action.type.isNotBlank() }
            ?.let { action ->
                val normalized = action.normalizedJsonAction(pages, prompt)
                val validation = actionSchemaValidator.validate(listOf(normalized))
                return AiService.AiActionResult(
                    reply = listOf(normalized).recoveredReplyForPrompt(prompt),
                    actions = validation.actions,
                    validationIssues = validation.issues,
                )
            }

        return jsonObject.toLegacyActionResult(prompt = prompt, pages = pages)
    }

    private fun AiService.AiActionItem.normalizedJsonAction(
        pages: List<AiPageContext>,
        prompt: String,
    ): AiService.AiActionItem {
        val normalizedType = type.normalizedActionType()
        val explicitTarget = targetTitle.cleanAiPageTitle()
            .ifBlank { title.cleanAiPageTitle().takeIf { normalizedType != "CREATE_DATABASE" }.orEmpty() }
        val targetPage = pages.findPageByAiTitle(explicitTarget)
            ?: pages.findTargetPage(prompt)
        return copy(
            type = normalizedType,
            targetTitle = targetPage?.title ?: explicitTarget,
            tableTitle = tableTitle.ifBlank {
                if (normalizedType in tableRowActionTypes) targetPage?.defaultTableTitle().orEmpty() else ""
            },
        )
    }

    private fun JsonObject.toLegacyActionResult(
        prompt: String,
        pages: List<AiPageContext>,
    ): AiService.AiActionResult? {
        val action = stringValue("action").lowercase()
        if (action.isBlank()) return null
        val targetPage = pages.findPageByAiTitle(stringValue("page"))
            ?: pages.findPageByAiTitle(stringValue("targetPage"))
            ?: pages.findPageByAiTitle(stringValue("targetTitle"))
            ?: pages.findTargetPage(prompt)
            ?: return null
        val dataRows = legacyDataRows()
        if (dataRows.isEmpty()) return null

        val tableTitle = stringValue("table")
            .ifBlank { stringValue("tableTitle") }
            .ifBlank { targetPage.defaultTableTitle().orEmpty() }

        val actions = when (action) {
            "add", "append", "create", "insert", "update", "upsert", "set" -> {
                dataRows.mapNotNull { row ->
                    val rowTitle = row.legacyRowTitle()
                    val cellValues = row
                        .filterKeys { key -> key.normalizedLegacyKey() !in ignoredLegacyDataKeys }
                        .mapKeys { (key, _) -> key.toAiColumnName() }
                    if (rowTitle.isBlank() && cellValues.isEmpty()) {
                        null
                    } else {
                        AiService.AiActionItem(
                            type = "ADD_TABLE_ROW",
                            targetTitle = targetPage.title,
                            tableTitle = tableTitle,
                            rowTitle = rowTitle,
                            cellValues = cellValues,
                        )
                    }
                }
            }
            "delete", "remove", "padam", "buang" -> {
                dataRows.mapNotNull { row ->
                    row.legacyRowTitle().takeIf { rowTitle -> rowTitle.isNotBlank() }?.let { rowTitle ->
                        AiService.AiActionItem(
                            type = "DELETE_TABLE_ROW",
                            targetTitle = targetPage.title,
                            tableTitle = tableTitle,
                            rowTitle = rowTitle,
                        )
                    }
                }
            }
            else -> emptyList()
        }

        if (actions.isEmpty()) return null
        val validation = actionSchemaValidator.validate(actions)
        return AiService.AiActionResult(
            reply = actions.recoveredReplyForPrompt(prompt),
            actions = validation.actions,
            validationIssues = validation.issues,
        )
    }

    private fun List<AiService.AiActionItem>.recoveredReplyForPrompt(prompt: String): String =
        prompt.withoutMentionContext().recoveryReply(
            malay = recoveredMalayReply(),
            english = recoveredEnglishReply(),
        )

    private fun List<AiService.AiActionItem>.recoveredMalayReply(): String {
        if (size > 1) return "Siap - saya buat perubahan itu."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Siap - saya padam semua block dalam page itu."
            "DELETE_BLOCK" -> "Siap - saya buang block itu."
            "UPDATE_BLOCK" -> "Siap - saya ubah block itu."
            "ADD_TABLE_ROW" -> "Siap - saya tambah row itu."
            "CREATE_DATABASE" -> "Siap - saya buat table itu."
            "DELETE_PROPERTY" -> "Siap - saya padam property itu."
            "UPDATE_PROPERTY" -> "Siap - saya ubah property itu."
            "ADD_PROPERTY" -> "Siap - saya tambah property itu."
            else -> "Siap - saya buat perubahan itu."
        }
    }

    private fun List<AiService.AiActionItem>.recoveredEnglishReply(): String {
        if (size > 1) return "Done - I made those changes."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Done - I deleted all blocks in that page."
            "DELETE_BLOCK" -> "Done - I deleted that block."
            "UPDATE_BLOCK" -> "Done - I updated that block."
            "ADD_TABLE_ROW" -> "Done - I added that row."
            "CREATE_DATABASE" -> "Done - I created that table."
            "DELETE_PROPERTY" -> "Done - I deleted that property."
            "UPDATE_PROPERTY" -> "Done - I updated that property."
            "ADD_PROPERTY" -> "Done - I added that property."
            else -> "Done - I made that change."
        }
    }

    private fun JsonObject.legacyDataRows(): List<Map<String, String>> {
        val data = this["data"] ?: this["rows"] ?: this["row"]
        val explicitRows = when (data) {
            is JsonArray -> data.mapNotNull { item -> (item as? JsonObject)?.toPlainStringMap() }
            is JsonObject -> listOf(data.toPlainStringMap())
            else -> emptyList()
        }
        if (explicitRows.isNotEmpty()) return explicitRows

        val inlineRow = toPlainStringMap()
            .filterKeys { key -> key.normalizedLegacyKey() !in legacyEnvelopeKeys }
        return listOf(inlineRow).filter { row -> row.isNotEmpty() }
    }

    private fun JsonObject.toPlainStringMap(): Map<String, String> =
        entries.mapNotNull { (key, value) ->
            key.takeIf { it.isNotBlank() }?.let {
                it to value.plainString().trim()
            }
        }
            .filter { (_, value) -> value.isNotBlank() }
            .toMap()

    private fun JsonElement.plainString(): String =
        when (this) {
            JsonNull -> ""
            is JsonPrimitive -> contentOrNull ?: toString()
            is JsonArray, is JsonObject -> toString()
        }

    private fun JsonObject.stringValue(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun Map<String, String>.legacyRowTitle(): String {
        val preferredKeys = listOf("name", "item", "title", "task", "category", "description", "note", "notes")
        preferredKeys.firstNotNullOfOrNull { preferred ->
            entries.firstOrNull { (key, value) ->
                key.normalizedLegacyKey() == preferred && value.isNotBlank()
            }?.value
        }?.let { return it }
        return entries.firstOrNull { (key, value) ->
            key.normalizedLegacyKey() !in ignoredLegacyDataKeys && value.isNotBlank()
        }?.value.orEmpty()
    }

    private fun List<AiPageContext>.findPageByAiTitle(rawTitle: String): AiPageContext? {
        val normalized = rawTitle.cleanAiPageTitle().normalizeForAiMatch()
        if (normalized.isBlank()) return null
        return firstOrNull { page -> page.id == rawTitle.trim() }
            ?: firstOrNull { page -> page.title.normalizeForAiMatch() == normalized }
            ?: firstOrNull { page ->
                val title = page.title.normalizeForAiMatch()
                title.isNotBlank() && (title.contains(normalized) || normalized.contains(title))
            }
    }

    private fun List<AiPageContext>.findTargetPage(prompt: String): AiPageContext? {
        prompt.extractMentionContextPageIds().firstNotNullOfOrNull { pageId ->
            firstOrNull { page -> page.id == pageId }
        }?.let { return it }

        val normalizedPrompt = prompt.lowercase()
        val pagesWithTitle = filter { page -> page.title.isNotBlank() }
            .sortedByDescending { page -> page.title.length }

        pagesWithTitle.firstOrNull { page ->
            normalizedPrompt.contains("@${page.title.lowercase()}")
        }?.let { return it }

        val mention = Regex("@([^\\n,.;:]+)")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (mention.isNotBlank()) {
            pagesWithTitle.firstOrNull { page ->
                val title = page.title.lowercase()
                title == mention ||
                    title.startsWith(mention) ||
                    mention.startsWith(title) ||
                    title.contains(mention)
            }?.let { return it }
        }

        return singleOrNull()?.takeIf {
            prompt.looksLikePageMutationRequest() || prompt.looksLikeTableRowRequest()
        }
    }

    private fun AiPageContext.defaultTableTitle(): String? {
        blocks.firstOrNull { block ->
            block.type.equals("DatabaseTable", ignoreCase = true) && block.tableTitle.isNotBlank()
        }?.tableTitle?.let { return it }
        blocks.firstOrNull { block ->
            block.type.equals("DatabaseTable", ignoreCase = true)
        }?.text?.let { text ->
            Regex("(?i)title=([^;]+)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return blocks.firstOrNull { block -> block.tableTitle.isNotBlank() }?.tableTitle
    }

    private fun String.extractMentionContextPageIds(): List<String> {
        val context = substringAfter("CYL_MENTION_CONTEXT:", missingDelimiterValue = "")
        if (context.isBlank()) return emptyList()
        return Regex("\\bid=([^\\s]+)")
            .findAll(context)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().trim() }
            .filter { pageId -> pageId.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun String.withoutMentionContext(): String =
        substringBefore("CYL_MENTION_CONTEXT:").trim()

    private fun String.looksLikePageMutationRequest(): Boolean {
        val value = lowercase()
        val mutationIntent = listOf(
            "write",
            "tulis",
            "catat",
            "masukkan",
            "insert",
            "append",
            "tambah",
            "add",
            "buat",
            "draft",
            "ubah",
            "tukar",
            "edit",
            "update",
            "ganti",
            "padam",
            "buang",
            "hapus",
            "delete",
            "remove",
        ).any { token -> value.contains(token) }
        val targetHint = listOf(
            "@",
            "page ini",
            "current page",
            "this page",
            "sini",
            "dalam page",
            "dekat page",
            "property",
            "block",
            "blok",
        ).any { token -> value.contains(token) }
        return mutationIntent && targetHint
    }

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
            "write",
            "tulis",
            "catat",
            "masukkan",
            "insert",
            "append",
            "add note",
            "buat nota",
            "draft",
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

    private fun String.cleanAiPageTitle(): String =
        trim().removePrefix("@").trim()

    private fun String.toAiColumnName(): String =
        trim()
            .replace("_", " ")
            .replace("-", " ")
            .split(Regex("\\s+"))
            .filter { part -> part.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char -> char.uppercaseChar() }
            }

    private fun String.normalizedLegacyKey(): String =
        trim()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .lowercase()

    private fun String.normalizeForAiMatch(): String =
        lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.normalizedActionType(): String =
        trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

    private fun String.recoveryReply(
        malay: String,
        english: String,
    ): String = if (prefersMalayReply()) malay else english

    private fun String.prefersMalayReply(): Boolean {
        val value = lowercase()
        return listOf(
            "saya",
            "awak",
            "tolong",
            "buat",
            "ubah",
            "tukar",
            "padam",
            "buang",
            "tambah",
            "dalam",
            "dekat",
            "baris",
            "jadual",
            "tajuk",
            "tarikh",
            "ringgit",
        ).any { token -> value.contains(token) }
    }

    @Serializable
    private data class AiActionEnvelope(
        val reply: String = "",
        val actions: List<AiService.AiActionItem> = emptyList(),
    )

    private companion object {
        val tableRowActionTypes = setOf(
            "ADD_TABLE_ROW",
            "UPDATE_TABLE_ROW",
            "DELETE_TABLE_ROW",
            "UPDATE_TABLE_CELL",
        )
        val legacyEnvelopeKeys = setOf("page", "targetpage", "targettitle", "action", "data", "rows", "row", "table", "tabletitle")
        val ignoredLegacyDataKeys = setOf("id", "rowid", "row_id", "uuid")
    }
}
