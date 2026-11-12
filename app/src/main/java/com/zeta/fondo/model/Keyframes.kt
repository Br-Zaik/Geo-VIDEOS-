package com.zeta.fondo.model

/**
 * v44 · Fotogramas clave por bloque y por parámetro (código puro, pruebas JVM).
 *
 * - El tiempo de cada clave es LOCAL al bloque (segundos desde su inicio): mover el bloque
 *   mueve sus claves con él. Recortar por la izquierda las deja en su sitio absoluto.
 * - La curva de una clave decide cómo se llega DESDE ella a la siguiente.
 * - Antes de la primera clave vale la primera; después de la última, la última.
 * - La velocidad (cycles) no se anima: cambiarla durante el vídeo rompería el bucle.
 */
data class Keyframe(val t: Float, val value: Float, val curve: Int = Keyframes.LINEAR)

object Keyframes {
    const val LINEAR = 0
    const val EASE_IN = 1
    const val EASE_OUT = 2
    const val EASE_IN_OUT = 3
    val curveNames = listOf("Lineal", "Entrada suave", "Salida suave", "Entrada y salida")

    /** Margen para considerar que el cabezal está «sobre» una clave (≈ 1 cuadro a 24 fps). */
    const val SNAP_SEC = .04f

    /** Sufijos que se pueden animar (todo lo continuo; nada de velocidad, tipo, máscara…). */
    private val blocked = setOf("cycles", "on", "mode", "every", "mask", "ownedMask", "variant", "shape",
        "zone", "origin", "behind", "tintOn", "followWind", "anchor", "start", "dur",
        "zshape", "zx", "zy", "zw", "zh", "zrot", "zsoft")

    fun animatable(key: String): Boolean {
        val suffix = key.substringAfter('.', "")
        return suffix.isNotEmpty() && suffix !in blocked && !suffix.startsWith("copy.")
    }

    fun ease(curve: Int, u: Float): Float {
        val x = u.coerceIn(0f, 1f)
        return when (curve) {
            EASE_IN -> x * x
            EASE_OUT -> 1f - (1f - x) * (1f - x)
            EASE_IN_OUT -> x * x * (3f - 2f * x)
            else -> x
        }
    }

    /** Segmento activo: índice de la clave anterior y peso (0..1) hacia la siguiente. */
    fun segment(keys: List<Keyframe>, local: Float): Pair<Int, Float> {
        if (keys.size < 2 || local <= keys.first().t) return 0 to 0f
        if (local >= keys.last().t) return keys.size - 1 to 0f
        var i = 0
        while (i < keys.size - 2 && local >= keys[i + 1].t) i++
        val a = keys[i]; val b = keys[i + 1]
        val span = (b.t - a.t).coerceAtLeast(1e-4f)
        return i to ease(a.curve, (local - a.t) / span)
    }

    fun valueAt(keys: List<Keyframe>, local: Float): Float? {
        if (keys.isEmpty()) return null
        val (i, w) = segment(keys, local)
        if (w <= 0f || i >= keys.size - 1) return keys[i].value
        return keys[i].value + (keys[i + 1].value - keys[i].value) * w
    }

    /** Añade o sustituye la clave en [t] (dentro de [SNAP_SEC]); la lista queda ordenada. */
    fun put(keys: List<Keyframe>, t: Float, value: Float, curve: Int? = null): List<Keyframe> {
        val tt = Math.round(t.coerceAtLeast(0f) * 100f) / 100f
        val existing = keys.indexOfFirst { kotlin.math.abs(it.t - tt) <= SNAP_SEC }
        val out = keys.toMutableList()
        if (existing >= 0) out[existing] = out[existing].copy(value = value, curve = curve ?: out[existing].curve)
        else out += Keyframe(tt, value, curve ?: LINEAR)
        return out.sortedBy { it.t }
    }

    fun remove(keys: List<Keyframe>, t: Float): List<Keyframe> =
        keys.filterNot { kotlin.math.abs(it.t - t) <= SNAP_SEC }

    fun at(keys: List<Keyframe>?, t: Float): Keyframe? = keys?.firstOrNull { kotlin.math.abs(it.t - t) <= SNAP_SEC }

    /** Guardado: [t, valor, curva, t, valor, curva…] (compatible con JSON simple). */
    fun encode(keys: List<Keyframe>): FloatArray {
        val out = FloatArray(keys.size * 3)
        keys.forEachIndexed { i, k -> out[i * 3] = k.t; out[i * 3 + 1] = k.value; out[i * 3 + 2] = k.curve.toFloat() }
        return out
    }

    /** Lectura tolerante: ignora restos incompletos, valores no finitos y curvas desconocidas. */
    fun decode(flat: FloatArray): List<Keyframe> {
        val out = ArrayList<Keyframe>()
        var i = 0
        while (i + 2 < flat.size) {
            val t = flat[i]; val v = flat[i + 1]; val c = flat[i + 2].toInt()
            if (t.isFinite() && v.isFinite() && t >= 0f) out += Keyframe(t, v, if (c in 0..3) c else LINEAR)
            i += 3
        }
        return out.sortedBy { it.t }
    }

    /** Recorte por la izquierda: el inicio avanzó [delta] s; las claves conservan su tiempo absoluto. */
    fun shift(all: Map<String, List<Keyframe>>, delta: Float): Map<String, List<Keyframe>> =
        if (delta == 0f) all else all.mapValues { (_, ks) -> ks.map { it.copy(t = Math.round((it.t - delta) * 100f) / 100f) } }

    /**
     * Dividir un bloque en el instante local [at]: la parte izquierda conserva las claves
     * anteriores y la derecha las posteriores (desplazadas), y ambas reciben una clave en el
     * corte con el valor exacto de ese momento, así la animación no salta.
     */
    fun split(all: Map<String, List<Keyframe>>, at: Float): Pair<Map<String, List<Keyframe>>, Map<String, List<Keyframe>>> {
        val left = LinkedHashMap<String, List<Keyframe>>()
        val right = LinkedHashMap<String, List<Keyframe>>()
        for ((k, ks) in all) {
            if (ks.isEmpty()) continue
            val v = valueAt(ks, at) ?: continue
            val (i, _) = segment(ks, at)
            val curve = ks[i].curve
            left[k] = ks.filter { it.t < at - 1e-4f } + Keyframe(at, v, curve)
            right[k] = listOf(Keyframe(0f, v, curve)) + ks.filter { it.t > at + 1e-4f }.map { it.copy(t = it.t - at) }
        }
        return left to right
    }
}
