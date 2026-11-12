package com.zeta.fondo.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v45 · Matemática del mapa de profundidad IA, sin Android (se prueba en la JVM).
 *
 * Los dos modelos (Depth Anything V2 Small y MiDaS v2.1 small) devuelven profundidad
 * INVERSA relativa: valor alto = cerca. Aquí se normaliza de forma robusta (percentiles
 * 2-98, para que un píxel aislado no aplane el resto) a bytes 0..255 con 255 = cerca.
 */
/** v46: placa de fondo (ARGB, mismo encuadre que la imagen) para el 2.5D. */
class PlateMap(val w: Int, val h: Int, val argb: IntArray, val ai: Boolean = false)

/** Mapa de profundidad listo para la GPU: un byte por píxel, fila 0 = arriba, 255 = cerca. */
class DepthMap(val w: Int, val h: Int, val map: ByteArray, val model: String = "")

object DepthMath {

    /** Lado largo de entrada de Depth Anything V2 (el ViT trabaja con parches de 14 px). */
    const val DAV2_LONG = 518
    const val PATCH = 14
    const val MIDAS_SIZE = 256

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Tamaño de entrada que conserva la proporción, lado largo [long] y múltiplos de [multiple]. */
    fun inputSize(w: Int, h: Int, long: Int = DAV2_LONG, multiple: Int = PATCH): IntArray {
        require(w > 0 && h > 0)
        val s = long.toFloat() / max(w, h)
        fun r(x: Int) = max(multiple, (x * s / multiple).roundToInt() * multiple)
        return intArrayOf(r(w), r(h))
    }

