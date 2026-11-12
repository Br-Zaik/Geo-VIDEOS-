package com.zeta.fondo.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rectángulo de recorte en 0..1 (izquierda, arriba, derecha, abajo). */
data class CropRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/** Qué parte del marco se arrastra. */
enum class CropHandle { MOVE, TL, TR, BL, BR, L, T, R, B }

/**
 * v44.27 · Recortar una capa de imagen sin perder nada.
 *
 * - El archivo ORIGINAL de la capa se guarda ([ImageLayer.src]); la capa dibuja una copia
 *   recortada ([ImageLayer.file]). «Original» vuelve a la imagen completa cuando quieras.
 * - El recorte se marca sobre el original ya girado [ImageLayer.cropRot] (0/90/180/270, como las
 *   agujas del reloj). Sus medidas van en 0..1 de esa imagen girada.
 * - Al aplicar, la parte que queda se ve EN EL MISMO LUGAR y del mismo tamaño en el lienzo (como
 *   al recortar en un editor de fotos), con giro, espejo y claves incluidos.
 * - La vista previa y la exportación no cambian: siguen dibujando un archivo de imagen normal.
 */
object LayerCrop {
    /** Lado mínimo del marco (fracción de la imagen). */
    const val MIN = .04f

    /** Proporciones del marco (ancho/alto en píxeles; 0 = libre). */
    val ratios = listOf("Libre" to 0f, "Cuadrado" to 1f, "9:16" to 9f / 16f, "16:9" to 16f / 9f,
        "3:4" to 3f / 4f, "4:5" to 4f / 5f)

    fun normRot(rot: Int): Int = (((rot / 90) % 4) + 4) % 4 * 90

    /** Medidas de la imagen girada [rot]. */
    fun rotatedSize(w: Int, h: Int, rot: Int): Pair<Int, Int> =
        if (normRot(rot) % 180 == 90) h to w else w to h

    /** El mismo marco en la imagen girada 90° más (sentido del reloj). */
    fun rotate90(r: CropRect): CropRect = CropRect(1f - r.b, r.l, 1f - r.t, r.r)

    /** [r] marcado en la imagen girada [rot] → el mismo trozo en el original sin girar. */
    fun toSource(r: CropRect, rot: Int): CropRect = when (normRot(rot)) {
        90 -> CropRect(r.t, 1f - r.r, r.b, 1f - r.l)
        180 -> CropRect(1f - r.r, 1f - r.b, 1f - r.l, 1f - r.t)
        270 -> CropRect(1f - r.b, r.l, 1f - r.t, r.r)
        else -> r
    }

    /** Píxeles del original [w]×[h] que hay que copiar: x, y, ancho, alto. */
    fun sourcePixels(w: Int, h: Int, r: CropRect, rot: Int): IntArray {
        val s = toSource(clamp(r), rot)
        val x0 = (s.l * w).roundToInt().coerceIn(0, max(0, w - 1))
        val y0 = (s.t * h).roundToInt().coerceIn(0, max(0, h - 1))
        val x1 = (s.r * w).roundToInt().coerceIn(x0 + 1, max(1, w))
        val y1 = (s.b * h).roundToInt().coerceIn(y0 + 1, max(1, h))
        return intArrayOf(x0, y0, x1 - x0, y1 - y0)
    }

    /** Medidas en píxeles del resultado (lo que se muestra arriba del marco). */
    fun outputSize(w: Int, h: Int, r: CropRect, rot: Int): Pair<Int, Int> {
        val (rw, rh) = rotatedSize(w, h, rot)
        return max(1, (rw * r.w).roundToInt()) to max(1, (rh * r.h).roundToInt())
    }

    fun isFull(r: CropRect, rot: Int): Boolean =
        normRot(rot) == 0 && r.l <= 1e-3f && r.t <= 1e-3f && r.r >= 1f - 1e-3f && r.b >= 1f - 1e-3f

