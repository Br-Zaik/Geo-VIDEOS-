package com.bph.geosusuaudio

import android.util.Log

/**
 * Centraliza la regla de los modos de respuesta para que la pantalla,
 * Google interno y Vosk se comporten exactamente igual.
 */
class QuestionRouter(
    private val jsonRepository: JsonKnowledgeRepository
) {
    sealed class Result {
        data class Json(val match: ResponseRepository.MatchResult) : Result()
        data object Net : Result()
        data object JsonNotFound : Result()
    }

    fun resolve(mode: AnswerModeSettings.Mode, question: String): Result {
        return when (mode) {
            AnswerModeSettings.Mode.INTERNET -> Result.Net
            AnswerModeSettings.Mode.JSON -> {
                val match = safeJsonMatch(question)
                if (match.itemId != "none") Result.Json(match) else Result.JsonNotFound
            }
            AnswerModeSettings.Mode.AUTO -> {
                // Los ejercicios variables se resuelven por API en AUTO. El JSON queda
                // como apoyo para preguntas de estudio y respuestas de mayor profundidad.
                if (OnlineAnswerProvider.requiresStructuredNetAnswer(question)) {
                    Result.Net
                } else {
                    val match = safeJsonMatch(question)
                    if (match.itemId != "none") Result.Json(match) else Result.Net
                }
            }
        }
    }

    private fun safeJsonMatch(question: String): ResponseRepository.MatchResult {
        return try {
            jsonRepository.findBest(question)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al buscar en el JSON", e)
            ResponseRepository.MatchResult(JsonKnowledgeRepository.JSON_NOT_FOUND, "none", 0)
        }
    }

    companion object {
        private const val TAG = "GeoSusuRouter"
    }
}
