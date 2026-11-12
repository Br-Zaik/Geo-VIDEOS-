package com.zeta.fondo.engine

/** Cálculo puro (sin Android) del rectángulo pintado en una máscara de 8 bits. */
object MaskBounds {
    /**
     * @param alpha bytes de la máscara (fila a fila, [rowBytes] por fila)
     * @return [x0, y0, x1, y1] normalizados 0..1, o null si no hay píxeles sobre el umbral.
     */
    fun of(alpha: ByteArray, w: Int, h: Int, rowBytes: Int, threshold: Int = 24): FloatArray? {
        if (w <= 0 || h <= 0) return null
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        // Muestreo cada 2 píxeles: suficiente para un borde de raíz y el doble de rápido.
        var y = 0
        while (y < h) {
            val base = y * rowBytes
            var x = 0
            while (x < w) {
                if ((alpha[base + x].toInt() and 0xFF) > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += 2
            }
            y += 2
        }
        if (maxX < 0) return null
        return floatArrayOf(
            minX.toFloat() / w, minY.toFloat() / h,
            ((maxX + 2).coerceAtMost(w)).toFloat() / w, ((maxY + 2).coerceAtMost(h)).toFloat() / h
        )
    }
}
