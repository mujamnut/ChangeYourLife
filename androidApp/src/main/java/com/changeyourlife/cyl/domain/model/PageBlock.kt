package com.changeyourlife.cyl.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PageBlockDocument(
    val version: Int = 1,
    val properties: List<PageProperty> = emptyList(),
    val blocks: List<PageBlock> = emptyList(),
)

@Serializable
data class PageProperty(
    val id: String,
    val name: String,
    val type: PagePropertyType,
    val value: String = "",
)

@Serializable
enum class PagePropertyType {
    Summarize,
    Translate,
    Text,
    Number,
    Select,
    MultiSelect,
    Status,
    Date,
    Person,
    FilesMedia,
    Checkbox,
    Url,
    Email,
    Phone,
    Formula,
    Relation,
    Rollup,
    CreatedTime,
    CreatedBy,
    LastEditedTime,
    LastEditedBy,
    Button,
    Place,
    Id,
    GoogleDriveFile,
    FigmaFile,
    GitHubPullRequests,
    ZendeskTicket,
}

@Serializable
data class PageBlock(
    val id: String,
    val type: PageBlockType,
    val text: String = "",
    val richTextSpans: List<PageTextSpan> = emptyList(),
    val mediaAttachments: List<PageMediaAttachment> = emptyList(),
    val isChecked: Boolean = false,
    val table: PageTable = PageTable(),
    val children: List<PageBlock> = emptyList(),
)

@Serializable
data class PageTextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String = "",
    val color: String = "",
    val highlight: String = "",
    val mentionPageId: String = "",
    val mentionLabel: String = "",
)

@Serializable
data class PageMediaAttachment(
    val id: String,
    val uri: String,
    val name: String = "Untitled file",
    val mimeType: String = "",
    val sizeBytes: Long = 0,
)

@Serializable
enum class PageBlockType {
    Text,
    Heading,
    Todo,
    Bullet,
    Numbered,
    Toggle,
    Quote,
    Callout,
    Code,
    Table,
    WebBookmark,
    Divider,
    MediaFile,
    DatabaseTable,
}

@Serializable
data class PageTable(
    val title: String = "",
    val view: PageTableView = PageTableView.Table,
    val viewConfig: PageTableViewConfig = PageTableViewConfig(),
    val columns: List<PageTableColumn> = emptyList(),
    val rows: List<PageTableRow> = emptyList(),
    val sort: PageTableSort = PageTableSort(),
    val filter: PageTableFilter = PageTableFilter(),
    val groupByColumnId: String = "",
)

@Serializable
data class PageTableViewConfig(
    val calendarDateColumnId: String = "",
    val timelineStartColumnId: String = "",
    val timelineEndColumnId: String = "",
    val dashboardMetricColumnId: String = "",
    val dashboardGroupColumnId: String = "",
    val dataSourcePageId: String = "",
    val dataSourceTableBlockId: String = "",
    val dataSourceTitle: String = "",
)

@Serializable
data class PageTableSort(
    val columnId: String = "",
    val direction: PageTableSortDirection = PageTableSortDirection.Ascending,
)

@Serializable
enum class PageTableSortDirection {
    Ascending,
    Descending,
}

@Serializable
data class PageTableFilter(
    val columnId: String = "",
    val query: String = "",
    val operator: PageTableFilterOperator = PageTableFilterOperator.Contains,
)

@Serializable
enum class PageTableFilterOperator {
    Contains,
    Equals,
    NotEquals,
    IsEmpty,
    IsNotEmpty,
    GreaterThan,
    LessThan,
    Before,
    After,
    OnOrBefore,
    OnOrAfter,
}

fun PageTableFilter.isActive(): Boolean {
    if (columnId.isBlank()) return false
    return operator == PageTableFilterOperator.IsEmpty ||
        operator == PageTableFilterOperator.IsNotEmpty ||
        query.isNotBlank()
}

@Serializable
data class PageTableColumn(
    val id: String,
    val name: String,
    val type: PageTableColumnType = PageTableColumnType.Text,
    val config: PageTableColumnConfig = PageTableColumnConfig(),
    val dateFormat: PageTableDateFormat = PageTableDateFormat.DayMonthYear,
    val timeFormat: PageTableTimeFormat = PageTableTimeFormat.Hidden,
    val dateReminder: PageTableDateReminder = PageTableDateReminder.OnDayOfEvent,
    val timezoneLabel: String = "Local",
    val formula: String = "",
    val relationTargetTableId: String = "",
    val rollupRelationColumnId: String = "",
    val rollupTargetColumnId: String = "",
    val rollupAggregation: PageTableRollupAggregation = PageTableRollupAggregation.Count,
)

