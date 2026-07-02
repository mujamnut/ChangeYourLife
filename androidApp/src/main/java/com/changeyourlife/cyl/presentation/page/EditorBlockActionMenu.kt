package com.changeyourlife.cyl.presentation.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.PageBlockInsertPosition
import com.changeyourlife.cyl.domain.model.PageBlockType

@Composable
internal fun EditorBlockActionMenu(
    expanded: Boolean,
    currentType: PageBlockType,
    onDismiss: () -> Unit,
    onTurnInto: (PageBlockType) -> Unit,
    onInsertNear: (PageBlockType, PageBlockInsertPosition) -> Unit,
    onAddChild: (PageBlockType) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onDelete: () -> Unit,
    context: EditorCommandContext = EditorCommandContext(),
) {
    val typeEntries = EditorCommandRegistry.changeTypeEntries(context)
    val insertEntries = EditorCommandRegistry.insertBlockEntries(context = context)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        EditorBlockActionSection("Turn into")
        typeEntries.forEach { entry ->
            val type = entry.changeTypeOrNull() ?: return@forEach
            DropdownMenuItem(
                text = { Text(text = entry.command.label) },
                enabled = type != currentType,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismiss()
                    onTurnInto(type)
                },
            )
        }

        HorizontalDivider()
        EditorBlockActionSection("Insert")
        insertEntries.forEach { entry ->
            val action = entry.insertBlockActionOrNull() ?: return@forEach
            DropdownMenuItem(
                text = { Text(text = entry.command.label) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismiss()
                    onInsertNear(action.type, action.position)
                },
            )
        }

        HorizontalDivider()
        EditorBlockActionSection("Add inside")
        typeEntries
            .filter { entry -> entry.changeTypeOrNull() != PageBlockType.DatabaseTable }
            .take(6)
            .forEach { entry ->
                val type = entry.changeTypeOrNull() ?: return@forEach
                DropdownMenuItem(
                    text = { Text(text = entry.command.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onDismiss()
                        onAddChild(type)
                    },
                )
            }

        HorizontalDivider()
        EditorBlockActionItem(
            label = "Move up",
            icon = Icons.Rounded.KeyboardArrowUp,
            onClick = {
                onDismiss()
                onMoveUp()
            },
        )
        EditorBlockActionItem(
            label = "Move down",
            icon = Icons.Rounded.KeyboardArrowDown,
            onClick = {
                onDismiss()
                onMoveDown()
            },
        )
        EditorBlockActionItem(
            label = "Indent",
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            onClick = {
                onDismiss()
                onIndent()
            },
        )
        EditorBlockActionItem(
            label = "Outdent",
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            onClick = {
                onDismiss()
                onOutdent()
            },
        )
        EditorBlockActionItem(
            label = "Delete",
            icon = Icons.Rounded.Delete,
            isDestructive = true,
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

@Composable
private fun EditorBlockActionSection(label: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EditorBlockActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        onClick = onClick,
    )
}
