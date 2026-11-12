package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.engine.ImportedAudio
import com.zeta.fondo.model.ExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Picos de la pista PCM, calculados con lecturas limitadas para no bloquear el editor. */
internal fun sampleWaveform(file: File, totalFrames: Long, channels: Int, count: Int = 128): FloatArray {
    val result = FloatArray(count)
    if (!file.isFile || totalFrames <= 0L || channels !in 1..2) return result
    RandomAccessFile(file, "r").use { pcm ->
        val bytesPerFrame = 2L * channels
        val frames = min(totalFrames, pcm.length() / bytesPerFrame)
        if (frames <= 0L) return result
        val chunk = ByteArray(128 * channels * 2)
        for (i in result.indices) {
            val mid = ((i.toDouble() + 0.5) * frames / count).toLong().coerceIn(0L, frames - 1L)
            val start = (mid - 64L).coerceIn(0L, (frames - 128L).coerceAtLeast(0L))
            pcm.seek(start * bytesPerFrame)
            val bytes = pcm.read(chunk, 0, min(chunk.size.toLong(), (frames - start) * bytesPerFrame).toInt())
            var peak = 0
            var k = 0
            while (k + 1 < bytes) {
                val value = (chunk[k].toInt() and 255) or (chunk[k + 1].toInt() shl 8)
                val signed = value.toShort().toInt()
                peak = max(peak, abs(signed))
                k += 2
            }
            result[i] = (peak / 32768f).coerceIn(.025f, 1f)
        }
    }
    return result
}

