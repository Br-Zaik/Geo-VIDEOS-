package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EffectTimeline
import com.zeta.fondo.model.EventMode

/**
 * v40 · Valores de los uniforms que cambian cuadro a cuadro según la línea de tiempo.
 * Código puro (sin OpenGL): lo usan la vista previa y la exportación a través de
 * FondoGl, y las pruebas JVM, así lo exportado coincide con lo previsualizado.
 *
 * Para cada efecto con bloques:
 *  - se elige el bloque dominante en el instante t (EffectTimeline.sample);
 *  - si ese bloque tiene ajustes propios (posición, tamaño, color, forma…), sus
 *    diferencias se aplican sobre los valores del efecto;
 *  - la intensidad se multiplica por la visibilidad del bloque (fundido de entrada y salida).
 * Lo que se tocó en el cuadro anterior y ya no se toca se restaura al valor base.
 */
class FrameUniforms {
    interface Sink {
        fun putFloats(name: String, values: FloatArray)
        fun putInt(name: String, value: Int)
    }

    private var touchedFloats: Set<String> = emptySet()
    private var touchedInts: Set<String> = emptySet()

    /** Llamar cuando se acaba de escribir el bloque base completo (nuevos RenderParams). */
    fun reset() {
        touchedFloats = emptySet()
        touchedInts = emptySet()
    }

    /** Escribe el bloque completo con los valores base del efecto. */
    fun writeBase(p: RenderParams, sink: Sink) {
        p.floatUniforms.forEach { (name, v) -> sink.putFloats(name, v) }
        p.intUniforms.forEach { (name, v) -> sink.putInt(name, v) }
        reset()
    }

    /** Calcula y escribe los cambios del instante [t] (0..1 del bucle). */
    fun apply(p: RenderParams, t: Float, sink: Sink) {
        val floats = compute(p, t)
        val ints = HashMap<String, Int>()
        for (clips in p.timeline.values) {
            val clip = EffectTimeline.sample(clips, t, p.loopSec).clip ?: continue
            p.clipDiffs[clip.uid]?.ints?.let { ints.putAll(it) }
        }
        ints.putAll(secondInstanceInts(p, t))
        for (name in touchedFloats) if (name !in floats) p.floatUniforms[name]?.let { sink.putFloats(name, it) }
        for (name in touchedInts) if (name !in ints) p.intUniforms[name]?.let { sink.putInt(name, it) }
        floats.forEach { (name, v) -> sink.putFloats(name, v) }
        ints.forEach { (name, v) -> sink.putInt(name, v) }
        touchedFloats = floats.keys
        touchedInts = ints.keys
    }

