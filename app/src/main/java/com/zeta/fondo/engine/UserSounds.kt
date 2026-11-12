package com.zeta.fondo.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * v44 · Sonidos propios del usuario (sus grabaciones), decodificados una vez a PCM en
 * <proyecto>/sounds/<id>.pcm + <id>.txt ("frecuencia,canales,nombre"). Al usarlos se pasan a
 * estéreo, a la frecuencia de la mezcla y con un fundido cruzado en el empalme para poder
 * repetirse sin clic. Caché pequeña en memoria.
 */
object UserSounds {
    const val DIR = "sounds"
    const val MAX_SECONDS = 60

    data class Info(val id: String, val name: String, val rate: Int, val channels: Int)

    fun list(projectDir: File): List<Info> {
        val dir = File(projectDir, DIR)
        return dir.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }.mapNotNull { meta ->
            val parts = meta.readText().split(',', limit = 3)
            val rate = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val ch = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            Info(meta.nameWithoutExtension, parts.getOrNull(2)?.trim().orEmpty().ifBlank { "Sonido" }, rate, ch)
        }
    }

    private val cache = object : LinkedHashMap<String, ShortArray>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortArray>?): Boolean = size > 6
    }

    /** v44.25 · Android pide memoria: se suelta la caché (se vuelve a leer del disco). */
    @Synchronized
    fun trim() = cache.clear()

    /** Estéreo intercalado a [rate] Hz, preparado para repetirse sin clic (null si falta). */
    @Synchronized
    fun load(projectDir: String, id: String, rate: Int): ShortArray? {
        val pcm = File(File(projectDir, DIR), "$id.pcm")
        val meta = File(File(projectDir, DIR), "$id.txt")
        if (!pcm.isFile || !meta.isFile) return null
        val key = "${pcm.path}|${pcm.lastModified()}|$rate"
        cache[key]?.let { return it }
        val parts = meta.readText().split(',')
        val srcRate = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val ch = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(1, 2) ?: return null
        val bytes = pcm.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val frames = bytes.size / (2 * ch)
        val l = FloatArray(frames); val r = FloatArray(frames)
        for (i in 0 until frames) {
            l[i] = bb.getShort((i * ch) * 2) / 32768f
            r[i] = if (ch == 2) bb.getShort((i * ch + 1) * 2) / 32768f else l[i]
        }
        val out = loopable(resample(l, srcRate, rate), resample(r, srcRate, rate), rate)
        cache[key] = out
        return out
    }

    /** Remuestreo lineal (suficiente para ambientes). */
    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || x.isEmpty()) return x
        val n = (x.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val k = from.toDouble() / to
        for (i in 0 until n) {
            val p = i * k
            val a = p.toInt().coerceAtMost(x.size - 1)
            val b = (a + 1).coerceAtMost(x.size - 1)
            val f = (p - a).toFloat()
            out[i] = x[a] + (x[b] - x[a]) * f
        }
        return out
    }

    /** Funde el final con el principio (igual potencia, ≤0,25 s): al repetirse no hay clic. */
    fun loopable(l: FloatArray, r: FloatArray, rate: Int): ShortArray {
        val n = l.size
        val fade = minOf(n / 4, rate / 4).coerceAtLeast(0)
        val len = (n - fade).coerceAtLeast(1)
        val out = ShortArray(len * 2)
        for (i in 0 until len) {
            var a = l[i]; var b = r[i]
            if (i < fade) {
                val t = i.toFloat() / fade
                val wi = kotlin.math.sqrt(t); val wo = kotlin.math.sqrt(1f - t)
                a = l[i] * wi + l[len + i] * wo
                b = r[i] * wi + r[len + i] * wo
            }
            out[i * 2] = (a.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out[i * 2 + 1] = (b.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }
}
