package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableCellValue
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSort
import com.changeyourlife.cyl.domain.model.normalizedForType
import com.changeyourlife.cyl.domain.model.toTypedCellValue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NoMonthLabel = "No month"

internal fun PageBlockDocument.withBudgetLedgerSummarySynced(): PageBlockDocument {
    val migrated = withLegacyBudgetTablesMigrated()
    val transactionBlock = migrated.blocks.firstOrNull { block -> block.isTransactionLedgerTable() } ?: return migrated
    val normalizedTransactionTable = transactionBlock.table.normalizedTransactionTable(emptyList())
    val ledger = normalizedTransactionTable.toLedgerRows()
    val syncedTransactionTable = normalizedTransactionTable.normalizedTransactionTable(ledger)
    val existingSummaryBlock = migrated.blocks.firstOrNull { block -> block.isMonthlySummaryTable() }
    val summaryBlock = existingSummaryBlock ?: newMonthlySummaryBlock(
        transactionBlockId = transactionBlock.id,
        transactionTable = syncedTransactionTable,
    )
    val workingDocument = if (existingSummaryBlock == null) {
        migrated.copy(
            blocks = migrated.blocks.flatMap { block ->
                if (block.id == transactionBlock.id) listOf(block, summaryBlock) else listOf(block)
            },
        )
    } else {
        migrated
    }

    val syncedSummaryTable = summaryBlock.table.rebuiltMonthlySummary(
        transactionBlockId = transactionBlock.id,
        transactionTable = syncedTransactionTable,
        ledger = ledger,
    )
    if (
        syncedTransactionTable == transactionBlock.table &&
        syncedSummaryTable == summaryBlock.table &&
        workingDocument == migrated
    ) {
        return migrated
    }

    return workingDocument.copy(
        blocks = workingDocument.blocks.map { block ->
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

private fun PageBlockDocument.withLegacyBudgetTablesMigrated(): PageBlockDocument {
    var didMigrate = false
    val nextBlocks = blocks.flatMap { block ->
        if (!block.isLegacyBudgetTable()) return@flatMap listOf(block)
        didMigrate = true
        listOf(block.toMigratedTransactionsBlock())
    }
    return if (didMigrate) copy(blocks = nextBlocks) else this
}

private fun PageBlock.isLegacyBudgetTable(): Boolean {
    if (type != PageBlockType.DatabaseTable) return false
    if (isTransactionLedgerTable() || isMonthlySummaryTable()) return false
    val title = table.title.normalizedBudgetKey()
    val titleLooksBudget = listOf("budget", "expense", "expenses", "monthly", "belanja", "pengeluaran", "duit").any { token ->
        title.contains(token)
    }
    val columnNames = table.columns.map { column -> column.name.normalizedBudgetKey() }.toSet()
    val hasOldMonthlyColumns = "category" in columnNames &&
        ("budget" in columnNames || "actual" in columnNames || "variance" in columnNames)
    val hasOldTemplateColumns = ("item" in columnNames || "name" in columnNames) &&
        "amount" in columnNames &&
        ("paid" in columnNames || "due date" in columnNames || "date" in columnNames)
    return titleLooksBudget && (hasOldMonthlyColumns || hasOldTemplateColumns)
}

private fun PageBlock.toMigratedTransactionsBlock(): PageBlock {
    val fallbackMonth = table.title.toBudgetMonthKeyFromText()
    val migratedRows = table.toMigratedLedgerRows(fallbackMonth)
    val columns = transactionColumnsFor(migratedRows)
    val rows = migratedRows.map { row -> row.toPageTableRow(columns) }
    return copy(
        table = table.copy(
            title = "Transactions",
            columns = columns,
            rows = rows,
        ),
    )
}

private data class MigratedLedgerRow(
    val id: String,
    val name: String,
    val date: String,
    val month: String,
    val category: String,
    val type: String,
    val amount: String,
    val status: String,
    val notes: String,
)

private fun PageTable.toMigratedLedgerRows(fallbackMonth: String): List<MigratedLedgerRow> {
    val hasActualColumn = columns.any { column -> column.name.normalizedBudgetKey() == "actual" }
    return rows.mapNotNull { row ->
        val category = row.valueByColumnName(this, "Category")
        val name = row.valueByColumnName(this, "Name")
            .ifBlank { row.valueByColumnName(this, "Item") }
            .ifBlank { category }
            .ifBlank { return@mapNotNull null }
            .toBudgetDisplayName()
        val amount = row.valueByColumnName(this, "Actual")
            .ifBlank { row.valueByColumnName(this, "Amount") }
            .ifBlank { row.valueByColumnName(this, "Budget") }
            .ifBlank { row.valueByColumnName(this, "Total") }
        val date = row.valueByColumnName(this, "Date")
            .ifBlank { row.valueByColumnName(this, "Due Date") }
            .ifBlank { row.valueByColumnName(this, "Due date") }
        val month = row.valueByColumnName(this, "Month")
            .ifBlank { date.toBudgetMonthKey() }
            .ifBlank { fallbackMonth }
        val paid = row.valueByColumnName(this, "Paid")
        val status = when {
            amount.isBlank() -> "Empty"
            paid.toBudgetBoolean() -> "Confirmed"
            hasActualColumn && row.valueByColumnName(this, "Actual").isBlank() -> "Incomplete"
            else -> "Confirmed"
        }
        val notes = listOfNotNull(
            row.valueByColumnName(this, "Notes").takeIf { value -> value.isNotBlank() },
            row.valueByColumnName(this, "Variance").takeIf { value -> value.isNotBlank() }?.let { value -> "Variance: $value" },
        ).joinToString("; ")
        MigratedLedgerRow(
            id = row.id,
            name = name,
            date = date,
            month = month,
            category = category.ifBlank { name }.toBudgetDisplayName(),
            type = category.inferredBudgetType(name).toBudgetDisplayName(),
            amount = amount,
            status = status,
            notes = notes,
        )
    }
}

private fun MigratedLedgerRow.toPageTableRow(columns: List<PageTableColumn>): PageTableRow {
    val values = mapOf(
        "Name" to name,
        "Date" to date,
        "Month" to month,
        "Category" to category,
        "Type" to type,
        "Amount" to amount,
        "Status" to status,
        "Notes" to notes,
    )
    return values.entries.fold(PageBlockCodec.newTableRow(columns).copy(id = id)) { row, (columnName, value) ->
        row.withCellValue(columns, columnName, value)
    }
}

private fun transactionColumnsFor(rows: List<MigratedLedgerRow>): List<PageTableColumn> =
    listOf(
        PageBlockCodec.newTableColumn("Name", PageTableColumnType.Text),
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date),
        PageBlockCodec.newTableColumn("Month", PageTableColumnType.Select).withSelectOptions(rows.map { row -> row.month }),
        PageBlockCodec.newTableColumn("Category", PageTableColumnType.Select).withSelectOptions(rows.map { row -> row.category }),
        PageBlockCodec.newTableColumn("Type", PageTableColumnType.Select).withSelectOptions(rows.map { row -> row.type } + listOf("Expense", "Income", "Debt")),
        PageBlockCodec.newTableColumn("Amount", PageTableColumnType.Number),
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status).withSelectOptions(rows.map { row -> row.status } + listOf("Confirmed", "Incomplete", "Empty")),
        PageBlockCodec.newTableColumn("Notes", PageTableColumnType.Text),
    )

private fun newMonthlySummaryBlock(
    transactionBlockId: String,
    transactionTable: PageTable,
): PageBlock {
    val columns = PageTable().monthlySummaryColumns(
        transactionBlockId = transactionBlockId,
        transactionTable = transactionTable,
        monthOptions = emptyList(),
    )
    return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
        table = PageTable(
            title = "Monthly Summary",
            columns = columns,
        ),
    )
}

