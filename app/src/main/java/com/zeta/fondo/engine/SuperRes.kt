package com.zeta.fondo.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.zeta.fondo.diag.ZetaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * v47 · Mejora opcional de imagen anime/manga (superresolución) por baldosas, sin Android
 * en la parte de cálculo ([TileUpscale], se prueba en la JVM).
 * Baldosa de 128 px con margen de 10 px: cada trozo se procesa con algo de contexto y al
 * pegarlo se recorta el margen, así no hay costuras. La RAM queda acotada por la baldosa.
 */
object TileUpscale {

    /** Baldosas: [x0, y0, x1, y1] útiles y [px0, py0, px1, py1] con margen, en píxeles de entrada. */
    fun tiles(w: Int, h: Int, tile: Int = 128, pad: Int = 10): List<IntArray> {
        val out = ArrayList<IntArray>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val x1 = min(w, x + tile); val y1 = min(h, y + tile)
                out.add(intArrayOf(x, y, x1, y1, max(0, x - pad), max(0, y - pad), min(w, x1 + pad), min(h, y1 + pad)))
                x += tile
            }
            y += tile
        }
        return out
    }

    /**
     * v44.18 · Escala final elegida por el usuario: 1 = Original (mismo tamaño, más nítida),
     * 2 = x2, 4 = x4; nunca más de 4096 px en el lado largo.
     */
    fun targetScale(w: Int, h: Int, choice: Int, maxSide: Int = 4096): Float =
        min(choice.coerceIn(1, 4).toFloat(), maxSide.toFloat() / max(w, h)).coerceAtLeast(min(1f, maxSide.toFloat() / max(w, h)))

    /** Tamaño de salida: el modelo multiplica por [model] pero el lado largo no pasa de [maxSide]. */
    fun outputScale(w: Int, h: Int, model: Int = 4, maxSide: Int = 4096): Float =
        min(model.toFloat(), maxSide.toFloat() / max(w, h)).coerceAtLeast(1f)

    /**
     * Imagen ARGB → ARGB ampliada por [outScale] (≤ [model]). [run] recibe un tensor CHW 0..1
     * de (tw x th) y devuelve CHW de (tw*model x th*model). [onTile] informa del avance.
     */
    suspend fun upscale(argb: IntArray, w: Int, h: Int, model: Int, outScale: Float,
                        run: suspend (FloatArray, Int, Int) -> FloatArray,
                        onTile: (Int, Int) -> Unit = { _, _ -> },
                        tile: Int = 128, pad: Int = 10): Triple<IntArray, Int, Int> {
        val ow = (w * outScale).toInt().coerceAtLeast(1); val oh = (h * outScale).toInt().coerceAtLeast(1)
        val out = IntArray(ow * oh)
        val list = tiles(w, h, tile, pad)
        for ((k, t) in list.withIndex()) {
            val (x0, y0, x1, y1) = t.toList(); val px0 = t[4]; val py0 = t[5]; val px1 = t[6]; val py1 = t[7]
            val tw = px1 - px0; val th = py1 - py0
            val n = tw * th
            val chw = FloatArray(3 * n)
            for (yy in 0 until th) for (xx in 0 until tw) {
                val c = argb[(py0 + yy) * w + px0 + xx]; val i = yy * tw + xx
                chw[i] = ((c shr 16) and 0xFF) / 255f; chw[n + i] = ((c shr 8) and 0xFF) / 255f; chw[2 * n + i] = (c and 0xFF) / 255f
            }
            val res = run(chw, tw, th)
            val rw = tw * model; val rh = th * model; val rn = rw * rh
            require(res.size == 3 * rn) { "Salida ${res.size} ≠ 3x${rw}x$rh" }
            // Píxeles de salida de la parte útil de esta baldosa (sin margen).
            val ox0 = (x0 * outScale).toInt(); val oy0 = (y0 * outScale).toInt()
            val ox1 = if (x1 == w) ow else (x1 * outScale).toInt(); val oy1 = if (y1 == h) oh else (y1 * outScale).toInt()
            val k2 = model / outScale
            // v44.18: al reducir mucho (Original = 4 px del modelo por píxel) se promedia el bloque
            // entero (más limpio que tomar un punto suelto: sin dientes ni brillos).
            val box = (k2 + .5f).toInt()
            if (box >= 2) {
                for (oy in oy0 until oy1) for (ox in ox0 until ox1) {
                    val bx = ((ox * k2) - px0 * model).toInt().coerceIn(0, rw - box)
                    val by = ((oy * k2) - py0 * model).toInt().coerceIn(0, rh - box)
                    var c = 0xFF shl 24
                    for (ch in 0..2) {
                        val o = ch * rn
                        var s = 0f
                        for (yy in 0 until box) { val row = o + (by + yy) * rw + bx; for (xx in 0 until box) s += res[row + xx] }
                        val v = ((s / (box * box)).coerceIn(0f, 1f) * 255f + .5f).toInt()
                        c = c or (v shl (16 - 8 * ch))
                    }
                    out[oy * ow + ox] = c
                }
                onTile(k + 1, list.size)
                continue
            }
            for (oy in oy0 until oy1) {
                // Centro del píxel de salida en coordenadas de la salida del modelo (relativas a la baldosa).
                val sy = ((oy + .5f) * k2 - .5f - py0 * model).coerceIn(0f, (rh - 1).toFloat())
                val ya = sy.toInt(); val yb = min(ya + 1, rh - 1); val ty = sy - ya
                for (ox in ox0 until ox1) {
                    val sx = ((ox + .5f) * k2 - .5f - px0 * model).coerceIn(0f, (rw - 1).toFloat())
                    val xa = sx.toInt(); val xb = min(xa + 1, rw - 1); val tx = sx - xa
                    var c = 0xFF shl 24
                    for (ch in 0..2) {
                        val o = ch * rn
                        val a = res[o + ya * rw + xa] + (res[o + ya * rw + xb] - res[o + ya * rw + xa]) * tx
                        val b = res[o + yb * rw + xa] + (res[o + yb * rw + xb] - res[o + yb * rw + xa]) * tx
                        val v = ((a + (b - a) * ty).coerceIn(0f, 1f) * 255f + .5f).toInt()
                        c = c or (v shl (16 - 8 * ch))
                    }
                    out[oy * ow + ox] = c
                }
            }
            onTile(k + 1, list.size)
        }
        return Triple(out, ow, oh)
    }
}

