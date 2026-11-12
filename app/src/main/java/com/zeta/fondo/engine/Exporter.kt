package com.zeta.fondo.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface
import com.zeta.fondo.export.Gallery
import com.zeta.fondo.model.ExportSettings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExportCancelled : Exception("Exportación cancelada")

class ExportRequest(
    val imageFile: File,
    val imgW: Int,
    val imgH: Int,
    val name: String,
    val settings: ExportSettings,
    val params: RenderParams,
    val flow: FlowField.Data?,
    val masks: MaskLayers,
    val characterFile: File? = null,
    val boltCount: Int = 0,
    /** v45: mapa de profundidad IA (el mismo de la vista previa; null = 2.5D manual). */
    val depth: DepthMap? = null,
    /** v46: placa de fondo del 2.5D (la misma de la vista previa). */
    val plate: PlateMap? = null,
    /** v44.23: capas de imagen (con sus archivos; se decodifican a resolución de exportación). */
    val layers: LayerStack? = null
)

data class ExportResult(
    val isPng: Boolean,
    val file: File?,
    val galleryPath: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val frames: Int,
    val bitrate: Int,
    val rotated: Boolean,
    val reducedFrom: String?
)

object ExportPlanner {
    val resLabels = listOf("Original", "4K UHD", "2K", "1080p", "720p", "QHD/1440p", "Personalizada")
    private val resLong = intArrayOf(0, 3840, 2048, 1920, 1280, 2560)
    val framingLabels = listOf("Completo", "9:16", "16:9", "1:1", "3:4", "4:3")
    val qualityLabels = listOf("Alta", "Máxima", "Extrema")
    private val bpp = floatArrayOf(0.25f, 0.40f, 0.60f)

    /** Recorte en coordenadas de imagen: [offX, offY, escalaX, escalaY]. */
    fun crop(imgW: Int, imgH: Int, framing: Int, pos: Float): FloatArray {
        val ia = imgW.toFloat() / imgH
        val ta = when (framing) {
            1 -> 9f / 16f
            2 -> 16f / 9f
            3 -> 1f
            4 -> 3f / 4f
            5 -> 4f / 3f
            else -> ia
        }
        return if (ia > ta + 1e-4f) {
            val sx = ta / ia
            floatArrayOf((1f - sx) * pos, 0f, sx, 1f)
        } else if (ia < ta - 1e-4f) {
            val sy = ia / ta
            floatArrayOf(0f, (1f - sy) * pos, 1f, sy)
        } else {
            floatArrayOf(0f, 0f, 1f, 1f)
        }
    }

    fun targetAspect(imgW: Int, imgH: Int, s: ExportSettings): Float {
        if (s.res == 6) return s.customW.toFloat() / s.customH.coerceAtLeast(1)
        return when (s.framing) {
            1 -> 9f / 16f
            2 -> 16f / 9f
            3 -> 1f
            4 -> 3f / 4f
            5 -> 4f / 3f
            else -> imgW.toFloat() / imgH.coerceAtLeast(1)
        }
    }

    /** Tamaño máximo del recorte (sin zoom) en fracción de la imagen: [ancho, alto]. */
    fun baseCropSize(imgW: Int, imgH: Int, s: ExportSettings): FloatArray {
        val ia = imgW.toFloat() / imgH.coerceAtLeast(1)
        val ta = targetAspect(imgW, imgH, s)
        return if (ia > ta + 1e-4f) floatArrayOf(ta / ia, 1f)
        else if (ia < ta - 1e-4f) floatArrayOf(1f, ia / ta)
        else floatArrayOf(1f, 1f)
    }

    /** Recorte libre: centro (cropCx, cropCy) y zoom; compatible con el antiguo cropPos. */
    fun cropForSettings(imgW: Int, imgH: Int, s: ExportSettings): FloatArray {
        val base = baseCropSize(imgW, imgH, s)
        val z = s.cropZoom.coerceIn(1f, 6f)
        val sx = base[0] / z
        val sy = base[1] / z
        val cx = if (s.cropCx >= 0f) s.cropCx else if (base[0] < 1f) base[0] / 2f + (1f - base[0]) * s.cropPos else 0.5f
        val cy = if (s.cropCy >= 0f) s.cropCy else if (base[1] < 1f) base[1] / 2f + (1f - base[1]) * s.cropPos else 0.5f
        val ox = (cx - sx / 2f).coerceIn(0f, (1f - sx).coerceAtLeast(0f))
        val oy = (cy - sy / 2f).coerceIn(0f, (1f - sy).coerceAtLeast(0f))
        return floatArrayOf(ox, oy, sx, sy)
    }

