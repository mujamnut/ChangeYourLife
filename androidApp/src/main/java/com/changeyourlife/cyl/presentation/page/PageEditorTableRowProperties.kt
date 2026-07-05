package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.PageTable
import com.changeyourlife.cyl.domain.model.PageTableColumn
import com.changeyourlife.cyl.domain.model.PageTableColumnType
import com.changeyourlife.cyl.domain.model.PageTableDateFormat
import com.changeyourlife.cyl.domain.model.PageTableDateReminder
import com.changeyourlife.cyl.domain.model.PageTableRow
import com.changeyourlife.cyl.domain.model.PageTableTimeFormat

@Composable
internal fun RowPageTitleEditor(
    title: String,
    enabled: Boolean,
    onFocusTitle: () -> Unit,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = title,
        onValueChange = { nextTitle -> onTitleChange(nextTitle.toSingleLineTableCellValue()) },
        enabled = enabled,
        singleLine = true,
        modifier = modifier
            .heightIn(min = 50.dp)
            .padding(end = 10.dp)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusTitle()
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (title.isBlank()) {
                    Text(
                        text = "Untitled row",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun RowPagePropertyList(
    table: PageTable,
    row: PageTableRow,
    tableReferences: List<PageTableReference>,
    onAddProperty: () -> Unit,
    onEditProperty: (PageTableColumn) -> Unit,
    onCellChange: (String, String) -> Unit,
    onRelationCellChange: (String, List<String>) -> Unit,
    onColumnDateSettingsChange: (
        String,
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
    currentPageId: String,
    onAddRelationTargetRow: (String) -> Unit,
) {
    val propertyCountLabel = if (table.columns.isEmpty()) {
        "Properties"
    } else {
        "Properties · ${table.columns.size}"
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFocusProperties,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = propertyCountLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onAddProperty,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add property",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (table.columns.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TableGridTokens.dimensions.propertyRowMinHeight)
                    .clickable(onClick = onAddProperty),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Add property",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        val tableColors = TableGridTokens.colors()
        Column(modifier = Modifier.fillMaxWidth()) {
            table.columns.forEachIndexed { index, column ->
                RowPagePropertyItem(
                    table = table,
                    row = row,
                    column = column,
                    tableReferences = tableReferences,
                    onValueChange = { value -> onCellChange(column.id, value) },
                    onRelationValueChange = { relationRowIds -> onRelationCellChange(column.id, relationRowIds) },
                    onDateSettingsChange = { dateFormat, timeFormat, reminder, timezoneLabel ->
                        onColumnDateSettingsChange(column.id, dateFormat, timeFormat, reminder, timezoneLabel)
                    },
                    onEditProperty = { onEditProperty(column) },
                    onFocusProperties = onFocusProperties,
                    currentPageId = currentPageId,
                    onAddRelationTargetRow = onAddRelationTargetRow,
                )
                if (index < table.columns.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 32.dp),
                        color = tableColors.propertyDivider,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowPagePropertyItem(
    table: PageTable,
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    onValueChange: (String) -> Unit,
    onRelationValueChange: (List<String>) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onEditProperty: () -> Unit,
    onFocusProperties: () -> Unit,
    currentPageId: String,
    onAddRelationTargetRow: (String) -> Unit,
) {
    val tableColors = TableGridTokens.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TableGridTokens.dimensions.propertyRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(TableGridTokens.dimensions.propertyNameWidth)
                .height(TableGridTokens.dimensions.propertyValueHeight)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onEditProperty)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = column.type.icon,
                contentDescription = column.type.label,
                modifier = Modifier.size(19.dp),
                tint = tableColors.propertyIcon,
            )
            Text(
                text = column.name.ifBlank { column.type.label },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowPagePropertyValueEditor(
            table = table,
            row = row,
            column = column,
            tableReferences = tableReferences,
            value = row.cellText(column),
            onValueChange = onValueChange,
            onRelationValueChange = onRelationValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onFocusProperties = onFocusProperties,
            currentPageId = currentPageId,
            onAddRelationTargetRow = onAddRelationTargetRow,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowPagePropertyValueEditor(
    table: PageTable,
    row: PageTableRow,
    column: PageTableColumn,
    tableReferences: List<PageTableReference>,
    value: String,
    onValueChange: (String) -> Unit,
    onRelationValueChange: (List<String>) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
    currentPageId: String,
    onAddRelationTargetRow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (column.type) {
        PageTableColumnType.Formula,
        PageTableColumnType.Rollup,
        -> RowPageReadOnlyPropertyValue(
            value = table.displayCellText(row, column, tableReferences),
            modifier = modifier,
        )
        PageTableColumnType.Relation -> RelationCellEditor(
            column = column,
            value = value,
            tableReferences = tableReferences,
            onValueChange = onValueChange,
            onRelationValueChange = onRelationValueChange,
            currentPageId = currentPageId,
            onCreateTargetRow = onAddRelationTargetRow,
            modifier = modifier,
        )
        PageTableColumnType.Checkbox -> RowPageCheckboxPropertyValue(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.Date -> RowPageDatePropertyValue(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
        PageTableColumnType.Select,
        PageTableColumnType.MultiSelect,
        PageTableColumnType.Status,
        -> RowPageChoicePropertyValue(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
        PageTableColumnType.FilesMedia -> TableMediaCellEditor(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        PageTableColumnType.Number,
        PageTableColumnType.Text,
        -> RowPagePlainPropertyValue(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onFocusProperties = onFocusProperties,
            modifier = modifier,
        )
    }
}

@Composable
private fun RowPagePlainPropertyValue(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tableColors = TableGridTokens.colors()
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = { nextValue ->
            onFocusProperties()
            onValueChange(nextValue.toSingleLineTableCellValue())
        },
        modifier = modifier.height(TableGridTokens.dimensions.propertyValueHeight),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (column.type) {
                PageTableColumnType.Number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        text = when (column.type) {
                            PageTableColumnType.Number -> "0"
                            PageTableColumnType.Select,
                            PageTableColumnType.MultiSelect,
                            PageTableColumnType.Status,
                            -> "Empty"
                            else -> "Empty"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tableColors.emptyValue,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RowPageReadOnlyPropertyValue(
    value: String,
    modifier: Modifier = Modifier,
) {
    val tableColors = TableGridTokens.colors()
    Box(
        modifier = modifier.height(TableGridTokens.dimensions.propertyValueHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { "Empty" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) tableColors.emptyValue else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowPageCheckboxPropertyValue(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tableColors = TableGridTokens.colors()
    Row(
        modifier = modifier
            .height(TableGridTokens.dimensions.propertyValueHeight)
            .clickable {
                onValueChange(if (value == CheckboxValueChecked) "" else CheckboxValueChecked)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = value == CheckboxValueChecked,
            onCheckedChange = { checked -> onValueChange(if (checked) CheckboxValueChecked else "") },
        )
        Text(
            text = if (value == CheckboxValueChecked) "Done" else "Empty",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == CheckboxValueChecked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                tableColors.emptyValue
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowPageDatePropertyValue(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onDateSettingsChange: (
        PageTableDateFormat,
        PageTableTimeFormat,
        PageTableDateReminder,
        String,
    ) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val displayText = column.displayDateCellValue(value)

    if (isSheetOpen) {
        TableDateEditorSheet(
            column = column,
            value = value,
            onValueChange = onValueChange,
            onDateSettingsChange = onDateSettingsChange,
            onDismiss = { isSheetOpen = false },
        )
    }

    RowPageClickablePropertyValue(
        text = displayText.ifBlank { "Empty" },
        isEmpty = displayText.isBlank(),
        modifier = modifier,
        onClick = {
            onFocusProperties()
            isSheetOpen = true
        },
    )
}

@Composable
private fun RowPageChoicePropertyValue(
    column: PageTableColumn,
    value: String,
    onValueChange: (String) -> Unit,
    onFocusProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedValues = remember(value) { value.selectedChoiceValues() }
    val displayValue = remember(selectedValues) { selectedValues.joinToString(", ") }
    Box(modifier = modifier) {
        RowPageClickablePropertyValue(
            text = displayValue.ifBlank { "Empty" },
            isEmpty = displayValue.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onFocusProperties()
                isExpanded = true
            },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Clear") },
                onClick = {
                    isExpanded = false
                    onValueChange("")
                },
            )
            if (column.choiceOptions.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No options yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            }
            column.choiceOptions.forEach { option ->
                val selected = option.name in selectedValues
                DropdownMenuItem(
                    text = { Text(text = option.name) },
                    trailingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        if (column.type == PageTableColumnType.MultiSelect) {
                            val nextValues = if (selected) {
                                selectedValues.filterNot { selectedValue -> selectedValue == option.name }
                            } else {
                                selectedValues + option.name
                            }
                            onValueChange(nextValues.toChoiceCellValue())
                        } else {
                            isExpanded = false
                            onValueChange(option.name)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowPageClickablePropertyValue(
    text: String,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tableColors = TableGridTokens.colors()
    Row(
        modifier = modifier
            .height(TableGridTokens.dimensions.propertyValueHeight)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isEmpty) tableColors.emptyValue else MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
