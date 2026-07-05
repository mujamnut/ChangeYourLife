package com.changeyourlife.cyl.presentation.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import com.changeyourlife.cyl.domain.model.Page
import com.changeyourlife.cyl.domain.model.PageBlock
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType
import com.changeyourlife.cyl.domain.model.PageMediaAttachment
import com.changeyourlife.cyl.domain.model.PageProperty
import com.changeyourlife.cyl.domain.model.PagePropertyType
import com.changeyourlife.cyl.domain.model.DefaultPageTableStatusOptions
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableFilter
import com.changeyourlife.cyl.domain.model.PageTableFilterOperator
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSelectOption
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTextSpan
import com.changeyourlife.cyl.domain.model.displayValue
import com.changeyourlife.cyl.domain.model.isActive
import com.changeyourlife.cyl.domain.model.withColumnType
import com.changeyourlife.cyl.presentation.ai.AiChatMode
import com.changeyourlife.cyl.presentation.ai.AiChatSheet
import com.changeyourlife.cyl.presentation.ai.AiChatMessage
import com.changeyourlife.cyl.presentation.ai.AiChatPageLink
import com.changeyourlife.cyl.presentation.components.CylBottomCommandBar
import com.changeyourlife.cyl.presentation.components.CylChromeIconButton
import com.changeyourlife.cyl.presentation.components.CylFloatingChromeSurface
import com.changeyourlife.cyl.presentation.home.HomeUiState
import com.changeyourlife.cyl.presentation.theme.ChangeYourLifeTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun blockTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)

@Composable
internal fun plainBlockTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
internal fun SubpageSectionHeader(
    onCreateChildPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Subpages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = onCreateChildPage) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
            )
            Text(
                text = "New",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
internal fun CenterMessage(
    text: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun List<PageBlock>.containsBlockId(blockId: String): Boolean {
    return any { block ->
        block.id == blockId || block.children.containsBlockId(blockId)
    }
}

internal fun List<PageBlock>.indexOfSearchTarget(
    targetType: String,
    targetId: String,
): Int {
    if (targetType.isBlank() || targetType == SearchTargetPageTitle) return -1
    if (targetId.isBlank() && targetType != SearchTargetPageTitle) return -1
    return indexOfFirst { block -> block.containsSearchTarget(targetType, targetId) }
}

internal fun PageBlock.containsSearchTarget(
    targetType: String,
    targetId: String,
): Boolean {
    return isDirectSearchTarget(targetType, targetId) ||
        ((type == PageBlockType.DatabaseTable || type == PageBlockType.Table) &&
            table.highlightedRowId(targetType, targetId) != null) ||
        children.any { child -> child.containsSearchTarget(targetType, targetId) }
}

internal fun PageBlock.isDirectSearchTarget(
    targetType: String,
    targetId: String,
): Boolean {
    return targetType == SearchTargetBlock && targetId == id
}

internal fun PageTable.highlightedRowId(
    targetType: String,
    targetId: String,
): String? {
    return when (targetType) {
        SearchTargetRow -> rows.firstOrNull { row -> row.id == targetId }?.id
        SearchTargetRowBlock -> rows.firstOrNull { row -> row.blocks.containsBlockId(targetId) }?.id
        else -> null
    }
}

internal const val SearchTargetPageTitle = "title"
internal const val SearchTargetBlock = "block"
internal const val SearchTargetRow = "row"
internal const val SearchTargetRowBlock = "row_block"

internal fun Uri.toPageMediaAttachment(context: Context): PageMediaAttachment? {
    val resolver = context.contentResolver
    var displayName = lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { value -> value.isNotBlank() }
        ?: "Selected file"
    var sizeBytes = 0L

    runCatching {
        resolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex).orEmpty().ifBlank { displayName }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                }
            }
        }
    }

    return PageMediaAttachment(
        id = UUID.randomUUID().toString(),
        uri = toString(),
        name = displayName,
        mimeType = resolver.getType(this).orEmpty(),
        sizeBytes = sizeBytes,
    )
}

internal val TableMediaAttachmentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun String.toTableMediaAttachments(): List<PageMediaAttachment> {
    if (isBlank()) return emptyList()
    return runCatching {
        TableMediaAttachmentJson.decodeFromString<List<PageMediaAttachment>>(this)
    }.getOrDefault(emptyList())
}

internal fun List<PageMediaAttachment>.toTableMediaCellValue(): String {
    return if (isEmpty()) {
        ""
    } else {
        TableMediaAttachmentJson.encodeToString(this)
    }
}

internal fun List<PageMediaAttachment>.toTableMediaSummary(): String {
    return when (size) {
        0 -> "Attach file"
        1 -> first().name.ifBlank { "1 file" }
        else -> "$size files"
    }
}

internal fun Context.persistMediaReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

internal fun Context.openMediaAttachment(attachment: PageMediaAttachment) {
    val uri = Uri.parse(attachment.uri)
    val mimeType = attachment.mimeType.ifBlank { "*/*" }
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show()
    }
}

internal fun Long.formatFileSize(): String {
    if (this <= 0L) return "unknown size"
    val units = listOf("B", "KB", "MB", "GB")
    var size = toDouble()
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${size.toLong()} ${units[unitIndex]}"
    } else {
        "%.1f %s".format(Locale.US, size, units[unitIndex])
    }
}

internal val AiAutofillPropertyTypes = listOf(
    PagePropertyType.Summarize,
    PagePropertyType.Translate,
)

internal val CorePropertyTypes = listOf(
    PagePropertyType.Text,
    PagePropertyType.Number,
    PagePropertyType.Select,
    PagePropertyType.MultiSelect,
    PagePropertyType.Status,
    PagePropertyType.Date,
    PagePropertyType.Person,
    PagePropertyType.FilesMedia,
    PagePropertyType.Checkbox,
    PagePropertyType.Url,
    PagePropertyType.Email,
    PagePropertyType.Phone,
    PagePropertyType.Formula,
    PagePropertyType.Relation,
    PagePropertyType.Rollup,
    PagePropertyType.CreatedTime,
    PagePropertyType.CreatedBy,
    PagePropertyType.LastEditedTime,
    PagePropertyType.LastEditedBy,
    PagePropertyType.Button,
    PagePropertyType.Place,
    PagePropertyType.Id,
)

