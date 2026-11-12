package com.zeta.fondo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.EditorTab
import com.zeta.fondo.model.Tool

/*
 * v38 · Herramientas del editor (fila compacta sobre las categorías) y el panel de cada
 * herramienta. Una sola regla para toda la app:
 *   categoría (abajo) → herramientas (fila) → opciones (panel sobre la fila).
 */

// ------------------------------------------------------------------ Barra inferior (v41)
// Una sola barra, como en un editor de vídeo:
//   principal: Animar · Proteger · Efectos · Audio · Ajustes
//   categoría o selección: «<» + sus herramientas (tocar «<» vuelve a la principal).

private data class MainItem(val label: String, val icon: ImageVector, val action: (AppViewModel) -> Unit)

private val mainItems = listOf(
    MainItem("Animar", Icons.Filled.PlayCircleOutline) { it.openCategory(EditorTab.TOOLS) },
    MainItem("Proteger", Icons.Filled.Shield) { it.openCategory(EditorTab.MASKS) },
    MainItem("Efectos", Icons.Filled.AutoAwesome) { it.openCategory(EditorTab.EFFECTS) },
    MainItem("Audio", Icons.Filled.MusicNote) { it.openAudio() },
    MainItem("Ajustes", Icons.Filled.Tune) { it.openCategory(EditorTab.CANVAS) }
)

/** Barra principal: iconos con su nombre, sin fondos, repartidos a lo ancho. */
@Composable
fun MainBar(vm: AppViewModel, modifier: Modifier) {
    // v44.23: «Imagen» agrega otra imagen encima (capa), como en Alight Motion o ibisPaint.
    // v44.25: se pueden elegir VARIAS a la vez (hasta el máximo del teléfono; ver addLayers).
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(
            com.zeta.fondo.model.ImageLayers.maxLayers(false))) { uris -> if (uris.isNotEmpty()) vm.addLayers(uris) }
    val items = mainItems.take(3) + MainItem("Imagen", Icons.Filled.AddPhotoAlternate) { model ->
        val limit = com.zeta.fondo.model.ImageLayers.maxLayers(model.lowResourceDevice)
        if (model.layers.size >= limit) {
            model.message = "Máximo $limit imágenes por proyecto en este teléfono."
            return@MainItem
        }
        val ok = try {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            true
        } catch (_: Throwable) { false }
        if (!ok) model.message = "No se pudo abrir la galería"
    } + MainItem("Texto", Icons.Filled.TextFields) { it.openNewText() } + mainItems.drop(3)
    // v44.24: en tabletas los 6 botones no se separan de más (como máximo 560 dp, centrados);
    // en pantallas angostas el nombre se corta con «…» en vez de montarse.
    Box(modifier.background(Night), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                Column(Modifier.weight(1f).fillMaxHeight().clickable { item.action(vm) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.icon, null, tint = StudioText, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, color = StudioText, fontSize = 11.sp, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    }
}

/** «<» a la izquierda y las herramientas al lado, con desplazamiento lateral. */
@Composable
fun BarWithBack(modifier: Modifier, onBack: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(modifier.background(Night), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 8.dp, end = 4.dp).size(width = 40.dp, height = 52.dp)
            .clip(RoundedCornerShape(10.dp)).background(StudioTile).clickable(onClick = onBack),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronLeft, "Volver", tint = StudioText, modifier = Modifier.size(24.dp))
        }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()).padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
            content = content)
    }
}

/**
 * Botón de herramienta plano: icono y nombre, sin recuadro. Activo = rosa. Mantener
 * pulsado muestra una ayuda corta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolChip(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier,
             enabled: Boolean = true, badge: String? = null, help: String? = null,
             onHelp: ((String) -> Unit)? = null, onClick: () -> Unit) {
    val tint = when {
        !enabled -> StudioMuted.copy(alpha = .45f)
        selected -> StudioPink
        else -> StudioText
    }
    Box(modifier.width(64.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
        .combinedClickable(enabled = enabled, onClickLabel = label,
            onLongClick = if (help != null && onHelp != null) ({ onHelp(help) }) else null,
            onClick = onClick)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = tint, fontSize = 11.sp, lineHeight = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (badge != null) Text(badge, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp).clip(RoundedCornerShape(6.dp))
                .background(StudioPink).padding(horizontal = 4.dp))
    }
}

/** Botón «?» al final de cada fila: abre la ayuda de la categoría. */
@Composable
private fun HelpChip(vm: AppViewModel, tab: EditorTab) {
    Box(Modifier.width(48.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clickable { vm.helpTab = tab },
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.HelpOutline, "Ayuda", tint = StudioMuted, modifier = Modifier.size(20.dp))
    }
}

