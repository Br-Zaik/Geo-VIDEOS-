package com.zeta.fondo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Silueta de una goma de borrar inclinada, no una varita mágica. */
val ZetaEraserIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Borrador", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(16.9f, 3.0f)
            curveTo(16.0f, 2.1f, 14.6f, 2.1f, 13.7f, 3.0f)
            lineTo(3.3f, 13.4f)
            curveTo(2.4f, 14.3f, 2.4f, 15.8f, 3.3f, 16.6f)
            lineTo(7.9f, 21.2f)
            lineTo(14.0f, 21.2f)
            lineTo(21.4f, 13.8f)
            curveTo(22.3f, 12.9f, 22.3f, 11.5f, 21.4f, 10.6f)
            close()
        }
        path(fill = SolidColor(Color(0xFF775CBE))) {
            moveTo(9.4f, 19.1f)
            lineTo(4.7f, 14.4f)
            lineTo(10.7f, 8.4f)
            lineTo(17.5f, 15.2f)
            lineTo(13.6f, 19.1f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(15.0f, 20.3f)
            lineTo(22.0f, 20.3f)
            lineTo(22.0f, 21.7f)
            lineTo(13.6f, 21.7f)
            close()
        }
    }.build()
}
