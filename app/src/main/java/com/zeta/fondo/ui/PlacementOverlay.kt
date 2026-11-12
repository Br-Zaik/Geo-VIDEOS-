package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.ImageRect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * v40 · Herramienta Posición del bloque seleccionado, separada del zoom del lienzo:
 *  - 1 dedo sobre la imagen: mueve el ORIGEN del efecto (el punto de la explosión, del humo…);
 *  - aro con tirador: agranda o achica el efecto (su ajuste Tamaño), sin tocar el zoom;
 *  - flecha: cambia la dirección (si el efecto la tiene);
 *  - 2 dedos: siempre zoom/desplazamiento del lienzo, como en el resto del editor.
 * Cada cambio va al bloque seleccionado (sus ajustes propios) y se ve y exporta igual.
 */

private enum class PlaceMode { MOVE, SCALE, AIM, VIEW }

private val presetSpots = arrayOf(floatArrayOf(.5f, .5f), floatArrayOf(.5f, 0f), floatArrayOf(.5f, 1f),
    floatArrayOf(0f, .5f), floatArrayOf(1f, .5f), floatArrayOf(0f, 0f), floatArrayOf(1f, 0f),
    floatArrayOf(0f, 1f), floatArrayOf(1f, 1f))

private val defaultAngles = mapOf("smoke" to 270f, "blast" to 0f, "blood" to 20f, "slash" to 35f, "speed" to 0f)

/** Origen efectivo (0..1 de la imagen) del bloque, igual que lo calcula RenderParams. */
internal fun clipOrigin(vm: AppViewModel, clip: EffectClip): Pair<Float, Float> {
    val origin = (vm.clipValue(clip, "origin") ?: 0f).toInt().coerceIn(0, 8)
    if (origin != 0) return presetSpots[origin][0] to presetSpots[origin][1]
    val ox = vm.clipValue(clip, "originX")
    val oy = vm.clipValue(clip, "originY")
    if (clip.effectId == "rays" && ox == null) return (vm.clipValue(clip, "x") ?: .5f) to 0f
    val def = com.zeta.fondo.model.Wind.defaultOrigin[clip.effectId]
    return (ox ?: def?.first ?: .5f) to (oy ?: def?.second ?: .5f)
}

/** Radio aproximado del efecto (en altos de imagen) para dibujar el aro. */
private fun effectRadius(id: String, size: Float?): Float {
    val def = Effects.all.firstOrNull { it.id == id }?.params?.firstOrNull { it.key == "size" }?.def ?: 1f
    val s = size ?: def
    return when (id) {
        "blast" -> s * .45f
        "smoke" -> .05f + .06f * s
        else -> (.12f * s / def.coerceAtLeast(.01f))
    }.coerceIn(.03f, .9f)
}