/** Contenido de una barra con «<» (las filas de cada categoría se dibujan aquí dentro). */
@Composable
private fun ToolRowContainer(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        content = content)
}

/** Ayudas cortas (pulsación larga en cada herramienta y diálogo «?»). */
internal val toolHelp = mapOf(
    "Ruta" to "Dibuja la dirección del movimiento",
    "Ancla" to "Toca para fijar un punto que no se mueve",
    "Eliminar" to "Toca una ruta o un ancla para quitarla",
    "Congelar" to "Pinta lo que no debe moverse",
    "Natural" to "Cabello, ropa, árboles y pasto con viento",
    "2.5D" to "Profundidad entre fondo y primer plano",
    "Ondular" to "Ondula suavemente una zona pintada",
    "Zona" to "Mueve solo la zona que pintes",
    "Cámara" to "Zoom, giro y desplazamiento suaves",
    "Bucle" to "Cómo empieza y termina el movimiento",
    "Pincel" to "Un dedo pinta · dos dedos hacen zoom",
    "Borrador" to "Borra lo pintado",
    "Rellenar" to "Pinta toda la capa",
    "Vaciar" to "Borra toda la capa",
    "Ver" to "Muestra u oculta la máscara",
    "Punto fijo" to "Toca para fijar un punto",
    "Quitar punto" to "Toca un punto fijo para quitarlo",
    "Opciones" to "Tamaño, borde, capa y transparencia"
)

/** Barra inferior según el contexto (una sola barra, nunca dos filas). */
@Composable
fun EditorToolRow(vm: AppViewModel, modifier: Modifier) {
    val editing = vm.editingEffect
    when {
        // v44.27: recortando una capa → ✕ · proporciones · Girar 90° · Original · ✓.
        vm.layerCrop != null -> LayerCropBar(vm, modifier)
        vm.paintingZone && editing != null -> BarWithBack(modifier, onBack = { vm.finishPainting() }) {
            BrushToolRow(vm, Modifier, onDone = { vm.finishPainting() })
        }
        // v44.23: capa de imagen seleccionada → sus acciones (Mezcla, Clave, Movimiento…).
        vm.selectedLayerObj() != null && !vm.paintingZone -> LayerActionRow(vm, modifier)
        // v40: bloque seleccionado en la línea de tiempo → sus acciones (edición por contexto).
        vm.selectedClip() != null && !vm.paintingZone -> ClipActionRow(vm, modifier)
        vm.imageSelected -> ImageActionRow(vm, modifier)
        !vm.categoryOpen -> MainBar(vm, modifier)
        vm.tab == EditorTab.TOOLS -> BarWithBack(modifier, onBack = { vm.backToMain() }) { AnimateToolRow(vm, Modifier) }
        vm.tab == EditorTab.MASKS -> BarWithBack(modifier, onBack = { vm.backToMain() }) { ProtectToolRow(vm, Modifier) }
        else -> BarWithBack(modifier, onBack = { vm.backToMain() }) { SettingsToolRow(vm, Modifier) }
    }
}

private val animEffects = listOf(
    Triple("natural", "Natural", Icons.Filled.Air),
    Triple("parallax", "2.5D", Icons.Filled.Layers),
    Triple("mesh", "Ondular", Icons.Filled.Waves),
    Triple("zonemotion", "Zona", Icons.Filled.Gesture),
    Triple("cam", "Cámara", Icons.Filled.Videocam)
)

/** v42 · Animaciones de cámara directas (modo del efecto Cámara). */
private val cameraPresets = listOf(
    Triple(0, "Acercar", Icons.Filled.ZoomIn),
    Triple(1, "Alejar", Icons.Filled.ZoomOut),
    Triple(6, "Latido", Icons.Filled.Favorite),
    Triple(7, "Flotar", Icons.Filled.SwapVert),
    Triple(8, "Balanceo", Icons.Filled.RotateRight),
    Triple(4, "Temblar", Icons.Filled.Vibration)
)

/** Separador fino entre grupos (herramientas de un dedo · animaciones · global). */
@Composable
private fun GroupDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(StudioMuted.copy(alpha = .25f)))
}

