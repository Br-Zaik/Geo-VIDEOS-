package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.CropHandle
import com.zeta.fondo.model.CropRect
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.ImageRect
import com.zeta.fondo.model.LayerCrop
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * v44.27 · Recortar una capa de imagen, en el mismo lugar (sin abrir otra pantalla):
 *  - cajita flotante debajo de la capa elegida: ✂ Recortar · ⟳ 90° · ⇋ Voltear;
 *  - marco con esquinas, lados y cuadrícula sobre la imagen ORIGINAL completa (lo que queda fuera
 *    se oscurece) y su tamaño en píxeles arriba;
 *  - barra de abajo: ✕ · proporciones · Girar 90° · Original · ✓ Listo.
 */

/** Rectángulo (en píxeles de la pantalla) donde se dibuja la imagen girada, con margen para los tiradores. */
private fun cropArea(w: Float, h: Float, rw: Int, rh: Int, side: Float, top: Float, bottom: Float): Rect {
    val aw = (w - 2f * side).coerceAtLeast(1f)
    val ah = (h - top - bottom).coerceAtLeast(1f)
    val k = min(aw / rw.coerceAtLeast(1), ah / rh.coerceAtLeast(1))
    val dw = rw * k; val dh = rh * k
    val left = (w - dw) / 2f
    val t = top + (ah - dh) / 2f
    return Rect(left, t, left + dw, t + dh)
}

/** Con espejo, el marco se ve al revés de izquierda a derecha (la misma función va y vuelve). */
private fun mirror(r: CropRect, flip: Boolean): CropRect = if (flip) CropRect(1f - r.r, r.t, 1f - r.l, r.b) else r

