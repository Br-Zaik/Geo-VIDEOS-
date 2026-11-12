package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.Keyframes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Amarillo de «estás sobre una clave» (se distingue del rosa de la app). */
internal val KeyOn = Color(0xFFFFD54F)

/**
 * v44 · Rombo de fotograma clave junto a un control.
 * - Vacío: el control no está animado. Tocar = primera clave en el cabezal.
 * - Relleno rosa: hay una clave justo en el cabezal (tocar = quitarla).
 * - Contorno rosa: está animado pero el cabezal está entre claves (tocar = añadir clave aquí).
 * - Mantener pulsado: curva de la clave y borrar la animación.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyDiamond(vm: AppViewModel, key: String) {
    var menu by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT   // se redibuja al mover el cabezal
    val animated = vm.keysOf(key).isNotEmpty()
    val onKey = vm.keyAtPlayhead(key) != null
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
        .combinedClickable(onLongClick = { if (animated) menu = true }) { vm.toggleKeyframe(key) },
        contentAlignment = Alignment.Center) {
        // v44.12 · como en Alight Motion: tenue si hay claves en otro momento, brillante y
        // más grande justo sobre una clave; gris si el control no está animado.
        Canvas(Modifier.size(if (onKey) 22.dp else 18.dp)) {
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f); lineTo(w, h / 2f); lineTo(w / 2f, h); lineTo(0f, h / 2f); close()
            }
            when {
                onKey -> { drawPath(path, KeyOn); drawPath(path, Color.White, style = Stroke(1.5.dp.toPx())) }
                animated -> { drawPath(path, StudioPink.copy(alpha = .35f)); drawPath(path, StudioPink, style = Stroke(1.5.dp.toPx())) }
                else -> drawPath(path, StudioMuted, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
    if (menu) KeyframeDialog(vm, key) { menu = false }
}

@Composable
private fun KeyframeDialog(vm: AppViewModel, key: String, onDismiss: () -> Unit) {
    val k = vm.keyAtPlayhead(key)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fotograma clave", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (k == null) Text("El cabezal no está sobre una clave de este control. Muévelo hasta un rombo de la línea de tiempo para cambiar su curva.",
                color = StudioMuted, fontSize = 12.sp)
            else {
                Text("Cómo se llega desde esta clave a la siguiente:", color = StudioMuted, fontSize = 12.sp)
                Keyframes.curveNames.forEachIndexed { i, name ->
                    SmallChoice(name, k.curve == i) { vm.setKeyCurve(key, i) }
                }
            }
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                .clickable { vm.clearKeyframes(key); onDismiss() }, contentAlignment = Alignment.Center) {
                Text("Borrar la animación de este control", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Listo", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * v44.12 · Tira de fotogramas clave del efecto en edición (como en Alight Motion). El panel de
 * ajustes tapa la línea de tiempo; aquí se ven TODAS las claves del bloque en grande, el cabezal
 * y se salta de una clave a otra. Tocar o arrastrar en la tira mueve el cabezal.
 */
@Composable
fun KeyframeStrip(vm: AppViewModel, id: String) {
    val clip = vm.keyClip(id) ?: return
    val total = vm.exportSettings.loopSec.toFloat().coerceAtLeast(.1f)
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
    val times = clip.keys.values.flatten().map { it.t }.distinct().sorted()
    val local = (vm.scrubT * total - clip.startSec).coerceIn(0f, clip.durationSec)
    fun seek(t: Float) { if (vm.playing) vm.togglePlay(); vm.scrubTo(((clip.startSec + t) / total).coerceIn(0f, .999f)) }
    val prev = times.lastOrNull { it < local - .02f }
    val next = times.firstOrNull { it > local + .02f }
    Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyNavButton("◀", prev != null) { prev?.let(::seek) }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF23232D))
            .pointerInput(clip.uid, total) {
                detectTapGestures { o -> seek(o.x / size.width * clip.durationSec) }
            }
            .pointerInput(clip.uid, total) {
                detectHorizontalDragGestures { change, _ -> change.consume(); seek((change.position.x / size.width).coerceIn(0f, 1f) * clip.durationSec) }
            }) {
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val w = size.width; val h = size.height; val y = h / 2f
                drawLine(Color.White.copy(alpha = .18f), Offset(0f, y), Offset(w, y), 2.dp.toPx())
                for (t in times) {
                    val x = (t / clip.durationSec.coerceAtLeast(.01f)) * w
                    val on = kotlin.math.abs(t - local) < .05f
                    diamond(x, y, if (on) 9.dp.toPx() else 7.dp.toPx(), if (on) KeyOn else StudioPink)
                }
                val px = (local / clip.durationSec.coerceAtLeast(.01f)) * w
                drawLine(Color.White, Offset(px, 2f), Offset(px, h - 2f), 2.dp.toPx())
            }
            if (times.isEmpty()) Text("Sin fotogramas clave · toca ◇ junto a un ajuste", color = StudioMuted, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(4.dp))
        KeyNavButton("▶", next != null) { next?.let(::seek) }
    }
    Text(String.format(java.util.Locale.US, "%.2f s", local) + (if (times.isNotEmpty()) " · ${times.size} clave(s)" else ""),
        color = StudioMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp))
}

private fun DrawScope.diamond(x: Float, y: Float, r: Float, fill: Color) {
    val p = Path().apply { moveTo(x, y - r); lineTo(x + r, y); lineTo(x, y + r); lineTo(x - r, y); close() }
    drawPath(p, fill)
    drawPath(p, Color(0xFF15151B), style = Stroke(1.5.dp.toPx()))
}

@Composable
private fun KeyNavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF23232D))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) StudioText else StudioMuted.copy(alpha = .4f), fontSize = 13.sp)
    }
}