@Composable
private fun AnimateToolRow(vm: AppViewModel, modifier: Modifier) {
    val freezing = vm.paintingZone && vm.editingEffect == null
    val guide = vm.editingEffect == null && !freezing && vm.animPanel.isEmpty()
    val help: (String) -> Unit = { vm.showHint(it) }
    fun guideTool(t: Tool, name: String) {
        val wasSelected = vm.tool == t && guide
        vm.selectGuideTool(t)
        if (!wasSelected) vm.showHint(toolHelp.getValue(name))
    }
    ToolRowContainer(modifier) {
        // v44: el catálogo visual de animaciones (como Efectos).
        ToolChip("Catálogo", Icons.Filled.GridView, vm.animCatalogOpen) { vm.openAnimCatalog() }
        GroupDivider()
        // Herramientas de un dedo sobre la imagen
        ToolChip("Ruta", Icons.Filled.Timeline, guide && vm.tool == Tool.ARROW,
            badge = vm.arrows.size.takeIf { it > 0 }?.toString(), help = toolHelp["Ruta"], onHelp = help) { guideTool(Tool.ARROW, "Ruta") }
        ToolChip("Ancla", Icons.Filled.PushPin, guide && vm.tool == Tool.ANCHOR,
            badge = vm.anchors.size.takeIf { it > 0 }?.toString(), help = toolHelp["Ancla"], onHelp = help) { guideTool(Tool.ANCHOR, "Ancla") }
        ToolChip("Eliminar", Icons.Filled.DeleteOutline, guide && vm.tool == Tool.ERASE_GUIDE,
            enabled = vm.arrows.isNotEmpty() || vm.anchors.isNotEmpty(), help = toolHelp["Eliminar"], onHelp = help) { guideTool(Tool.ERASE_GUIDE, "Eliminar") }
        ToolChip("Congelar", Icons.Filled.AcUnit, freezing, help = toolHelp["Congelar"], onHelp = help) {
            if (freezing) vm.finishEdit(true) else { vm.freezeZone(); vm.showHint(toolHelp.getValue("Congelar")) }
        }
        GroupDivider()
        // Animaciones de la imagen (abren su vista previa con ✓ / ✕)
        animEffects.forEach { (id, label, icon) ->
            ToolChip(label, icon, vm.editingEffect == id, badge = if (vm.isEffectOn(id)) "●" else null,
                help = toolHelp[label], onHelp = help) {
                if (id == "zonemotion") vm.editZoneMotion() else vm.editEffect(id)
            }
        }
        GroupDivider()
        // v42 · Animaciones de cámara de un toque (como las animaciones de un editor de vídeo).
        cameraPresets.forEach { (mode, label, icon) ->
            val on = vm.isEffectOn("cam") && (vm.settings["cam.mode"] ?: 0f).toInt() == mode
            ToolChip(label, icon, vm.editingEffect == "cam" && on, badge = if (on) "●" else null) {
                vm.applyCameraPreset(mode)
            }
        }
        GroupDivider()
        ToolChip("Bucle", Icons.Filled.AllInclusive, vm.animPanel == "loop", help = toolHelp["Bucle"], onHelp = help) {
            vm.finishEdit(true)
            vm.animPanel = if (vm.animPanel == "loop") "" else "loop"
            if (vm.animPanel == "loop") vm.requestOptions()
        }
        HelpChip(vm, EditorTab.TOOLS)
    }
}

