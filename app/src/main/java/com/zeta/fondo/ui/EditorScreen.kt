package com.zeta.fondo.ui

import android.app.Activity
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.ExportState
import com.zeta.fondo.engine.PreviewRenderer
import com.zeta.fondo.model.Arrow
import com.zeta.fondo.model.EditorLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import com.zeta.fondo.model.EditorTab
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.GestureRules
import com.zeta.fondo.model.ImageRect
import com.zeta.fondo.model.PointN
import com.zeta.fondo.model.Tool
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * v40 · Editor con la estructura de un editor de vídeo (referencia: capturas de CapCut):
 *  - barra superior mínima: volver · proyecto (carpeta) · Exportar;
 *  - lienzo FIJO arriba: mismo tamaño y posición con cualquier categoría, herramienta,
 *    hoja de opciones o efecto seleccionado (solo cambia con Ajustes › Vista o pantalla completa);
 *  - controles del lienzo justo debajo: tiempo, reproducir, deshacer y rehacer;
 *  - zona de trabajo: línea de tiempo con pistas, u opciones de la herramienta con su propio scroll;
 *  - herramientas con contexto (al seleccionar un bloque: Ajustar, Posición, Dividir, Duplicar,
 *    Sustituir, Capa, Eliminar) y las 4 categorías.
 */
