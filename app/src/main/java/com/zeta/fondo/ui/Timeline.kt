package com.zeta.fondo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import android.graphics.Bitmap
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.ClipEdits
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Effects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * v41 · Línea de tiempo como la de un editor de vídeo:
 *  - el cabezal blanco está FIJO en el centro y lo que se mueve es la línea de tiempo;
 *  - arrastrar a los lados (en cualquier sitio libre) recorre el vídeo, con inercia;
 *  - pellizcar con dos dedos acerca o aleja la escala de tiempo;
 *  - pistas: la imagen (tócala para duración, formato, color…; su tirador derecho cambia la
 *    duración), la música (+ Agregar música) y una pista por capa de efectos;
 *  - un bloque se toca para seleccionarlo; seleccionado se arrastra para moverlo y sus
 *    tiradores recortan o alargan; sin seleccionar hay que mantenerlo pulsado (así nunca
 *    se mueve por accidente al recorrer la línea de tiempo).
 */

private fun secText(sec: Float): String = String.format(java.util.Locale.US, "%.1f", sec.coerceAtLeast(0f))

/** 00:03 (o 00:03.5 si [tenths]). */
internal fun clockText(sec: Float, tenths: Boolean = false): String {
    val s = sec.coerceAtLeast(0f)
    val m = (s / 60f).toInt()
    val r = s - m * 60
    return if (tenths) String.format(java.util.Locale.US, "%02d:%04.1f", m, r)
    else String.format(java.util.Locale.US, "%02d:%02d", m, r.toInt())
}

/** Nombres cortos: la timeline muestra el EFECTO, nunca su configuración. */
internal fun timelineTitle(id: String): String = if (com.zeta.fondo.model.SoundClips.isSound(id))
    "♪ " + com.zeta.fondo.model.SoundCatalog.title(com.zeta.fondo.model.SoundClips.soundOf(id)) else when (id) {
    "flow" -> "Animación"
    "drift" -> "Deriva"
    "gust" -> "Viento"
    "appear" -> "Personaje"
    "impact" -> "Impacto"
    "bolt" -> "Rayo"
    "parallax" -> "Profundidad"
    "natural" -> "Natural"
    "mesh" -> "Deformar"
    "cam" -> "Cámara"
    "shore" -> "Olas"
    "leaves" -> "Hojas"
    "clouds3" -> "Nubes"
    "blast" -> "Explosión"
    "slash" -> "Corte"
    "blood" -> "Tinta"
    "reveal" -> "Revelación"
    "sunpath" -> "Reflejo"
    "zonemotion" -> "Zona"
    "smoke" -> "Humo"
    "flame" -> "Fuego"
    else -> Effects.all.firstOrNull { it.id == id }?.title
        ?.replace(" (Zona 1)", "")
        ?.replace(" (Zona 2)", "")
        ?.replace(" 2.5D", "")
        ?.substringBefore(" (")
        ?.substringBefore(" /")
        ?: id
}

/** Color de cada familia en la línea de tiempo (identifica el bloque de un vistazo). */
/**
 * v42 · Color del bloque según la familia del efecto, para reconocerlo de un vistazo:
 * fuego rojo, agua azul, nieve/cielo celeste, humo gris, energía morado, luz dorado,
 * naturaleza verde, manga sepia y las animaciones de la imagen en rosa.
 */
internal fun clipFamily(id: String): Int = if (com.zeta.fondo.model.SoundClips.isSound(id)) 10 else when (id) {
    "flame", "blast", "embers", "heat", "impact" -> 0
    "water", "shore", "waterfall", "reflect", "rain", "bubbles", "sunpath", "ripple", "glint", "glassrain" -> 1
    "snow", "sky", "clouds3", "drift", "cloudshadow", "aurora" -> 2
    "smoke", "fog", "ash", "dust", "sand" -> 3
    "energy", "electric", "shock", "bolt", "slash", "blackaura", "runes", "pulse" -> 4
    "rays", "sparkle", "bokeh", "lightleak", "flash", "stars", "comet", "eyes", "elements", "lensflare", "citylights" -> 5
    "leaves", "sakura", "flies", "gust", "tornado", "birds" -> 6
    "tone", "grain", "ink", "hatch", "focus", "speed", "inkdrip", "dramatic", "blood", "dispersion", "reveal", "shards" -> 7
    "natural", "parallax", "mesh", "cam", "zonemotion", "flow", "sway", "cloth", "blink", "appear" -> 8
    else -> 9
}

private val familyColors = listOf(
    Color(0xFFD9442B), Color(0xFF2B79DB), Color(0xFF2F9CC0), Color(0xFF6B7280), Color(0xFF8452E6),
    Color(0xFFC08A12), Color(0xFF3C9550), Color(0xFF94673C), Color(0xFFD9437F), Color(0xFF6E58B8),
    Color(0xFF2B7D5C))   // v44: sonidos

internal fun clipColor(id: String): Color = familyColors[clipFamily(id)]

private val AudioGreen = Color(0xFF2B7D5C)

internal data class TimelineSnap(val startSec: Float, val guideSec: Float?)

/**
 * Magnetismo de edición: pega el inicio o final del bloque al inicio/final de otro clip
 * (o al cabezal) cuando están a pocos píxeles. Devuelve además la guía vertical a dibujar.
 */
