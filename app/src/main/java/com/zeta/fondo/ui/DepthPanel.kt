package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.engine.DepthMath
import com.zeta.fondo.model.EffectDef

private val Leaf = Color(0xFF6BD68A)

/** Claves del 2.5D que se muestran en este bloque y no en la lista general de ajustes. */
val depthParamKeys = setOf("ai", "layers", "fix", "plate")

/**
 * Ajustes del 2.5D: profundidad automática SIN IA (al instante), ver mapa, capas y pinceles
 * de corrección Acercar (Zona verde) / Alejar (Zona azul). v44.17: sin IA local.
 */
@Composable
fun DepthControls(vm: AppViewModel, def: EffectDef) {
    LaunchedEffect(Unit) { vm.ensureDepth() }
    val aiOn = (vm.settings["parallax.ai"] ?: 1f) > .5f
    val state = vm.depthState
    // v44.10 · Panel sencillo: el 2.5D se prepara solo; arriba lo esencial y abajo las correcciones.
    Text("Profundidad", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    when (state) {
        is AppViewModel.DepthState.Running -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = StudioPink)
            Text(state.stage, color = StudioMuted, fontSize = 11.sp)
            SmallChoice("Cancelar", false) { vm.cancelDepth() }
        }
        is AppViewModel.DepthState.Ready -> {
            Note("Listo · automático, al instante" + if (state.fromCache) " · guardado con el proyecto" else "")
            if (state.contrast < DepthMath.LOW_CONTRAST)
                Warn("Esta imagen casi no tiene profundidad (pasa en manga B/N o viñetas planas). Pinta al personaje para separarlo.")
        }
        is AppViewModel.DepthState.Failed -> Warn(state.reason)
        else -> if (!aiOn) Note("Profundidad automática apagada: se usan las zonas que pintes.")
    }
    vm.depthNotice?.let { Warn(it) }

    // Fuerza del movimiento en un toque (el deslizador «Profundidad» sigue abajo para afinar).
    val amount = vm.settings["parallax.amount"] ?: .035f
    Text("Movimiento", color = StudioMuted, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for ((label, v) in DEPTH_STRENGTHS) {
            val sel = DEPTH_STRENGTHS.minByOrNull { kotlin.math.abs(it.second - amount) }?.first == label
            SmallChoice(label, sel) { vm.setSetting("parallax.amount", v) }
        }
    }

    // Lo que más mejora el resultado: separar al personaje.
    SmallChoice(if (vm.maskHasPaint(0)) "Repasar personaje" else "Pintar personaje (mejor resultado)",
        vm.maskHasPaint(0), dot = Color(0xFFFF6B6B)) { vm.protect() }

    if (state !is AppViewModel.DepthState.Running) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallChoice("Recalcular 2.5D", false) { vm.computeQuickDepth() }
        }
    }

    Text("Correcciones", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    val ai = vm.depthUsable
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        SmallChoice(if (ai) "Alejar" else "Pintar fondo", vm.maskHasPaint(1), dot = Sky) { vm.paintBuiltinMask(1) }
        SmallChoice(if (ai) "Acercar" else "Pintar primer plano", vm.maskHasPaint(2), dot = Leaf) { vm.paintBuiltinMask(2) }
    }
    if (state is AppViewModel.DepthState.Ready) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        SmallChoice("Ver mapa", vm.depthView) { vm.toggleDepthView() }
    }
    SwitchRow("Profundidad automática", aiOn) { vm.setSetting("parallax.ai", if (it) 1f else 0f) }
    if (ai) def.params.filter { it.key == "layers" || it.key == "fix" }.forEach { EffectParameter(vm, def, it) }
    CharacterAndBackground(vm)
}

/** v44.10 · Fuerza del 2.5D en un toque: Suave / Normal / Fuerte (parallax.amount). */
val DEPTH_STRENGTHS = listOf("Suave" to .02f, "Normal" to .035f, "Fuerte" to .055f)   // v44.24: más visible

/**
 * Relleno de lo que hay detrás del personaje, para que al moverse el fondo no arrastre su
 * contorno. v44.10: sin MI-GAN en el panel (importación manual, fuera de la versión sencilla).
 */
@Composable
private fun CharacterAndBackground(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.ensurePlate() }
    val plateOn = (vm.settings["parallax.plate"] ?: 1f) > .5f
    SwitchRow("Rellenar detrás del personaje", plateOn) { vm.setSetting("parallax.plate", if (it) 1f else 0f); if (it) vm.ensurePlate() }
    if (!plateOn) return
    when (val pl = vm.plateState) {
        is AppViewModel.AiJob.Running -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = StudioPink); Note(pl.stage) }
        is AppViewModel.AiJob.Done -> if (vm.plateStale) SmallChoice("Actualizar relleno", true) { vm.computePlate() }
        is AppViewModel.AiJob.Failed -> { Warn(pl.reason); SmallChoice("Reintentar", false) { vm.computePlate() } }
        else -> if (!vm.maskHasPaint(0)) Note("Funciona cuando pintas al personaje.")
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = StudioMuted, fontSize = 11.sp, lineHeight = 14.sp)
}

@Composable
private fun Warn(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioOrange.copy(alpha = .16f))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(text, color = StudioText, fontSize = 12.sp, lineHeight = 15.sp)
    }
}
