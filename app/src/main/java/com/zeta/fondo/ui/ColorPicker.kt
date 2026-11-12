package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.ColorHex

private val swatches = listOf(0xFFFFFF, 0x1A1A1F, 0x8A8F99, 0xE53935, 0xFF7A1A, 0xFFD23F,
    0x3DDC84, 0x22D3EE, 0x2F6BFF, 0x8B5CF6, 0xF472B6)

private fun rgbColor(rgb: Int) = Color(0xFF000000.toInt() or rgb)

/** Fila compacta: color del estilo (automático), muestras rápidas y selector HEX. */
@Composable
fun EffectColorRow(vm: AppViewModel, id: String) {
    val current = vm.tintOf(id)
    var open by remember(id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(StudioTile)
            .then(if (current == null) Modifier.border(2.dp, StudioGradient, CircleShape) else Modifier)
            .clickable { vm.setTint(id, null) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, "Color del estilo", tint = StudioText, modifier = Modifier.size(18.dp))
        }
        swatches.forEach { rgb ->
            Box(Modifier.size(34.dp).clip(CircleShape).background(rgbColor(rgb))
                .then(if (current == rgb) Modifier.border(3.dp, StudioText, CircleShape) else Modifier.border(1.dp, StudioMuted.copy(alpha = .4f), CircleShape))
                .clickable { vm.setTint(id, rgb) })
        }
        Box(Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(StudioTile)
            .clickable { open = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Colorize, "Color personalizado", tint = StudioText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(current?.let { ColorHex.format(it) } ?: "HEX", color = StudioText, fontSize = 12.sp)
            }
        }
    }
    if (open) HexColorDialog(current ?: 0xFFFFFF, onDismiss = { open = false }) { rgb ->
        vm.setTint(id, rgb); open = false
    }
}

@Composable
fun HexColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember(initial) { ColorHex.toHsv(initial) }
    var h by remember { mutableFloatStateOf(hsv0[0]) }
    var s by remember { mutableFloatStateOf(hsv0[1]) }
    var v by remember { mutableFloatStateOf(hsv0[2]) }
    var text by remember { mutableStateOf(ColorHex.format(initial)) }
    val rgb = ColorHex.fromHsv(h, s, v)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(rgbColor(rgb)))
                Spacer(Modifier.width(10.dp))
                Text("Color del efecto", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            LabeledSlider("Tono", h, 0f, 360f, true, suffix = "°") { h = it; text = ColorHex.format(ColorHex.fromHsv(h, s, v)) }
            LabeledSlider("Saturación", s, 0f, 1f, false) { s = it; text = ColorHex.format(ColorHex.fromHsv(h, s, v)) }
            LabeledSlider("Brillo", v, 0f, 1f, false) { v = it; text = ColorHex.format(ColorHex.fromHsv(h, s, v)) }
            OutlinedTextField(value = text, singleLine = true, label = { Text("HEX") },
                onValueChange = { t ->
                    text = t.take(7)
                    ColorHex.parse(text)?.let { parsed ->
                        val x = ColorHex.toHsv(parsed); h = x[0]; s = x[1]; v = x[2]
                    }
                }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = StudioMuted) }
                TextButton(onClick = { onPick(rgb) }) { Text("Aplicar", color = StudioPurple) }
            }
        }
    }
}