internal fun snapTimelineStart(
    movingUid: Long, wantedStart: Float, duration: Float, total: Float,
    pxPerSec: Float, clips: List<EffectClip>, playheadSec: Float? = null, thresholdPx: Float = 10f
): TimelineSnap {
    val maxStart = (total - duration).coerceAtLeast(0f)
    val clamped = wantedStart.coerceIn(0f, maxStart)
    val thresholdSec = (thresholdPx / pxPerSec.coerceAtLeast(1f)).coerceIn(.015f, .6f)
    var bestStart = clamped
    var bestGuide: Float? = null
    var bestDelta = thresholdSec + .001f
    val movingEnd = clamped + duration
    val anchors = ArrayList<Float>()
    clips.filter { it.uid != movingUid }.forEach { anchors += it.startSec; anchors += it.startSec + it.durationSec }
    playheadSec?.let { anchors += it }
    anchors += 0f; anchors += total
    for (anchor in anchors) {
        val deltaStart = anchor - clamped
        if (abs(deltaStart) < bestDelta) {
            val candidate = (clamped + deltaStart).coerceIn(0f, maxStart)
            if (abs(candidate - clamped) <= thresholdSec + .001f) {
                bestDelta = abs(deltaStart); bestStart = candidate; bestGuide = anchor
            }
        }
        val deltaEnd = anchor - movingEnd
        if (abs(deltaEnd) < bestDelta) {
            val candidate = (clamped + deltaEnd).coerceIn(0f, maxStart)
            if (abs(candidate - clamped) <= thresholdSec + .001f) {
                bestDelta = abs(deltaEnd); bestStart = candidate; bestGuide = anchor
            }
        }
    }
    return TimelineSnap(bestStart, bestGuide)
}

/**
 * v43 · Imán de UN borde (recortar un bloque o recorrer con el cabezal): si [raw] queda a menos
 * de [thresholdSec] de un punto de enganche (cabezal, bordes de otros bloques, inicio o
 * final del vídeo) se pega a él. Devuelve la posición y el punto enganchado (o null).
 */
internal fun snapEdge(raw: Float, anchors: List<Float>, thresholdSec: Float): Pair<Float, Float?> {
    var best: Float? = null
    var bestD = thresholdSec
    for (a in anchors) {
        val d = abs(a - raw)
        if (d <= bestD) { bestD = d; best = a }
    }
    return (best ?: raw) to best
}

/** Puntos de enganche: bordes de los demás bloques, inicio y final del vídeo y el cabezal. */
internal fun snapAnchors(clips: List<EffectClip>, exceptUid: Long?, total: Float, playheadSec: Float?): List<Float> {
    val out = ArrayList<Float>(clips.size * 2 + 3)
    clips.forEach { if (it.uid != exceptUid) { out += it.startSec; out += it.startSec + it.durationSec } }
    out += 0f; out += total
    playheadSec?.let { out += it }
    return out
}

/** Asigna pistas solo cuando los bloques se solapan; clips consecutivos comparten la misma fila. */
internal fun timelineLaneLayout(clips: List<EffectClip>): Pair<Map<Long, Int>, Int> {
    if (clips.isEmpty()) return emptyMap<Long, Int>() to 1
    val result = LinkedHashMap<Long, Int>()
    val placed = mutableListOf<Triple<Int, Float, Float>>() // pista, inicio, fin
    fun overlaps(start: Float, end: Float, otherStart: Float, otherEnd: Float): Boolean =
        start < otherEnd - .015f && otherStart < end - .015f
    clips.sortedWith(compareBy<EffectClip> { it.startSec }.thenBy { it.uid }).forEach { clip ->
        val startSec = clip.startSec
        val endSec = clip.startSec + clip.durationSec
        fun free(track: Int): Boolean = placed.none { (t, s, e) ->
            t == track && overlaps(startSec, endSec, s, e)
        }
        var lane = clip.track
        if (lane < 0 || !free(lane)) {
            lane = 0
            while (!free(lane)) lane++
        }
        result[clip.uid] = lane
        placed += Triple(lane, startSec, endSec)
    }
    val count = (result.values.maxOrNull() ?: 0) + 1
    return result to count.coerceAtLeast(1)
}

/** Posición del cabezal (0..1): la del vídeo al reproducir, la elegida en pausa. */
@Composable
private fun rememberPlayhead(vm: AppViewModel): State<Float> {
    val position = remember { mutableFloatStateOf(vm.scrubT) }
    // v42: el cabezal se lee con snapshotFlow; la línea de tiempo ya no se recompone entera
    // en cada movimiento del dedo (solo se redibuja en la fase de dibujo).
    LaunchedEffect(Unit) {
        snapshotFlow { vm.playing }.collectLatest { playing ->
            if (!playing) snapshotFlow { vm.scrubT }.collect { position.floatValue = it }
            else while (true) { withFrameNanos { position.floatValue = vm.hub.currentT() } }
        }
    }
    return position
}

/** Límites de la escala (dp por segundo). */
private const val MIN_PPS = 14f
private const val MAX_PPS = 320f

// Alturas de las pistas (dp).
private const val RULER_H = 24
private const val IMAGE_Y = 6
private const val IMAGE_H = 52
private const val AUDIO_H = 34
private const val LANE_STEP = 40
private const val BLOCK_H = 34
private const val ADD_H = 32
private const val LAYER_H = 34
private const val LAYER_STEP = 40
internal val SnapYellow = Color(0xFFFFD54A)

