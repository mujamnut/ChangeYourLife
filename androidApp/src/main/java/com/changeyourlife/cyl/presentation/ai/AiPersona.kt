package com.changeyourlife.cyl.presentation.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal const val DefaultAiAvatarIconKey = "spark"

data class AiPersonaUiState(
    val displayName: String = "CYL AI",
    val avatarColorIndex: Int = 0,
    val avatarIconKey: String = DefaultAiAvatarIconKey,
)

internal data class AiAvatarSpec(
    val color: Color,
    val icon: AiAvatarIconOption,
)

internal data class AiAvatarIconOption(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

internal val aiAvatarColors = listOf(
    Color(0xFFE74C3C),
    Color(0xFF2E7DFF),
    Color(0xFF18A058),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFF0F766E),
)

internal fun aiAvatarIconOptions(): List<AiAvatarIconOption> {
    return listOf(
        AiAvatarIconOption(DefaultAiAvatarIconKey, Icons.Rounded.AutoAwesome, "Spark"),
        AiAvatarIconOption("edit", Icons.Rounded.Edit, "Edit"),
        AiAvatarIconOption("web", Icons.Rounded.Public, "Web"),
        AiAvatarIconOption("person", Icons.Rounded.Person, "Guide"),
    )
}

internal fun AiPersonaUiState.toAvatarSpec(): AiAvatarSpec {
    val icons = aiAvatarIconOptions()
    return AiAvatarSpec(
        color = aiAvatarColors[avatarColorIndex.mod(aiAvatarColors.size)],
        icon = icons.firstOrNull { icon -> icon.key == avatarIconKey } ?: icons.first(),
    )
}

@Composable
internal fun AiAvatar(
    spec: AiAvatarSpec,
    size: Int,
    iconSize: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 2).dp))
            .background(spec.color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}