    /** Dentro de 0..1, ordenado y con el tamaño mínimo. */
    fun clamp(r: CropRect): CropRect {
        fun f(v: Float, d: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else d
        var l = f(min(r.l, r.r), 0f); var rr = f(max(r.l, r.r), 1f)
        var t = f(min(r.t, r.b), 0f); var b = f(max(r.t, r.b), 1f)
        if (rr - l < MIN) { val c = ((l + rr) / 2f).coerceIn(MIN / 2f, 1f - MIN / 2f); l = c - MIN / 2f; rr = c + MIN / 2f }
        if (b - t < MIN) { val c = ((t + b) / 2f).coerceIn(MIN / 2f, 1f - MIN / 2f); t = c - MIN / 2f; b = c + MIN / 2f }
        return CropRect(l, t, rr, b)
    }

    /** [ratio] (ancho/alto en píxeles) en fracciones de una imagen de [imgW]×[imgH]: ancho/alto. */
    private fun q(ratio: Float, imgW: Int, imgH: Int): Float = ratio * imgH.coerceAtLeast(1) / imgW.coerceAtLeast(1)

    /** El marco más grande con esa proporción, centrado donde estaba el marco y dentro de la imagen. */
    fun fitRatio(r: CropRect, ratio: Float, imgW: Int, imgH: Int): CropRect {
        if (ratio <= 0f) return clamp(r)
        val qq = q(ratio, imgW, imgH)
        var h = min(1f, 1f / qq)
        var w = h * qq
        if (w > 1f) { w = 1f; h = w / qq }
        val cx = r.cx.coerceIn(w / 2f, 1f - w / 2f)
        val cy = r.cy.coerceIn(h / 2f, 1f - h / 2f)
        return CropRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /**
     * Arrastre de [handle] [du], [dv] (fracciones de la imagen) desde el marco [r0] del inicio.
     * Con proporción fija ([ratio] > 0) solo las esquinas cambian el tamaño y la mantienen.
     */
    fun drag(r0: CropRect, handle: CropHandle, du: Float, dv: Float, ratio: Float, imgW: Int, imgH: Int): CropRect {
        if (handle == CropHandle.MOVE) {
            val dx = du.coerceIn(-r0.l, 1f - r0.r)
            val dy = dv.coerceIn(-r0.t, 1f - r0.b)
            return CropRect(r0.l + dx, r0.t + dy, r0.r + dx, r0.b + dy)
        }
        if (ratio > 0f) {
            val qq = q(ratio, imgW, imgH)
            val left = handle == CropHandle.TL || handle == CropHandle.BL
            val top = handle == CropHandle.TL || handle == CropHandle.TR
            if (handle !in setOf(CropHandle.TL, CropHandle.TR, CropHandle.BL, CropHandle.BR)) return r0
            // Esquina contraria fija; el tamaño sigue al dedo (promedio de lo horizontal y lo vertical).
            val ax = if (left) r0.r else r0.l
            val ay = if (top) r0.b else r0.t
            val wByU = if (left) r0.w - du else r0.w + du
            val wByV = (if (top) r0.h - dv else r0.h + dv) * qq
            var w = (wByU + wByV) / 2f
            val maxW = min(if (left) ax else 1f - ax, (if (top) ay else 1f - ay) * qq)
            val minW = max(MIN, MIN * qq)
            w = w.coerceIn(min(minW, maxW), maxW)
            val h = w / qq
            val l = if (left) ax - w else ax
            val t = if (top) ay - h else ay
            return CropRect(l, t, l + w, t + h)
        }
        var l = r0.l; var t = r0.t; var r = r0.r; var b = r0.b
        when (handle) {
            CropHandle.TL -> { l += du; t += dv }
            CropHandle.TR -> { r += du; t += dv }
            CropHandle.BL -> { l += du; b += dv }
            CropHandle.BR -> { r += du; b += dv }
            CropHandle.L -> l += du
            CropHandle.R -> r += du
            CropHandle.T -> t += dv
            CropHandle.B -> b += dv
            CropHandle.MOVE -> Unit
        }
        l = l.coerceIn(0f, r0.r - MIN); r = r.coerceIn(r0.l + MIN, 1f)
        t = t.coerceIn(0f, r0.b - MIN); b = b.coerceIn(r0.t + MIN, 1f)
        if (handle == CropHandle.TR || handle == CropHandle.BR || handle == CropHandle.R) l = r0.l
        if (handle == CropHandle.TL || handle == CropHandle.BL || handle == CropHandle.L) r = r0.r
        if (handle == CropHandle.BL || handle == CropHandle.BR || handle == CropHandle.B) t = r0.t
        if (handle == CropHandle.TL || handle == CropHandle.TR || handle == CropHandle.T) b = r0.b
        return CropRect(l, t, r, b)
    }

    /** El marco que tiene ahora la capa (sin recorte = toda la imagen, sin giro). */
    fun current(layer: ImageLayer): CropRect =
        if (layer.src == null) CropRect.FULL else CropRect(layer.cropL, layer.cropT, layer.cropR, layer.cropB)

    fun currentRot(layer: ImageLayer): Int = if (layer.src == null) 0 else normRot(layer.cropRot)

    /**
     * La capa con el recorte nuevo [nr] / [nrot] sobre su original de [srcW]×[srcH] píxeles.
     * [newFile] = la copia recortada (no se usa si el recorte es la imagen completa sin giro).
     * [baseAspect] = ancho/alto de la imagen de fondo (para pasar del ancho al alto del lienzo).
     * [atSec] = momento que se está viendo: si el tamaño o el giro tienen claves, la posición sin
     * claves queda exacta en ese momento (y la que tiene claves, en cada clave).
     */
    fun place(layer: ImageLayer, srcW: Int, srcH: Int, nr0: CropRect, nrot0: Int, newFile: String,
              baseAspect: Float, atSec: Float = 0f): ImageLayer {
        val nr = clamp(nr0)
        val nrot = normRot(nrot0)
        val or = current(layer)
        val orot = currentRot(layer)
        val srcFile = layer.src ?: layer.file
        val (rwO, rhO) = rotatedSize(srcW, srcH, orot)
        val (rwN, rhN) = rotatedSize(srcW, srcH, nrot)
        // Mismos píxeles de pantalla por píxel de la imagen: el tamaño cambia en la misma proporción.
        val k = (rwN * nr.w) / (rwO * or.w).coerceAtLeast(1e-6f)
        val aspectO = layer.aspect.coerceIn(.02f, 50f)
        val aspectN = ((rwN * nr.w) / (rhN * nr.h).coerceAtLeast(1e-6f)).coerceIn(.02f, 50f)
        val ba = if (baseAspect.isFinite() && baseAspect > 0f) baseAspect else 1f
        val fl = if (layer.flipX) -1f else 1f
        // Centro del marco nuevo dentro de la imagen que se ve ahora (−0,5..0,5 = dentro).
        val sameRot = nrot == orot
        val ox = if (sameRot) (nr.cx - or.l) / or.w - .5f else 0f
        val oy = if (sameRot) (nr.cy - or.t) / or.h - .5f else 0f
        fun shift(sx: Float, sy: Float, rotDeg: Float): Pair<Float, Float> {
            val a = rotDeg * (Math.PI / 180.0).toFloat()
            val ax = ox * abs(sx) * fl
            val ay = oy * abs(sy) / aspectO
            val c = cos(a); val s = sin(a)
            return (c * ax - s * ay) to ((s * ax + c * ay) * ba)
        }
        val (dx, dy) = shift(ImageLayers.valueAt(layer, "sx", atSec), ImageLayers.valueAt(layer, "sy", atSec),
            ImageLayers.valueAt(layer, "rot", atSec))
        val keys = layer.keys.mapValues { (prop, list) ->
            when (prop) {
                "sx", "sy" -> list.map { it.copy(value = it.value * k) }
                "x", "y" -> list.map { kf ->
                    val (kx, ky) = shift(ImageLayers.valueAt(layer, "sx", kf.t), ImageLayers.valueAt(layer, "sy", kf.t),
                        ImageLayers.valueAt(layer, "rot", kf.t))
                    kf.copy(value = kf.value + if (prop == "x") kx else ky)
                }
                else -> list
            }
        }
        val full = isFull(nr, nrot)
        return ImageLayers.sanitize(layer.copy(
            file = if (full) srcFile else newFile,
            src = if (full) null else srcFile,
            cropL = if (full) 0f else nr.l, cropT = if (full) 0f else nr.t,
            cropR = if (full) 1f else nr.r, cropB = if (full) 1f else nr.b,
            cropRot = if (full) 0 else nrot,
            aspect = aspectN,
            x = layer.x + dx, y = layer.y + dy,
            sx = layer.sx * k, sy = layer.sy * k,
            keys = keys
        ))
    }
}
