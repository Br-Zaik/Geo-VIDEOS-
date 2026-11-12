package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.zeta.fondo.model.MaskStroke
import com.zeta.fondo.model.PointN
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Máscaras nombradas de 8 bits (ALPHA_8). Se guardan los trazos para poder deshacer
 * y redibujar. Todo acceso a los bitmaps va dentro de synchronized(lock),
 * porque el hilo de OpenGL los lee para subirlos como textura.
 */
class MaskLayers private constructor(val w: Int, val h: Int) {

    val lock = Any()
    /** Aviso tras cada cambio (fuera del candado) para que la vista previa dibuje al instante. */
    @Volatile var onChange: (() -> Unit)? = null
    val bitmaps = ArrayList<Bitmap>()
    private val canvases = ArrayList<Canvas>()
    val strokes = ArrayList<ArrayList<MaskStroke>>()
    val versions = ArrayList<Int>()
    /** Filas modificadas desde la última subida a OpenGL (por capa). top > bottom = nada pendiente. */
    private val dirtyTop = ArrayList<Int>()
    private val dirtyBottom = ArrayList<Int>()
    val names = ArrayList<String>()
    private var serial = 0

    init {
        listOf("Congelar", "Zona azul", "Zona verde").forEach { addLayer(it) }
    }

    val count: Int get() = bitmaps.size

    fun addLayer(name: String): Int = synchronized(lock) {
        if (count >= MAX_MASKS) return@synchronized -1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        bitmaps.add(bmp)
        canvases.add(Canvas(bmp))
        strokes.add(ArrayList())
        versions.add(++serial)
        dirtyTop.add(0); dirtyBottom.add(h)
        names.add(name.trim().take(32).ifBlank { "Máscara ${count}" })
        count - 1
    }

    fun renameLayer(index: Int, name: String) = synchronized(lock) {
        if (index in names.indices && index > 0) names[index] = name.trim().take(32).ifBlank { "Máscara $index" }
    }


    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val dstOut = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private var cachedStroke: MaskStroke? = null
    private var cachedBlur: BlurMaskFilter? = null

    companion object {
        const val MAX_MASKS = 12
        fun create(imgW: Int, imgH: Int, maxSide: Int = 1280): MaskLayers {
            val k = min(1f, maxSide.toFloat() / max(imgW, imgH))
            var w = max(4, (imgW * k).roundToInt())
            val h = max(4, (imgH * k).roundToInt())
            w = (w + 3) / 4 * 4 // ancho múltiplo de 4: filas sin relleno al subir a OpenGL
            return MaskLayers(w, h)
        }
    }

