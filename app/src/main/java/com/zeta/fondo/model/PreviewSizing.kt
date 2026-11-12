package com.zeta.fondo.model

/**
 * v39 · Tamaño ÚNICO de la vista previa de edición. Lo usan la importación, la caché y la
 * apertura: así no se decodifica el original dos veces (antes la caché era de 1920 px y al
 * abrir se pedían 2048/2560 px, lo que obligaba a volver a decodificar el original).
 */
object PreviewSizing {
    fun target(lowRam: Boolean, memoryClassMb: Int): Int = when {
        lowRam -> 1600
        memoryClassMb < 256 -> 2048
        else -> 2560
    }

    /** ¿Sirve una caché existente? Basta con que llegue al 74 % del objetivo (o del original). */
    fun cacheIsEnough(cacheLongSide: Int, target: Int, originalLongSide: Int): Boolean =
        cacheLongSide >= (minOf(target, originalLongSide) * 0.74f).toInt() - 16
}