    /** Píxeles ARGB (fila 0 = arriba) → tensor CHW normalizado con la media/desviación de ImageNet. */
    fun toChw(argb: IntArray, w: Int, h: Int): FloatArray {
        require(argb.size >= w * h)
        val n = w * h
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            val c = argb[i]
            out[i] = (((c shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[n + i] = (((c shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * n + i] = ((c and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    /** Percentiles [lo] y [hi] (0..1) de los valores finitos, con una muestra de hasta 65 536. */
    fun percentiles(raw: FloatArray, lo: Float, hi: Float): FloatArray {
        val step = max(1, raw.size / 65_536)
        val sample = ArrayList<Float>(raw.size / step + 1)
        var i = 0
        while (i < raw.size) { val v = raw[i]; if (v.isFinite()) sample.add(v); i += step }
        if (sample.isEmpty()) return floatArrayOf(0f, 1f)
        sample.sort()
        fun at(p: Float) = sample[((sample.size - 1) * p.coerceIn(0f, 1f)).roundToInt()]
        return floatArrayOf(at(lo), at(hi))
    }

    /** Profundidad inversa cruda → bytes 0..255 (255 = cerca). Un mapa plano da 128 en todas partes. */
    fun normalize(raw: FloatArray, lo: Float = 0.02f, hi: Float = 0.98f): ByteArray {
        val (a, b) = percentiles(raw, lo, hi).let { it[0] to it[1] }
        val out = ByteArray(raw.size)
        val span = b - a
        if (!(span > 1e-6f)) { out.fill(128.toByte()); return out }
        for (i in raw.indices) {
            val v = raw[i]
            val t = if (v.isFinite()) ((v - a) / span).coerceIn(0f, 1f) else 0f
            out[i] = (t * 255f + .5f).toInt().toByte()
        }
        return out
    }

    /** Reescalado bilineal de un mapa de un canal (fila 0 = arriba). */
    fun resize(src: ByteArray, sw: Int, sh: Int, dw: Int, dh: Int): ByteArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val out = ByteArray(dw * dh)
        for (y in 0 until dh) {
            val fy = ((y + .5f) * sh / dh - .5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = minOf(y0 + 1, sh - 1); val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + .5f) * sw / dw - .5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = minOf(x0 + 1, sw - 1); val tx = fx - x0
                fun p(xx: Int, yy: Int) = (src[yy * sw + xx].toInt() and 0xFF).toFloat()
                val top = p(x0, y0) + (p(x1, y0) - p(x0, y0)) * tx
                val bot = p(x0, y1) + (p(x1, y1) - p(x0, y1)) * tx
                out[y * dw + x] = (top + (bot - top) * ty + .5f).toInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /**
     * Capa (0 = más lejana … layers-1 = más cercana) de un valor 0..255, igual que el shader
     * (depthQuant) salvo la transición suave. layers < 2 = continuo (devuelve -1).
     */
    fun layerOf(v: Int, layers: Int): Int {
        if (layers < 2) return -1
        val x = (v.coerceIn(0, 255) / 255f) * (layers - 1)
        return minOf(layers - 1, (x + .5f).toInt())
    }

    /** Porcentaje de píxeles de cada capa: sirve para mostrar el reparto en Ajustes. */
    fun layerShares(map: ByteArray, layers: Int): FloatArray {
        val n = layers.coerceAtLeast(1)
        val c = IntArray(n)
        if (n == 1) return floatArrayOf(1f)
        for (b in map) c[layerOf(b.toInt() and 0xFF, n)]++
        return FloatArray(n) { c[it].toFloat() / max(1, map.size) }
    }

    /**
     * Contraste del mapa (desviación típica 0..1). Por debajo de [LOW_CONTRAST] la escena casi
     * no tiene profundidad legible (típico de viñetas manga planas o tramas): se avisa y se
     * recomiendan los pinceles Acercar/Alejar en lugar de presentar el 2.5D como listo.
     */
    fun contrast(map: ByteArray): Float {
        if (map.isEmpty()) return 0f
        var s = 0.0; var s2 = 0.0
        for (b in map) { val v = (b.toInt() and 0xFF) / 255.0; s += v; s2 += v * v }
        val m = s / map.size
        return sqrt(max(0.0, s2 / map.size - m * m)).toFloat()
    }
    const val LOW_CONTRAST = 0.12f

    /**
     * v49 · Profundidad aproximada SIN IA para el «2.5D rápido» (como el efecto de CapCut):
     * - abajo = cerca, arriba = lejos (la regla clásica de la perspectiva);
     * - lo detallado y saturado se acerca (primeros planos), lo liso y apagado se aleja (cielo, niebla);
     * - Proteger (el personaje), si está pintado, va al frente;
     * - todo muy suavizado para que el movimiento no rompa la imagen.
     * Mismo formato que la IA: 0..255, 255 = cerca.
     */
    fun quick(argb: IntArray, w: Int, h: Int, protect: ByteArray? = null, subject: FloatArray? = null): ByteArray {
        require(argb.size >= w * h)
        val n = w * h
        val lum = FloatArray(n); val sat = FloatArray(n)
        for (i in 0 until n) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val mx = max(r, max(g, b)); val mn = minOf(r, g, b)
            lum[i] = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            sat[i] = if (mx == 0) 0f else (mx - mn).toFloat() / mx
        }
        val detail = FloatArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val dx = lum[y * w + minOf(x + 1, w - 1)] - lum[y * w + max(x - 1, 0)]
            val dy = lum[minOf(y + 1, h - 1) * w + x] - lum[max(y - 1, 0) * w + x]
            detail[i] = kotlin.math.abs(dx) + kotlin.math.abs(dy)
        }
        val rad = max(2, max(w, h) / 24)
        val det = boxBlur(detail, w, h, rad)
        val sa = boxBlur(sat, w, h, rad)
        val dRef = percentiles(det, .05f, .95f); val sRef = percentiles(sa, .05f, .95f)
        fun nrm(v: Float, ref: FloatArray) = if (ref[1] - ref[0] > 1e-5f) ((v - ref[0]) / (ref[1] - ref[0])).coerceIn(0f, 1f) else .5f
        var d = FloatArray(n) { i ->
            val v = (i / w).toFloat() / max(1, h - 1)
            .55f * v + .30f * nrm(det[i], dRef) + .15f * nrm(sa[i], sRef)
        }
        d = boxBlur(d, w, h, max(1, rad / 2))
        // v44.10 · Silueta aproximada por las caras (sin Proteger): el personaje al frente y el
        // fondo detrás, para que el 2.5D separe algo visible aunque no se pinte nada.
        if (subject != null && subject.size >= n && (protect == null || protect.size < n)) {
            val lo = percentiles(d, .02f, .98f)
            val span = max(1e-4f, lo[1] - lo[0])
            for (i in 0 until n) {
                val s = subject[i]
                val rel = ((d[i] - lo[0]) / span).coerceIn(0f, 1f)
                // Dentro del personaje: casi todo «cerca», con un poco de relieve propio.
                d[i] = lo[0] + span * (rel * (1f - s) + (.82f + .18f * rel) * s)
                // Fuera del personaje se empuja algo hacia el fondo.
                if (s < .5f) d[i] -= span * .18f * (1f - 2f * s)
            }
        }
        if (protect != null && protect.size >= n) {
            val p = boxBlur(FloatArray(n) { (protect[it].toInt() and 0xFF) / 255f }, w, h, 1)
            for (i in 0 until n) d[i] = d[i] * (1f - p[i]) + 1.15f * p[i]
        }
        return normalize(d, .02f, .98f)
    }

    /**
     * v44.10 · Silueta aproximada del personaje (0..1) a partir de caras normalizadas
     * [x, y, ancho, alto] (0..1): cabeza con pelo (elipse) + cuello y torso que se ensanchan
     * hacia abajo hasta el borde inferior. Bordes suaves. Sirve para el 2.5D sin IA descargable.
     */
    fun subjectPrior(w: Int, h: Int, faces: List<FloatArray>): FloatArray {
        val out = FloatArray(w * h)
        for (f in faces) {
            if (f.size < 4) continue
            val fx = f[0] * w; val fy = f[1] * h; val fw = f[2] * w; val fh = f[3] * h
            if (fw < 2f || fh < 2f) continue
            val cx = fx + fw / 2f
            val hcY = fy + fh * .42f
            val hrx = fw * .95f; val hry = fh * 1.0f        // cabeza + pelo
            val neckY = fy + fh * .85f
            for (y in 0 until h) {
                val py = y + .5f
                val t = (py - neckY) / (fh * 1.3f)            // 0 en el cuello, 1 ≈ hombros
                val half = if (t < 0f) 0f else fw * (.42f + .9f * min(t, 1f) + .35f * max(0f, t - 1f))
                for (x in 0 until w) {
                    val px = x + .5f
                    val ex = (px - cx) / hrx; val ey = (py - hcY) / hry
                    val head = smooth(1.08f, .8f, kotlin.math.sqrt(ex * ex + ey * ey))
                    val body = if (half <= 0f) 0f else smooth(half * 1.15f, half * .85f, kotlin.math.abs(px - cx)) *
                        smooth(neckY - fh * .1f, neckY + fh * .1f, py)
                    val v = max(head, body)
                    val i = y * w + x
                    if (v > out[i]) out[i] = v
                }
            }
        }
        return boxBlur(out, w, h, max(1, max(w, h) / 64))
    }

    /**
     * v44.10 · Silueta del personaje SIN IA: el fondo de las ilustraciones anime suele ser liso
     * y estar en las esquinas de arriba. Se «inunda» desde esas esquinas (y la parte alta de los
     * lados) por píxeles de color parecido y sin línea de tinta en medio; lo que no se inunda es
     * el personaje. Devuelve 0..1 (1 = personaje) o null si la imagen no parece un retrato con
     * fondo separable (paisajes, fondos muy cargados): entonces se usa solo el 2.5D general.
     */
    fun subjectFromBackground(argb: IntArray, w: Int, h: Int): FloatArray? {
        val n = w * h
        if (w < 16 || h < 16 || argb.size < n) return null
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) { val c = argb[i]; r[i] = ((c shr 16) and 0xFF) / 255f; g[i] = ((c shr 8) and 0xFF) / 255f; b[i] = (c and 0xFF) / 255f }
        val cw = max(3, (w * .10f).toInt()); val ch = max(3, (h * .08f).toInt())
        val seeds = ArrayList<Int>()
        for (y in 0 until ch) { for (x in 0 until cw) seeds.add(y * w + x); for (x in w - cw until w) seeds.add(y * w + x) }
        for (y in 0 until (h * .4f).toInt()) for (x in intArrayOf(0, 1, w - 2, w - 1)) seeds.add(y * w + x)
        // Paleta del fondo: hasta 64 colores de las semillas.
        val step = max(1, seeds.size / 64)
        val pal = seeds.filterIndexed { k, _ -> k % step == 0 }.map { floatArrayOf(r[it], g[it], b[it]) }
        fun score(i: Int): Float {
            var best = Float.MAX_VALUE
            for (p in pal) {
                val dr = r[i] - p[0]; val dg = g[i] - p[1]; val db = b[i] - p[2]
                val d = dr * dr + dg * dg + db * db
                if (d < best) best = d
            }
            return sqrt(best)
        }
        val thr = .10f; val near = .03f * .03f
        val bg = BooleanArray(n)
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in seeds) if (!bg[i] && score(i) < thr) { bg[i] = true; queue[tail++] = i }
        val sc = FloatArray(n) { -1f }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            for (k in 0..3) {
                val nx = x + intArrayOf(1, -1, 0, 0)[k]; val ny = y + intArrayOf(0, 0, 1, -1)[k]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (bg[j]) continue
                val dr = r[j] - r[i]; val dg = g[j] - g[i]; val db = b[j] - b[i]
                if (dr * dr + dg * dg + db * db >= near) continue
                if (sc[j] < 0f) sc[j] = score(j)
                if (sc[j] >= thr) continue
                bg[j] = true; queue[tail++] = j
            }
        }
        val frac = tail.toFloat() / n
        if (frac < .04f || frac > .85f) return null
        return boxBlur(FloatArray(n) { if (bg[it]) 0f else 1f }, w, h, max(1, max(w, h) / 96))
    }

    /**
     * v44.24 · Silueta del personaje (0..1) sin IA, la mejor disponible:
     *  1. fondo liso inundado desde las esquinas, SOLO si es creíble (antes una imagen con fondo
     *     con detalle podía dar el 80 % de la imagen como «personaje» y el 2.5D se veía quieto);
     *  2. si no, la silueta por color y nitidez ([SubjectSegment]), que funciona sin caras;
     *  3. las caras detectadas se suman a cualquiera de las dos (o van solas).
     * null = no se distingue un personaje (paisajes): el 2.5D usa el relieve general.
     */
    fun subjectAuto(argb: IntArray, w: Int, h: Int, faces: List<FloatArray> = emptyList()): FloatArray? {
        val fromBg = subjectFromBackground(argb, w, h)?.takeIf { SubjectSegment.plausible(it, argb, w, h) }
        val shape = fromBg ?: SubjectSegment.detect(argb, w, h)
        val fromFaces = if (faces.isNotEmpty()) subjectPrior(w, h, faces) else null
        return when {
            shape != null && fromFaces != null -> FloatArray(w * h) { max(shape[it], fromFaces[it]) }
            else -> shape ?: fromFaces
        }
    }

    /**
     * v44.16 · «Proteger automático»: silueta del personaje (0..255) para los efectos que la
     * necesitan (luz de contorno, aura, efectos detrás del personaje) cuando el usuario aún no ha
     * pintado Proteger. Une el fondo liso inundado y las caras detectadas. null = no se distingue.
     */
    fun autoProtect(argb: IntArray, w: Int, h: Int, faces: List<FloatArray> = emptyList()): ByteArray? {
        val s = subjectAuto(argb, w, h, faces) ?: return null
        // Cierre morfológico (dilatar y luego erosionar): tapa los huecos finos que deja la tinta
        // dentro del pelo o la ropa, para que el aura y la luz no aparezcan DENTRO del personaje.
        val r = max(2, max(w, h) / 48)
        val dil = boxBlur(FloatArray(w * h) { if (s[it] > .5f) 1f else 0f }, w, h, r)
        val closed = boxBlur(FloatArray(w * h) { if (dil[it] > .2f) 1f else 0f }, w, h, r)
        val soft = boxBlur(FloatArray(w * h) { if (closed[it] > .8f) 1f else 0f }, w, h, max(1, r / 3))
        var cover = 0f
        for (v in soft) cover += v
        cover /= soft.size
        if (cover < .04f || cover > .92f) return null
        return ByteArray(w * h) { (soft[it] * 255f).roundToInt().coerceIn(0, 255).toByte() }
    }

    /** 1 cuando v ≤ b, 0 cuando v ≥ a (a > b), suave entre medias; o al revés si a < b. */
    private fun smooth(a: Float, b: Float, v: Float): Float {
        val t = ((v - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Desenfoque de caja separable (radio [r]) con sumas acumuladas: O(n) sea cual sea el radio. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        val acc = FloatArray(max(w, h) + 1)
        for (y in 0 until h) {
            acc[0] = 0f
            for (x in 0 until w) acc[x + 1] = acc[x] + src[y * w + x]
            for (x in 0 until w) { val a = max(0, x - r); val b = minOf(w, x + r + 1); tmp[y * w + x] = (acc[b] - acc[a]) / (b - a) }
        }
        for (x in 0 until w) {
            acc[0] = 0f
            for (y in 0 until h) acc[y + 1] = acc[y] + tmp[y * w + x]
            for (y in 0 until h) { val a = max(0, y - r); val b = minOf(h, y + r + 1); out[y * w + x] = (acc[b] - acc[a]) / (b - a) }
        }
        return out
    }

    /** Huella de la imagen de entrada: si la imagen cambia, el mapa en caché ya no sirve. */
    fun stamp(argb: IntArray, w: Int, h: Int): String {
        val crc = java.util.zip.CRC32()
        val buf = java.nio.ByteBuffer.allocate(4)
        crc.update(w); crc.update(h)
        for (c in argb) { buf.clear(); buf.putInt(c); crc.update(buf.array()) }
        return java.lang.Long.toHexString(crc.value) + "-${w}x$h"
    }

    /** Metadatos del mapa guardado junto al proyecto (depth.txt). */
    data class Meta(val model: String, val w: Int, val h: Int, val stamp: String, val ms: Long, val contrast: Float) {
        fun encode(): String = "version=$VERSION\nmodel=$model\nw=$w\nh=$h\nstamp=$stamp\nms=$ms\ncontrast=$contrast\n"
        companion object {
            // v44.24: 2 = mapas con la silueta sin caras (SubjectSegment); los anteriores se rehacen.
            const val VERSION = 2
            fun decode(text: String): Meta? {
                val m = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
                if (m["version"]?.toIntOrNull() != VERSION) return null
                return Meta(m["model"] ?: return null, m["w"]?.toIntOrNull() ?: return null, m["h"]?.toIntOrNull() ?: return null,
                    m["stamp"] ?: return null, m["ms"]?.toLongOrNull() ?: 0L, m["contrast"]?.toFloatOrNull() ?: 0f)
            }
        }
    }
}