@Composable
fun LayerCropOverlay(vm: AppViewModel, modifier: Modifier) {
    val s = vm.layerCrop ?: return
    val image = remember(s) { s.preview.asImageBitmap() }
    val flip = vm.layers.firstOrNull { it.id == s.layerId }?.flipX == true
    val rot = vm.layerCropRot
    val (rw, rh) = LayerCrop.rotatedSize(s.srcW, s.srcH, rot)
    val (ow, oh) = LayerCrop.outputSize(s.srcW, s.srcH, vm.layerCropRect, rot)
    val density = LocalDensity.current
    val side = with(density) { 26.dp.toPx() }
    val top = with(density) { 40.dp.toPx() }
    // Abajo queda sitio para el tamaño en píxeles (arriba a la izquierda va el aviso del editor).
    val bottom = with(density) { 36.dp.toPx() }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(s) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val (gw, gh) = LayerCrop.rotatedSize(s.srcW, s.srcH, vm.layerCropRot)
                val area = cropArea(size.width.toFloat(), size.height.toFloat(), gw, gh, side, top, bottom)
                val flipNow = vm.layers.firstOrNull { it.id == s.layerId }?.flipX == true
                val r0 = mirror(vm.layerCropRect, flipNow)
                val tl = Offset(area.left + r0.l * area.width, area.top + r0.t * area.height)
                val br = Offset(area.left + r0.r * area.width, area.top + r0.b * area.height)
                val grab = 30.dp.toPx()
                val edge = 22.dp.toPx()
                val p = down.position
                val free = vm.layerCropRatio <= 0f
                val corners = listOf(CropHandle.TL to tl, CropHandle.TR to Offset(br.x, tl.y),
                    CropHandle.BL to Offset(tl.x, br.y), CropHandle.BR to br)
                val cornerHit = corners.minByOrNull { (it.second - p).getDistance() }
                    ?.takeIf { (it.second - p).getDistance() < grab }?.first
                // Los lados solo con proporción libre (con proporción fija, las esquinas la mantienen).
                val edgeHit = if (!free) null else when {
                    abs(p.x - tl.x) < edge && p.y in tl.y..br.y -> CropHandle.L
                    abs(p.x - br.x) < edge && p.y in tl.y..br.y -> CropHandle.R
                    abs(p.y - tl.y) < edge && p.x in tl.x..br.x -> CropHandle.T
                    abs(p.y - br.y) < edge && p.x in tl.x..br.x -> CropHandle.B
                    else -> null
                }
                val inside = p.x in tl.x..br.x && p.y in tl.y..br.y
                val handle = cornerHit ?: edgeHit ?: (if (inside) CropHandle.MOVE else null)
                if (handle == null) return@awaitEachGesture
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val du = (ch.position.x - p.x) / area.width.coerceAtLeast(1f)
                    val dv = (ch.position.y - p.y) / area.height.coerceAtLeast(1f)
                    val nd = LayerCrop.drag(r0, handle, du, dv, vm.layerCropRatio, gw, gh)
                    vm.moveLayerCropFrame(mirror(nd, flipNow))
                    ch.consume()
                }
            }
        }) {
            drawRect(Color(0xF2101115))
            val area = cropArea(size.width, size.height, rw, rh, side, top, bottom)
            // La imagen original, girada (y con espejo) como se verá.
            val quarter = rot % 180 == 90
            val dw = if (quarter) area.height else area.width
            val dh = if (quarter) area.width else area.height
            val c = area.center
            withTransform({
                if (flip) scale(-1f, 1f, c)
                rotate(rot.toFloat(), c)
            }) {
                drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((c.x - dw / 2f).roundToInt(), (c.y - dh / 2f).roundToInt()),
                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium)
            }
            val r = mirror(vm.layerCropRect, flip)
            val tl = Offset(area.left + r.l * area.width, area.top + r.t * area.height)
            val br = Offset(area.left + r.r * area.width, area.top + r.b * area.height)
            // Lo que queda fuera, oscurecido.
            val shade = Color.Black.copy(alpha = .62f)
            drawRect(shade, area.topLeft, Size(area.width, (tl.y - area.top).coerceAtLeast(0f)))
            drawRect(shade, Offset(area.left, br.y), Size(area.width, (area.bottom - br.y).coerceAtLeast(0f)))
            drawRect(shade, Offset(area.left, tl.y), Size((tl.x - area.left).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)))
            drawRect(shade, Offset(br.x, tl.y), Size((area.right - br.x).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)))
            val w = br.x - tl.x
            val h = br.y - tl.y
            val grid = Color.White.copy(alpha = .5f)
            for (k in 1..2) {
                drawLine(grid, Offset(tl.x + w * k / 3f, tl.y), Offset(tl.x + w * k / 3f, br.y), 1.dp.toPx())
                drawLine(grid, Offset(tl.x, tl.y + h * k / 3f), Offset(br.x, tl.y + h * k / 3f), 1.dp.toPx())
            }
            drawRect(Color.White, tl, Size(w, h), style = Stroke(1.6.dp.toPx()))
            val cl = min(20.dp.toPx(), min(w, h) / 2.2f)
            val sw = 4.dp.toPx()
            for ((pt, dx, dy) in listOf(Triple(tl, 1f, 1f), Triple(Offset(br.x, tl.y), -1f, 1f),
                    Triple(Offset(tl.x, br.y), 1f, -1f), Triple(br, -1f, -1f))) {
                drawLine(Color.White, pt, pt + Offset(cl * dx, 0f), sw, cap = StrokeCap.Round)
                drawLine(Color.White, pt, pt + Offset(0f, cl * dy), sw, cap = StrokeCap.Round)
            }
            if (vm.layerCropRatio <= 0f) {
                val bar = min(16.dp.toPx(), min(w, h) / 3f)
                val mx = (tl.x + br.x) / 2f; val my = (tl.y + br.y) / 2f
                drawLine(Color.White, Offset(mx - bar, tl.y), Offset(mx + bar, tl.y), sw, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(mx - bar, br.y), Offset(mx + bar, br.y), sw, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(tl.x, my - bar), Offset(tl.x, my + bar), sw, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(br.x, my - bar), Offset(br.x, my + bar), sw, cap = StrokeCap.Round)
            }
        }
        Text("$ow × $oh", color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .5f))
                .padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** Barra del recorte: ✕ a la izquierda, opciones al medio (se deslizan) y ✓ Listo fijo a la derecha. */