@Composable
fun CapTimeline(vm: AppViewModel, modifier: Modifier) {
    val total = vm.exportSettings.loopSec.toFloat().coerceAtLeast(.5f)
    val clips = vm.effectClips.sortedWith(compareBy<EffectClip> { it.startSec }.thenBy { it.uid })
    val playhead = rememberPlayhead(vm)
    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val scrollY = rememberScrollState()
    var movingClip by remember { mutableStateOf(false) }
    var snapGuideSec by remember { mutableStateOf<Float?>(null) }
    var live by remember { mutableStateOf<String?>(null) }
    // v43: vibración corta (como en un editor de vídeo) cada vez que algo se pega a la
    // línea blanca, al borde de otro bloque o al inicio/final del vídeo.
    val view = LocalView.current
    // v44: la vibración se puede desactivar (Imán › mantener pulsado).
    fun tick() { if (vm.magnetHaptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    val setGuide: (Float?) -> Unit = { g ->
        if (g != null && (snapGuideSec == null || abs(g - snapGuideSec!!) > 1e-4f)) tick()
        snapGuideSec = g
    }
    var rawScrubSec by remember { mutableFloatStateOf(0f) }
    val scrubMagnet = remember { MagnetState() }
    var magnetDialog by remember { mutableStateOf(false) }
    val (laneByUid, usedLaneCount) = remember(clips) { timelineLaneLayout(clips) }
    // Una pista vacía al final hace que mover hacia abajo sea natural.
    val laneCount = if (clips.isEmpty()) 0 else usedLaneCount + 1
    // v44.23: las capas de imagen van ARRIBA de la imagen (la de más arriba = la de delante).
    val layerList = vm.layers.asReversed()
    val imageY = IMAGE_Y + layerList.size * LAYER_STEP
    val audioY = imageY + IMAGE_H + 8
    val lanesY = audioY + AUDIO_H + 8
    val addY = lanesY + LANE_STEP * laneCount + 4
    val contentH = addY + ADD_H + 16

    BoxWithConstraints(modifier.background(Night)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val center = widthPx / 2f
        val autoPps = (maxWidth.value * .72f / total).coerceIn(MIN_PPS, MAX_PPS)
        val ppsDp = if (vm.timelinePps > 0f) vm.timelinePps else autoPps
        val pps = with(density) { ppsDp.dp.toPx() }
        val ppsState = rememberUpdatedState(pps)
        val ppsDpState = rememberUpdatedState(ppsDp)
        // v44: tolerancia del imán (≈ 8 dp, ajustable), igual a cualquier zoom.
        val snapPx = with(density) { vm.magnetDp.dp.toPx() }
        val clipsState = rememberUpdatedState(clips)
        /** Segundo bajo el cabezal (se lee en la fase de dibujo: sin recomponer). */
        fun cur(): Float = playhead.value * total

        Box(Modifier.fillMaxSize().pointerInput(total, widthPx) {
            timelineGestures(
                ppsNow = { ppsState.value },
                onTouch = { fling?.cancel(); fling = null },
                onStartScrub = {
                    if (vm.playing) vm.togglePlay()
                    rawScrubSec = vm.scrubT * total
                    scrubMagnet.reset()
                },
                onScrub = { dx ->
                    // v43: el cabezal se pega a los bordes de los bloques (y vibra), como en CapCut.
                    rawScrubSec = (rawScrubSec - dx / ppsState.value).coerceIn(0f, total * .999f)
                    val enter = snapPx / ppsState.value
                    val r = scrubMagnet.apply(rawScrubSec,
                        MagnetState.edgeCandidates(snapAnchors(clipsState.value, null, total * .999f, null) + vm.beatMarkers),
                        enter, enter * MagnetState.EXIT_FACTOR, vm.magnetOn)
                    if (r.newSnap) tick()
                    vm.scrubTo((r.value / total).coerceIn(0f, .999f))
                },
                onFling = { velSec ->
                    if (abs(velSec) > .05f) fling = scope.launch {
                        animateDecay(vm.scrubT * total, velSec, FloatExponentialDecaySpec(1.4f)) { value, _ ->
                            vm.scrubTo((value / total).coerceIn(0f, .999f))
                        }
                    }
                },
                onZoom = { z -> vm.timelinePps = (ppsDpState.value * z).coerceIn(MIN_PPS, MAX_PPS) },
                onTap = {
                    // Tocar un hueco quita la selección (como en un editor de vídeo).
                    if (vm.selectedTimelineClip != null) vm.selectTimelineClip(null)
                    if (vm.imageSelected) vm.selectImageTrack(false)
                    if (vm.selectedLayer != null) vm.selectLayer(null)
                }
            )
        }) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(RULER_H.dp)) {
                    Ruler(Modifier.fillMaxSize(), { cur() }, total, pps, center)
                    // v44 · golpes de la música (Beat Sync)
                    val beats = vm.beatMarkers
                    if (beats.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
                        for (b in beats) {
                            val x = center + (b - cur()) * pps
                            if (x < 0f || x > size.width) continue
                            drawLine(SnapYellow.copy(alpha = .85f), Offset(x, size.height * .45f), Offset(x, size.height), 2.dp.toPx())
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollY, enabled = !movingClip)) {
                    Box(Modifier.fillMaxWidth().height(contentH.dp)) {
                        layerList.forEachIndexed { i, layer ->
                            key("layer_${layer.id}") {
                                LayerTrack(vm, layer, Modifier.offset(y = (IMAGE_Y + i * LAYER_STEP).dp).fillMaxWidth().height(LAYER_H.dp),
                                    total, pps, center) { cur() }
                            }
                        }
                        ImageTrack(vm, Modifier.offset(y = imageY.dp).fillMaxWidth().height(IMAGE_H.dp),
                            total, pps, center, { cur() }, onLive = { live = it })
                        AudioTrack(vm, Modifier.offset(y = audioY.dp).fillMaxWidth().height(AUDIO_H.dp),
                            total, pps, center, { cur() })
                        // Guías de las pistas de efectos.
                        if (laneCount > 0) Canvas(Modifier.offset(y = lanesY.dp).fillMaxWidth().height((LANE_STEP * laneCount).dp)) {
                            val x0 = center - cur() * pps
                            val x1 = x0 + total * pps
                            for (lane in 0 until laneCount) {
                                val y = lane * LANE_STEP.dp.toPx() + BLOCK_H.dp.toPx() / 2f
                                drawLine(StudioTile, Offset(x0.coerceAtLeast(0f), y), Offset(x1.coerceAtMost(size.width), y), 1.dp.toPx())
                            }
                            snapGuideSec?.let { g ->
                                val gx = center + (g - cur()) * pps
                                drawLine(SnapYellow, Offset(gx, 0f), Offset(gx, size.height), 1.5.dp.toPx())
                            }
                        }
                        clips.forEach { clip ->
                            key(clip.uid) {
                                val lane = laneByUid[clip.uid] ?: vm.timelineTrackOf(clip.uid)
                                TimelineBlock(vm, clip, lane, clips, total, pps, center, widthPx, { cur() }, laneCount,
                                    top = lanesY, snapPx = snapPx, onMoving = { movingClip = it }, onGuide = setGuide,
                                    onLive = { live = it })
                            }
                        }
                        AddPill("+ Agregar efecto", Modifier.offset(y = addY.dp).height(ADD_H.dp), center, pps, { cur() }) {
                            vm.openCategory(com.zeta.fondo.model.EditorTab.EFFECTS)
                        }
                    }
                }
            }
            // Cabezal fijo en el centro (no capta toques).
            // v43: enganchado a la línea blanca → la línea se vuelve amarilla y más gruesa.
            val onPlayhead = snapGuideSec?.let { abs(it - cur()) < 1e-3f } == true
            Canvas(Modifier.fillMaxSize()) {
                val c = if (onPlayhead) SnapYellow else Color.White
                drawLine(c, Offset(center, 6.dp.toPx()), Offset(center, size.height),
                    strokeWidth = (if (onPlayhead) 3.dp else 2.dp).toPx())
                drawCircle(c, 4.5.dp.toPx(), Offset(center, 6.dp.toPx()))
            }
            // v44: interruptor del imán (tocar = ON/OFF · mantener = tolerancia y vibración).
            MagnetPill(vm, Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp)) { magnetDialog = true }
            if (vm.soloActive || vm.soundSoloActive) SoloPill(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 2.dp)) { vm.clearSolo() }
            if (magnetDialog) MagnetDialog(vm) { magnetDialog = false }
            live?.let { text ->
                Text(text, color = Color.White, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp)).background(StudioPink.copy(alpha = .95f))
                        .padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/**
 * Gestos del fondo de la línea de tiempo: arrastre lateral = recorrer (con inercia al soltar),
 * dos dedos = escala, toque = quitar selección. Si un bloque toma el gesto, se ignora; el
 * desplazamiento vertical lo hace la lista de pistas.
 */
private suspend fun PointerInputScope.timelineGestures(
    ppsNow: () -> Float, onTouch: () -> Unit, onStartScrub: () -> Unit, onScrub: (Float) -> Unit,
    onFling: (Float) -> Unit, onZoom: (Float) -> Unit, onTap: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onTouch()
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var mode = 0            // 0 esperando · 1 recorrer · 2 escala · -1 lo hace otro
        var accX = 0f
        var accY = 0f
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                val up = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                if (mode == 1) {
                    tracker.addPosition(up.uptimeMillis, up.position)
                    val vx = tracker.calculateVelocity().x
                    onFling(-vx / ppsNow().coerceAtLeast(1f))
                } else if (mode == 0 && !up.isConsumed) onTap()
                break
            }
            if (pressed >= 2) {
                if (mode != 2) { mode = 2; onStartScrub() }
                val z = event.calculateZoom()
                if (z != 1f) onZoom(z)
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == 2 || mode == -1) continue
            val ch = event.changes.firstOrNull { it.id == down.id } ?: continue
            if (mode == 0) {
                if (ch.isConsumed) { mode = -1; continue }
                val d = ch.positionChange()
                accX += d.x; accY += d.y
                if (abs(accX) > slop && abs(accX) > abs(accY)) {
                    mode = 1
                    onStartScrub()
                    onScrub(accX)
                    ch.consume()
                } else if (abs(accY) > slop) mode = -1
                continue
            }
            // Recorriendo.
            onScrub(ch.positionChange().x)
            tracker.addPosition(ch.uptimeMillis, ch.position)
            ch.consume()
        }
    }
}