@Composable
fun EffectPlacementOverlay(vm: AppViewModel, rect: ImageRect, modifier: Modifier) {
    val rectState = rememberUpdatedState(rect)
    val clipNow = vm.placementClip()
    Canvas(modifier.pointerInput(clipNow?.uid) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val r = rectState.value
            val clip = vm.placementClip() ?: return@awaitEachGesture
            val id = clip.effectId
            fun toScreen(x: Float, y: Float) = Offset(r.left + (x - vm.viewX) * vm.viewScale * r.width / vm.cropW,
                r.top + (y - vm.viewY) * vm.viewScale * r.height / vm.cropH)
            fun toUv(o: Offset) = vm.screenToUv(r.normX(o.x), r.normY(o.y))
            val (ox, oy) = clipOrigin(vm, clip)
            val center = toScreen(ox, oy)
            val pxPerUnit = r.height * vm.viewScale / vm.cropH
            val size0 = vm.clipValue(clip, "size")
            val ringPx = (effectRadius(id, size0) * pxPerUnit).coerceAtLeast(26.dp.toPx())
            val ringKnob = center + Offset(ringPx * .7071f, ringPx * .7071f)
            val yUp = id in yUpEffects
            val angle0 = Math.toRadians((vm.clipValue(clip, "direction") ?: defaultAngles[id] ?: 0f).toDouble())
            val dir = Offset(cos(angle0).toFloat(), (if (yUp) -sin(angle0) else sin(angle0)).toFloat())
            val aimKnob = center + dir * (ringPx + 34.dp.toPx())
            val grab = 30.dp.toPx()
            var mode = when {
                vm.canScale(id) && (down.position - ringKnob).getDistance() < grab -> PlaceMode.SCALE
                vm.canAim(id) && (down.position - aimKnob).getDistance() < grab -> PlaceMode.AIM
                else -> PlaceMode.MOVE
            }
            // Mover conserva la distancia entre el dedo y el centro (no salta bajo el dedo),
            // salvo si se toca lejos del efecto: entonces el origen va a donde se tocó.
            val far = (down.position - center).getDistance() > ringPx + grab
            val grabOffset = if (far) Offset.Zero else center - down.position
            val startDist = (down.position - center).getDistance().coerceAtLeast(1f)
            val startSize = size0 ?: 1f
            vm.beginClipTransform()
            if (mode == PlaceMode.MOVE && far) {
                val uv = toUv(down.position)
                vm.transformClip(clip.uid, originX = uv.x, originY = uv.y)
            }
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) mode = PlaceMode.VIEW
                val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                when (mode) {
                    PlaceMode.VIEW -> {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val c = event.calculateCentroid(useCurrent = true)
                        if (c.isSpecified) vm.transformView(zoom, pan.x / r.width.coerceAtLeast(1f),
                            pan.y / r.height.coerceAtLeast(1f), r.normX(c.x), r.normY(c.y))
                    }
                    PlaceMode.MOVE -> {
                        val uv = toUv(ch.position + grabOffset)
                        vm.transformClip(clip.uid, originX = uv.x, originY = uv.y)
                    }
                    PlaceMode.SCALE -> {
                        val d = (ch.position - center).getDistance()
                        vm.transformClip(clip.uid, size = startSize * d / startDist)
                    }
                    PlaceMode.AIM -> {
                        val v = ch.position - center
                        if (v.getDistance() > 6f) {
                            var deg = Math.toDegrees(atan2((if (yUp) -v.y else v.y).toDouble(), v.x.toDouble())).toFloat()
                            if (deg < 0f) deg += 360f
                            vm.transformClip(clip.uid, direction = deg)
                        }
                    }
                }
                event.changes.forEach { it.consume() }
            }
            vm.endClipTransform()
        }
    }) {
        val clip = clipNow ?: return@Canvas
        val r = rect
        val id = clip.effectId
        fun toScreen(x: Float, y: Float) = Offset(r.left + (x - vm.viewX) * vm.viewScale * r.width / vm.cropW,
            r.top + (y - vm.viewY) * vm.viewScale * r.height / vm.cropH)
        val (ox, oy) = clipOrigin(vm, clip)
        val c = toScreen(ox, oy)
        val pxPerUnit = r.height * vm.viewScale / vm.cropH
        val ringPx = (effectRadius(id, vm.clipValue(clip, "size")) * pxPerUnit).coerceAtLeast(26.dp.toPx())
        val shadow = Color.Black.copy(alpha = .55f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        drawCircle(shadow, ringPx, c, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, ringPx, c, style = Stroke(2.dp.toPx(), pathEffect = dash))
        // Centro (mover).
        drawCircle(shadow, 13.dp.toPx(), c)
        drawCircle(StudioPink, 10.dp.toPx(), c)
        drawLine(Color.White, c - Offset(6.dp.toPx(), 0f), c + Offset(6.dp.toPx(), 0f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, c - Offset(0f, 6.dp.toPx()), c + Offset(0f, 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // Tirador de tamaño.
        if (vm.canScale(id)) {
            val k = c + Offset(ringPx * .7071f, ringPx * .7071f)
            drawCircle(shadow, 12.dp.toPx(), k)
            drawCircle(Color.White, 9.dp.toPx(), k)
            drawLine(StudioPink, k - Offset(4.dp.toPx(), 4.dp.toPx()), k + Offset(4.dp.toPx(), 4.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        // Flecha de dirección.
        if (vm.canAim(id)) {
            val yUp = id in yUpEffects
            val a = Math.toRadians((vm.clipValue(clip, "direction") ?: defaultAngles[id] ?: 0f).toDouble())
            val dir = Offset(cos(a).toFloat(), (if (yUp) -sin(a) else sin(a)).toFloat())
            val from = c + dir * ringPx
            val tip = c + dir * (ringPx + 34.dp.toPx())
            drawLine(shadow, from, tip, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(StudioOrange, from, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(StudioOrange, 8.dp.toPx(), tip)
            val side = Offset(-dir.y, dir.x)
            drawLine(Color.White, tip, tip - dir * 6.dp.toPx() + side * 4.dp.toPx(), strokeWidth = 2.dp.toPx())
            drawLine(Color.White, tip, tip - dir * 6.dp.toPx() - side * 4.dp.toPx(), strokeWidth = 2.dp.toPx())
        }
    }
}
