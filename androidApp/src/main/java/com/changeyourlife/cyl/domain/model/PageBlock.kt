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
    Quote,
    Divider,
    MediaFile,
    DatabaseTable,
}

@Serializable
data class PageTable(
    val title: String = "Untitled database",
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
)

@Serializable
data class PageTableColumn(
    val id: String,
    val name: String,
    val type: PageTableColumnType = PageTableColumnType.Text,
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
