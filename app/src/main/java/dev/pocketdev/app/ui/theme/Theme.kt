package dev.pocketdev.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF8FD3FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00344F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004B6B),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFBEE9FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB6C9D3),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF213945),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF384F5B),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD2E6F2),
    tertiary = androidx.compose.ui.graphics.Color(0xFFC3C5DD),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF2D2F4D),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF444564),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE0E0FF),
    background = androidx.compose.ui.graphics.Color(0xFF0F1417),
    onBackground = androidx.compose.ui.graphics.Color(0xFFDFE3E5),
    surface = androidx.compose.ui.graphics.Color(0xFF151B1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFDFE3E5),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF414649),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC1C7CC),
    outline = androidx.compose.ui.graphics.Color(0xFF8B9297),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0061A4),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E4FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001D36),
    secondary = androidx.compose.ui.graphics.Color(0xFF51606F),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD4E5F7),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0D1D2A),
    tertiary = androidx.compose.ui.graphics.Color(0xFF67587A),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFEEDBFF),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF231332),
    background = androidx.compose.ui.graphics.Color(0xFFFCFCFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
    surface = androidx.compose.ui.graphics.Color(0xFFFCFCFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDEE2E6),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF414649),
    outline = androidx.compose.ui.graphics.Color(0xFF71787D),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
)

@Composable
fun PocketDevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}