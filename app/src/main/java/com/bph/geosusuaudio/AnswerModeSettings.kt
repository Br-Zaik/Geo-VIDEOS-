package com.bph.geosusuaudio

import android.content.Context

class AnswerModeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("geo_susu_memory", Context.MODE_PRIVATE)

    enum class Mode {
        JSON,
        INTERNET,
        AUTO
    }

    fun getMode(): Mode {
        return when (prefs.getString(KEY_MODE, Mode.AUTO.name)) {
            Mode.JSON.name -> Mode.JSON
            Mode.INTERNET.name -> Mode.INTERNET
            Mode.AUTO.name -> Mode.AUTO
            "LOCAL" -> Mode.JSON
            else -> Mode.AUTO
        }
    }

    fun setMode(mode: Mode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getLabel(): String {
        return when (getMode()) {
            Mode.JSON -> "JSON"
            Mode.INTERNET -> "NET"
            Mode.AUTO -> "AUTO"
        }
    }

    companion object {
        private const val KEY_MODE = "answer_mode"
    }
}
