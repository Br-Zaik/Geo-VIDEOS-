package com.zeta.fondo.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.ImageRect
import com.zeta.fondo.model.ZoneShapes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * v44.23 · Zona con forma del efecto en edición (círculo o rectángulo), como las formas de
 * Alight Motion: arrastrar dentro = mover · esquinas = tamaño · lados = estirar ·
 * punto rosa = girar · dos dedos sobre la forma = tamaño y giro · dos dedos fuera = zoom.
 */

private enum class ZoneDrag { NONE, MOVE, SCALE, STRETCH_X, STRETCH_Y, ROTATE, PINCH, VIEW }

@Composable
fun ZoneShapeOverlay(vm: AppViewModel, rect: ImageRect, modifier: Modifier) {
    val rectState = rememberUpdatedState(rect)
    val id = vm.editingEffect ?: return
    val shapeNow = vm.zoneShapeOf(id)
    val view = LocalView.current
    var snapC by remember { mutableStateOf(false) }
    var snapR by remember { mutableStateOf(false) }
    Canvas(modifier.pointerInput(id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val r = rectState.value
            val sh = vm.zoneShapeOf(id) ?: return@awaitEachGesture
            val aspect = vm.aspect
            fun toScreen(u: Float, v: Float) = Offset(r.left + (u - vm.viewX) * vm.viewScale * r.width / vm.cropW,
                r.top + (v - vm.viewY) * vm.viewScale * r.height / vm.cropH)
            fun toUv(o: Offset) = Offset(vm.viewX + r.normX(o.x) * vm.cropW / vm.viewScale,
                vm.viewY + r.normY(o.y) * vm.cropH / vm.viewScale)
            // Píxeles de pantalla por «ancho de imagen» (las medidas de la forma van en anchos).
            val kx = vm.viewScale * r.width / vm.cropW
            val center = toScreen(sh.cx, sh.cy)
            val cs = ZoneShapes.corners(sh, aspect).map { toScreen(it.first, it.second) }
            val mids = (0..3).map { (cs[it] + cs[(it + 1) % 4]) / 2f }
            val up = (mids[0] - center).let { v -> v / v.getDistance().coerceAtLeast(1f) }
            val knob = mids[0] + up * 36.dp.toPx()
            val grab = 26.dp.toPx()
            val rr = Math.toRadians(sh.rotDeg.toDouble())
            val axisX = Offset(cos(rr).toFloat(), sin(rr).toFloat())
            val axisY = Offset(-sin(rr).toFloat(), cos(rr).toFloat())
            val uv0 = toUv(down.position)
            var mode = when {
                (down.position - knob).getDistance() < grab -> ZoneDrag.ROTATE
                cs.any { (down.position - it).getDistance() < grab } -> ZoneDrag.SCALE
                (down.position - mids[1]).getDistance() < grab || (down.position - mids[3]).getDistance() < grab -> ZoneDrag.STRETCH_X
                (down.position - mids[0]).getDistance() < grab || (down.position - mids[2]).getDistance() < grab -> ZoneDrag.STRETCH_Y
                ZoneShapes.inside(sh, uv0.x, uv0.y, aspect) -> ZoneDrag.MOVE
                else -> ZoneDrag.NONE
            }
            val onShape = mode != ZoneDrag.NONE
            val d0 = (down.position - center).getDistance().coerceAtLeast(1f)
            val a0 = atan2((down.position - center).y, (down.position - center).x)
            var pinchScale = 1f; var pinchRot = 0f; var pinchPan = Offset.Zero
            if (onShape) { vm.beginZoneTransform(); down.consume() }
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if (pressed >= 2 && mode != ZoneDrag.PINCH && mode != ZoneDrag.VIEW) {
                    mode = if (onShape) ZoneDrag.PINCH else ZoneDrag.VIEW
                }
                when (mode) {
                    ZoneDrag.NONE -> {}
                    ZoneDrag.VIEW -> {
                        val c = event.calculateCentroid(useCurrent = true)
                        val pan = event.calculatePan()
                        if (c.isSpecified) vm.transformView(event.calculateZoom(), pan.x / r.width.coerceAtLeast(1f),
                            pan.y / r.height.coerceAtLeast(1f), r.normX(c.x), r.normY(c.y))
                    }
                    ZoneDrag.MOVE -> {
                        val d = toUv(ch.position) - uv0
                        var nx = sh.cx + d.x; var ny = sh.cy + d.y
                        val snap = abs(nx - .5f) * kx < 10.dp.toPx() && abs(ny - .5f) * kx / aspect < 10.dp.toPx()
                        if (snap) { nx = .5f; ny = .5f }
                        if (snap && !snapC) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        snapC = snap
                        vm.transformZone(id, mapOf("$id.zx" to nx, "$id.zy" to ny))
                    }
                    ZoneDrag.SCALE -> {
                        val s = (ch.position - center).getDistance() / d0
                        vm.transformZone(id, mapOf("$id.zw" to sh.hw * s, "$id.zh" to sh.hh * s))
                    }
                    ZoneDrag.STRETCH_X -> {
                        val half = abs((ch.position - center).let { it.x * axisX.x + it.y * axisX.y }).coerceAtLeast(6f)
                        vm.transformZone(id, mapOf("$id.zw" to half / kx))
                    }
                    ZoneDrag.STRETCH_Y -> {
                        val half = abs((ch.position - center).let { it.x * axisY.x + it.y * axisY.y }).coerceAtLeast(6f)
                        vm.transformZone(id, mapOf("$id.zh" to half / kx))
                    }
                    ZoneDrag.ROTATE -> {
                        val a = atan2((ch.position - center).y, (ch.position - center).x)
                        var deg = sh.rotDeg + (a - a0) * 180f / PI.toFloat()
                        val nearest = (deg / 90f).roundToInt() * 90f
                        val snapped = abs(deg - nearest) < 3f
                        if (snapped) deg = nearest
                        if (snapped && !snapR) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        snapR = snapped
                        vm.transformZone(id, mapOf("$id.zrot" to deg))
                    }
                    ZoneDrag.PINCH -> {
                        pinchScale *= event.calculateZoom()
                        pinchRot += event.calculateRotation()
                        pinchPan += event.calculatePan()
                        val c2 = toUv(center + pinchPan) - toUv(center)
                        vm.transformZone(id, mapOf("$id.zw" to sh.hw * pinchScale, "$id.zh" to sh.hh * pinchScale,
                            "$id.zrot" to sh.rotDeg + pinchRot, "$id.zx" to sh.cx + c2.x, "$id.zy" to sh.cy + c2.y))
                    }
                }
                if (mode != ZoneDrag.NONE) event.changes.forEach { it.consume() }
            }
            snapC = false; snapR = false
            if (onShape) vm.endZoneTransform(id)
        }
    }) {
        val sh = shapeNow ?: return@Canvas
        val r = rect
        val aspect = vm.aspect
        fun toScreen(u: Float, v: Float) = Offset(r.left + (u - vm.viewX) * vm.viewScale * r.width / vm.cropW,
            r.top + (v - vm.viewY) * vm.viewScale * r.height / vm.cropH)
        val kx = vm.viewScale * r.width / vm.cropW
        val c = toScreen(sh.cx, sh.cy)
        val cs = ZoneShapes.corners(sh, aspect).map { toScreen(it.first, it.second) }
        val shadow = Color.Black.copy(alpha = .55f)
        val hw = sh.hw * kx; val hh = sh.hh * kx
        // La forma (con su borde suave dibujado como un segundo contorno punteado por dentro).
        rotate(sh.rotDeg, c) {
            val tl = c - Offset(hw, hh)
            val sz = Size(hw * 2f, hh * 2f)
            val inner = (1f - sh.soft * .95f).coerceIn(.05f, 1f)
            val itl = c - Offset(hw * inner, hh * inner)
            val isz = Size(hw * 2f * inner, hh * 2f * inner)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            if (sh.type == ZoneShapes.CIRCLE) {
                drawOval(StudioPink.copy(alpha = .16f), tl, sz)
                drawOval(shadow, tl, sz, style = Stroke(4.dp.toPx()))
                drawOval(Color.White, tl, sz, style = Stroke(2.dp.toPx()))
                if (inner < .97f) drawOval(Color.White.copy(alpha = .7f), itl, isz, style = Stroke(1.dp.toPx(), pathEffect = dash))
            } else {
                drawRect(StudioPink.copy(alpha = .16f), tl, sz)
                drawRect(shadow, tl, sz, style = Stroke(4.dp.toPx()))
                drawRect(Color.White, tl, sz, style = Stroke(2.dp.toPx()))
                if (inner < .97f) drawRect(Color.White.copy(alpha = .7f), itl, isz, style = Stroke(1.dp.toPx(), pathEffect = dash))
            }
        }
        // Caja de los tiradores (en el círculo, la caja que lo contiene).
        if (sh.type == ZoneShapes.CIRCLE) {
            val p = Path().apply { moveTo(cs[0].x, cs[0].y); for (i in 1..3) lineTo(cs[i].x, cs[i].y); close() }
            drawPath(p, Color.White.copy(alpha = .45f), style = Stroke(1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
        }
        val mids = (0..3).map { (cs[it] + cs[(it + 1) % 4]) / 2f }
        val up = (mids[0] - c).let { v -> v / v.getDistance().coerceAtLeast(1f) }
        val knob = mids[0] + up * 36.dp.toPx()
        drawLine(shadow, mids[0], knob, 4.dp.toPx())
        drawLine(Color.White, mids[0], knob, 1.6.dp.toPx())
        drawCircle(shadow, 12.dp.toPx(), knob)
        drawCircle(if (snapR) SnapYellow else StudioPink, 10.dp.toPx(), knob)
        drawArc(Color.White, -200f, 220f, false, knob - Offset(5.dp.toPx(), 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()),
            style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        for (p in cs) { drawCircle(shadow, 10.dp.toPx(), p); drawCircle(Color.White, 8.dp.toPx(), p) }
        for (mp in mids) {
            drawRoundRect(shadow, mp - Offset(7.dp.toPx(), 7.dp.toPx()), Size(14.dp.toPx(), 14.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(Color.White, mp - Offset(5.dp.toPx(), 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
        // Centro (con imán).
        drawCircle(shadow, 7.dp.toPx(), c)
        drawCircle(if (snapC) SnapYellow else Color.White, 5.dp.toPx(), c)
    }
}