private data class LedgerRow(
    val rowId: String,
    val name: String,
    val month: String,
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
        val date = row.valueByColumnName(this, "Date")
        val month = row.valueByColumnName(this, "Month")
            .ifBlank { date.toBudgetMonthKey() }
            .ifBlank { NoMonthLabel }
        val amount = row.valueByColumnName(this, "Amount")
            .ifBlank { row.valueByColumnName(this, "Jumlah") }
            .ifBlank { row.valueByColumnName(this, "Price") }
            .ifBlank { row.valueByColumnName(this, "Cost") }
            .toBudgetAmount()
            ?: return@mapNotNull null
        LedgerRow(
            rowId = row.id,
            name = name,
            month = month,
            category = category.ifBlank { "Other" }.toBudgetDisplayName(),
            type = row.valueByColumnName(this, "Type").ifBlank { category.inferredBudgetType(name) }.toBudgetDisplayName(),
            amount = amount,
            status = row.valueByColumnName(this, "Status").ifBlank { "Confirmed" }.toBudgetDisplayName(),
        )
    }
}

private fun PageTable.normalizedTransactionTable(ledger: List<LedgerRow>): PageTable {
    val nextColumns = budgetTransactionColumns(ledger)
    val dateColumn = nextColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "date" }
    val monthColumn = nextColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "month" }
    val categoryOptions = ledger.map { row -> row.category }
    val monthOptions = ledger.map { row -> row.month }
    val typeOptions = ledger.map { row -> row.type } + listOf("Expense", "Income", "Debt")
    val statusOptions = ledger.map { row -> row.status } + listOf("Confirmed", "Incomplete", "Empty")
    val configuredColumns = nextColumns.map { column ->
        when (column.name.normalizedBudgetKey()) {
            "month" -> column.withSelectOptions(monthOptions)
            "category" -> column.withSelectOptions(categoryOptions)
            "type" -> column.withSelectOptions(typeOptions)
            "status" -> column.withSelectOptions(statusOptions)
            else -> column
        }
    }
    val nextRows = rows.map { row ->
        val name = row.valueByColumnName(this, "Name").ifBlank { row.valueByColumnName(this, "Item") }
        val date = row.valueByColumnName(this, "Date")
        val month = row.valueByColumnName(this, "Month").ifBlank { date.toBudgetMonthKey() }
        val category = row.valueByColumnName(this, "Category").ifBlank { name }.toBudgetDisplayName()
        val type = row.valueByColumnName(this, "Type").ifBlank { category.inferredBudgetType(name) }.toBudgetDisplayName()
        val amount = row.valueByColumnName(this, "Amount")
        val normalizedAmount = amount.toBudgetAmount()?.toPlainBudgetAmount() ?: amount
        val status = row.valueByColumnName(this, "Status").ifBlank {
            if (amount.isBlank()) "Empty" else "Confirmed"
        }.toBudgetDisplayName()
        row.withCellValue(configuredColumns, "Name", name)
            .withCellValue(configuredColumns, "Date", date)
            .withCellValue(configuredColumns, "Month", month)
            .withCellValue(configuredColumns, "Category", category)
            .withCellValue(configuredColumns, "Type", type)
            .withCellValue(configuredColumns, "Amount", normalizedAmount)
            .withCellValue(configuredColumns, "Status", status)
            .withCellValue(configuredColumns, "Notes", row.valueByColumnName(this, "Notes"))
    }
    return copy(
        columns = configuredColumns,
        rows = nextRows,
        sort = sort.takeIf { value -> value.columnId.isNotBlank() }
            ?: PageTableSort(columnId = dateColumn?.id.orEmpty()),
        groupByColumnId = groupByColumnId.ifBlank { monthColumn?.id.orEmpty() },
        viewConfig = viewConfig.copy(
            calendarDateColumnId = viewConfig.calendarDateColumnId.ifBlank { dateColumn?.id.orEmpty() },
            dashboardMetricColumnId = viewConfig.dashboardMetricColumnId.ifBlank {
                configuredColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "amount" }?.id.orEmpty()
            },
            dashboardGroupColumnId = viewConfig.dashboardGroupColumnId.ifBlank {
                configuredColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "category" }?.id.orEmpty()
            },
        ),
    )
}

