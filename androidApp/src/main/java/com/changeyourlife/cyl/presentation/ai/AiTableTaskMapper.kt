package com.changeyourlife.cyl.presentation.ai

import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnConfig
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableOptionColor
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.normalizedForType
import com.changeyourlife.cyl.domain.repository.ChatAction
import com.changeyourlife.cyl.domain.repository.ChatTableColumn
import com.changeyourlife.cyl.presentation.page.PageBlockCodec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TaskTableMutationPlan(
    val tableBlock: PageBlock,
    val isNewTable: Boolean,
    val tableTitle: String,
    val rowTitle: String,
    val rowId: String,
    val dateColumnId: String,
    val dateCellValue: String,
)

fun ChatTableColumn.toPageTableColumnFromAi(): PageTableColumn {
    val columnType = type.toPageTableColumnTypeFromAi()
    return PageBlockCodec.newTableColumn(name.trim(), columnType).copy(
        config = PageTableColumnConfig(options = options.toAiTableSelectOptions()).normalizedForType(columnType),
        dateFormat = dateFormat.toPageTableDateFormat(),
        timeFormat = timeFormat.toPageTableTimeFormat(defaultFor = name, type = columnType),
        dateReminder = dateReminder.toPageTableDateReminder(defaultFor = name, type = columnType),
        timezoneLabel = timezoneLabel.ifBlank { "Local" },
        formula = formula,
        relationTargetTableId = relationTargetTableId,
        rollupAggregation = rollupAggregation.toPageTableRollupAggregationFromAi(),
    )
}

internal fun List<String>.toAiTableSelectOptions(): List<PageTableSelectOption> {
    val colors = listOf(
        PageTableOptionColor.Gray,
        PageTableOptionColor.Blue,
        PageTableOptionColor.Green,
        PageTableOptionColor.Yellow,
        PageTableOptionColor.Orange,
        PageTableOptionColor.Red,
    )
    return map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinctBy { value -> value.lowercase() }
        .mapIndexed { index, value ->
            PageTableSelectOption(
                id = value.normalizedAiKey().replace(" ", "-").ifBlank { "option" } + "-$index",
                name = value,
                color = colors[index % colors.size],
            )
        }
}

fun ChatAction.isTaskTableRowAction(): Boolean {
    if (type.normalizedAiActionType() != "ADD_TABLE_ROW") return false
    val tableHint = tableTitle.contains("task", ignoreCase = true) ||
        tableTitle.contains("reminder", ignoreCase = true) ||
        title.contains("reminder", ignoreCase = true) ||
        title.contains("task", ignoreCase = true) ||
        rowTitle.contains("reminder", ignoreCase = true) ||
        rowTitle.contains("task", ignoreCase = true)
    val cellKeys = cellValues.keys.map { key -> key.normalizedAiKey() }.toSet()
    val taskKeys = setOf("task", "todo", "reminder")
    return tableHint || cellKeys.any { key -> key in taskKeys }
}

fun PageBlockDocument.planTaskTableAction(action: ChatAction): TaskTableMutationPlan {
    val existing = blocks.findTaskLikeTable()
    return if (existing == null) {
        val columns = defaultTaskColumns()
        val row = action.toTaskTableRow(columns)
        val dateColumn = columns.first { column -> column.type == PageTableColumnType.Date }
        val tableBlock = PageBlockCodec.newBlock(PageBlockType.DatabaseTable).copy(
            table = PageTable(
                title = "Tasks",
                view = PageTableView.Table,
                viewConfig = PageTableViewConfig(
                    calendarDateColumnId = columns.firstOrNull { column -> column.type == PageTableColumnType.Date }?.id.orEmpty(),
                ),
                columns = columns,
                rows = listOf(row),
            ),
        )
        TaskTableMutationPlan(
            tableBlock = tableBlock,
            isNewTable = true,
            tableTitle = tableBlock.table.title,
            rowTitle = row.cellText(columns.firstOrNull()).ifBlank { action.taskLikeTitle() },
            rowId = row.id,
            dateColumnId = dateColumn.id,
            dateCellValue = row.cells[dateColumn.id].orEmpty(),
        )
    } else {
        val normalized = existing.withRequiredTaskColumns()
        val row = action.toTaskTableRow(normalized.table.columns)
        val dateColumn = normalized.table.columns.first { column -> column.type == PageTableColumnType.Date }
        val updated = normalized.copy(
            table = normalized.table.copy(rows = normalized.table.rows + row),
        )
        TaskTableMutationPlan(
            tableBlock = updated,
            isNewTable = false,
            tableTitle = updated.table.title,
            rowTitle = row.cellText(updated.table.columns.firstOrNull()).ifBlank { action.taskLikeTitle() },
            rowId = row.id,
            dateColumnId = dateColumn.id,
            dateCellValue = row.cells[dateColumn.id].orEmpty(),
        )
    }
}

