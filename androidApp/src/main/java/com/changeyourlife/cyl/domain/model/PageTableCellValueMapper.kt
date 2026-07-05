package com.changeyourlife.cyl.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val pageTableCellValueJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun PageTableColumn.toTypedCellValue(displayValue: String): PageTableCellValue {
    return displayValue.toTypedCellValue(type)
}

fun String.toTypedCellValue(type: PageTableColumnType): PageTableCellValue {
    val normalized = trim()
    return when (type) {
        PageTableColumnType.Text -> PageTableCellValue(type = type, text = this)
        PageTableColumnType.Number -> PageTableCellValue(type = type, number = normalized)
        PageTableColumnType.Select -> PageTableCellValue(type = type, text = normalized)
        PageTableColumnType.MultiSelect -> PageTableCellValue(type = type, text = normalized.toPageTableChoiceListValue())
        PageTableColumnType.Status -> PageTableCellValue(type = type, text = normalized)
        PageTableColumnType.Date -> PageTableCellValue(type = type, date = normalized.toPageTableDateCellValue())
        PageTableColumnType.FilesMedia -> PageTableCellValue(
            type = type,
            text = normalized,
            files = normalized.toPageTableFilesCellValue(),
        )
        PageTableColumnType.Checkbox -> PageTableCellValue(
            type = type,
            checked = normalized.equals("true", ignoreCase = true),
        )
        PageTableColumnType.Relation -> PageTableCellValue(
            type = type,
            relationRowIds = normalized
                .split(",")
                .map { value -> value.trim() }
                .filter { value -> value.isNotBlank() },
        )
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> PageTableCellValue(type = type, text = normalized)
    }
}

fun PageTableCellValue.displayValue(fallback: String = ""): String {
    return when (type) {
        PageTableColumnType.Text -> text
        PageTableColumnType.Number -> number
        PageTableColumnType.Select -> text
        PageTableColumnType.MultiSelect -> text
        PageTableColumnType.Status -> text
        PageTableColumnType.Date -> date.toStorageValue()
        PageTableColumnType.FilesMedia -> if (files.isNotEmpty()) {
            pageTableCellValueJson.encodeToString(files)
        } else {
            text
        }
        PageTableColumnType.Checkbox -> if (checked) "true" else ""
        PageTableColumnType.Relation -> relationRowIds.joinToString(",")
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> text.ifBlank { fallback }
    }
}

fun PageTableCellValue.withColumnType(type: PageTableColumnType, fallback: String = ""): PageTableCellValue {
    return displayValue(fallback).toTypedCellValue(type)
}

private fun String.toPageTableDateCellValue(): PageTableDateCellValue {
    if (isBlank()) return PageTableDateCellValue()
    if (startsWith("{") && endsWith("}")) {
        return runCatching {
            pageTableCellValueJson.decodeFromString<PageTableDateCellValue>(this)
        }.getOrDefault(PageTableDateCellValue(startDate = this))
    }
    return PageTableDateCellValue(startDate = this)
}

private fun String.toPageTableFilesCellValue(): List<PageMediaAttachment> {
    if (isBlank()) return emptyList()
    return runCatching {
        pageTableCellValueJson.decodeFromString<List<PageMediaAttachment>>(this)
    }.getOrDefault(emptyList())
}

private fun String.toPageTableChoiceListValue(): String {
    return split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinctBy { value -> value.lowercase() }
        .joinToString(", ")
}

private fun PageTableDateCellValue.toStorageValue(): String {
    if (startDate.isBlank()) return ""
    val isPlainDate = !includeTime &&
        !includeEndDate &&
        startTime.isBlank() &&
        endDate.isBlank() &&
        timezoneLabel == "Local"
    return if (isPlainDate) {
        startDate
    } else {
        pageTableCellValueJson.encodeToString(this)
    }
}