@Composable
fun EditorScreen(vm: AppViewModel) {
    // v44: tiempo exacto de un bloque (0,01 s).
    vm.preciseTimeClip?.let { uid -> PreciseTimeDialog(vm, uid) { vm.preciseTimeClip = null } }
    // v44.23: tiempo de una capa de imagen.
    vm.layerTimeOpen?.let { id -> LayerTimeDialog(vm, id) { vm.layerTimeOpen = null } }
    // v44.25: «Detrás» sin personaje pintado → pregunta (nunca una máscara a escondidas).
    if (vm.behindAsk != null) BehindAskDialog(vm)
    val meta = vm.meta ?: return
    var exportOpen by remember(meta.id) { mutableStateOf(false) }
    var durationOpen by remember(meta.id) { mutableStateOf(false) }
    BackHandler(!exportOpen) {
        when {
            vm.textEditor != null -> vm.closeTextEditor()
            vm.layerCrop != null -> vm.cancelLayerCrop()
            vm.fullscreen -> vm.fullscreen = false
            vm.blendPanelOpen -> vm.blendPanelOpen = false
            vm.layerMotionOpen -> vm.layerMotionOpen = false
            vm.selectedLayer != null -> vm.selectLayer(null)
            vm.paintingZone && vm.editingEffect != null -> vm.finishPainting()
            vm.editingEffect != null && vm.editFromCatalog && vm.adjustOpen -> vm.adjustOpen = false
            vm.editingEffect != null || vm.paintingZone -> vm.finishEdit(false)
            vm.placingClip -> vm.togglePlacing()
            vm.replacingClip != null -> vm.closeOptions()
            vm.optionsOpen -> vm.applyAndClose()
            vm.selectedTimelineClip != null -> vm.selectTimelineClip(null)
            vm.imageSelected -> vm.selectImageTrack(false)
            vm.categoryOpen -> vm.backToMain()
            else -> vm.closeEditor()
        }
    }

    val context = LocalContext.current
    val rootView = LocalView.current
    val owner = LocalLifecycleOwner.current
    val audio = remember(meta.id) { com.zeta.fondo.engine.ImportedAudioPreview(context.applicationContext, vm.hub) { message ->
        android.os.Handler(android.os.Looper.getMainLooper()).post { vm.message = message }
    } }
    // v44: con los bloques de sonido de la línea de tiempo (y «Solo» si está en un sonido).
    val audioSettings = vm.audioSettings()
    val audioPlaying = vm.playing && vm.exportState !is ExportState.Running &&
        vm.exportState !is ExportState.Done && vm.busy == null
    val bolt = vm.boltCount
    SideEffect {
        audio.update(java.io.File(meta.dir, com.zeta.fondo.engine.ImportedAudio.FILE_NAME),
            audioSettings, audioPlaying, bolt)
    }
    DisposableEffect(audio, owner) {
        audio.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) audio.setForeground(false)
            if (event == Lifecycle.Event.ON_RESUME) audio.setForeground(true)
        }
        owner.lifecycle.addObserver(observer)
        audio.start()
        onDispose { owner.lifecycle.removeObserver(observer); audio.close() }
    }
    val fullScreen = vm.fullscreen
    SideEffect {
        (context as? Activity)?.let {
            val controller = WindowCompat.getInsetsController(it.window, rootView)
            if (fullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(rootView) {
        onDispose { (context as? Activity)?.let { WindowCompat.getInsetsController(it.window, rootView)
            .show(WindowInsetsCompat.Type.systemBars()) } }
    }
    LaunchedEffect(Unit) { vm.hub.bottomInset = 0f; vm.hub.resizing = false }
    // v44.16: las miniaturas de Efectos se preparan en segundo plano al abrir el editor
    // (primero las populares): al entrar en Efectos ya están y no hay parpadeo.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        // v44.17: también las de Animar (al cambiar de pestaña allí había el mismo destello).
        ThumbCache.prewarm(context, (com.zeta.fondo.model.EffectCatalog.popular +
            com.zeta.fondo.model.EffectCatalog.groupIds.values.flatten() +
            com.zeta.fondo.model.AnimCatalog.entries.values.flatten().map { it.thumb }.filter { it.isNotEmpty() }).distinct())
    }
    val onExport: () -> Unit = { vm.updateOriginalPreview(false); vm.finishEdit(true); exportOpen = true }

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        val screenW = maxWidth
        val screenH = maxHeight
        val layout = EditorLayout.compute(screenW.value, screenH.value, vm.previewAspect, vm.fullscreen, vm.previewSize)
        // El lienzo es SIEMPRE el primer hijo y en la misma posición del árbol: entrar o
        // salir de pantalla completa no recrea la superficie OpenGL. Su tamaño no depende
        // de la herramienta, de la categoría ni de las opciones abiertas.
        val canvasModifier = when {
            vm.fullscreen -> Modifier.fillMaxSize()
            layout.landscape -> Modifier.offset(x = 10.dp, y = layout.topBar.dp)
                .width(screenW - layout.sideW.dp - 18.dp).height(layout.canvasH.dp)
            else -> Modifier.offset(x = 10.dp, y = layout.topBar.dp)
                .width(screenW - 20.dp).height(layout.canvasH.dp)
        }
        EditorCanvas(vm, canvasModifier, framed = !vm.fullscreen)

        if (!vm.fullscreen) {
            val bottomStack: @Composable ColumnScope.() -> Unit = {
                PreviewControlBar(vm, Modifier.fillMaxWidth().height(layout.controls.dp), onDuration = { durationOpen = true })
                // v41: línea de tiempo + UNA barra, o una hoja que cubre ambas (catálogo, Ajustar…).
                EditorBottomArea(vm, Modifier.fillMaxWidth().weight(1f), layout.toolRow.dp)
            }
            if (layout.landscape) {
                EditorTopBar(vm, Modifier.width(screenW - layout.sideW.dp).height(layout.topBar.dp), onExport)
                Column(Modifier.align(Alignment.TopEnd).width(layout.sideW.dp).fillMaxHeight()
                    .background(Night), content = bottomStack)
            } else {
                EditorTopBar(vm, Modifier.fillMaxWidth().height(layout.topBar.dp), onExport)
                Column(Modifier.offset(y = layout.topBar.dp + layout.canvasH.dp).fillMaxWidth()
                    .height((screenH - layout.topBar.dp - layout.canvasH.dp).coerceAtLeast(0.dp)),
                    content = bottomStack)
            }
        }
        // v44.27: escribir un texto (encima de todo; los controles suben con el teclado).
        if (vm.textEditor != null) TextEditorOverlay(vm, Modifier.fillMaxSize())
    }
    vm.helpTab?.let { tab -> EditorHelpDialog(tab) { vm.helpTab = null } }
    if (durationOpen) {
        EditorDurationDialog(vm) { durationOpen = false }
    }
    if (exportOpen) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val exportDialogMaxHeight = (screenHeightDp * 0.90f).dp.coerceIn(430.dp, 690.dp)
        Dialog(
            onDismissRequest = { if (vm.exportState !is ExportState.Running) exportOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val state = vm.exportState
            when (state) {
                is ExportState.Running -> Column(Modifier.fillMaxWidth(.90f).clip(RoundedCornerShape(16.dp))
                    .background(StudioPanel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Exportando…", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${(state.progress * 100).toInt().coerceIn(0, 100)}%",
                        color = StudioPurple, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    ProgressBar(state.progress)
                    StudioSmallButton("Cancelar", Icons.Filled.Close, Modifier.fillMaxWidth()) { vm.cancelExport() }
                }
                is ExportState.Done -> Box(Modifier.fillMaxWidth(.92f)) {
                    ExportResultCard(vm, state.result) { vm.dismissExportResult(); exportOpen = false }
                }
                else -> Column(
                    Modifier.fillMaxWidth(.92f).heightIn(max = exportDialogMaxHeight)
                        .clip(RoundedCornerShape(16.dp)).background(StudioPanel)
                ) {
                    Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Exportar vídeo", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, "Cerrar", tint = StudioText, modifier = Modifier.size(44.dp)
                            .clickable { exportOpen = false }.padding(11.dp))
                    }
                    ExportPanel(vm, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Controles asociados al lienzo, justo debajo (no lo tapan): tiempo actual / total,
 * reproducir en el centro, deshacer y rehacer a la derecha, pantalla completa a la izquierda.
 */
@Composable
private fun PreviewControlBar(vm: AppViewModel, modifier: Modifier, onDuration: () -> Unit) {
    var position by remember { mutableFloatStateOf(vm.scrubT) }
    LaunchedEffect(Unit) {
        snapshotFlow { vm.playing }.collectLatest { playing ->
            if (!playing) snapshotFlow { vm.scrubT }.collect { position = it }
            else while (true) {
                // v42: el texto del tiempo cambia a lo sumo 10 veces por segundo.
                withFrameNanos { }
                val p = vm.hub.currentT()
                if (kotlin.math.abs(p - position) * vm.exportSettings.loopSec >= .1f || p < position) position = p
            }
        }
    }
    val total = vm.exportSettings.loopSec.toFloat()
    Box(modifier.background(Night).padding(horizontal = 6.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            SmallRound(Icons.Filled.Fullscreen, "Pantalla completa", true) { vm.fullscreen = true }
            val notice = vm.undoNotice
            if (notice != null) {
                Row(Modifier.padding(start = 4.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
                    .background(StudioTile).clickable { vm.undoFromNotice() }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(notice, color = StudioMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 92.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Deshacer", color = StudioPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("${clockText(position * total, tenths = true)} / ${clockText(total)}", color = StudioText, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDuration)
                        .padding(horizontal = 6.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            SmallRound(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (vm.playing) "Pausar" else "Reproducir", true, size = 44) { vm.togglePlay() }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SmallRound(Icons.Filled.Undo, "Deshacer", vm.canUndo) { vm.undo() }
            SmallRound(Icons.Filled.Redo, "Rehacer", vm.canRedo) { vm.redo() }
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, enabled: Boolean, size: Int = 36,
                       highlight: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape)
        .background(if (highlight) StudioPink.copy(alpha = .9f) else Color.Transparent)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) StudioText else StudioMuted.copy(alpha = .35f),
            modifier = Modifier.size((size * .58f).dp))
    }
}

/** Lienzo: superficie OpenGL + guías y gestos + controles discretos del encuadre. */
@Composable
private fun EditorCanvas(vm: AppViewModel, modifier: Modifier, framed: Boolean) {
    Box(modifier.background(Color.Black)) {
        // La superficie OpenGL ocupa siempre todo el lienzo.
        GlPreview(vm, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val rect = ImageRect.fit(wPx, hPx, vm.previewAspect)
            val fallback = vm.previewFallback
            if (!vm.previewReady && fallback != null && !fallback.isRecycled) {
                Image(bitmap = fallback.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            val imageBox = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            when {
                // v44.27: recortar una capa (marco sobre su imagen original completa).
                vm.layerCrop != null -> LayerCropOverlay(vm, Modifier.fillMaxSize())
                vm.cropEditing -> CropOverlay(vm, imageBox)
                vm.editingEffect == "appear" && vm.hasCharacter && !vm.fullscreen -> CharacterOverlay(vm, imageBox)
                vm.editingEffect == "elements" && vm.selectedElement >= 0 && !vm.fullscreen -> ElementOverlay(vm, imageBox)
                // v44.23: editando un efecto → su zona con forma (círculo / rectángulo) o su punto
                // de origen se arrastran directo sobre la imagen (antes solo desde la línea de tiempo).
                vm.editingEffect != null && !vm.paintingZone && !vm.fullscreen && vm.tool == Tool.PAN &&
                    vm.zoneShapeActive(vm.editingEffect!!) -> ZoneShapeOverlay(vm, rect, Modifier.fillMaxSize())
                vm.editingEffect != null && !vm.paintingZone && !vm.fullscreen && vm.tool == Tool.PAN &&
                    vm.canPlace(vm.editingEffect!!) && vm.placementClip() != null ->
                    EffectPlacementOverlay(vm, rect, Modifier.fillMaxSize())
                // v44.23: capa de imagen seleccionada → caja con tiradores (mover, tamaño, giro).
                vm.selectedLayer != null && !vm.fullscreen && vm.editingEffect == null && !vm.paintingZone ->
                    LayerTransformOverlay(vm, rect, Modifier.fillMaxSize())
                // v40: Posición del bloque seleccionado (mover / tamaño / dirección) sin tocar el zoom.
                vm.placingClip && !vm.fullscreen && vm.editingEffect == null -> EffectPlacementOverlay(vm, rect, Modifier.fillMaxSize())
                // Los gestos cubren TODO el lienzo (también las franjas negras): el zoom
                // funciona aunque los dedos caigan fuera de la imagen.
                else -> GuidesOverlay(vm, rect, Modifier.fillMaxSize())
            }
            if (framed) RoundedCornerMask(16.dp, Night)
            vm.glError?.let { error ->
                Box(Modifier.fillMaxSize().background(Night).padding(12.dp)) {
                    SelectionContainer { Text(error, color = StudioText, fontSize = 12.sp) }
                }
            }
            if (vm.layerCrop == null) CanvasControls(vm)
        }
    }
}

/**
 * Las superficies OpenGL (SurfaceView) no se recortan con clip(): se dibujan las cuatro
 * esquinas con el color del fondo encima para que el lienzo se vea redondeado.
 */
@Composable
private fun RoundedCornerMask(radius: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val r = radius.toPx()
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(r, r)))
        }
        drawPath(path, color)
    }
}

/** Controles discretos dentro del lienzo: comparar y zoom arriba; en pantalla completa, play y salir. */
@Composable
private fun BoxScope.CanvasControls(vm: AppViewModel) {
    // v44.18: con «Mejorar imagen» abierto, el lienzo compara antes / después con una línea.
    if (vm.srPanelOpen) vm.srCompare?.let { (before, after) -> BeforeAfterSplit(before, after, Modifier.matchParentSize()) }
    if (vm.fullscreen) {
        Row(Modifier.align(Alignment.BottomCenter).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FloatingRound(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (vm.playing) "Pausar" else "Reproducir", true, size = 50, highlight = vm.playing) { vm.togglePlay() }
            FloatingRound(Icons.Filled.FullscreenExit, "Salir de pantalla completa", true, size = 50) { vm.fullscreen = false }
        }
    }
    // Arriba a la derecha: comparar con el original y ajustar el zoom (solo si hay zoom).
    val zoomed by remember { derivedStateOf { vm.viewScale > 1.01f } }
    Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val original = vm.showOriginal
        FloatingRound(if (original) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            if (original) "Ver animación" else "Comparar con la imagen original", true, size = 34,
            highlight = original) { vm.updateOriginalPreview(!original) }
        if (zoomed) FloatingRound(Icons.Filled.CenterFocusWeak, "Ajustar a la pantalla", true, size = 34) { vm.resetView(true) }
    }
    // Arriba a la izquierda: un aviso corto (zona que falta, modo pincel o efectos cargando).
    // derivedStateOf: el aviso solo se recalcula cuando cambian los ajustes o las zonas,
    // no en cada recomposición del lienzo.
    val warning by remember { derivedStateOf { vm.zoneWarning } }
    val anyEffect by remember { derivedStateOf { vm.activeEffectsCount > 0 } }
    val status: Pair<ImageVector, String>? = when {
        vm.hint != null -> Icons.Filled.Lightbulb to (vm.hint ?: "")
        vm.layerCrop != null -> Icons.Filled.Crop to "Esquinas y lados = tamaño · dentro = mover"
        vm.placingClip -> Icons.Filled.OpenWith to "Moviendo el efecto · 2 dedos = zoom"
        vm.selectedLayer != null && !vm.blendPanelOpen && !vm.layerMotionOpen -> Icons.Filled.OpenWith to "Arrastra · esquinas = tamaño · punto rosa = girar"
        vm.paintingZone -> Icons.Filled.Brush to "1 dedo pinta · 2 dedos zoom"
        warning != null -> Icons.Filled.Brush to (warning ?: "")
        !vm.effectsReady && vm.previewReady && anyEffect -> Icons.Filled.HourglassTop to "Preparando efectos…"
        else -> null
    }
    status?.let { (icon, text) ->
        Row(Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .62f))
            .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = StudioOrange, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = StudioText, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FloatingRound(icon: ImageVector, label: String, enabled: Boolean, size: Int,
                          highlight: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape)
        .background(if (highlight) StudioPink.copy(alpha = .92f) else Color.Black.copy(alpha = .55f))
        .border(1.dp, Color.White.copy(alpha = .14f), CircleShape)
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) Color.White else Color.White.copy(alpha = .32f),
            modifier = Modifier.size((size * .5f).dp))
    }
}

private fun timeText(sec: Float): String = String.format(java.util.Locale.US, "%.1f", sec.coerceAtLeast(0f))

@Composable
private fun EditorDurationDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var seconds by remember(vm.exportSettings.loopSec) { mutableIntStateOf(vm.exportSettings.loopSec) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Duración del vídeo", color = StudioText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Los bloques de efectos se ajustan al nuevo tiempo.", color = StudioMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4, 6, 10, 15, 30).forEach { value ->
                    Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (seconds == value) StudioPink.copy(alpha = .3f) else StudioTile)
                        .clickable { seconds = value }, contentAlignment = Alignment.Center) {
                        Text("${value}s", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("$seconds segundos", color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Slider(value = seconds.toFloat(), onValueChange = { seconds = it.toInt().coerceIn(2, 60) },
                valueRange = 2f..60f, steps = 57, colors = SliderDefaults.colors(
                    thumbColor = StudioPink, activeTrackColor = StudioPink, inactiveTrackColor = StudioTile))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                    .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Cancelar", color = StudioMuted, fontSize = 13.sp)
                }
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(StudioGradient)
                    .clickable {
                        vm.updateExport(vm.exportSettings.copy(loopSec = seconds))
                        vm.saveProject()
                        onDismiss()
                    }, contentAlignment = Alignment.Center) {
                    Text("Aplicar", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Barra superior mínima: volver · proyecto · captura · Exportar. */
@Composable
private fun EditorTopBar(vm: AppViewModel, modifier: Modifier, onExport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importImage(uri)
    }
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TopIcon(Icons.Filled.ArrowBack, "Volver a proyectos") { vm.closeEditor() }
        Box {
            TopIcon(Icons.Filled.FolderOpen, "Proyecto") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                modifier = Modifier.background(StudioPanel)) {
                DropdownMenuItem(text = { Text("Guardar ahora", color = StudioText) },
                    leadingIcon = { Icon(Icons.Filled.Save, null, tint = StudioText) },
                    onClick = { menu = false; vm.saveProject(); vm.message = "Proyecto guardado" })
                DropdownMenuItem(text = { Text("Nuevo proyecto con otra imagen", color = StudioText) },
                    leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null, tint = StudioText) },
                    onClick = {
                        menu = false
                        picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif",
                            "image/heic", "image/heif", "image/*"))
                    })
                DropdownMenuItem(text = { Text("Mis proyectos", color = StudioText) },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, tint = StudioText) },
                    onClick = { menu = false; vm.closeEditor() })
            }
        }
        // v44.16: Captura = guarda el cuadro actual como imagen en la Galería.
        TopIcon(if (vm.capturing) Icons.Filled.HourglassTop else Icons.Filled.PhotoCamera, "Captura") { vm.captureFrame() }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(end = 6.dp).height(38.dp).clip(RoundedCornerShape(12.dp)).background(StudioGradient)
            .clickable(onClick = onExport).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FileUpload, null, tint = StudioText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Exportar", color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = StudioText, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun GlPreview(vm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember { PreviewRenderer(vm.hub, vm.vertSrc, vm.fragSrc, vm.liteSrc, vm.shaderCacheDir) }
    val view = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    // v41: al salir del editor se detiene el hilo que compila las variantes del shader.
    DisposableEffect(renderer) { onDispose { renderer.dispose() } }
    // Lupa al pintar (v44.26: siempre, el mínimo ya es Android 10).
    val magnifier = remember(view) { android.widget.Magnifier(view) }   // mismo aspecto que antes
    val cursor = vm.brushCursor
    LaunchedEffect(cursor, vm.paintingZone) {
        if (cursor != null && vm.paintingZone) {
            val r = ImageRect.fit(view.width.toFloat(), view.height.toFloat(), vm.hub.displayAspect)
            magnifier.show(r.left + cursor.x.coerceIn(0f, 1f) * r.width, r.top + cursor.y.coerceIn(0f, 1f) * r.height)
        }
        else magnifier.dismiss()
    }
    DisposableEffect(magnifier) { onDispose { magnifier.dismiss() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, view) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { view.onResume(); view.requestRender() }
            if (event == Lifecycle.Event.ON_PAUSE) view.onPause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); view.onPause() }
    }
    // Cualquier cambio (zoom, pincel, parámetros) pide un cuadro al instante.
    DisposableEffect(view) {
        val hub = vm.hub
        hub.onInvalidate = { view.requestRender() }
        hub.onRenderLater = { ms -> view.postDelayed({ view.requestRender() }, ms) }
        onDispose {
            if (hub.onInvalidate != null) hub.onInvalidate = null
            hub.onRenderLater = null
        }
    }
    // v38: el reloj de cuadros solo existe mientras se reproduce (antes despertaba la CPU
    // 60 veces por segundo también en pausa). Pide cuadros al ritmo del vídeo exportado.
    val playing = vm.playing
    LaunchedEffect(view, playing) {
        if (!playing) return@LaunchedEffect
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { now ->
                val fps = vm.exportSettings.fps.coerceIn(12, 60)
                val cap = if (vm.lowResourceDevice) minOf(fps, 30) else fps
                vm.hub.previewFps = cap
                val interval = 1_000_000_000L / cap
                if (vm.hub.renderEnabled && now - lastFrameNs >= interval - 4_000_000L) {
                    lastFrameNs = now
                    view.requestRender()
                }
            }
        }
    }
    // Teléfonos con poca RAM: la superficie se dibuja al 75 % y el compositor la escala.
    DisposableEffect(view) {
        val listener = android.view.View.OnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val w = r - l; val h = b - t
            if (vm.lowResourceDevice && w > 0 && h > 0) {
                (v as GLSurfaceView).holder.setFixedSize((w * .75f).toInt().coerceAtLeast(1), (h * .75f).toInt().coerceAtLeast(1))
            }
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * Guías y gestos sobre TODO el lienzo. [rect] = dónde se ve la imagen dentro del lienzo.
 * - Dos dedos: zoom y desplazamiento SIEMPRE, con cualquier herramienta. Nunca pintan.
 * - Pincel: empieza solo cuando el dedo se mueve (un toque sin mover = un punto).
 * - Un dedo fuera de la imagen (franjas negras): desplaza la vista.
 */
@Composable
private fun GuidesOverlay(vm: AppViewModel, rect: ImageRect, modifier: Modifier) {
    var dragArrow by remember { mutableStateOf<Arrow?>(null) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    val rectState = rememberUpdatedState(rect)

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val r = rectState.value
                fun uvOf(o: Offset): PointN = vm.screenToUv(r.normX(o.x), r.normY(o.y))
                fun radiusUv(): Float = vm.brushDp.dp.toPx() * vm.cropH / (r.height.coerceAtLeast(1f) * vm.viewScale)
                val insideImage = r.contains(down.position.x, down.position.y)
                val tool = if (vm.fullscreen || vm.exportState is ExportState.Running ||
                    (!vm.showGuides && !vm.paintingZone) || !insideImage) Tool.PAN else vm.tool
                var mode = if (tool == Tool.PAN) GestureRules.Mode.TRANSFORM else GestureRules.Mode.WAITING
                var drawing = false
                var arrowStart: PointN? = null
                var moved = false
                var lastPos = down.position
                val slop = viewConfiguration.touchSlop * 0.6f

                when {
                    tool == Tool.ARROW -> arrowStart = uvOf(down.position)
                    tool == Tool.ORIGIN -> vm.moveEffectOrigin(uvOf(down.position))
                    tool.isBrush -> {
                        cursor = down.position
                        vm.brushCursor = PointN(r.normX(down.position.x), r.normY(down.position.y))
                    }
                    else -> Unit
                }
                down.consume()

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break
                    val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    val dist = (ch.position - down.position).getDistance()
                    val previous = mode
                    mode = GestureRules.next(mode, pressed, dist, slop, toolDraws = tool != Tool.PAN)
                    if (mode == GestureRules.Mode.TRANSFORM && previous != GestureRules.Mode.TRANSFORM) {
                        // Llegó el segundo dedo: se cancela cualquier trazo y nunca se pinta.
                        if (drawing) { vm.cancelStroke(); drawing = false }
                        cursor = null
                        vm.brushCursor = null
                        arrowStart = null
                        dragArrow = null
                    }
                    if (mode == GestureRules.Mode.TRANSFORM) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        // Vista ligera solo cuando de verdad hay zoom o desplazamiento (un
                        // toque con temblor no hace parpadear los efectos).
                        if (!vm.hub.gestureActive && (abs(zoom - 1f) > 0.002f || pan.getDistance() > 0.5f))
                            vm.hub.gestureActive = true
                        val c = event.calculateCentroid(useCurrent = true)
                        if (c.isSpecified) vm.transformView(zoom, pan.x / r.width.coerceAtLeast(1f),
                            pan.y / r.height.coerceAtLeast(1f), r.normX(c.x), r.normY(c.y))
                    } else {
                        val pos = ch.position
                        if (dist > 8f) moved = true
                        lastPos = pos
                        val start = arrowStart
                        if (mode == GestureRules.Mode.DRAW && previous == GestureRules.Mode.WAITING && tool.isBrush) {
                            vm.beginStroke(uvOf(down.position), radiusUv())
                            drawing = true
                        }
                        if (tool == Tool.ORIGIN) {
                            vm.moveEffectOrigin(uvOf(pos))
                        } else if (tool == Tool.ARROW && start != null) {
                            val e = uvOf(pos)
                            dragArrow = Arrow(start.x, start.y, e.x, e.y)
                        } else if (drawing) {
                            vm.continueStroke(uvOf(pos))
                        }
                        if (tool.isBrush) {
                            cursor = pos
                            vm.brushCursor = PointN(r.normX(pos.x), r.normY(pos.y))
                        }
                    }
                    event.changes.forEach { it.consume() }
                }

                if (mode != GestureRules.Mode.TRANSFORM && vm.tapSelectFor != null) {
                    // v47: «Tocar para seleccionar» → región parecida al punto tocado.
                    if (!moved) vm.tapSelectAt(uvOf(lastPos))
                } else if (mode != GestureRules.Mode.TRANSFORM) {
                    val local = Offset(lastPos.x - r.left, lastPos.y - r.top)
                    when (tool) {
                        Tool.ARROW -> {
                            val a = dragArrow
                            if (a != null && hypot(a.x1 - a.x0, a.y1 - a.y0) > 0.008f) vm.addArrow(a)
                        }
                        Tool.ANCHOR -> if (!moved) vm.addAnchor(uvOf(lastPos))
                        Tool.ERASE_GUIDE -> if (!moved) {
                            eraseNearest(vm, local, r.width, r.height, 28.dp.toPx())
                        }
                        Tool.FREEZE, Tool.ZONE1, Tool.ZONE2, Tool.MASK -> {
                            // Toque sin arrastrar: un punto del pincel.
                            if (!drawing) vm.beginStroke(uvOf(down.position), radiusUv())
                            vm.endStroke()
                        }
                        Tool.PAN, Tool.ORIGIN -> Unit
                    }
                } else if (drawing) {
                    vm.endStroke()
                }
                if (vm.hub.gestureActive) vm.hub.gestureActive = false
                dragArrow = null
                cursor = null
                vm.brushCursor = null
            }
        }
    ) {
        val r = rect
        val s = vm.viewScale
        val vx = vm.viewX
        val vy = vm.viewY
        fun sp(x: Float, y: Float) = Offset(r.left + (x - vx) * s * r.width / vm.cropW, r.top + (y - vy) * s * r.height / vm.cropH)

        clipRect(r.left, r.top, r.left + r.width, r.top + r.height) {
            if (vm.showGuides && !vm.playing && !vm.fullscreen && !vm.paintingZone) {
                vm.activeMotionEffect?.let { id ->
                    val origin = (vm.settings["$id.origin"] ?: 0f).toInt()
                    if (origin == 0) {
                        val pt = sp(vm.settings["$id.originX"] ?: 0.5f, vm.settings["$id.originY"] ?: 0.5f)
                        drawCircle(Peach.copy(alpha = 0.35f), 13.dp.toPx(), pt)
                        drawCircle(Peach, 8.dp.toPx(), pt, style = Stroke(2.dp.toPx()))
                        drawLine(Peach, pt + Offset(-16.dp.toPx(), 0f), pt + Offset(16.dp.toPx(), 0f), strokeWidth = 1.dp.toPx())
                        drawLine(Peach, pt + Offset(0f, -16.dp.toPx()), pt + Offset(0f, 16.dp.toPx()), strokeWidth = 1.dp.toPx())
                    }
                }
                for (a in vm.arrows) drawArrowShape(sp(a.x0, a.y0), sp(a.x1, a.y1), Color.White)
                for (p in vm.anchors) drawAnchor(sp(p.x, p.y))
            }
            dragArrow?.let { drawArrowShape(sp(it.x0, it.y0), sp(it.x1, it.y1), Peach) }
        }
        cursor?.let {
            val rr = vm.brushDp.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.5f), rr, it, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, rr, it, style = Stroke(1.5.dp.toPx()))
        }
    }
}

