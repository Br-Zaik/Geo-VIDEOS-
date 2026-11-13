package com.geovideos.app.playback

import androidx.media3.common.Player

/**
 * "Mantener presionado = 2x", como YouTube: la velocidad SIEMPRE vuelve a la que tenías al
 * soltar. Si el dedo se pierde (el video se achica, cambia de video, la vista se recicla),
 * [release] devuelve la velocidad igual: nunca se queda en 2x sola.
 */
object HoldSpeed {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var restoreSpeed: Float = 1f

    fun start(player: Player) {
        // Si ya estaba activo (se perdió un "soltar"), no tomar 2x como velocidad normal.
        if (!active) restoreSpeed = player.playbackParameters.speed.takeIf { it in 0.25f..2f && it != 2f } ?: 1f
        active = true
        player.setPlaybackSpeed(2f)
    }

    /** Termina el 2x (seguro de llamar aunque no esté activo). */
    fun release(player: Player?) {
        if (!active) return
        active = false
        player?.setPlaybackSpeed(restoreSpeed)
    }
}