private fun PageTable.rebuiltMonthlySummary(
    transactionBlockId: String,
    transactionTable: PageTable,
    ledger: List<LedgerRow>,
): PageTable {
    val monthGroups = ledger.groupBy { row -> row.month.ifBlank { NoMonthLabel } }
    val monthOptions = monthGroups.keys.toList()
    val nextColumns = monthlySummaryColumns(
        transactionBlockId = transactionBlockId,
        transactionTable = transactionTable,
        monthOptions = monthOptions,
    )
    val existingRowsByMonth = rows.associateBy { row ->
        row.valueByColumnName(this, "Month").normalizedBudgetKey()
    }
    val nextRows = monthGroups.entries
        .sortedWith { left, right -> compareBudgetMonths(left.key, right.key) }
        .map { (month, rowsForMonth) ->
            val existing = existingRowsByMonth[month.normalizedBudgetKey()]
            summaryRowForMonth(
                existing = existing,
                columns = nextColumns,
                month = month,
                rowsForMonth = rowsForMonth,
            )
        }
    return copy(
        columns = nextColumns,
        rows = nextRows,
        viewConfig = viewConfig.copy(
            dashboardMetricColumnId = nextColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "balance" }?.id.orEmpty(),
            dashboardGroupColumnId = nextColumns.firstOrNull { column -> column.name.normalizedBudgetKey() == "month" }?.id.orEmpty(),
        ),
    )
}