/** Regla: 00:00 · 00:02 · … que se desplaza con la línea de tiempo. */
@Composable
private fun Ruler(modifier: Modifier, cur: () -> Float, total: Float, pps: Float, center: Float) {
    val density = LocalDensity.current
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(150, 255, 255, 255)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    paint.textSize = with(density) { 10.sp.toPx() }
    Canvas(modifier) {
        val minGap = 64.dp.toPx()
        val step = listOf(.5f, 1f, 2f, 5f, 10f, 15f, 30f).firstOrNull { it * pps >= minGap } ?: 30f
        val c = cur()
        var s = 0f
        while (s <= total + .001f) {
            val x = center + (s - c) * pps
            if (x > -40f && x < size.width + 40f) {
                drawIntoCanvas { it.nativeCanvas.drawText(clockText(s, tenths = step < 1f), x, size.height - 8.dp.toPx(), paint) }
            }
            val half = center + (s + step / 2f - c) * pps
            if (s + step / 2f < total && half > 0f && half < size.width)
                drawCircle(Color.White.copy(alpha = .35f), 1.5.dp.toPx(), Offset(half, size.height - 12.dp.toPx()))
            s += step
        }
    }
}

/** Botón «+ Agregar…» que viaja con el inicio del vídeo. */
@Composable
private fun AddPill(text: String, modifier: Modifier, center: Float, pps: Float, cur: () -> Float,
                    width: Dp? = null, onClick: () -> Unit) {
    Box(modifier
        .graphicsLayer { translationX = center - cur() * pps }
        .then(if (width != null) Modifier.width(width) else Modifier)
        .clip(RoundedCornerShape(8.dp)).background(StudioTile).clickable(onClick = onClick)
        .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = StudioText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pista de la imagen con miniaturas; seleccionada, su tirador derecho cambia la duración. */
@Composable
private fun ImageTrack(vm: AppViewModel, modifier: Modifier, total: Float, pps: Float, center: Float,
                       cur: () -> Float, onLive: (String?) -> Unit) {
    val source = vm.previewFallback
    val thumb by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.Default) {
            try {
                source?.takeIf { !it.isRecycled }?.let { b ->
                    val h = 96
                    val w = (h * b.width / b.height.coerceAtLeast(1)).coerceIn(16, 400)
                    Bitmap.createScaledBitmap(b, w, h, true).asImageBitmap()
                }
            } catch (_: Throwable) { null }
        }
    }
    val selected = vm.imageSelected
    var dragDur by remember { mutableStateOf<Float?>(null) }
    val totalState = rememberUpdatedState(total)
    val ppsState = rememberUpdatedState(pps)
    Canvas(modifier.pointerInput(selected, total, center) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val x0 = center - cur() * ppsState.value
            val x1 = x0 + totalState.value * ppsState.value
            val handle = 22.dp.toPx()
            if (selected && abs(down.position.x - x1) < handle) {
                // Tirador derecho: duración del vídeo en segundos enteros.
                down.consume()
                var dur = totalState.value
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    dur = ((ch.position.x - x0) / ppsState.value).coerceIn(2f, 60f)
                    dragDur = dur
                    onLive("Duración ${dur.roundToInt()} s")
                    ch.consume()
                }
                dragDur = null
                onLive(null)
                vm.setVideoDuration(dur.roundToInt())
                return@awaitEachGesture
            }
            // Toque sin mover = seleccionar; si se arrastra, lo recorre el fondo.
            val inside = down.position.x in x0..x1
            var moved = false
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!ch.pressed) {
                    if (!moved && inside && !ch.isConsumed) { ch.consume(); vm.selectImageTrack(!selected) }
                    break
                }
            }
        }
    }) {
        val x0 = center - cur() * pps
        val w = (dragDur ?: total) * pps
        val h = size.height
        val r = 6.dp.toPx()
        val rect = RoundRect(x0, 0f, x0 + w, h, CornerRadius(r, r))
        val path = Path().apply { addRoundRect(rect) }
        drawRoundRect(StudioTile, Offset(x0, 0f), Size(w, h), CornerRadius(r, r))
        val img = thumb
        if (img != null) clipPath(path) {
            val tileW = (h * img.width / img.height.coerceAtLeast(1)).coerceAtLeast(8f)
            var x = x0
            val maxX = (x0 + w).coerceAtMost(size.width + tileW)
            if (x < -tileW) x += ((-tileW - x) / tileW).toInt() * tileW
            while (x < maxX) {
                drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(x.roundToInt(), 0), dstSize = IntSize(tileW.roundToInt() + 1, h.roundToInt()))
                x += tileW
            }
        }
        if (selected) {
            drawRoundRect(Color.White, Offset(x0, 0f), Size(w, h), CornerRadius(r, r), style = Stroke(2.dp.toPx()))
            val hw = 12.dp.toPx()
            for (hx in listOf(x0, x0 + w - hw)) {
                drawRoundRect(Color.White, Offset(hx, 0f), Size(hw, h), CornerRadius(r, r))
                drawLine(Night, Offset(hx + hw / 2f, h * .32f), Offset(hx + hw / 2f, h * .68f), 2.dp.toPx())
            }
        }
    }
}

