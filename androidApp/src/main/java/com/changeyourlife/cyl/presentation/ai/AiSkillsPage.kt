package com.changeyourlife.cyl.presentation.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.changeyourlife.cyl.domain.model.AiSkill

private const val NewSkillEditorKey = "__new_skill__"
private const val MaxAiSkillNameChars = 64
private const val MaxAiSkillWhenChars = 320
private const val MaxAiSkillInstructionChars = 2_000

@Composable
internal fun AiSkillsRoute(
    skills: List<AiSkill>,
    errorMessage: String?,
    onBack: () -> Unit,
    onSaveSkill: (String?, String, String, String, Boolean) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onSetSkillEnabled: (String, Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSkill = editorKey
        ?.takeUnless { key -> key == NewSkillEditorKey }
        ?.let { skillId -> skills.firstOrNull { skill -> skill.id == skillId } }

    LaunchedEffect(editorKey, selectedSkill, skills) {
        if (editorKey != null && editorKey != NewSkillEditorKey && selectedSkill == null) {
            editorKey = null
        }
    }

    if (editorKey != null) {
        AiSkillEditorPage(
            skill = selectedSkill,
            onBack = { editorKey = null },
            onSave = { skillId, name, whenToUse, instructions, isEnabled ->
                onSaveSkill(skillId, name, whenToUse, instructions, isEnabled)
                editorKey = null
            },
            onDelete = { skillId ->
                onDeleteSkill(skillId)
                editorKey = null
            },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AiFullPageHeader(
                title = "Skills",
                onBack = onBack,
                action = {
                    IconButton(
                        onClick = { editorKey = NewSkillEditorKey },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New skill",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable(onClick = onDismissError),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (skills.isEmpty()) {
                AiSkillsEmptyState(
                    onCreate = { editorKey = NewSkillEditorKey },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    items(
                        items = skills,
                        key = AiSkill::id,
                    ) { skill ->
                        AiSkillRow(
                            skill = skill,
                            onClick = { editorKey = skill.id },
                            onEnabledChange = { enabled ->
                                onSetSkillEnabled(skill.id, enabled)
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSkillsEmptyState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No skills yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "New skill",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AiSkillRow(
    skill: AiSkill,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f),
            shape = RoundedCornerShape(11.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = skill.whenToUse,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = skill.isEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Composable
private fun AiSkillEditorPage(
    skill: AiSkill?,
    onBack: () -> Unit,
    onSave: (String?, String, String, String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var whenToUse by rememberSaveable(skill?.id) { mutableStateOf(skill?.whenToUse.orEmpty()) }
    var instructions by rememberSaveable(skill?.id) { mutableStateOf(skill?.instructions.orEmpty()) }
    var isEnabled by rememberSaveable(skill?.id) { mutableStateOf(skill?.isEnabled ?: true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && whenToUse.isNotBlank() && instructions.isNotBlank()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AiFullPageHeader(
                title = if (skill == null) "New skill" else "Edit skill",
                onBack = onBack,
                action = {
                    IconButton(
                        onClick = {
                            onSave(skill?.id, name, whenToUse, instructions, isEnabled)
                        },
                        enabled = canSave,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save skill",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value.take(MaxAiSkillNameChars) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = whenToUse,
                    onValueChange = { value -> whenToUse = value.take(MaxAiSkillWhenChars) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("When to use") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
            item {
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { value ->
                        instructions = value.take(MaxAiSkillInstructionChars)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instructions") },
                    minLines = 7,
                    maxLines = 14,
                    supportingText = {
                        Text("${instructions.length}/$MaxAiSkillInstructionChars")
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEnabled = !isEnabled }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enabled",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked -> isEnabled = checked },
                    )
                }
            }
            if (skill != null) {
                item {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Delete skill",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation && skill != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete skill?") },
            text = { Text(skill.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(skill.id)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
