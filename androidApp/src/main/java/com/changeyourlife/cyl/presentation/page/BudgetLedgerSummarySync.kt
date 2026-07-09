package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.normalizedForType
import java.math.BigDecimal

private val SummaryAggregateCategories = setOf("known expenses", "debt", "income", "balance")

internal fun PageBlockDocument.withBudgetLedgerSummarySynced(): PageBlockDocument {
    val transactionBlock = blocks.firstOrNull { block -> block.isTransactionLedgerTable() } ?: return this
    val summaryBlock = blocks.firstOrNull { block -> block.isMonthlySummaryTable() } ?: return this
    val ledger = transactionBlock.table.toLedgerRows()
    if (ledger.isEmpty()) return this

    val syncedTransactionTable = transactionBlock.table.normalizedTransactionTable(ledger)
    val syncedSummaryTable = summaryBlock.table.rebuiltMonthlySummary(ledger)
    if (syncedTransactionTable == transactionBlock.table && syncedSummaryTable == summaryBlock.table) return this

    return copy(
        blocks = blocks.map { block ->
            when (block.id) {
                transactionBlock.id -> block.copy(table = syncedTransactionTable)
                summaryBlock.id -> block.copy(table = syncedSummaryTable)
                else -> block
            }
        },
    )
}

internal fun PageBlock.isTransactionLedgerTable(): Boolean {
    if (type != PageBlockType.DatabaseTable) return false
    val title = table.title.normalizedBudgetKey()
    if (title == "transactions" || title == "transaction" || title == "transaksi") return true
    val columnNames = table.columns.map { column -> column.name.normalizedBudgetKey() }.toSet()
    return "amount" in columnNames &&
        "type" in columnNames &&
        ("category" in columnNames || "name" in columnNames)
}

private fun PageBlock.isMonthlySummaryTable(): Boolean {
    if (type != PageBlockType.DatabaseTable) return false
    val title = table.title.normalizedBudgetKey()
    if (title == "monthly summary" || title == "summary" || title == "ringkasan bulanan") return true
    val columnNames = table.columns.map { column -> column.name.normalizedBudgetKey() }.toSet()
    return "total" in columnNames && "category" in columnNames && "summary" in title
}

private data class LedgerRow(
    val name: String,
    val category: String,
    val type: String,
    val amount: BigDecimal,
    val status: String,
)

private fun PageTable.toLedgerRows(): List<LedgerRow> {
    return rows.mapNotNull { row ->
        val name = row.valueByColumnName(this, "Name")
            .ifBlank { row.valueByColumnName(this, "Item") }
        val category = row.valueByColumnName(this, "Category").ifBlank { name }
        val amount = row.valueByColumnName(this, "Amount")
            .ifBlank { row.valueByColumnName(this, "Jumlah") }
            .ifBlank { row.valueByColumnName(this, "Price") }
            .ifBlank { row.valueByColumnName(this, "Cost") }
            .toBudgetAmount()
            ?: return@mapNotNull null
        LedgerRow(
            name = name,
            category = category.ifBlank { "Other" }.toBudgetDisplayName(),
            type = row.valueByColumnName(this, "Type").ifBlank { category.inferredBudgetType(name) }.toBudgetDisplayName(),
            amount = amount,
            status = row.valueByColumnName(this, "Status").ifBlank { "Confirmed" }.toBudgetDisplayName(),
        )
    }
}