/**
 * v44.23 · Pista de una capa de imagen: miniaturas, nombre y modo de mezcla. Tocar = seleccionar
 * (aparecen sus acciones abajo y la caja con tiradores en el lienzo).
 */
@Composable
private fun LayerTrack(vm: AppViewModel, layer: com.zeta.fondo.model.ImageLayer, modifier: Modifier,
                       total: Float, pps: Float, center: Float, cur: () -> Float) {
    val selected = vm.selectedLayer == layer.id
    val thumb = vm.layerThumbs[layer.file]
    val img = remember(thumb) { thumb?.takeIf { !it.isRecycled }?.asImageBitmap() }
    val start = if (layer.durSec > 0f) layer.startSec.coerceIn(0f, total) else 0f
    val end = if (layer.durSec > 0f) (start + layer.durSec).coerceAtMost(total) else total
    val x0 = { center + (start - cur()) * pps }
    val x1 = { center + (end - cur()) * pps }
    Box(modifier) {
        Box(Modifier.spanX(x0, x1).fillMaxHeight().clip(RoundedCornerShape(6.dp))
            .background(layerColor(layer).copy(alpha = if (layer.hidden) .35f else 1f))
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
            .clickable { vm.selectLayer(if (selected) null else layer.id) }) {
            if (img != null) Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val tileW = (h * img.width / img.height.coerceAtLeast(1)).coerceAtLeast(8f)
                var x = 0f
                var n = 0
                while (x < size.width && n < 60) {
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(x.roundToInt(), 0), dstSize = IntSize(tileW.roundToInt() + 1, h.roundToInt()),
                        alpha = if (layer.hidden) .3f else .55f)
                    x += tileW; n++
                }
            }
            Row(Modifier.followStart(x0, x1).fillMaxHeight().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (layer.hidden) Text("⊘ ", color = Color.White, fontSize = 11.sp)
                Text(layer.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                val mode = com.zeta.fondo.model.BlendModes.byId(layer.blend)
                if (mode.id != 0) Text("  · ${mode.name}", color = Color.White.copy(alpha = .85f), fontSize = 10.sp, maxLines = 1)
                if (layer.keys.isNotEmpty()) Text("  ◆", color = KeyOn, fontSize = 10.sp)
            }
        }
    }
}