/**
 * Real-ESRGAN realesr-animevideov3 (BSD-3) convertido a ONNX con tools/banco_v44/pth_to_onnx.py
 * (verificado contra NumPy). x4, entrada RGB 0..1 de cualquier tamaño. Va dentro del APK (2,5 MB).
 */
class SuperResolver(private val ctx: Context) {

    val available: Boolean get() =
        try { ctx.assets.list("models")?.contains(ASSET) == true } catch (_: Exception) { false }

    /** Escribe la imagen mejorada (PNG) y devuelve su tamaño. Cancelable entre baldosas. */
    suspend fun enhance(src: Bitmap, out: File, scale: Int = SCALE, onProgress: (Float) -> Unit): Pair<Int, Int> = withContext(Dispatchers.Default) {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val model = modelFile()
        val env = OrtEnvironment.getEnvironment()
        val t0 = System.nanoTime()
        val res = OrtSession.SessionOptions().use { opt ->
            opt.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            opt.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            env.createSession(model.path, opt).use { s ->
                TileUpscale.upscale(px, w, h, SCALE, TileUpscale.targetScale(w, h, scale), run = { chw, tw, th ->
                    coroutineContext.ensureActive()
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, th.toLong(), tw.toLong())).use { t ->
                        s.run(mapOf("input" to t)).use { r ->
                            val fb = (r[0] as OnnxTensor).floatBuffer
                            FloatArray(fb.remaining()).also { fb.get(it) }
                        }
                    }
                }, onTile = { k, n -> onProgress(k.toFloat() / n) })
            }
        }
        val bmp = Bitmap.createBitmap(res.first, res.second, res.third, Bitmap.Config.ARGB_8888)
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        if (!tmp.renameTo(out)) throw IllegalStateException("No se pudo guardar la imagen mejorada")
        ZetaLog.info("superres", "${w}x$h → ${res.second}x${res.third} en ${(System.nanoTime() - t0) / 1_000_000} ms")
        res.second to res.third
    }

    /**
     * v44.17 · El modelo va DENTRO del APK (2,5 MB, sin descargas). ONNX Runtime necesita una
     * ruta: se copia una vez al almacenamiento interno y se reutiliza.
     */
    private fun modelFile(): File {
        val dir = File(ctx.noBackupFilesDir, "models").apply { mkdirs() }
        val f = File(dir, ASSET)
        val expected = try { ctx.assets.openFd("models/$ASSET").use { it.length } } catch (_: Exception) { -1L }
        if (f.isFile && (expected < 0 || f.length() == expected)) return f
        val tmp = File(dir, "$ASSET.part")
        ctx.assets.open("models/$ASSET").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        if (!tmp.renameTo(f)) throw IllegalStateException("No se pudo preparar $ASSET")
        return f
    }

    companion object {
        const val ASSET = "realesr_animevideov3_x4.onnx"
        const val SCALE = 4
        const val FILE = "image_sr.png"
    }
}