internal val ConnectionPropertyTypes = listOf(
    PagePropertyType.GoogleDriveFile,
    PagePropertyType.FigmaFile,
    PagePropertyType.GitHubPullRequests,
    PagePropertyType.ZendeskTicket,
)

internal val PagePropertyType.label: String
    get() = when (this) {
        PagePropertyType.Summarize -> "Summarize"
        PagePropertyType.Translate -> "Translate"
        PagePropertyType.Text -> "Text"
        PagePropertyType.Number -> "Number"
        PagePropertyType.Select -> "Select"
        PagePropertyType.MultiSelect -> "Multi-select"
        PagePropertyType.Status -> "Status"
        PagePropertyType.Date -> "Date"
        PagePropertyType.Person -> "Person"
        PagePropertyType.FilesMedia -> "Files & media"
        PagePropertyType.Checkbox -> "Checkbox"
        PagePropertyType.Url -> "URL"
        PagePropertyType.Email -> "Email"
        PagePropertyType.Phone -> "Phone"
        PagePropertyType.Formula -> "Formula"
        PagePropertyType.Relation -> "Relation"
        PagePropertyType.Rollup -> "Rollup"
        PagePropertyType.CreatedTime -> "Created time"
        PagePropertyType.CreatedBy -> "Created by"
        PagePropertyType.LastEditedTime -> "Last edited time"
        PagePropertyType.LastEditedBy -> "Last edited by"
        PagePropertyType.Button -> "Button"
        PagePropertyType.Place -> "Place"
        PagePropertyType.Id -> "ID"
        PagePropertyType.GoogleDriveFile -> "Google Drive File"
        PagePropertyType.FigmaFile -> "Figma File"
        PagePropertyType.GitHubPullRequests -> "GitHub Pull Requests"
        PagePropertyType.ZendeskTicket -> "Zendesk Ticket"
    }

internal val PagePropertyType.symbol: String
    get() = when (this) {
        PagePropertyType.Summarize -> "AI"
        PagePropertyType.Translate -> "A"
        PagePropertyType.Text -> "T"
        PagePropertyType.Number -> "#"
        PagePropertyType.Select -> "v"
        PagePropertyType.MultiSelect -> "[]"
        PagePropertyType.Status -> "*"
        PagePropertyType.Date -> "Cal"
        PagePropertyType.Person -> "@"
        PagePropertyType.FilesMedia -> "F"
        PagePropertyType.Checkbox -> "OK"
        PagePropertyType.Url -> "URL"
        PagePropertyType.Email -> "Mail"
        PagePropertyType.Phone -> "Tel"
        PagePropertyType.Formula -> "Fx"
        PagePropertyType.Relation -> "Rel"
        PagePropertyType.Rollup -> "Roll"
        PagePropertyType.CreatedTime -> "CT"
        PagePropertyType.CreatedBy -> "CB"
        PagePropertyType.LastEditedTime -> "ET"
        PagePropertyType.LastEditedBy -> "EB"
        PagePropertyType.Button -> "Btn"
        PagePropertyType.Place -> "Pin"
        PagePropertyType.Id -> "ID"
        PagePropertyType.GoogleDriveFile -> "G"
        PagePropertyType.FigmaFile -> "Fig"
        PagePropertyType.GitHubPullRequests -> "GH"
        PagePropertyType.ZendeskTicket -> "Zen"
    }

internal val PagePropertyType.valuePlaceholder: String
    get() = when (this) {
        PagePropertyType.Number -> "0"
        PagePropertyType.Select -> "Option"
        PagePropertyType.MultiSelect -> "Option 1, Option 2"
        PagePropertyType.Status -> "Not started"
        PagePropertyType.Date,
        PagePropertyType.CreatedTime,
        PagePropertyType.LastEditedTime,
        -> "YYYY-MM-DD"
        PagePropertyType.Email -> "name@example.com"
        PagePropertyType.Phone -> "+60"
        PagePropertyType.Url -> "https://"
        PagePropertyType.Place -> "Place"
        PagePropertyType.FilesMedia -> "File or media reference"
        PagePropertyType.Relation -> "Related page"
        PagePropertyType.Rollup -> "Rollup value"
        PagePropertyType.Formula -> "Formula result"
        PagePropertyType.Person,
        PagePropertyType.CreatedBy,
        PagePropertyType.LastEditedBy,
        -> "Person"
        PagePropertyType.Button -> "Button action"
        PagePropertyType.Id -> "ID"
        PagePropertyType.Summarize -> "Summary"
        PagePropertyType.Translate -> "Translation"
        PagePropertyType.GoogleDriveFile -> "Google Drive file"
        PagePropertyType.FigmaFile -> "Figma file"
        PagePropertyType.GitHubPullRequests -> "GitHub pull request"
        PagePropertyType.ZendeskTicket -> "Zendesk ticket"
        PagePropertyType.Text,
        PagePropertyType.Checkbox,
        -> "Value"
    }

internal val PagePropertyType.isBasicAiType: Boolean
    get() = this == PagePropertyType.Summarize || this == PagePropertyType.Translate

internal data class TableViewOption(
    val view: PageTableView,
    val label: String,
) {
    companion object {
        val entries = listOf(
            TableViewOption(PageTableView.Table, "Table"),
            TableViewOption(PageTableView.List, "List"),
            TableViewOption(PageTableView.Board, "Board"),
            TableViewOption(PageTableView.Calendar, "Calendar"),
            TableViewOption(PageTableView.Gallery, "Gallery"),
            TableViewOption(PageTableView.Timeline, "Timeline"),
            TableViewOption(PageTableView.Dashboard, "Dashboard"),
        )
    }
}

internal data class PageTableReference(
    val blockId: String,
    val title: String,
    val table: PageTable,
    val pageId: String = "",
    val pageTitle: String = "",
)

internal data class TableRowSummary(
    val row: PageTableRow,
    val title: String,
    val status: String,
    val date: String,
    val endDate: String,
    val details: String,
    val timelineDetails: String,
)

internal data class DashboardStat(
    val label: String,
    val value: String,
)