/** Pista de audio: la música elegida o «+ Agregar música». */
@Composable
private fun AudioTrack(vm: AppViewModel, modifier: Modifier, total: Float, pps: Float, center: Float, cur: () -> Float) {
    val e = vm.exportSettings
    val ambient = e.ambient.count { it.value > 0.01f }
    // v43: la pista de música mide EXACTAMENTE lo mismo que el vídeo, a cualquier zoom.
    // v44.16: con o sin música, la pista va JUSTO debajo del vídeo (mismo inicio y final) y se
    // desliza con él; el texto va pegado a su inicio, como en CapCut (no se queda flotando).
    val empty = e.audioName.isBlank()
    val x0 = { center - cur() * pps }
    // v44.17: «+ Agregar música» es un botón como «+ Agregar efecto»: abre directo tus archivos y
    // la canción entra como un BLOQUE (desde 0 s, con su duración real), no como pista de todo el vídeo.
    if (empty) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importUserSound(it, asMusic = true) }
        }
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            AddPill(if (ambient > 0) "♪  + Agregar música · $ambient de ambiente" else "♪  + Agregar música",
                Modifier.fillMaxHeight(), center, pps, cur) {
                try { picker.launch(arrayOf("audio/*")) } catch (_: Throwable) { vm.openAudio() }
            }
        }
        return
    }
    Box(modifier) {
        Box(Modifier.spanX(x0, { center + (total - cur()) * pps }).fillMaxHeight()
            .clip(RoundedCornerShape(6.dp)).background(if (empty) StudioTile else AudioGreen).clickable { vm.openAudio() },
            contentAlignment = Alignment.CenterStart) {
            Text(if (empty) (if (ambient > 0) "♪  + Agregar música · $ambient de ambiente" else "♪  + Agregar música")
                else "♪ " + e.audioName + (if (ambient > 0) "  · $ambient de ambiente" else ""),
                color = if (empty) StudioText else Color.White, fontSize = if (empty) 12.sp else 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.followStart(x0, { center + (total - cur()) * pps }).padding(horizontal = 10.dp))
        }
    }
}