    companion object {
        /**
         * v44 · Efectos con SEGUNDA instancia propia en el shader (uniforms con sufijo 2):
         * dos explosiones, salpicaduras, cortes o humos a la vez, cada uno con su sitio,
         * tamaño, color y fase. Antes solo se veía uno con la intensidad sumada.
         */
        val secondInstance: Map<String, List<String>> = mapOf(
            "blast" to listOf("uFxBlast", "uBlastShape", "uBlastMore", "uMotionBlast", "uTintBlast"),
            "blood" to listOf("uFxBlood", "uBloodStyle", "uBloodShape", "uMotionBlood", "uTintBlood"),
            "slash" to listOf("uFxSlash", "uSlashStyle", "uSlashShape", "uMotionSlash", "uTintSlash"),
            "smoke" to listOf("uFxSmoke", "uSmokeShape", "uSmokeControl", "uMotionSmoke", "uTintSmoke"))

        /** v44 · Partículas que usan su ranura «Copia» como 2.ª instancia (si el usuario no la usa ya). */
        val copyInstance = setOf("ash", "leaves", "embers", "dust", "bubbles", "electric",
            "focus", "shock", "flash", "tone", "grain", "ink")

        private val phaseIndex = mapOf("blast" to 0, "blood" to 1, "slash" to 2)

        /** Valores de los uniforms de [name] para el bloque [clip] (efecto + ajustes propios + claves). */
        private fun clipArray(p: RenderParams, clip: EffectClip, name: String, local: Float = 0f): FloatArray? {
            val a = p.floatUniforms[name]?.copyOf() ?: return null
            p.clipDiffs[clip.uid]?.floats?.get(name)?.let { patch ->
                for (i in patch.changed.indices) if (i < a.size && patch.changed[i]) a[i] = patch.values[i]
            }
            p.keyTracks[clip.uid]?.forEach { it.apply(name, a, local) }
            return a
        }

        private fun copyFree(p: RenderParams, id: String): Boolean =
            (p.floatUniforms["uFx${RenderParams.uniformName(id)}Copy"]?.get(0) ?: 1f) <= 0f

        /** Máscaras de las segundas instancias. */
        fun secondInstanceInts(p: RenderParams, t: Float): Map<String, Int> {
            val out = HashMap<String, Int>()
            for ((id, clips) in p.timeline) {
                val isSecond = id in secondInstance
                if (!isSecond && !(id in copyInstance && copyFree(p, id))) continue
                val act = EffectTimeline.active(clips, t, p.loopSec)
                if (act.size < 2) continue
                val n = RenderParams.uniformName(id)
                val c2 = act[1].clip
                val mask = p.clipDiffs[c2.uid]?.ints?.get("uMask$n") ?: p.intUniforms["uMask$n"] ?: continue
                out[if (isSecond) "uMask${n}2" else "uMask${n}Copy"] = mask
            }
            return out
        }

        /** Uniforms float que cambian en [t] (con el bloque dominante y su visibilidad). */
        fun compute(p: RenderParams, t: Float): Map<String, FloatArray> {
            val out = LinkedHashMap<String, FloatArray>()
            if (p.timeline.isEmpty()) return out
            fun arr(name: String): FloatArray? = out[name] ?: p.floatUniforms[name]?.copyOf()?.also { out[name] = it }
            fun gate0(name: String, g: Float) { arr(name)?.let { it[0] *= g } }

            var boltLocal = -1f
            var boltSeed = 0f
            var impactLocal = -1f
            val phaseB = floatArrayOf(-1f, -1f, -1f, -1f)
            var anyPhaseB = false
            for ((id, clips) in p.timeline) {
                val sample = EffectTimeline.sample(clips, t, p.loopSec)
                // v44: con dos bloques solapados de un efecto con 2.ª instancia, cada uno se
                // dibuja con SU visibilidad (no la suma) y en su sitio.
                val multi = (id in secondInstance || (id in copyInstance && copyFree(p, id)))
                val act = if (multi) EffectTimeline.active(clips, t, p.loopSec) else emptyList()
                val gate = if (act.size >= 2) act[0].gate else sample.gate
                if (act.size >= 2) {
                    val c2 = act[1]
                    val n = RenderParams.uniformName(id)
                    if (id in secondInstance) {
                        for (name in secondInstance.getValue(id)) {
                            val a = clipArray(p, c2.clip, name, c2.localSec) ?: continue
                            if (name.startsWith("uFx")) a[0] *= c2.gate
                            out[name + "2"] = a
                        }
                        phaseIndex[id]?.let { k ->
                            val cfg = p.events[id]
                            val mode = cfg?.get(0)?.toInt() ?: EventMode.LOOP
                            phaseB[k] = if (mode == EventMode.LOOP || cfg == null) -1f
                                else EventMode.phase(mode, c2.localSec, c2.clip.durationSec, cfg[1])
                            anyPhaseB = true
                        }
                    } else {
                        clipArray(p, c2.clip, "uFx$n", c2.localSec)?.let { a -> a[0] *= c2.gate; out["uFx${n}Copy"] = a }
                        clipArray(p, c2.clip, "uMotion$n", c2.localSec)?.let { out["uMotion${n}Copy"] = it }
                    }
                }
                // 1) Ajustes propios del bloque dominante.
                sample.clip?.let { c ->
                    p.clipDiffs[c.uid]?.floats?.forEach { (name, patch) ->
                        val a = arr(name) ?: return@forEach
                        for (i in patch.changed.indices) if (i < a.size && patch.changed[i]) a[i] = patch.values[i]
                    }
                    // v44: fotogramas clave del bloque en su instante local.
                    p.keyTracks[c.uid]?.forEach { track ->
                        for (name in track.arrays.keys) { val a = arr(name) ?: continue; track.apply(name, a, sample.localSec) }
                    }
                }
                // 2) Visibilidad del bloque.
                when (id) {
                    "flow" -> gate0("uFlow3", gate)
                    "drift" -> arr("uDrift")?.let { it[0] *= gate; it[1] *= gate }
                    "ripple" -> gate0("uRipple", gate)
                    "glint" -> gate0("uGlint", gate)
                    "sway" -> gate0("uSway", gate)
                    "heat" -> gate0("uHeat", gate)
                    "rain" -> gate0("uRain", gate)
                    "snow" -> gate0("uSnow", gate)
                    "sakura" -> gate0("uSakura", gate)
                    "stars" -> gate0("uStars", gate)
                    "flies" -> gate0("uFlies", gate)
                    "fog" -> gate0("uFog", gate)
                    "rays" -> gate0("uRays", gate)
                    "energy" -> gate0("uEnergy", gate)
                    "speed" -> gate0("uSpeed", gate)
                    "cam" -> arr("uCam")?.let { it[0] *= gate; it[1] *= gate }
                    "parallax" -> gate0("uParallax", gate)
                    "mesh" -> gate0("uMesh", gate)
                    "natural" -> gate0("uNatural", gate)
                    "zonemotion" -> gate0("uZoneMotion", gate)
                    "sky" -> gate0("uStudioSky", gate)
                    "water" -> gate0("uStudioWater", gate)
                    "dispersion" -> gate0("uStudioDispersion", gate)
                    "bolt" -> {
                        gate0("uBolt", gate)
                        if (sample.clip != null && gate > 0f) {
                            boltLocal = sample.localSec
                            boltSeed = (sample.clip.uid % 997L).toFloat()
                        }
                    }
                    // La posición y duración las gobierna cada bloque, no un único start/dur global.
                    "gust" -> arr("uGust")?.let { it[0] *= gate; it[1] = 0f; it[2] = 1f }
                    "tornado" -> gate0("uTornado", gate)
                    "appear" -> arr("uAppear")?.let {
                        val c = sample.clip
                        val baseTransition = it[2]
                        if (c != null) {
                            it[1] = (c.startSec / p.loopSec).coerceIn(0f, .999f)
                            it[2] = minOf(c.durationSec / p.loopSec, baseTransition).coerceAtLeast(.02f)
                        }
                        it[0] *= gate
                    }
                    "clouds3" -> gate0("uClouds3", gate)
                    "shore" -> gate0("uShore", gate)
                    "sunpath" -> gate0("uSunpath", gate)
                    "impact" -> arr("uImpact")?.let {
                        it[0] *= gate; it[3] = 1f
                        if (sample.clip != null && gate > 0f) impactLocal = sample.localSec
                    }
                    else -> gate0("uFx${RenderParams.uniformName(id)}", gate)
                }
            }
            if (anyPhaseB) out["uEventPhaseB"] = phaseB
            if (p.timeline.containsKey("bolt") || p.timeline.containsKey("impact")) {
                out["uLoopInfo"] = floatArrayOf(p.loopSec, boltLocal, impactLocal, boltSeed)
            }
            // Eventos en modo Una vez / Repetir: fase calculada dentro de su propio bloque.
            val ph = FloatArray(6) { -1f }
            val order = arrayOf("blast", "blood", "slash", "reveal", "flash", "shock")
            for (i in order.indices) {
                val id = order[i]
                val clips = p.timeline[id] ?: continue
                val cfg = p.events[id] ?: continue
                val mode = cfg[0].toInt()
                if (mode == EventMode.LOOP) continue
                val sample = EffectTimeline.sample(clips, t, p.loopSec)
                val clip = sample.clip
                ph[i] = if (clip == null) 0f else EventMode.phase(mode, sample.localSec, clip.durationSec, cfg[1])
            }
            if (ph.any { it >= 0f }) {
                out["uEventPhase"] = floatArrayOf(ph[0], ph[1], ph[2], ph[3])
                out["uEventPhase2"] = floatArrayOf(ph[4], ph[5], -1f, -1f)
            }
            return out
        }
    }
}
