package com.zeta.fondo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.EditorTab
import com.zeta.fondo.model.PreviewSize
import com.zeta.fondo.model.Tool

/*
 * v41 · Parte inferior del editor, como en un editor de vídeo:
 *  - normalmente: línea de tiempo (con su propio desplazamiento) y UNA barra de herramientas;
 *  - hoja compacta: solo las opciones de la herramienta (pincel, rutas) sobre la barra;
 *  - hoja completa: catálogo de efectos, Ajustar, audio, color… cubre la línea de tiempo y la
 *    barra hasta tocar ✓. El lienzo de arriba nunca cambia de tamaño.
 */

internal enum class SheetKind { NONE, COMPACT, FULL }

internal fun sheetKind(vm: AppViewModel): SheetKind = when {
    (vm.blendPanelOpen || vm.layerMotionOpen) && vm.selectedLayer != null -> SheetKind.FULL
    vm.paintingZone -> SheetKind.COMPACT
    vm.soundEditing != null -> SheetKind.FULL
    vm.animCatalogOpen -> SheetKind.FULL
    vm.editingEffect != null -> SheetKind.FULL
    vm.replacingClip != null -> SheetKind.FULL
    vm.tab == EditorTab.TOOLS -> when {
        vm.animPanel == "loop" -> SheetKind.FULL
        !vm.categoryOpen -> SheetKind.NONE
        vm.tool == Tool.ARROW && vm.arrows.isNotEmpty() -> SheetKind.COMPACT
        (vm.tool == Tool.ANCHOR || vm.tool == Tool.ERASE_GUIDE) && (vm.arrows.isNotEmpty() || vm.anchors.isNotEmpty()) -> SheetKind.COMPACT
        else -> SheetKind.NONE
    }
    !vm.optionsOpen -> SheetKind.NONE
    vm.tab == EditorTab.MASKS -> SheetKind.NONE
    else -> SheetKind.FULL
}

/** Todo lo que va debajo de los controles del lienzo. */
@Composable
fun EditorBottomArea(vm: AppViewModel, modifier: Modifier, barHeight: Dp) {
    when (sheetKind(vm)) {
        SheetKind.FULL -> FullSheet(vm, modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
        else -> BoxWithConstraints(modifier.background(Night)) {
            val h = maxHeight
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CapTimeline(vm, Modifier.fillMaxSize())
                    if (sheetKind(vm) == SheetKind.COMPACT) {
                        CompactSheet(vm, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .heightIn(max = ((h - barHeight) * .82f).coerceAtLeast(96.dp))
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                    }
                }
                EditorToolRow(vm, Modifier.fillMaxWidth().height(barHeight))
            }
        }
    }
}

/** Cabecera fina de una hoja: título y botón para volver a la línea de tiempo. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ExpandMore, "Cerrar opciones", tint = StudioMuted, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Acciones del bloque seleccionado (como al tocar un efecto en la línea de tiempo de un
 * editor de vídeo). «<» vuelve a la barra anterior.
 */