@Serializable
data class PageTableColumnConfig(
    val options: List<PageTableSelectOption> = emptyList(),
    val isHidden: Boolean = false,
    val wrapContent: Boolean = false,
    val widthDp: Int = 0,
    val defaultValue: String = "",
    val description: String = "",
)

@Serializable
data class PageTableSelectOption(
    val id: String,
    val name: String,
    val color: PageTableOptionColor = PageTableOptionColor.Gray,
)

@Serializable
enum class PageTableOptionColor {
    Gray,
    Red,
    Orange,
    Yellow,
    Green,
    Blue,
    Purple,
    Pink,
}

val DefaultPageTableStatusOptions = listOf(
    PageTableSelectOption(id = "not-started", name = "Not started", color = PageTableOptionColor.Gray),
    PageTableSelectOption(id = "in-progress", name = "In progress", color = PageTableOptionColor.Blue),
    PageTableSelectOption(id = "done", name = "Done", color = PageTableOptionColor.Green),
    PageTableSelectOption(id = "blocked", name = "Blocked", color = PageTableOptionColor.Red),
)

fun PageTableColumnType.defaultConfig(): PageTableColumnConfig {
    return when (this) {
        PageTableColumnType.Status -> PageTableColumnConfig(options = DefaultPageTableStatusOptions)
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        -> PageTableColumnConfig(options = emptyList())
        else -> PageTableColumnConfig()
    }
}

fun PageTableColumnConfig.normalizedForType(type: PageTableColumnType): PageTableColumnConfig {
    return when (type) {
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        -> {
            val normalizedOptions = options
                .mapNotNull { option ->
                    option
                        .copy(name = option.name.trim())
                        .takeIf { it.name.isNotBlank() }
                }
                .distinctBy { option -> option.name.lowercase() }
            copy(
                options = if (type == PageTableColumnType.Status) {
                    normalizedOptions.ifEmpty { DefaultPageTableStatusOptions }
                } else {
                    normalizedOptions
                },
            )
        }
        else -> copy(options = emptyList())
    }
}

@Serializable
enum class PageTableDateFormat {
    DayMonthYear,
    MonthDayYear,
    YearMonthDay,
}

@Serializable
enum class PageTableTimeFormat {
    Hidden,
    TwelveHour,
    TwentyFourHour,
}

@Serializable
enum class PageTableDateReminder {
    None,
    AtTimeOfEvent,
    OnDayOfEvent,
    OneDayBefore,
}

@Serializable
enum class PageTableRollupAggregation {
    Count,
    Sum,
    Average,
    Min,
    Max,
}

@Serializable
data class PageTableRow(
    val id: String,
    val cells: Map<String, String> = emptyMap(),
    val cellValues: Map<String, PageTableCellValue> = emptyMap(),
    val metadata: PageTableRowMetadata = PageTableRowMetadata(),
    val blocks: List<PageBlock> = emptyList(),
)

@Serializable
data class PageTableRowMetadata(
    val icon: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0,
    val createdBy: String = "",
    val lastEditedAt: Long = 0,
    val lastEditedBy: String = "",
)

@Serializable
data class PageTableCellValue(
    val type: PageTableColumnType = PageTableColumnType.Text,
    val text: String = "",
    val number: String = "",
    val checked: Boolean = false,
    val date: PageTableDateCellValue = PageTableDateCellValue(),
    val files: List<PageMediaAttachment> = emptyList(),
    val relationRowIds: List<String> = emptyList(),
)

@Serializable
data class PageTableDateCellValue(
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val includeEndDate: Boolean = false,
    val includeTime: Boolean = false,
    val timezoneLabel: String = "Local",
    val reminder: PageTableDateReminder = PageTableDateReminder.OnDayOfEvent,
)

@Serializable
enum class PageTableColumnType {
    Text,
    Number,
    Select,
    MultiSelect,
    Status,
    Date,
    FilesMedia,
    Checkbox,
    Formula,
    Relation,
    Rollup,
}

@Serializable
enum class PageTableView {
    Table,
    List,
    Board,
    Calendar,
    Gallery,
    Timeline,
    Dashboard,
}