private fun PageTable.normalizedTransactionTable(ledger: List<LedgerRow>): PageTable {
    val categoryOptions = ledger.map { row -> row.category }
    val typeOptions = ledger.map { row -> row.type } + listOf("Expense", "Income", "Debt")
    val statusOptions = ledger.map { row -> row.status } + listOf("Confirmed", "Incomplete", "Empty")
    val nextColumns = columns.map { column ->
        when (column.name.normalizedBudgetKey()) {
            "category" -> column.withSelectOptions(categoryOptions)
            "type" -> column.withSelectOptions(typeOptions)
            "status" -> column.withSelectOptions(statusOptions)
            else -> column
        }
    }
    val nextRows = rows.map { row ->
        val name = row.valueByColumnName(this, "Name").ifBlank { row.valueByColumnName(this, "Item") }
        val category = row.valueByColumnName(this, "Category").ifBlank { name }.toBudgetDisplayName()
        val type = row.valueByColumnName(this, "Type").ifBlank { category.inferredBudgetType(name) }.toBudgetDisplayName()
        val amount = row.valueByColumnName(this, "Amount")
        val status = row.valueByColumnName(this, "Status").ifBlank {
            if (amount.isBlank()) "Empty" else "Confirmed"
        }.toBudgetDisplayName()
        row.withCellValue(nextColumns, "Category", category)
            .withCellValue(nextColumns, "Type", type)
            .withCellValue(nextColumns, "Status", status)
    }
    return copy(columns = nextColumns, rows = nextRows)
}

private fun PageTable.rebuiltMonthlySummary(ledger: List<LedgerRow>): PageTable {
    val existingRowsByCategory = rows.associateBy { row ->
        row.valueByColumnName(this, "Category").normalizedBudgetKey()
    }
    val categoryTotals = ledger.groupBy { row -> row.category }
    val categoryRows = categoryTotals.entries
        .sortedBy { (category, _) -> category.lowercase() }
        .map { (category, rows) ->
            val type = rows.firstOrNull()?.type.orEmpty()
            val status = if (rows.any { row -> row.status.equals("Incomplete", ignoreCase = true) }) {
                "Incomplete"
            } else {
                "Confirmed"
            }
            summaryRow(
                existing = existingRowsByCategory[category.normalizedBudgetKey()],
                values = mapOf(
                    "Category" to category,
                    "Type" to type,
                    "Total" to rows.sumAmounts().toPlainBudgetAmount(),
                    "Status" to status,
                ),
            )
        }

    val expenseTotal = ledger.filter { row -> row.type.equals("Expense", ignoreCase = true) }.sumAmounts()
    val debtTotal = ledger.filter { row -> row.type.equals("Debt", ignoreCase = true) }.sumAmounts()
    val incomeTotal = ledger.filter { row -> row.type.equals("Income", ignoreCase = true) }.sumAmounts()
    val aggregateRows = listOf(
        summaryRow(
            existing = existingRowsByCategory["known expenses"],
            values = mapOf(
                "Category" to "Known Expenses",
                "Type" to "Summary",
                "Total" to expenseTotal.toPlainBudgetAmount(),
                "Status" to "Confirmed",
            ),
        ),
        summaryRow(
            existing = existingRowsByCategory["debt"],
            values = mapOf(
                "Category" to "Debt",
                "Type" to "Summary",
                "Total" to debtTotal.toPlainBudgetAmount(),
                "Status" to "Confirmed",
            ),
        ),
        summaryRow(
            existing = existingRowsByCategory["income"],
            values = mapOf(
                "Category" to "Income",
                "Type" to "Summary",
                "Total" to incomeTotal.toPlainBudgetAmount(),
                "Status" to "Confirmed",
            ),
        ),
        summaryRow(
            existing = existingRowsByCategory["balance"],
            values = mapOf(
                "Category" to "Balance",
                "Type" to "Summary",
                "Total" to incomeTotal.subtract(expenseTotal).subtract(debtTotal).toPlainBudgetAmount(),
                "Status" to "Confirmed",
                "Notes" to "Income minus known expenses and debt",
            ),
        ),
    )

    val preservedManualRows = rows.filter { row ->
        val category = row.valueByColumnName(this, "Category").normalizedBudgetKey()
        category.isNotBlank() &&
            category !in SummaryAggregateCategories &&
            category !in categoryTotals.keys.map { it.normalizedBudgetKey() }.toSet()
    }
    val nextColumns = columns.map { column ->
        when (column.name.normalizedBudgetKey()) {
            "category" -> column.withSelectOptions(categoryRows.map { row -> row.valueByColumnName(copy(rows = emptyList()), "Category") } + listOf("Known Expenses", "Debt", "Income", "Balance"))
            "type" -> column.withSelectOptions(listOf("Expense", "Income", "Debt", "Summary"))
            "status" -> column.withSelectOptions(listOf("Confirmed", "Incomplete", "Empty"))
            else -> column
        }
    }
    return copy(
        columns = nextColumns,
        rows = categoryRows + preservedManualRows + aggregateRows,
    )
}

