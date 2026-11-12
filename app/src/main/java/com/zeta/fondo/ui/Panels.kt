package com.zeta.fondo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.ExportState
import com.zeta.fondo.engine.Ambient
import com.zeta.fondo.engine.CodecSupport
import com.zeta.fondo.engine.ExportResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.viewinterop.AndroidView
import com.zeta.fondo.engine.ExportPlanner
import com.zeta.fondo.export.ShareUtil
import com.zeta.fondo.model.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
}

@Composable
fun MasksPanel(vm: AppViewModel) {
    PanelColumn {
        Text("Máscaras", color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            vm.maskNames.forEachIndexed { i, label ->
                StudioChoice(label, vm.selectedMaskLayer == i) {
                    if (vm.playing) vm.togglePlay()
                    vm.selectMask(i)
                }
            }
            StudioSmallButton("Nueva", Icons.Filled.Add) { vm.addMask() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioSmallButton("Pintar", Icons.Filled.Brush, Modifier.weight(1f), selected = !vm.eraser) {
                vm.eraser = false
            }
            StudioSmallButton("Borrar", Icons.Filled.DeleteOutline, Modifier.weight(1f), selected = vm.eraser) {
                vm.eraser = true
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { LabeledSlider("Pincel", vm.brushDp, 6f, 140f, true) { vm.brushDp = it } }
            Box(Modifier.weight(1f)) { LabeledSlider("Suavizado", vm.feather, 0f, 1f, false) { vm.feather = it } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioSmallButton("Rellenar", Icons.Filled.Check, Modifier.weight(1f)) { vm.fillLayer(false) }
            StudioSmallButton("Vaciar", Icons.Filled.Clear, Modifier.weight(1f)) { vm.fillLayer(true) }
        }
        SwitchRow("Ver máscara", vm.showMasks) { vm.toggleMasks() }
        val selected = vm.selectedMaskLayer
        if (selected > 0 && selected < vm.maskNames.size) {
            var name by remember(selected) { mutableStateOf(vm.maskNames[selected]) }
            OutlinedTextField(value = name, onValueChange = { name = it; vm.renameMask(selected, it) },
                label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

    }
}

@Composable
fun CameraPanel(vm: AppViewModel) {
    val def = Effects.all.first { it.id == "cam" }
    PanelColumn {
        Text("Cámara", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SwitchRow("Activar", (vm.settings["cam.on"] ?: 0f) > .5f) {
            vm.setSetting("cam.on", if (it) 1f else 0f)
        }
        def.params.forEach { EffectParameter(vm, def, it) }
    }
}

@Composable
fun PresetsPanel(vm: AppViewModel) {
    PanelColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Presets", color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("${Presets.all.size}", color = StudioMuted, fontSize = 13.sp)
        }
        Presets.all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { p ->
                    Column(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(12.dp))
                        .background(StudioTile).clickable { vm.applyPreset(p) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(if (p.manga) Icons.Filled.MenuBook else Icons.Filled.AutoAwesome,
                            null, tint = StudioPurple, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(p.name, color = StudioText, fontSize = 10.sp,
                            fontWeight = FontWeight.Medium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        StudioSmallButton("Quitar efectos", Icons.Filled.Clear, Modifier.fillMaxWidth()) { vm.clearEffects() }
    }
}

internal fun effectIcon(id: String): ImageVector = when (id) {
    "sky" -> Icons.Filled.Cloud
    "water" -> Icons.Filled.Waves
    "dispersion" -> Icons.Filled.ScatterPlot
    "flow" -> Icons.Filled.Timeline
    "drift" -> Icons.Filled.CloudQueue
    "speed" -> Icons.Filled.Speed
    "rain" -> Icons.Filled.Grain
    "ripple" -> Icons.Filled.Waves
    "bubbles" -> Icons.Filled.BubbleChart
    "snow" -> Icons.Filled.AcUnit
    "sakura" -> Icons.Filled.LocalFlorist
    "leaves" -> Icons.Filled.Eco
    "stars" -> Icons.Filled.Star
    "flies", "glint" -> Icons.Filled.Flare
    "fog" -> Icons.Filled.Cloud
    "smoke", "ash", "reveal" -> Icons.Filled.BlurOn
    "blast" -> Icons.Filled.Flare
    "blood" -> Icons.Filled.InvertColors
    "slash" -> Icons.Filled.Bolt
    "rays" -> Icons.Filled.LightMode
    "flash" -> Icons.Filled.FlashOn
    "energy" -> Icons.Filled.AutoAwesome
    "electric" -> Icons.Filled.Bolt
    "shock" -> Icons.Filled.ElectricBolt
    "pulse" -> Icons.Filled.RadioButtonChecked
    "cam" -> Icons.Filled.Videocam
    "parallax" -> Icons.Filled.Layers
    "mesh" -> Icons.Filled.Waves
    "focus" -> Icons.Filled.CenterFocusStrong
    "runes" -> Icons.Filled.FilterTiltShift
    "tone" -> Icons.Filled.Contrast
    "grain" -> Icons.Filled.Grain
    "ink" -> Icons.Filled.Brush
    "hatch" -> Icons.Filled.Dehaze
    "heat" -> Icons.Filled.LocalFireDepartment
    "embers" -> Icons.Filled.Whatshot
    "comet" -> Icons.Filled.RocketLaunch
    "shards" -> Icons.Filled.Diamond
    "dust" -> Icons.Filled.ScatterPlot
    "style" -> Icons.Filled.Palette
    "cine" -> Icons.Filled.Movie
    "sway" -> Icons.Filled.Park
    "natural" -> Icons.Filled.Air
    "flame" -> Icons.Filled.LocalFireDepartment
    "eyes" -> Icons.Filled.Visibility
    "blink" -> Icons.Filled.VisibilityOff
    "cloth" -> Icons.Filled.Waves
    else -> Icons.Filled.AutoAwesome
}
private val duplicateIds = setOf("ash", "leaves", "embers", "dust", "bubbles", "electric",
    "focus", "shock", "flash", "tone", "grain", "ink")
private val directionalIds = setOf("rain", "snow", "sakura", "fog", "rays", "energy", "speed", "ash",
    "leaves", "embers", "dust", "bubbles", "electric", "focus", "shock", "comet", "runes",
    "pulse", "smoke", "shards")

internal fun effectShortTitle(effect: EffectDef): String = when (effect.id) {
    "flow" -> "Movimiento"
    "drift" -> "Nubes"
    "ripple" -> "Ondas"
    "glint" -> "Brillos"
    "sway" -> "Viento"
    "heat" -> "Calor"
    "sakura" -> "Sakura"
    "stars" -> "Estrellas"
    "flies" -> "Luciérnagas"
    "rays" -> "Rayos de luz"
    "energy" -> "Aura anime"
    "speed" -> "Líneas manga"
    "cam" -> "Cámara"
    "parallax" -> "Profundidad"
    "mesh" -> "Deformar"
    "natural" -> "Movimiento natural"
    "comet" -> "Cometas"
    "runes" -> "Magia"
    "hatch" -> "Rayado"
    "pulse" -> "Anillos"
    "smoke" -> "Humo"
    "shards" -> "Cristales"
    "ash" -> "Cenizas"
    "leaves" -> "Hojas"
    "embers" -> "Chispas"
    "dust" -> "Polvo"
    "electric" -> "Electricidad"
    "focus" -> "Enfoque"
    "shock" -> "Onda de choque"
    "flash" -> "Destello"
    "tone" -> "Screentone"
    "grain" -> "Papel manga"
    "ink" -> "Tinta"
    "style" -> "Estilo manga"
    "cine" -> "Calidad cine"
    "flame" -> "Llamas"
    "eyes" -> "Brillo de ojos"
    "blink" -> "Parpadeo"
    "cloth" -> "Pelo y ropa"
    "bokeh" -> "Bokeh"
    "sparkle" -> "Destellos"
    "lightleak" -> "Luz ambiental"
    "waterfall" -> "Cascada"
    "reflect" -> "Reflejo de agua"
    "blackaura" -> "Aura oscura"
    "inkdrip" -> "Tinta animada"
    "dramatic" -> "Sombra dramática"
    else -> effect.title
}

/** Catálogo visible directamente en el espacio fijo del editor. */
@Composable
fun EffectsPanel(vm: AppViewModel, selectedId: String?, onSelect: (String?) -> Unit) {
    val selected = Effects.all.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clickable(enabled = selected != null) { onSelect(null) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GridView, null, tint = StudioPurple, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Efectos", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (selected != null) Text(effectShortTitle(selected), color = StudioMuted, fontSize = 12.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            if (selected == null) {
                Icon(Icons.Filled.DeleteOutline, "Quitar efectos", tint = StudioMuted,
                    modifier = Modifier.size(38.dp).clickable { vm.clearEffects() }.padding(9.dp))
            } else if (selected.toggleable) {
                val enabled = (vm.settings["${selected.id}.on"] ?: if (selected.defaultOn) 1f else 0f) > .5f
                Icon(if (enabled) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    if (enabled) "Desactivar efecto" else "Activar efecto",
                    tint = if (enabled) StudioPurple else StudioMuted,
                    modifier = Modifier.size(38.dp).clickable {
                        vm.setSetting("${selected.id}.on", if (enabled) 0f else 1f)
                    }.padding(8.dp))
            }
        }
        if (selected == null) {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(Effects.all, key = { it.id }) { effect ->
                    val enabled = (vm.settings["${effect.id}.on"] ?: if (effect.defaultOn) 1f else 0f) > .5f
                    Column(Modifier.fillMaxWidth().height(75.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (enabled) StudioPurple.copy(alpha = .13f) else StudioTile)
                        .clickable {
                            onSelect(effect.id)
                            vm.activeMotionEffect = effect.id.takeIf { it in directionalIds }
                            if (effect.toggleable && !enabled) vm.setSetting("${effect.id}.on", 1f)
                        }.padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.fillMaxWidth().height(27.dp), contentAlignment = Alignment.Center) {
                            Icon(effectIcon(effect.id), null, modifier = Modifier.size(24.dp),
                                tint = if (enabled) StudioPurple else StudioText)
                            if (enabled) Icon(Icons.Filled.Check, "Activo", tint = StudioPurple,
                                modifier = Modifier.align(Alignment.TopEnd).size(12.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(effectShortTitle(effect), color = StudioText, fontSize = 11.sp, lineHeight = 13.sp,
                            maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            val active = !selected.toggleable || (vm.settings["${selected.id}.on"]
                ?: if (selected.defaultOn) 1f else 0f) > .5f
            val strength = selected.params.firstOrNull { it.key in listOf("int", "amp", "amount", "zoom") }
                ?: selected.params.firstOrNull { it.key !in listOf("cycles", "zone", "speed") }
            val speed = selected.params.firstOrNull { it.key == "cycles" || it.key == "speed" }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val hint = when (selected.id) {
                    "heat", "sway", "flame", "cloth" -> "Pinta la zona verde en Máscara"
                    "ripple", "glint", "drift", "eyes", "blink" -> "Pinta la zona azul en Máscara"
                    else -> null
                }
                if (hint != null) Text(hint, color = StudioMuted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (strength != null) Box(Modifier.weight(1f)) { PrimaryEffectParameter(vm, selected, strength, active) }
                    if (speed != null && speed != strength) Box(Modifier.weight(1f)) { PrimaryEffectParameter(vm, selected, speed, active) }
                }
                selected.params.filter { it != strength && it != speed }.forEach { parameter ->
                    PrimaryEffectParameter(vm, selected, parameter, active)
                }
                if (selected.id in directionalIds) MotionDirection(vm, selected.id)
                Text("Aplicar en", color = StudioMuted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val mask = (vm.settings["${selected.id}.mask"] ?: -1f).toInt()
                    StudioChoice("Predeterminado", mask == -1) { vm.setEffectMask(selected.id, -1) }
                    vm.maskNames.forEachIndexed { index, name ->
                        StudioChoice(name, mask == index) { vm.setEffectMask(selected.id, index) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PrimaryEffectParameter(vm: AppViewModel, effect: EffectDef,
                                   param: ParamDef, enabled: Boolean) {
    val key = "${effect.id}.${param.key}"
    val value = vm.settings[key] ?: param.def
    val label = when (param.key) {
        "cycles" -> "Velocidad"
        "amp" -> "Intensidad"
        "int" -> "Intensidad"
        "amount" -> "Movimiento"
        "zoom" -> "Zoom"
        else -> param.label
    }
    if (param.kind == Kind.TOGGLE) {
        SwitchRow(label, value > .5f, enabled = enabled) {
            vm.setSetting(key, if (it) 1f else 0f)
        }
    } else {
        LabeledSlider(label, value, param.min, param.max,
            param.kind == Kind.INT, enabled = enabled) { vm.setSetting(key, it) }
    }
}

@Composable
private fun MotionDirection(vm: AppViewModel, id: String) {
    LabeledSlider("Dirección", vm.settings["$id.direction"] ?: 0f,
        0f, 360f, true, suffix = "°") { vm.setSetting("$id.direction", it) }
    LabeledSlider("Origen X", vm.settings["$id.originX"] ?: .5f,
        0f, 1f, false) { vm.setSetting("$id.origin", 0f); vm.setSetting("$id.originX", it) }
    LabeledSlider("Origen Y", vm.settings["$id.originY"] ?: .5f,
        0f, 1f, false) { vm.setSetting("$id.origin", 0f); vm.setSetting("$id.originY", it) }
}

/** Efectos que miden la dirección con 90° hacia arriba (convención del shader). */
internal val yUpEffects = setOf("natural", "blood", "parallax", "mesh", "cam")

@Composable
internal fun EffectParameter(vm: AppViewModel, effect: EffectDef, p: ParamDef) {
    val key = "${effect.id}.${p.key}"
    // v40: con varios bloques del efecto, cada uno muestra SUS valores.
    // v44: si el control está animado, muestra el valor en el cabezal.
    val value = vm.keyedValue(key) ?: vm.fx[key] ?: p.def
    val keyable = p.kind == Kind.FLOAT && vm.canKeyframe(key) && vm.keyClip(effect.id) != null &&
        !(effect.id == "zonemotion" && p.key == "int")
    if (keyable) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { EffectParameterBody(vm, effect, p, key, value) }
            KeyDiamond(vm, key)
        }
        return
    }
    EffectParameterBody(vm, effect, p, key, value)
}

@Composable
private fun EffectParameterBody(vm: AppViewModel, effect: EffectDef, p: ParamDef, key: String, value: Float) {
    val label = when(p.key) {
        "cycles" -> "Velocidad"
        "amp", "int" -> "Intensidad"
        "freq" -> "Frecuencia"
        "dens" -> "Densidad"
        "dir", "direction" -> "Dirección"
        "size" -> "Tamaño"
        else -> p.label.substringBefore(" (")
    }
    if (p.key == "direction" && effect.id in com.zeta.fondo.model.Wind.directional && effect.id !in yUpEffects) {
        // v44: joystick 2D (dirección + fuerza) en vez del dial.
        EffectWindControls(vm, effect.id)
    } else if (p.key == "direction" || p.key == "dir") {
        DirectionDial(label, value, yUp = effect.id in yUpEffects) { vm.setSetting(key, it) }
    } else if (effect.id == "zonemotion" && p.key == "int") {
        LabeledSlider("Fuerza", value / p.max * 100f, 0f, 100f, true, suffix = "%") {
            vm.setSetting(key, it / 100f * p.max)
        }
    } else if (p.kind == Kind.TOGGLE) SwitchRow(label, value > .5f) {
        vm.setSetting(key, if (it) 1f else 0f)
    } else LabeledSlider(label, value, p.min, p.max, p.kind == Kind.INT) {
        vm.setSetting(key, it)
    }
}

@Composable
fun AudioPanel(vm: AppViewModel) {
    val e = vm.exportSettings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // v44.17: la música entra como bloque (como un efecto), no como pista de todo el vídeo.
        uri?.let { vm.importUserSound(it, asMusic = true) }
    }
    val anim = rememberInfiniteTransition(label = "sounds")
    val animatedTime = anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    PanelColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Música", color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (e.audioName.isNotBlank()) Text(e.audioName, color = StudioMuted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StudioSmallButton(if (e.audioName.isBlank()) "Importar" else "Cambiar", Icons.Filled.AudioFile) {
                picker.launch(arrayOf("audio/*"))
            }
            if (e.audioName.isNotBlank()) androidx.compose.material3.IconButton(onClick = vm::removeAudio) {
                Icon(Icons.Filled.DeleteOutline, "Quitar música", tint = StudioMuted)
            }
        }
        if (e.audioName.isNotBlank()) {
            AudioTrim(vm, e)
            LabeledSlider("Volumen de la música", e.soundVolume, 0f, 1f, false) { vm.updateExport(e.copy(soundVolume = it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { SwitchRow("Sonido", e.audioEnabled) { vm.updateExport(e.copy(audioEnabled = it)) } }
                Box(Modifier.weight(1f)) { SwitchRow("Fundido", e.audioFade) { vm.updateExport(e.copy(audioFade = it)) } }
            }
            SwitchRow("Repetir si la canción es más corta", e.audioLoop) { vm.updateExport(e.copy(audioLoop = it)) }
            // v44 · Beat Sync (análisis de la señal, sin IA): marca los golpes y propone efectos en ellos.
            Text("Ritmo (Beat Sync)", color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            LabeledSlider("Sensibilidad", vm.beatSensitivity, 0f, 1f, false) { vm.beatSensitivity = it }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallChoice(if (vm.beatMarkers.isEmpty()) "Detectar golpes" else "Volver a detectar (${vm.beatMarkers.size})", false) { vm.detectBeats() }
                if (vm.beatMarkers.isNotEmpty()) {
                    SmallChoice("Destello en cada golpe", false) { vm.addEffectOnBeats("flash") }
                    SmallChoice("Temblor", false) { vm.addEffectOnBeats("impact") }
                    SmallChoice("Onda", false) { vm.addEffectOnBeats("shock") }
                    SmallChoice("Quitar marcas", false) { vm.clearBeats() }
                }
            }
        } else {
            Text("Elige una canción o grabación de tu teléfono. Luego escoges la parte exacta que suena.",
                color = StudioMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(4.dp))
        // v44: los ambientes son ahora bloques de sonido por escenas (Ajustar en la línea de tiempo).
        SoundLibrary(vm)
    }
}

/** Formatos con su rectángulo dibujado a la proporción real. */
private val ratioOptions = listOf("Completo" to 0f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "1:1" to 1f,
    "3:4" to 3f / 4f, "4:3" to 4f / 3f)

@Composable
private fun RatioChip(label: String, ratio: Float, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(62.dp).height(64.dp).clip(shape)
        .background(if (selected) StudioGradient else Brush.horizontalGradient(listOf(StudioTile, StudioTile)))
        .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            val box = size.minDimension
            if (ratio <= 0f) {
                // Imagen completa: marco con montaña
                val rw = box; val rh = box * 0.72f
                val tl = androidx.compose.ui.geometry.Offset((size.width - rw) / 2f, (size.height - rh) / 2f)
                drawRoundRect(StudioText, tl, androidx.compose.ui.geometry.Size(rw, rh),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tl.x + rw * 0.15f, tl.y + rh * 0.85f); lineTo(tl.x + rw * 0.45f, tl.y + rh * 0.4f)
                    lineTo(tl.x + rw * 0.65f, tl.y + rh * 0.65f); lineTo(tl.x + rw * 0.75f, tl.y + rh * 0.5f)
                    lineTo(tl.x + rw * 0.9f, tl.y + rh * 0.85f); close()
                }
                drawPath(path, StudioText)
            } else {
                val rw = if (ratio >= 1f) box else box * ratio
                val rh = if (ratio >= 1f) box / ratio else box
                drawRoundRect(StudioText, androidx.compose.ui.geometry.Offset((size.width - rw) / 2f, (size.height - rh) / 2f),
                    androidx.compose.ui.geometry.Size(rw, rh), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = StudioText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun CanvasPanel(vm: AppViewModel) {
    val e = vm.exportSettings
    PanelColumn {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ratioOptions.forEachIndexed { i, (name, ratio) ->
                RatioChip(name, ratio, e.framing == i && e.res != 6) {
                    val res = if (e.res == 6) 3 else e.res
                    vm.updateExport(e.copy(framing = i, res = res, cropCx = -1f, cropCy = -1f, cropZoom = 1f))
                    if (i != 0) vm.startCropEdit()
                }
            }
        }
        if (vm.cropEditing) {
            Text("Arrastra el marco para elegir la parte de la imagen y pellizca para agrandarla o achicarla.",
                color = StudioMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSmallButton("Centrar", Icons.Filled.Refresh, Modifier.weight(1f)) { vm.resetCrop() }
                StudioSmallButton("Listo", Icons.Filled.Check, Modifier.weight(1f), selected = true) { vm.finishCropEdit() }
            }
        } else {
            StudioSmallButton("Ajustar encuadre", Icons.Filled.Crop, Modifier.fillMaxWidth()) { vm.startCropEdit() }
        }
        LabeledSlider("Duración del bucle", e.loopSec.toFloat(), 2f, 60f, true, suffix = " s") {
            vm.updateExport(e.copy(loopSec = it.toInt()))
        }
    }
}

@Composable
private fun MiniChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(36.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) StudioGradient else Brush.horizontalGradient(listOf(StudioTile, StudioTile)))
        .clickable(onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = StudioText, fontSize = 13.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun CompactRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = StudioMuted, fontSize = 13.sp, modifier = Modifier.width(78.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Configuración de exportación: corta y directa. El resultado se muestra en ExportResultCard. */
@Composable
fun ExportPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val e = vm.exportSettings
    val meta = vm.meta ?: return
    val state = vm.exportState
    var advancedExport by remember { mutableStateOf(
        e.format != 0 || e.res !in listOf(1, 2, 3, 4) || e.fps == 24 ||
            e.codec != 0 || e.repeats != 1 || e.overlayOnly
    ) }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val bodyMaxHeight = (screenHeightDp * 0.67f).dp.coerceIn(330.dp, 510.dp)
    // v44: en Android 13+ se pide UNA vez el permiso de notificaciones para ver el progreso
    // de la exportación fuera de la app. Si se rechaza, se exporta igual.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        vm.startExport()
    }
    fun doExport() {
        if (Build.VERSION.SDK_INT >= 33 && !vm.notificationsAsked() &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            vm.markNotificationsAsked()
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        // v44.26: desde Android 10 guardar en la Galería no pide permiso.
        vm.startExport()
    }

    // v27: altura según contenido. No reserva un bloque negro vacío debajo de las opciones.
    // Si una pantalla es muy baja, solo el cuerpo central hace scroll; Exportar queda visible.
    Column(modifier.fillMaxWidth().background(StudioPanel)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (state is ExportState.Failed) {
                Text("No se pudo exportar: ${state.message}", color = Danger, fontSize = 13.sp)
            }
            // v44: lo que se ve en la vista previa y lo que sale en el vídeo, sin sorpresas.
            val hiddenCount = vm.effectClips.count { it.hidden }
            if (vm.soloActive) Text("«Solo» es solo para mirar: el vídeo incluye todos los bloques visibles.",
                color = StudioMuted, fontSize = 12.sp)
            if (hiddenCount > 0) Text(if (hiddenCount == 1) "1 bloque oculto no se exporta." else "$hiddenCount bloques ocultos no se exportan.",
                color = StudioMuted, fontSize = 12.sp)
            // v28: una sola selección de resolución. Ya no hay un selector arriba y otro
            // abajo que puedan contradecirse. Tocar de nuevo Original/Otro vuelve a 1080p.
            fun chooseResolution(index: Int) {
                vm.updateExport(vm.exportSettings.copy(res = index))
            }
            // v36: se consulta el codificador real del teléfono (en segundo plano) para
            // saber qué resolución/fps acepta. Las que no admite se ven atenuadas.
            val encodable by produceState<Map<Int, Pair<Int, Int>>>(emptyMap(), e.fps, e.codec, e.framing, e.cropZoom, meta.id) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    listOf(4, 3, 2, 1, 5, 0).associateWith { r ->
                        val (rw, rh) = ExportPlanner.targetSize(meta.w, meta.h, e.copy(res = r))
                        CodecSupport.displaySize(e.codec, rw, rh, e.fps)
                    }
                }
            }
            fun supported(index: Int): Boolean {
                val got = encodable[index] ?: return true
                val (rw, rh) = ExportPlanner.targetSize(meta.w, meta.h, e.copy(res = index))
                return got.first * got.second >= rw * rh * 0.98f
            }
            // v41 · como en un editor de vídeo: deslizadores con marcas (Resolución,
            // Cuadros por segundo, Tasa de bits) y el tamaño estimado del archivo.
            val resOptions = listOf("720p" to 4, "1080p" to 3, "2K" to 2, "4K" to 1)
            val resPos = resOptions.indexOfFirst { it.second == e.res }
            TickSlider("Resolución", resOptions.map { it.first }, resPos,
                valueText = if (resPos < 0) when (e.res) { 5 -> "QHD"; 0 -> "Original"; else -> "Personalizada" } else null,
                enabled = { supported(resOptions[it].second) }) { chooseResolution(resOptions[it].second) }
            val fpsOptions = listOf(24, 25, 30, 50, 60)
            TickSlider("Cuadros por segundo", fpsOptions.map { "$it" }, fpsOptions.indexOf(e.fps)) {
                vm.updateExport(vm.exportSettings.copy(fps = fpsOptions[it]))
            }
            if (e.format == 0) TickSlider("Tasa de bits", ExportPlanner.qualityLabels,
                e.quality.coerceIn(0, ExportPlanner.qualityLabels.lastIndex)) {
                vm.updateExport(vm.exportSettings.copy(quality = it))
            }
            CompactRow("Duración") {
                listOf(4, 6, 10, 15).forEach { sec ->
                    MiniChoice("${sec}s", e.loopSec == sec) { vm.updateExport(vm.exportSettings.copy(loopSec = sec)) }
                }
                if (e.loopSec !in listOf(4, 6, 10, 15)) Text("${e.loopSec}s", color = StudioText, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Opciones avanzadas", color = StudioMuted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                Icon(if (advancedExport) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    if (advancedExport) "Ocultar opciones avanzadas" else "Mostrar opciones avanzadas",
                    tint = StudioText, modifier = Modifier.size(44.dp)
                        .clickable { advancedExport = !advancedExport }.padding(10.dp))
            }
            if (advancedExport) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("QHD" to 5, "Original" to 0, "Otro" to 6).forEach { (label, index) ->
                    MiniChoice(label, e.res == index, Modifier.weight(1f)) { chooseResolution(index) }
                }
            }
            val (rw0, rh0) = ExportPlanner.targetSize(meta.w, meta.h, e)
            val planned = if (e.format == 0) encodable[e.res] else null
            val (w, h) = planned ?: (rw0 to rh0)
            if (planned != null && planned.first * planned.second < rw0 * rh0 * 0.98f) Text(
                "Tu teléfono no codifica ${rw0}×$rh0 a ${e.fps} fps: se exportará a ${w}×$h con la mejor calidad posible.",
                color = StudioOrange, fontSize = 12.sp)
            val seconds = e.loopSec * (if (e.format == 0) e.repeats else 1)
            val mb = ExportPlanner.bitrate(w, h, e.fps, e.quality).toDouble() * seconds / 8.0 / 1_000_000.0
            val formatName = if (e.format == 0) (if (e.codec == 1) "HEVC" else "MP4") else "PNG"
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.format == 0) "Tamaño estimado del archivo" else "${e.loopSec * e.fps} imágenes PNG",
                    color = StudioMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (e.format == 0) Text(String.format(java.util.Locale.US, if (mb < 10) "%.1f MB" else "%.0f MB", mb),
                    color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(if (e.format == 0) "${w}×$h · ${e.fps} fps · $seconds s · $formatName" else "${w}×$h",
                color = StudioMuted, fontSize = 11.sp)
            val sounds = buildList {
                if (e.audioName.isNotBlank() && e.audioEnabled) add("música")
                if (e.hasAmbient) add("${e.ambient.count { it.value > 0.01f }} sonidos de ambiente")
            }
            if (e.format == 0) Text(
                if (sounds.isEmpty()) "Sin sonido" else "Sonido: " + sounds.joinToString(" + "),
                color = StudioMuted, fontSize = 12.sp
            )

            if (advancedExport) {
            CompactRow("Formato") {
                MiniChoice("MP4", e.format == 0) { vm.updateExport(e.copy(format = 0)) }
                MiniChoice("PNG", e.format == 1) { vm.updateExport(e.copy(format = 1)) }
            }
            if (e.res == 6) {
                LabeledSlider("Ancho", e.customW.toFloat(), 128f, 7680f, true, suffix = " px") {
                    vm.updateExport(e.copy(customW = it.toInt()))
                }
                LabeledSlider("Alto", e.customH.toFloat(), 128f, 7680f, true, suffix = " px") {
                    vm.updateExport(e.copy(customH = it.toInt()))
                }
            }
            if (e.format == 0) {
                CompactRow("Códec") {
                    listOf("H.264" to 0, "HEVC" to 1).forEach { (name, i) ->
                        if (CodecSupport.usable(i)) MiniChoice(name, e.codec == i) { vm.updateExport(e.copy(codec = i)) }
                    }
                }
                CompactRow("Repetir") {
                    MiniChoice("−", false) { vm.updateExport(e.copy(repeats = (e.repeats - 1).coerceAtLeast(1))) }
                    Text("${e.repeats}×", color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    MiniChoice("+", false) { vm.updateExport(e.copy(repeats = (e.repeats + 1).coerceAtMost(10))) }
                }
            }
            SwitchRow("Solo efectos (fondo negro)", e.overlayOnly) { vm.updateExport(e.copy(overlayOnly = it)) }
            }
        }

        Box(
            Modifier.fillMaxWidth().background(StudioPanel)
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
        ) {
            StudioPrimary("Exportar", Icons.Filled.FileUpload, Modifier.fillMaxWidth()) { doExport() }
        }
    }
}

/** Resultado: vídeo en bucle dentro de la app, datos en una línea y acciones directas. */
@Composable
fun ExportResultCard(vm: AppViewModel, r: ExportResult, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(StudioPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = StudioPurple, modifier = Modifier.size(22.dp))
            Text("Guardado en la Galería", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp))
            Icon(Icons.Filled.Close, "Cerrar", tint = StudioMuted, modifier = Modifier.size(36.dp)
                .clickable(onClick = onClose).padding(7.dp))
        }
        val file = r.file
        if (!r.isPng && file != null && file.isFile) {
            val ratio = r.width.toFloat() / r.height.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(factory = { c ->
                    android.widget.VideoView(c).apply {
                        setVideoURI(android.net.Uri.fromFile(file))
                        setOnPreparedListener { mp -> mp.isLooping = true; start() }
                    }
                }, modifier = Modifier.heightIn(max = 300.dp).aspectRatio(ratio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(12.dp)))
            }
        }
        val mb = (file?.length() ?: 0L) / 1_000_000.0
        Text(if (r.isPng) "${r.width}×${r.height} · ${r.frames} imágenes · ${r.galleryPath}"
            else String.format(java.util.Locale.US, "%d×%d · %d fps · %.1f MB", r.width, r.height, r.fps, mb),
            color = StudioMuted, fontSize = 12.sp)
        r.reducedFrom?.let { Text("Tu teléfono no acepta $it; se ajustó al máximo posible.", color = StudioMuted, fontSize = 12.sp) }
        if (!r.isPng && file != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSmallButton("Alight Motion", Icons.Filled.Movie, Modifier.weight(1f), selected = true) {
                    if (!ShareUtil.sendToAlight(ctx, file)) vm.message = "Elige Alight Motion en el menú o impórtalo desde la Galería."
                }
                StudioSmallButton("Compartir", Icons.Filled.Share, Modifier.weight(1f)) { ShareUtil.shareChooser(ctx, file) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioSmallButton("Galería", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) {
                    if (!ShareUtil.openVideo(ctx, file)) vm.message = "Búscalo en Galería › ${r.galleryPath}"
                }
                StudioSmallButton("Otra", Icons.Filled.Refresh, Modifier.weight(1f)) { vm.dismissExportResult() }
            }
        } else {
            StudioSmallButton("Otra exportación", Icons.Filled.Refresh, Modifier.fillMaxWidth()) { vm.dismissExportResult() }
        }
    }
}

/**
 * v41 · Deslizador con marcas fijas (como «Resolución» o «Cuadros por segundo» al exportar en
 * un editor de vídeo): se toca o arrastra y siempre cae en una marca. Los nombres van debajo.
 */
@Composable
fun TickSlider(title: String, labels: List<String>, selected: Int, valueText: String? = null,
               enabled: (Int) -> Boolean = { true }, onSelect: (Int) -> Unit) {
    val n = labels.size.coerceAtLeast(2)
    val pick by rememberUpdatedState(onSelect)
    val can by rememberUpdatedState(enabled)
    val current by rememberUpdatedState(selected)
    val density = LocalDensity.current
    val pad = 12.dp
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = StudioText, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueText ?: labels.getOrNull(selected) ?: "", color = StudioText, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp).pointerInput(n) {
            val padPx = with(density) { pad.toPx() }
            fun indexAt(x: Float): Int {
                val span = (size.width - 2 * padPx).coerceAtLeast(1f)
                return ((x - padPx) / span * (n - 1)).roundToInt().coerceIn(0, n - 1)
            }
            fun choose(x: Float) {
                val i = indexAt(x)
                if (i != current && can(i)) pick(i)
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                choose(down.position.x)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    choose(ch.position.x)
                    ch.consume()
                }
            }
        }) {
            val span = maxWidth - pad * 2
            fun xOf(i: Int): Dp = pad + span * (i.toFloat() / (n - 1))
            Box(Modifier.align(Alignment.CenterStart).padding(horizontal = pad).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3B46)))
            if (selected >= 0) Box(Modifier.align(Alignment.CenterStart).offset(x = pad).width(span * (selected.toFloat() / (n - 1)))
                .height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            for (i in 0 until n) {
                val on = selected >= 0 && i <= selected
                Box(Modifier.align(Alignment.CenterStart).offset(x = xOf(i) - 3.dp).size(6.dp).clip(CircleShape)
                    .background(if (!enabled(i)) Color(0xFF2A2B33) else if (on) Color.White else Color(0xFF5A5B66)))
            }
            if (selected >= 0) Box(Modifier.align(Alignment.CenterStart).offset(x = xOf(selected) - 10.dp).size(20.dp)
                .clip(CircleShape).background(Color.White))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val span = maxWidth - pad * 2
            val w = 64.dp
            labels.forEachIndexed { i, label ->
                val x = pad + span * (i.toFloat() / (n - 1))
                val left = (x - w / 2).coerceIn(0.dp, maxWidth - w)
                Text(label, color = if (!enabled(i)) StudioMuted.copy(alpha = .35f) else if (i == selected) StudioText else StudioMuted,
                    fontSize = 11.sp, maxLines = 1, textAlign = when (i) { 0 -> TextAlign.Start; n - 1 -> TextAlign.End; else -> TextAlign.Center },
                    modifier = Modifier.width(w).offset(x = left))
            }
        }
    }
}
