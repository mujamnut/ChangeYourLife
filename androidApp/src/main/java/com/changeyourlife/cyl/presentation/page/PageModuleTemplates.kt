package com.changeyourlife.cyl.presentation.page

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.normalizedForType

enum class PageModuleType(
    val label: String,
    val defaultTitle: String,
) {
    Goal("Goal", "Goal Tracker"),
    Habit("Habit", "Habit Tracker"),
    Travel("Travel", "Travel Planner"),
    Budget("Budget", "Budget Tracker");

    companion object {
        fun from(value: String): PageModuleType? {
            val key = value.normalizedModuleKey()
            return when {
                key.contains("goal") || key.contains("objective") || key.contains("okr") -> Goal
                key.contains("habit") || key.contains("routine") || key.contains("streak") -> Habit
                key.contains("travel") || key.contains("trip") || key.contains("itinerary") -> Travel
                key.contains("budget") || key.contains("expense") || key.contains("finance") || key.contains("cost") -> Budget
                else -> entries.firstOrNull { type -> key == type.name.lowercase() || key == type.label.lowercase() }
            }
        }
    }
}

object PageModuleTemplates {
    fun defaultTitle(type: PageModuleType): String = type.defaultTitle

    fun contentFor(type: PageModuleType): String {
        return PageBlockCodec.encodeDocument(documentFor(type))
    }

    fun fromActionFields(vararg values: String): PageModuleType? {
        return values.firstNotNullOfOrNull { value -> PageModuleType.from(value) }
    }