@Composable
fun ClipActionRow(vm: AppViewModel, modifier: Modifier) {
    val clip = vm.selectedClip() ?: return
    val id = clip.effectId
    // v44: bloque de sonido → sus propias acciones (sin Mover en la imagen ni Reemplazar).
    if (com.zeta.fondo.model.SoundClips.isSound(id)) {
        BarWithBack(modifier, onBack = { vm.selectTimelineClip(null) }) {
            ToolChip("Ajustar", Icons.Filled.Tune, false) { vm.openSoundClip(clip.uid) }
            ToolChip(if (clip.hidden) "Activar" else "Silenciar", if (clip.hidden) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                clip.hidden) { vm.toggleClipHidden(clip.uid) }
            ToolChip("Solo", Icons.Filled.CenterFocusStrong, vm.soloClip == clip.uid) { vm.toggleSolo(clip.uid) }
            ToolChip("Tiempo", Icons.Filled.Timer, vm.preciseTimeClip == clip.uid) { vm.preciseTimeClip = clip.uid }
            ToolChip("Dividir", Icons.Filled.ContentCut, false) { vm.splitEffectClip(clip.uid) }
            ToolChip("Duplicar", Icons.Filled.ContentCopy, false) { vm.duplicateEffectClip(clip.uid) }
            ToolChip("Eliminar", Icons.Filled.DeleteOutline, false) { vm.removeEffectClip(clip.uid) }
        }
        return
    }
    BarWithBack(modifier, onBack = { vm.selectTimelineClip(null) }) {
        ToolChip("Ajustar", Icons.Filled.Tune, false) { vm.editEffect(id) }
        // v44.10: Reemplazar va 2.º (antes estaba al final, fuera de la pantalla): cambia el
        // efecto conservando su tiempo, pista y posición.
        ToolChip("Reemplazar", Icons.Filled.SwapHoriz, vm.replacingClip == clip.uid,
            help = "Cambia este efecto por otro sin perder su tiempo ni su lugar", onHelp = { vm.showHint(it) }) { vm.startReplace() }
        // v44: ojo (se guarda y se respeta al exportar) y Solo (temporal, solo vista previa).
        ToolChip(if (clip.hidden) "Mostrar" else "Ocultar", if (clip.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            clip.hidden, help = "Oculta el bloque en la vista previa y en el vídeo, sin borrarlo",
            onHelp = { vm.showHint(it) }) { vm.toggleClipHidden(clip.uid) }
        ToolChip("Solo", Icons.Filled.CenterFocusStrong, vm.soloClip == clip.uid,
            help = "Ver solo este bloque un momento. No cambia el proyecto ni el vídeo exportado",
            onHelp = { vm.showHint(it) }) { vm.toggleSolo(clip.uid) }
        ToolChip("Tiempo", Icons.Filled.Timer, vm.preciseTimeClip == clip.uid,
            help = "Inicio y duración exactos, al 0,01 s", onHelp = { vm.showHint(it) }) { vm.preciseTimeClip = clip.uid }
        // Mover en el TIEMPO = arrastrar el bloque; este botón lo mueve/agranda EN LA IMAGEN.
        ToolChip("Mover", Icons.Filled.OpenWith, vm.placingClip,
            help = if (vm.canPlace(id)) "Arrastra el punto en la imagen · aro = tamaño · flecha = dirección"
                   else "Este efecto se coloca con su zona (Ajustar › Zona); en el tiempo, arrastra el bloque",
            onHelp = { vm.showHint(it) }) { vm.togglePlacing() }
        ToolChip("Dividir", Icons.Filled.ContentCut, false) { vm.splitEffectClip(clip.uid) }
        ToolChip("Duplicar", Icons.Filled.ContentCopy, false) { vm.duplicateEffectClip(clip.uid) }
        if (id in behindCapable) ToolChip(if (vm.isBehind(id)) "Detrás" else "Delante", Icons.Filled.Layers,
            vm.isBehind(id)) { vm.setBehind(id, !vm.isBehind(id)) }
        ToolChip("Eliminar", Icons.Filled.DeleteOutline, false) { vm.removeEffectClip(clip.uid) }
    }
}

/** Acciones de la pista de la imagen: duración, formato, color, animar y proteger. */
@Composable
fun ImageActionRow(vm: AppViewModel, modifier: Modifier) {
    BarWithBack(modifier, onBack = { vm.selectImageTrack(false) }) {
        ToolChip("Duración", Icons.Filled.Timer, false) { vm.openImageSetting(0) }
        ToolChip("Formato", Icons.Filled.Crop, false) { vm.openImageSetting(1) }
        ToolChip("Ajustar", Icons.Filled.Tune, false) { vm.openImageSetting(2) }
        ToolChip("Animar", Icons.Filled.PlayCircleOutline, false) { vm.openCategory(EditorTab.TOOLS) }
        ToolChip("Proteger", Icons.Filled.Shield, false) { vm.openCategory(EditorTab.MASKS) }
        ToolChip("Efectos", Icons.Filled.AutoAwesome, false) { vm.openCategory(EditorTab.EFFECTS) }
    }
}

/** Ajustes › Vista: el usuario decide el tamaño del lienzo (nunca cambia solo). */
@Composable
fun PreviewSizePanel(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth()) {
        CapHeader("Tamaño de la vista previa") { vm.applyAndClose() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewSize.values().forEach { size ->
                Box(Modifier.weight(1f)) { SmallChoice(size.label, vm.previewSize == size) { vm.changePreviewSize(size) } }
            }
        }
    }
}
