package dev.pocketdev.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.pocketdev.settings.ThemeMode

private val PocketBlue = Color(0xFF3D6DFF)
private val PocketTeal = Color(0xFF00BFA6)
private val PocketDarkBackground = Color(0xFF0F1218)
private val PocketDarkSurface = Color(0xFF171C26)

private val LightColors = lightColorScheme(
    primary = PocketBlue,
    secondary = PocketTeal,
    background = Color(0xFFF7F9FF),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    secondary = Color(0xFF4FD8C7),
    background = PocketDarkBackground,
    surface = PocketDarkSurface,
)

@Composable
fun PocketDevTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}