private fun defaultTaskColumns(): List<PageTableColumn> {
    return listOf(
        PageBlockCodec.newTableColumn("Task", PageTableColumnType.Text),
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status),
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date).copy(
            dateFormat = PageTableDateFormat.DayMonthYear,
            timeFormat = PageTableTimeFormat.TwelveHour,
            dateReminder = PageTableDateReminder.None,
            timezoneLabel = "Local",
        ),
        PageBlockCodec.newTableColumn("Notes", PageTableColumnType.Text),
    )
}

private fun PageBlock.withRequiredTaskColumns(): PageBlock {
    if (type != PageBlockType.DatabaseTable) return this
    val columns = table.columns.toMutableList()

    fun ensureColumn(
        aliases: Set<String>,
        create: () -> PageTableColumn,
    ): PageTableColumn {
        val existing = columns.firstOrNull { column -> column.name.normalizedAiKey() in aliases }
        if (existing != null) return existing
        val column = create()
        columns += column
        return column
    }

    ensureColumn(setOf("task", "name", "title", "item")) {
        PageBlockCodec.newTableColumn("Task", PageTableColumnType.Text)
    }
    ensureColumn(setOf("status", "progress")) {
        PageBlockCodec.newTableColumn("Status", PageTableColumnType.Status)
    }
    val dateColumn = ensureColumn(setOf("date", "due date", "deadline", "time", "reminder")) {
        PageBlockCodec.newTableColumn("Date", PageTableColumnType.Date)
    }
    ensureColumn(setOf("notes", "note", "details")) {
        PageBlockCodec.newTableColumn("Notes", PageTableColumnType.Text)
    }

    val normalizedColumns = columns.map { column ->
        if (column.id == dateColumn.id) {
            column.copy(
                type = PageTableColumnType.Date,
                dateFormat = PageTableDateFormat.DayMonthYear,
                timeFormat = PageTableTimeFormat.TwelveHour,
                dateReminder = PageTableDateReminder.None,
                timezoneLabel = column.timezoneLabel.ifBlank { "Local" },
            )
        } else {
            column
        }
    }
    val validCellIds = normalizedColumns.map { column -> column.id }.toSet()
    val normalizedRows = table.rows.map { row ->
        val withMissingColumns = normalizedColumns.associate { column -> column.id to row.cells[column.id].orEmpty() }
        row.copy(cells = withMissingColumns.filterKeys { columnId -> columnId in validCellIds })
    }

    return copy(
        table = table.copy(
            columns = normalizedColumns,
            rows = normalizedRows,
            viewConfig = table.viewConfig.copy(
                calendarDateColumnId = table.viewConfig.calendarDateColumnId.ifBlank { dateColumn.id },
            ),
        ),
    )
}

private fun ChatAction.toTaskTableRow(columns: List<PageTableColumn>): PageTableRow {
    val title = taskLikeTitle()
    val notes = content.ifBlank {
        when (type.normalizedAiActionType()) {
            "CREATE_REMINDER" -> "Reminder created by AI"
            else -> ""
        }
    }
    val values = columns.associate { column ->
        val normalizedName = column.name.normalizedAiKey()
        val value = when {
            normalizedName in setOf("task", "name", "title", "item") -> title
            normalizedName in setOf("status", "progress") -> "Not started"
            column.type == PageTableColumnType.Date -> taskDateCellValue()
            normalizedName in setOf("notes", "note", "details") -> notes
            else -> cellValues.entries.firstOrNull { entry ->
                entry.key.normalizedAiKey() == normalizedName
            }?.value.orEmpty()
        }
        column.id to value
    }
    return PageBlockCodec.newTableRow(columns).copy(cells = values)
}