@Composable
private fun ProtectToolRow(vm: AppViewModel, modifier: Modifier) {
    val painting = vm.paintingZone
    val help: (String) -> Unit = { vm.showHint(it) }
    ToolRowContainer(modifier) {
        ToolChip("Pincel", Icons.Filled.Brush, painting && !vm.eraser, help = toolHelp["Pincel"], onHelp = help) {
            if (!painting) vm.protect()
            vm.eraser = false
        }
        ToolChip("Borrador", ZetaEraserIcon, painting && vm.eraser, help = toolHelp["Borrador"], onHelp = help) {
            if (!painting) vm.protect()
            vm.eraser = true
        }
        ToolChip("Rellenar", Icons.Filled.FormatColorFill, false, help = toolHelp["Rellenar"], onHelp = help) {
            if (!painting) vm.protect()
            vm.fillLayer(false)
        }
        ToolChip("Vaciar", Icons.Filled.LayersClear, false, help = toolHelp["Vaciar"], onHelp = help) {
            if (!painting) vm.protect()
            vm.fillLayer(true)
        }
        ToolChip(if (vm.showMasks) "Ocultar" else "Ver", if (vm.showMasks) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            false, help = toolHelp["Ver"], onHelp = help) { vm.toggleMasks() }
        ToolChip("Opciones", Icons.Filled.Tune, false, help = toolHelp["Opciones"], onHelp = help) {
            if (!painting) vm.protect()
            vm.requestOptions()
        }
        GroupDivider()
        ToolChip("Punto fijo", Icons.Filled.PushPin, !painting && vm.tool == Tool.ANCHOR, help = toolHelp["Punto fijo"], onHelp = help) {
            vm.protectPoints(); vm.showHint(toolHelp.getValue("Punto fijo"))
        }
        ToolChip("Quitar punto", Icons.Filled.DeleteOutline, !painting && vm.tool == Tool.ERASE_GUIDE,
            enabled = vm.anchors.isNotEmpty(), help = toolHelp["Quitar punto"], onHelp = help) { vm.protectPoints(true) }
        HelpChip(vm, EditorTab.MASKS)
    }
}

/** Pincel de la zona de un efecto: mismas herramientas + Listo. */
@Composable
private fun BrushToolRow(vm: AppViewModel, modifier: Modifier, onDone: () -> Unit) {
    val help: (String) -> Unit = { vm.showHint(it) }
    ToolRowContainer(modifier) {
        ToolChip("Pincel", Icons.Filled.Brush, !vm.eraser, help = toolHelp["Pincel"], onHelp = help) { vm.eraser = false }
        ToolChip("Borrador", ZetaEraserIcon, vm.eraser, help = toolHelp["Borrador"], onHelp = help) { vm.eraser = true }
        ToolChip("Rellenar", Icons.Filled.FormatColorFill, false, help = toolHelp["Rellenar"], onHelp = help) { vm.fillLayer(false) }
        ToolChip("Vaciar", Icons.Filled.LayersClear, false, help = toolHelp["Vaciar"], onHelp = help) { vm.fillLayer(true) }
        GroupDivider()
        ToolChip("Listo", Icons.Filled.Check, true, onClick = onDone)
    }
}

private val settingsTools = listOf(
    Triple(0, "Duración", Icons.Filled.Timer),
    Triple(1, "Formato", Icons.Filled.Crop),
    Triple(2, "Color", Icons.Filled.Palette),
    Triple(3, "Audio", Icons.Filled.MusicNote),
    Triple(4, "Bucle", Icons.Filled.AllInclusive),
    Triple(5, "Vista", Icons.Filled.AspectRatio),
    Triple(7, "Viento", Icons.Filled.Air),
    Triple(6, "Diagnóstico", Icons.Filled.Speed),
    Triple(9, "Mejorar", Icons.Filled.AutoAwesome),
    Triple(8, "Licencias", Icons.Filled.Info)
)

@Composable
private fun SettingsToolRow(vm: AppViewModel, modifier: Modifier) {
    ToolRowContainer(modifier) {
        settingsTools.forEach { (i, label, icon) ->
            ToolChip(label, icon, vm.settingsSection == i && vm.optionsOpen) {
                if (i != 1) vm.finishCropEdit()
                if (vm.settingsSection == i && vm.optionsOpen) vm.closeOptions()
                else { vm.settingsSection = i; vm.requestOptions() }
            }
        }
        GroupDivider()
        ToolChip(if (vm.advancedMode) "Avanzado" else "Fácil", if (vm.advancedMode) Icons.Filled.Tune else Icons.Filled.AutoFixHigh,
            vm.advancedMode) { vm.toggleAdvancedMode() }
    }
}

// ------------------------------------------------------------------ Panel

/**
 * v41 · Hoja COMPLETA: cubre la línea de tiempo y la barra (catálogo de efectos, ajustes de
 * un efecto, ajustes del proyecto, audio…), como en un editor de vídeo. ✓ la cierra.
 */
