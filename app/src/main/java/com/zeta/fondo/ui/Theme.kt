package com.zeta.fondo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Zeta Motion Studio: negro violeta, contraste de editor y acentos anime.
val Night = Color(0xFF101115)
val Dusk1 = Color(0xFF1B1C23)
val Dusk2 = Color(0xFF292A34)
val Peach = Color(0xFFAB83FC)
val Sky = Color(0xFF69E5FF)
val Ink = Color(0xFFF7F7FF)
val Mist = Color(0xFFADADB9)
val OnPeach = Color(0xFF130A28)
val Danger = Color(0xFFFF6B7A)

@Composable
fun FondoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Peach,
            onPrimary = OnPeach,
            secondary = Sky,
            onSecondary = Night,
            background = Night,
            onBackground = Ink,
            surface = Dusk1,
            onSurface = Ink,
            surfaceVariant = Dusk2,
            onSurfaceVariant = Mist,
            error = Danger
        ),
        content = content
    )
}