private fun ChatAction.taskDateCellValue(): String {
    val explicit = cellValues.entries.firstOrNull { entry ->
        entry.key.normalizedAiKey() in setOf("date", "due date", "deadline", "time", "reminder")
    }?.value.orEmpty()
    if (explicit.isNotBlank()) {
        return if (isCreateReminderAction()) {
            explicit.withReminderMetadata()
        } else {
            explicit
        }
    }

    val delayMinutes = delayMinutes ?: return ""
    val dateTime = LocalDateTime.now().plusMinutes(delayMinutes)
    return buildDateCellValue(
        dateTime = dateTime,
        reminder = if (isCreateReminderAction()) {
            PageTableDateReminder.AtTimeOfEvent
        } else {
            PageTableDateReminder.None
        },
    )
}

private fun buildDateCellValue(
    dateTime: LocalDateTime,
    reminder: PageTableDateReminder,
): String = AiDateCellJson.encodeToString(
    PageTableDateCellValue(
        startDate = dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
        startTime = dateTime.toLocalTime()
            .withSecond(0)
            .withNano(0)
            .format(DateTimeFormatter.ISO_LOCAL_TIME),
        includeTime = true,
        timezoneLabel = "Local",
        reminder = reminder,
    ),
)

private fun String.withReminderMetadata(): String {
    val parsed = if (trim().startsWith("{")) {
        runCatching {
            AiDateCellJson.decodeFromString<PageTableDateCellValue>(trim())
        }.getOrNull() ?: return this
    } else {
        PageTableDateCellValue(startDate = trim())
    }
    val defaultReminder = if (parsed.includeTime && parsed.startTime.isNotBlank()) {
        PageTableDateReminder.AtTimeOfEvent
    } else {
        PageTableDateReminder.OnDayOfEvent
    }
    return AiDateCellJson.encodeToString(
        parsed.copy(
            timezoneLabel = parsed.timezoneLabel.ifBlank { "Local" },
            reminder = parsed.reminder.takeUnless { reminder -> reminder == PageTableDateReminder.None }
                ?: defaultReminder,
        ),
    )
}

private fun ChatAction.isCreateReminderAction(): Boolean =
    type.normalizedAiActionType() == "CREATE_REMINDER"

private fun ChatAction.taskLikeTitle(): String {
    return title
        .ifBlank { rowTitle }
        .ifBlank { cellValues.entries.firstOrNull { entry -> entry.key.normalizedAiKey() in setOf("task", "name", "title", "item") }?.value.orEmpty() }
        .ifBlank { content }
        .ifBlank { "Untitled task" }
}

private fun List<PageBlock>.findTaskLikeTable(): PageBlock? {
    for (block in this) {
        if (block.type == PageBlockType.DatabaseTable && block.table.isTaskLikeTable()) return block
        block.children.findTaskLikeTable()?.let { return it }
    }
    return null
}

private fun PageTable.isTaskLikeTable(): Boolean {
    val titleHint = title.contains("task", ignoreCase = true) ||
        title.contains("reminder", ignoreCase = true) ||
        title.contains("todo", ignoreCase = true)
    val columnNames = columns.map { column -> column.name.normalizedAiKey() }.toSet()
    val hasTaskColumn = columnNames.any { name -> name in setOf("task", "name", "title", "item") }
    val hasDateColumn = columns.any { column ->
        column.type == PageTableColumnType.Date ||
            column.name.normalizedAiKey() in setOf("date", "due date", "deadline", "time", "reminder")
    }
    return titleHint || (hasTaskColumn && hasDateColumn)
}

private fun PageTableRow.cellText(column: PageTableColumn?): String {
    return column?.let { tableColumn -> cells[tableColumn.id] }.orEmpty().trim()
}

private fun String.toPageTableColumnTypeFromAi(): PageTableColumnType {
    return when (trim().lowercase()) {
        "number", "count", "amount", "price", "cost", "total" -> PageTableColumnType.Number
        "select", "option", "choice" -> PageTableColumnType.Select
        "multiselect", "multi select", "multi-select", "tags", "tag", "labels", "label" -> PageTableColumnType.MultiSelect
        "status", "stage", "state", "phase" -> PageTableColumnType.Status
        "date", "day", "deadline", "due", "time", "calendar", "reminder" -> PageTableColumnType.Date
        "checkbox", "check", "done", "complete", "completed", "boolean" -> PageTableColumnType.Checkbox
        "formula", "calculation", "calculate", "computed" -> PageTableColumnType.Formula
        "relation", "related", "link", "linkedrow", "linkedrows" -> PageTableColumnType.Relation
        "rollup", "aggregate", "aggregation" -> PageTableColumnType.Rollup
        else -> PageTableColumnType.Text
    }
}

