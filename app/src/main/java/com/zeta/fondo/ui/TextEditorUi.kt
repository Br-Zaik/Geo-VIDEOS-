package com.zeta.fondo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.engine.TextRender
import com.zeta.fondo.model.TextLayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * v44.27 · Escribir un texto: arriba ✕ y ✓ Listo, al medio el texto tal cual saldrá (la misma
 * imagen que se guarda, en pequeño) y abajo, pegado al teclado, lo escrito, Fuente, Color, Borde
 * y Sombra. La vista previa se rehace a los ~0,1 s de dejar de escribir (no en cada letra).
 */
@Composable
fun TextEditorOverlay(vm: AppViewModel, modifier: Modifier) {
    val e = vm.textEditor ?: return
    val spec = e.spec
    var field by remember(e.layerId) { mutableStateOf(TextFieldValue(spec.text, TextRange(spec.text.length))) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(spec) {
        if (spec.text.isBlank()) { preview = null; return@LaunchedEffect }
        delay(90)
        preview = withContext(Dispatchers.Default) {
            try { TextRender.render(spec, 900).asImageBitmap() } catch (_: Throwable) { null }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try { focus.requestFocus() } catch (_: Throwable) {}
    }
    // Fondo que tapa (y absorbe los toques de) lo de atrás; se ve el lienzo apagado.
    Box(modifier.background(Color.Black.copy(alpha = .8f)).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(StudioTile).clickable { vm.closeTextEditor() },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Cancelar", tint = StudioText, modifier = Modifier.size(22.dp))
                }
                Text(if (e.layerId == null) "Texto nuevo" else "Editar texto", color = StudioText, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Row(Modifier.height(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (spec.text.isBlank()) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(StudioTile, StudioTile)) else StudioGradient)
                    .clickable(enabled = spec.text.isNotBlank()) { vm.applyTextEditor() }
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                val p = preview
                if (p != null) Image(p, "Vista previa del texto", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Escribe abajo", color = StudioMuted, fontSize = 15.sp)
            }
            Column(Modifier.fillMaxWidth().background(StudioPanel).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { v ->
                        val t = v.text.replace("\r", "").take(TextLayers.MAX_CHARS)
                        field = if (t == v.text) v else TextFieldValue(t, TextRange(t.length))
                        vm.updateTextDraft(spec.copy(text = t))
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Escribe aquí (Enter = otra línea)", color = StudioMuted) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = StudioText, unfocusedTextColor = StudioText,
                        focusedBorderColor = StudioPink, unfocusedBorderColor = StudioTile, cursorColor = StudioPink)
                )
                // Fuentes: cada botón muestra «Aa» con esa fuente.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextLayers.fonts.forEachIndexed { i, f ->
                        val sel = spec.font == i
                        val family = remember(i) { FontFamily(TextRender.typefaceFor(i)) }
                        Column(Modifier.width(64.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) StudioPink.copy(alpha = .22f) else StudioTile)
                            .border(1.5.dp, if (sel) StudioPink else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { vm.updateTextDraft(spec.copy(font = i)) }.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aa", color = StudioText, fontSize = 18.sp, fontFamily = family, maxLines = 1)
                            Text(f.name, color = if (sel) StudioPink else StudioMuted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                SwatchRow("Color", spec.color) { vm.updateTextDraft(spec.copy(color = it)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextLayers.outlineNames.forEachIndexed { i, name ->
                        SmallPill(name, spec.outline == i) { vm.updateTextDraft(spec.copy(outline = i)) }
                    }
                    SmallPill("Sombra", spec.shadow) { vm.updateTextDraft(spec.copy(shadow = !spec.shadow)) }
                }
                if (spec.outline > 0) SwatchRow("Borde", spec.outlineColor) { vm.updateTextDraft(spec.copy(outlineColor = it)) }
            }
        }
    }
}

@Composable
private fun SwatchRow(label: String, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = StudioMuted, fontSize = 12.sp, modifier = Modifier.width(46.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TextLayers.colors.forEach { c ->
                val sel = c == selected
                Box(Modifier.size(30.dp).clip(CircleShape)
                    .border(if (sel) 3.dp else 1.dp, if (sel) StudioPink else Color.White.copy(alpha = .35f), CircleShape)
                    .padding(if (sel) 4.dp else 2.dp).clip(CircleShape).background(Color(c)).clickable { onPick(c) })
            }
        }
    }
}

@Composable
private fun SmallPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
        .background(if (selected) StudioPink.copy(alpha = .25f) else StudioTile)
        .border(1.5.dp, if (selected) StudioPink else Color.Transparent, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) StudioPink else StudioText, fontSize = 12.sp, maxLines = 1)
    }
}
