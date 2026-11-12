package com.zeta.fondo.model

import kotlin.math.max
import kotlin.math.min

/**
 * Un bloque independiente de un efecto dentro de la línea de tiempo.
 * Los tiempos se guardan en segundos para que el usuario pueda colocarlos con precisión.
 */
data class EffectClip(
    val uid: Long,
    val effectId: String,
    val startSec: Float,
    val durationSec: Float,
    /** Pista visual de la línea de tiempo. -1 = proyecto antiguo/autocolocar. */
    val track: Int = -1,
    /** Multiplicador local del bloque; 1 = intensidad original (proyectos antiguos). */
    val strength: Float = 1f,
    /**
     * v40 · Ajustes propios de ESTE bloque (claves completas, p. ej. "blast.size",
     * "blast.originX"). Vacío = usa los ajustes del efecto. Así un duplicado puede tener
     * otra posición, tamaño, color, forma… sin cambiar el original.
     */
    val overrides: Map<String, Float> = emptyMap(),
    /**
     * v44 · Bloque oculto con el ojo: no se dibuja ni en la vista previa ni al exportar,
     * pero se conserva con todos sus ajustes. Proyectos antiguos (sin el campo) = visible.
     */
    val hidden: Boolean = false,
    /**
     * v44 · Fotogramas clave: clave completa ("rain.int") → claves en tiempo LOCAL del bloque.
     * Vacío = sin animación (proyectos antiguos).
     */
    val keys: Map<String, List<Keyframe>> = emptyMap()
) {
    val endSec: Float get() = startSec + durationSec
}

data class TimelineSample(
    val gate: Float,
    val localSec: Float,
    val clip: EffectClip?
)

object EffectTimeline {
    /**
     * Devuelve cuánto debe verse un efecto en el instante t del bucle.
     * Si no hay clips para ese efecto se mantiene el comportamiento heredado (gate = 1).
     */
    fun sample(clips: List<EffectClip>?, t: Float, loopSec: Float): TimelineSample {
        if (clips == null) return TimelineSample(1f, t.coerceIn(0f, .999999f) * loopSec, null)
        // v44: el efecto tiene bloques pero todos están ocultos (o fuera de «Solo»): no se ve.
        if (clips.isEmpty()) return TimelineSample(0f, t.coerceIn(0f, .999999f) * loopSec, null)
        val total = loopSec.coerceAtLeast(.1f)
        val sec = t.coerceIn(0f, .999999f) * total
        var bestGate = 0f
        var summedGate = 0f
        var best: EffectClip? = null
        var bestLocal = 0f
        for (clip in clips) {
            val start = clip.startSec.coerceIn(0f, total)
            val dur = clip.durationSec.coerceAtLeast(.04f).coerceAtMost((total - start).coerceAtLeast(.04f))
            val end = start + dur
            if (sec < start || sec > end) continue
            val full = start <= .001f && end >= total - .001f
            val local = (sec - start).coerceAtLeast(0f)
            val gate = if (full) 1f else {
                val fade = min(.10f, dur * .18f).coerceAtLeast(.015f)
                val fadeIn = smooth(local / fade)
                val fadeOut = smooth((end - sec) / fade)
                min(fadeIn, fadeOut)
            }
            // Cada duplicado tiene su propio nivel: una brisa puede coexistir con una ráfaga.
            val weightedGate = gate * clip.strength.coerceIn(0f, 2f)
            summedGate += weightedGate
            if (weightedGate > bestGate || (weightedGate == bestGate && best != null && start > best.startSec)) {
                bestGate = weightedGate
                best = clip
                bestLocal = local
            }
        }
        // Las copias solapadas del mismo efecto se acumulan. Así un viento continuo
        // puede convivir con ráfagas adicionales sin que una copia sustituya a otra.
        // El límite de 4x solo protege al shader de valores extremos; no limita
        // la cantidad de bloques que el usuario puede crear.
        return TimelineSample(summedGate.coerceIn(0f, 4f), bestLocal, best)
    }

    /** v44 · Un bloque visible en el instante pedido, con su propia visibilidad. */
    data class Active(val clip: EffectClip, val gate: Float, val localSec: Float)