private fun timeLabel(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toLong()
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

private const val PEAKS = 4096

/**
 * Selector de fragmento para canciones largas:
 * - Arrastra el cuadro morado para mover la parte que suena (si llegas al borde, la onda avanza).
 * - Arrastra fuera del cuadro para recorrer la canción; toca para llevar el cuadro ahí.
 * - Pellizca para acercar o alejar. La barra de abajo muestra la canción completa.
 */
@Composable
fun AudioTrim(vm: AppViewModel, e: ExportSettings) {
    val meta = vm.meta ?: return
    val total = e.audioFrames
    if (total <= 0L) return
    val rate = e.audioRate.coerceAtLeast(1)
    val length = min(total, e.loopSec.toLong() * rate)
    val maxStart = (total - length).coerceAtLeast(0L)
    val start = e.audioStartFrame.coerceIn(0L, maxStart)
    val end = start + length
    val file = remember(meta.id) { File(meta.dir, ImportedAudio.FILE_NAME) }
    var peaks by remember(file, e.audioFrames, e.audioChannels, e.audioName) { mutableStateOf(FloatArray(PEAKS) { .1f }) }
    LaunchedEffect(file, e.audioFrames, e.audioChannels, e.audioRate, e.audioName, file.lastModified()) {
        peaks = withContext(Dispatchers.IO) {
            try { sampleWaveform(file, total, e.audioChannels, PEAKS) } catch (ex: Exception) { FloatArray(PEAKS) { .1f } }
        }
    }
    val minSpan = max((length * 1.2).toLong(), rate * 2L).coerceAtMost(total)
    var viewSpan by remember(meta.id, total) { mutableLongStateOf(min(total, max(length * 5, rate * 20L))) }
    var viewStart by remember(meta.id, total) {
        mutableLongStateOf((start + length / 2 - viewSpan / 2).coerceIn(0L, (total - viewSpan).coerceAtLeast(0L)))
    }
    val latestStart by rememberUpdatedState(start)
    val latestSettings by rememberUpdatedState(e)
    fun clampView() {
        viewSpan = viewSpan.coerceIn(minSpan, total)
        viewStart = viewStart.coerceIn(0L, (total - viewSpan).coerceAtLeast(0L))
    }
    fun select(frame: Long) {
        val current = latestSettings
        val candidate = frame.coerceIn(0L, maxStart)
        if (candidate != current.audioStartFrame) vm.updateExport(current.copy(audioStartFrame = candidate))
    }
    fun zoomBy(k: Float, centerFrame: Long, centerFrac: Float) {
        viewSpan = (viewSpan / k).toLong().coerceIn(minSpan, total)
        viewStart = (centerFrame - (centerFrac * viewSpan).toLong())
        clampView()
    }
    fun showSelection() {
        viewStart = start + length / 2 - viewSpan / 2
        clampView()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Parte que suena · canción de ${timeLabel(total.toDouble() / rate)}", color = StudioMuted, fontSize = 12.sp,
                modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ZoomOut, "Alejar", tint = StudioText, modifier = Modifier.size(34.dp)
                .clickable { zoomBy(0.6f, viewStart + viewSpan / 2, 0.5f) }.padding(6.dp))
            Icon(Icons.Filled.ZoomIn, "Acercar", tint = StudioText, modifier = Modifier.size(34.dp)
                .clickable { zoomBy(1.6f, start + length / 2, 0.5f) }.padding(6.dp))
            Icon(Icons.Filled.CenterFocusStrong, "Ir a la selección", tint = StudioText, modifier = Modifier.size(34.dp)
                .clickable { showSelection() }.padding(6.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp)
            .background(StudioTile, RoundedCornerShape(8.dp))
            .pointerInput(total, length) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    fun frameAt(x: Float): Long = viewStart + (x / w * viewSpan).toLong()
                    val downFrame = frameAt(down.position.x)
                    val selStart = latestStart
                    // Margen táctil de 12 dp alrededor del cuadro para agarrarlo fácil.
                    val slack = (12.dp.toPx() / w * viewSpan).toLong()
                    var mode = if (downFrame in (selStart - slack)..(selStart + length + slack)) 1 else 0
                    val viewAnchor = viewStart
                    var scrollAcc = 0L
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            mode = 2
                            moved = true
                            val z = event.calculateZoom()
                            val c = event.calculateCentroid(useCurrent = true)
                            if (z != 1f && c.x.isFinite()) zoomBy(z, frameAt(c.x), (c.x / w).coerceIn(0f, 1f))
                        } else if (mode != 2) {
                            val pos = pressed.first().position.x
                            val dx = pos - down.position.x
                            if (abs(dx) > 6.dp.toPx()) moved = true
                            val dFrames = (dx / w * viewSpan).toLong()
                            if (mode == 1) {
                                val stepFrames = (viewSpan * 0.02f).toLong()
                                if (pos > w * 0.9f && viewStart + viewSpan < total) {
                                    val before = viewStart; viewStart += stepFrames; clampView(); scrollAcc += viewStart - before
                                } else if (pos < w * 0.1f && viewStart > 0L) {
                                    val before = viewStart; viewStart -= stepFrames; clampView(); scrollAcc += viewStart - before
                                }
                                select(selStart + dFrames + scrollAcc)
                            } else {
                                viewStart = viewAnchor - dFrames
                                clampView()
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved && mode == 0) select(downFrame - length / 2)
                }
            }) {
            val bars = 110
            val span = viewSpan.coerceAtLeast(1L)
            val fromX = size.width * (start - viewStart).toFloat() / span
            val toX = size.width * (end - viewStart).toFloat() / span
            drawRect(StudioPurple.copy(alpha = .24f), Offset(fromX, 0f),
                androidx.compose.ui.geometry.Size((toX - fromX).coerceAtLeast(2f), size.height))
            for (j in 0 until bars) {
                val f0 = viewStart + span * j / bars
                val f1 = viewStart + span * (j + 1) / bars
                val i0 = (f0 * PEAKS / total).toInt().coerceIn(0, PEAKS - 1)
                val i1 = (f1 * PEAKS / total).toInt().coerceIn(i0, PEAKS - 1)
                var peak = 0f
                for (i in i0..i1) peak = max(peak, peaks[i])
                val x = size.width * (j + .5f) / bars
                val h = size.height * peak.coerceIn(.04f, 1f) * .86f
                drawLine(if (x in fromX..toX) StudioPurple else StudioMuted.copy(alpha = .55f),
                    Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                    strokeWidth = (size.width / bars * .62f).coerceAtLeast(1f))
            }
            val edge = 3.dp.toPx()
            drawLine(StudioPurple, Offset(fromX, 0f), Offset(fromX, size.height), strokeWidth = edge)
            drawLine(StudioPurple, Offset(toX, 0f), Offset(toX, size.height), strokeWidth = edge)
            // Asas redondas para indicar que el cuadro se arrastra
            drawCircle(StudioPurple, 6.dp.toPx(), Offset(fromX, size.height / 2))
            drawCircle(StudioPurple, 6.dp.toPx(), Offset(toX, size.height / 2))
        }
        // Mapa de la canción completa: toca o arrastra para ir a otra parte.
        Canvas(Modifier.fillMaxWidth().height(18.dp)
            .background(StudioTile.copy(alpha = .6f), RoundedCornerShape(5.dp))
            .pointerInput(total) {
                detectTapGestures { p ->
                    viewStart = (p.x / size.width * total).toLong() - viewSpan / 2; clampView()
                }
            }
            .pointerInput(total) {
                detectDragGestures { change, _ ->
                    change.consume()
                    viewStart = (change.position.x / size.width * total).toLong() - viewSpan / 2; clampView()
                }
            }) {
            val bars = 80
            for (j in 0 until bars) {
                val i0 = j * PEAKS / bars
                var peak = 0f
                for (i in i0 until min(PEAKS, i0 + PEAKS / bars)) peak = max(peak, peaks[i])
                val x = size.width * (j + .5f) / bars
                val h = size.height * peak * .8f
                drawLine(StudioMuted.copy(alpha = .45f), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                    strokeWidth = (size.width / bars * .6f).coerceAtLeast(1f))
            }
            val vx = size.width * viewStart.toFloat() / total
            val vw = size.width * viewSpan.toFloat() / total
            drawRect(StudioText.copy(alpha = .9f), Offset(vx, 0f), androidx.compose.ui.geometry.Size(vw.coerceAtLeast(3f), size.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            val sx = size.width * start.toFloat() / total
            drawRect(StudioPurple, Offset(sx, 0f), androidx.compose.ui.geometry.Size(
                (size.width * length.toFloat() / total).coerceAtLeast(3f), size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${timeLabel(start.toDouble() / rate)} → ${timeLabel(end.toDouble() / rate)}",
                color = StudioText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("${timeLabel(length.toDouble() / rate)} = un bucle", color = StudioMuted, fontSize = 12.sp)
        }
        StudioSmallButton(if (vm.playing) "Pausar" else "Escuchar la parte elegida",
            if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, Modifier.fillMaxWidth()) {
            if (!vm.playing) vm.scrubTo(0f)
            vm.togglePlay()
        }
    }
}
