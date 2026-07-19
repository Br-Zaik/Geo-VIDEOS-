package com.bph.geosusuaudio

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("geo_susu_settings", Context.MODE_PRIVATE)

    enum class VoiceSpeed {
        VERY_SLOW,
        SLOW,
        NORMAL,
        FAST,
        VERY_FAST
    }

    enum class MicSensitivity {
        NORMAL,
        WHISPER
    }

    enum class NoiseCleaner {
        NORMAL,
        STRONG
    }

    enum class ListenEngine {
        GOOGLE_INTERNAL,
        GOOGLE,
        VOSK
    }

    fun getVoiceSpeed(): VoiceSpeed {
        return when (prefs.getString(KEY_VOICE_SPEED, VoiceSpeed.NORMAL.name)) {
            VoiceSpeed.VERY_SLOW.name -> VoiceSpeed.VERY_SLOW
            VoiceSpeed.SLOW.name -> VoiceSpeed.SLOW
            VoiceSpeed.FAST.name -> VoiceSpeed.FAST
            VoiceSpeed.VERY_FAST.name -> VoiceSpeed.VERY_FAST
            else -> VoiceSpeed.NORMAL
        }
    }

    fun setVoiceSpeed(value: VoiceSpeed) {
        prefs.edit().putString(KEY_VOICE_SPEED, value.name).apply()
    }

    fun getVoiceRate(): Float {
        return when (getVoiceSpeed()) {
            VoiceSpeed.VERY_SLOW -> 0.50f
            VoiceSpeed.SLOW -> 0.75f
            VoiceSpeed.NORMAL -> 1.00f
            VoiceSpeed.FAST -> 1.25f
            VoiceSpeed.VERY_FAST -> 1.50f
        }
    }

    fun getVoiceSpeedLabel(): String {
        return when (getVoiceSpeed()) {
            VoiceSpeed.VERY_SLOW -> "0.50x"
            VoiceSpeed.SLOW -> "0.75x"
            VoiceSpeed.NORMAL -> "1.00x"
            VoiceSpeed.FAST -> "1.25x"
            VoiceSpeed.VERY_FAST -> "1.50x"
        }
    }

    fun getMicSensitivity(): MicSensitivity {
        return when (prefs.getString(KEY_MIC_SENSITIVITY, MicSensitivity.WHISPER.name)) {
            MicSensitivity.NORMAL.name -> MicSensitivity.NORMAL
            else -> MicSensitivity.WHISPER
        }
    }

    fun getMicGain(): Float {
        return when (getMicSensitivity()) {
            MicSensitivity.NORMAL -> 1.80f
            MicSensitivity.WHISPER -> 4.20f
        }
    }

    fun getNoiseCleaner(): NoiseCleaner {
        return when (prefs.getString(KEY_NOISE_CLEANER, NoiseCleaner.STRONG.name)) {
            NoiseCleaner.NORMAL.name -> NoiseCleaner.NORMAL
            else -> NoiseCleaner.STRONG
        }
    }

    fun getListenEngine(): ListenEngine {
        return when (prefs.getString(KEY_LISTEN_ENGINE, ListenEngine.GOOGLE_INTERNAL.name)) {
            ListenEngine.VOSK.name -> ListenEngine.VOSK
            ListenEngine.GOOGLE.name -> ListenEngine.GOOGLE
            else -> ListenEngine.GOOGLE_INTERNAL
        }
    }

    companion object {
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_NOISE_CLEANER = "noise_cleaner"
        private const val KEY_LISTEN_ENGINE = "listen_engine"
    }
}
