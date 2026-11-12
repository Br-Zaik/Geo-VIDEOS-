package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.Wind
import java.util.Locale

/**
 * v44 · Joystick 2D, como un mando: el punto marca HACIA DÓNDE (ángulo) y CON QUÉ FUERZA
 * (distancia al centro). Centro = suave; borde = fuerte. Imán de 15° para direcciones exactas.
 * Ángulos como en toda la app: 0° derecha, 90° abajo.
 */
@Composable
fun WindJoystick(label: String, angleDeg: Float, force: Float, enabled: Boolean = true,
                 onChange: (angle: Float, force: Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.size(104.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun set(p: Offset) {
                        val r = size.width / 2f - 10.dp.toPx()
                        val x = (p.x - size.width / 2f) / r
                        val y = (p.y - size.height / 2f) / r
                        val (a, f) = Wind.fromStick(x, y)
                        change(a, f)
                    }
                    detectTapGestures { set(it) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        val r = size.width / 2f - 10.dp.toPx()
                        val (a, f) = Wind.fromStick((ch.position.x - size.width / 2f) / r, (ch.position.y - size.height / 2f) / r)
                        change(a, f)
                    }
                }
        ) {
            val c = center
            val r = size.minDimension / 2f - 10.dp.toPx()
            val dim = if (enabled) 1f else .4f
            drawCircle(Dusk2, r + 6.dp.toPx(), c)
            drawCircle(Mist.copy(alpha = .35f * dim), r, c, style = Stroke(1.dp.toPx()))
            drawCircle(Mist.copy(alpha = .2f * dim), r * .5f, c, style = Stroke(1.dp.toPx()))
            // cruz de referencia
            drawLine(Mist.copy(alpha = .15f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(Mist.copy(alpha = .15f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            val (sx, sy) = Wind.toStick(angleDeg, force)
            val knob = Offset(c.x + sx * r, c.y + sy * r)
            val col = (if (enabled) StudioPink else Mist).copy(alpha = dim)
            drawLine(col, c, knob, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            // punta de flecha en el sentido del movimiento
            val len = kotlin.math.sqrt(sx * sx + sy * sy).coerceAtLeast(1e-3f)
            val d = Offset(sx / len, sy / len)
            val side = Offset(-d.y, d.x)
            val tip = knob
            drawLine(col, tip, tip - d * 9.dp.toPx() + side * 6.dp.toPx(), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(col, tip, tip - d * 9.dp.toPx() - side * 6.dp.toPx(), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = dim), 9.dp.toPx(), knob)
            drawCircle(col, 6.dp.toPx(), knob)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${arrowFor(angleDeg)} ${angleDeg.toInt()}°", color = StudioText, fontSize = 13.sp)
            Text("Fuerza ${String.format(Locale.US, "%.1f", force).replace('.', ',')} · ${forceName(force)}",
                color = StudioMuted, fontSize = 12.sp)
            if (!enabled) Text("Sigue el viento global", color = StudioMuted, fontSize = 11.sp)
        }
    }
}

private fun arrowFor(a: Float): String {
    val arrows = arrayOf("→", "↘", "↓", "↙", "←", "↖", "↑", "↗")
    return arrows[(((a % 360f) + 360f) % 360f / 45f + .5f).toInt() % 8]
}

private fun forceName(f: Float): String = when {
    f < .7f -> "suave"
    f < 1.3f -> "media"
    f < 1.7f -> "fuerte"
    else -> "vendaval"
}

/** Dirección + fuerza de un efecto con «Seguir el viento global». */
@Composable
fun EffectWindControls(vm: AppViewModel, id: String) {
    val follows = Wind.follows(vm.settings + (vm.fxTarget(id)?.overrides ?: emptyMap()), id)
    val s = vm.settings + (vm.fxTarget(id)?.overrides ?: emptyMap())
    val (angle, force) = Wind.resolve(s, id)
    WindJoystick("Dirección y fuerza", angle, force, enabled = !follows) { a, f ->
        vm.setSettingsBatch(mapOf("$id.direction" to a, "$id.force" to f))
    }
    if (id in Wind.windDriven) {
        SwitchRow("Seguir el viento global", (vm.fx["$id.followWind"] ?: 0f) > .5f) { on ->
            vm.setSettingsBatch(mapOf("$id.followWind" to if (on) 1f else 0f))
            if (on && !Wind.globalOn(vm.settings)) {
                vm.setSettingsBatch(mapOf("wind.on" to 1f, "wind.direction" to angle, "wind.force" to force))
                vm.showHint("Viento global activado (Ajustes › Viento)")
            }
        }
    }
}

/** Ajustes › Viento: un viento para toda la escena; cada efecto decide si lo sigue. */
@Composable
fun WindPanel(vm: AppViewModel) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        CapHeader("Viento global") { vm.applyAndClose() }
        PanelScroll {
            val on = Wind.globalOn(vm.settings)
            SwitchRow("Viento global", on) { vm.setSettingsBatch(mapOf("wind.on" to if (it) 1f else 0f)) }
            WindJoystick("Viento", vm.settings["wind.direction"] ?: 0f, vm.settings["wind.force"] ?: 1f, enabled = on) { a, f ->
                vm.setSettingsBatch(mapOf("wind.direction" to a, "wind.force" to f))
            }
            LabeledSlider("Ráfagas", vm.settings["wind.gust"] ?: 0f, 0f, 1f, false, enabled = on) {
                vm.setSettingsBatch(mapOf("wind.gust" to it))
            }
            LabeledSlider("Turbulencia", vm.settings["wind.turb"] ?: 0f, 0f, 1f, false, enabled = on) {
                vm.setSettingsBatch(mapOf("wind.turb" to it))
            }
            Text("Las ráfagas y la turbulencia mueven lluvia, nieve, niebla, hojas, polvo y el movimiento Natural.",
                color = StudioMuted, fontSize = 11.sp)
            val active = Wind.windDriven.filter { vm.isEffectOn(it) }
            if (active.isEmpty()) EmptyHint("Añade lluvia, nieve, hojas, humo… para que el viento los mueva")
            else {
                Text("Efectos que siguen el viento", color = StudioMuted, fontSize = 12.sp)
                active.forEach { id ->
                    SwitchRow(timelineTitle(id), (vm.settings["$id.followWind"] ?: 0f) > .5f, enabled = on) {
                        vm.setSettingsBatch(mapOf("$id.followWind" to if (it) 1f else 0f))
                    }
                }
            }
        }
    }
}
