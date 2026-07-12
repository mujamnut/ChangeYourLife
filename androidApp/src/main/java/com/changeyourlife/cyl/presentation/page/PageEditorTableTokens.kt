package com.changeyourlife.cyl.presentation.page

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class TableGridDimensions(
    val cellWidth: Dp = 180.dp,
    val actionWidth: Dp = 48.dp,
    val addColumnWidth: Dp = 64.dp,
    val headerHeight: Dp = 44.dp,
    val rowHeight: Dp = 50.dp,
    val groupHeaderWidth: Dp = 280.dp,
    val boardColumnWidth: Dp = 220.dp,
    val calendarDayWidth: Dp = 240.dp,
    val galleryItemWidth: Dp = 220.dp,
    val timelineDateWidth: Dp = 92.dp,
    val dashboardStatWidth: Dp = 120.dp,
    val dashboardLabelWidth: Dp = 96.dp,
    val dashboardCountWidth: Dp = 32.dp,
    val propertySymbolWidth: Dp = 44.dp,
    val propertyNameWidth: Dp = 143.dp,
    val propertyRowMinHeight: Dp = 56.dp,
    val propertyValueHeight: Dp = 52.dp,
)

@Immutable
internal data class TableGridColors(
    val cellBackground: Color,
    val headerBackground: Color,
    val highlightedRowBackground: Color,
    val draggedRowBackground: Color,
    val divider: Color,
    val propertyDivider: Color,
    val propertyIcon: Color,
    val emptyValue: Color,
)

internal object TableGridTokens {
    val dimensions = TableGridDimensions()

    @Composable
    fun colors(): TableGridColors {
        val colorScheme = MaterialTheme.colorScheme
        return TableGridColors(
            cellBackground = colorScheme.surface,
            headerBackground = colorScheme.surface,
            highlightedRowBackground = colorScheme.primaryContainer.copy(alpha = 0.35f),
            draggedRowBackground = colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            divider = colorScheme.outlineVariant.copy(alpha = 0.40f),
            propertyDivider = colorScheme.outlineVariant.copy(alpha = 0.48f),
            propertyIcon = colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            emptyValue = colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
        )
    }
}
