package com.zeta.fondo.model

/**
 * v44 · Sonidos como BLOQUES de la línea de tiempo (código puro, pruebas JVM).
 *
 * Un sonido es un [EffectClip] con id "snd_<sonido>": así hereda mover, recortar, dividir,
 * duplicar, ocultar (= silenciar), Solo, tiempo exacto y fotogramas clave del volumen.
 * Ajustes propios de cada bloque: vol (0–1,5), pan (−1..1), fadein y fadeout (segundos).
 * El motor visual los ignora: no son efectos del shader.
 */
object SoundClips {
    const val PREFIX = "snd_"
    /** Sonidos importados por el usuario: "snd_user_<n>" (PCM en la carpeta del proyecto). */
    const val USER = "user_"

    fun isSound(effectId: String): Boolean = effectId.startsWith(PREFIX)

    /** v44.17 · Duración del bloque de música: la de la canción, sin pasar del vídeo. */
    fun musicSpan(seconds: Float, total: Float): Float =
        (if (seconds > 0f) minOf(seconds, total) else total).coerceAtLeast(ClipEdits.MIN_DURATION)
    fun soundOf(effectId: String): String = effectId.removePrefix(PREFIX)
    fun clipId(sound: String): String = PREFIX + sound

    val defaults = mapOf("vol" to .8f, "pan" to 0f, "fadein" to .3f, "fadeout" to .3f)

    data class Track(
        val uid: Long, val sound: String, val startSec: Float, val durationSec: Float,
        val vol: Float, val pan: Float, val fadeIn: Float, val fadeOut: Float,
        val volKeys: List<Keyframe> = emptyList(), val panKeys: List<Keyframe> = emptyList()
    ) {
        val endSec: Float get() = startSec + durationSec
    }

    private fun value(c: EffectClip, s: Map<String, Float>, k: String): Float =
        c.overrides["${c.effectId}.$k"] ?: s["${c.effectId}.$k"] ?: defaults.getValue(k)

    /**
     * Pistas que suenan. Bloques ocultos = silenciados. Si «Solo» está en un SONIDO, solo suena
     * ese (la música también se calla: [musicMuted]).
     */
    fun tracks(clips: List<EffectClip>, s: Map<String, Float>, soloUid: Long? = null): List<Track> {
        val sounds = clips.filter { isSound(it.effectId) && !it.hidden }
        val soloSound = soloUid?.let { id -> sounds.firstOrNull { it.uid == id } }
        return (if (soloSound != null) listOf(soloSound) else sounds).map { c ->
            Track(c.uid, soundOf(c.effectId), c.startSec, c.durationSec,
                value(c, s, "vol").coerceIn(0f, 1.5f), value(c, s, "pan").coerceIn(-1f, 1f),
                value(c, s, "fadein").coerceAtLeast(0f), value(c, s, "fadeout").coerceAtLeast(0f),
                c.keys["${c.effectId}.vol"].orEmpty(), c.keys["${c.effectId}.pan"].orEmpty())
        }
    }

    fun musicMuted(clips: List<EffectClip>, soloUid: Long?): Boolean =
        soloUid != null && clips.any { it.uid == soloUid && isSound(it.effectId) }

    /** Volumen del bloque en el segundo [sec] del vídeo (fundidos + claves). 0 fuera del bloque. */
    fun gain(t: Track, sec: Float): Float {
        if (sec < t.startSec || sec > t.endSec) return 0f
        val local = sec - t.startSec
        val fi = t.fadeIn.coerceAtMost(t.durationSec / 2f)
        val fo = t.fadeOut.coerceAtMost(t.durationSec / 2f)
        var g = Keyframes.valueAt(t.volKeys, local) ?: t.vol
        if (fi > 0f && local < fi) g *= local / fi
        if (fo > 0f && t.endSec - sec < fo) g *= (t.endSec - sec) / fo
        return g.coerceIn(0f, 1.5f)
    }

    fun pan(t: Track, sec: Float): Float = (Keyframes.valueAt(t.panKeys, sec - t.startSec) ?: t.pan).coerceIn(-1f, 1f)

    /** Panorama de igual potencia: (izquierda, derecha). pan 0 = centro (≈0,707 cada lado). */
    fun panGains(pan: Float): Pair<Float, Float> {
        val a = (pan.coerceIn(-1f, 1f) + 1f) * (Math.PI / 4).toFloat()
        return kotlin.math.cos(a) to kotlin.math.sin(a)
    }

    /** Proyectos antiguos: cada ambiente del bucle pasa a un bloque que cubre todo el vídeo. */
    fun fromLegacy(ambient: Map<String, Float>, loopSec: Float, firstUid: Long): List<EffectClip> {
        var uid = firstUid
        return ambient.filter { it.value > .01f }.map { (id, vol) ->
            EffectClip(uid++, clipId(id), 0f, loopSec, track = -1, overrides = mapOf("${clipId(id)}.vol" to vol, "${clipId(id)}.fadein" to 0f, "${clipId(id)}.fadeout" to 0f))
        }
    }
}
