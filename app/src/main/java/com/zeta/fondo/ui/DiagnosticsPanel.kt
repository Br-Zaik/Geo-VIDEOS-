package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.diag.Benchmark
import com.zeta.fondo.diag.DeviceInfo
import com.zeta.fondo.diag.ZetaLog
import kotlinx.coroutines.delay

/**
 * v44 · Ajustes › Diagnóstico. Discreto: solo existe dentro de Ajustes.
 * Muestra lo que se mide de verdad en este teléfono; lo que no se puede medir dice
 * «No disponible». La prueba de 60 s por etapa solo empieza al tocar el botón.
 */
@Composable
fun DiagnosticsPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var logPreview by remember { mutableStateOf("") }
    // Mientras el panel está abierto se mide también la GPU (si el teléfono lo admite).
    DisposableEffect(Unit) {
        vm.hub.diagnostics = true
        onDispose {
            vm.stopBenchmark()
            vm.hub.diagnostics = false
        }
    }
    LaunchedEffect(Unit) {
        logPreview = ZetaLog.readAll().lines().filter { it.isNotBlank() }.takeLast(12).joinToString("\n")
        while (true) { delay(500); tick++ }
    }
    Column(Modifier.fillMaxSize()) {
        CapHeader("Diagnóstico") { vm.applyAndClose() }
        PanelScroll {
            val snap = remember(tick) { vm.hub.stats.snapshot() }
            val gpuAvail = vm.hub.gpuTimerAvailable
            Text("Vista previa (mientras se reproduce)", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Metric("FPS", Benchmark.na(snap.fps))
            Metric("Cuadro medio / p95", if (snap.frameMs == null) "No disponible"
                else "${Benchmark.na(snap.frameMs)} / ${Benchmark.na(snap.p95Ms)} ms")
            Metric("CPU hilo GL", if (snap.cpuMs == null) "No disponible" else "${Benchmark.na(snap.cpuMs, "%.2f")} ms")
            Metric("GPU", when {
                gpuAvail == false -> "No disponible (sin temporizador de GPU)"
                snap.gpuMs == null -> "No disponible"
                else -> "${Benchmark.na(snap.gpuMs, "%.2f")} ms"
            })
            Metric("Escala de render", "${(vm.hub.lastRenderScale * 100).toInt()} %")
            Metric("RAM libre", DeviceInfo.ramAvailMb(ctx)?.let { "$it MB" } ?: "No disponible")
            Metric("Memoria de la app", "${DeviceInfo.heapUsedMb()} / ${DeviceInfo.heapMaxMb()} MB")
            Metric("Estado térmico", Benchmark.thermalName(DeviceInfo.thermalStatus(ctx)))
            Metric("GPU del teléfono", vm.hub.gpuInfo.ifBlank { "No disponible" })
            Metric("Calidad cine", vm.hub.cineInfo.ifBlank { "Sin usar todavía" })
            Metric("Cuadros sin efectos", vm.hub.blankFrames.toString())
            if (!vm.playing) Text("Pulsa Play para medir FPS.", color = StudioMuted, fontSize = 11.sp)

            Text("Prueba de rendimiento", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp))
            val b = vm.benchmark
            if (b?.running == true) {
                val count = Benchmark.stages.getOrElse(b.stage) { 6 }
                Text(if (b.waitingShader) "Etapa ${b.stage + 1}/3 · $count efecto(s): preparando el shader…"
                    else "Etapa ${b.stage + 1}/3 · $count efecto(s): quedan ${b.stageSecLeft} s",
                    color = StudioMuted, fontSize = 12.sp)
                StudioSmallButton("Detener prueba", Icons.Filled.Stop, Modifier.fillMaxWidth()) { vm.stopBenchmark() }
            } else {
                Text("1, 3 y 6 efectos, ${Benchmark.STAGE_SEC} s cada uno (unos 3–4 min). No cambia tu proyecto.",
                    color = StudioMuted, fontSize = 12.sp)
                StudioSmallButton("Iniciar prueba", Icons.Filled.PlayArrow, Modifier.fillMaxWidth()) { vm.startBenchmark() }
            }
            b?.results?.forEach { r ->
                Metric("${r.effects} efecto(s)", "${Benchmark.na(r.stats.totalFps)} fps · GPU ${
                    if (r.stats.totalGpuMs == null) "No disponible" else Benchmark.na(r.stats.totalGpuMs, "%.1f") + " ms"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioSmallButton("Compartir informe", Icons.Filled.Share, Modifier.weight(1f)) { vm.shareDiagnostics(includeLog = true) }
            }

            Text("Registro de errores (solo en este teléfono)", color = StudioText, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioTile).padding(8.dp)) {
                Text(logPreview.ifBlank { "Sin errores registrados." }, color = StudioMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
            }
            StudioSmallButton("Borrar registro", Icons.Filled.DeleteOutline, Modifier.fillMaxWidth()) {
                ZetaLog.clear(); logPreview = ""
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = StudioMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = StudioText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