private fun PageTable.budgetTransactionColumns(ledger: List<LedgerRow>): List<PageTableColumn> {
    fun existingOrNew(
        name: String,
        type: PageTableColumnType,
        aliases: List<String> = emptyList(),
    ): PageTableColumn {
        val keys = (listOf(name) + aliases).map { value -> value.normalizedBudgetKey() }
        val existing = columns.firstOrNull { column -> column.name.normalizedBudgetKey() in keys }
        return (existing ?: PageBlockCodec.newTableColumn(name, type)).copy(
            name = name,
            type = type,
            config = (existing?.config ?: PageTableColumnConfig()).normalizedForType(type),
        )
    }

    val standardColumns = listOf(
        existingOrNew("Name", PageTableColumnType.Text, aliases = listOf("Item")),
        existingOrNew("Date", PageTableColumnType.Date),
        existingOrNew("Month", PageTableColumnType.Select)
            .withSelectOptions(ledger.map { row -> row.month }),
        existingOrNew("Category", PageTableColumnType.Select)
            .withSelectOptions(ledger.map { row -> row.category }),
        existingOrNew("Type", PageTableColumnType.Select)
            .withSelectOptions(ledger.map { row -> row.type } + listOf("Expense", "Income", "Debt")),
        existingOrNew("Amount", PageTableColumnType.Number, aliases = listOf("Jumlah", "Price", "Cost", "Total")),
        existingOrNew("Status", PageTableColumnType.Status)
            .withSelectOptions(ledger.map { row -> row.status } + listOf("Confirmed", "Incomplete", "Empty")),
        existingOrNew("Notes", PageTableColumnType.Text),
    )
    val standardIds = standardColumns.map { column -> column.id }.toSet()
    return standardColumns + columns.filterNot { column -> column.id in standardIds }
}