private fun eraseNearest(vm: AppViewModel, pos: Offset, w: Float, h: Float, maxPx: Float) {
    val s = vm.viewScale
    fun sp(x: Float, y: Float) = Offset((x - vm.viewX) * s * w / vm.cropW, (y - vm.viewY) * s * h / vm.cropH)
    var best = maxPx
    var bestArrow = -1
    var bestAnchor = -1
    // v44.10: las anclas se quitan desde cualquier pestaña (antes, en Animar, solo las flechas).
    vm.anchors.forEachIndexed { i, p ->
        val d = (sp(p.x, p.y) - pos).getDistance()
        if (d < best) {
            best = d
            bestAnchor = i
            bestArrow = -1
        }
    }
    val anchorBest = best
    vm.arrows.forEachIndexed { i, a ->
        val d = segDist(pos, sp(a.x0, a.y0), sp(a.x1, a.y1))
        // Un ancla tocada gana a una flecha que pase cerca (los puntos son más pequeños).
        if (d < best && (bestAnchor < 0 || d < anchorBest * .5f)) {
            best = d
            bestArrow = i
            bestAnchor = -1
        }
    }
    if (bestArrow >= 0) vm.removeArrowAt(bestArrow)
    else if (bestAnchor >= 0) vm.removeAnchorAt(bestAnchor)
}

