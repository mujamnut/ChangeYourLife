package com.changeyourlife.cyl.domain.usecase

import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockDocument
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateCellValue
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.Reminder
import com.changeyourlife.cyl.domain.repository.ReminderRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ScheduleTableDateReminderUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
) {
    suspend operator fun invoke(
        page: Page,
        document: PageBlockDocument,
        tableBlockId: String,
        rowId: String,
        columnId: String,
        value: String,
    ): Reminder? {
        val tableBlock = document.findTableBlock(tableBlockId)
        if (tableBlock == null) {
            cancel(page, tableBlockId, rowId, columnId)
            return null
        }
        val table = tableBlock.table
        val column = table.columns.firstOrNull { it.id == columnId }
        if (column == null || column.type != PageTableColumnType.Date) {
            cancel(page, tableBlockId, rowId, columnId)
            return null
        }
        val row = table.rows.firstOrNull { it.id == rowId }
        if (row == null) {
            cancel(page, tableBlockId, rowId, columnId)
            return null
        }
        val parsed = value.parseDateCell()
        val dateValue = parsed.value
        val effectiveReminder = if (parsed.hasMetadata) dateValue.reminder else column.dateReminder
        val effectiveTimezone = if (parsed.hasMetadata) dateValue.timezoneLabel else column.timezoneLabel
        val remindAt = dateValue.toReminderInstant(effectiveReminder, effectiveTimezone)?.toEpochMilli()

        if (remindAt == null || remindAt <= System.currentTimeMillis()) {
            cancel(page, tableBlockId, rowId, columnId)
            return null
        }

        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = reminderId(page.id, tableBlockId, rowId, columnId),
            workspaceId = page.workspaceId,
            pageId = page.id,
            taskId = null,
            title = table.reminderTitle(row, column, page),
            remindAt = remindAt,
            isDone = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        reminderRepository.upsertReminder(reminder)
        return reminder
    }

    suspend fun cancel(
        page: Page,
        tableBlockId: String,
        rowId: String,
        columnId: String,
    ) {
        val now = System.currentTimeMillis()
        reminderRepository.upsertReminder(
            Reminder(
                id = reminderId(page.id, tableBlockId, rowId, columnId),
                workspaceId = page.workspaceId,
                pageId = page.id,
                taskId = null,
                title = "Table date reminder",
                remindAt = now,
                isDone = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = now,
            ),
        )
    }

    suspend fun cancelColumn(
        page: Page,
        table: PageTable,
        tableBlockId: String,
        columnId: String,
    ) {
        table.rows.forEach { row ->
            cancel(page, tableBlockId, row.id, columnId)
        }
    }

    suspend fun cancelRow(
        page: Page,
        table: PageTable,
        tableBlockId: String,
        rowId: String,
    ) {
        table.columns
            .filter { column -> column.type == PageTableColumnType.Date }
            .forEach { column -> cancel(page, tableBlockId, rowId, column.id) }
    }

    private fun PageTableDateCellValue.toReminderInstant(
        reminder: PageTableDateReminder,
        timezoneLabel: String,
    ): Instant? {
        if (reminder == PageTableDateReminder.None) return null
        val date = startDate.toLocalDateOrNull() ?: return null
        val time = startTime.toLocalTimeOrNull()
        val eventDateTime = when {
            includeTime && time != null -> date.atTime(time)
            else -> date.atTime(DefaultReminderTime)
        }
        val zoneId = timezoneLabel.toZoneIdOrLocal()
        val now = Instant.now()
        val onDayDateTime = date.atTime(DefaultReminderTime)
        val reminderDateTime = when (reminder) {
            PageTableDateReminder.None -> return null
            PageTableDateReminder.AtTimeOfEvent -> eventDateTime
            PageTableDateReminder.FiveMinutesBefore -> eventDateTime.minusMinutes(5)
            PageTableDateReminder.TenMinutesBefore -> eventDateTime.minusMinutes(10)
            PageTableDateReminder.FifteenMinutesBefore -> eventDateTime.minusMinutes(15)
            PageTableDateReminder.ThirtyMinutesBefore -> eventDateTime.minusMinutes(30)
            PageTableDateReminder.OneHourBefore -> eventDateTime.minusHours(1)
            PageTableDateReminder.TwoHoursBefore -> eventDateTime.minusHours(2)
            PageTableDateReminder.OnDayOfEvent -> {
                val onDayInstant = onDayDateTime.atZone(zoneId).toInstant()
                val eventInstant = eventDateTime.atZone(zoneId).toInstant()
                if (includeTime && time != null && onDayInstant <= now && eventInstant > now) {
                    eventDateTime
                } else {
                    onDayDateTime
                }
            }
            PageTableDateReminder.OneDayBefore -> eventDateTime.minusDays(1)
            PageTableDateReminder.TwoDaysBefore -> eventDateTime.minusDays(2)
            PageTableDateReminder.OneWeekBefore -> eventDateTime.minusWeeks(1)
        }
        return reminderDateTime.atZone(zoneId).toInstant()
    }

    private fun String.parseDateCell(): ParsedDateCell {
        val trimmed = trim()
        if (trimmed.isBlank()) return ParsedDateCell(PageTableDateCellValue(), hasMetadata = false)
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return ParsedDateCell(
                value = runCatching {
                    Json.decodeFromString<PageTableDateCellValue>(trimmed)
                }.getOrDefault(PageTableDateCellValue()),
                hasMetadata = true,
            )
        }
        return ParsedDateCell(
            value = PageTableDateCellValue(
                startDate = trimmed.toLocalDateOrNull()
                    ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    ?: trimmed,
            ),
            hasMetadata = false,
        )
    }

    private fun PageBlockDocument.findTableBlock(tableBlockId: String): PageBlock? {
        return blocks.findTableBlock(tableBlockId)
    }

    private fun List<PageBlock>.findTableBlock(tableBlockId: String): PageBlock? {
        for (block in this) {
            if (block.id == tableBlockId) return block
            block.children.findTableBlock(tableBlockId)?.let { return it }
        }
        return null
    }

    private fun PageTable.reminderTitle(row: PageTableRow, column: PageTableColumn, page: Page): String {
        val primaryColumn = columns.firstOrNull()
        val rowTitle = primaryColumn
            ?.let { row.cells[it.id].orEmpty() }
            ?.trim()
            .orEmpty()
            .ifBlank { "Untitled row" }
        val tableName = title.ifBlank { page.title.ifBlank { "Database" } }
        val columnName = column.name.ifBlank { "Date" }
        return "$rowTitle · $columnName · $tableName"
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
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

    private fun String.toLocalTimeOrNull(): LocalTime? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm", Locale.US),
            DateTimeFormatter.ofPattern("HH:mm", Locale.US),
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        )
        return formatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalTime.parse(trimmed.uppercase(Locale.US), formatter) }.getOrNull()
        }
    }

    private fun String.toZoneIdOrLocal(): ZoneId {
        val normalized = trim()
        if (normalized.isBlank() || normalized.equals("Local", ignoreCase = true)) {
            return ZoneId.systemDefault()
        }
        if (normalized.startsWith("GMT", ignoreCase = true)) {
            val offset = normalized.removePrefix("GMT").removePrefix("gmt").ifBlank { "+0" }
            val normalizedOffset = offset.toNormalizedZoneOffset()
            return runCatching { ZoneOffset.of(normalizedOffset) }.getOrDefault(ZoneId.systemDefault())
        }
        return runCatching { ZoneId.of(normalized) }.getOrDefault(ZoneId.systemDefault())
    }

    private fun String.toNormalizedZoneOffset(): String {
        val trimmed = trim()
        val sign = when {
            trimmed.startsWith("-") -> "-"
            else -> "+"
        }
        val body = trimmed.removePrefix("+").removePrefix("-")
        val parts = body.split(":", limit = 2)
        val hours = parts.getOrNull(0).orEmpty().ifBlank { "0" }.padStart(2, '0')
        val minutes = parts.getOrNull(1).orEmpty().ifBlank { "00" }.padStart(2, '0')
        return "$sign$hours:$minutes"
    }

    private fun reminderId(pageId: String, tableBlockId: String, rowId: String, columnId: String): String {
        return "table-date:$pageId:$tableBlockId:$rowId:$columnId"
    }

    private data class ParsedDateCell(
        val value: PageTableDateCellValue,
        val hasMetadata: Boolean,
    )

    private companion object {
        val DefaultReminderTime: LocalTime = LocalTime.of(9, 0)
    }
}
