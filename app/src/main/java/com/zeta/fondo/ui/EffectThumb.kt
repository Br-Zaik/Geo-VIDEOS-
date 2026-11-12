package com.zeta.fondo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Miniaturas REALES de los efectos: tiras de 8 cuadros renderizadas con el mismo
 * shader de la app (assets/thumbs/<id>.webp, ~10 KB cada una). Se decodifican solo
 * cuando la tarjeta es visible, en RGB_565 (la mitad de memoria) y con caché LRU.
 */
object ThumbCache {
    // v44.16: caché por BYTES (16 MB ≈ todas las tiras en RGB_565). Antes 40 tiras: al entrar en
    // Efectos se volvían a decodificar y cada tarjeta parpadeaba (dibujo provisional → miniatura).
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
    }
    private val missing = HashSet<String>()

    /** Ya decodificada (sin esperar): la tarjeta nace con su miniatura, sin parpadeo. */
    fun peek(id: String): ImageBitmap? = cache.get(id)

    /** v44.25 · Android pide memoria: se sueltan (se vuelven a leer al verlas). */
    fun trim() = cache.evictAll()

    @Volatile private var available: Set<String>? = null

    /** v44.17 · ¿Hay tira para este id? (lista de assets leída una vez). */
    fun has(ctx: Context, id: String): Boolean {
        val set = available ?: (try { ctx.assets.list("thumbs")?.map { it.removeSuffix(".webp") }?.toSet() } catch (_: Exception) { null }
            ?: emptySet()).also { available = it }
        return id in set
    }

    suspend fun load(ctx: Context, id: String): ImageBitmap? = withContext(Dispatchers.IO) { loadNow(ctx, id) }

    private fun loadNow(ctx: Context, id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        synchronized(missing) { if (id in missing) return null }
        return try {
            ctx.assets.open("thumbs/$id.webp").use { input ->
                val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeStream(input, null, o)?.asImageBitmap()?.also { cache.put(id, it) }
            }
        } catch (_: Exception) {
            synchronized(missing) { missing.add(id) }
            null
        }
    }

    /** v44.16 · Decodifica por adelantado (en segundo plano, al abrir el editor) las miniaturas. */
    suspend fun prewarm(ctx: Context, ids: List<String>) = withContext(Dispatchers.IO) {
        for (id in ids) { loadNow(ctx, id); kotlinx.coroutines.yield() }
    }
}

private val ThumbPlaceholder = androidx.compose.ui.graphics.Color(0xFF23242C)

/** Reloj compartido a ~6 fps para todas las miniaturas visibles (no a 60 fps). */
@Composable
fun rememberThumbTick(): () -> Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(160L); tick++ }
    }
    return { tick }
}

@Composable
fun EffectThumb(id: String, animate: Boolean, tick: () -> Int, glyphTime: () -> Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState(ThumbCache.peek(id), id) { value = ThumbCache.peek(id) ?: ThumbCache.load(ctx, id) }
    val b = bmp
    if (b == null) {
        // v44.17: si la miniatura existe y aún se está leyendo, la tarjeta queda lisa un instante
        // (antes se dibujaba el icono provisional y luego saltaba a la imagen: el «flash»).
        if (ThumbCache.has(ctx, id)) Box(modifier.background(ThumbPlaceholder))
        else Box(modifier) { EffectGlyph(id, glyphTime, animate, modifier) }
        return
    }
    Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
        val frames = (b.width / b.height.coerceAtLeast(1)).coerceAtLeast(1)
        val f = if (animate) tick() % frames else frames / 2
        // Recorte centrado para llenar la tarjeta sin deformar.
        val side = b.height
        val dstW = size.width.toInt().coerceAtLeast(1)
        val dstH = size.height.toInt().coerceAtLeast(1)
        val ratio = dstW.toFloat() / dstH
        val srcW = if (ratio >= 1f) side else (side * ratio).toInt().coerceAtLeast(1)
        val srcH = if (ratio >= 1f) (side / ratio).toInt().coerceAtLeast(1) else side
        drawImage(b, srcOffset = IntOffset(f * side + (side - srcW) / 2, (side - srcH) / 2),
            srcSize = IntSize(srcW, srcH), dstSize = IntSize(dstW, dstH))
    }
}
