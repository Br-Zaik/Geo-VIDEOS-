package com.zeta.fondo.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * v44.21 · «Libro de efectos»: fuego simulado con física de fluidos (32 cuadros en bucle) y
 * hojas/pétalos pintados con detalle, en UNA imagen de datos (assets/fx/fxbook.webp, 1024×896,
 * WebP sin pérdida, ~0,4 MB). Todo generado por nosotros (tools/fxgen): sin assets de terceros.
 *
 * Optimización: solo se carga si un efecto que lo usa está encendido, una vez por contexto de
 * OpenGL, y se libera con él. Si no se puede cargar, los efectos vuelven a su versión procedural.
 */
object FxBook {
    const val PATH = "fx/fxbook.webp"
    const val WIDTH = 1024
    const val HEIGHT = 896
    /** Efectos (variantes del shader) que leen el libro. */
    val users = setOf("flame", "leaves", "sakura")

    @Volatile private var assets: AssetManager? = null

    fun init(a: AssetManager) { if (assets == null) assets = a }

    fun needed(features: Set<String>): Boolean = features.any { it in users }

    /** Decodifica el libro (≈20–40 ms, una sola vez). null si no hay assets o falla. */
    fun decode(): Bitmap? = try {
        assets?.open(PATH)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            })
        }?.takeIf { it.width == WIDTH && it.height == HEIGHT }
    } catch (t: Throwable) {
        com.zeta.fondo.diag.ZetaLog.error("FxBook", "No se pudo cargar el libro de efectos: ${t.message}")
        null
    }
}
