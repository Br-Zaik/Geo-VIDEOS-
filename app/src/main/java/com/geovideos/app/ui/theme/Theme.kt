package com.geovideos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeoDarkColors = darkColorScheme(
    primary = Color(0xFF9B6DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF382060),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFB9A6D9),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFF5F2FA),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFF5F2FA),
    surfaceVariant = Color(0xFF23232B),
    onSurfaceVariant = Color(0xFFC9C4D2),
    error = Color(0xFFFFB4AB)
)

@Composable
fun GeoVideosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeoDarkColors,
        content = content
    )
}
