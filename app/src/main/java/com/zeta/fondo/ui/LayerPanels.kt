package com.zeta.fondo.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.BlendModes
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.ImageRect
import com.zeta.fondo.model.Keyframes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/*
 * v44.23 · Capas de imagen (como en Alight Motion):
 *  - acciones de la capa seleccionada en la barra de abajo;
 *  - caja con tiradores sobre el lienzo: arrastrar = mover, esquinas = tamaño, lados = estirar,
 *    punto de arriba = girar, dos dedos sobre la imagen = tamaño y giro a la vez;
 *  - hoja «Mezcla y opacidad» con los 29 modos y una miniatura de ejemplo de cada uno;
 *  - hoja «Movimiento» y diálogo de tiempo.
 */

/** Barra de acciones de la capa seleccionada. */
@Composable
fun LayerActionRow(vm: AppViewModel, modifier: Modifier) {
    val l = vm.selectedLayerObj() ?: return
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
    val help: (String) -> Unit = { vm.showHint(it) }
    val keyHere = vm.layerHasKeyHere(l.id, ImageLayers.transformProps)
    val animated = vm.layerAnimated(l.id, ImageLayers.transformProps)
    BarWithBack(modifier, onBack = { vm.selectLayer(null) }) {
        // v44.27: lo más usado primero (también está en la cajita flotante sobre la imagen).
        if (l.text != null) ToolChip("Editar", Icons.Filled.Edit, true,
            help = "Cambia el texto, la fuente, el color, el borde o la sombra", onHelp = help) { vm.editTextLayer(l.id) }
        else ToolChip("Recortar", Icons.Filled.Crop, true,
            help = "Quita la parte que no quieres. «Original» la devuelve entera", onHelp = help) { vm.startLayerCrop(l.id) }
        ToolChip("Girar 90°", Icons.Filled.RotateRight, false,
            help = "Gira la imagen un cuarto de vuelta exacto", onHelp = help) { vm.rotateLayer90(l.id) }
        ToolChip("Mezcla", Icons.Filled.Opacity, vm.blendPanelOpen,
            help = "Mezcla y opacidad: cómo se combina la imagen con lo de abajo", onHelp = help) { vm.openBlendPanel() }
        ToolChip(if (keyHere) "Quitar clave" else "Clave", Icons.Filled.Diamond, keyHere || animated,
            badge = if (animated) "◆" else null,
            help = "Guarda la posición, el tamaño y el giro en este momento. Con 2 claves la imagen se mueve sola",
            onHelp = help) { vm.toggleLayerKey(l.id, ImageLayers.transformProps) }
        ToolChip("Movimiento", Icons.Filled.Animation, vm.layerMotionOpen, badge = if (l.motion != 0) "●" else null,
            help = "Flotar, latir, balanceo, girar o parpadeo de luz", onHelp = help) {
            vm.blendPanelOpen = false; vm.layerMotionOpen = true
        }
        ToolChip(if (l.behind) "Detrás" else "Delante", Icons.Filled.Person, l.behind,
            help = "Detrás = la imagen pasa por detrás del personaje pintado con Proteger", onHelp = help) { vm.toggleLayerBehind(l.id) }
        ToolChip("Subir", Icons.Filled.ArrowUpward, false, enabled = vm.canMoveLayer(l.id, 1),
            help = "Pone la imagen delante de la siguiente capa", onHelp = help) { vm.moveLayer(l.id, 1) }
        ToolChip("Bajar", Icons.Filled.ArrowDownward, false, enabled = vm.canMoveLayer(l.id, -1),
            help = "Pone la imagen detrás de la capa anterior", onHelp = help) { vm.moveLayer(l.id, -1) }
        ToolChip("Llenar", Icons.Filled.Fullscreen, false, help = "Que la imagen cubra todo el fondo", onHelp = help) { vm.fillLayer(l.id) }
        ToolChip("Centrar", Icons.Filled.CenterFocusStrong, false, help = "Al centro, con su tamaño inicial y sin giro", onHelp = help) { vm.resetLayerTransform(l.id) }
        ToolChip("Voltear", Icons.Filled.Flip, l.flipX, help = "Espejo horizontal", onHelp = help) { vm.flipLayer(l.id) }
        ToolChip("Tiempo", Icons.Filled.Timer, l.durSec > 0f, help = "Cuándo aparece y cuánto dura", onHelp = help) { vm.layerTimeOpen = l.id }
        ToolChip(if (l.hidden) "Mostrar" else "Ocultar", if (l.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            l.hidden, help = "Oculta la imagen sin borrarla (tampoco sale en el vídeo)", onHelp = help) { vm.toggleLayerHidden(l.id) }
        ToolChip("Duplicar", Icons.Filled.ContentCopy, false) { vm.duplicateLayer(l.id) }
        ToolChip("Eliminar", Icons.Filled.DeleteOutline, false) { vm.removeLayer(l.id) }
    }
}

private enum class LayerDrag { NONE, MOVE, SCALE, STRETCH_X, STRETCH_Y, ROTATE, PINCH, VIEW }

/** Caja con tiradores de la capa seleccionada sobre el lienzo. */
@Composable
fun LayerTransformOverlay(vm: AppViewModel, rect: ImageRect, modifier: Modifier) {
    val rectState = rememberUpdatedState(rect)
    val layer = vm.selectedLayerObj()
    val view = LocalView.current
    var snapX by remember { mutableStateOf(false) }
    var snapY by remember { mutableStateOf(false) }
    var snapRot by remember { mutableStateOf(false) }
    // v44.27: mientras se arrastra la imagen, la cajita flotante se esconde.
    var dragging by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
    val m = vm.meta
    BoxWithConstraints(modifier) {
    val areaW = constraints.maxWidth.toFloat()
    val areaH = constraints.maxHeight.toFloat()
    Canvas(Modifier.fillMaxSize().pointerInput(layer?.id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val r = rectState.value
            val l0 = vm.selectedLayerObj() ?: return@awaitEachGesture
            val meta = vm.meta ?: return@awaitEachGesture
            val f0 = vm.layerHandleFrame(l0) ?: return@awaitEachGesture
            val bw = meta.w.toFloat(); val bh = meta.h.toFloat()
            fun toScreen(u: Float, v: Float) = Offset(r.left + (u - vm.viewX) * vm.viewScale * r.width / vm.cropW,
                r.top + (v - vm.viewY) * vm.viewScale * r.height / vm.cropH)
            fun toUv(o: Offset) = Offset(vm.viewX + r.normX(o.x) * vm.cropW / vm.viewScale,
                vm.viewY + r.normY(o.y) * vm.cropH / vm.viewScale)
            // Píxeles de pantalla por píxel de la imagen base.
            val k = vm.viewScale * r.width / (vm.cropW * bw)
            val center = toScreen(f0.x, f0.y)
            val cs = ImageLayers.corners(f0, meta.w, meta.h).map { toScreen(it.first, it.second) }
            val mids = (0..3).map { (cs[it] + cs[(it + 1) % 4]) / 2f }   // arriba, derecha, abajo, izquierda
            val up = (mids[0] - center).let { v -> val d = v.getDistance().coerceAtLeast(1f); v / d }
            val rotKnob = mids[0] + up * 36.dp.toPx()
            val grab = 26.dp.toPx()
            val axisX = Offset(kotlin.math.cos(f0.rot), kotlin.math.sin(f0.rot))
            val axisY = Offset(-kotlin.math.sin(f0.rot), kotlin.math.cos(f0.rot))
            val inside = ImageLayers.localOf(f0, toUv(down.position).x, toUv(down.position).y, meta.w, meta.h) != null
            var mode = when {
                (down.position - rotKnob).getDistance() < grab -> LayerDrag.ROTATE
                cs.any { (down.position - it).getDistance() < grab } -> LayerDrag.SCALE
                (down.position - mids[1]).getDistance() < grab || (down.position - mids[3]).getDistance() < grab -> LayerDrag.STRETCH_X
                (down.position - mids[0]).getDistance() < grab || (down.position - mids[2]).getDistance() < grab -> LayerDrag.STRETCH_Y
                inside -> LayerDrag.MOVE
                else -> LayerDrag.NONE
            }
            val startedOnLayer = mode != LayerDrag.NONE
            val d0 = (down.position - center).getDistance().coerceAtLeast(1f)
            val a0 = atan2((down.position - center).y, (down.position - center).x)
            val rot0 = f0.rot * 180f / PI.toFloat()
            val sx0 = ImageLayers.valueAt(l0, "sx", vm.scrubT * vm.exportSettings.loopSec)
            val sy0 = ImageLayers.valueAt(l0, "sy", vm.scrubT * vm.exportSettings.loopSec)
            var pinchScale = 1f; var pinchRot = 0f; var pinchPan = Offset.Zero
            var moved = false
            if (startedOnLayer) { vm.beginLayerTransform(); down.consume(); dragging = true }
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (pressed >= 2 && mode != LayerDrag.PINCH && mode != LayerDrag.VIEW) {
                    mode = if (startedOnLayer) LayerDrag.PINCH else LayerDrag.VIEW
                    if (!startedOnLayer) { /* el lienzo hace zoom */ } else { pinchScale = 1f; pinchRot = 0f; pinchPan = Offset.Zero }
                }
                when (mode) {
                    LayerDrag.NONE -> { /* toque fuera: se decide al soltar */ }
                    LayerDrag.VIEW -> {
                        val c = event.calculateCentroid(useCurrent = true)
                        val pan = event.calculatePan()
                        if (c.isSpecified) vm.transformView(event.calculateZoom(), pan.x / r.width.coerceAtLeast(1f),
                            pan.y / r.height.coerceAtLeast(1f), r.normX(c.x), r.normY(c.y))
                    }
                    LayerDrag.MOVE -> {
                        val delta = toUv(ch.position) - toUv(down.position)
                        var nx = f0.x + delta.x; var ny = f0.y + delta.y
                        // Imán al centro (con vibración corta), como en los editores de vídeo.
                        val sxSnap = abs(nx - .5f) * bw * k < 10.dp.toPx()
                        val sySnap = abs(ny - .5f) * bh * k < 10.dp.toPx()
                        if (sxSnap) nx = .5f
                        if (sySnap) ny = .5f
                        if ((sxSnap && !snapX) || (sySnap && !snapY)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        snapX = sxSnap; snapY = sySnap
                        vm.transformLayer(l0.id, mapOf("x" to nx, "y" to ny))
                    }
                    LayerDrag.SCALE -> {
                        val s = (ch.position - center).getDistance() / d0
                        vm.transformLayer(l0.id, mapOf("sx" to sx0 * s, "sy" to sy0 * s))
                    }
                    LayerDrag.STRETCH_X -> {
                        val half = abs((ch.position - center).let { it.x * axisX.x + it.y * axisX.y }).coerceAtLeast(6f)
                        vm.transformLayer(l0.id, mapOf("sx" to (half / k) * 2f / bw))
                    }
                    LayerDrag.STRETCH_Y -> {
                        val half = abs((ch.position - center).let { it.x * axisY.x + it.y * axisY.y }).coerceAtLeast(6f)
                        vm.transformLayer(l0.id, mapOf("sy" to (half / k) * 2f * l0.aspect / bw))
                    }
                    LayerDrag.ROTATE -> {
                        val a = atan2((ch.position - center).y, (ch.position - center).x)
                        var deg = rot0 + (a - a0) * 180f / PI.toFloat()
                        val nearest = (deg / 90f).roundToInt() * 90f
                        val snapped = abs(deg - nearest) < 3f
                        if (snapped) deg = nearest
                        if (snapped && !snapRot) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        snapRot = snapped
                        vm.transformLayer(l0.id, mapOf("rot" to deg))
                    }
                    LayerDrag.PINCH -> {
                        pinchScale *= event.calculateZoom()
                        pinchRot += event.calculateRotation()
                        pinchPan += event.calculatePan()
                        val c2 = toUv(center + pinchPan) - toUv(center)
                        vm.transformLayer(l0.id, mapOf("sx" to sx0 * pinchScale, "sy" to sy0 * pinchScale,
                            "rot" to rot0 + pinchRot, "x" to f0.x + c2.x, "y" to f0.y + c2.y))
                    }
                }
                if (mode != LayerDrag.NONE) event.changes.forEach { it.consume() }
            }
            snapX = false; snapY = false; snapRot = false
            dragging = false
            if (startedOnLayer) vm.endLayerTransform()
            else if (!moved && mode == LayerDrag.NONE) {
                // Toque fuera de la capa: elige la capa que está debajo del dedo (la de más arriba).
                val uv = toUv(down.position)
                val hit = vm.layers.asReversed().firstOrNull { other ->
                    !other.hidden && vm.layerHandleFrame(other)?.let { ImageLayers.localOf(it, uv.x, uv.y, meta.w, meta.h) } != null
                }
                vm.selectLayer(hit?.id)
            }
        }
    }) {
        val l = layer ?: return@Canvas
        val meta = m ?: return@Canvas
        val f = vm.layerHandleFrame(l) ?: return@Canvas
        val r = rect
        fun toScreen(u: Float, v: Float) = Offset(r.left + (u - vm.viewX) * vm.viewScale * r.width / vm.cropW,
            r.top + (v - vm.viewY) * vm.viewScale * r.height / vm.cropH)
        val cs = ImageLayers.corners(f, meta.w, meta.h).map { toScreen(it.first, it.second) }
        val center = toScreen(f.x, f.y)
        val shadow = Color.Black.copy(alpha = .55f)
        // Guías del imán al centro.
        if (snapX) { val x = toScreen(.5f, 0f).x; drawLine(SnapYellow, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx()) }
        if (snapY) { val y = toScreen(0f, .5f).y; drawLine(SnapYellow, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx()) }
        val path = Path().apply { moveTo(cs[0].x, cs[0].y); for (i in 1..3) lineTo(cs[i].x, cs[i].y); close() }
        drawPath(path, shadow, style = Stroke(4.dp.toPx()))
        drawPath(path, Color.White, style = Stroke(1.6.dp.toPx()))
        val mids = (0..3).map { (cs[it] + cs[(it + 1) % 4]) / 2f }
        val up = (mids[0] - center).let { v -> val d = v.getDistance().coerceAtLeast(1f); v / d }
        val knob = mids[0] + up * 36.dp.toPx()
        drawLine(shadow, mids[0], knob, 4.dp.toPx())
        drawLine(Color.White, mids[0], knob, 1.6.dp.toPx())
        drawCircle(shadow, 12.dp.toPx(), knob)
        drawCircle(if (snapRot) SnapYellow else StudioPink, 10.dp.toPx(), knob)
        // Flecha curva de girar.
        drawArc(Color.White, -200f, 220f, false, knob - Offset(5.dp.toPx(), 5.dp.toPx()),
            androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        for (c in cs) { drawCircle(shadow, 10.dp.toPx(), c); drawCircle(Color.White, 8.dp.toPx(), c) }
        for (mp in mids) {
            drawRoundRect(shadow, mp - Offset(7.dp.toPx(), 7.dp.toPx()), androidx.compose.ui.geometry.Size(14.dp.toPx(), 14.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRoundRect(Color.White, mp - Offset(5.dp.toPx(), 5.dp.toPx()), androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
    if (layer != null && !dragging) LayerQuickBar(vm, layer, rect, areaW, areaH)
    }
}

/** Rombo de fotograma clave de una propiedad de la capa (como el de los efectos). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayerKeyDiamond(vm: AppViewModel, id: Long, props: List<String>) {
    var menu by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
    val animated = vm.layerAnimated(id, props)
    val onKey = vm.layerHasKeyHere(id, props)
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
        .combinedClickable(onLongClick = { if (animated) menu = true }) { vm.toggleLayerKey(id, props) },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (onKey) 22.dp else 18.dp)) {
            val w = size.width; val h = size.height
            val p = Path().apply { moveTo(w / 2f, 0f); lineTo(w, h / 2f); lineTo(w / 2f, h); lineTo(0f, h / 2f); close() }
            when {
                onKey -> { drawPath(p, KeyOn); drawPath(p, Color.White, style = Stroke(1.5.dp.toPx())) }
                animated -> { drawPath(p, StudioPink.copy(alpha = .35f)); drawPath(p, StudioPink, style = Stroke(1.5.dp.toPx())) }
                else -> drawPath(p, StudioMuted, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
    if (menu) LayerCurveDialog(vm, id, props) { menu = false }
}

@Composable
private fun LayerCurveDialog(vm: AppViewModel, id: Long, props: List<String>, onDismiss: () -> Unit) {
    val curve = vm.layerKeyCurve(id, props)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fotograma clave", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (curve == null) Text("El cabezal no está sobre una clave. Usa ◀ ▶ para saltar a una y cambiar su curva.",
                color = StudioMuted, fontSize = 12.sp)
            else {
                Text("Cómo se llega desde esta clave a la siguiente:", color = StudioMuted, fontSize = 12.sp)
                Keyframes.curveNames.forEachIndexed { i, name -> SmallChoice(name, curve == i) { vm.setLayerKeyCurve(id, props, i) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SmallChoice("◀ Clave anterior", false) { vm.seekLayerKey(id, -1) } }
                Box(Modifier.weight(1f)) { SmallChoice("Clave siguiente ▶", false) { vm.seekLayerKey(id, 1) } }
            }
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                .clickable { vm.clearLayerKeys(id, props); onDismiss() }, contentAlignment = Alignment.Center) {
                Text("Borrar esta animación", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Listo", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Hoja «Mezcla y opacidad» (como la de Alight Motion: miniatura de ejemplo de cada modo). */
@Composable
fun BlendSheet(vm: AppViewModel) {
    val l = vm.selectedLayerObj() ?: run { LaunchedEffect(Unit) { vm.blendPanelOpen = false }; return }
    @Suppress("UNUSED_VARIABLE") val tick = vm.scrubT
    val sec = vm.scrubT * vm.exportSettings.loopSec
    val opacity = ImageLayers.valueAt(l, "opacity", sec)
    val current = BlendModes.byId(l.blend)
    var open by remember(l.id) { mutableStateOf(setOf(current.group)) }
    Column(Modifier.fillMaxSize()) {
        CapHeader("Mezcla y opacidad") { vm.blendPanelOpen = false }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Opacity, null, tint = StudioMuted, modifier = Modifier.size(20.dp))
            Slider(value = opacity, onValueChange = { vm.setLayerOpacity(l.id, it, live = true) },
                onValueChangeFinished = { vm.setLayerOpacity(l.id, ImageLayers.valueAt(vm.selectedLayerObj() ?: l, "opacity",
                    vm.scrubT * vm.exportSettings.loopSec), live = false) },
                colors = capSliderColors(), modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("${(opacity * 100).roundToInt()}%", color = StudioText, fontSize = 13.sp, modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End)
            LayerKeyDiamond(vm, l.id, listOf("opacity"))
        }
        // Qué hace el modo elegido, siempre a la vista.
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(StudioTile).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(current.name, color = StudioPink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(current.help, color = StudioText, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            BlendGroupHeader("Normal", BlendModes.groupHelp[0], current.id == BlendModes.NORMAL, if (current.id == 0) "Normal" else null,
                expandable = false) { vm.setLayerBlend(l.id, BlendModes.NORMAL) }
            for (g in 1 until BlendModes.groups.size) {
                val inGroup = current.group == g
                BlendGroupHeader(BlendModes.groups[g], BlendModes.groupHelp[g], g in open, if (inGroup) current.name else null) {
                    open = if (g in open) open - g else open + g
                }
                if (g in open) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in BlendModes.inGroup(g)) BlendThumb(vm, mode, mode.id == current.id) {
                        vm.setLayerBlend(l.id, mode.id)
                    }
                }
            }
            Text("Consejo: para luces y destellos con fondo negro prueba «Aclarar › Pantalla» o «Sobreexposición lineal». " +
                "Para sombras o texturas, «Oscurecer › Multiplicar». Mantén pulsado un modo para leer qué hace.",
                color = StudioMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun BlendGroupHeader(name: String, help: String, expanded: Boolean, chosen: String?, expandable: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).height(46.dp).clip(RoundedCornerShape(10.dp))
        .background(StudioTile).clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (expandable) Icon(if (expanded) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight, null, tint = StudioText,
            modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = if (expandable) 2.dp else 6.dp)) {
            Text(name, color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(help, color = StudioMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chosen != null) {
            Text(chosen, color = StudioPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.CheckCircle, null, tint = StudioPink, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlendThumb(vm: AppViewModel, mode: BlendModes.Mode, selected: Boolean, onClick: () -> Unit) {
    // Miniatura de ejemplo propia de la app (atardecer + esfera de color), igual para todos.
    val ctx = LocalContext.current
    val key = "blend_%02d".format(mode.id)
    var bmp by remember(key) { mutableStateOf(ThumbCache.peek(key)) }
    LaunchedEffect(key) { if (bmp == null) bmp = ThumbCache.load(ctx, key) }
    Column(Modifier.width(72.dp).combinedClickable(onLongClick = { vm.showHint("${mode.name}: ${mode.help}", 5000L) }, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF15151B))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) StudioPink else Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            val img = bmp
            if (img != null) Image(img, mode.name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
            else Icon(Icons.Filled.Opacity, null, tint = StudioMuted, modifier = Modifier.size(22.dp))
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = StudioPink,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
        }
        Spacer(Modifier.height(3.dp))
        Text(mode.name, color = if (selected) StudioPink else StudioText, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 2,
            textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Hoja «Movimiento» de la capa. */
@Composable
fun LayerMotionSheet(vm: AppViewModel) {
    val l = vm.selectedLayerObj() ?: run { LaunchedEffect(Unit) { vm.layerMotionOpen = false }; return }
    var amt by remember(l.id) { mutableFloatStateOf(l.motionAmt) }
    Column(Modifier.fillMaxSize()) {
        CapHeader("Movimiento de la imagen") { vm.layerMotionOpen = false }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowChips(ImageLayers.motionNames, l.motion) { vm.setLayerMotion(l.id, it) }
            Text(ImageLayers.motionHelp[l.motion.coerceIn(0, ImageLayers.motionHelp.size - 1)], color = StudioMuted, fontSize = 12.sp)
            if (l.motion != 0) {
                Text("Fuerza", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = amt, onValueChange = { amt = it; vm.setLayerMotionAmt(l.id, it, live = true) },
                    onValueChangeFinished = { vm.setLayerMotionAmt(l.id, amt, live = false) }, colors = capSliderColors())
                Text("Vueltas por vídeo (el final empalma con el inicio)", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in listOf(1, 2, 3, 4, 6)) SmallChoice("$n", l.motionCycles == n) { vm.setLayerMotionCycles(l.id, n) }
                }
                if (l.motion == ImageLayers.MOTION_SPIN) Text("Fuerza menor a la mitad = gira al revés.", color = StudioMuted, fontSize = 11.sp)
            }
            Text("Puedes combinarlo con los fotogramas clave (Clave) para mover la imagen de un lugar a otro.",
                color = StudioMuted, fontSize = 11.sp)
            // v44.24: profundidad de la capa dentro de la «Profundidad 2.5D».
            Text("Profundidad 2.5D", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp))
            val names = com.zeta.fondo.model.LayerParallax.choices.map { it.first }
            val sel = com.zeta.fondo.model.LayerParallax.choices.indexOfFirst { kotlin.math.abs(it.second - l.depth) < .01f }
                .let { if (it < 0) com.zeta.fondo.model.LayerParallax.choices.indices.minByOrNull { i ->
                    kotlin.math.abs(com.zeta.fondo.model.LayerParallax.choices[i].second - l.depth) } ?: 0 else it }
            FlowChips(names, sel) { vm.setLayerDepth(l.id, com.zeta.fondo.model.LayerParallax.choices[it].second) }
            Text(if (sel == 0) "Pegada: se mueve igual que lo que tiene debajo (un sol en el cielo, como el cielo)."
                else "Lejos se mueve poco; Delante, lo que más.",
                color = StudioMuted, fontSize = 11.sp)
            if (!vm.isEffectOn("parallax"))
                Text("Se nota cuando activas Animar › Profundidad 2.5D.", color = StudioMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FlowChips(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    // Filas de 3 para que entren en cualquier pantalla.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i -> Box(Modifier.weight(1f)) { SmallChoice(names[i], i == selected) { onSelect(i) } } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Inicio y duración de una capa. */
@Composable
fun LayerTimeDialog(vm: AppViewModel, id: Long, onDismiss: () -> Unit) {
    val l = vm.layers.firstOrNull { it.id == id } ?: run { LaunchedEffect(Unit) { onDismiss() }; return }
    val total = vm.exportSettings.loopSec.toFloat().coerceAtLeast(.5f)
    var start by remember(id) { mutableFloatStateOf(if (l.durSec > 0f) l.startSec else 0f) }
    var dur by remember(id) { mutableFloatStateOf(if (l.durSec > 0f) l.durSec else total) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tiempo de la imagen", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Aparece: ${String.format(java.util.Locale.US, "%.1f", start)} s", color = StudioText, fontSize = 13.sp)
            Slider(value = start, onValueChange = { start = it; dur = dur.coerceAtMost(total - it).coerceAtLeast(.1f) },
                valueRange = 0f..(total - .1f), colors = capSliderColors())
            Text("Dura: ${String.format(java.util.Locale.US, "%.1f", dur)} s", color = StudioText, fontSize = 13.sp)
            Slider(value = dur, onValueChange = { dur = it.coerceAtMost(total - start) }, valueRange = .1f..total, colors = capSliderColors())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SmallChoice("Todo el vídeo", false) { start = 0f; dur = total } }
                Box(Modifier.weight(1f)) { SmallChoice("Desde el cabezal", false) {
                    start = (vm.scrubT * total).coerceIn(0f, total - .1f); dur = total - start } }
            }
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioPink)
                .clickable { vm.setLayerSpan(id, start, dur); onDismiss() }, contentAlignment = Alignment.Center) {
                Text("Listo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Color de una capa en la línea de tiempo según su modo (luces = naranja, sombras = azul…). */
internal fun layerColor(l: ImageLayer): Color = when (BlendModes.byId(l.blend).group) {
    1 -> Color(0xFF3E5A8C)
    2 -> Color(0xFFB8732E)
    3 -> Color(0xFF7A4FB0)
    4 -> Color(0xFF2E8C7A)
    5 -> Color(0xFFB04F7A)
    6 -> Color(0xFF5A5B66)
    else -> Color(0xFF4A4B58)
}
