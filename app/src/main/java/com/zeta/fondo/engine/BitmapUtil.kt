package com.zeta.fondo.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtil {

    fun bounds(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth to o.outHeight
    }

    /**
     * Vista previa nítida con menos trabajo de CPU: ImageDecoder decodifica directamente al
     * tamaño objetivo. v44.26: es la misma decodificación que [decodeAtMost] (antes una copia
     * aparte sin el camino de respaldo ni el paso a 8 bits: un PNG de 16 bits ocupaba el doble).
     */
    fun decodePreview(file: File, maxSide: Int): Bitmap = decodeAtMost(file, maxSide)

    /**
     * Decodifica la imagen con su lado largo como máximo [maxSide].
     *
     * v44.25 · ImageDecoder decodifica DIRECTAMENTE al tamaño final (antes se decodificaba hasta
     * el doble y luego se reducía: hasta 4 veces más memoria en el pico, por ejemplo al exportar
     * en 4K o al «Mejorar»). Respaldo, solo si ImageDecoder no puede con el archivo: inSampleSize
     * hasta 1,5× el tamaño final y ajuste con filtrado.
     */
    fun decodeAtMost(file: File, maxSide: Int): Bitmap {
        try {
            return to8888(decodeTarget(ImageDecoder.createSource(file), maxSide))
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (_: Exception) {
            // Archivo raro para ImageDecoder: se intenta con BitmapFactory.
        }
        val (w, h) = bounds(file)
        if (w <= 0 || h <= 0) throw IOException("No se pudo leer la imagen")
        val o = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(max(w, h), maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, o)
            ?: throw IOException("No se pudo decodificar la imagen")
        return fitTo(decoded, maxSide)
    }

    /**
     * v44.25 · Imagen de la galería o de Archivos ([uri]) con su lado largo ≤ [maxSide] y DERECHA.
     * [orientation] = etiqueta EXIF de orientación tal cual (1 normal; 3, 6 y 8 giros; 2, 4, 5 y 7
     * espejos de la cámara frontal). Una sola decodificación con ImageDecoder, ya derecha y al
     * tamaño justo (una foto de 50 MP girada pedía antes ~400 MB y en muchos teléfonos no se
     * abría). Respaldo, solo si ImageDecoder no puede con el archivo: muestreo y luego giro /
     * espejo y reducción en una sola copia.
     */
    fun decodeUriAtMost(cr: ContentResolver, uri: Uri, maxSide: Int, orientation: Int = 1): Bitmap {
        try {
            val bmp = to8888(decodeTarget(ImageDecoder.createSource(cr, uri), maxSide))
            // ImageDecoder aplica el EXIF él mismo. Si no lo hizo (formato raro), se aplica aquí;
            // solo se puede saber en las orientaciones que cambian el ancho por el alto (5 a 8).
            if (orientation !in 5..8) return bmp
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            val notApplied = o.outWidth > 0 && o.outWidth != o.outHeight && bmp.width != bmp.height &&
                (o.outWidth >= o.outHeight) == (bmp.width >= bmp.height)
            return if (notApplied) transform(bmp, 1f, orientation) else bmp
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (_: Exception) {
            // Se intenta con BitmapFactory.
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Solo se leen las medidas: decodeStream devuelve null a propósito, no es un error.
        val head = cr.openInputStream(uri) ?: throw IOException("No se pudo abrir la imagen")
        head.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw IOException("Ese archivo no es una imagen compatible")
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(max(o.outWidth, o.outHeight), maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("No se pudo decodificar la imagen")
        val long = max(decoded.width, decoded.height)
        val k = if (long > maxSide) maxSide.toFloat() / long else 1f
        if (orientation !in 2..8 && k >= 1f) return to8888(decoded)
        return to8888(transform(decoded, k, orientation))
    }

    /**
     * Matriz de la etiqueta EXIF de orientación (la correspondencia estándar, la misma que usan
     * las galerías): 2 espejo, 3 giro 180°, 4 espejo vertical, 5 trasponer, 6 giro 90°,
     * 7 trasponer al revés, 8 giro 270°. Otro valor: sin cambio.
     */
    fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> { setRotate(180f); postScale(-1f, 1f) }
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        }
    }

    /** Reducción [k] y orientación EXIF en una sola copia (libera el original). */
    private fun transform(b: Bitmap, k: Float, orientation: Int): Bitmap {
        val m = orientationMatrix(orientation).apply { postScale(k, k) }
        val out = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (out !== b) b.recycle()
        return out
    }

    /** ImageDecoder al tamaño justo (lado largo ≤ [maxSide]), en memoria normal y tolerante. */
    private fun decodeTarget(source: ImageDecoder.Source, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longSide = max(w, h)
            if (longSide > maxSide) {
                val k = maxSide.toFloat() / longSide.toFloat()
                decoder.setTargetSize(max(1, (w * k).roundToInt()), max(1, (h * k).roundToInt()))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            // Un JPEG cortado se abre igual (como hacía BitmapFactory) en vez de fallar.
            decoder.setOnPartialImageListener { true }
        }

    /**
     * Muestreo del camino de respaldo: potencia de 2 que deja el lado largo en ≤ 1,5 × [maxSide] (antes
     * hasta 2× → hasta 4 veces más píxeles en memoria de los necesarios).
     */
    fun sampleFor(long: Int, maxSide: Int): Int {
        var sample = 1
        val limit = maxSide * 3 / 2
        while (long / sample > limit) sample *= 2
        return sample
    }

    private fun fitTo(decoded: Bitmap, maxSide: Int): Bitmap {
        val dl = max(decoded.width, decoded.height)
        if (dl <= maxSide) return to8888(decoded)
        val k = maxSide.toFloat() / dl
        val nw = max(1, (decoded.width * k).roundToInt())
        val nh = max(1, (decoded.height * k).roundToInt())
        val scaled = Bitmap.createScaledBitmap(decoded, nw, nh, true)
        if (scaled !== decoded) decoded.recycle()
        return to8888(scaled)
    }

    /** Siempre ARGB_8888 (los PNG de 16 bits llegan en F16: el doble de memoria). */
    private fun to8888(b: Bitmap): Bitmap {
        if (b.config == Bitmap.Config.ARGB_8888) return b
        val c = b.copy(Bitmap.Config.ARGB_8888, false) ?: return b
        b.recycle()
        return c
    }
}