    private fun documentFor(type: PageModuleType): PageBlockDocument {
        val properties = listOf(
            PageBlockCodec.newProperty(PagePropertyType.Select, "Module").copy(value = type.label),
            PageBlockCodec.newProperty(PagePropertyType.Status, "Status").copy(value = "Active"),
        )
        val blocks = when (type) {
            PageModuleType.Goal -> listOf(
                heading("Goal Tracker"),
                text("Track outcomes, progress, next actions, and why each goal matters."),
                tableBlock(
                    title = "Goals",
                    columnSpecs = listOf(
                        "Goal" to PageTableColumnType.Text,
                        "Area" to PageTableColumnType.Status,
                        "Target date" to PageTableColumnType.Date,
                        "Progress" to PageTableColumnType.Number,
                        "Status" to PageTableColumnType.Status,
                        "Next action" to PageTableColumnType.Text,
                        "Why" to PageTableColumnType.Text,
                    ),
                    rowValues = listOf(
                        mapOf(
                            "Goal" to "Launch first CYL workspace",
                            "Area" to "Work",
                            "Progress" to "25",
                            "Status" to "In progress",
                            "Next action" to "Plan weekly review",
                            "Why" to "Build a stable life system",
                        ),
                    ),
                    viewConfig = { columns ->
                        PageTableViewConfig(
                            dashboardMetricColumnId = columns.idFor("Progress"),
                            dashboardGroupColumnId = columns.idFor("Status"),
                        )
                    },
                ),
            )

            PageModuleType.Habit -> listOf(
                heading("Habit Tracker"),
                text("Log daily habits, streaks, and completion without turning habits into separate tasks."),
                tableBlock(
                    title = "Habits",
                    columnSpecs = listOf(
                        "Habit" to PageTableColumnType.Text,
                        "Date" to PageTableColumnType.Date,
                        "Done" to PageTableColumnType.Checkbox,
                        "Frequency" to PageTableColumnType.Status,
                        "Streak" to PageTableColumnType.Number,
                        "Notes" to PageTableColumnType.Text,
                    ),
                    rowValues = listOf(
                        mapOf(
                            "Habit" to "Morning planning",
                            "Done" to "false",
                            "Frequency" to "Daily",
                            "Streak" to "0",
                        ),
                    ),
                    viewConfig = { columns ->
                        PageTableViewConfig(
                            calendarDateColumnId = columns.idFor("Date"),
                            dashboardMetricColumnId = columns.idFor("Streak"),
                            dashboardGroupColumnId = columns.idFor("Frequency"),
                        )
                    },
                ),
            )

            PageModuleType.Travel -> listOf(
                heading("Travel Planner"),
                text("Plan itinerary, places, statuses, and budget items in one trip database."),
                tableBlock(
                    title = "Itinerary",
                    columnSpecs = listOf(
                        "Item" to PageTableColumnType.Text,
                        "Start date" to PageTableColumnType.Date,
                        "End date" to PageTableColumnType.Date,
                        "Place" to PageTableColumnType.Text,
                        "Status" to PageTableColumnType.Status,
                        "Budget" to PageTableColumnType.Number,
                        "Notes" to PageTableColumnType.Text,
                    ),
                    rowValues = listOf(
                        mapOf(
                            "Item" to "Book flights",
                            "Status" to "Not started",
                            "Budget" to "0",
                        ),
                    ),
                    viewConfig = { columns ->
                        PageTableViewConfig(
                            calendarDateColumnId = columns.idFor("Start date"),
                            timelineStartColumnId = columns.idFor("Start date"),
                            timelineEndColumnId = columns.idFor("End date"),
                            dashboardMetricColumnId = columns.idFor("Budget"),
                            dashboardGroupColumnId = columns.idFor("Status"),
                        )
                    },
                ),
            )

            PageModuleType.Budget -> listOf(
                heading("Budget Tracker"),
                tableBlock(
                    title = "Transactions",
                    columnSpecs = listOf(
                        "Name" to PageTableColumnType.Text,
                        "Date" to PageTableColumnType.Date,
                        "Month" to PageTableColumnType.Select,
                        "Category" to PageTableColumnType.Select,
                        "Type" to PageTableColumnType.Select,
                        "Amount" to PageTableColumnType.Number,
                        "Status" to PageTableColumnType.Status,
                        "Notes" to PageTableColumnType.Text,
                    ),
                    rowValues = listOf(
                        mapOf(
                            "Name" to "Salary",
                            "Month" to "2026-07",
                            "Category" to "Salary",
                            "Type" to "Income",
                            "Amount" to "0",
                            "Status" to "Confirmed",
                        ),
                    ),
                    configureColumns = { columns ->
                        columns.withOptions("Month", "2026-07")
                            .withOptions("Category", "Salary", "Food", "Fuel", "Makeup", "Transport", "Other")
                            .withOptions("Type", "Expense", "Income", "Debt")
                            .withOptions("Status", "Confirmed", "Incomplete", "Empty")
                    },
                    viewConfig = { columns ->
                        PageTableViewConfig(
                            calendarDateColumnId = columns.idFor("Date"),
                            dashboardMetricColumnId = columns.idFor("Amount"),
                            dashboardGroupColumnId = columns.idFor("Category"),
                        )
                    },
                ),
                monthlySummaryTableBlock(),
            )
        }
        return PageBlockDocument(properties = properties, blocks = blocks).withBudgetLedgerSummarySynced()
    }