private fun segDist(p: Offset, a: Offset, b: Offset): Float {
    val d = b - a
    val l2 = d.x * d.x + d.y * d.y
    var t = if (l2 > 0.0001f) ((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2 else 0f
    t = t.coerceIn(0f, 1f)
    return (a + d * t - p).getDistance()
}

private fun DrawScope.drawArrowShape(a: Offset, b: Offset, color: Color) {
    val sw = 3.dp.toPx()
    val shadow = Color.Black.copy(alpha = 0.55f)
    drawLine(shadow, a, b, strokeWidth = sw + 3.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, a, b, strokeWidth = sw, cap = StrokeCap.Round)
    val d = b - a
    val len = d.getDistance()
    if (len > 1f) {
        val ux = -d.x / len
        val uy = -d.y / len
        val hl = 14.dp.toPx()
        val c = 0.866f
        val s = 0.5f
        val l1 = Offset(ux * c - uy * s, ux * s + uy * c) * hl
        val l2 = Offset(ux * c + uy * s, -ux * s + uy * c) * hl
        drawLine(color, b, b + l1, strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, b, b + l2, strokeWidth = sw, cap = StrokeCap.Round)
    }
    drawCircle(shadow, 5.5.dp.toPx(), a)
    drawCircle(color, 4.dp.toPx(), a)
}

private fun DrawScope.drawAnchor(p: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.55f), 9.dp.toPx(), p)
    drawCircle(Sky, 7.dp.toPx(), p)
    drawCircle(Color.White, 2.5.dp.toPx(), p)
}