    fun even(v: Int): Int = max(16, v - v % 2)

    /** Tamaño pedido (antes de revisar lo que soporta el codificador del teléfono). */
    fun targetSize(imgW: Int, imgH: Int, s: ExportSettings): Pair<Int, Int> {
        val c = cropForSettings(imgW, imgH, s)
        val cw = imgW * c[2]
        val ch = imgH * c[3]
        val longC = max(cw, ch)
        if (s.res == 6) return even(s.customW) to even(s.customH)
        val long = if (s.res == 0) longC else resLong[s.res.coerceIn(0, 5)].toFloat()
        val k = long / longC
        return even((cw * k).roundToInt()) to even((ch * k).roundToInt())
    }

    fun bitrate(w: Int, h: Int, fps: Int, quality: Int): Int {
        val b = w.toDouble() * h * fps * bpp[quality.coerceIn(0, 2)]
        return b.coerceIn(2_000_000.0, 100_000_000.0).toInt()
    }
}

/**
 * Renderiza cada cuadro con el mismo shader de la vista previa, a la resolución
 * de salida, directo a la superficie del codificador H.264 (sin pasar por pantalla).
 * Debe ejecutarse de principio a fin en un solo hilo (bloqueante).
 */
/** No mostrar codecs que el dispositivo NO tiene como encoder. */
object CodecSupport {
    val names = listOf("H.264 · compatible", "HEVC · menor peso", "AV1 · moderno")
    val mimes = listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC, "video/av01")
    private val usableCache = HashMap<Int, Boolean>()
    private val planCache = HashMap<String, Triple<Int, Int, Boolean>?>()

    fun usable(index: Int): Boolean = synchronized(this) {
        usableCache.getOrPut(index) {
            if (index !in mimes.indices || (index == 2 && Build.VERSION.SDK_INT < 34)) false
            else try {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(mimes[index], ignoreCase = true) }
                }
            } catch (_: Exception) { index == 0 }
        }
    }

    private fun software(i: MediaCodecInfo): Boolean = i.isSoftwareOnly

    private fun alignDown(v: Int, a: Int): Int = max(a, v / a * a)

    private fun ok(vc: MediaCodecInfo.VideoCapabilities, w: Int, h: Int, fps: Int): Boolean =
        try { vc.areSizeAndRateSupported(w, h, fps.toDouble()) } catch (_: Exception) { false }

    /**
     * Tamaño que REALMENTE se exportará con el mejor codificador del teléfono
     * (hardware primero): (ancho, alto, guardadoGirado). null si no hay codificador.
     * La exportación no es en tiempo real, así que basta con que el tamaño y el fps
     * sean aceptados; si no, se reduce manteniendo la proporción.
     */
    fun plan(mime: String, w0: Int, h0: Int, fps: Int): Triple<Int, Int, Boolean>? = synchronized(this) {
        planCache.getOrPut("$mime/$w0/$h0/$fps") {
            val infos = try {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                }.sortedBy { if (software(it)) 1 else 0 }
            } catch (_: Exception) { emptyList() }
            var best: Triple<Int, Int, Boolean>? = null
            for (info in infos) {
                val vc = try { info.getCapabilitiesForType(mime).videoCapabilities } catch (_: Exception) { null } ?: continue
                val wa = max(2, vc.widthAlignment)
                val ha = max(2, vc.heightAlignment)
                var f = 1f
                var found: Triple<Int, Int, Boolean>? = null
                for (step in 0 until 80) {
                    val w = alignDown((w0 * f).roundToInt(), wa)
                    val h = alignDown((h0 * f).roundToInt(), ha)
                    if (ok(vc, w, h, fps)) { found = Triple(w, h, false); break }
                    val rw = alignDown((h0 * f).roundToInt(), wa)
                    val rh = alignDown((w0 * f).roundToInt(), ha)
                    if (w0 != h0 && ok(vc, rw, rh, fps)) { found = Triple(rw, rh, true); break }
                    f *= 0.93f
                }
                if (found != null && (best == null || found.first * found.second > best.first * best.second)) best = found
                if (found != null && found.first * found.second >= w0 * h0 * 0.98f) break
            }
            best
        }
    }

    /** Tamaño mostrado (sin la rotación de almacenamiento). */
    fun displaySize(codec: Int, w: Int, h: Int, fps: Int): Pair<Int, Int> {
        val p = plan(mimes[codec.coerceIn(0, 2)], w, h, fps) ?: return w to h
        return if (p.third) p.second to p.first else p.first to p.second
    }
}

