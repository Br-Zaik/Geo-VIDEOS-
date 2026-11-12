package com.zeta.fondo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel

/**
 * Ajustes › Mejorar imagen (Real-ESRGAN anime, BSD-3), simple como en Ibis Paint (v44.18):
 * eliges Original / x2 / x4 y tocas Mejorar. Se aplica al momento (vista previa y exportación)
 * y el antes / después se compara EN EL LIENZO arrastrando la línea del medio.
 * El original nunca se modifica: «Quitar mejora» vuelve a él.
 */
@Composable
fun SuperResPanel(vm: AppViewModel) {
    DisposableEffect(Unit) { vm.srPanelOpen = true; onDispose { vm.srPanelOpen = false } }
    LaunchedEffect(Unit) { vm.ensureSrCompare() }
    Column(Modifier.fillMaxSize()) {
        CapHeader("Mejorar imagen") { vm.applyAndClose() }
        PanelScroll {
            if (!vm.superResAvailable) {
                Text("Este APK no incluye el modelo de mejora.", color = StudioMuted, fontSize = 12.sp)
                return@PanelScroll
            }
            val running = vm.srState is AppViewModel.AiJob.Running
            Text("Tamaño", color = StudioText, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, s) in listOf("Original" to 1, "x2" to 2, "x4" to 4))
                    SmallChoice(label, vm.srScale == s) { if (!running) vm.chooseSrScale(s) }
            }
            vm.srOutputSize(vm.srScale)?.let { (w, h) ->
                val capped = vm.srScale > 1 && maxOf(w, h) >= 4090
                Text("La imagen queda de $w × $h." + if (capped) " (Límite: 4096 px.)" else "",
                    color = StudioMuted, fontSize = 12.sp)
            }
            when (val st = vm.srState) {
                is AppViewModel.AiJob.Running -> {
                    LinearProgressIndicator(progress = { vm.srProgress }, modifier = Modifier.fillMaxWidth(), color = StudioPink)
                    Text("${st.stage} ${(vm.srProgress * 100).toInt()} %", color = StudioMuted, fontSize = 11.sp)
                    SmallChoice("Cancelar", false) { vm.cancelEnhance() }
                }
                is AppViewModel.AiJob.Done -> Text(st.text, color = StudioMuted, fontSize = 11.sp)
                is AppViewModel.AiJob.Failed -> AiWarn(st.reason)
                else -> Unit
            }
            if (!running) {
                val has = vm.srFile() != null
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSmallButton(if (has) "Mejorar de nuevo" else "Mejorar", Icons.Filled.AutoAwesome) { vm.enhanceImage() }
                    if (has) SmallChoice("Quitar mejora", false) { vm.discardEnhanced() }
                }
                if (has) Text("Arrastra la línea del lienzo: izquierda antes, derecha después.",
                    color = StudioMuted, fontSize = 11.sp)
            }
        }
    }
}

/**
 * v44.18 · Antes / después en el lienzo: la imagen original a la izquierda y la mejorada a la
 * derecha, separadas por una línea que se arrastra con el dedo (como en Ibis Paint).
 */
@Composable
fun BeforeAfterSplit(before: android.graphics.Bitmap, after: android.graphics.Bitmap, modifier: Modifier = Modifier) {
    val b = androidx.compose.runtime.remember(before) { before.asImageBitmapSafe() }
    val a = androidx.compose.runtime.remember(after) { after.asImageBitmapSafe() }
    var split by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(.5f) }
    androidx.compose.foundation.Canvas(modifier
        .background(androidx.compose.ui.graphics.Color(0xFF0E0F13))
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                split = (down.position.x / size.width).coerceIn(0f, 1f)
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    split = (c.position.x / size.width).coerceIn(0f, 1f)
                    c.consume()
                }
            }
        }) {
        // Encaje: la imagen entera dentro del lienzo, centrada (igual en las dos).
        val k = minOf(size.width / b.width, size.height / b.height)
        val dw = (b.width * k).toInt(); val dh = (b.height * k).toInt()
        val dx = ((size.width - dw) / 2).toInt(); val dy = ((size.height - dh) / 2).toInt()
        val dst = androidx.compose.ui.unit.IntOffset(dx, dy)
        val dsz = androidx.compose.ui.unit.IntSize(dw, dh)
        val q = androidx.compose.ui.graphics.FilterQuality.High
        drawImage(b, dstOffset = dst, dstSize = dsz, filterQuality = q)
        val x = size.width * split
        clipRect(left = x) { drawImage(a, dstOffset = dst, dstSize = dsz, filterQuality = q) }
        val white = androidx.compose.ui.graphics.Color.White
        drawLine(white, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 2.dp.toPx())
        drawCircle(white, 13.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
        drawCircle(androidx.compose.ui.graphics.Color(0xFF2A2B33), 11.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
    }
    Row(modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        SplitLabel("Antes"); SplitLabel("Después")
    }
}

@Composable
private fun SplitLabel(text: String) {
    Text(text, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
        modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f),
            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

private fun android.graphics.Bitmap.asImageBitmapSafe() = this.asImageBitmap()
