package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel

/** Efectos que viven en el cielo / el agua (para ofrecer la selección automática). */
private val skyEffects = setOf("sky", "clouds3", "cloudshadow", "aurora", "stars", "birds", "rays", "comet", "lensflare")
private val waterEffects = setOf("water", "shore", "reflect", "sunpath", "ripple", "glint", "waterfall")
private val eyeEffects = setOf("eyes", "blink")

/**
 * v47 · Zona del efecto con ayuda: caras/ojos (detector anime), pecho para la respiración,
 * cielo y agua automáticos, y «Tocar para seleccionar». Todo PROPONE una zona normal del
 * efecto: se deshace con ↶ y se corrige con el pincel. La cara nunca se deforma.
 */
@Composable
fun AiZoneTools(vm: AppViewModel, id: String) {
    val faceTool = id in eyeEffects || id == "zonemotion"
    Text("Selección asistida", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            id in eyeEffects -> Box(Modifier.weight(1f)) { SmallChoice("Detectar ojos", false) { vm.aiFaceZone(id) } }
            id == "zonemotion" -> Box(Modifier.weight(1f)) { SmallChoice("Respiración (pecho)", false) { vm.aiFaceZone(id) } }
            id in skyEffects -> Box(Modifier.weight(1f)) { SmallChoice("Detectar cielo", false) { vm.aiSkyOrWater(id, false) } }
            id in waterEffects -> Box(Modifier.weight(1f)) { SmallChoice("Detectar agua", false) { vm.aiSkyOrWater(id, true) } }
        }
        val tapping = vm.tapSelectFor == id
        Box(Modifier.weight(1f)) {
            SmallChoice(if (tapping) "Toca la imagen… (cancelar)" else "Tocar para seleccionar", tapping) {
                if (tapping) vm.cancelTapSelect() else vm.startTapSelect(id)
            }
        }
    }
    if (vm.tapSelectFor == id || !faceTool)
        LabeledSlider("Tolerancia del toque", vm.tapTolerance, 0f, 1f, false) { vm.tapTolerance = it }
    when (val st = vm.regionState) {
        is AppViewModel.AiJob.Running -> { LinearProgressIndicator(Modifier.fillMaxWidth(), color = StudioPink); Text(st.stage, color = StudioMuted, fontSize = 11.sp) }
        is AppViewModel.AiJob.Done -> {
            Text(st.text, color = StudioMuted, fontSize = 11.sp, lineHeight = 14.sp)
            st.warning?.let { AiWarn(it) }
        }
        is AppViewModel.AiJob.Failed -> AiWarn(st.reason)
        else -> if (faceTool) Text("La app solo propone la zona; la cara nunca se deforma. Corrige con el pincel.",
            color = StudioMuted, fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
internal fun AiWarn(text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioOrange.copy(alpha = .16f))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(text, color = StudioText, fontSize = 12.sp, lineHeight = 15.sp)
    }
}