private fun PageTable.monthlySummaryColumns(
    transactionBlockId: String,
    transactionTable: PageTable,
    monthOptions: List<String>,
): List<PageTableColumn> {
    val amountColumnId = transactionTable.columnIdByName("Amount")
    fun existingOrNew(
        name: String,
        type: PageTableColumnType,
    ): PageTableColumn {
        val existing = columns.firstOrNull { column -> column.name.normalizedBudgetKey() == name.normalizedBudgetKey() }
        return (existing ?: PageBlockCodec.newTableColumn(name, type)).copy(
            name = name,
            type = type,
            config = (existing?.config ?: PageTableColumnConfig()).normalizedForType(type),
        )
    }

    val month = existingOrNew("Month", PageTableColumnType.Select)
        .withSelectOptions(monthOptions)
    val incomeRelation = existingOrNew("Income Transactions", PageTableColumnType.Relation)
        .copy(
            relationTargetTableId = transactionBlockId,
            config = PageTableColumnConfig(isHidden = true).normalizedForType(PageTableColumnType.Relation),
        )
    val expenseRelation = existingOrNew("Expense Transactions", PageTableColumnType.Relation)
        .copy(
            relationTargetTableId = transactionBlockId,
            config = PageTableColumnConfig(isHidden = true).normalizedForType(PageTableColumnType.Relation),
        )
    val debtRelation = existingOrNew("Debt Transactions", PageTableColumnType.Relation)
        .copy(
            relationTargetTableId = transactionBlockId,
            config = PageTableColumnConfig(isHidden = true).normalizedForType(PageTableColumnType.Relation),
        )
    val income = existingOrNew("Income", PageTableColumnType.Rollup)
        .copy(
            rollupRelationColumnId = incomeRelation.id,
            rollupTargetColumnId = amountColumnId,
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
    val expenses = existingOrNew("Known Expenses", PageTableColumnType.Rollup)
        .copy(
            rollupRelationColumnId = expenseRelation.id,
            rollupTargetColumnId = amountColumnId,
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
    val debt = existingOrNew("Debt", PageTableColumnType.Rollup)
        .copy(
            rollupRelationColumnId = debtRelation.id,
            rollupTargetColumnId = amountColumnId,
            rollupAggregation = PageTableRollupAggregation.Sum,
        )
    val balance = existingOrNew("Balance", PageTableColumnType.Formula)
        .copy(formula = "{Income} - {Known Expenses} - {Debt}")
    val status = existingOrNew("Status", PageTableColumnType.Status)
        .withSelectOptions(listOf("Confirmed", "Incomplete", "Empty"))
    val notes = existingOrNew("Notes", PageTableColumnType.Text)
    return listOf(month, incomeRelation, expenseRelation, debtRelation, income, expenses, debt, balance, status, notes)
}

private fun summaryRowForMonth(
    existing: PageTableRow?,
    columns: List<PageTableColumn>,
    month: String,
    rowsForMonth: List<LedgerRow>,
): PageTableRow {
    val base = existing ?: PageBlockCodec.newTableRow(columns).copy(id = "summary-${month.normalizedBudgetKey().replace(" ", "-").ifBlank { "no-month" }}")
    val rowsByType = rowsForMonth.groupBy { row -> row.type.normalizedBudgetKey() }
    val status = if (rowsForMonth.any { row -> row.status.equals("Incomplete", ignoreCase = true) }) {
        "Incomplete"
    } else {
        "Confirmed"
    }
    return base
        .withCellValue(columns, "Month", month)
        .withCellValue(columns, "Income Transactions", rowsByType["income"].orEmpty().joinToString(",") { row -> row.rowId })
        .withCellValue(columns, "Expense Transactions", rowsByType["expense"].orEmpty().joinToString(",") { row -> row.rowId })
        .withCellValue(columns, "Debt Transactions", rowsByType["debt"].orEmpty().joinToString(",") { row -> row.rowId })
        .withCellValue(columns, "Income", "")
        .withCellValue(columns, "Known Expenses", "")
        .withCellValue(columns, "Debt", "")
        .withCellValue(columns, "Balance", "")
        .withCellValue(columns, "Status", status)
        .withCellValue(columns, "Notes", "Balance = Income - Known Expenses - Debt")
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
    return if (column.type == PageTableColumnType.Relation) {
        val relationRowIds = value.relatedBudgetRowIds()
        copy(
            cells = cells + (column.id to relationRowIds.joinToString(",")),
            cellValues = cellValues + (
                column.id to PageTableCellValue(
                    type = PageTableColumnType.Relation,
                    relationRowIds = relationRowIds,
                )
                ),
        )
    } else {
        copy(
            cells = cells + (column.id to value),
            cellValues = cellValues + (column.id to value.toTypedCellValue(column.type)),
        )
    }
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

private fun String.toBudgetBoolean(): Boolean =
    normalizedBudgetKey() in setOf("true", "yes", "paid", "done", "checked", "1", "ya", "sudah", "siap")

private fun BigDecimal.toPlainBudgetAmount(): String =
    stripTrailingZeros().toPlainString()

private fun String.relatedBudgetRowIds(): List<String> =
    split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinct()

private fun PageTable.columnIdByName(columnName: String): String =
    columns.firstOrNull { column -> column.name.normalizedBudgetKey() == columnName.normalizedBudgetKey() }?.id.orEmpty()

private fun String.toBudgetMonthKey(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    Regex("^\\d{4}-\\d{2}$").find(trimmed)?.let { return it.value }
    val jsonDate = Regex("\"startDate\"\\s*:\\s*\"([^\"]+)\"")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
    val dateText = jsonDate ?: trimmed
    val date = dateText.toBudgetLocalDateOrNull() ?: return dateText.toBudgetMonthKeyFromText()
    return YearMonth.from(date).toString()
}

private fun String.toBudgetMonthKeyFromText(): String {
    val value = lowercase()
    val year = Regex("\\b(20\\d{2})\\b").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: LocalDate.now().year
    val monthNumber = Regex("(?i)\\b(?:bulan|month)\\s*(\\d{1,2})\\b")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val month = monthNumber ?: when {
        "january" in value || "januari" in value -> 1
        "february" in value || "februari" in value -> 2
        "march" in value || "mac" in value -> 3
        "april" in value -> 4
        "may" in value || "mei" in value -> 5
        "june" in value || "jun" in value -> 6
        "july" in value || "julai" in value -> 7
        "august" in value || "ogos" in value -> 8
        "september" in value -> 9
        "october" in value || "oktober" in value -> 10
        "november" in value -> 11
        "december" in value || "disember" in value -> 12
        else -> null
    } ?: return ""
    return if (month in 1..12) "%04d-%02d".format(year, month) else ""
}

private fun String.toBudgetLocalDateOrNull(): LocalDate? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val formatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US),
    )
    return formatters.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDate.parse(trimmed, formatter) }.getOrNull()
    }
}

private fun compareBudgetMonths(left: String, right: String): Int {
    val leftMonth = runCatching { YearMonth.parse(left) }.getOrNull()
    val rightMonth = runCatching { YearMonth.parse(right) }.getOrNull()
    return when {
        leftMonth != null && rightMonth != null -> leftMonth.compareTo(rightMonth)
        left == NoMonthLabel -> 1
        right == NoMonthLabel -> -1
        else -> left.compareTo(right, ignoreCase = true)
    }
}

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