    /**
     * v44 · Todos los bloques visibles en t, ordenados como [sample] elige el dominante
     * (más visible primero; a igualdad, el que empezó después). Sirve para dibujar varias
     * instancias del mismo efecto a la vez en vez de sumar su intensidad.
     */
    fun active(clips: List<EffectClip>?, t: Float, loopSec: Float): List<Active> {
        if (clips.isNullOrEmpty()) return emptyList()
        val total = loopSec.coerceAtLeast(.1f)
        val sec = t.coerceIn(0f, .999999f) * total
        val out = ArrayList<Active>(2)
        for (clip in clips) {
            val start = clip.startSec.coerceIn(0f, total)
            val dur = clip.durationSec.coerceAtLeast(.04f).coerceAtMost((total - start).coerceAtLeast(.04f))
            val end = start + dur
            if (sec < start || sec > end) continue
            val full = start <= .001f && end >= total - .001f
            val local = (sec - start).coerceAtLeast(0f)
            val gate = if (full) 1f else {
                val fade = min(.10f, dur * .18f).coerceAtLeast(.015f)
                min(smooth(local / fade), smooth((end - sec) / fade))
            }
            val w = gate * clip.strength.coerceIn(0f, 2f)
            if (w > 0f) out += Active(clip, w, local)
        }
        out.sortWith(compareByDescending<Active> { it.gate }.thenByDescending { it.clip.startSec })
        return out
    }

    private fun smooth(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    fun normalized(clip: EffectClip, loopSec: Float): EffectClip {
        val total = loopSec.coerceAtLeast(.1f)
        val start = clip.startSec.coerceIn(0f, (total - .04f).coerceAtLeast(0f))
        val duration = clip.durationSec.coerceAtLeast(.04f).coerceAtMost(max(.04f, total - start))
        return clip.copy(startSec = start, durationSec = duration,
            strength = clip.strength.coerceIn(0f, 2f))
    }
}

/**
 * v40 · Reglas de los ajustes propios de cada bloque y operaciones puras de la línea de
 * tiempo (dividir, sustituir, límites de recorte). Sin Android: se prueba en JVM.
 */
object ClipEdits {
    /** Duración mínima de un bloque al recortarlo (evita bloques invisibles por accidente). */
    const val MIN_DURATION = .10f

    /** v44.10 · Un efecto nuevo dura como mucho esto (los vídeos suelen ser de 6 s). */
    const val DEFAULT_SPAN = 6f

    /**
     * v44.10 · Tramo de un efecto nuevo: SIEMPRE desde el inicio del vídeo y hasta el final,
     * o hasta 6 s si el vídeo es más largo (un vídeo de 10 s → 0 a 6 s). Sin huecos.
     */
    fun defaultSpan(total: Float): Pair<Float, Float> = 0f to minOf(total, DEFAULT_SPAN).coerceAtLeast(MIN_DURATION)

    /** Sufijos que pertenecen siempre al efecto entero, nunca a un bloque. */
    private val effectWideSuffixes = setOf("on", "mode", "every", "ownedMask", "boxX0", "boxY0",
        "boxX1", "boxY1", "hasImage", "start", "dur",
        // v44.11: el color del efecto es del efecto entero (uGrade es uno por efecto)
        "gmode", "gsat", "gbri", "gcon",
        // v44.23: la zona con forma es del efecto entero (va por máscara en el shader)
        "zshape", "zx", "zy", "zw", "zh", "zrot", "zsoft")

    /** ¿La clave [key] puede ser propia del bloque de [effectId]? */
    fun isClipKey(effectId: String, key: String): Boolean {
        val prefix = "$effectId."
        if (!key.startsWith(prefix)) return false
        val suffix = key.substring(prefix.length)
        return suffix.isNotEmpty() && suffix !in effectWideSuffixes && !suffix.startsWith("copy.")
    }

