package com.changeyourlife.cyl.backend.service

import com.changeyourlife.cyl.backend.model.ai.AiBlockContext
import com.changeyourlife.cyl.backend.model.ai.AiPageContext
import com.changeyourlife.cyl.backend.model.ai.AiActionValidationIssue
import java.time.LocalDate

private typealias AiActionItem = AiService.AiActionItem
private typealias AiActionResult = AiService.AiActionResult
private typealias AiTableColumnItem = AiService.AiTableColumnItem

class AiPromptActionRecovery(
    private val actionSchemaValidator: AiActionSchemaValidator = AiActionSchemaValidator(),
) {
    private data class AiActionSchemaValidation(
        val actions: List<AiActionItem>,
        val issues: List<AiActionValidationIssue>,
    )

    private fun List<AiActionItem>.validatedForActionSchema(): AiActionSchemaValidation {
        val validation = actionSchemaValidator.validate(this)
        return AiActionSchemaValidation(
            actions = validation.actions,
            issues = validation.issues,
        )
    }

    fun recoverActionFromPrompt(
        prompt: String,
        pages: List<AiPageContext>,
    ): AiActionResult? {
        if (prompt.isBlank()) return null
        val visiblePrompt = prompt.withoutMentionContext()
        visiblePrompt.recoverCreatePageAction(
            allowHomeTablePage = !prompt.hasMentionContext(),
        )?.let { action ->
            val validation = listOf(action).validatedForActionSchema()
            return AiActionResult(
                reply = visiblePrompt.recoveryReply(
                    malay = validation.actions.ifEmpty { listOf(action) }.recoveredMalayReply(),
                    english = validation.actions.ifEmpty { listOf(action) }.recoveredEnglishReply(),
                ),
                actions = validation.actions,
                validationIssues = validation.issues,
            )
        }
        val targetPage = pages.findTargetPage(prompt)
            ?: pages.findTargetPage(visiblePrompt)
            ?: return null

        val recoveredActions = visiblePrompt.recoverStructuredActions(targetPage)
        if (recoveredActions.isNotEmpty()) {
            val validation = recoveredActions.validatedForActionSchema()
            return AiActionResult(
                reply = visiblePrompt.recoveryReply(
                    malay = validation.actions.ifEmpty { recoveredActions }.recoveredMalayReply(),
                    english = validation.actions.ifEmpty { recoveredActions }.recoveredEnglishReply(),
                ),
                actions = validation.actions,
                validationIssues = validation.issues,
            )
        }

        val fallbackActions = listOfNotNull(
            visiblePrompt.recoverBlockAction(targetPage)
                ?: visiblePrompt.recoverTableRowAction(targetPage)
                ?: visiblePrompt.recoverPropertyAction(targetPage)
                ?: visiblePrompt.recoverTableRenameAction(targetPage)
                ?: visiblePrompt.recoverDatabaseAction(targetPage)
                ?: visiblePrompt.takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage),
        )
        if (fallbackActions.isEmpty()) return null
        val validation = fallbackActions.validatedForActionSchema()
        return AiActionResult(
            reply = visiblePrompt.recoveryReply(
                malay = validation.actions.ifEmpty { fallbackActions }.recoveredMalayReply(),
                english = validation.actions.ifEmpty { fallbackActions }.recoveredEnglishReply(),
            ),
            actions = validation.actions,
            validationIssues = validation.issues,
        )
    }

    private fun String.recoverStructuredActions(targetPage: AiPageContext): List<AiActionItem> {
        val actions = mutableListOf<AiActionItem>()
        var createdTableTitle: String? = null
        actionSegments().forEach { segment ->
            val rowAction = segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
            if (rowAction == null &&
                segment.looksLikeTableRowRequest() &&
                createdTableTitle.isNullOrBlank() &&
                !targetPage.hasAnyTable()
            ) {
                val tableAction = segment.recoverImplicitDatabaseAction(targetPage)
                createdTableTitle = tableAction.tableTitle
                actions += tableAction
                segment.recoverTableRowAction(targetPage, fallbackTableTitle = createdTableTitle)
                    ?.let { action -> actions += action }
                return@forEach
            }

            val action = segment.recoverBlockAction(targetPage)
                ?: rowAction
                ?: segment.recoverPropertyAction(targetPage)
                ?: segment.recoverTableRenameAction(targetPage)
                ?: segment.recoverDatabaseAction(targetPage)
                ?: segment.takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage)
            if (action != null) {
                actions += action
                if (action.type.equals("CREATE_DATABASE", ignoreCase = true)) {
                    createdTableTitle = action.tableTitle.takeIf { title -> title.isNotBlank() }
                }
            }
        }
        return if (actions.isNotEmpty()) {
            actions
        } else {
            listOfNotNull(
                recoverBlockAction(targetPage)
                    ?: recoverTableRowAction(targetPage)
                    ?: recoverPropertyAction(targetPage)
                    ?: recoverTableRenameAction(targetPage)
                    ?: recoverDatabaseAction(targetPage)
                    ?: takeUnless { it.looksLikeTableContextOnlyRequest() }?.recoverWriteAction(targetPage),
            )
        }
    }

    private fun List<AiActionItem>.recoveredMalayReply(): String {
        if (size > 1) return "Siap - saya buat perubahan itu."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Siap - saya padam semua block dalam page itu."
            "DELETE_BLOCK" -> "Siap - saya buang block itu."
            "UPDATE_BLOCK" -> "Siap - saya ubah block itu."
            "ADD_TABLE_ROW" -> "Siap - saya tambah row itu."
            "CREATE_DATABASE" -> "Siap - saya buat table itu."
            "CREATE_PAGE" -> "Siap - saya buat page itu."
            "DELETE_PROPERTY" -> "Siap - saya padam property itu."
            "UPDATE_PROPERTY" -> "Siap - saya ubah property itu."
            "ADD_PROPERTY" -> "Siap - saya tambah property itu."
            else -> "Siap - saya buat perubahan itu."
        }
    }

    private fun List<AiActionItem>.recoveredEnglishReply(): String {
        if (size > 1) return "Done - I made those changes."
        return when (singleOrNull()?.type) {
            "DELETE_ALL_BLOCKS" -> "Done - I deleted all blocks in that page."
            "DELETE_BLOCK" -> "Done - I deleted that block."
            "UPDATE_BLOCK" -> "Done - I updated that block."
            "ADD_TABLE_ROW" -> "Done - I added that row."
            "CREATE_DATABASE" -> "Done - I created that table."
            "CREATE_PAGE" -> "Done - I created that page."
            "DELETE_PROPERTY" -> "Done - I deleted that property."
            "UPDATE_PROPERTY" -> "Done - I updated that property."
            "ADD_PROPERTY" -> "Done - I added that property."
            else -> "Done - I made that change."
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

    private fun String.hasMentionContext(): Boolean =
        contains("CYL_MENTION_CONTEXT:", ignoreCase = true)

    private fun String.recoverWriteAction(targetPage: AiPageContext): AiActionItem? {
        if (!looksLikePageWriteRequest()) return null
        val content = extractWriteContent(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "APPEND_BLOCK",
            targetTitle = targetPage.title,
            blockType = inferBlockType(),
            content = content,
        )
    }

    private fun String.recoverPropertyAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val mentionsProperty = listOf("property", "properties", "prop").any { token -> value.contains(token) }
        if (!mentionsProperty) return null

        val actionType = when {
            listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) } -> "DELETE_PROPERTY"
            listOf("ubah", "tukar", "edit", "update", "set", "jadikan", "change").any { token -> value.contains(token) } -> "UPDATE_PROPERTY"
            listOf("tambah", "add", "create", "buat", "masukkan", "letak").any { token -> value.contains(token) } -> "ADD_PROPERTY"
            else -> return null
        }
        val propertyName = extractPropertyName(targetPage.title)
        if (propertyName.isBlank()) return null

        return AiActionItem(
            type = actionType,
            targetTitle = targetPage.title,
            propertyName = propertyName,
            propertyType = inferPropertyType(),
            value = extractPropertyValue(targetPage.title),
        )
    }

    private fun String.recoverDatabaseAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        if (looksLikeTableRowRequest()) return null
        val isTableContext = looksLikeTableContextOnlyRequest()
        val hasDatabaseIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasDatabaseIntent && !isTableContext) return null
        if (isTableContext && !hasDatabaseIntent && targetPage.hasAnyTable()) return null
        val isCreate = listOf("buat", "create", "cipta", "tambah", "add", "masukkan", "letak").any { token ->
            value.contains(token)
        } || isTableContext
        if (!isCreate) return null

        val tableTitle = extractTableTitle(targetPage.title).ifBlank { "Table" }
        return AiActionItem(
            type = "CREATE_DATABASE",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(value),
        )
    }

    private fun String.recoverImplicitDatabaseAction(targetPage: AiPageContext): AiActionItem {
        val tableTitle = when {
            looksLikeExpenseText() -> targetPage.title.ifBlank { "Belanja" }
            else -> targetPage.title.ifBlank { "Table" }
        }
        return AiActionItem(
            type = "CREATE_DATABASE",
            targetTitle = targetPage.title,
            tableTitle = tableTitle,
            tableView = "Table",
            tableColumns = tableTitle.defaultRecoveredTableColumns(lowercase()),
        )
    }

    private fun String.recoverCreatePageAction(
        allowHomeTablePage: Boolean,
    ): AiActionItem? {
        val value = lowercase()
        val hasCreateIntent = listOf("buat", "buatkan", "create", "cipta", "add", "tambah").any { token ->
            value.contains(token)
        }
        val hasPageIntent = listOf("page", "halaman").any { token -> value.contains(token) }
        val hasNewIntent = listOf("baru", "new").any { token -> value.contains(token) }
        val isExpensePage = looksLikeMonthlyExpensePage()
        val isHomeTablePage = allowHomeTablePage && looksLikeHomeTablePageRequest()
        val isExplicitPageCreate = hasCreateIntent && hasPageIntent && (hasNewIntent || isExpensePage)
        if (!isExplicitPageCreate && !isHomeTablePage) return null
        if (listOf("subpage", "sub page", "sub-page").any { token -> value.contains(token) }) return null

        val pageTitle = when {
            isExpensePage -> listOfNotNull(extractRequestedMonthName(), "Monthly Expenses").joinToString(" ")
            isHomeTablePage -> extractHomeTablePageTitle().ifBlank { "Untitled page" }
            else -> extractCreatePageTitle().ifBlank { "Untitled page" }
        }
        val salaryAmount = extractSalaryAmount()
        return AiActionItem(
            type = "CREATE_PAGE",
            title = pageTitle,
            tableTitle = when {
                isExpensePage -> "Monthly Expenses"
                isHomeTablePage -> pageTitle
                else -> ""
            },
            tableView = "Table",
            tableColumns = when {
                isExpensePage -> monthlyExpenseTableColumns()
                isHomeTablePage -> pageTitle.defaultRecoveredTableColumns(value)
                else -> emptyList()
            },
            tableRows = if (isExpensePage && salaryAmount != null) {
                listOf(
                    mapOf(
                        "Category" to "Salary",
                        "Budget" to salaryAmount,
                        "Actual" to salaryAmount,
                        "Variance" to "0",
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun String.looksLikeHomeTablePageRequest(): Boolean {
        val value = lowercase()
        val hasCreateIntent = listOf("buat", "buatkan", "create", "cipta", "add", "tambah").any { token ->
            value.contains(token)
        }
        val hasTableIntent = listOf(
            "table",
            "database",
            "jadual",
            "tracker",
            "tracking",
            "rekod",
            "record",
        ).any { token -> value.contains(token) }
        return hasCreateIntent && hasTableIntent && !referencesExistingPageTarget()
    }

    private fun String.referencesExistingPageTarget(): Boolean {
        val value = lowercase()
        return contains("@") ||
            listOf(
                "dalam page",
                "dekat page",
                "di page",
                "ke page",
                "page ini",
                "current page",
                "this page",
                "sini",
                "tersebut",
            ).any { token -> value.contains(token) }
    }

    private fun String.extractHomeTablePageTitle(): String {
        return removeTargetMention("")
            .replace(
                Regex(
                    "(?i)\\b(tolong|please|buatkan|buat|create|cipta|add|tambah|table|database|jadual|baru|new|untuk|for|punya|dengan|with)\\b",
                ),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',', '.')
            .toReadableTitle()
    }

    private fun String.looksLikeMonthlyExpensePage(): Boolean {
        val value = lowercase()
        val hasMonthly = listOf("monthly", "bulanan", "bulan").any { token -> value.contains(token) }
        val hasExpense = listOf(
            "expense",
            "expenses",
            "budget",
            "bajet",
            "belanja",
            "perbelanjaan",
            "pengeluaran",
            "duit",
            "kewangan",
            "finance",
            "spending",
        ).any { token -> value.contains(token) }
        return hasMonthly && hasExpense
    }

    private fun String.extractRequestedMonthName(): String? {
        val monthNumber = Regex("(?i)\\b(?:bulan|month)\\s*(\\d{1,2})\\b")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (monthNumber != null) return monthNumber.toMonthNameOrNull()
        val value = lowercase()
        return when {
            "january" in value || "januari" in value -> "January"
            "february" in value || "februari" in value -> "February"
            "march" in value || "mac" in value -> "March"
            "april" in value -> "April"
            "may" in value || "mei" in value -> "May"
            "june" in value || "jun" in value -> "June"
            "july" in value || "julai" in value -> "July"
            "august" in value || "ogos" in value -> "August"
            "september" in value -> "September"
            "october" in value || "oktober" in value -> "October"
            "november" in value -> "November"
            "december" in value || "disember" in value -> "December"
            else -> null
        }
    }

    private fun Int.toMonthNameOrNull(): String? = when (this) {
        1 -> "January"
        2 -> "February"
        3 -> "March"
        4 -> "April"
        5 -> "May"
        6 -> "June"
        7 -> "July"
        8 -> "August"
        9 -> "September"
        10 -> "October"
        11 -> "November"
        12 -> "December"
        else -> null
    }

    private fun String.extractSalaryAmount(): String? {
        return Regex("(?i)\\b(?:gaji|salary|income)\\s*(?:rm\\s*)?([0-9][0-9\\s.,_]*[0-9]|\\d)\\b")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("[\\s_]"), "")
            ?.replace(',', '.')
            ?.takeIf { value -> value.isNotBlank() }
    }

    private fun monthlyExpenseTableColumns(): List<AiTableColumnItem> = listOf(
        AiTableColumnItem(name = "Category", type = "Text"),
        AiTableColumnItem(name = "Budget", type = "Number"),
        AiTableColumnItem(name = "Actual", type = "Number"),
        AiTableColumnItem(name = "Variance", type = "Number"),
        AiTableColumnItem(name = "Notes", type = "Text"),
    )

    private fun String.extractCreatePageTitle(): String {
        return removeTargetMention("")
            .replace(
                Regex("(?i)\\b(tolong|please|buatkan|buat|create|cipta|add|tambah|page|halaman|baru|new|untuk|for|punya|dengan|with)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',', '.')
            .toReadableTitle()
    }

    private fun String.recoverTableRenameAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val hasTableIntent = listOf("table", "database", "jadual").any { token -> value.contains(token) }
        if (!hasTableIntent || !targetPage.hasAnyTable()) return null
        val hasRenameIntent = listOf("rename", "ubah nama", "tukar nama", "ganti nama", "change name", "jadikan nama")
            .any { token -> value.contains(token) } ||
            (
                listOf("ubah", "tukar", "ganti", "jadikan", "change", "set").any { token -> value.contains(token) } &&
                    listOf("nama", "name", "title").any { token -> value.contains(token) }
                )
        if (!hasRenameIntent) return null
        val newTitle = extractNewTableTitle(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "RENAME_TABLE",
            title = newTitle,
            targetTitle = targetPage.title,
        )
    }

    private fun String.recoverTableRowAction(
        targetPage: AiPageContext,
        fallbackTableTitle: String? = null,
    ): AiActionItem? {
        if (!looksLikeTableRowRequest()) return null
        val tableTitle = targetPage.defaultTableTitle()
            ?: fallbackTableTitle?.takeIf { title -> title.isNotBlank() }
        if (tableTitle == null && !targetPage.hasAnyTable()) return null

        val rowPrompt = bestTableRowSegment()
        val rowText = rowPrompt.extractTableRowText(targetPage.title)
        if (rowText.isBlank()) return null
        val amount = rowText.extractMoneyAmount()
        val rowTitle = rowText.removeMoneyAmount().removeMetricRequestWords().removeDateRequestWords().ifBlank { rowText }
        val dateValue = rowPrompt.inferredDateValue()
        val cellValues = buildMap {
            put("Name", rowTitle)
            put("Item", rowTitle)
            amount?.let { value ->
                put("Amount", value)
                put("Jumlah", value)
                put("Harga", value)
                put("Price", value)
                put("Cost", value)
                put("Total", value)
            }
            dateValue?.let { value ->
                put("Date", value)
                put("Tarikh", value)
            }
            if (amount != null && !rowText.equals(rowTitle, ignoreCase = true)) {
                put("Notes", rowText)
            }
        }
        return AiActionItem(
            type = "ADD_TABLE_ROW",
            targetTitle = targetPage.title,
            tableTitle = tableTitle.orEmpty(),
            rowTitle = rowTitle,
            cellValues = cellValues,
        )
    }

    private fun String.recoverBlockAction(targetPage: AiPageContext): AiActionItem? {
        val value = lowercase()
        val hasBlockIntent = listOf(
            "block",
            "blok",
            "heading",
            "tajuk",
            "todo",
            "checklist",
            "quote",
            "petikan",
            "divider",
            "media",
            "file",
            "gambar",
        ).any { token -> value.contains(token) }
        if (!hasBlockIntent) return null

        val isDelete = listOf("padam", "buang", "hapus", "delete", "remove").any { token -> value.contains(token) }
        if (isDelete) {
            if (requestsAllBlocksDeletion()) {
                return AiActionItem(
                    type = "DELETE_ALL_BLOCKS",
                    targetTitle = targetPage.title,
                )
            }
            val reference = extractBlockReference(targetPage.title)
            val block = targetPage.findMatchingBlock(inferBlockType(), reference)
            return AiActionItem(
                type = "DELETE_BLOCK",
                targetTitle = targetPage.title,
                blockId = block?.id.orEmpty(),
                blockType = block?.type ?: inferBlockType(),
                blockText = reference.ifBlank { block?.text.orEmpty() },
                content = reference.ifBlank { block?.text.orEmpty() },
                tableTitle = block?.tableTitle.orEmpty(),
            )
        }

        val isUpdate = listOf("ubah", "tukar", "edit", "update", "ganti", "jadikan", "change").any { token ->
            value.contains(token)
        }
        if (isUpdate) {
            val replacement = splitBlockReplacement(targetPage.title) ?: return null
            val block = targetPage.findMatchingBlock(inferBlockType(), replacement.first)
            return AiActionItem(
                type = "UPDATE_BLOCK",
                targetTitle = targetPage.title,
                blockId = block?.id.orEmpty(),
                blockType = block?.type ?: inferBlockType(),
                blockText = replacement.first.ifBlank { block?.text.orEmpty() },
                content = replacement.second,
            )
        }

        val isAppend = listOf("tambah", "add", "buat", "masukkan", "letak", "tulis", "catat", "append").any { token ->
            value.contains(token)
        }
        if (!isAppend) return null
        val content = extractBlockReference(targetPage.title).ifBlank { return null }
        return AiActionItem(
            type = "APPEND_BLOCK",
            targetTitle = targetPage.title,
            blockType = inferBlockType(),
            content = content,
        )
    }

    private fun AiPageContext.findMatchingBlock(
        requestedType: String,
        reference: String,
    ): AiBlockContext? {
        val normalizedReference = reference.normalizeForAiMatch()
        val candidates = blocks.filterNot { block -> block.type.equals("Property", ignoreCase = true) }
        if (normalizedReference.isNotBlank()) {
            candidates.firstOrNull { block ->
                val blockText = block.text.normalizeForAiMatch()
                blockText.isNotBlank() &&
                    (blockText.contains(normalizedReference) || normalizedReference.contains(blockText))
            }?.let { return it }

            candidates.firstOrNull { block ->
                val tableTitle = block.tableTitle.normalizeForAiMatch()
                tableTitle.isNotBlank() && tableTitle.contains(normalizedReference)
            }?.let { return it }
        }

        val normalizedType = requestedType.normalizeForAiMatch()
        return candidates.firstOrNull { block ->
            block.type.normalizeForAiMatch() == normalizedType ||
                block.type.equals(requestedType, ignoreCase = true)
        }
    }

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

    private fun String.extractTableTitle(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(buat|create|cipta|tambah|add|masukkan|letak|untuk|catat|rekod|record|table|database|jadual|dalam|dekat|di|page|ini|sini|this|current)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractNewTableTitle(pageTitle: String): String {
        val cleaned = removeTargetMention(pageTitle)
            .replace(Regex("(?i)\\b(ubah|tukar|ganti|jadikan|change|set|rename|nama|name|title|table|database|jadual)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val afterMarker = listOf(
            "dengan nama ",
            "kepada ",
            "menjadi ",
            "jadikan ",
            "dengan ",
            "jadi ",
            "to ",
            "into ",
            "as ",
            " dengan nama ",
            " kepada ",
            " menjadi ",
            " jadikan ",
            " dengan ",
            " jadi ",
            " to ",
            " into ",
            " as ",
        ).firstNotNullOfOrNull { marker ->
            cleaned.substringAfter(marker, missingDelimiterValue = "")
                .takeIf { value -> value.isNotBlank() }
        } ?: cleaned

        val title = afterMarker
            .replace(Regex("(?i)\\b(yang|baru|new)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\s+(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(pendek|short|ringkas|simple)\\b"), " ")
            .replace(Regex("(?i)\\b(dan|and)\\b\\s*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '"', '\'')
        return title.takeUnless { candidate -> candidate.isQualitativeTableTitleRequest() }.orEmpty()
    }

    private fun String.isQualitativeTableTitleRequest(): Boolean {
        val normalized = lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isBlank()) return true
        val descriptorWords = setOf(
            "sesuai",
            "sensuai",
            "sesual",
            "sensual",
            "appropriate",
            "suitable",
            "better",
            "nice",
            "good",
            "bagus",
            "kemas",
            "cantik",
            "proper",
            "short",
            "simple",
            "ringkas",
            "pendek",
        )
        return normalized.split(Regex("\\s+")).all { word -> word in descriptorWords }
    }

    private fun String.defaultRecoveredTableColumns(promptValue: String): List<AiTableColumnItem> =
        if (looksLikeExpenseText() || promptValue.looksLikeExpenseText()) {
            listOf(
                AiTableColumnItem(name = "Name", type = "Text"),
                AiTableColumnItem(name = "Amount", type = "Number"),
                AiTableColumnItem(name = "Date", type = "Date"),
                AiTableColumnItem(name = "Notes", type = "Text"),
            )
        } else {
            listOf(
                AiTableColumnItem(name = "Name", type = "Text"),
                AiTableColumnItem(name = "Status", type = "Status"),
                AiTableColumnItem(name = "Notes", type = "Text"),
            )
        }

    private fun String.looksLikeExpenseText(): Boolean {
        val value = lowercase()
        return extractMoneyAmount() != null ||
            listOf(
                "duit",
                "belanja",
                "perbelanjaan",
                "pengeluaran",
                "expense",
                "expenses",
                "budget",
                "bajet",
                "kewangan",
                "finance",
                "spend",
                "makan",
                "ringgit",
                "rm",
                "harga",
                "jumlah",
            )
                .any { token -> value.contains(token) }
    }

    private fun AiPageContext.hasAnyTable(): Boolean =
        blocks.any { block ->
            block.type.equals("DatabaseTable", ignoreCase = true) || block.tableTitle.isNotBlank()
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

    private fun String.looksLikeTaskRowRequest(): Boolean {
        val value = lowercase()
        return listOf("task", "todo", "reminder", "ingatkan", "deadline", "due", "appointment", "jadualkan")
            .any { token -> value.contains(token) }
    }

    private fun String.looksLikeTableContextOnlyRequest(): Boolean {
        val value = lowercase()
        val hasContextIntent = listOf("catat", "rekod", "record", "track", "tracking").any { token ->
            value.contains(token)
        } &&
            listOf("duit", "belanja", "expense", "expenses", "spending").any { token ->
                value.contains(token)
            } &&
            listOf("bulanan", "monthly", "bulan", "harian", "daily").any { token ->
                value.contains(token)
            }
        return hasContextIntent && !looksLikeTableRowRequest()
    }

    private fun String.extractTableRowText(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(tambah|add|masukkan|letak|insert|create|buat|catat|satu|1|row|baris|rekod|record|dalam|dekat|di|ke|to|into|table|database|jadual|page|ini|tersebut|yang|saya|guna|pakai|beli|belikan|buy|purchase|purchased|spent|spend|use|used|untuk|tu|dah|sekali)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractMoneyAmount(): String? {
        val match = Regex("(?i)(?:rm\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:ringgit|rm)?")
            .find(this)
            ?: return null
        return match.groupValues.getOrNull(1)
            ?.replace(',', '.')
            ?.takeIf { value -> value.isNotBlank() }
    }

    private fun String.removeMoneyAmount(): String =
        replace(Regex("(?i)\\b(?:rm\\s*)?\\d+(?:[.,]\\d+)?\\s*(?:ringgit|rm)?\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.removeMetricRequestWords(): String =
        replace(Regex("(?i)\\b(amount|jumlah|harga|price|cost|total|nilai|value)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.removeDateRequestWords(): String =
        replace(Regex("(?i)\\b(hari\\s*ini|harini|today|tarikh|date|sekali)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', ',')

    private fun String.inferredDateValue(): String? {
        val value = lowercase()
        val wantsToday = listOf("hari ini", "harini", "today").any { token -> value.contains(token) }
        val wantsDate = wantsToday || listOf("tarikh", "date").any { token -> value.contains(token) }
        return if (wantsDate) LocalDate.now().toString() else null
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

    private fun String.bestTableRowSegment(): String =
        actionSegments()
            .lastOrNull { segment -> segment.looksLikeTableRowRequest() }
            ?: this

    private fun String.requestsAllBlocksDeletion(): Boolean {
        val value = lowercase()
        val hasDeleteIntent = listOf("padam", "buang", "hapus", "delete", "remove", "clear", "kosongkan")
            .any { token -> value.contains(token) }
        val hasAllIntent = listOf("semua", "all", "every", "keseluruhan", "seluruh")
            .any { token -> value.contains(token) }
        val hasBlockIntent = listOf("block", "blok", "blocks")
            .any { token -> value.contains(token) }
        return hasDeleteIntent && hasAllIntent && hasBlockIntent
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
            "tambah nota",
            "nota",
            "note",
            "isi",
            "content",
            "memo",
            "buat isi",
            "draft",
            "karangan",
        ).any { token -> value.contains(token) }
        if (!hasWriteIntent) return false

        val nonWriteIntent = listOf(
            "delete",
            "hapus",
            "buang",
            "rename",
            "table",
            "database",
            "row",
            "column",
            "property",
            "block",
            "blok",
        ).any { token -> value.contains(token) }
        return !nonWriteIntent
    }

    private fun String.extractWriteContent(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(Regex("(?i)\\b(tolong|please|buat|create|write|tulis|catat|masukkan|insert|append|draft|nota|note|memo|isi|content|buat isi|tambah nota|add note)\\b"), " ")
            .replace(Regex("(?i)\\b(dalam|dekat|di|ke|to|into|page|ini|sini|this page|current page)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.extractPropertyName(pageTitle: String): String {
        val typeWords = setOf(
            "text",
            "number",
            "nombor",
            "select",
            "multi",
            "multiselect",
            "status",
            "date",
            "tarikh",
            "person",
            "files",
            "media",
            "checkbox",
            "url",
            "email",
            "phone",
            "telefon",
            "formula",
            "relation",
            "rollup",
            "button",
            "place",
            "tempat",
            "id",
        )
        val cleaned = removeTargetMention(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(tambah|add|create|buat|masukkan|letak|ubah|tukar|edit|update|set|jadikan|change|padam|buang|hapus|delete|remove|property|properties|prop|type|jenis|dalam|dekat|di|page|ini|sini|this|current)\\b",
                ),
                " ",
            )
            .replace(Regex("(?i)\\b(kepada|ke|as|sebagai|value|nilai|with|dengan)\\b.*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

        return cleaned
            .split(" ")
            .filter { token -> token.isNotBlank() && token.lowercase() !in typeWords }
            .joinToString(" ")
            .trim()
    }

    private fun String.extractPropertyValue(pageTitle: String): String {
        val withoutMention = removeTargetMention(pageTitle)
        val match = Regex("(?i)\\b(value|nilai|kepada|ke|as|sebagai|dengan|with)\\b\\s+(.+)$")
            .find(withoutMention)
            ?: return ""
        return match.groupValues.getOrNull(2)
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
    }

    private fun String.inferPropertyType(): String {
        val value = lowercase()
        return when {
            value.contains("number") || value.contains("nombor") || value.contains("jumlah") || value.contains("harga") -> "Number"
            value.contains("multi-select") || value.contains("multiselect") || value.contains("multi select") -> "MultiSelect"
            value.contains("select") -> "Select"
            value.contains("status") -> "Status"
            value.contains("date") || value.contains("tarikh") || value.contains("deadline") || value.contains("due") -> "Date"
            value.contains("person") || value.contains("orang") -> "Person"
            value.contains("file") || value.contains("media") || value.contains("gambar") -> "FilesMedia"
            value.contains("checkbox") || value.contains("check") || value.contains("tick") || value.contains("siap") -> "Checkbox"
            value.contains("url") || value.contains("link") -> "Url"
            value.contains("email") -> "Email"
            value.contains("phone") || value.contains("telefon") -> "Phone"
            value.contains("formula") || value.contains("kira") -> "Formula"
            value.contains("relation") || value.contains("hubungan") -> "Relation"
            value.contains("rollup") -> "Rollup"
            value.contains("button") -> "Button"
            value.contains("place") || value.contains("tempat") || value.contains("lokasi") -> "Place"
            value.contains("id") -> "Id"
            else -> "Text"
        }
    }

    private fun String.extractBlockReference(pageTitle: String): String =
        removeTargetMention(pageTitle)
            .replace(
                Regex(
                    "(?i)\\b(tambah|add|buat|masukkan|letak|tulis|catat|padam|buang|hapus|delete|remove|block|blok|text|heading|tajuk|todo|checklist|quote|petikan|divider|garisan|media|file|gambar|dalam|dekat|di|page|ini|sini|this|current)\\b",
                ),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')

    private fun String.splitBlockReplacement(pageTitle: String): Pair<String, String>? {
        val cleaned = removeTargetMention(pageTitle)
            .replace(
                Regex("(?i)\\b(ubah|tukar|edit|update|ganti|jadikan|change|block|blok|text|heading|tajuk|todo|checklist|quote|petikan|divider|garisan|media|file|gambar|dalam|dekat|di|page|ini|sini|this|current)\\b"),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':')
        val parts = Regex("(?i)\\s+\\b(kepada|ke|jadi|menjadi|dengan|to|into|with)\\b\\s+")
            .split(cleaned, limit = 2)
        if (parts.size != 2) return null
        val from = parts[0].trim(' ', '-', ':')
        val to = parts[1].trim(' ', '-', ':')
        if (from.isBlank() || to.isBlank()) return null
        return from to to
    }

    private fun String.inferBlockType(): String {
        val value = lowercase()
        return when {
            value.contains("plain table") || value.contains("table biasa") || value.contains("jadual biasa") -> "Table"
            value.contains("database") || value.contains("table") || value.contains("jadual") -> "DatabaseTable"
            value.contains("heading") || value.contains("tajuk") -> "Heading"
            value.contains("numbered") || value.contains("ordered") -> "Numbered"
            value.contains("toggle") -> "Toggle"
            value.contains("callout") -> "Callout"
            value.contains("code") || value.contains("kod") -> "Code"
            value.contains("bookmark") -> "WebBookmark"
            value.contains("bullet") || value.contains("list") || value.contains("senarai") -> "Bullet"
            value.contains("quote") || value.contains("petikan") -> "Quote"
            value.contains("todo") || value.contains("checklist") -> "Todo"
            value.contains("divider") || value.contains("garisan") || value.contains("line") -> "Divider"
            value.contains("media") || value.contains("file") || value.contains("gambar") -> "MediaFile"
            else -> "Text"
        }
    }

    private fun String.removeTargetMention(pageTitle: String): String =
        replace("@$pageTitle", "", ignoreCase = true)
            .replace(Regex("@[^\\s,.;:]+"), " ")

    private fun String.normalizeForAiMatch(): String =
        lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.toReadableTitle(): String =
        split(Regex("\\s+"))
            .filter { word -> word.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { char -> char.titlecase() }
            }

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
            "boleh",
            "nak",
            "tulis",
            "catat",
            "tambah",
            "masukkan",
            "ubah",
            "tukar",
            "padam",
            "buang",
            "hapus",
            "dekat",
            "dalam",
            "sini",
            "page ini",
            "tarikh",
        ).any { token -> value.contains(token) }
    }


}