internal val TableCellWidth = TableGridTokens.dimensions.cellWidth
internal val TableActionWidth = TableGridTokens.dimensions.actionWidth
internal val TableAddColumnWidth = TableGridTokens.dimensions.addColumnWidth
internal val TableHeaderHeight = TableGridTokens.dimensions.headerHeight
internal val TableRowHeight = TableGridTokens.dimensions.rowHeight
internal val TableGroupHeaderWidth = TableGridTokens.dimensions.groupHeaderWidth
internal val BoardColumnWidth = TableGridTokens.dimensions.boardColumnWidth
internal val CalendarDayWidth = TableGridTokens.dimensions.calendarDayWidth
internal val GalleryItemWidth = TableGridTokens.dimensions.galleryItemWidth
internal val TimelineDateWidth = TableGridTokens.dimensions.timelineDateWidth
internal val DashboardStatWidth = TableGridTokens.dimensions.dashboardStatWidth
internal val DashboardLabelWidth = TableGridTokens.dimensions.dashboardLabelWidth
internal val DashboardCountWidth = TableGridTokens.dimensions.dashboardCountWidth
internal val PropertySymbolWidth = TableGridTokens.dimensions.propertySymbolWidth
internal val TableStatusOptions = listOf("Not started", "In progress", "Done", "Blocked")
internal val TableWeekdayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
internal val TableTimeOptions = List(48) { index -> LocalTime.MIDNIGHT.plusMinutes(index * 30L) }
internal val TableQuickTimeOptions = listOf(
    LocalTime.of(8, 0),
    LocalTime.of(9, 0),
    LocalTime.of(10, 0),
    LocalTime.of(12, 0),
    LocalTime.of(14, 0),
    LocalTime.of(18, 0),
    LocalTime.of(20, 0),
)
internal val TableTimezoneOptions = listOf("Local", "GMT+0", "GMT+2", "GMT+8")
internal const val NoStatusLabel = "No status"
internal const val NoDateLabel = "No date"
internal const val CheckboxValueChecked = "true"

internal val PageTableColumn.statusOptions: List<String>
    get() = choiceOptions.map { option -> option.name }

internal val PageTableColumn.choiceOptions: List<PageTableSelectOption>
    get() = config.options
        .mapNotNull { option ->
            option
                .copy(name = option.name.trim())
                .takeIf { it.name.isNotBlank() }
        }
        .distinctBy { option -> option.name.lowercase() }
        .ifEmpty {
            if (type == PageTableColumnType.Status) DefaultPageTableStatusOptions else emptyList()
        }

internal val PageTableColumn.choiceOptionNames: List<String>
    get() = choiceOptions
        .map { option -> option.name.trim() }
        .filter { name -> name.isNotBlank() }

internal fun String.selectedChoiceValues(): List<String> {
    return split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinctBy { value -> value.lowercase() }
}

internal fun List<String>.toChoiceCellValue(): String {
    return filter { value -> value.isNotBlank() }
        .distinctBy { value -> value.lowercase() }
        .joinToString(", ")
}

@Serializable
internal data class TableDateCellValue(
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val includeEndDate: Boolean = false,
    val includeTime: Boolean = false,
    val timezoneLabel: String = "Local",
    val reminder: PageTableDateReminder = PageTableDateReminder.OnDayOfEvent,
)

internal val TableDateCellJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun String.toTableDateCellValue(): TableDateCellValue {
    val trimmed = trim()
    if (trimmed.isBlank()) return TableDateCellValue()
    if (trimmed.startsWith("{")) {
        return runCatching {
            TableDateCellJson.decodeFromString<TableDateCellValue>(trimmed)
        }.getOrDefault(TableDateCellValue())
    }
    val parsedDate = trimmed.toLocalDateOrNull()
    return TableDateCellValue(
        startDate = parsedDate
            ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            ?: trimmed,
    )
}

internal fun TableDateCellValue.toTableDateCellStorageValue(): String {
    if (startDate.isBlank()) return ""
    val isPlainDate = !includeTime &&
        !includeEndDate &&
        startTime.isBlank() &&
        endDate.isBlank() &&
        timezoneLabel == "Local" &&
        reminder == PageTableDateReminder.OnDayOfEvent
    return if (isPlainDate) {
        startDate
    } else {
        TableDateCellJson.encodeToString(this)
    }
}

internal fun PageTableColumn.displayDateCellValue(rawValue: String): String {
    val value = rawValue.toTableDateCellValue()
    val date = value.startDate.toLocalDateOrNull() ?: return value.startDate
    val startParts = mutableListOf(date.formatForColumn(dateFormat))
    if (value.includeTime && timeFormat != PageTableTimeFormat.Hidden) {
        val time = value.startTime.toLocalTimeOrNull()
        if (time != null) {
            startParts += time.formatForColumn(timeFormat.visibleOrDefault())
        }
    }
    val startText = startParts.joinToString(" ")
    if (!value.includeEndDate) return startText

    val endDate = value.endDate.toLocalDateOrNull() ?: return startText
    val endParts = mutableListOf(endDate.formatForColumn(dateFormat))
    if (value.includeTime && timeFormat != PageTableTimeFormat.Hidden) {
        val endTime = value.endTime.toLocalTimeOrNull()
        if (endTime != null) {
            endParts += endTime.formatForColumn(timeFormat.visibleOrDefault())
        }
    }
    return "$startText - ${endParts.joinToString(" ")}"
}

