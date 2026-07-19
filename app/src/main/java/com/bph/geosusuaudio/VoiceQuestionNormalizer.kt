package com.bph.geosusuaudio

object VoiceQuestionNormalizer {

    private val technicalContextWords = setOf(
        "topografia", "topografico", "topografica", "nivelacion", "nivel", "cota", "cotas", "altura",
        "terreno", "levantamiento", "replanteo", "banco", "instrumento", "mira", "vista",
        "mineria", "minera", "geologia", "mineral", "mina", "yacimiento", "exploracion", "prospeccion"
    )

    private val weakNoiseWords = setOf(
        "ruido", "musica", "ingles", "english", "hola", "eh", "ah", "mmm", "este", "esto",
        "cosa", "cosas", "algo", "alguien", "gente"
    )

    private val stopWords = setOf(
        "que", "q", "ke", "es", "son", "un", "una", "el", "la", "los", "las", "de", "del",
        "en", "por", "para", "dime", "di", "define", "definicion", "concepto", "significa",
        "explica", "sobre", "pregunta", "oye"
    )

    fun normalize(raw: String): String {
        var question = ResponseRepository.normalize(raw)
        if (question.isBlank()) return ""

        question = question.replace("\u00bf", " ").replace("\\s+".toRegex(), " ").trim()
        val hasContext = hasTechnicalContext(question)

        // Correcciones de reconocimiento ya usadas por la app. Solo corrigen el texto
        // escuchado; no contienen respuestas ni sustituyen la búsqueda JSON/NET.
        val phraseCorrections = linkedMapOf(
            "be eme" to "bm",
            "ve eme" to "bm",
            "baby" to "bm",
            "beibi" to "bm",
            "barbie" to "bm",
            "beybi" to "bm",
            "beby" to "bm",
            "babi" to "bm",
            "e pe pe" to "epp",
            "e p p" to "epp",
            "i pe pe" to "epp",
            "equipo proteccion personal" to "epp",
            "bi em" to "bm",
            "bee em" to "bm",
            "be me" to "bm",
            "b m" to "bm",
            "banco nivel" to "banco de nivel",
            "banco del nivel" to "banco de nivel",
            "cuota" to "cota",
            "cuotas" to "cotas",
            "ce pe" to "cp",
            "c p" to "cp",
            "ve a" to "va",
            "v a" to "va",
            "ve i" to "vi",
            "v i" to "vi",
            "ve efe" to "vf",
            "v f" to "vf",
            "a i" to "ai",
            "altura instrumental" to "altura de instrumento",
            "altura del instrumento" to "altura de instrumento",
            "vista final" to "vista adelante",
            "lectura final" to "vista adelante",
            "vista atras" to "vista atras",
            "mieria" to "mineria",
            "miniria" to "mineria",
            "menerea" to "mineria",
            "meneria" to "mineria",
            "topo grafia" to "topografia",
            "topogafia" to "topografia",
            "topografia superfacial" to "topografia superficial"
        )

        for ((bad, good) in phraseCorrections) {
            question = question.replace("\\b${Regex.escape(bad)}\\b".toRegex(), good)
        }

        if (hasContext || question.contains("banco") || question.contains("nivel")) {
            val bmLike = listOf("bebe", "bebé", "pm", "p m", "bm.", "beme")
            for (bad in bmLike) {
                question = question.replace(
                    "\\b${Regex.escape(ResponseRepository.normalize(bad))}\\b".toRegex(),
                    "bm"
                )
            }
        }

        return question.replace("\u00bf", " ").replace("\\s+".toRegex(), " ").trim()
    }

    fun isTooUnclearForJsonOrNet(question: String): Boolean {
        val normalized = normalize(question)
        if (normalized.isBlank()) return true
        val words = normalized.split(" ").filter { it.isNotBlank() }
        val content = words.filter { it.length >= 2 && it !in stopWords && it !in weakNoiseWords }
        if (content.isEmpty()) return true

        val noiseHits = words.count { it in weakNoiseWords }
        return noiseHits >= 3 && content.size <= 1 && !hasTechnicalContext(normalized)
    }

    fun isUnsafeForNet(question: String): Boolean {
        return isTooUnclearForJsonOrNet(normalize(question))
    }

    private fun hasTechnicalContext(question: String): Boolean {
        val words = question.split(" ").filter { it.isNotBlank() }.toSet()
        return words.any { it in technicalContextWords } ||
            question.contains("banco de nivel") ||
            question.contains("vista atras") ||
            question.contains("vista adelante") ||
            question.contains("vista intermedia") ||
            question.contains("altura de instrumento")
    }
}
