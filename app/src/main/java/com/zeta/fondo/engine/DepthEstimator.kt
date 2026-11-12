package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.zeta.fondo.diag.ZetaLog
import java.io.File

/**
 * «2.5D rápido» SIN IA: mapa de profundidad calculado al instante con reglas (fondo liso,
 * caras, Proteger), sin modelos ni descargas. Se guarda en la carpeta del proyecto
 * (depth.png + depth.txt) y la animación solo lee esa textura.
 *
 * v44.17: se quitó por completo la IA local (Depth Anything / MiDaS y ONNX Runtime).
 */
class DepthEstimator {

    data class Result(val w: Int, val h: Int, val map: ByteArray, val meta: DepthMath.Meta)

    /** Mapa en caché del proyecto si corresponde a la misma imagen; null si no hay o es de otra imagen. */
    fun loadCached(projectDir: File, imageFile: File): Result? = try {
        val metaFile = File(projectDir, META)
        val png = File(projectDir, PNG)
        val meta = if (metaFile.isFile && png.isFile) DepthMath.Meta.decode(metaFile.readText()) else null
        if (meta == null) null else {
            // v44.17: solo el 2.5D rápido; los mapas antiguos de la IA se descartan y se rehacen.
            val argb = if (meta.model == QUICK) pixelsAt(imageFile, meta.w, meta.h) else null
            if (argb == null || DepthMath.stamp(argb.first, argb.second, argb.third) != meta.stamp) null
            else {
                val bmp = BitmapFactory.decodeFile(png.path) ?: throw IllegalStateException("depth.png ilegible")
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                bmp.recycle()
                Result(meta.w, meta.h, ByteArray(px.size) { (px[it] shr 16).toByte() }, meta)
            }
        }
    } catch (e: Throwable) {
        ZetaLog.warn("depth", "caché no válida: ${e.message}")
        null
    }

    /**
     * v49 · «2.5D rápido» SIN IA (como el efecto de profundidad de CapCut): al instante, sin
     * descargas ni riesgo de memoria. [protect] = Proteger (personaje) del tamaño [pw]x[ph].
     */
    fun estimateQuick(projectDir: File, imageFile: File, protect: ByteArray?, pw: Int, ph: Int,
                      faces: List<FloatArray> = emptyList()): Result {
        val t0 = System.nanoTime()
        val (iw, ih) = BitmapUtil.bounds(imageFile)
        val sz = DepthMath.inputSize(iw, ih, 384, 1)
        val (argb, w, h) = pixelsAt(imageFile, sz[0], sz[1])
        val prot = protect?.let { DepthMath.resize(it, pw, ph, w, h) }
        // v44.10 · Sin Proteger, las caras detectadas dan la silueta aproximada del personaje.
        val subject = if (prot != null) null else DepthMath.subjectAuto(argb, w, h, faces)
        val map = DepthMath.quick(argb, w, h, prot, subject)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val r = Result(w, h, map, DepthMath.Meta(QUICK, w, h, DepthMath.stamp(argb, w, h), ms, DepthMath.contrast(map)))
        save(projectDir, r)
        return r
    }

    private fun pixelsAt(imageFile: File, w: Int, h: Int): Triple<IntArray, Int, Int> {
        val size = intArrayOf(w, h)
        val src = BitmapUtil.decodeAtMost(imageFile, maxOf(size[0], size[1]) * 2)
        val scaled = Bitmap.createScaledBitmap(src, size[0], size[1], true)
        if (scaled !== src) src.recycle()
        val px = IntArray(size[0] * size[1])
        scaled.getPixels(px, 0, size[0], 0, 0, size[0], size[1])
        scaled.recycle()
        return Triple(px, size[0], size[1])
    }

    private fun save(projectDir: File, r: Result) {
        val px = IntArray(r.map.size) { val v = r.map[it].toInt() and 0xFF; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val bmp = Bitmap.createBitmap(px, r.w, r.h, Bitmap.Config.ARGB_8888)
        val tmp = File(projectDir, "$PNG.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        tmp.renameTo(File(projectDir, PNG))
        File(projectDir, META).writeText(r.meta.encode())
    }

    companion object {
        const val QUICK = "rapido"
        const val PNG = "depth.png"
        const val META = "depth.txt"
        fun labelOf(id: String) = "2.5D rápido"

        /** Borra el mapa guardado (p. ej. al reemplazar la imagen del proyecto). */
        fun clear(projectDir: File) {
            File(projectDir, PNG).delete(); File(projectDir, META).delete()
        }
    }
}
