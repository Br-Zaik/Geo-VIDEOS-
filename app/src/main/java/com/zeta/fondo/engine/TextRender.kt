package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.zeta.fondo.model.TextLayers
import com.zeta.fondo.model.TextSpec
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * v44.27 · Dibuja un [TextSpec] a una imagen transparente (lado largo ≤ maxSide): borde, relleno
 * y sombra suave, varias líneas centradas (Enter = línea nueva; una línea muy larga se parte sola).
 * Se usa una vez al crear o editar el texto; la capa luego es una imagen normal (nada se
 * redibuja en cada cuadro). Fuentes del sistema: no hay archivos de fuentes en la app.
 */
object TextRender {
    /** Tamaño de letra base (se reduce si el resultado no cabe en maxSide). */
    private const val BASE = 220f

    private val faces = HashMap<Int, Typeface>()

    @Synchronized
    fun typefaceFor(font: Int): Typeface = faces.getOrPut(font) {
        val f = TextLayers.fonts.getOrElse(font) { TextLayers.fonts[0] }
        try { Typeface.create(f.family, if (f.bold) Typeface.BOLD else Typeface.NORMAL) } catch (_: Throwable) { Typeface.DEFAULT_BOLD }
    }

    private class Measured(val layout: StaticLayout, val paint: TextPaint, val pad: Float, val outline: Float,
                           val shadowR: Float, val shadowD: Float, val w: Int, val h: Int)

    private fun measure(text: String, s: TextSpec, size: Float): Measured {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typefaceFor(s.font)
            textSize = size
            color = s.color
        }
        val widest = text.split('\n').maxOf { StaticLayout.getDesiredWidth(it, paint) }
        // Una línea muy larga se parte en ~12 letras de ancho (si no, las letras quedarían diminutas).
        val wrap = min(ceil(widest).toInt().coerceAtLeast(1), (size * 12f).toInt().coerceAtLeast(1))
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, wrap)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()
        val outline = size * TextLayers.outlineFraction(s.outline)
        val shadowR = if (s.shadow) size * .07f else 0f
        val shadowD = if (s.shadow) size * .035f else 0f
        val pad = ceil(outline + shadowR + shadowD + size * .04f)
        return Measured(layout, paint, pad, outline, shadowR, shadowD,
            (wrap + 2f * pad).toInt().coerceAtLeast(1), (layout.height + 2f * pad).toInt().coerceAtLeast(1))
    }

    fun render(spec: TextSpec, maxSide: Int): Bitmap {
        val s = TextLayers.sanitize(spec)
        val text = s.text.ifBlank { " " }
        var m = measure(text, s, BASE)
        val longest = max(m.w, m.h)
        if (longest > maxSide) m = measure(text, s, BASE * maxSide / longest.toFloat() * .98f)
        val bmp = Bitmap.createBitmap(m.w.coerceAtMost(maxSide), m.h.coerceAtMost(maxSide), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(m.pad, m.pad)
        val p = m.paint
        val shadowColor = 0xAA000000.toInt()
        if (m.outline > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = m.outline * 2f
            p.strokeJoin = Paint.Join.ROUND
            p.strokeCap = Paint.Cap.ROUND
            p.color = s.outlineColor
            if (s.shadow) p.setShadowLayer(m.shadowR, m.shadowD, m.shadowD, shadowColor)
            m.layout.draw(c)
            p.clearShadowLayer()
        }
        p.style = Paint.Style.FILL
        p.color = s.color
        if (s.shadow && m.outline <= 0f) p.setShadowLayer(m.shadowR, m.shadowD, m.shadowD, shadowColor)
        m.layout.draw(c)
        p.clearShadowLayer()
        return bmp
    }
}
