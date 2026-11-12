package com.zeta.fondo.model

/**
 * v44 · Biblioteca de sonidos por escenas (código puro).
 *
 * Origen de cada sonido:
 *  - SYNTH: sintetizado por el código de la app (engine/Ambient.kt). Sin derechos de terceros.
 *  - RECORDING: grabación incluida en assets/sounds con su licencia en sounds/LICENSES.json.
 *    En esta versión NO hay grabaciones incluidas (ver LEEME_V44.md: no se pudo obtener ninguna
 *    con licencia verificada desde el entorno de desarrollo).
 *  - USER: grabaciones propias del usuario importadas al proyecto.
 */
object SoundCatalog {
    enum class Source { SYNTH, RECORDING, USER }
    data class Sound(val id: String, val name: String, val source: Source, val event: Boolean = false)

    private fun s(id: String, name: String, event: Boolean = false) = Sound(id, name, Source.SYNTH, event)

    val categories = listOf("Noche", "Campo", "Ciudad", "Tormenta", "Mar", "Viento", "Terror", "Acción", "Mis sonidos")

    val byCategory: Map<String, List<Sound>> = mapOf(
        "Noche" to listOf(s("crickets", "Grillos"), s("owl", "Búho"), s("wind", "Viento nocturno"), s("river", "Arroyo")),
        "Campo" to listOf(s("birds", "Pájaros"), s("leaves", "Hojas"), s("river", "Arroyo"), s("wind", "Brisa")),
        "Ciudad" to listOf(s("city", "Tráfico lejano"), s("horn", "Claxon lejano"), s("train", "Tren"), s("footsteps", "Pasos")),
        "Tormenta" to listOf(s("rain_soft", "Lluvia suave"), s("rain_heavy", "Lluvia fuerte"), s("thunder", "Trueno"),
            s("lightning", "Relámpago"), s("gale", "Ráfagas"), s("drip", "Goteras")),
        "Mar" to listOf(s("waves", "Olas"), s("seagulls", "Gaviotas"), s("waterfall", "Cascada"), s("river", "Río")),
        "Viento" to listOf(s("wind", "Brisa"), s("gale", "Vendaval"), s("sand", "Arena"), s("creak", "Madera crujiendo")),
        "Terror" to listOf(s("creak", "Crujidos"), s("heartbeat", "Latidos"), s("footsteps", "Pasos lejanos"),
            s("drip", "Goteras"), s("wind", "Viento en la casa")),
        "Acción" to listOf(s("impact", "Impacto", true), s("whoosh", "Whoosh", true), s("boom", "Explosión", true),
            s("magic", "Magia", true), s("fire", "Fuego"))
    )

    fun find(id: String): Sound? = byCategory.values.flatten().firstOrNull { it.id == id }

    /** v44.17 · Nombre del archivo de cada sonido/música propio del proyecto abierto (id → nombre). */
    val userNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun title(id: String): String = find(id)?.name ?: if (id.startsWith(SoundClips.USER))
        (userNames[id]?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Mi sonido") else id

    /** Grabaciones reales que la orden pide y que faltan (se documentan, no se inventan). */
    val missingRecordings = listOf(
        "Noche: grillos, búho, perro lejano, viento nocturno",
        "Mañana: gallo, pajaritos, bosque, arroyo, brisa, hojas",
        "Ciudad: tráfico cercano/lejano, claxon, motores, tren, estación, pasos",
        "Tormenta: lluvia sobre suelo/tejado/ventana, truenos, ráfagas, goteras",
        "Mar: olas suaves/fuertes, gaviotas, río, cascada",
        "Viento/desierto: brisa, vendaval, arena, remolinos, puerta golpeando",
        "Terror: crujidos, latidos, pasos, viento de habitación, golpes",
        "Anime/acción: impacto, whoosh, explosión, magia")
}
