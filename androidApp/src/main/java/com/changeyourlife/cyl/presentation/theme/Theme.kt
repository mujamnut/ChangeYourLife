package com.changeyourlife.cyl.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.changeyourlife.cyl.data.local.session.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = CylPrimary,
    onPrimary = CylOnPrimary,
    primaryContainer = CylPrimaryContainer,
    onPrimaryContainer = CylOnPrimaryContainer,
    secondary = CylSecondary,
    onSecondary = CylOnSecondary,
    secondaryContainer = CylSecondaryContainer,
    onSecondaryContainer = CylOnSecondaryContainer,
    tertiary = CylTertiary,
    onTertiary = CylOnTertiary,
    tertiaryContainer = CylTertiaryContainer,
    onTertiaryContainer = CylOnTertiaryContainer,
    background = CylBackground,
    surface = CylSurface,
    surfaceVariant = CylSurfaceVariant,
    surfaceContainerLowest = CylSurfaceContainerLowest,
    surfaceContainerLow = CylSurfaceContainerLow,
    surfaceContainer = CylSurfaceContainer,
    surfaceContainerHigh = CylSurfaceContainerHigh,
    surfaceContainerHighest = CylSurfaceContainerHighest,
    onSurface = CylOnSurface,
    onSurfaceVariant = CylOnSurfaceVariant,
    outline = CylOutline,
    outlineVariant = CylOutlineVariant,
    inverseSurface = CylInverseSurface,
    inverseOnSurface = CylInverseOnSurface,
    inversePrimary = CylSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = CylDarkPrimary,
    onPrimary = CylDarkOnPrimary,
    primaryContainer = CylDarkPrimaryContainer,
    onPrimaryContainer = CylDarkOnPrimaryContainer,
    secondary = CylDarkSecondary,
    onSecondary = CylDarkOnSecondary,
    secondaryContainer = CylDarkSecondaryContainer,
    onSecondaryContainer = CylDarkOnSecondaryContainer,
    tertiary = CylDarkTertiary,
    onTertiary = CylDarkOnTertiary,
    tertiaryContainer = CylDarkTertiaryContainer,
    onTertiaryContainer = CylDarkOnTertiaryContainer,
    background = CylDarkBackground,
    surface = CylDarkSurface,
    surfaceVariant = CylDarkSurfaceVariant,
    surfaceContainerLowest = CylDarkSurfaceContainerLowest,
    surfaceContainerLow = CylDarkSurfaceContainerLow,
    surfaceContainer = CylDarkSurfaceContainer,
    surfaceContainerHigh = CylDarkSurfaceContainerHigh,
    surfaceContainerHighest = CylDarkSurfaceContainerHighest,
    onSurface = CylDarkOnSurface,
    onSurfaceVariant = CylDarkOnSurfaceVariant,
    outline = CylDarkOutline,
    outlineVariant = CylDarkOutlineVariant,
    inverseSurface = CylDarkInverseSurface,
    inverseOnSurface = CylDarkInverseOnSurface,
    inversePrimary = CylDarkSurface,
)

@Composable
fun ChangeYourLifeTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CylTypography,
        content = content,
    )
}
