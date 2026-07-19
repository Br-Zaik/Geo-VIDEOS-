package com.bph.geosusuaudio

import java.text.Normalizer

/**
 * Tipos y normalización compartidos por el buscador JSON, la voz y NET.
 * El antiguo banco TXT fue retirado porque la app usa importación JSON local.
 */
class ResponseRepository private constructor() {

    data class MatchResult(
        val answer: String,
        val itemId: String,
        val score: Int
    )

    companion object {
        fun normalize(text: String): String {
            return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .replace("[^a-z0-9ñ ]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }
}
