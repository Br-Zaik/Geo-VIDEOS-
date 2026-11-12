package com.zeta.fondo.model

/**
 * v44.11 · Color propio de cada efecto: Original / Blanco y negro / Sepia + saturación, brillo
 * y contraste. Solo cambia lo que el efecto añade, nunca la imagen. Código puro (pruebas JVM).
 *
 * El shader recibe `uGrade[i]` = (modo, saturación, brillo, contraste) con i = índice en [ids]
 * (el mismo orden que usa fondo.frag). «Modo manga» = "style.mono": todo en blanco y negro.
 */
object EffectGrade {
    /** Efectos que dibujan algo encima de la imagen (los que mueven la imagen no tienen color). */
    val ids = listOf(
        "rays", "energy", "speed", "stars", "fog", "rain", "snow", "sakura", "flies", "cloudshadow",
        "aurora", "citylights", "birds", "sand", "ash", "leaves", "embers", "dust", "bubbles", "electric",
        "focus", "shock", "flash", "tone", "grain", "ink", "comet", "runes", "hatch", "pulse",
        "smoke", "shards", "flame", "eyes", "bokeh", "sparkle", "lightleak", "blackaura", "inkdrip", "dramatic",
        "tornado", "gust", "bolt", "blast", "blood", "slash", "reveal", "lensflare", "glowup", "inkweather",
        "symbols",
        // v44.12
        "dapple", "caustics", "steam", "flicker",
        // v44.13
        "sunlight",
        // v44.14
        "atmos", "lightdir", "rimlight",
        // v44.15
        "deepglow", "filmburn"
    )

    val modes = listOf("Original", "Blanco y negro", "Sepia")
    const val MODE = "gmode"
    const val SAT = "gsat"
    const val BRI = "gbri"
    const val CON = "gcon"
    val keys = setOf(MODE, SAT, BRI, CON)

    fun has(id: String): Boolean = id in ids

    /** Valores para el shader: (modo, saturación −1..1, brillo −1..1, contraste −1..1). */
    fun uniform(s: Map<String, Float>, id: String): FloatArray = floatArrayOf(
        (s["$id.$MODE"] ?: 0f).coerceIn(0f, 2f),
        (s["$id.$SAT"] ?: 0f).coerceIn(-1f, 1f),
        (s["$id.$BRI"] ?: 0f).coerceIn(-1f, 1f),
        (s["$id.$CON"] ?: 0f).coerceIn(-1f, 1f)
    )

    fun isDefault(s: Map<String, Float>, id: String): Boolean = uniform(s, id).all { it == 0f }

    /** Vuelve el color del efecto a como venía. */
    fun reset(id: String): Map<String, Float> = keys.associate { "$id.$it" to 0f }
}