private fun PageTable.summaryRow(
    existing: PageTableRow?,
    values: Map<String, String>,
): PageTableRow {
    val base = existing ?: PageBlockCodec.newTableRow(columns)
    return values.entries.fold(base) { row, (columnName, value) ->
        row.withCellValue(columns, columnName, value)
    }
}

private fun PageTableRow.valueByColumnName(table: PageTable, columnName: String): String {
    val column = table.columns.firstOrNull { column ->
        column.name.normalizedBudgetKey() == columnName.normalizedBudgetKey()
    } ?: return ""
    return cells[column.id].orEmpty()
}

private fun PageTableRow.withCellValue(
    columns: List<PageTableColumn>,
    columnName: String,
    value: String,
): PageTableRow {
    val column = columns.firstOrNull { item -> item.name.normalizedBudgetKey() == columnName.normalizedBudgetKey() }
        ?: return this
    return copy(cells = cells + (column.id to value))
}

private fun PageTableColumn.withSelectOptions(options: List<String>): PageTableColumn {
    if (type != PageTableColumnType.Select && type != PageTableColumnType.MultiSelect && type != PageTableColumnType.Status) return this
    val optionNames = (config.options.map { option -> option.name } + options)
        .map { option -> option.toBudgetDisplayName() }
        .filter { option -> option.isNotBlank() }
        .distinctBy { option -> option.lowercase() }
    return copy(config = config.copy(options = optionNames.toBudgetSelectOptions()).normalizedForType(type))
}

private fun List<String>.toBudgetSelectOptions(): List<PageTableSelectOption> {
    val colors = listOf(
        PageTableOptionColor.Gray,
        PageTableOptionColor.Blue,
        PageTableOptionColor.Green,
        PageTableOptionColor.Yellow,
        PageTableOptionColor.Orange,
        PageTableOptionColor.Red,
    )
    return map { option -> option.trim() }
        .filter { option -> option.isNotBlank() }
        .distinctBy { option -> option.lowercase() }
        .mapIndexed { index, option ->
            PageTableSelectOption(
                id = option.normalizedBudgetKey().replace(" ", "-").ifBlank { "option" } + "-$index",
                name = option,
                color = colors[index % colors.size],
            )
        }
}

private fun Iterable<LedgerRow>.sumAmounts(): BigDecimal =
    fold(BigDecimal.ZERO) { total, row -> total.add(row.amount) }

private fun String.toBudgetAmount(): BigDecimal? =
    trim()
        .replace(",", ".")
        .let { value -> Regex("-?\\d+(?:\\.\\d+)?").find(value)?.value.orEmpty() }
        .takeIf { value -> value.isNotBlank() }
        ?.let { value -> runCatching { BigDecimal(value) }.getOrNull() }

private fun BigDecimal.toPlainBudgetAmount(): String =
    stripTrailingZeros().toPlainString()

private fun String.inferredBudgetType(name: String): String {
    val value = "$this $name".lowercase()
    return when {
        listOf("gaji", "salary", "income").any { token -> value.contains(token) } -> "Income"
        listOf("hutang", "debt", "loan").any { token -> value.contains(token) } -> "Debt"
        else -> "Expense"
    }
}

private fun String.toBudgetDisplayName(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .split(" ")
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char -> char.titlecase() }
        }

private fun String.normalizedBudgetKey(): String =
    trim()
        .lowercase()
        .replace("_", " ")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
