package com.zeta.fondo.engine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondo sin el personaje (placa) para el 2.5D, SIN IA: el hueco de Proteger se rellena con el
 * relleno pull-push de [CutoutMath.fill]. v44.17: sustituye al antiguo CharacterSegmenter (MI-GAN).
 */
object BackgroundFill {
    /** [hole] = máscara del personaje del mismo tamaño que [src] (0..255). Devuelve ARGB y false (sin IA). */
    suspend fun fill(src: Bitmap, hole: ByteArray): Pair<IntArray, Boolean> = withContext(Dispatchers.Default) {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        // Un poco más ancho que el personaje: el contorno (líneas, halo) no se copia al fondo.
        val grown = CutoutMath.dilate(hole, w, h, (maxOf(w, h) / 160).coerceIn(2, 12))
        CutoutMath.fill(px, w, h, grown) to false
    }
}