/** Un bloque de efecto: seleccionar, mover, recortar desde los dos extremos. */
@Composable
private fun TimelineBlock(
    vm: AppViewModel, clip: EffectClip, lane: Int, clips: List<EffectClip>, total: Float, pps: Float,
    center: Float, widthPx: Float, cur: () -> Float, laneCount: Int, top: Int, snapPx: Float,
    onMoving: (Boolean) -> Unit, onGuide: (Float?) -> Unit, onLive: (String?) -> Unit
) {
    val density = LocalDensity.current
    var editing by remember(clip.uid) { mutableStateOf(false) }
    var visualStart by remember(clip.uid) { mutableFloatStateOf(clip.startSec) }
    var visualDuration by remember(clip.uid) { mutableFloatStateOf(clip.durationSec) }
    var visualTrack by remember(clip.uid) { mutableIntStateOf(lane) }
    LaunchedEffect(clip.startSec, clip.durationSec, clip.track, lane, editing) {
        if (!editing) {
            visualStart = clip.startSec
            visualDuration = clip.durationSec
            visualTrack = lane
        }
    }
    val selected = vm.selectedTimelineClip == clip.uid
    val clipsNow by rememberUpdatedState(clips)
    val ppsState = rememberUpdatedState(pps)
    // Posición "cruda" del dedo: el imán puede pegar el bloque, pero el dedo siempre
    // puede volver a despegarlo.
    var rawStart by remember(clip.uid) { mutableFloatStateOf(clip.startSec) }
    var rawEdge by remember(clip.uid) { mutableFloatStateOf(clip.startSec) }
    val magnet = remember(clip.uid) { MagnetState() }
    var snapDelta by remember(clip.uid) { mutableFloatStateOf(0f) }
    val laneStepPx = with(density) { LANE_STEP.dp.toPx() }
    fun liveText(): String = "${secText(visualStart)} s → ${secText(visualStart + visualDuration)} s · ${secText(visualDuration)} s" +
        (if (magnet.lockedValue != null && abs(snapDelta) >= .005f) "  · Imán ${MagnetState.deltaText(snapDelta)}" else "")
    var lastPreviewNs = 0L
    fun preview() {
        val now = System.nanoTime()
        if (lastPreviewNs == 0L || now - lastPreviewNs >= 16_000_000L) {
            lastPreviewNs = now
            vm.previewEffectClipTiming(clip.uid, visualStart, visualDuration)
        }
    }
    fun begin() {
        if (vm.playing) vm.togglePlay()
        val current = vm.effectClips.firstOrNull { it.uid == clip.uid } ?: clip
        visualStart = current.startSec
        visualDuration = current.durationSec
        visualTrack = vm.timelineTrackOf(clip.uid)
        editing = true
        lastPreviewNs = 0L
        magnet.reset(); snapDelta = 0f
        onGuide(null)
        vm.selectTimelineClip(clip.uid)
        onLive(liveText())
    }
    fun finish() {
        vm.setEffectClipPlacement(clip.uid, visualStart, visualDuration, visualTrack)
        editing = false
        onMoving(false)
        onGuide(null)
        onLive(null)
        vm.saveProject()
    }
    /** Si el dedo llega a un borde, la línea de tiempo avanza sola (para llevar el bloque lejos). */
    fun edgeScroll(fingerX: Float): Float {
        val edge = with(density) { 48.dp.toPx() }
        val k = when {
            fingerX < edge -> -(edge - fingerX) / edge
            fingerX > widthPx - edge -> (fingerX - (widthPx - edge)) / edge
            else -> 0f
        }
        if (k == 0f) return 0f
        val ds = k * 0.06f
        val now = vm.scrubT * total
        val next = (now + ds).coerceIn(0f, total * .999f)
        vm.scrubTo(next / total)
        return next - now
    }
    val color = clipColor(clip.effectId)
    val minWidthPx = with(density) { (if (selected) 40.dp else 24.dp).toPx() }
    val handlePx = with(density) { 16.dp.toPx() }
    // v43: bordes REALES del bloque en píxeles (se leen al colocar, sin recomponer). Antes el
    // ancho se recortaba al de la pantalla y, con zoom, el final se dibujaba antes de tiempo.
    fun x0(): Float = center + (visualStart - cur()) * ppsState.value
    fun x1(): Float = x0() + (visualDuration.coerceIn(ClipEdits.MIN_DURATION, total) * ppsState.value).coerceAtLeast(minWidthPx)
    Box(Modifier.offset(y = (top + LANE_STEP * visualTrack).dp).fillMaxWidth().height(BLOCK_H.dp)) {
        // Centro: tocar selecciona; mover = arrastrar (seleccionado) o mantener pulsado (sin seleccionar).
        var baseTrack = 0
        var dragY = 0f
        // v44.12: mover SIEMPRE tras mantener pulsado (también si ya está seleccionado): un toque
        // solo selecciona y no lo arrastra sin querer. Quitar = botón ← al inicio del bloque.
        val view = LocalView.current
        val moveGesture: suspend PointerInputScope.() -> Unit = {
            val onStart: (Offset) -> Unit = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                begin(); baseTrack = visualTrack; dragY = 0f; rawStart = visualStart; onMoving(true)
            }
            val onEnd: () -> Unit = { finish() }
            val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, dragAmount ->
                change.consume()
                val p = ppsState.value
                // Lo visible del bloque empieza en el borde de la pantalla si su inicio está fuera.
                val bodyLeft = x0().coerceAtLeast(0f)
                val auto = edgeScroll(bodyLeft + change.position.x)
                rawStart = ClipEdits.move(rawStart, visualDuration, dragAmount.x / p + auto, total)
                // v44: imán con histéresis (entra a ~8 dp, suelta a 1,5×) y se puede apagar.
                val enter = snapPx / p
                val r = magnet.apply(rawStart,
                    MagnetState.moveCandidates(snapAnchors(clipsNow, clip.uid, total, vm.scrubT * total) + vm.beatMarkers, visualDuration, total),
                    enter, enter * MagnetState.EXIT_FACTOR, vm.magnetOn)
                visualStart = r.value.coerceIn(0f, (total - visualDuration).coerceAtLeast(0f))
                snapDelta = r.delta
                onGuide(r.guide)
                dragY += dragAmount.y
                val requested = (baseTrack + (dragY / laneStepPx).roundToInt()).coerceIn(0, (laneCount - 1).coerceAtLeast(0))
                // Solo cambia de pista cuando el hueco está realmente libre en ese instante.
                if (vm.isTimelineTrackFree(clip.uid, visualStart, visualDuration, requested)) visualTrack = requested
                else if (!vm.isTimelineTrackFree(clip.uid, visualStart, visualDuration, visualTrack))
                    visualTrack = vm.suggestTimelineTrack(clip.uid, visualStart, visualDuration, requested)
                onLive(liveText())
                preview()
            }
            detectDragGesturesAfterLongPress(onDragStart = onStart, onDragEnd = onEnd,
                onDragCancel = { finish() }, onDrag = onDrag)
        }
        Box(
            Modifier
                .spanX({ x0() }, { x1() })
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                // v44: un bloque oculto se ve apagado y rayado; sigue ahí con todos sus ajustes.
                .background(when {
                    clip.hidden -> color.copy(alpha = .28f)
                    vm.soloActive && vm.soloClip != clip.uid -> color.copy(alpha = .40f)
                    selected -> color
                    else -> color.copy(alpha = .85f)
                })
                .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                // v44.10: los tiradores van FUERA del bloque (como CapCut): el color llega
                // exacto hasta el inicio y el final reales, y el imán se ve pegado a la línea blanca.
                .clickable { vm.selectTimelineClip(clip.uid) }
                .pointerInput(clip.uid, selected, total, laneCount) { moveGesture() },
            contentAlignment = Alignment.CenterStart
        ) {
            // v44.16: flecha y nombre van PEGADOS al inicio real del bloque y se desplazan con él
            // (antes se quedaban en el borde de la pantalla y parecían moverse solos).
            Row(Modifier.followStart({ x0() }, { x1() }), verticalAlignment = Alignment.CenterVertically) {
            // v44.12: botón pequeño ← para QUITAR el efecto (con Deshacer en el aviso).
            // v44.15: flecha DIBUJADA y centrada (el carácter «←» quedaba bajo por la línea base).
            Box(Modifier.padding(start = 3.dp).size(22.dp).clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = .28f))
                .clickable { vm.removeEffectClip(clip.uid) },
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(12.dp)) {
                    val w = size.width; val h = size.height; val sw = 1.8.dp.toPx()
                    drawLine(Color.White, Offset(w * .9f, h / 2f), Offset(w * .12f, h / 2f), sw, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * .12f, h / 2f), Offset(w * .48f, h * .14f), sw, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(w * .12f, h / 2f), Offset(w * .48f, h * .86f), sw, cap = StrokeCap.Round)
                }
            }
            // Seleccionado: también su duración (como en un editor de vídeo).
            Text((if (clip.hidden) "⊘ " else if (vm.soloClip == clip.uid) "◎ " else "") +
                timelineTitle(clip.effectId) + (if (clip.overrides.isNotEmpty()) " •" else "") +
                (if (selected && !editing) " · ${secText(clip.durationSec)} s" else ""),
                color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        // v44: rombos de los fotogramas clave del bloque (no captan toques).
        // v44.12: más grandes y centrados, con borde oscuro para verse sobre cualquier color.
        val keyTimes = remember(clip.keys) { clip.keys.values.flatten().map { it.t }.distinct() }
        if (keyTimes.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            val r = 7.dp.toPx()
            val y = size.height / 2f
            val now = cur() - visualStart
            for (t in keyTimes) {
                if (t > visualDuration + .01f) continue
                val x = x0() + t * ppsState.value
                if (x < -r || x > size.width + r) continue
                val onKey = abs(t - now) < .05f
                val rr = if (onKey) r * 1.15f else r
                val path = Path().apply { moveTo(x, y - rr); lineTo(x + rr, y); lineTo(x, y + rr); lineTo(x - rr, y); close() }
                drawPath(path, if (onKey) Color(0xFFFFD54F) else Color.White)
                drawPath(path, Color(0xFF1A1A22), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        if (selected) {
            // Tirador izquierdo: el final queda fijo y cambia el inicio (y la duración).
            TrimHandle(Modifier.atX { x0() - handlePx }, clip.uid, "left") { phase, dx ->
                when (phase) {
                    0 -> { begin(); rawEdge = visualStart; magnet.reset() }
                    1 -> {
                        // v43: el borde se pega a la línea blanca y a los bordes de otros bloques.
                        val anchorEnd = (vm.effectClips.firstOrNull { it.uid == clip.uid } ?: clip).endSec
                        rawEdge += dx / ppsState.value
                        val enter = snapPx / ppsState.value
                        val r = magnet.apply(rawEdge, MagnetState.edgeCandidates(snapAnchors(clipsNow, clip.uid, total, vm.scrubT * total)),
                            enter, enter * MagnetState.EXIT_FACTOR, vm.magnetOn)
                        val (s, d) = ClipEdits.trimStart(r.value, anchorEnd, 0f)
                        visualStart = s; visualDuration = d
                        snapDelta = r.delta
                        onGuide(r.guide)
                        onLive(liveText()); preview()
                    }
                    else -> finish()
                }
            }
            // Tirador derecho: el inicio queda fijo y cambia la duración.
            TrimHandle(Modifier.atX { x1() }, clip.uid, "right") { phase, dx ->
                when (phase) {
                    0 -> { begin(); rawEdge = visualStart + visualDuration; magnet.reset() }
                    1 -> {
                        rawEdge += dx / ppsState.value
                        val enter = snapPx / ppsState.value
                        val r = magnet.apply(rawEdge, MagnetState.edgeCandidates(snapAnchors(clipsNow, clip.uid, total, vm.scrubT * total)),
                            enter, enter * MagnetState.EXIT_FACTOR, vm.magnetOn)
                        val (s, d) = ClipEdits.trimEnd(visualStart, r.value, 0f, total)
                        visualStart = s; visualDuration = d
                        snapDelta = r.delta
                        onGuide(r.guide)
                        onLive(liveText()); preview()
                    }
                    else -> finish()
                }
            }
        }
    }
}

/**
 * v43 · Coloca el contenido entre los píxeles [x0] y [x1] de su fila, recortado a la parte
 * visible. Así un bloque más largo que la pantalla (con zoom) conserva su final real.
 * Se lee en la fase de colocación: recorrer la línea de tiempo no recompone nada.
 */
internal fun Modifier.spanX(x0: () -> Float, x1: () -> Float): Modifier = layout { measurable, constraints ->
    val view = constraints.maxWidth
    val (left, w) = visibleSpan(x0(), x1(), view)
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(view, placeable.height) { placeable.place(left, 0) }
}

/**
 * v44.16 · Dentro de un bloque recortado por [spanX]: coloca el contenido en el INICIO REAL del
 * bloque (x0 puede estar fuera de la pantalla, a la izquierda). Así el texto se mueve con el
 * bloque y no se queda pegado al borde. Se lee en la fase de colocación: no recompone.
 */
internal fun Modifier.followStart(x0: () -> Float, x1: () -> Float): Modifier = layout { measurable, constraints ->
    val full = (x1() - x0()).roundToInt().coerceIn(0, 100_000)
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = full))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(x0().coerceAtMost(0f).roundToInt(), 0)
    }
}