private fun DrawScope.drawCropShade(tl: Offset, br: Offset, full: Size) {
    val shade = Color.Black.copy(alpha = 0.55f)
    // arriba, abajo, izquierda, derecha
    drawRect(shade, Offset(0f, 0f), Size(full.width, tl.y.coerceAtLeast(0f)))
    drawRect(shade, Offset(0f, br.y), Size(full.width, (full.height - br.y).coerceAtLeast(0f)))
    drawRect(shade, Offset(0f, tl.y), Size(tl.x.coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)))
    drawRect(shade, Offset(br.x, tl.y), Size((full.width - br.x).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f)))
    drawRect(Peach, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(2.dp.toPx()))
}

@Composable
private fun ElementOverlay(vm: AppViewModel, modifier: Modifier) {
    Canvas(modifier.pointerInput(vm.selectedElement) {
        detectTransformGestures { _, pan, zoom, rotation ->
            vm.transformElement(pan.x / size.width * vm.cropW / vm.viewScale,
                pan.y / size.height * vm.cropH / vm.viewScale, zoom, rotation)
        }
    }) {
        val id = "element${vm.selectedElement}"
        val x = ((vm.settings["$id.x"] ?: .5f) - vm.viewX) * size.width * vm.viewScale / vm.cropW
        val y = ((vm.settings["$id.y"] ?: .5f) - vm.viewY) * size.height * vm.viewScale / vm.cropH
        val h = (vm.settings["$id.size"] ?: .3f) * size.height * vm.viewScale / vm.cropH
        drawCircle(StudioPurple, 5.dp.toPx(), Offset(x,y))
        rotate(vm.settings["$id.rotation"] ?: 0f, Offset(x,y)) {
            drawRect(StudioPurple, Offset(x-h/2,y-h/2), Size(h,h), style=Stroke(1.dp.toPx()))
        }
    }
}