private fun String.toPageTableDateFormat(): PageTableDateFormat {
    return when (trim().lowercase()) {
        "monthdayyear", "month/day/year", "mm/dd/yyyy" -> PageTableDateFormat.MonthDayYear
        "yearmonthday", "year/month/day", "yyyy-mm-dd", "yyyy/mm/dd" -> PageTableDateFormat.YearMonthDay
        else -> PageTableDateFormat.DayMonthYear
    }
}

private fun String.toPageTableTimeFormat(
    defaultFor: String,
    type: PageTableColumnType,
): PageTableTimeFormat {
    return when (trim().lowercase()) {
        "twelvehour", "12hour", "12 hour", "12-hour" -> PageTableTimeFormat.TwelveHour
        "twentyfourhour", "24hour", "24 hour", "24-hour" -> PageTableTimeFormat.TwentyFourHour
        "hidden", "none", "off" -> PageTableTimeFormat.Hidden
        else -> if (type == PageTableColumnType.Date && defaultFor.containsTimeIntent()) {
            PageTableTimeFormat.TwelveHour
        } else {
            PageTableTimeFormat.Hidden
        }
    }
}

private fun String.toPageTableDateReminder(
    defaultFor: String,
    type: PageTableColumnType,
): PageTableDateReminder {
    return when (trim().lowercase()) {
        "none", "off" -> PageTableDateReminder.None
        "attimeofevent", "at time of event", "time" -> PageTableDateReminder.AtTimeOfEvent
        "fiveminutesbefore", "5 minutes before", "5 minute before", "5 min before" -> PageTableDateReminder.FiveMinutesBefore
        "tenminutesbefore", "10 minutes before", "10 minute before", "10 min before" -> PageTableDateReminder.TenMinutesBefore
        "fifteenminutesbefore", "15 minutes before", "15 minute before", "15 min before" -> PageTableDateReminder.FifteenMinutesBefore
        "thirtyminutesbefore", "30 minutes before", "30 minute before", "30 min before" -> PageTableDateReminder.ThirtyMinutesBefore
        "onehourbefore", "1 hour before", "hour before" -> PageTableDateReminder.OneHourBefore
        "twohoursbefore", "2 hours before", "2 hour before" -> PageTableDateReminder.TwoHoursBefore
        "onedaybefore", "1 day before", "day before" -> PageTableDateReminder.OneDayBefore
        "twodaysbefore", "2 days before", "2 day before" -> PageTableDateReminder.TwoDaysBefore
        "oneweekbefore", "1 week before", "week before" -> PageTableDateReminder.OneWeekBefore
        "ondayofevent", "on day of event", "same day" -> PageTableDateReminder.OnDayOfEvent
        else -> PageTableDateReminder.None
    }
}

private fun String.containsTimeIntent(): Boolean {
    val normalized = normalizedAiKey()
    return normalized.contains("time") ||
        normalized.contains("reminder") ||
        normalized.contains("deadline") ||
        normalized.contains("due")
}

private fun String.toPageTableRollupAggregationFromAi() = when (trim().lowercase()) {
    "sum" -> com.changeyourlife.cyl.domain.model.PageTableRollupAggregation.Sum
    "average", "avg" -> com.changeyourlife.cyl.domain.model.PageTableRollupAggregation.Average
    "min", "minimum" -> com.changeyourlife.cyl.domain.model.PageTableRollupAggregation.Min
    "max", "maximum" -> com.changeyourlife.cyl.domain.model.PageTableRollupAggregation.Max
    else -> com.changeyourlife.cyl.domain.model.PageTableRollupAggregation.Count
}

private fun String.normalizedAiActionType(): String = trim().uppercase().replace(' ', '_')

private fun String.normalizedAiKey(): String = trim().lowercase().replace("_", " ")

private val AiDateCellJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
