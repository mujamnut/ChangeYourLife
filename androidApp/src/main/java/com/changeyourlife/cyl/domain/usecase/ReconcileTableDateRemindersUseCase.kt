package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.Reminder
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ReconcileTableDateRemindersUseCase @Inject constructor(
    private val scheduleTableDateReminderUseCase: ScheduleTableDateReminderUseCase,
) {
    suspend operator fun invoke(
        previousPage: Page,
        currentPage: Page,
        previousDocument: PageBlockDocument,
        currentDocument: PageBlockDocument,
    ): List<Reminder> {
        val previousTargets = previousDocument.reminderTargets(previousPage.title)
        val currentTargets = currentDocument.reminderTargets(currentPage.title)

        (previousTargets.keys - currentTargets.keys).forEach { key ->
            scheduleTableDateReminderUseCase.cancel(
                page = previousPage,
                tableBlockId = key.tableBlockId,
                rowId = key.rowId,
                columnId = key.columnId,
            )
        }

        return buildList {
            currentTargets.forEach { (key, target) ->
                if (previousTargets[key] == target) return@forEach
                scheduleTableDateReminderUseCase(
                    page = currentPage,
                    document = currentDocument,
                    tableBlockId = key.tableBlockId,
                    rowId = key.rowId,
                    columnId = key.columnId,
                    value = target.value,
                )?.let(::add)
            }
        }
    }

    suspend fun scheduleAll(
        page: Page,
        document: PageBlockDocument,
    ): List<Reminder> {
        return invoke(
            previousPage = page,
            currentPage = page,
            previousDocument = PageBlockDocument(),
            currentDocument = document,
        )
    }

    suspend fun cancelAll(
        page: Page,
        document: PageBlockDocument,
    ) {
        invoke(
            previousPage = page,
            currentPage = page,
            previousDocument = document,
            currentDocument = PageBlockDocument(),
        )
    }
}

private data class TableDateReminderKey(
    val tableBlockId: String,
    val rowId: String,
    val columnId: String,
)

private data class TableDateReminderTarget(
    val value: String,
    val pageTitle: String,
    val tableTitle: String,
    val rowTitle: String,
    val columnName: String,
    val defaultReminder: String,
    val timezoneLabel: String,
)

private fun PageBlockDocument.reminderTargets(
    pageTitle: String,
): Map<TableDateReminderKey, TableDateReminderTarget> {
    return buildMap {
        blocks.collectReminderTargets(pageTitle, this)
    }
}

private fun List<PageBlock>.collectReminderTargets(
    pageTitle: String,
    destination: MutableMap<TableDateReminderKey, TableDateReminderTarget>,
) {
    forEach { block ->
        if (block.type == PageBlockType.DatabaseTable || block.type == PageBlockType.Table) {
            block.table.collectReminderTargets(
                pageTitle = pageTitle,
                tableBlockId = block.id,
                destination = destination,
            )
        }
        block.children.collectReminderTargets(pageTitle, destination)
    }
}

private fun PageTable.collectReminderTargets(
    pageTitle: String,
    tableBlockId: String,
    destination: MutableMap<TableDateReminderKey, TableDateReminderTarget>,
) {
    val primaryColumnId = columns.firstOrNull()?.id
    columns
        .filter { column -> column.type == PageTableColumnType.Date }
        .forEach { column ->
            rows.forEach rowLoop@ { row ->
                val value = row.cells[column.id].orEmpty()
                val reminderConfig = value.activeReminderConfig(
                    defaultReminder = column.dateReminder,
                    defaultTimezone = column.timezoneLabel,
                ) ?: return@rowLoop
                destination[
                    TableDateReminderKey(
                        tableBlockId = tableBlockId,
                        rowId = row.id,
                        columnId = column.id,
                    ),
                ] = TableDateReminderTarget(
                    value = value,
                    pageTitle = pageTitle,
                    tableTitle = title,
                    rowTitle = primaryColumnId?.let { columnId -> row.cells[columnId] }.orEmpty(),
                    columnName = column.name,
                    defaultReminder = reminderConfig.reminder.name,
                    timezoneLabel = reminderConfig.timezoneLabel,
                )
            }
        }
}

private data class ActiveDateReminderConfig(
    val reminder: PageTableDateReminder,
    val timezoneLabel: String,
)

private fun String.activeReminderConfig(
    defaultReminder: PageTableDateReminder,
    defaultTimezone: String,
): ActiveDateReminderConfig? {
    val value = trim()
    if (value.isBlank()) return null
    if (value.startsWith("{") && value.endsWith("}")) {
        val dateCell = runCatching {
            Json.decodeFromString<PageTableDateCellValue>(value)
        }.getOrNull() ?: return null
        if (dateCell.startDate.isBlank() || dateCell.reminder == PageTableDateReminder.None) {
            return null
        }
        return ActiveDateReminderConfig(
            reminder = dateCell.reminder,
            timezoneLabel = dateCell.timezoneLabel,
        )
    }
    if (defaultReminder == PageTableDateReminder.None) return null
    return ActiveDateReminderConfig(
        reminder = defaultReminder,
        timezoneLabel = defaultTimezone,
    )
}
