package com.zeta.fondo.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v44.23 · Zonas con forma (círculo o rectángulo) que se arrastran, agrandan y giran con
 * tiradores, en vez de pintarlas con el dedo (como las formas de Alight Motion).
 *
 * La forma vive en la zona PROPIA del efecto (su máscara 3..11): el shader suma la forma a lo
 * pintado, así se puede combinar (forma + retoques con el pincel). Como todos los efectos leen
 * sus zonas por la misma función, funciona con TODOS sin tocarlos uno a uno.
 *
 * Ajustes del efecto (sufijos): zshape (0 nada, 1 círculo, 2 rectángulo), zx/zy (centro, 0..1),
 * zw/zh (medio ancho y medio alto en fracción del ANCHO de la imagen: zw = zh es un círculo
 * redondo en cualquier formato), zrot (grados) y zsoft (borde suave 0..1).
 * Código puro: lo usan la interfaz, RenderParams y las pruebas JVM.
 */
object ZoneShapes {
    const val NONE = 0
    const val CIRCLE = 1
    const val RECT = 2

    /** Sufijos de la forma (son del efecto entero; no se animan por bloque). */
    val suffixes = setOf("zshape", "zx", "zy", "zw", "zh", "zrot", "zsoft")

    /** Primera máscara propia de un efecto (0 Proteger, 1 azul, 2 verde son comunes). */
    const val FIRST_OWN = 3
    const val SLOTS = 12

    data class Shape(val type: Int, val cx: Float, val cy: Float, val hw: Float, val hh: Float,
                     val rotDeg: Float, val soft: Float)

    fun of(s: Map<String, Float>, id: String): Shape? {
        val type = (s["$id.zshape"] ?: 0f).toInt()
        if (type != CIRCLE && type != RECT) return null
        return Shape(type,
            (s["$id.zx"] ?: .5f).finite(.5f).coerceIn(-.5f, 1.5f),
            (s["$id.zy"] ?: .5f).finite(.5f).coerceIn(-.5f, 1.5f),
            (s["$id.zw"] ?: .22f).finite(.22f).coerceIn(.01f, 3f),
            (s["$id.zh"] ?: .22f).finite(.22f).coerceIn(.01f, 3f),
            (s["$id.zrot"] ?: 0f).finite(0f),
            (s["$id.zsoft"] ?: .35f).finite(.35f).coerceIn(0f, 1f))
    }

    private fun Float.finite(d: Float) = if (isFinite()) this else d

    /** Ajustes iniciales de una forma nueva, centrada y del tamaño que cabe en la imagen. */
    fun initial(id: String, type: Int, aspect: Float): Map<String, Float> {
        val a = aspect.coerceIn(.2f, 5f)
        val half = .3f * min(1f, 1f / a)    // 30 % del lado corto
        return mapOf("$id.zshape" to type.toFloat(), "$id.zx" to .5f, "$id.zy" to .5f,
            "$id.zw" to half, "$id.zh" to half, "$id.zrot" to 0f, "$id.zsoft" to .35f)
    }

    /**
     * Valor de la forma (0..1) en el punto (u, v) de una imagen con [aspect] = ancho/alto.
     * MISMA fórmula que el shader (zoneShape).
     */
    fun value(sh: Shape, u: Float, v: Float, aspect: Float): Float {
        val dx = u - sh.cx
        val dy = (v - sh.cy) / aspect.coerceAtLeast(1e-3f)
        val r = Math.toRadians(sh.rotDeg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val qx = (c * dx + s * dy) / max(sh.hw, 1e-4f)
        val qy = (-s * dx + c * dy) / max(sh.hh, 1e-4f)
        val d = if (sh.type == CIRCLE) sqrt(qx * qx + qy * qy) else max(abs(qx), abs(qy))
        val e0 = 1f - sh.soft * .95f - 1e-3f
        val t = ((d - e0) / (1f - e0)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    /** Esquinas (o caja del círculo) en coordenadas 0..1 de la imagen: para los tiradores. */
    fun corners(sh: Shape, aspect: Float): List<Pair<Float, Float>> {
        val r = Math.toRadians(sh.rotDeg.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val a = aspect.coerceAtLeast(1e-3f)
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (x, y) ->
            val lx = x * sh.hw; val ly = y * sh.hh
            (sh.cx + c * lx - s * ly) to (sh.cy + (s * lx + c * ly) * a)
        }
    }

    /** ¿(u, v) está dentro de la forma? (para empezar a arrastrar). */
    fun inside(sh: Shape, u: Float, v: Float, aspect: Float): Boolean = value(sh.copy(soft = 0f), u, v, aspect) > .5f

    /** Rectángulo que ocupa la forma [x0, y0, x1, y1] (0..1), recortado a la imagen. */
    fun bounds(sh: Shape, aspect: Float): FloatArray {
        val cs = corners(sh, aspect)
        return floatArrayOf(cs.minOf { it.first }.coerceIn(0f, 1f), cs.minOf { it.second }.coerceIn(0f, 1f),
            cs.maxOf { it.first }.coerceIn(0f, 1f), cs.maxOf { it.second }.coerceIn(0f, 1f))
    }

    /**
     * Uniforms del shader: por cada una de las 12 máscaras, A = (cx, cy, hw, hh) y
     * B = (giro en radianes, borde suave, tipo, 0). Primero las 12 A y después las 12 B.
     * Solo cuentan los efectos encendidos con forma y con su zona propia.
     */
    fun uniforms(s: Map<String, Float>): FloatArray {
        val out = FloatArray(SLOTS * 8)
        val ids = s.keys.asSequence().filter { it.endsWith(".zshape") }.map { it.substringBefore('.') }.toList()
        for (id in ids) {
            val sh = of(s, id) ?: continue
            if ((s["$id.on"] ?: 0f) <= .5f) continue
            val slot = (s["$id.ownedMask"] ?: -1f).toInt()
            if (slot !in FIRST_OWN until SLOTS) continue
            if ((s["$id.mask"] ?: -2f).toInt() != slot) continue
            val a = slot * 4
            out[a] = sh.cx; out[a + 1] = sh.cy; out[a + 2] = sh.hw; out[a + 3] = sh.hh
            val b = SLOTS * 4 + slot * 4
            out[b] = Math.toRadians(sh.rotDeg.toDouble()).toFloat(); out[b + 1] = sh.soft; out[b + 2] = sh.type.toFloat()
        }
        return out
    }

    /** Forma de la máscara [slot] según [uniforms] (la vista previa la dibuja en morado al pintar). */
    fun hasShape(u: FloatArray, slot: Int): Boolean = slot in 0 until SLOTS && u.size >= SLOTS * 8 && u[SLOTS * 4 + slot * 4 + 2] > .5f
}