    /**
     * Bloque cuyos ajustes se editan: con varios bloques (o si ya tiene ajustes propios)
     * se edita SOLO el seleccionado; con uno solo, el efecto entero (null).
     */
    fun target(clipsOfEffect: List<EffectClip>, selected: Long?): EffectClip? {
        if (clipsOfEffect.isEmpty()) return null
        if (clipsOfEffect.size < 2 && clipsOfEffect.none { it.overrides.isNotEmpty() }) return null
        return clipsOfEffect.firstOrNull { it.uid == selected } ?: clipsOfEffect.first()
    }

    /** Valor efectivo de [key] para [clip] (sus ajustes propios o los del efecto). */
    fun value(settings: Map<String, Float>, clip: EffectClip?, key: String): Float? =
        clip?.overrides?.get(key) ?: settings[key]

    /** Ajustes que ve el motor para un bloque: los del efecto + los propios del bloque. */
    fun effectiveSettings(settings: Map<String, Float>, clip: EffectClip): Map<String, Float> {
        if (clip.overrides.isEmpty()) return settings
        return settings + clip.overrides.filterKeys { isClipKey(clip.effectId, it) }
    }

    /**
     * Divide [clip] en el segundo [atSec]. Ambas partes conservan pista, intensidad y
     * ajustes propios. null si el corte queda demasiado cerca de un extremo.
     */
    fun split(clip: EffectClip, atSec: Float, newUid: Long): Pair<EffectClip, EffectClip>? {
        val start = clip.startSec
        val end = clip.endSec
        if (atSec < start + MIN_DURATION || atSec > end - MIN_DURATION) return null
        val left = clip.copy(durationSec = atSec - start)
        val right = clip.copy(uid = newUid, startSec = atSec, durationSec = end - atSec)
        return left to right
    }

    /** Claves que tienen el mismo significado en cualquier efecto (se conservan al sustituir). */
    private val portableSuffixes = setOf("origin", "originX", "originY", "direction", "behind",
        "tintOn", "tintR", "tintG", "tintB")

    /** Sustituye el efecto del bloque sin moverlo en el tiempo ni cambiar su pista. */
    fun replaceEffect(clip: EffectClip, newEffectId: String): EffectClip {
        if (newEffectId == clip.effectId) return clip
        val moved = clip.overrides.mapNotNull { (k, v) ->
            if (!k.startsWith("${clip.effectId}.")) return@mapNotNull null
            val suffix = k.substringAfter("${clip.effectId}.")
            if (suffix in portableSuffixes) "$newEffectId.$suffix" to v else null
        }.toMap()
        // v44: las claves de parámetros comunes (posición, dirección…) también pasan.
        val keys = clip.keys.mapNotNull { (k, v) ->
            val suffix = k.substringAfter("${clip.effectId}.")
            if (k.startsWith("${clip.effectId}.") && suffix in portableSuffixes) "$newEffectId.$suffix" to v else null
        }.toMap()
        return clip.copy(effectId = newEffectId, overrides = moved, keys = keys)
    }

    /**
     * Recorte desde el extremo izquierdo: el final queda fijo.
     * Devuelve (inicio, duración) válidos para un vídeo de [total] segundos.
     */
    fun trimStart(anchorStart: Float, anchorEnd: Float, deltaSec: Float): Pair<Float, Float> {
        val maxStart = (anchorEnd - MIN_DURATION).coerceAtLeast(0f)
        val start = (anchorStart + deltaSec).coerceIn(0f, maxStart)
        return start to (anchorEnd - start).coerceAtLeast(MIN_DURATION)
    }

    /** Recorte desde el extremo derecho: el inicio queda fijo. */
    fun trimEnd(anchorStart: Float, anchorEnd: Float, deltaSec: Float, total: Float): Pair<Float, Float> {
        val end = (anchorEnd + deltaSec).coerceIn(anchorStart + MIN_DURATION, total.coerceAtLeast(anchorStart + MIN_DURATION))
        return anchorStart to (end - anchorStart)
    }

    /** Mover conservando la duración. */
    fun move(start: Float, duration: Float, deltaSec: Float, total: Float): Float =
        (start + deltaSec).coerceIn(0f, (total - duration).coerceAtLeast(0f))
}