/** Parte visible [izquierda, ancho] de un tramo x0..x1 en una fila de [view] píxeles. */
internal fun visibleSpan(x0: Float, x1: Float, view: Int): Pair<Int, Int> {
    val a = x0.coerceAtLeast(0f)
    val b = x1.coerceAtMost(view.toFloat())
    val w = (b - a).roundToInt().coerceIn(0, view)
    return (if (w == 0) -10_000 else a.roundToInt()) to w
}

/** v43 · Coloca un elemento pequeño (tirador) en el píxel [x] de su fila. */
internal fun Modifier.atX(x: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    layout(placeable.width, placeable.height) { placeable.place(x().roundToInt(), 0) }
}

/** Tirador de recorte (16 dp de zona táctil, barra blanca). phase: 0 inicio, 1 arrastre, 2 fin. */
@Composable
private fun TrimHandle(modifier: Modifier, uid: Long, side: String, onEvent: (phase: Int, dx: Float) -> Unit) {
    val event by rememberUpdatedState(onEvent)
    Box(
        modifier.fillMaxHeight().width(16.dp)
            .background(Color.White)
            .pointerInput(uid, side) {
                detectDragGestures(
                    onDragStart = { event(0, 0f) },
                    onDragEnd = { event(2, 0f) },
                    onDragCancel = { event(2, 0f) }
                ) { change, dragAmount ->
                    change.consume()
                    event(1, dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center
    ) { Box(Modifier.width(2.dp).height(14.dp).background(Night)) }
}
