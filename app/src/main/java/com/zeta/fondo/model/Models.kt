package com.zeta.fondo.model

import org.json.JSONObject

/** Punto en coordenadas normalizadas de la imagen (0..1, origen arriba-izquierda). */
data class PointN(val x: Float, val y: Float)

/** Flecha de movimiento en coordenadas normalizadas. */
data class Arrow(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Capas de máscara: 0 = congelar (rojo), 1 = zona 1 (azul), 2 = zona 2 (verde). */
object Layer {
    const val FREEZE = 0
    const val ZONE1 = 1
    const val ZONE2 = 2
}

/**
 * Trazo de pincel sobre una capa de máscara.
 * radius: radio en fracción de la ALTURA de la imagen.
 * feather: suavizado 0..1. fill: rellena/vacía toda la capa.
 */
class MaskStroke(
    val layer: Int,
    val radius: Float,
    val feather: Float,
    val erase: Boolean,
    val fill: Boolean = false,
    /**
     * v46: máscara de imagen (PNG con alfa, guardado en la carpeta del proyecto) en lugar
     * de un trazo: p. ej. el recorte de personaje por IA. Se deshace y se repasa con el
     * pincel como cualquier trazo.
     */
    val image: String? = null
) {
    val points = ArrayList<PointN>()
}

enum class Tool(val label: String, val hint: String) {
    ARROW("➡️ Flechas", "Arrastra para dibujar una flecha: el agua, las nubes o el pelo se moverán hacia donde apunta. Más larga = más rápido. Usa dos dedos para hacer zoom."),
    ANCHOR("📍 Anclas", "Toca para poner un punto fijo que no se mueve. Rodea con anclas la zona que animas para que el movimiento no se escape."),
    FREEZE("🧊 Congelar", "Pinta en rojo lo que NUNCA debe moverse: personajes, casas, rocas y líneas importantes."),
    ZONE1("💧 Zona 1", "Pinta en azul el agua o el cielo. Ahí actúan las ondas, los brillos, la deriva de nubes y los efectos marcados como 'solo en Zona 1'."),
    ZONE2("🌿 Zona 2", "Pinta en verde árboles, hierba o fuego. Ahí actúan el viento y el calor."),
    ERASE_GUIDE("🧽 Quitar guía", "Toca una flecha o un ancla para borrarla."),
    MASK("✎ Máscara", "Pinta la máscara elegida. Los efectos pueden usarla para limitarse a esa parte del anime."),
    ORIGIN("◎ Origen FX", "Arrastra directamente sobre el dibujo el punto desde donde nace el efecto seleccionado."),
    PAN("✋ Mover", "Arrastra con un dedo para moverte y pellizca para acercar o alejar.");

    val isBrush: Boolean get() = this == FREEZE || this == ZONE1 || this == ZONE2 || this == MASK
    val layer: Int
        get() = when (this) {
            FREEZE -> Layer.FREEZE
            ZONE1 -> Layer.ZONE1
            ZONE2 -> Layer.ZONE2
            else -> -1
        }
}

enum class EditorTab(val label: String) {
    TOOLS("↗ Movimiento"),
    MASKS("◩ Máscaras"),
    PRESETS("✦ Presets"),
    EFFECTS("✧ Efectos"),
    MANGA("漫 Manga"),
    CAMERA("▣ Cámara"),
    AUDIO("♫ Audio"),
    CANVAS("▣ Lienzo"),
    EXPORT("⇧ Exportar")
}

/** Ajustes de exportación. */
data class ExportSettings(
    val format: Int = 0,        // 0 = MP4, 1 = secuencia PNG
    val res: Int = 3,           // índice en ExportPlanner.resLabels (3 = 1080p)
    val framing: Int = 0,       // 0 completo, 1 = 9:16, 2 = 16:9, 3 = 1:1, 4 = 3:4, 5 = 4:3
    val cropPos: Float = 0.5f,  // posición del recorte 0..1
    val fps: Int = 30,
    val loopSec: Int = 6,
    val soundVolume: Float = 1f,
    val audioName: String = "",
    val audioRate: Int = 44100,
    val audioChannels: Int = 2,
    val audioFrames: Long = 0L,
    val audioStartFrame: Long = 0L, // inicio del fragmento de música en el archivo PCM
    val audioEnabled: Boolean = true,
    val audioLoop: Boolean = true,
    val repeats: Int = 1,
    val quality: Int = 1,       // 0 alta, 1 máxima, 2 extrema
    val overlayOnly: Boolean = false,
    val codec: Int = 0,          // AVC, HEVC, AV1 (si hay encoder real)
    val customW: Int = 1920,
    val customH: Int = 1080,
    // Encuadre libre: centro del recorte (-1 = usar cropPos) y acercamiento dentro de la imagen.
    val cropCx: Float = -1f,
    val cropCy: Float = -1f,
    val cropZoom: Float = 1f,
    // Sonidos de ambiente generados por código: id -> volumen 0..1.
    val ambient: Map<String, Float> = emptyMap(),
    // Fundido de entrada/salida de la música en cada bucle.
    val audioFade: Boolean = true,
    // v44 · bloques de sonido (derivados de la línea de tiempo; NO se guardan aquí).
    val sounds: List<SoundClips.Track> = emptyList(),
    // v44 · carpeta del proyecto con los sonidos importados por el usuario (no se guarda).
    val soundDir: String = "",
    // v44 · «Solo» en un sonido: la música se calla mientras se escucha (no se guarda).
    val musicMuted: Boolean = false
) {
    val hasSounds: Boolean get() = sounds.isNotEmpty()
    val hasAmbient: Boolean get() = ambient.values.any { it > 0.01f }

    fun toJson(): JSONObject = JSONObject()
        .put("format", format)
        .put("res", res)
        .put("framing", framing)
        .put("cropPos", cropPos.jsonSafe(.5f))
        .put("fps", fps)
        .put("loopSec", loopSec)
        .put("soundVolume", soundVolume.jsonSafe(1f))
        .put("audioName", audioName)
        .put("audioRate", audioRate)
        .put("audioChannels", audioChannels)
        .put("audioFrames", audioFrames)
        .put("audioStartFrame", audioStartFrame)
        .put("audioEnabled", audioEnabled)
        .put("audioLoop", audioLoop)
        .put("repeats", repeats)
        .put("quality", quality)
        .put("overlayOnly", overlayOnly)
        .put("codec", codec)
        .put("customW", customW)
        .put("customH", customH)
        .put("cropCx", cropCx.jsonSafe(.5f))
        .put("cropCy", cropCy.jsonSafe(.5f))
        .put("cropZoom", cropZoom.jsonSafe(1f))
        .put("audioFade", audioFade)
        .put("ambient", JSONObject().also { o -> ambient.forEach { (k, v) -> if (v.isFinite()) o.put(k, v.toDouble()) } })

    companion object {
        fun fromJson(j: JSONObject?): ExportSettings {
            if (j == null) return ExportSettings()
            val d = ExportSettings()
            return ExportSettings(
                format = j.optInt("format", d.format).coerceIn(0, 1),
                res = j.optInt("res", d.res).coerceIn(0, 6),
                framing = j.optInt("framing", d.framing).coerceIn(0, 5),
                cropPos = j.optDouble("cropPos", d.cropPos.toDouble()).toFloat().coerceIn(0f, 1f),
                fps = j.optInt("fps", d.fps).let { if (it == 24 || it == 30 || it == 60) it else 30 },
                loopSec = j.optInt("loopSec", d.loopSec).coerceIn(2, 60),
                soundVolume = j.optDouble("soundVolume", d.soundVolume.toDouble()).toFloat().coerceIn(0f, 1f),
                audioName = j.optString("audioName", ""),
                audioRate = j.optInt("audioRate", 44100).coerceIn(8000, 96000),
                audioChannels = j.optInt("audioChannels", 2).coerceIn(1, 2),
                audioFrames = j.optLong("audioFrames", 0).coerceAtLeast(0),
                audioStartFrame = j.optLong("audioStartFrame", 0).coerceAtLeast(0),
                audioEnabled = j.optBoolean("audioEnabled", true),
                audioLoop = j.optBoolean("audioLoop", true),
                repeats = j.optInt("repeats", d.repeats).coerceIn(1, 10),
                quality = j.optInt("quality", d.quality).coerceIn(0, 2),
                overlayOnly = j.optBoolean("overlayOnly", d.overlayOnly),
                codec = j.optInt("codec", d.codec).coerceIn(0, 2),
                customW = j.optInt("customW", d.customW).coerceIn(128, 7680),
                customH = j.optInt("customH", d.customH).coerceIn(128, 7680),
                cropCx = j.optDouble("cropCx", -1.0).toFloat().let { if (it < 0f) -1f else it.coerceIn(0f, 1f) },
                cropCy = j.optDouble("cropCy", -1.0).toFloat().let { if (it < 0f) -1f else it.coerceIn(0f, 1f) },
                cropZoom = j.optDouble("cropZoom", 1.0).toFloat().coerceIn(1f, 6f),
                audioFade = j.optBoolean("audioFade", true),
                ambient = j.optJSONObject("ambient")?.let { o ->
                    val m = LinkedHashMap<String, Float>()
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        m[k] = o.optDouble(k, 0.0).toFloat().coerceIn(0f, 1f)
                    }
                    m
                } ?: emptyMap()
            )
        }
    }
}