internal fun LocalDate.formatForColumn(format: PageTableDateFormat): String {
    val pattern = when (format) {
        PageTableDateFormat.DayMonthYear -> "dd/MM/yyyy"
        PageTableDateFormat.MonthDayYear -> "MM/dd/yyyy"
        PageTableDateFormat.YearMonthDay -> "yyyy-MM-dd"
    }
    return format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

internal fun LocalTime.formatForColumn(format: PageTableTimeFormat): String {
    val pattern = when (format.visibleOrDefault()) {
        PageTableTimeFormat.TwelveHour -> "h:mm a"
        PageTableTimeFormat.TwentyFourHour -> "HH:mm"
        PageTableTimeFormat.Hidden -> "h:mm a"
    }
    return format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

internal fun PageTableTimeFormat.visibleOrDefault(): PageTableTimeFormat {
    return if (this == PageTableTimeFormat.Hidden) {
        PageTableTimeFormat.TwelveHour
    } else {
        this
    }
}

internal val PageTableDateFormat.label: String
    get() = when (this) {
        PageTableDateFormat.DayMonthYear -> "Day/Month/Year"
        PageTableDateFormat.MonthDayYear -> "Month/Day/Year"
        PageTableDateFormat.YearMonthDay -> "Year/Month/Day"
    }

internal val PageTableTimeFormat.label: String
    get() = when (this) {
        PageTableTimeFormat.Hidden -> "Hidden"
        PageTableTimeFormat.TwelveHour -> "12 hour"
        PageTableTimeFormat.TwentyFourHour -> "24 hour"
    }

internal val PageTableDateReminder.label: String
    get() = when (this) {
        PageTableDateReminder.None -> "None"
        PageTableDateReminder.AtTimeOfEvent -> "At time of event"
        PageTableDateReminder.OnDayOfEvent -> "On day of event"
        PageTableDateReminder.OneDayBefore -> "1 day before"
    }

internal fun String.toLocalDateOrNull(): LocalDate? {
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

internal fun String.toLocalTimeOrNull(): LocalTime? {
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

internal fun String.toDatePickerMillis(): Long? {
    val date = trim().toTableDateCellValue().startDate.toLocalDateOrNull() ?: return null
    return runCatching {
        date
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

internal fun Long.toIsoDateString(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

internal fun List<PageBlock>.tableReferences(): List<PageTableReference> {
    return flatMap { block ->
        val current = if (block.type == PageBlockType.DatabaseTable) {
            listOf(
                PageTableReference(
                    blockId = block.id,
                    title = block.table.title,
                    table = block.table,
                ),
            )
        } else {
            emptyList()
        }
        current + block.children.tableReferences()
    }
}

internal fun PageTable.rowSummaries(
    tableReferences: List<PageTableReference>,
    dateColumnId: String = "",
    endDateColumnId: String = "",
    statusColumnId: String = "",
    searchQuery: String = "",
): List<TableRowSummary> {
    val titleColumn = titleColumn()
    val statusColumn = statusColumn(statusColumnId)
    val dateColumn = dateColumn(dateColumnId)
    val endDateColumn = columns.firstOrNull { column -> column.id == endDateColumnId }

    return visibleRows(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    ).map { row ->
        val excludedForDetails = setOfNotNull(titleColumn?.id)
        val excludedForTimeline = setOfNotNull(titleColumn?.id, statusColumn?.id, dateColumn?.id, endDateColumn?.id)
        TableRowSummary(
            row = row,
            title = displayCellText(row, titleColumn, tableReferences).ifBlank { "Untitled" },
            status = displayCellText(row, statusColumn, tableReferences).ifBlank { NoStatusLabel },
            date = displayCellText(row, dateColumn, tableReferences).ifBlank { NoDateLabel },
            endDate = displayCellText(row, endDateColumn, tableReferences),
            details = rowDetails(row, excludedForDetails, tableReferences),
            timelineDetails = rowDetails(row, excludedForTimeline, tableReferences),
        )
    }
}

internal fun PageTable.visibleRows(
    tableReferences: List<PageTableReference> = emptyList(),
    searchQuery: String = "",
): List<PageTableRow> {
    val filterColumn = columns.firstOrNull { column -> column.id == filter.columnId }
    val filteredRows = if (filterColumn != null && filter.isActive()) {
        rows.filter { row -> row.matchesTableFilter(this, filterColumn, filter, tableReferences) }
    } else {
        rows
    }
    val searchedRows = if (searchQuery.isBlank()) {
        filteredRows
    } else {
        filteredRows.filter { row -> row.matchesTableSearch(this, searchQuery, tableReferences) }
    }
    val sortColumn = columns.firstOrNull { column -> column.id == sort.columnId } ?: return searchedRows
    val sortedRows = searchedRows.sortedWith { left, right ->
        compareRowsByColumn(left, right, sortColumn, tableReferences)
    }
    return if (sort.direction == PageTableSortDirection.Descending) {
        sortedRows.asReversed()
    } else {
        sortedRows
    }
}

internal fun PageTable.groupColumn(): PageTableColumn? {
    return columns.firstOrNull { column -> column.id == groupByColumnId }
}

internal fun PageTable.groupedSummaries(
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    defaultToStatus: Boolean = false,
): List<Pair<String, List<TableRowSummary>>> {
    val summaries = rowSummaries(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    )
    val groupingColumn = groupColumn()
    if (groupingColumn != null) {
        return summaries.groupBy { summary -> groupLabel(summary.row, groupingColumn, tableReferences) }.toList()
    }
    if (defaultToStatus) {
        return summaries.groupBy { summary -> summary.status }.toList()
    }
    return listOf("" to summaries)
}

internal fun PageTableRow.matchesTableSearch(
    table: PageTable,
    query: String,
    tableReferences: List<PageTableReference>,
): Boolean {
    val terms = query
        .trim()
        .split(Regex("\\s+"))
        .filter { term -> term.isNotBlank() }
    if (terms.isEmpty()) return true

    val searchableText = buildString {
        table.columns.forEach { column ->
            append(column.name)
            append(' ')
            append(table.displayCellText(this@matchesTableSearch, column, tableReferences))
            append(' ')
        }
        blocks.forEach { block ->
            append(block.searchText())
            append(' ')
        }
    }
    return terms.all { term -> searchableText.contains(term, ignoreCase = true) }
}

internal fun PageBlock.searchText(): String {
    return buildString {
        append(text)
        append(' ')
        mediaAttachments.forEach { attachment ->
            append(attachment.name)
            append(' ')
        }
        if (type == PageBlockType.DatabaseTable || type == PageBlockType.Table) {
            append(table.title)
            append(' ')
            table.columns.forEach { column ->
                append(column.name)
                append(' ')
            }
        }
        children.forEach { child ->
            append(child.searchText())
            append(' ')
        }
    }
}

internal fun PageTableRow.matchesTableFilter(
    table: PageTable,
    column: PageTableColumn,
    filter: PageTableFilter,
    tableReferences: List<PageTableReference>,
): Boolean {
    val displayText = table.displayCellText(this, column, tableReferences)
    val query = filter.query.trim()
    val isEmpty = displayText.isBlank()
    return when (filter.operator) {
        PageTableFilterOperator.IsEmpty -> isEmpty
        PageTableFilterOperator.IsNotEmpty -> !isEmpty
        PageTableFilterOperator.Contains -> displayText.contains(query, ignoreCase = true)
        PageTableFilterOperator.Equals -> column.matchesFilterEquals(this, displayText, query, tableReferences)
        PageTableFilterOperator.NotEquals -> !column.matchesFilterEquals(this, displayText, query, tableReferences)
        PageTableFilterOperator.GreaterThan -> column.compareFilterNumberOrText(this, displayText, query, tableReferences) > 0
        PageTableFilterOperator.LessThan -> column.compareFilterNumberOrText(this, displayText, query, tableReferences) < 0
        PageTableFilterOperator.Before -> column.compareFilterDateOrText(this, displayText, query, tableReferences) < 0
        PageTableFilterOperator.After -> column.compareFilterDateOrText(this, displayText, query, tableReferences) > 0
        PageTableFilterOperator.OnOrBefore -> column.compareFilterDateOrText(this, displayText, query, tableReferences) <= 0
        PageTableFilterOperator.OnOrAfter -> column.compareFilterDateOrText(this, displayText, query, tableReferences) >= 0
    }
}

private fun PageTableColumn.matchesFilterEquals(
    row: PageTableRow,
    displayText: String,
    query: String,
    tableReferences: List<PageTableReference>,
): Boolean = when (type) {
    PageTableColumnType.Checkbox -> {
        val expected = query.equals("true", ignoreCase = true) ||
            query.equals("checked", ignoreCase = true) ||
            query.equals("done", ignoreCase = true) ||
            query.equals("yes", ignoreCase = true)
        (row.cellText(this) == CheckboxValueChecked) == expected
    }
    PageTableColumnType.Number -> displayText.toDoubleOrNull() == query.toDoubleOrNull()
    PageTableColumnType.Date -> compareFilterDateOrText(row, displayText, query, tableReferences) == 0
    PageTableColumnType.MultiSelect -> row.cellText(this)
        .selectedChoiceValues()
        .any { value -> value.equals(query, ignoreCase = true) }
    else -> displayText.equals(query, ignoreCase = true)
}

private fun PageTableColumn.compareFilterNumberOrText(
    row: PageTableRow,
    displayText: String,
    query: String,
    tableReferences: List<PageTableReference>,
): Int {
    val leftNumber = when (type) {
        PageTableColumnType.Number,
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> displayText.toDoubleOrNull()
        else -> null
    }
    val rightNumber = query.toDoubleOrNull()
    if (leftNumber != null && rightNumber != null) {
        return leftNumber.compareTo(rightNumber)
    }
    return compareFilterDateOrText(row, displayText, query, tableReferences)
}

private fun PageTableColumn.compareFilterDateOrText(
    row: PageTableRow,
    displayText: String,
    query: String,
    tableReferences: List<PageTableReference>,
): Int {
    val leftDate = row.cellValues[id]?.date?.startDate?.toLocalDateOrNull()
        ?: displayText.toLocalDateOrNull()
    val rightDate = query.toLocalDateOrNull()
    if (leftDate != null && rightDate != null) {
        return leftDate.compareTo(rightDate)
    }
    return displayText.lowercase(Locale.US).compareTo(query.lowercase(Locale.US))
}

internal fun PageTable.groupLabel(
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): String {
    return displayCellText(row, column, tableReferences).ifBlank { "Empty" }
}

internal fun PageTable.compareRowsByColumn(
    leftRow: PageTableRow,
    rightRow: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): Int {
    val left = displayCellText(leftRow, column, tableReferences)
    val right = displayCellText(rightRow, column, tableReferences)
    return when (column.type) {
        PageTableColumnType.Number -> compareValues(left.toDoubleOrNull(), right.toDoubleOrNull())
        PageTableColumnType.Checkbox -> compareValues(left == CheckboxValueChecked, right == CheckboxValueChecked)
        PageTableColumnType.Date,
        PageTableColumnType.Formula,
        PageTableColumnType.Relation,
        PageTableColumnType.Rollup,
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        PageTableColumnType.Text,
        PageTableColumnType.FilesMedia,
        -> left.lowercase().compareTo(right.lowercase())
    }
}

internal val PageTableSortDirection.arrowLabel: String
    get() = when (this) {
        PageTableSortDirection.Ascending -> "Asc"
        PageTableSortDirection.Descending -> "Desc"
    }

internal fun PageTable.titleColumn(): PageTableColumn? {
    return columns.firstOrNull()
}

internal fun PageTable.statusColumn(preferredColumnId: String = ""): PageTableColumn? {
    if (preferredColumnId.isNotBlank()) {
        columns.firstOrNull { column -> column.id == preferredColumnId }?.let { column -> return column }
    }
    return columns.firstOrNull { column ->
        column.type == PageTableColumnType.Status ||
            column.name.contains("status", ignoreCase = true) ||
            column.name.contains("stage", ignoreCase = true) ||
            column.name.contains("state", ignoreCase = true) ||
            column.name.contains("phase", ignoreCase = true)
    } ?: columns.getOrNull(1)
}

internal fun PageTable.dateColumn(preferredColumnId: String = ""): PageTableColumn? {
    if (preferredColumnId.isNotBlank()) {
        columns.firstOrNull { column -> column.id == preferredColumnId }?.let { column -> return column }
    }
    return columns.firstOrNull { column ->
        column.type == PageTableColumnType.Date ||
            column.name.contains("date", ignoreCase = true) ||
            column.name.contains("due", ignoreCase = true) ||
            column.name.contains("deadline", ignoreCase = true) ||
            column.name.contains("day", ignoreCase = true)
    }
}

internal fun PageTable.dateCandidateColumns(): List<PageTableColumn> {
    val candidates = columns.filter { column ->
        column.type == PageTableColumnType.Date ||
            column.name.contains("date", ignoreCase = true) ||
            column.name.contains("due", ignoreCase = true) ||
            column.name.contains("deadline", ignoreCase = true) ||
            column.name.contains("day", ignoreCase = true) ||
            column.name.contains("start", ignoreCase = true) ||
            column.name.contains("end", ignoreCase = true)
    }
    return candidates.ifEmpty { columns }
}

internal fun PageTable.metricCandidateColumns(): List<PageTableColumn> {
    val candidates = columns.filter { column ->
        column.type == PageTableColumnType.Number ||
            column.type == PageTableColumnType.Formula ||
            column.type == PageTableColumnType.Rollup ||
            column.name.contains("amount", ignoreCase = true) ||
            column.name.contains("total", ignoreCase = true) ||
            column.name.contains("score", ignoreCase = true) ||
            column.name.contains("count", ignoreCase = true)
    }
    return candidates.ifEmpty { columns }
}

internal fun PageTable.rowDetails(
    row: PageTableRow,
    excludedColumnIds: Set<String>,
    tableReferences: List<PageTableReference>,
): String {
    return columns
        .filterNot { column -> column.id in excludedColumnIds }
        .mapNotNull { column ->
            displayCellText(row, column, tableReferences)
                .takeIf { value -> value.isNotBlank() }
                ?.let { value -> "${column.name}: $value" }
        }
        .joinToString(" • ")
}

internal fun PageTable.displayCellText(
    row: PageTableRow,
    column: PageTableColumn?,
    tableReferences: List<PageTableReference>,
    depth: Int = 0,
    evaluationPath: Set<String> = emptySet(),
): String {
    if (column == null) return ""
    if (depth > 24) return CircularDependencyText
    val rawValue = row.cellText(column)
    return when (column.type) {
        PageTableColumnType.Formula -> {
            val key = evaluationKey(row, column, tableReferences)
            if (key in evaluationPath) {
                CircularDependencyText
            } else {
                evaluateFormula(
                    row = row,
                    sourceColumn = column,
                    tableReferences = tableReferences,
                    depth = depth + 1,
                    evaluationPath = evaluationPath + key,
                )
            }
        }
        PageTableColumnType.Relation -> relationDisplayText(rawValue, column, tableReferences)
        PageTableColumnType.Rollup -> {
            val key = evaluationKey(row, column, tableReferences)
            if (key in evaluationPath) {
                CircularDependencyText
            } else {
                rollupDisplayText(
                    row = row,
                    column = column,
                    tableReferences = tableReferences,
                    depth = depth + 1,
                    evaluationPath = evaluationPath + key,
                )
            }
        }
        PageTableColumnType.Checkbox -> if (rawValue == CheckboxValueChecked) "Checked" else ""
        PageTableColumnType.FilesMedia -> rawValue.toTableMediaAttachments()
            .joinToString(separator = ", ") { attachment -> attachment.name }
        PageTableColumnType.Date -> column.displayDateCellValue(rawValue)
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Status,
        -> rawValue
    }
}

internal fun PageTable.evaluateFormula(
    row: PageTableRow,
    sourceColumn: PageTableColumn,
    tableReferences: List<PageTableReference>,
    depth: Int,
    evaluationPath: Set<String>,
): String {
    val formula = sourceColumn.formula
    if (formula.isBlank()) return ""
    var expression = formula
    var hasCircularDependency = false
    columns
        .filterNot { column -> column.id == sourceColumn.id }
        .sortedByDescending { column -> column.name.length }
        .forEach { column ->
            val displayValue = displayCellText(row, column, tableReferences, depth, evaluationPath)
            if (displayValue == CircularDependencyText) {
                hasCircularDependency = true
            }
            val value = displayValue.toDoubleOrNull() ?: 0.0
            expression = expression.replace("{${column.name}}", value.toString(), ignoreCase = true)
        }
    if (hasCircularDependency) return CircularDependencyText
    return expression.evaluateArithmeticExpression()
        ?.formatTableNumber()
        .orEmpty()
}

internal fun PageTable.relationDisplayText(
    rawValue: String,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): String {
    val targetTable = tableReferences.firstOrNull { reference -> reference.blockId == column.relationTargetTableId }?.table
        ?: return if (column.relationTargetTableId.isBlank()) "" else MissingSourceText
    val rowsById = targetTable.rows.associateBy { row -> row.id }
    return rawValue.relatedRowIdList()
        .mapNotNull { rowId -> rowsById[rowId] }
        .joinToString { row -> targetTable.rowTitle(row) }
}

internal fun PageTable.rollupDisplayText(
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    depth: Int,
    evaluationPath: Set<String> = emptySet(),
): String {
    val relationColumn = columns.firstOrNull { candidate -> candidate.id == column.rollupRelationColumnId }
        ?: return "Set relation"
    if (relationColumn.type != PageTableColumnType.Relation) return "Invalid relation"
    val targetTable = tableReferences.firstOrNull { reference ->
        reference.blockId == relationColumn.relationTargetTableId
    }?.table ?: return if (relationColumn.relationTargetTableId.isBlank()) "Set target" else MissingSourceText
    val targetColumn = targetTable.columns.firstOrNull { target -> target.id == column.rollupTargetColumnId }
        ?: return MissingPropertyText
    val rowsById = targetTable.rows.associateBy { targetRow -> targetRow.id }
    val relatedRows = row.cellText(relationColumn)
        .relatedRowIdList()
        .mapNotNull { rowId -> rowsById[rowId] }
    if (relatedRows.isEmpty()) return ""
    val values = relatedRows
        .map { relatedRow ->
            targetTable.displayCellText(
                row = relatedRow,
                column = targetColumn,
                tableReferences = tableReferences,
                depth = depth,
                evaluationPath = evaluationPath,
            )
        }
    if (values.any { value -> value == CircularDependencyText }) return CircularDependencyText
    if (values.any { value -> value == MissingSourceText }) return MissingSourceText
    if (values.any { value -> value == MissingPropertyText }) return MissingPropertyText
    val nonBlankValues = values
        .filter { value -> value.isNotBlank() }
    val numericValues = nonBlankValues.mapNotNull { value -> value.toDoubleOrNull() }

    return when (column.rollupAggregation) {
        PageTableRollupAggregation.Count -> relatedRows.size.toString()
        PageTableRollupAggregation.Sum -> numericValues.sum().formatTableNumber().takeIf { numericValues.isNotEmpty() }.orEmpty()
        PageTableRollupAggregation.Average -> {
            if (numericValues.isEmpty()) {
                ""
            } else {
                numericValues.average().formatTableNumber()
            }
        }
        PageTableRollupAggregation.Min -> numericValues.minOrNull()?.formatTableNumber().orEmpty()
        PageTableRollupAggregation.Max -> numericValues.maxOrNull()?.formatTableNumber().orEmpty()
    }
}

private const val CircularDependencyText = "Circular dependency"
private const val MissingSourceText = "Missing source"
private const val MissingPropertyText = "Missing property"

private fun PageTable.evaluationKey(
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
): String {
    val tableId = tableReferences.firstOrNull { reference -> reference.table === this }?.blockId
        ?: tableReferences.firstOrNull { reference -> reference.table == this }?.blockId
        ?: "table-${System.identityHashCode(this)}"
    return "$tableId:${row.id}:${column.id}"
}

internal fun PageTableRow.cellText(column: PageTableColumn?): String {
    if (column == null) return ""
    val fallback = cells[column.id].orEmpty()
    return (cellValues[column.id]
        ?.withColumnType(column.type, fallback)
        ?.displayValue(fallback)
        ?: fallback)
        .trim()
}

internal fun PageTable.rowTitle(row: PageTableRow): String {
    return row.cellText(titleColumn()).ifBlank { "Untitled" }
}

internal val PageTableColumnType.needsColumnConfig: Boolean
    get() = this == PageTableColumnType.Select ||
        this == PageTableColumnType.MultiSelect ||
        this == PageTableColumnType.Status ||
        this == PageTableColumnType.Formula ||
        this == PageTableColumnType.Relation ||
        this == PageTableColumnType.Rollup

internal val PageTableColumnType.label: String
    get() = when (this) {
        PageTableColumnType.Text -> "Text"
        PageTableColumnType.Number -> "Number"
        PageTableColumnType.Select -> "Select"
        PageTableColumnType.MultiSelect -> "Multi-select"
        PageTableColumnType.Status -> "Status"
        PageTableColumnType.Date -> "Date"
        PageTableColumnType.FilesMedia -> "Files & media"
        PageTableColumnType.Checkbox -> "Checkbox"
        PageTableColumnType.Formula -> "Formula"
        PageTableColumnType.Relation -> "Relation"
        PageTableColumnType.Rollup -> "Rollup"
    }

internal val PageTableColumnType.shortLabel: String
    get() = when (this) {
        PageTableColumnType.Text -> "Aa"
        PageTableColumnType.Number -> "#"
        PageTableColumnType.Select -> "Sel"
        PageTableColumnType.MultiSelect -> "Multi"
        PageTableColumnType.Status -> "St"
        PageTableColumnType.Date -> "Cal"
        PageTableColumnType.FilesMedia -> "F"
        PageTableColumnType.Checkbox -> "OK"
        PageTableColumnType.Formula -> "Fx"
        PageTableColumnType.Relation -> "Rel"
        PageTableColumnType.Rollup -> "Roll"
    }

internal val PageTableColumnType.icon: ImageVector
    get() = when (this) {
        PageTableColumnType.Text -> Icons.Rounded.Edit
        PageTableColumnType.Number -> Icons.Rounded.Calculate
        PageTableColumnType.Select -> Icons.Rounded.KeyboardArrowDown
        PageTableColumnType.MultiSelect -> Icons.Rounded.ViewColumn
        PageTableColumnType.Status -> Icons.Rounded.TaskAlt
        PageTableColumnType.Date -> Icons.Rounded.CalendarMonth
        PageTableColumnType.FilesMedia -> Icons.AutoMirrored.Rounded.Article
        PageTableColumnType.Checkbox -> Icons.Rounded.TaskAlt
        PageTableColumnType.Formula -> Icons.Rounded.Functions
        PageTableColumnType.Relation -> Icons.Rounded.ViewColumn
        PageTableColumnType.Rollup -> Icons.Rounded.Calculate
    }

internal fun PageTableColumn.configSummary(
    table: PageTable,
    tableReferences: List<PageTableReference>,
): String {
    return when (type) {
        PageTableColumnType.Formula -> formula.ifBlank { "Set formula" }
        PageTableColumnType.Relation -> {
            val target = tableReferences.firstOrNull { reference -> reference.blockId == relationTargetTableId }
            when {
                target != null -> target.title.databaseTitleOrPlaceholder()
                relationTargetTableId.isBlank() -> "Set target"
                else -> MissingSourceText
            }
        }
        PageTableColumnType.Rollup -> {
            val relationColumn = table.columns.firstOrNull { column -> column.id == rollupRelationColumnId }
            val targetTable = tableReferences.firstOrNull { reference ->
                reference.blockId == relationColumn?.relationTargetTableId
            }?.table
            val targetColumn = targetTable?.columns?.firstOrNull { column -> column.id == rollupTargetColumnId }
            val targetSummary = when {
                relationColumn == null -> null
                relationColumn.relationTargetTableId.isBlank() -> null
                targetTable == null -> MissingSourceText
                targetColumn == null -> MissingPropertyText
                else -> targetColumn.name.ifBlank { "Property" }
            }
            listOfNotNull(
                relationColumn?.name?.ifBlank { "Relation" },
                targetSummary,
                rollupAggregation.name,
            ).joinToString(" • ").ifBlank { "Set rollup" }
        }
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        -> "${choiceOptions.size} options"
        PageTableColumnType.Text,
        PageTableColumnType.Number,
        PageTableColumnType.Date,
        PageTableColumnType.Checkbox,
        PageTableColumnType.FilesMedia,
        -> ""
    }
}

private fun String.databaseTitleOrPlaceholder(): String {
    return takeUnless { title -> title.isBlank() || title == "Untitled database" } ?: "Table"
}

internal fun String.relatedRowIds(): Set<String> {
    return relatedRowIdList().toSet()
}

internal fun String.relatedRowIdList(): List<String> {
    return split(",")
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
        .distinct()
}

internal fun String.evaluateArithmeticExpression(): Double? {
    class Parser(private val input: String) {
        private var index = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            skipSpaces()
            return if (index == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> {
                        index++
                        value + (parseTerm() ?: return null)
                    }
                    '-' -> {
                        index++
                        value - (parseTerm() ?: return null)
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> {
                        index++
                        value * (parseFactor() ?: return null)
                    }
                    '/' -> {
                        index++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double? {
            skipSpaces()
            if (peek() == '-') {
                index++
                return -(parseFactor() ?: return null)
            }
            if (peek() == '(') {
                index++
                val value = parseExpression() ?: return null
                skipSpaces()
                if (peek() != ')') return null
                index++
                return value
            }
            val start = index
            while (peek()?.let { char -> char.isDigit() || char == '.' } == true) {
                index++
            }
            return input.substring(start, index).toDoubleOrNull()
        }

        private fun skipSpaces() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        private fun peek(): Char? = input.getOrNull(index)
    }

    return Parser(this).parse()
}

internal fun Double.formatTableNumber(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        "%.2f".format(Locale.US, this).trimEnd('0').trimEnd('.')
    }
}

internal fun String.compactControlLabel(maxLength: Int = 28): String {
    val clean = trim()
    return if (clean.length <= maxLength) {
        clean
    } else {
        clean.take(maxLength - 3).trimEnd() + "..."
    }
}

internal fun String.dateSortKey(): String {
    if (this == NoDateLabel) return "9999-99-99"
    val clean = trim()
    val dateCandidate = clean.substringBefore(" ").ifBlank { clean }
    return dateCandidate.toLocalDateOrNull()
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ?: clean.lowercase()
}

@Preview(showBackground = true)
@Composable
internal fun PageEditorScreenPreview() {
    ChangeYourLifeTheme {
        PageEditorScreen(
            uiState = PageEditorUiState(
                isLoading = false,
                page = Page(
                    id = "preview-page",
                    workspaceId = "preview-workspace",
                    parentPageId = null,
                    title = "Weekly reset",
                    content = "",
                    sortOrder = 0,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
                title = "Weekly reset",
                blocks = listOf(
                    PageBlock(
                        id = "1",
                        type = PageBlockType.Heading,
                        text = "Weekly reset",
                    ),
                    PageBlock(
                        id = "2",
                        type = PageBlockType.Todo,
                        text = "Review goals",
                    ),
                    PageBlock(
                        id = "3",
                        type = PageBlockType.Bullet,
                        text = "Plan work blocks",
                    ),
                ),
            ),
            homeAiState = HomeUiState(),
            onBack = {},
            onOpenPage = { _, _, _ -> },
            onTitleChange = {},
            onBlockTextChange = { _, _ -> },
            onBlockRichTextChange = { _, _, _ -> },
            onPasteBlocks = { _, _ -> },
            onBlockTypeChange = { _, _ -> },
            onBlockMediaAdd = { _, _ -> },
            onBlockMediaRemove = { _, _ -> },
            onToggleTodo = {},
            onAddBlock = {},
            onInsertBlockNear = { _, _, _ -> },
            onDeleteBlock = {},
            onMoveBlockUp = {},
            onMoveBlockDown = {},
            onIndentBlock = {},
            onOutdentBlock = {},
            onTableTitleChange = { _, _ -> },
            onTableViewChange = { _, _ -> },
            onTableViewConfigChange = { _, _ -> },
            onTableSortChange = { _, _, _ -> },
            onTableFilterChange = { _, _ -> },
            onTableGroupChange = { _, _ -> },
            onTableColumnNameChange = { _, _, _ -> },
            onTableColumnTypeChange = { _, _, _ -> },
            onTableColumnConfigChange = { _, _, _ -> },
            onTableColumnDateSettingsChange = { _, _, _, _, _, _ -> },
            onTableColumnFormulaChange = { _, _, _ -> },
            onTableColumnRelationTargetChange = { _, _, _ -> },
            onTableColumnRollupChange = { _, _, _, _, _ -> },
            onTableCellChange = { _, _, _, _ -> },
            onTableRelationCellChange = { _, _, _, _ -> },
            onAddTableColumn = { _, _, _ -> },
            onInsertTableColumn = { _, _, _ -> },
            onDuplicateTableColumn = { _, _ -> },
            onDeleteTableColumn = { _, _ -> },
            onAddTableRow = {},
            onDeleteTableRow = { _, _ -> },
            onDuplicateTableRow = { _, _ -> },
            onMoveTableRow = { _, _, _ -> },
            onTableRowBlockTextChange = { _, _, _, _ -> },
            onTableRowBlockRichTextChange = { _, _, _, _, _ -> },
            onTableRowBlockPasteBlocks = { _, _, _, _ -> },
            onTableRowBlockTypeChange = { _, _, _, _ -> },
            onTableRowBlockMediaAdd = { _, _, _, _ -> },
            onTableRowBlockMediaRemove = { _, _, _, _ -> },
            onToggleTableRowTodoBlock = { _, _, _ -> },
            onAddTableRowPageBlock = { _, _, _ -> },
            onInsertTableRowPageBlockNear = { _, _, _, _, _ -> },
            onDeleteTableRowPageBlock = { _, _, _ -> },
            onMoveTableRowPageBlockUp = { _, _, _ -> },
            onMoveTableRowPageBlockDown = { _, _, _ -> },
            onIndentTableRowPageBlock = { _, _, _ -> },
            onOutdentTableRowPageBlock = { _, _, _ -> },
            onTableDataSourceChange = { _, _ -> },
            onAddProperty = { _, _ -> },
            onPropertyNameChange = { _, _ -> },
            onPropertyValueChange = { _, _ -> },
            onDeleteProperty = {},
            onAddChildBlock = { _, _ -> },
            onCreateChildPage = {},
            onCreateLinkedChildPageFromBlock = {},
            onCreateLinkedChildPageFromTableRowBlock = { _, _, _ -> },
            onUndoEditorChange = {},
            onKeepLocalConflict = {},
            onUseRemoteConflict = {},
            onSendAiMessage = { _, _, _, _ -> },
            onUndoAiAction = { _, _ -> },
            onHomeAiModeChange = {},
            onClearHomeAiHistory = {},
            onCreateHomeChatSession = {},
            onDismissHomeAiError = {},
        )
    }
}