@Composable
fun FullSheet(vm: AppViewModel, modifier: Modifier) {
    val editing = vm.editingEffect
    Box(modifier.background(StudioPanel)) {
        when {
            vm.blendPanelOpen && vm.selectedLayer != null -> BlendSheet(vm)
            vm.layerMotionOpen && vm.selectedLayer != null -> LayerMotionSheet(vm)
            vm.soundEditing != null -> SoundClipPanel(vm, vm.soundEditing!!)
            vm.replacingClip != null && vm.replaceInAnim -> AnimCatalogPanel(vm)
            vm.replacingClip != null -> EffectCatalogPanel(vm)
            vm.animCatalogOpen && !vm.adjustOpen -> AnimCatalogPanel(vm)
            editing != null && vm.editFromCatalog && !vm.adjustOpen -> EffectCatalogPanel(vm)
            editing != null -> EffectEditorPanel(vm, editing)
            vm.tab == EditorTab.TOOLS -> LoopPanel(vm) { vm.closeOptions() }
            vm.tab == EditorTab.EFFECTS -> EffectCatalogPanel(vm)
            else -> SettingsPanel(vm)
        }
    }
}

/** v41 · Hoja COMPACTA sobre la barra: solo lo que necesita la herramienta (pincel, rutas). */
@Composable
fun CompactSheet(vm: AppViewModel, modifier: Modifier) {
    Box(modifier.background(StudioPanel)) {
        when {
            vm.paintingZone && vm.editingEffect != null -> SimpleBrushPanel(vm)
            vm.tab == EditorTab.MASKS -> ProtectPanel(vm)
            else -> AnimatePanel(vm)
        }
    }
}

/** Cabecera de una hoja: título y ✓ (listo). */
@Composable
fun CapHeader(title: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = StudioText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onDone),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, "Listo", tint = StudioText, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun PanelScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun PanelTitle(title: String, hint: String? = null) {
    Text(title, color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    if (hint != null) Text(hint, color = StudioMuted, fontSize = 12.sp, lineHeight = 15.sp)
}

/** Animar: solo controles útiles de la herramienta elegida; nada de textos fijos. */
@Composable
private fun AnimatePanel(vm: AppViewModel) {
    if (vm.animPanel == "loop") { LoopPanel(vm) { vm.animPanel = "" }; return }
    if (vm.paintingZone) { SimpleBrushPanel(vm); return }
    PanelScroll {
        when (vm.tool) {
            Tool.ARROW -> if (vm.arrows.isNotEmpty()) {
                LabeledSlider("Movimiento", vm.settings["flow.amount"] ?: .35f, .05f, 1f, false) { vm.setSetting("flow.amount", it) }
                LabeledSlider("Velocidad", vm.settings["flow.cycles"] ?: 2f, 1f, 8f, true) { vm.setSetting("flow.cycles", it) }
                SmallChoice("Borrar rutas y anclas", false) { vm.clearGuides() }
            } else EmptyHint("Dibuja una ruta sobre la imagen")
            Tool.ANCHOR -> if (vm.anchors.isNotEmpty()) SmallChoice("Borrar rutas y anclas", false) { vm.clearGuides() }
                else EmptyHint("Toca la imagen para fijar puntos")
            else -> {
                // Animaciones activas: acceso directo para ajustarlas (sin explicaciones).
                val active = animEffects.filter { vm.isEffectOn(it.first) }
                if (active.isEmpty()) EmptyHint("Abre «Catálogo» para ver las animaciones")
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.forEach { (id, label, _) -> SmallChoice(label, false, dot = StudioPink) { vm.editEffect(id) } }
                }
            }
        }
    }
}

/** Una sola línea discreta cuando no hay nada que ajustar. */
@Composable
fun EmptyHint(text: String) {
    Text(text, color = StudioMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp))
}

/**
 * Proteger: se pinta directamente (un dedo) lo que NO se mueve; dos dedos hacen zoom.
 * Capa, tamaño, borde suave y transparencia de la máscara, sin párrafos.
 */
@Composable
private fun ProtectPanel(vm: AppViewModel) {
    PanelScroll {
        if (vm.paintingZone) {
            val layer = vm.selectedMaskLayer
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.maskNames.forEachIndexed { i, name ->
                    val label = when (i) { 0 -> "Proteger"; 1 -> "Zona azul"; 2 -> "Zona verde"; else -> name }
                    SmallChoice(label, layer == i, dot = when (i) { 0 -> StudioPink; 1 -> Sky; 2 -> Color(0xFF6BD68A); else -> StudioPurple }) {
                        vm.selectMask(i)
                    }
                }
                SmallChoice("+ Nueva", false) { vm.addMask() }
            }
            LabeledSlider("Tamaño", vm.brushDp, 6f, 140f, true) { vm.brushDp = it }
            LabeledSlider("Borde suave", vm.feather, 0f, 1f, false) { vm.feather = it }
            LabeledSlider("Transparencia", vm.maskOpacity, .1f, .9f, false) { vm.changeMaskOpacity(it, persist = false) }
            // Se guarda al soltar: un solo acceso a disco por gesto.
            DisposableEffect(Unit) { onDispose { vm.changeMaskOpacity(vm.maskOpacity, persist = true) } }
        } else {
            EmptyHint(when (vm.tool) {
                Tool.ANCHOR -> "Toca la imagen para fijar un punto"
                Tool.ERASE_GUIDE -> "Toca un punto para quitarlo"
                else -> "Elige Pincel para pintar lo que no se mueve"
            })
        }
    }
}

