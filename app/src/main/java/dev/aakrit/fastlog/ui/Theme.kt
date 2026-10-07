package dev.aakrit.fastlog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Every slot is defined explicitly. Material 3 falls back to its default purple
 * for any slot left unset, which would otherwise leak into chips and the nav bar.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD6C2),
    onPrimary = Color(0xFF00322C),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB8EDE4),
    secondary = Color(0xFF8FCFC4),
    onSecondary = Color(0xFF00322C),
    secondaryContainer = Color(0xFF1E3E3A),
    onSecondaryContainer = Color(0xFF9FE5D8),
    tertiary = Color(0xFFE8A33D),
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF523300),
    onTertiaryContainer = Color(0xFFFFDDAF),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE6EBEC),
    surface = Color(0xFF181D20),
    onSurface = Color(0xFFE6EBEC),
    surfaceVariant = Color(0xFF232A2E),
    onSurfaceVariant = Color(0xFFB4BFC3),
    outline = Color(0xFF3A444A),
    outlineVariant = Color(0xFF2C3438),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E9F8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EDE4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF3F6B64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE7),
    onSecondaryContainer = Color(0xFF07564C),
    tertiary = Color(0xFFB87514),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAF),
    onTertiaryContainer = Color(0xFF3A2500),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF131A1C),
    surface = Color.White,
    onSurface = Color(0xFF131A1C),
    surfaceVariant = Color(0xFFE6EDEE),
    onSurfaceVariant = Color(0xFF4A585C),
    outline = Color(0xFFC3CFD2),
    outlineVariant = Color(0xFFD8E2E4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun FastLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
