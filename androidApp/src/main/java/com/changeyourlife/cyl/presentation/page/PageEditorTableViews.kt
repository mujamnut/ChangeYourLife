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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableRollupAggregation
import com.changeyourlife.cyl.domain.model.PageTableSortDirection
import com.changeyourlife.cyl.domain.model.PageTableView
import com.changeyourlife.cyl.domain.model.PageTableViewConfig
import com.changeyourlife.cyl.domain.model.PageSyncState
import com.changeyourlife.cyl.domain.model.PageTextSpan
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
internal fun TableListView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val groupedSummaries = table.groupedSummaries(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    )

    val listModifier = if (isFullPage) Modifier.fillMaxHeight() else Modifier.heightIn(max = 560.dp)

    LazyColumn(
        modifier = listModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (groupedSummaries.all { group -> group.second.isEmpty() }) {
            item { EmptyTableMessage() }
        } else {
            groupedSummaries.forEach { (group, rows) ->
                if (group.isNotBlank()) {
                    item(key = "group_$group", contentType = "group") {
                        Text(
                            text = "$group (${rows.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(
                    items = rows,
                    key = { summary -> summary.row.id },
                    contentType = { "row" },
                ) { summary ->
                    Column {
                        ListItem(
                            headlineContent = { Text(text = summary.title) },
                            supportingContent = {
                                if (summary.details.isNotBlank()) {
                                    Text(text = summary.details)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TableBoardView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val groupedRows = table.groupedSummaries(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
        defaultToStatus = true,
    )

    if (groupedRows.all { group -> group.second.isEmpty() }) {
        EmptyTableMessage()
        return
    }

    val rowModifier = if (isFullPage) Modifier.fillMaxHeight() else Modifier
    LazyRow(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = groupedRows.toList(),
            key = { group -> group.first },
        ) { (status, rows) ->
            val listModifier = if (isFullPage) Modifier.width(BoardColumnWidth).fillMaxHeight() else Modifier.width(BoardColumnWidth).heightIn(max = 560.dp)
            LazyColumn(
                modifier = listModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "$status (${rows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = rows,
                    key = { summary -> summary.row.id },
                ) { summary ->
                    Column {
                        ListItem(
                            headlineContent = { Text(text = summary.title) },
                            supportingContent = {
                                if (summary.details.isNotBlank()) {
                                    Text(text = summary.details)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TableCalendarView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val summaries = table.rowSummaries(
        tableReferences = tableReferences,
        dateColumnId = table.viewConfig.calendarDateColumnId,
        searchQuery = searchQuery,
    )
    val groupedRows = summaries
        .groupBy { summary -> summary.date }
        .toList()
        .sortedBy { group -> group.first.dateSortKey() }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    val rowModifier = if (isFullPage) Modifier.fillMaxHeight() else Modifier
    LazyRow(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = groupedRows,
            key = { group -> group.first },
        ) { (date, rows) ->
            val listModifier = if (isFullPage) Modifier.width(CalendarDayWidth).fillMaxHeight() else Modifier.width(CalendarDayWidth).heightIn(max = 560.dp)
            LazyColumn(
                modifier = listModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "$date (${rows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = rows,
                    key = { summary -> summary.row.id },
                ) { summary ->
                    Column {
                        ListItem(
                            headlineContent = { Text(text = summary.title) },
                            supportingContent = {
                                if (summary.details.isNotBlank()) {
                                    Text(text = summary.details)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TableGalleryView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val summaries = table.rowSummaries(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    )

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    val rowModifier = if (isFullPage) Modifier.fillMaxHeight() else Modifier
    LazyRow(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = summaries,
            key = { summary -> summary.row.id },
        ) { summary ->
            Column(
                modifier = Modifier.width(GalleryItemWidth),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${summary.status} • ${summary.date}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.details.isNotBlank()) {
                    Text(
                        text = summary.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun TableTimelineView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val summaries = if (table.sort.columnId.isBlank()) {
        table.rowSummaries(
            tableReferences = tableReferences,
            dateColumnId = table.viewConfig.timelineStartColumnId,
            endDateColumnId = table.viewConfig.timelineEndColumnId,
            searchQuery = searchQuery,
        ).sortedWith(
            compareBy<TableRowSummary> { summary -> summary.date.dateSortKey() }
                .thenBy { summary -> summary.title },
        )
    } else {
        table.rowSummaries(
            tableReferences = tableReferences,
            dateColumnId = table.viewConfig.timelineStartColumnId,
            endDateColumnId = table.viewConfig.timelineEndColumnId,
            searchQuery = searchQuery,
        )
    }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    val listModifier = if (isFullPage) Modifier.fillMaxHeight() else Modifier.heightIn(max = 560.dp)
    LazyColumn(
        modifier = listModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = summaries,
            key = { summary -> summary.row.id },
        ) { summary ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val dateLabel = if (summary.endDate.isNotBlank() && summary.endDate != NoDateLabel) {
                        "${summary.date} - ${summary.endDate}"
                    } else {
                        summary.date
                    }
                    Text(
                        text = dateLabel,
                        modifier = Modifier.width(TimelineDateWidth),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = summary.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = summary.status,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (summary.timelineDetails.isNotBlank()) {
                            Text(
                                text = summary.timelineDetails,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun TableDashboardView(
    table: PageTable,
    tableReferences: List<PageTableReference>,
    searchQuery: String = "",
    isFullPage: Boolean = false,
) {
    val summaries = table.rowSummaries(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    )
    val rows = table.visibleRows(
        tableReferences = tableReferences,
        searchQuery = searchQuery,
    )
    val groupColumn = table.columns.firstOrNull { column -> column.id == table.viewConfig.dashboardGroupColumnId }
        ?: table.statusColumn()
    val metricColumn = table.columns.firstOrNull { column -> column.id == table.viewConfig.dashboardMetricColumnId }
        ?: table.metricCandidateColumns().firstOrNull()
    val groupCounts = rows
        .groupingBy { row ->
            table.displayCellText(row, groupColumn, tableReferences).ifBlank { "Empty" }
        }
        .eachCount()
        .toList()
        .sortedByDescending { entry -> entry.second }
    val metricValues = rows.mapNotNull { row ->
        metricColumn?.let { column -> table.displayCellText(row, column, tableReferences).toDoubleOrNull() }
    }
    val dateCounts = summaries
        .filterNot { summary -> summary.date == NoDateLabel }
        .groupingBy { summary -> summary.date }
        .eachCount()
        .toList()
        .sortedBy { entry -> entry.first.dateSortKey() }

    if (summaries.isEmpty()) {
        EmptyTableMessage()
        return
    }

    val columnModifier = if (isFullPage) {
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = buildList {
                    add(DashboardStat("Rows", summaries.size.toString()))
                    add(DashboardStat("Groups", groupCounts.size.toString()))
                    if (metricColumn != null && metricValues.isNotEmpty()) {
                        add(DashboardStat("${metricColumn.name} total", metricValues.sum().formatTableNumber()))
                        add(DashboardStat("${metricColumn.name} avg", (metricValues.sum() / metricValues.size).formatTableNumber()))
                    } else {
                        add(DashboardStat("Dated", dateCounts.sumOf { entry -> entry.second }.toString()))
                    }
                },
                key = { stat -> stat.label },
            ) { stat ->
                DashboardStatTile(stat = stat)
            }
        }

        Text(
            text = groupColumn?.name?.ifBlank { "Group" } ?: "Group",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        groupCounts.take(50).forEach { (status, count) ->
            DashboardBar(
                label = status,
                count = count,
                maxCount = groupCounts.maxOf { entry -> entry.second },
            )
        }

        Text(
            text = "Dates",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (dateCounts.isEmpty()) {
            Text(
                text = "No dated rows.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            dateCounts.take(50).forEach { (date, count) ->
                DashboardBar(
                    label = date,
                    count = count,
                    maxCount = dateCounts.maxOf { entry -> entry.second },
                )
            }
        }
    }
}

@Composable
internal fun DashboardStatTile(stat: DashboardStat) {
    Column(
        modifier = Modifier.width(DashboardStatWidth),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DashboardBar(
    label: String,
    count: Int,
    maxCount: Int,
) {
    val progress = if (maxCount <= 0) {
        0f
    } else {
        count.toFloat() / maxCount.toFloat()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(DashboardLabelWidth),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            modifier = Modifier.width(DashboardCountWidth),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun EmptyTableMessage() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "No rows yet. Add a row from the table grid.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