class Exporter(
    private val ctx: Context,
    private val vertSrc: String,
    private val fragSrc: String
) {

    fun run(req: ExportRequest, cancel: AtomicBoolean, progress: (Float) -> Unit): ExportResult =
        if (req.settings.format == 1) runPng(req, cancel, progress) else runVideo(req, cancel, progress)

    private fun alignDown(v: Int, a: Int): Int = max(a, v / a * a)

    /**
     * Prefiere un codificador que acepte el tamaño EXACTO (por ejemplo 1080×1920).
     * Primero los de hardware; si ninguno lo acepta, uno por software. Si nadie, null.
     */
    private fun pickExactEncoder(mime: String, w: Int, h: Int, fps: Int): String? {
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        } catch (e: Exception) { return null }
        fun software(i: MediaCodecInfo): Boolean = i.isSoftwareOnly
        for (info in infos.sortedBy { if (software(it)) 1 else 0 }) {
            val vc = try { info.getCapabilitiesForType(mime).videoCapabilities } catch (e: Exception) { null } ?: continue
            val wa = max(1, vc.widthAlignment)
            val ha = max(1, vc.heightAlignment)
            if (w % wa == 0 && h % ha == 0 && sizeOk(vc, w, h, fps)) return info.name
            if (w != h && h % wa == 0 && w % ha == 0 && sizeOk(vc, h, w, fps) && !software(info)) return info.name
        }
        return null
    }

    private fun sizeOk(vc: MediaCodecInfo.VideoCapabilities, w: Int, h: Int, fps: Int): Boolean =
        try {
            vc.areSizeAndRateSupported(w, h, fps.toDouble())
        } catch (e: Exception) {
            false
        }

    /** Busca el tamaño más grande que el codificador acepta. Devuelve (anchoCod, altoCod, rotado). */
    private fun fitSize(vc: MediaCodecInfo.VideoCapabilities, w0: Int, h0: Int, fps: Int): Triple<Int, Int, Boolean> {
        val wa = max(2, vc.widthAlignment)
        val ha = max(2, vc.heightAlignment)
        var f = 1f
        repeat(80) {
            val w = alignDown((w0 * f).roundToInt(), wa)
            val h = alignDown((h0 * f).roundToInt(), ha)
            if (sizeOk(vc, w, h, fps)) return Triple(w, h, false)
            // Muchos teléfonos codifican 3840x2160 pero no 2160x3840: se guarda en
            // horizontal con marca de rotación (igual que la cámara).
            val rw = alignDown((h0 * f).roundToInt(), wa)
            val rh = alignDown((w0 * f).roundToInt(), ha)
            if (w0 != h0 && sizeOk(vc, rw, rh, fps)) return Triple(rw, rh, true)
            f *= 0.93f
        }
        return Triple(alignDown(1280, wa), alignDown(720, ha), h0 > w0)
    }

    private fun decodeForOutput(req: ExportRequest, dispW: Int, dispH: Int, crop: FloatArray, maxTex: Int): Bitmap {
        val needW = dispW / crop[2]
        val needH = dispH / crop[3]
        val r = max(needW / req.imgW, needH / req.imgH)
        val long = max(req.imgW, req.imgH)
        val target = min(maxTex.toFloat(), long * min(1f, r)).let { ceil(it).toInt() }.coerceAtLeast(256)
        return BitmapUtil.decodeAtMost(req.imageFile, min(target, maxTex))
    }

    private fun uploadAll(gl: FondoGl, req: ExportRequest, bmp: Bitmap) {
        gl.uploadImage(bmp)
        val appearOn = (req.params.studio["uAppear"]?.getOrNull(0) ?: 0f) > 0f
        val cf = req.characterFile
        if (appearOn && cf != null && cf.isFile) {
            val ch = BitmapUtil.decodeAtMost(cf, 2048)
            gl.uploadCharacter(ch)
            ch.recycle()
        }
        req.flow?.let { gl.uploadFlow(it) }
        req.depth?.let { gl.uploadDepth(it.w, it.h, it.map) }
        req.plate?.let { gl.uploadPlate(it.w, it.h, it.argb) }
        synchronized(req.masks.lock) {
            for (i in req.masks.bitmaps.indices) gl.uploadMask(i, req.masks.bitmaps[i])
        }
        // v44.23: capas de imagen, una a una (decodificar → subir → reciclar: poca memoria).
        val st = req.layers
        if (st != null && !st.isEmpty) {
            val side = min(gl.maxTextureSize(), com.zeta.fondo.model.ImageLayers.maxSide(false, true))
            val uploaded = HashMap<String, Bitmap>()
            for (l in st.visible) {
                if (l.file in uploaded) continue
                val f = st.files[l.file] ?: continue
                if (!f.isFile) continue
                val b = try { BitmapUtil.decodeAtMost(f, side) } catch (_: Throwable) { null } ?: continue
                try { gl.uploadLayer(l.file, b) } finally { b.recycle() }
                uploaded[l.file] = b
            }
            gl.layerStack = st.withBitmaps(uploaded)
        }
    }

    // ------------------------------------------------------------------ Vídeo

    private fun makeFormat(mime: String, w: Int, h: Int, fps: Int, bitrate: Int, profile: MediaCodecInfo.CodecProfileLevel?): MediaFormat =
        MediaFormat.createVideoFormat(mime, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (profile != null) {
                setInteger(MediaFormat.KEY_PROFILE, profile.profile)
                setInteger(MediaFormat.KEY_LEVEL, profile.level)
            }
        }

    private fun runVideo(req: ExportRequest, cancel: AtomicBoolean, progress: (Float) -> Unit): ExportResult {
        val s = req.settings
        val fps = s.fps
        val (reqW, reqH) = ExportPlanner.targetSize(req.imgW, req.imgH, s)
        val crop = ExportPlanner.cropForSettings(req.imgW, req.imgH, s)

        val outDir = File(ctx.cacheDir, "exports")
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() } // solo guardamos el último
        val fileName = req.name + ".mp4"
        val outFile = File(outDir, fileName)

        val codecIndex = s.codec.coerceIn(0, 2)
        if (!CodecSupport.usable(codecIndex)) {
            throw IllegalStateException("Este teléfono no tiene codificador ${CodecSupport.names[codecIndex]}. Elige H.264.")
        }
        // Codificar primero el sonido permite declarar ambas pistas ANTES de iniciar MediaMuxer.
        // Los paquetes AAC se escriben después de conocer el formato de la pista de vídeo.
        val musicFile = File(req.imageFile.parentFile, ImportedAudio.FILE_NAME)
        val sound = if (SoundMixer.hasSound(s, musicFile)) {
            ImportedAudio.encode(musicFile, s, cancel, req.boltCount)
        } else null
        val mime = CodecSupport.mimes[codecIndex]
        val exactName = pickExactEncoder(mime, reqW, reqH, fps)
        val codec = if (exactName != null) {
            try { MediaCodec.createByCodecName(exactName) } catch (e: Exception) { MediaCodec.createEncoderByType(mime) }
        } else MediaCodec.createEncoderByType(mime)
        var egl: EglCore? = null
        var eglSurface: EGLSurface? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerStopped = false
        var gl: FondoGl? = null
        var bmp: Bitmap? = null

        try {
            val caps = codec.codecInfo.getCapabilitiesForType(mime)
            val vc = caps.videoCapabilities
            val (encW, encH, rotate) = fitSize(vc, reqW, reqH, fps)
            val dispW = if (rotate) encH else encW
            val dispH = if (rotate) encW else encH
            val reduced = if (dispW * dispH < reqW * reqH * 0.98f) "${reqW}×$reqH" else null

            var bitrate = ExportPlanner.bitrate(encW, encH, fps, s.quality)
            bitrate = bitrate.coerceIn(vc.bitrateRange.lower, vc.bitrateRange.upper)

            val high = if (codecIndex == 0) caps.profileLevels
                .filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
                .maxByOrNull { it.level } else null
            // Algunos codificadores rechazan perfiles altos o tasas extremas aunque las
            // anuncien. Se prueba de mayor a menor exigencia antes de rendirse.
            var configured = false
            val attempts = listOfNotNull(
                high?.let { it to bitrate },
                null to bitrate,
                null to (bitrate * 0.6f).toInt().coerceAtLeast(vc.bitrateRange.lower),
                null to (bitrate * 0.35f).toInt().coerceAtLeast(vc.bitrateRange.lower)
            )
            var lastError: Exception? = null
            for ((profile, rate) in attempts) {
                try {
                    codec.configure(makeFormat(mime, encW, encH, fps, rate, profile), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    configured = true
                    bitrate = rate
                    break
                } catch (e: Exception) {
                    lastError = e
                    com.zeta.fondo.diag.ZetaLog.warn("MediaCodec", "configure ${encW}x$encH $fps fps $rate bps perfil=$profile rechazado", e)
                    try { codec.reset() } catch (_: Exception) { }
                }
            }
            if (!configured) throw IllegalStateException(
                "El codificador no aceptó ${encW}×$encH a $fps fps. Prueba una resolución menor.", lastError)

            val surf = codec.createInputSurface()
            inputSurface = surf
            val core = EglCore(true)
            egl = core
            val es = core.createWindowSurface(surf)
            eglSurface = es
            core.makeCurrent(es)

            val g = FondoGl(vertSrc, fragSrc)
            g.init()
            gl = g
            val b = decodeForOutput(req, dispW, dispH, crop, g.maxTextureSize())
            bmp = b
            uploadAll(g, req, b)
            b.recycle()
            bmp = null

            codec.start()
            val mux = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            if (rotate) mux.setOrientationHint(90)

            val info = MediaCodec.BufferInfo()
            var track = -1
            var audioTrack = -1
            var audioPacketIndex = 0

            fun writeAudioThrough(pts: Long) {
                val packets = sound?.packets ?: return
                while (audioTrack >= 0 && audioPacketIndex < packets.size &&
                    packets[audioPacketIndex].info.presentationTimeUs <= pts) {
                    val packet = packets[audioPacketIndex++]
                    mux.writeSampleData(audioTrack, ByteBuffer.wrap(packet.bytes), packet.info)
                }
            }

            fun drain(endOfStream: Boolean) {
                if (endOfStream) codec.signalEndOfInputStream()
                var idle = 0
                while (true) {
                    val idx = codec.dequeueOutputBuffer(info, 10_000)
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) return
                        idle++
                        if (idle > 500) return
                    } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            track = mux.addTrack(codec.outputFormat)
                            audioTrack = sound?.let { mux.addTrack(it.format) } ?: -1
                            mux.start()
                            muxerStarted = true
                        }
                    } else if (idx >= 0) {
                        val buf = codec.getOutputBuffer(idx)
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (buf != null && !isConfig && info.size > 0 && muxerStarted) {
                            writeAudioThrough(info.presentationTimeUs)
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            mux.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }

            // Misma rejilla temporal que la vista previa (LoopClock): t = i / N y el
            // cuadro N (igual al 0) no se escribe, así el bucle empalma sin salto.
            val frames = LoopClock.frameCount(s.loopSec.toFloat(), fps)
            val total = frames * s.repeats
            for (i in 0 until total) {
                if (cancel.get()) throw ExportCancelled()
                drain(false)
                val t = LoopClock.phase(i, frames)
                g.draw(req.params, t, encW, encH, crop, false, rotate, false, s.overlayOnly)
                core.setPresentationTime(es, i.toLong() * 1_000_000_000L / fps)
                if (!core.swap(es)) throw RuntimeException("El codificador dejó de aceptar cuadros (${i + 1}/$total).")
                progress((i + 1f) / total * 0.97f)
            }
            drain(true)
            if (muxerStarted) {
                writeAudioThrough(Long.MAX_VALUE)
                mux.stop()
                muxerStopped = true
            }
            if (!muxerStarted) throw RuntimeException("El codificador no produjo vídeo")

            progress(0.98f)
            val path = Gallery.saveVideo(ctx, outFile, fileName)
            progress(1f)
            return ExportResult(false, outFile, path, dispW, dispH, fps, total, bitrate, rotate, reduced)
        } catch (e: Throwable) {
            if (e !is ExportCancelled) {
                outFile.delete()
                com.zeta.fondo.diag.ZetaLog.error("Exportar", "Falló la exportación de vídeo", e)
            }
            throw e
        } finally {
            try { codec.stop() } catch (ignored: Exception) { }
            try { codec.release() } catch (ignored: Exception) { }
            try { if (muxerStarted && !muxerStopped) muxer?.stop() } catch (ignored: Exception) { }
            try { muxer?.release() } catch (ignored: Exception) { }
            try { gl?.release() } catch (ignored: Exception) { }
            val core = egl
            val es = eglSurface
            if (core != null) {
                if (es != null) try { core.releaseSurface(es) } catch (ignored: Exception) { }
                try { core.release() } catch (ignored: Exception) { }
            }
            try { inputSurface?.release() } catch (ignored: Exception) { }
            bmp?.recycle()
            if (cancel.get()) outFile.delete()
        }
    }

    // ------------------------------------------------------------------ PNG

    /**
     * v44.16 · «Captura»: guarda SOLO el cuadro del instante [t] (0..1 del bucle) como PNG en la
     * Galería, con la misma calidad y efectos que la exportación.
     */
    fun still(req: ExportRequest, t: Float, fileName: String): ExportResult =
        runPng(req, AtomicBoolean(false), {}, t.coerceIn(0f, 1f), fileName)

    private fun runPng(req: ExportRequest, cancel: AtomicBoolean, progress: (Float) -> Unit,
                       stillT: Float? = null, stillName: String? = null): ExportResult {
        val s = req.settings
        var (w, h) = ExportPlanner.targetSize(req.imgW, req.imgH, s)
        val crop = ExportPlanner.cropForSettings(req.imgW, req.imgH, s)
        val core = EglCore(false)
        var pb: EGLSurface? = null
        var gl: FondoGl? = null
        var frameBmp: Bitmap? = null
        val fbo = IntArray(1)
        val colorTex = IntArray(1)
        var reduced: String? = null
        try {
            val p = core.createPbuffer(16, 16)
            pb = p
            core.makeCurrent(p)
            val g = FondoGl(vertSrc, fragSrc)
            g.init()
            gl = g
            val maxTex = g.maxTextureSize()
            if (max(w, h) > maxTex) {
                reduced = "${w}×$h"
                val k = maxTex.toFloat() / max(w, h)
                w = ExportPlanner.even((w * k).toInt())
                h = ExportPlanner.even((h * k).toInt())
            }
            val b = decodeForOutput(req, w, h, crop, maxTex)
            uploadAll(g, req, b)
            b.recycle()

            // Textura de salida + framebuffer
            GLES30.glGenTextures(1, colorTex, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + 5)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex[0])
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, w, h)
            GLES30.glGenFramebuffers(1, fbo, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorTex[0], 0)
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) throw RuntimeException("No se pudo preparar el cuadro ${w}×$h (0x${Integer.toHexString(status)})")

            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            val fb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            frameBmp = fb
            val frames = if (stillT != null) 1 else LoopClock.frameCount(s.loopSec.toFloat(), s.fps)
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            for (i in 0 until frames) {
                if (cancel.get()) throw ExportCancelled()
                val t = stillT ?: LoopClock.phase(i, frames)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
                g.draw(req.params, t, w, h, crop, true, false, false, s.overlayOnly)
                buf.position(0)
                GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                buf.position(0)
                fb.copyPixelsFromBuffer(buf)
                Gallery.savePng(ctx, fb, req.name, stillName ?: String.format(java.util.Locale.US, "frame_%04d.png", i + 1))
                progress((i + 1f) / frames)
            }
            return ExportResult(
                true, null, "Pictures/${Gallery.FOLDER}/${req.name}/", w, h, s.fps, frames, 0, false, reduced
            )
        } finally {
            try {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                if (fbo[0] != 0) GLES30.glDeleteFramebuffers(1, fbo, 0)
                if (colorTex[0] != 0) GLES30.glDeleteTextures(1, colorTex, 0)
            } catch (ignored: Exception) { }
            try { gl?.release() } catch (ignored: Exception) { }
            val p = pb
            if (p != null) try { core.releaseSurface(p) } catch (ignored: Exception) { }
            try { core.release() } catch (ignored: Exception) { }
            frameBmp?.recycle()
        }
    }

    companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }
}
