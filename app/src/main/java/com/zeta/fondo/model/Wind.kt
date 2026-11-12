package com.zeta.fondo.model

/**
 * v44 · Viento global y fuerza por efecto (código puro, pruebas JVM).
 *
 * - Cada efecto direccional tiene su dirección ("id.direction", grados; 0 = derecha,
 *   90 = abajo, como siempre) y ahora también su fuerza ("id.force", 0,2–2; 1 = como la v43).
 * - Viento global: "wind.on", "wind.direction", "wind.force". Un efecto con
 *   "id.followWind" = 1 usa la dirección y fuerza del viento global en vez de las suyas.
 * - Proyectos anteriores: no tienen ninguna de estas claves → todo igual que en la v43.
 */
object Wind {
    const val MIN_FORCE = .2f
    const val MAX_FORCE = 2f

    val directional = listOf("rain", "snow", "sakura", "fog", "rays", "energy", "speed",
        "ash", "leaves", "embers", "dust", "bubbles", "electric", "focus", "shock",
        "comet", "runes", "pulse", "smoke", "shards", "gust", "tornado", "shore", "blast", "blood", "slash",
        // v44: efectos nuevos con dirección y/o origen
        "sand", "cloudshadow", "birds", "citylights", "lensflare",
        // v44.11
        "symbols", "inkweather", "steam", "skydrift", "sunlight", "lightdir", "rimlight", "radialblur", "focusblur")

    /** Efectos donde «seguir el viento» tiene sentido físico (partículas y atmósfera). */
    val windDriven = setOf("rain", "snow", "sakura", "fog", "ash", "leaves", "embers", "dust",
        "bubbles", "smoke", "gust", "shards", "comet", "sand", "cloudshadow", "inkweather")

    val defaultAngle = mapOf("rain" to 90f, "snow" to 90f, "sakura" to 135f,
        "ash" to 270f, "embers" to 270f, "bubbles" to 270f, "dust" to 90f,
        "leaves" to 135f, "fog" to 0f, "smoke" to 270f, "gust" to 0f, "shore" to 90f, "tornado" to 0f,
        "sand" to 0f, "cloudshadow" to 15f, "birds" to 345f, "citylights" to 0f, "lensflare" to 0f,
        "symbols" to 0f, "inkweather" to 100f, "steam" to 270f,
        "skydrift" to 0f, "sunlight" to 0f, "lightdir" to 45f, "rimlight" to 0f,
        "radialblur" to 0f, "focusblur" to 0f)

    /** v44: origen inicial (0..1 de la imagen) de los efectos que nacen en un punto concreto. */
    val defaultOrigin = mapOf("birds" to (.5f to .2f), "lensflare" to (.78f to .14f), "citylights" to (.5f to .8f),
        "symbols" to (.68f to .28f), "steam" to (.5f to .8f), "sunlight" to (.5f to .4f),
        "radialblur" to (.5f to .5f), "focusblur" to (.5f to .5f))

    fun globalOn(s: Map<String, Float>): Boolean = (s["wind.on"] ?: 0f) > .5f
    fun follows(s: Map<String, Float>, id: String): Boolean = id in windDriven && globalOn(s) && (s["$id.followWind"] ?: 0f) > .5f

    /** Dirección (grados) y fuerza efectivas del efecto [id] (clave base [prefix], p. ej. "leaves.copy"). */
    fun resolve(s: Map<String, Float>, id: String, prefix: String = id): Pair<Float, Float> {
        if (follows(s, id)) return (s["wind.direction"] ?: 0f) to (s["wind.force"] ?: 1f).coerceIn(MIN_FORCE, MAX_FORCE)
        val angle = s["$prefix.direction"] ?: s["$id.direction"] ?: defaultAngle[id] ?: 0f
        val force = (s["$prefix.force"] ?: s["$id.force"] ?: 1f).coerceIn(MIN_FORCE, MAX_FORCE)
        return angle to force
    }

    /** Ángulo (grados 0..360) y fuerza (0..[MAX_FORCE]) desde la posición del joystick (-1..1, y hacia abajo). */
    fun fromStick(x: Float, y: Float): Pair<Float, Float> {
        val len = kotlin.math.sqrt(x * x + y * y).coerceAtMost(1f)
        var a = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
        if (a < 0f) a += 360f
        // Imán de 15° para elegir direcciones exactas con el dedo.
        val snapped = (Math.round(a / 15f) * 15f) % 360f
        if (kotlin.math.abs(snapped - a) < 4f) a = snapped
        return a to (len * MAX_FORCE).coerceIn(MIN_FORCE, MAX_FORCE)
    }

    /** Posición del joystick (-1..1) para un ángulo y una fuerza. */
    fun toStick(angleDeg: Float, force: Float): Pair<Float, Float> {
        val r = Math.toRadians(angleDeg.toDouble())
        val k = (force / MAX_FORCE).coerceIn(0f, 1f)
        return (kotlin.math.cos(r) * k).toFloat() to (kotlin.math.sin(r) * k).toFloat()
    }
}