@Composable
fun SmallChoice(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    Row(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) StudioPink.copy(alpha = .9f) else StudioTile)
        .clickable(onClick = onClick).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = StudioText, fontSize = 12.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun SettingsPanel(vm: AppViewModel) {
    if (vm.settingsSection == 5) { PreviewSizePanel(vm); return }
    if (vm.settingsSection == 6) { DiagnosticsPanel(vm); return }
    if (vm.settingsSection == 7) { WindPanel(vm); return }
    if (vm.settingsSection == 8) { LicensesPanel(vm); return }
    if (vm.settingsSection == 9) { SuperResPanel(vm); return }
    Column(Modifier.fillMaxSize()) {
        if (vm.settingsSection in 0..3 && vm.settingsSection != 2)
            CapHeader(when (vm.settingsSection) { 2 -> "Ajustar"; 3 -> "Audio"
                else -> settingsTools.firstOrNull { it.first == vm.settingsSection }?.second ?: "Ajustes" }) { vm.applyAndClose() }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (vm.settingsSection) {
                0 -> DurationPanel(vm)
                1 -> CanvasPanel(vm)
                2 -> ColorPanel(vm)
                3 -> AudioPanel(vm)
                else -> LoopPanel(vm) { vm.applyAndClose() }
            }
        }
    }
}

@Composable
private fun DurationPanel(vm: AppViewModel) {
    val e = vm.exportSettings
    PanelScroll {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(4, 6, 8, 10, 15, 30).forEach { sec ->
                SmallChoice("${sec}s", e.loopSec == sec) { vm.updateExport(e.copy(loopSec = sec)); vm.saveProject() }
            }
        }
        LabeledSlider("Segundos", e.loopSec.toFloat(), 2f, 60f, true, suffix = " s") {
            vm.updateExport(vm.exportSettings.copy(loopSec = it.toInt()))
        }
        Text("Fotogramas por segundo", color = StudioMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(24, 30, 60).forEach { fps ->
                SmallChoice("$fps fps", e.fps == fps) { vm.updateExport(vm.exportSettings.copy(fps = fps)) }
            }
        }
        // v39: tiempos reales medidos en ESTE teléfono al abrir la imagen.
        fun sec(ms: Long) = if (ms < 0) "—" else String.format(java.util.Locale.US, "%.1f s", ms / 1000f)
        Text("Última apertura · imagen ${sec(vm.perfImageMs)} · efectos ${sec(vm.perfEffectsMs)} · 1.er zoom ${sec(vm.perfZoomMs)}",
            color = StudioMuted, fontSize = 10.sp, maxLines = 2)
    }
}

/** Ayuda de la categoría (botón «?»): todas las herramientas en una lista corta. */
@Composable
fun EditorHelpDialog(tab: EditorTab, onDismiss: () -> Unit) {
    val items = when (tab) {
        EditorTab.TOOLS -> listOf("Ruta", "Ancla", "Eliminar", "Congelar", "Natural", "2.5D", "Ondular", "Zona", "Cámara", "Bucle")
        EditorTab.MASKS -> listOf("Pincel", "Borrador", "Rellenar", "Vaciar", "Ver", "Opciones", "Punto fijo", "Quitar punto")
        else -> emptyList()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StudioPanel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ayuda", color = StudioText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            items.forEach { name ->
                Row {
                    Text(name, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
                    Text(toolHelp[name] ?: "", color = StudioMuted, fontSize = 13.sp)
                }
            }
            Text("Dos dedos: zoom y mover, con cualquier herramienta. Mantén pulsada una herramienta para ver su ayuda. " +
                "Toca un efecto en la línea de tiempo para moverlo, recortarlo, duplicarlo o colocarlo en la imagen.",
                color = StudioMuted, fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(11.dp)).background(StudioTile)
                .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("Entendido", color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