@Composable
fun LayerCropBar(vm: AppViewModel, modifier: Modifier) {
    Row(modifier.background(Night), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 8.dp, end = 4.dp).size(width = 40.dp, height = 52.dp)
            .clip(RoundedCornerShape(10.dp)).background(StudioTile).clickable { vm.cancelLayerCrop() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Cancelar", tint = StudioText, modifier = Modifier.size(22.dp))
        }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            LayerCrop.ratios.forEach { (label, ratio) ->
                RatioToolChip(label, ratio, abs(vm.layerCropRatio - ratio) < 1e-3f) { vm.chooseLayerCropRatio(ratio) }
            }
            ToolChip("Girar 90°", Icons.Filled.RotateRight, false) { vm.rotateLayerCrop() }
            ToolChip("Original", Icons.Filled.Restore, false) { vm.resetLayerCrop() }
        }
        Box(Modifier.padding(start = 4.dp, end = 8.dp).size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp)).background(StudioGradient).clickable { vm.applyLayerCrop() },
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text("Listo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Botón de proporción con su rectángulo dibujado a la proporción real (Libre = esquinas). */
@Composable
private fun RatioToolChip(label: String, ratio: Float, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) StudioPink else StudioText
    Column(Modifier.width(60.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(24.dp)) {
            val m = 2.dp.toPx()
            val box = size.width - 2f * m
            val (w, h) = when {
                ratio <= 0f -> box to box * .8f
                ratio >= 1f -> box to box / ratio
                else -> box * ratio to box
            }
            val tl = Offset((size.width - w) / 2f, (size.height - h) / 2f)
            val st = 1.8.dp.toPx()
            if (ratio <= 0f) drawRect(tint, tl, Size(w, h), style = Stroke(st,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            else drawRoundRect(tint, tl, Size(w, h), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(st))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * Cajita flotante con lo más usado de la capa elegida, justo debajo de su recuadro (o encima si
 * no cabe): se ve sin buscar en la barra. Se esconde mientras se arrastra la imagen.
 */
@Composable
fun LayerQuickBar(vm: AppViewModel, layer: ImageLayer, rect: ImageRect, areaW: Float, areaH: Float) {
    val meta = vm.meta ?: return
    val f = vm.layerHandleFrame(layer) ?: return
    val density = LocalDensity.current
    fun toScreen(u: Float, v: Float) = Offset(rect.left + (u - vm.viewX) * vm.viewScale * rect.width / vm.cropW,
        rect.top + (v - vm.viewY) * vm.viewScale * rect.height / vm.cropH)
    val cs = ImageLayers.corners(f, meta.w, meta.h).map { toScreen(it.first, it.second) }
    // El punto rosa de girar también cuenta (con la imagen girada puede quedar abajo o a un lado).
    val center = toScreen(f.x, f.y)
    val top = (cs[0] + cs[1]) / 2f
    val up = (top - center).let { v -> val d = v.getDistance().coerceAtLeast(1f); v / d }
    val knob = top + up * with(density) { 36.dp.toPx() }
    val knobR = with(density) { 14.dp.toPx() }
    val minX = min(cs.minOf { it.x }, knob.x - knobR); val maxX = maxOf(cs.maxOf { it.x }, knob.x + knobR)
    val minY = min(cs.minOf { it.y }, knob.y - knobR); val maxY = maxOf(cs.maxOf { it.y }, knob.y + knobR)
    val pillW = with(density) { (3 * 58 + 8).dp.toPx() }
    val pillH = with(density) { 52.dp.toPx() }
    val gap = with(density) { 16.dp.toPx() }
    val margin = with(density) { 6.dp.toPx() }
    val x = ((minX + maxX) / 2f - pillW / 2f).coerceIn(margin, (areaW - pillW - margin).coerceAtLeast(margin))
    var y = maxY + gap
    if (y + pillH > areaH - margin) {
        // Encima (el punto de girar ya cuenta en minY); si tampoco cabe, abajo dentro del lienzo.
        y = minY - pillH - gap
        if (y < margin) y = (areaH - pillH - margin).coerceAtLeast(margin)
    }
    Row(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .74f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (layer.text != null) {
            // Texto: los efectos ya pasan por detrás de las letras; «Detrás» = detrás del personaje.
            QuickButton("Editar", Icons.Filled.Edit, StudioPink) { vm.editTextLayer(layer.id) }
            QuickButton("90°", Icons.Filled.RotateRight, StudioText) { vm.rotateLayer90(layer.id) }
            QuickButton(if (layer.behind) "Detrás" else "Delante", Icons.Filled.Person,
                if (layer.behind) StudioPink else StudioText) { vm.toggleLayerBehind(layer.id) }
        } else {
            QuickButton("Recortar", Icons.Filled.Crop, StudioPink) { vm.startLayerCrop(layer.id) }
            QuickButton("90°", Icons.Filled.RotateRight, StudioText) { vm.rotateLayer90(layer.id) }
            QuickButton("Voltear", Icons.Filled.Flip, if (layer.flipX) StudioPink else StudioText) { vm.flipLayer(layer.id) }
        }
    }
}

@Composable
private fun QuickButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(58.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 10.5.sp, maxLines = 1)
    }
}
