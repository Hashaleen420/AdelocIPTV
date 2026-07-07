package com.adeloc.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = White,
    secondary = AccentTeal,
    onSecondary = BackgroundBlack,
    tertiary = ErrorRed,
    background = BackgroundBlack,
    surface = SurfaceGray,
    onBackground = White,
    onSurface = White,
    surfaceVariant = DialogBackground
)

@Composable
fun AdelocIPTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
