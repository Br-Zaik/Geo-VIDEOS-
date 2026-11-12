package com.zeta.fondo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.ClipEdits
import com.zeta.fondo.model.PreciseTime

/** v44 · «Imán ON/OFF» sobre la línea de tiempo. Tocar = encender/apagar; mantener = opciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MagnetPill(vm: AppViewModel, modifier: Modifier, onLongPress: () -> Unit) {
    val on = vm.magnetOn
    Row(modifier.height(20.dp).clip(RoundedCornerShape(10.dp))
        .background(if (on) StudioPurple.copy(alpha = .9f) else StudioTile)
        .combinedClickable(onLongClick = onLongPress) { vm.toggleMagnet() }
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (on) "Imán ON" else "Imán OFF", color = if (on) Color.White else StudioMuted, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** v44 · Salida sencilla del modo Solo. */
@Composable
internal fun SoloPill(modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(20.dp).clip(RoundedCornerShape(10.dp)).background(StudioPink)
        .clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Solo · Mostrar todos", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun MagnetDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Imán de la línea de tiempo", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            SwitchRow("Imán", vm.magnetOn) { vm.setMagnet(on = it) }
            SwitchRow("Vibrar al pegarse", vm.magnetHaptic) { vm.setMagnet(haptic = it) }
            LabeledSlider("Distancia", vm.magnetDp, 4f, 16f, true, suffix = " dp") { vm.setMagnet(dp = it) }
            Text("Se pega al cabezal blanco, a los bordes de otros bloques y al inicio o final del vídeo. " +
                "Para soltarlo, sigue arrastrando un poco más (1,5 × la distancia).", color = StudioMuted, fontSize = 12.sp)
            DialogButton("Listo", onDismiss)
        }
    }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * v44 · Tiempo exacto del bloque: inicio y duración con pasos de 0,01 s y 0,1 s o escritos a mano.
 * El imán no interviene aquí. Se aplica al tocar «Aplicar» y se puede deshacer.
 */
@Composable
internal fun PreciseTimeDialog(vm: AppViewModel, uid: Long, onDismiss: () -> Unit) {
    val clip = vm.effectClips.firstOrNull { it.uid == uid } ?: run { onDismiss(); return }
    val total = vm.exportSettings.loopSec.toFloat()
    var start by remember(uid) { mutableFloatStateOf(clip.startSec) }
    var dur by remember(uid) { mutableFloatStateOf(clip.durationSec) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tiempo exacto · ${timelineTitle(clip.effectId)}", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TimeField("Inicio", start) { v ->
                val (s, d) = PreciseTime.setStart(v, dur, total); start = s; dur = d
            }
            TimeField("Duración", dur) { v ->
                val (s, d) = PreciseTime.setDuration(start, v, total); start = s; dur = d
            }
            TimeField("Final", start + dur) { v ->
                val (s, d) = PreciseTime.setEnd(start, v, total); start = s; dur = d
            }
            Text("Vídeo: ${PreciseTime.format(total)} s · mínimo ${PreciseTime.format(ClipEdits.MIN_DURATION)} s",
                color = StudioMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DialogButton("Cancelar", onDismiss) }
                Box(Modifier.weight(1f)) {
                    DialogButton("Aplicar") {
                        vm.setEffectClipPlacement(uid, start, dur, vm.timelineTrackOf(uid))
                        vm.saveProject()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: Float, onChange: (Float) -> Unit) {
    var text by remember { mutableStateOf(PreciseTime.format(value)) }
    // Si el valor cambia por los botones o se ajusta a los límites, el texto lo refleja;
    // mientras se escribe un número válido no se toca (el cursor no salta).
    androidx.compose.runtime.LaunchedEffect(value) {
        val typed = PreciseTime.parse(text)
        if (typed == null || kotlin.math.abs(PreciseTime.round(typed) - value) > .004f) text = PreciseTime.format(value)
    }
    Column {
        Text(label, color = StudioMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepButton("−0,1") { onChange(value - .1f) }
            StepButton("−0,01") { onChange(value - .01f) }
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    text = t
                    PreciseTime.parse(t)?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = androidx.compose.ui.text.TextStyle(color = StudioText, fontSize = 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f)
            )
            StepButton("+0,01") { onChange(value + .01f) }
            StepButton("+0,1") { onChange(value + .1f) }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(Modifier.height(40.dp).width(46.dp).clip(RoundedCornerShape(9.dp)).background(StudioTile).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = StudioText, fontSize = 11.sp, maxLines = 1)
    }
}