    private fun blurFor(s: MaskStroke): BlurMaskFilter? {
        if (cachedStroke === s) return cachedBlur
        val rPx = s.radius * h
        val blur = rPx * s.feather * 0.9f
        cachedStroke = s
        cachedBlur = if (blur >= 0.5f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
        return cachedBlur
    }

    private fun configure(p: Paint, s: MaskStroke) {
        val rPx = s.radius * h
        p.xfermode = if (s.erase) dstOut else null
        p.maskFilter = blurFor(s)
        val blur = rPx * s.feather * 0.9f
        p.strokeWidth = max(1f, rPx * 2f - blur)
    }

    private fun markRows(layer: Int, yA: Float, yB: Float, s: MaskStroke) {
        val pad = s.radius * h * (1f + s.feather) + 3f
        val top = (min(yA, yB) - pad).toInt().coerceIn(0, h)
        val bottom = (max(yA, yB) + pad).toInt().plus(1).coerceIn(0, h)
        dirtyTop[layer] = min(dirtyTop[layer], top)
        dirtyBottom[layer] = max(dirtyBottom[layer], bottom)
    }

    private fun markAll(layer: Int) {
        dirtyTop[layer] = 0
        dirtyBottom[layer] = h
    }

    /**
     * Devuelve y limpia el rango de filas pendientes de la capa [layer] como
     * [desde, hasta). Debe llamarse dentro de synchronized(lock).
     */
    fun takeDirtyRows(layer: Int): IntArray {
        if (layer !in dirtyTop.indices) return intArrayOf(0, h)
        val out = if (dirtyTop[layer] < dirtyBottom[layer]) intArrayOf(dirtyTop[layer], dirtyBottom[layer]) else intArrayOf(0, 0)
        dirtyTop[layer] = h
        dirtyBottom[layer] = 0
        return out
    }

    private fun drawDot(s: MaskStroke, pt: PointN) {
        configure(dotPaint, s)
        val r = max(0.5f, dotPaint.strokeWidth / 2f)
        canvases[s.layer].drawCircle(pt.x * w, pt.y * h, r, dotPaint)
        markRows(s.layer, pt.y * h, pt.y * h, s)
    }

    /** v46: carga las máscaras de imagen (PNG del proyecto) por nombre. */
    @Volatile var imageSource: ((String) -> Bitmap?)? = null
    private val imageCache = HashMap<String, Bitmap>()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Añade al caché una máscara recién creada (evita leerla del disco). */
    fun putImage(name: String, bmp: Bitmap) = synchronized(lock) { imageCache[name] = bmp }

    private fun drawFull(s: MaskStroke) {
        val c = canvases[s.layer]
        s.image?.let { name ->
            val bmp = imageCache[name] ?: imageSource?.invoke(name)?.also { imageCache[name] = it } ?: return
            markAll(s.layer)
            imagePaint.xfermode = if (s.erase) dstOut else null
            c.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), imagePaint)
            return
        }
        if (s.fill) {
            markAll(s.layer)
            dotPaint.maskFilter = null
            dotPaint.xfermode = if (s.erase) dstOut else null
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dotPaint)
            return
        }
        if (s.points.isEmpty()) return
        if (s.points.size == 1) {
            drawDot(s, s.points[0])
            return
        }
        markAll(s.layer)
        configure(linePaint, s)
        val path = Path()
        path.moveTo(s.points[0].x * w, s.points[0].y * h)
        for (i in 1 until s.points.size) path.lineTo(s.points[i].x * w, s.points[i].y * h)
        c.drawPath(path, linePaint)
    }

    fun addStroke(s: MaskStroke) {
        synchronized(lock) {
            strokes[s.layer].add(s)
            drawFull(s)
            versions[s.layer] = ++serial
        }
        onChange?.invoke()
    }

    fun extendStroke(s: MaskStroke, pt: PointN) {
        synchronized(lock) {
            val last = s.points.lastOrNull()
            s.points.add(pt)
            if (last == null) {
                drawDot(s, pt)
            } else {
                configure(linePaint, s)
                canvases[s.layer].drawLine(last.x * w, last.y * h, pt.x * w, pt.y * h, linePaint)
                markRows(s.layer, last.y * h, pt.y * h, s)
            }
            versions[s.layer] = ++serial
        }
        onChange?.invoke()
    }

    fun removeStroke(s: MaskStroke) {
        synchronized(lock) {
            strokes[s.layer].remove(s)
            redraw(s.layer)
        }
        onChange?.invoke()
    }

    fun setAll(list: List<MaskStroke>, labels: List<String> = emptyList()) {
        synchronized(lock) {
            val required = (list.maxOfOrNull { it.layer + 1 } ?: 3)
                .coerceAtLeast(labels.size).coerceIn(3, MAX_MASKS)
            while (count > required) {
                bitmaps.removeAt(count - 1).recycle()
                canvases.removeAt(canvases.lastIndex); strokes.removeAt(strokes.lastIndex)
                versions.removeAt(versions.lastIndex); names.removeAt(names.lastIndex)
                dirtyTop.removeAt(dirtyTop.lastIndex); dirtyBottom.removeAt(dirtyBottom.lastIndex)
            }
            while (count < required) addLayer(labels.getOrElse(count) { "Máscara $count" })
            labels.take(MAX_MASKS).forEachIndexed { i, name ->
                names[i] = name.take(32).ifBlank { "Máscara $i" }
            }
            for (i in bitmaps.indices) strokes[i].clear()
            for (s in list) if (s.layer in bitmaps.indices) strokes[s.layer].add(s)
            for (i in bitmaps.indices) redraw(i)
        }
        onChange?.invoke()
    }

    /**
     * Rectángulo que ocupa lo pintado en una capa, en fracción de imagen [x0, y0, x1, y1].
     * Lo usa Movimiento natural para fijar la raíz en el borde REAL de la zona pintada
     * (antes se medía contra el borde de toda la imagen). null si la capa está vacía.
     */
    fun paintedBounds(layer: Int, threshold: Int = 24): FloatArray? = synchronized(lock) {
        if (layer !in bitmaps.indices) return@synchronized null
        val bmp = bitmaps[layer]
        val row = bmp.rowBytes
        val buf = java.nio.ByteBuffer.allocate(row * bmp.height)
        bmp.copyPixelsToBuffer(buf)
        MaskBounds.of(buf.array(), bmp.width, bmp.height, row, threshold)
    }

    /** Copia de todos los trazos (para guardar el proyecto). */
    fun snapshot(): List<MaskStroke> {
        synchronized(lock) {
            val out = ArrayList<MaskStroke>()
            for (i in bitmaps.indices) {
                for (s in strokes[i]) {
                    val c = MaskStroke(s.layer, s.radius, s.feather, s.erase, s.fill, s.image)
                    c.points.addAll(s.points)
                    out.add(c)
                }
            }
            return out
        }
    }

    private fun redraw(layer: Int) {
        markAll(layer)
        bitmaps[layer].eraseColor(Color.TRANSPARENT)
        for (s in strokes[layer]) drawFull(s)
        versions[layer] = ++serial
    }
}