    private fun monthlySummaryTableBlock(): PageBlock {
        val month = PageBlockCodec.newTableColumn("Month", PageTableColumnType.Select)
            .copy(config = PageTableColumnConfig(options = listOf("2026-07").toTemplateSelectOptions()))
        val incomeRelation = hiddenRelationColumn("Income Transactions")
        val expenseRelation = hiddenRelationColumn("Expense Transactions")
        val debtRelation = hiddenRelationColumn("Debt Transactions")
        val income = PageBlockCodec.newTableColumn("Income", PageTableColumnType.Rollup)
            .copy(
                rollupRelationColumnId = incomeRelation.id,
                rollupAggregation = PageTableRollupAggregation.Sum,
            )
        val expenses = PageBlockCodec.newTableColumn("Known Expenses", PageTableColumnType.Rollup)
            .copy(
                rollupRelationColumnId = expenseRelation.id,
                rollupAggregation = PageTableRollupAggregation.Sum,
            )
        val debt = PageBlockCodec.newTableColumn("Debt", PageTableColumnType.Rollup)
            .copy(
                rollupRelationColumnId = debtRelation.id,
                rollupAggregation = PageTableRollupAggregation.Sum,
            )
        val balance = PageBlockCodec.newTableColumn("Balance", PageTableColumnType.Formula)
            .copy(formula = "{Income} - {Known Expenses} - {Debt}")
        val status = PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status)
        val notes = PageBlockCodec.newTableColumn("Notes", PageTableColumnType.Text)
        val columns = listOf(month, incomeRelation, expenseRelation, debtRelation, income, expenses, debt, balance, status, notes)
        return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
            table = PageTable(
                title = "Monthly Summary",
                view = PageTableView.Table,
                viewConfig = PageTableViewConfig(
                    dashboardMetricColumnId = balance.id,
                    dashboardGroupColumnId = month.id,
                ),
                columns = columns,
                rows = listOf(
                    PageBlockCodec.newTableRow(columns).copy(
                        id = "summary-2026-07",
                        cells = mapOf(
                            month.id to "2026-07",
                            status.id to "Confirmed",
                            notes.id to "Balance = Income - Known Expenses - Debt",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun hiddenRelationColumn(name: String): PageTableColumn =
        PageBlockCodec.newTableColumn(name, PageTableColumnType.Relation).copy(
            config = PageTableColumnConfig(isHidden = true).normalizedForType(PageTableColumnType.Relation),
        )

    private fun heading(text: String): PageBlock {
        return PageBlockCodec.newBlock(PageBlockType.Heading).copy(text = text)
    }

    private fun text(text: String): PageBlock {
        return PageBlockCodec.newBlock(PageBlockType.Text).copy(text = text)
    }

    private fun tableBlock(
        title: String,
        columnSpecs: List<Pair<String, PageTableColumnType>>,
        rowValues: List<Map<String, String>>,
        viewConfig: (List<PageTableColumn>) -> PageTableViewConfig = { PageTableViewConfig() },
        configureColumns: (List<PageTableColumn>) -> List<PageTableColumn> = { it },
    ): PageBlock {
        val columns = configureColumns(columnSpecs.map { (name, type) -> PageBlockCodec.newTableColumn(name, type) })
        val rows = rowValues.ifEmpty { listOf(emptyMap()) }.map { valuesByName ->
            PageBlockCodec.newTableRow(columns).copy(
                cells = columns.associate { column ->
                    column.id to valuesByName[column.name].orEmpty()
                },
            )
        }
        return PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
            table = PageTable(
                title = title,
                view = PageTableView.Table,
                viewConfig = viewConfig(columns),
                columns = columns,
                rows = rows,
            ),
        )
    }

    private fun List<PageTableColumn>.idFor(name: String): String {
        return firstOrNull { column -> column.name.equals(name, ignoreCase = true) }?.id.orEmpty()
    }

    private fun List<PageTableColumn>.withOptions(
        columnName: String,
        vararg options: String,
    ): List<PageTableColumn> = map { column ->
        if (!column.name.equals(columnName, ignoreCase = true)) return@map column
        column.copy(
            config = column.config.copy(
                options = options.toList().toTemplateSelectOptions(),
            ),
        )
    }

    private fun List<String>.toTemplateSelectOptions(): List<PageTableSelectOption> {
        val colors = listOf(
            PageTableOptionColor.Gray,
            PageTableOptionColor.Blue,
            PageTableOptionColor.Green,
            PageTableOptionColor.Yellow,
            PageTableOptionColor.Orange,
        )
        return distinctBy { option -> option.lowercase() }.mapIndexed { index, option ->
            PageTableSelectOption(
                id = option.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "option-$index" },
                name = option,
                color = colors[index % colors.size],
            )
        }
    }
}

private fun String.normalizedModuleKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")
}