/** Marco del encuadre: arrastra para moverlo y pellizca para cambiar su tamaño. */
@Composable
private fun CropOverlay(vm: AppViewModel, modifier: Modifier) {
    Canvas(modifier.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            vm.moveCrop(pan.x / size.width * vm.cropW / vm.viewScale,
                pan.y / size.height * vm.cropH / vm.viewScale, 1f / zoom.coerceIn(0.5f, 2f))
        }
    }) {
        val f = vm.cropFrame()
        fun sp(x: Float, y: Float) = Offset((x - vm.viewX) * size.width * vm.viewScale / vm.cropW,
            (y - vm.viewY) * size.height * vm.viewScale / vm.cropH)
        val tl = sp(f[0], f[1])
        val br = sp(f[0] + f[2], f[1] + f[3])
        drawCropShade(tl, br, size)
        val w = br.x - tl.x
        val h = br.y - tl.y
        val grid = Color.White.copy(alpha = .45f)
        for (k in 1..2) {
            drawLine(grid, Offset(tl.x + w * k / 3f, tl.y), Offset(tl.x + w * k / 3f, br.y), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(tl.x, tl.y + h * k / 3f), Offset(br.x, tl.y + h * k / 3f), strokeWidth = 1.dp.toPx())
        }
        val c = 18.dp.toPx()
        val sw = 4.dp.toPx()
        for ((p, dx, dy) in listOf(Triple(tl, 1f, 1f), Triple(Offset(br.x, tl.y), -1f, 1f),
                Triple(Offset(tl.x, br.y), 1f, -1f), Triple(br, -1f, -1f))) {
            drawLine(Color.White, p, p + Offset(c * dx, 0f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(Color.White, p, p + Offset(0f, c * dy), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** Personaje del efecto Aparición: arrastrar, pellizcar y girar. */
@Composable
private fun CharacterOverlay(vm: AppViewModel, modifier: Modifier) {
    Canvas(modifier.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, rotation ->
            vm.transformCharacter(pan.x / size.width * vm.cropW / vm.viewScale,
                pan.y / size.height * vm.cropH / vm.viewScale, zoom, rotation)
        }
    }) {
        val ch = vm.hub.character ?: return@Canvas
        val charAspect = ch.width.toFloat() / ch.height.coerceAtLeast(1)
        val x = ((vm.settings["appear.x"] ?: .5f) - vm.viewX) * size.width * vm.viewScale / vm.cropW
        val y = ((vm.settings["appear.y"] ?: .62f) - vm.viewY) * size.height * vm.viewScale / vm.cropH
        val hh = (vm.settings["appear.size"] ?: .6f) * size.height * vm.viewScale / vm.cropH
        val sizeUv = vm.settings["appear.size"] ?: .6f
        val ww = sizeUv * charAspect / (vm.aspect.takeIf { it > 0f } ?: 1f) * size.width * vm.viewScale / vm.cropW
        rotate(vm.settings["appear.rot"] ?: 0f, Offset(x, y)) {
            drawRect(StudioPurple, Offset(x - ww / 2f, y - hh / 2f), Size(ww, hh), style = Stroke(2.dp.toPx()))
        }
        drawCircle(StudioPurple, 6.dp.toPx(), Offset(x, y))
    }
}

