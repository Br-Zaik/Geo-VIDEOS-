package com.zeta.fondo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeta.fondo.AppViewModel
import com.zeta.fondo.model.*

/** Pincel de la zona de un efecto (o Congelar en Animar). Dos dedos siempre hacen zoom. */
@Composable
fun SimpleBrushPanel(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.finishEdit(false) }) { Icon(Icons.Default.Close, "Descartar", tint = StudioText) }
            Text(when (vm.editingEffect) {
                null -> "Congelar: pinta lo que NO se mueve"
                "zonemotion" -> "Pinta lo que se mueve"
                else -> "Zona de ${Effects.all.firstOrNull { it.id == vm.editingEffect }?.let(::effectShortTitle) ?: "efecto"}"
            }, color = StudioText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (vm.editingEffect == null) vm.finishEdit(true) else vm.finishPainting() }) {
                Icon(Icons.Default.Check, "Listo", tint = StudioPink)
            }
        }
        if (vm.editingEffect == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallChoice("Pincel", !vm.eraser) { vm.eraser = false }
            SmallChoice("Borrador", vm.eraser) { vm.eraser = true }
            SmallChoice("Rellenar", false) { vm.fillLayer(false) }
            SmallChoice("Vaciar", false) { vm.fillLayer(true) }
        }
        LabeledSlider("Tamaño", vm.brushDp, 6f, 140f, true) { vm.brushDp = it }
        LabeledSlider("Borde suave", vm.feather, 0f, 1f, false) { vm.feather = it }
        LabeledSlider("Transparencia", vm.maskOpacity, .1f, .9f, false) { vm.changeMaskOpacity(it, persist = false) }
        DisposableEffect(Unit) { onDispose { vm.changeMaskOpacity(vm.maskOpacity, persist = true) } }
    }
}

private val elementNames = listOf("Nube", "Humo", "Pétalos", "Brillos", "Energía", "Niebla")
private val elementIcons = listOf(Icons.Default.Cloud, Icons.Default.BlurOn, Icons.Default.LocalFlorist,
    Icons.Default.AutoAwesome, Icons.Default.Bolt, Icons.Default.CloudQueue)

internal fun catalogTitle(id: String): String = when (id) {
    "gust" -> "Viento"
    "shore" -> "Olas"
    "leaves" -> "Hojas"
    "rain" -> "Lluvia"
    "snow" -> "Nieve"
    "elements" -> "Elementos"
    "clouds3" -> "Nubes"
    "blast" -> "Explosión"
    "slash" -> "Corte"
    "reveal" -> "Aparecer"
    "bolt" -> "Relámpago"
    "water" -> "Agua"
    "sky" -> "Cielo"
    else -> Effects.all.firstOrNull { it.id == id }?.let(::effectShortTitle) ?: id
}

/** Efectos cuyo origen se elige tocando o arrastrando sobre la imagen. */
private val originEffects = setOf("tornado", "blast", "blood", "slash", "smoke", "energy", "rays",
    "focus", "speed", "electric", "shock", "runes", "comet", "fog", "birds", "lensflare", "citylights", "symbols", "steam", "sunlight", "radialblur", "focusblur")

/** Capas que se dibujan sobre la imagen y pueden pasar por detrás del personaje. */
internal val behindCapable = setOf("smoke", "fog", "rain", "snow", "sakura", "leaves", "embers", "ash", "dust",
    "rays", "energy", "clouds3", "bokeh", "sparkle", "lightleak", "blast", "flame", "electric", "speed",
    "focus", "shock", "comet", "bubbles", "blackaura", "stars", "flies", "sky", "runes", "shards",
    "sand", "cloudshadow", "birds", "aurora", "citylights", "lensflare", "inkweather", "steam", "dapple")

private val camManualKeys = setOf("mzoom", "mx", "my", "mrot")

/** v44: controles visibles por efecto en modo Fácil. */
private const val EASY_PARAMS = 4

private val directionEffects = setOf("rain", "snow", "sakura", "fog", "rays", "energy", "speed", "leaves", "embers",
    "dust", "bubbles", "comet", "smoke", "gust", "shore", "blast", "ash", "electric", "sand", "cloudshadow", "birds")

// ------------------------------------------------------------------ Efectos · catálogo

/** Pestañas de texto con un subrayado bajo la elegida (sin iconos), como en un editor de vídeo. */
@Composable
fun TextTabs(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    // v44.16: al cambiar de categoría deslizando, su nombre se desplaza a la vista.
    val scroll = rememberScrollState()
    val xs = remember { HashMap<Int, Int>() }
    LaunchedEffect(selected) { withFrameNanos { }; xs[selected]?.let { scroll.animateScrollTo((it - 60).coerceAtLeast(0)) } }
    Row(modifier.horizontalScroll(scroll).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Column(Modifier.fillMaxHeight().onGloballyPositioned { xs[i] = it.positionInParent().x.toInt() }
                .clickable { onSelect(i) }.padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(label, color = if (on) StudioText else StudioMuted, fontSize = 14.sp, maxLines = 1,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(22.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (on) StudioPink else Color.Transparent))
            }
        }
    }
}

/**
 * v41 · Catálogo de efectos (hoja completa): categorías en texto, «Ninguno», miniaturas
 * animadas con su nombre debajo y ✓ para aplicar. Tocar una miniatura la prueba sobre tu
 * imagen; tocarla otra vez (o «Ajustar» sobre ella) abre sus ajustes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EffectCatalogPanel(vm: AppViewModel) {
    val category = EffectCatalog.normalize(vm.effectsCategory)
    val thumbTick = rememberThumbTick()
    val animatedTime = remember { object { val value: Float get() = (thumbTick() % 16) / 16f } }
    val replacing = vm.replacingClip
    val active = vm.activeEffectsCount
    val cats = EffectCatalog.categories.filter { it != "Activos" || active > 0 }
    // v44.16: deslizar a la izquierda o a la derecha cambia de categoría (como en CapCut),
    // sin tener que tocar su nombre. Las pestañas siguen funcionando igual.
    val pager = rememberPagerState(initialPage = cats.indexOf(category).coerceAtLeast(0)) { cats.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (replacing != null) Text("Reemplazar", color = StudioPink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp))
            // v44.17: al tocar un nombre, la página cambia en el MISMO cuadro (sin un cuadro viejo de por medio).
            TextTabs(cats.map { if (it == "Activos") "Activos $active" else it }, cats.indexOf(category).coerceAtLeast(0),
                Modifier.weight(1f).fillMaxHeight()) { i -> vm.effectsCategory = cats[i]; scope.launch { pager.scrollToPage(i) } }
            Box(Modifier.size(48.dp).clickable { vm.applyAndClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Aplicar", tint = StudioText, modifier = Modifier.size(26.dp))
            }
        }
        LaunchedEffect(pager.settledPage, cats) {
            val c = cats.getOrNull(pager.settledPage) ?: return@LaunchedEffect
            if (c != EffectCatalog.normalize(vm.effectsCategory)) vm.effectsCategory = c
        }
        LaunchedEffect(category, cats) {
            val i = cats.indexOf(category)
            if (i >= 0 && i != pager.currentPage && !pager.isScrollInProgress) pager.scrollToPage(i)
        }
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), key = { cats.getOrElse(it) { "$it" } },
            verticalAlignment = Alignment.Top) { page ->
            val cat = cats.getOrElse(page) { category }
            Column(Modifier.fillMaxSize()) {
                if (cat == "Escenas") ScenesPage(vm, thumbTick) { animatedTime.value }
                else EffectGrid(vm, cat, replacing, thumbTick) { animatedTime.value }
            }
        }
    }
}

@Composable
private fun ColumnScope.ScenesPage(vm: AppViewModel, thumbTick: () -> Int, glyphTime: () -> Float) {
            val scenes = remember { Presets.scenes }
            // v47: sugerencias por reglas (color, cielo, agua, caras); no hay IA pesada.
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.computeSceneSuggestions() }
            if (vm.sceneSuggestions.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Para esta imagen:", color = StudioMuted, fontSize = 11.sp)
                vm.sceneSuggestions.forEach { n ->
                    val scene = scenes.firstOrNull { it.name == n } ?: return@forEach
                    SmallChoice(n, false) { vm.applyPreset(scene); vm.previewFromCatalog(Presets.sceneMainEffect(scene)) }
                }
            }
            // v44.15: mismas tarjetas que el resto del catálogo (antes salían más grandes).
            LazyVerticalGrid(GridCells.Adaptive(78.dp), Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(scenes, key = { it.name }) { scene ->
                    val mainEffect = Presets.sceneMainEffect(scene)
                    CatalogCard(scene.name, false, false, Modifier.clickable {
                        vm.applyPreset(scene); vm.previewFromCatalog(mainEffect)
                    }) {
                        EffectThumb(mainEffect, animate = true, tick = thumbTick, glyphTime = glyphTime,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
}

@Composable
private fun ColumnScope.EffectGrid(vm: AppViewModel, category: String, replacing: Long?, thumbTick: () -> Int,
                                   glyphTime: () -> Float) {
    run {
        // La lista solo se recalcula si cambian la categoría o los efectos ENCENDIDOS.
        val onKey by remember { derivedStateOf { EffectSwitches.active(vm.settings).keys.sorted().joinToString(",") } }
        val shown = remember(category, onKey) {
            EffectCatalog.idsFor(category, Effects.all.map { it.id }) { vm.isEffectOn(it) }
        }
        val previewing = if (vm.editFromCatalog) vm.editingEffect else null
        // v44.22 · se preparan en segundo plano los efectos de esta categoría (tocar = al instante)
        LaunchedEffect(shown) { vm.prewarmEffects(shown) }
        LazyVerticalGrid(GridCells.Adaptive(78.dp), Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (replacing == null) item(key = "_none") {
                CatalogCard("Ninguno", previewing == null && vm.editingEffect == null, false,
                    Modifier.clickable { vm.cancelCatalogPreview() }) {
                    Box(Modifier.fillMaxSize().background(StudioTile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Block, null, tint = StudioText, modifier = Modifier.size(30.dp))
                    }
                }
            }
            if (category == "Activos" && shown.isNotEmpty() && replacing == null) item(key = "_clear") {
                CatalogCard("Quitar todos", false, false, Modifier.clickable { vm.removeAllEffects() }) {
                    Box(Modifier.fillMaxSize().background(StudioTile), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteOutline, null, tint = StudioText, modifier = Modifier.size(28.dp))
                    }
                }
            }
            items(shown, key = { it }) { eid ->
                val isOn = if (eid == "elements") (0..3).any { (vm.settings["element$it.on"] ?: 0f) > .5f }
                    else vm.isEffectOn(eid)
                val isPreview = previewing == eid
                CatalogCard(catalogTitle(eid), isPreview, isOn && !isPreview, tint = clipColor(eid), modifier = Modifier.clickable {
                    if (replacing != null) vm.replaceClipEffect(replacing, eid) else vm.previewFromCatalog(eid)
                }) {
                    EffectThumb(eid, animate = true, tick = thumbTick, glyphTime = glyphTime,
                        modifier = Modifier.fillMaxSize())
                    if (isPreview) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Tune, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text("Ajustar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Miniatura cuadrada redondeada y su nombre debajo; borde blanco si está elegida. */
@Composable
internal fun CatalogCard(title: String, selected: Boolean, active: Boolean, modifier: Modifier,
                        tint: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)) {
            content()
            // v42: franja con el color de su familia (fuego rojo, agua azul…), igual que en la línea de tiempo.
            if (tint != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(tint))
            if (active) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).clip(CircleShape).background(StudioPink))
        }
        Text(title, color = if (selected) StudioText else StudioMuted, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
    }
}

// ------------------------------------------------------------------ Efectos · Ajustar

private fun sectionsOf(id: String): List<String> =
    EffectCatalog.sections(id, id in Effects.colorable, Effects.shapeLabels.containsKey(id))

/**
 * v41 · Ajustar un efecto (hoja completa): «Restablecer», el nombre y ✓. Debajo, las
 * secciones en texto (Ajustes, Estilo, Color, Tiempo, Zona) y sus controles.
 * Desde el catálogo «‹» vuelve a las miniaturas sin perder la prueba; desde la línea de
 * tiempo «✕» descarta los cambios.
 */
@Composable
fun EffectEditorPanel(vm: AppViewModel, id: String) {
    val def = Effects.all.firstOrNull { it.id == id }
    val sections = sectionsOf(id)
    val section = vm.effectSection.takeIf { it in sections } ?: "Ajustes"
    val fromCatalog = vm.editFromCatalog
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clickable { if (fromCatalog) vm.adjustOpen = false else vm.finishEdit(false) },
                contentAlignment = Alignment.Center) {
                Icon(if (fromCatalog) Icons.Default.ChevronLeft else Icons.Default.Close,
                    if (fromCatalog) "Volver al catálogo" else "Descartar", tint = StudioText, modifier = Modifier.size(24.dp))
            }
            if (id != "elements") Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.resetEffectParams(id) }
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, null, tint = StudioMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restablecer", color = StudioMuted, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(catalogTitle(id), color = StudioText, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis)
                val target = vm.fxTarget(id)
                if (target != null) {
                    val all = vm.clipsForEffect(id)
                    Text("Solo este bloque · ${all.indexOfFirst { it.uid == target.uid } + 1} de ${all.size}",
                        color = StudioPink, fontSize = 10.sp, maxLines = 1)
                }
            }
            Box(Modifier.size(46.dp).clickable { vm.applyAndClose() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, "Aplicar", tint = StudioText, modifier = Modifier.size(26.dp))
            }
        }
        if (sections.size > 1) TextTabs(sections, sections.indexOf(section), Modifier.fillMaxWidth().height(42.dp)) {
            vm.effectSection = sections[it]
        }
        // v44.12: claves visibles aunque el panel tape la línea de tiempo.
        if (section == "Ajustes" && def != null && vm.keyClip(id) != null) KeyframeStrip(vm, id)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            vm.zoneProblem(id)?.let { problem ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioOrange.copy(alpha = .16f))
                    .clickable { if (id == "energy") vm.protect() else if (id == "parallax") vm.paintBuiltinMask(2) else vm.paintEffectZone() }
                    .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brush, null, tint = StudioOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(problem, color = StudioText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
            when {
                id == "elements" -> ElementsControls(vm)
                def == null -> Unit
                section == "Ajustes" -> EffectBasics(vm, def)
                section == "Estilo" -> EffectStyle(vm, id)
                section == "Color" -> EffectColor(vm, id)
                section == "Tiempo" -> EffectTimingControls(vm, id)
                section == "Zona" -> EffectZone(vm, id)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Ajustes principales: intensidad, velocidad, tamaño, dirección… (los finos en Avanzado). */
@Composable
private fun EffectBasics(vm: AppViewModel, def: EffectDef) {
    val id = def.id
    if (id == "appear") CharacterControls(vm)
    // v45: profundidad IA + pinceles Acercar/Alejar (o zonas manuales sin modelo).
    if (id == "parallax") DepthControls(vm, def)
    if (id == "natural") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallChoice("Pintar lo que se mueve", vm.maskHasPaint((vm.settings["natural.mask"] ?: 2f).toInt().coerceAtLeast(0))) { vm.paintEffectZone() }
            SmallChoice(if (vm.playing) "Pausar" else "Ver movimiento", vm.playing) {
                if (!vm.isEffectOn("natural")) vm.toggleEffect("natural")
                vm.togglePlay()
            }
        }
        Text("Borde fijo (raíz)", color = StudioMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val anchorIcons = listOf(Icons.Default.Block, Icons.Default.ArrowBack, Icons.Default.ArrowForward,
                Icons.Default.ArrowDownward, Icons.Default.ArrowUpward)
            val anchorLabels = listOf("Sin borde fijo", "Borde izquierdo fijo", "Borde derecho fijo",
                "Borde inferior fijo", "Borde superior fijo")
            anchorIcons.forEachIndexed { index, icon ->
                StudioIconAction(anchorLabels[index], icon, Modifier.weight(1f).height(40.dp),
                    selected = (vm.settings["natural.anchor"] ?: 3f).toInt() == index) {
                    vm.setSetting("natural.anchor", index.toFloat())
                }
            }
        }
    }
    if (id == "bolt" && (vm.exportSettings.ambient["thunder"] ?: 0f) <= 0.01f) {
        SmallChoice("+ Sonido de trueno", false) { vm.toggleAmbient("thunder") }
    }
    val hiddenTimelineParams = when (id) {
        "gust" -> setOf("start", "dur")
        "appear" -> setOf("start", "dur")
        "impact" -> setOf("start", "cycles")
        "bolt" -> setOf("cycles")
        else -> emptySet()
    }
    val params = def.params.filter { p ->
        p.key !in listOf("zone", "variant", "mode", "anchor", "shape") && !(id == "shore" && p.key == "tint") &&
            p.key !in hiddenTimelineParams &&
            !(id == "parallax" && p.key in depthParamKeys) &&
            (vm.advancedMode || !p.advanced) &&
            !(id == "zonemotion" && (vm.settings["zonemotion.variant"] ?: 0f) > 1.5f && p.key == "direction") &&
            !(id == "cam" && (vm.settings["cam.mode"] ?: 0f).toInt() !in setOf(2, 5) && p.key == "direction") &&
            // v44: en «Libre» solo zoom, posición y giro (se animan con ◆); en el resto, al revés.
            !(id == "cam" && (((vm.settings["cam.mode"] ?: 0f) > 8.5f) != (p.key in camManualKeys)))
    }
    // Orden fijo y predecible: intensidad → velocidad → tamaño → el resto.
    val order = listOf("int", "amp", "amount", "opacity", "cycles", "speed", "size", "scale", "dens", "density")
    // v44: modo Fácil = solo los 4 controles más útiles (intensidad, velocidad, tamaño…);
    // Avanzado = todos. Cambiar de modo no borra ningún ajuste.
    val sorted = params.sortedBy { p -> order.indexOf(p.key).let { if (it < 0) 99 else it } }
    val shown = if (vm.advancedMode) sorted else sorted.filter { it.key != "direction" }.take(EASY_PARAMS) +
        sorted.filter { it.key == "direction" }
    shown.forEach { p -> EffectParameter(vm, def, p) }
    val hiddenCount = sorted.size - shown.size + if (vm.advancedMode) 0 else def.params.count { it.advanced && it.key !in hiddenTimelineParams }
    if (!vm.advancedMode && hiddenCount > 0) Text("$hiddenCount ajustes más en Avanzado", color = StudioMuted, fontSize = 11.sp)
    if (id in directionEffects && def.params.none { it.key == "direction" || it.key == "dir" }) {
        // v44: joystick (dirección + fuerza) y «Seguir el viento global».
        if (id in com.zeta.fondo.model.Wind.directional) EffectWindControls(vm, id)
        else {
            val defAngle = when (id) { "gust", "blast", "fog" -> 0f; "smoke", "ash", "embers", "bubbles" -> 270f; else -> 90f }
            DirectionDial("Dirección", vm.fx["$id.direction"] ?: defAngle) { vm.setSetting("$id.direction", it) }
        }
    }
    if (def.params.any { it.advanced } || params.size > EASY_PARAMS) SwitchRow(if (vm.advancedMode) "Controles avanzados" else "Mostrar controles avanzados",
        vm.advancedMode) { if (it != vm.advancedMode) vm.toggleAdvancedMode() }

}

/** Estilo: variantes (Suave, Tormenta, Otoño…) y formas del efecto. */
@Composable
private fun EffectStyle(vm: AppViewModel, id: String) {
    val variants = EffectCatalog.variants(id)
    if (variants.isNotEmpty()) {
        Text("Estilo", color = StudioMuted, fontSize = 11.sp)
        FlowChoices(variants, (vm.fx[if (id == "cam") "cam.mode" else "$id.variant"] ?: 0f).toInt()) { i ->
            val key = if (id == "cam") "cam.mode" else "$id.variant"
            if (id in EffectCatalog.styledPresets) vm.applyEffectStyle(id, i)
            else if (id == "natural") vm.applyNaturalPreset(i)
            else vm.setSetting(key, i.toFloat())
        }
    }
    Effects.shapeLabels[id]?.let { shapes ->
        Text(Effects.all.first { it.id == id }.params.firstOrNull { it.key == "shape" }?.label ?: "Forma",
            color = StudioMuted, fontSize = 11.sp)
        FlowChoices(shapes, (vm.fx["$id.shape"] ?: 0f).toInt()) { i -> vm.setSetting("$id.shape", i.toFloat()) }
    }
}

/** Opciones en varias filas (sin desplazamiento lateral escondido). */
@Composable
private fun FlowChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    labels.chunked(3).forEachIndexed { row, chunk ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chunk.forEachIndexed { k, label ->
                val i = row * 3 + k
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) StudioPink else StudioTile)
                    .clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, color = StudioText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun EffectColor(vm: AppViewModel, id: String) {
    if (id in Effects.colorable) EffectColorRow(vm, id)
    if (com.zeta.fondo.model.EffectGrade.has(id)) EffectGradeControls(vm, id)
    if (id == "shore") {
        Text("Color de la espuma", color = StudioMuted, fontSize = 11.sp)
        FlowChoices(listOf("Natural", "Turquesa", "Azul", "Atardecer", "Noche"), (vm.fx["shore.tint"] ?: 0f).toInt()) {
            vm.setSetting("shore.tint", it.toFloat())
        }
    }
}

/** Zona: dónde actúa (toda la imagen, zona pintada), origen y si pasa detrás del personaje. */
@Composable
private fun EffectZone(vm: AppViewModel, id: String) {
    val slot = (vm.fx["$id.mask"] ?: -2f).toInt()
    // v44.23: la zona puede ser un círculo o un rectángulo que se arrastra sobre la imagen.
    val shapeOn = vm.zoneShapeActive(id)
    val shape = vm.zoneShapeOf(id)
    if (id != "natural") {
        Text("Dónde se ve", color = StudioMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SmallChoice("Toda la imagen", slot == -2) { vm.wholeEffectZone() } }
            Box(Modifier.weight(1f)) { SmallChoice("Pintar zona", slot >= 0 && !shapeOn) { vm.paintEffectZone() } }
        }
    } else {
        SmallChoice("Pintar lo que se mueve", !shapeOn) { vm.paintEffectZone() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { SmallChoice("◯ Círculo", shapeOn && shape?.type == com.zeta.fondo.model.ZoneShapes.CIRCLE) {
            vm.setZoneShape(id, com.zeta.fondo.model.ZoneShapes.CIRCLE) } }
        Box(Modifier.weight(1f)) { SmallChoice("▭ Rectángulo", shapeOn && shape?.type == com.zeta.fondo.model.ZoneShapes.RECT) {
            vm.setZoneShape(id, com.zeta.fondo.model.ZoneShapes.RECT) } }
    }
    if (shapeOn && shape != null) {
        Text("Arrastra la forma en la imagen: esquinas = tamaño, lados = estirar, punto rosa = girar.",
            color = StudioMuted, fontSize = 11.sp)
        LabeledSlider("Borde suave", shape.soft, 0f, 1f, false) { vm.transformZone(id, mapOf("$id.zsoft" to it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SmallChoice("Retocar con pincel", false) { vm.paintEffectZone() } }
            Box(Modifier.weight(1f)) { SmallChoice("Quitar forma", false) { vm.removeZoneShape(id) } }
        }
    }
    // v47: ojos, respiración, cielo, agua y tocar para seleccionar.
    AiZoneTools(vm, id)
    if (id in originEffects) {
        Text("Origen", color = StudioMuted, fontSize = 11.sp)
        SmallChoice(if (vm.tool == Tool.ORIGIN) "Toca o arrastra sobre la imagen" else "Elegir origen en la imagen",
            vm.tool == Tool.ORIGIN) { vm.pickOrigin(id) }
    }
    if (id in behindCapable) SwitchRow("Detrás del personaje", vm.isBehind(id)) { vm.setBehind(id, it) }
    if (vm.maskNames.size > 1 && id != "natural") {
        Text("Usar una zona ya pintada", color = StudioMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            vm.maskNames.drop(1).forEachIndexed { offset, name ->
                val index = offset + 1
                SmallChoice(when (index) { 1 -> "Zona azul"; 2 -> "Zona verde"; else -> name }, slot == index) { vm.setEffectMask(id, index) }
            }
        }
    }

}

/** Tiempo: Una vez / Repetir / Continuo para eventos; bloque y duración para el resto. */
@Composable
private fun EffectTimingControls(vm: AppViewModel, id: String) {
    if (!vm.isEffectOn(id)) {
        EmptyHint("Actívalo (👁) para ajustar su tiempo")
        return
    }
    val total = vm.exportSettings.loopSec.toFloat().coerceAtLeast(2f)
    val clips = vm.clipsForEffect(id)

    if (id in Effects.eventEffects) {
        val mode = vm.eventMode(id)
        Text("Cuándo ocurre", color = StudioMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Una vez" to EventMode.ONCE, "Repetir" to EventMode.REPEAT, "Continuo" to EventMode.LOOP).forEach { (label, m) ->
                Box(Modifier.weight(1f)) { SmallChoice(label, mode == m) { vm.setEventMode(id, m) } }
            }
        }
        if (mode == EventMode.REPEAT) LabeledSlider("Cada", vm.settings["$id.every"] ?: 2f, .4f,
            total.coerceAtLeast(1f), false, suffix = " s") { vm.setSetting("$id.every", it) }
    } else {
        val whole = clips.any { it.startSec <= .01f && it.durationSec >= total - .02f }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f)) { SmallChoice("Todo el vídeo", whole) { vm.addWholeVideoEffectClip(id) } }
            Box(Modifier.weight(1f)) { SmallChoice("+ Otro bloque", false) { vm.addEffectClip(id) } }
        }
    }

    if (clips.isEmpty()) {
        Text("Sin bloques", color = StudioMuted, fontSize = 11.sp)
        return
    }
    val selected = clips.firstOrNull { it.uid == vm.selectedTimelineClip } ?: clips.first()
    if (clips.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        clips.forEachIndexed { index, clip ->
            val start = String.format(java.util.Locale.US, "%.1f", clip.startSec)
            SmallChoice("${index + 1} · ${start}s", clip.uid == selected.uid) { vm.selectTimelineClip(clip.uid) }
        }
    }
    val maxStart = (total - .05f).coerceAtLeast(.05f)
    LabeledSlider("Inicio", selected.startSec, 0f, maxStart, false, suffix = " s") { newStart ->
        val newDur = selected.durationSec.coerceAtMost((total - newStart).coerceAtLeast(.05f))
        vm.setEffectClipTiming(selected.uid, newStart, newDur)
    }
    val maxDur = (total - selected.startSec).coerceAtLeast(.05f)
    LabeledSlider("Duración", selected.durationSec.coerceAtMost(maxDur), .05f, maxDur, false, suffix = " s") { newDur ->
        vm.setEffectClipTiming(selected.uid, selected.startSec, newDur)
    }
    if (vm.advancedMode) LabeledSlider("Intensidad del bloque", selected.strength, 0f, 2f, false) { value ->
        vm.setEffectClipStrength(selected.uid, value)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { SmallChoice("Duplicar bloque", false) { vm.duplicateEffectClip(selected.uid) } }
        Box(Modifier.weight(1f)) { SmallChoice("Eliminar bloque", false) { vm.removeEffectClip(selected.uid) } }
    }
}

@Composable
private fun CharacterControls(vm: AppViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importCharacter(uri)
    }
    StudioSmallButton(if (vm.hasCharacter) "Cambiar personaje (PNG)" else "Elegir personaje (PNG)",
        Icons.Default.Person, Modifier.fillMaxWidth()) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    EmptyHint(if (vm.hasCharacter) "Arrastra, pellizca y gira sobre la imagen" else "PNG con fondo transparente")
}

@Composable
private fun ElementsControls(vm: AppViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        elementNames.forEachIndexed { i, name -> StudioButton(name, elementIcons[i], Modifier.width(72.dp).height(58.dp)) { vm.addElement(i) } }
    }
    val slots = (0..3).filter { (vm.settings["element$it.on"] ?: 0f) > .5f }
    if (slots.isEmpty()) EmptyHint("Elige un elemento")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        slots.forEach { i -> SmallChoice("${i + 1} · ${elementNames[(vm.settings["element$i.type"] ?: 0f).toInt().coerceIn(0, 5)]}", vm.selectedElement == i) { vm.selectElement(i) } }
    }
    val slot = vm.selectedElement
    if (slot < 0 || slot !in slots) return
    val id = "element$slot"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Arrastra · pellizca · gira con dos dedos", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.setSetting("$id.on", 0f); vm.selectElement(-1) }) { Icon(Icons.Default.DeleteOutline, "Quitar", tint = StudioText) }
    }
    LabeledSlider("Opacidad", vm.settings["$id.opacity"] ?: .7f, 0f, 1f, false) { vm.setSetting("$id.opacity", it) }
    LabeledSlider("Velocidad", vm.settings["$id.cycles"] ?: 1f, 1f, 6f, true) { vm.setSetting("$id.cycles", it) }
    LabeledSlider("Tamaño", vm.settings["$id.size"] ?: .3f, .04f, 1.5f, false) { vm.setSetting("$id.size", it) }
    LabeledSlider("Giro", vm.settings["$id.rotation"] ?: 0f, -180f, 180f, true) { vm.setSetting("$id.rotation", it) }
}

/**
 * v44.11 · Color propio del efecto: Original / Blanco y negro / Sepia, saturación, brillo y
 * contraste. Solo cambia el efecto, nunca la imagen. «Modo manga» pasa TODOS a blanco y negro.
 */
@Composable
private fun EffectGradeControls(vm: AppViewModel, id: String) {
    val G = com.zeta.fondo.model.EffectGrade
    val manga = (vm.settings["style.mono"] ?: 0f) > .5f
    Text("Color del efecto (la imagen no cambia)", color = StudioMuted, fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp))
    FlowChoices(G.modes, (vm.settings["$id.${G.MODE}"] ?: 0f).toInt()) { vm.setSetting("$id.${G.MODE}", it.toFloat()) }
    LabeledSlider("Saturación", vm.settings["$id.${G.SAT}"] ?: 0f, -1f, 1f, false) { vm.setSetting("$id.${G.SAT}", it) }
    LabeledSlider("Brillo", vm.settings["$id.${G.BRI}"] ?: 0f, -1f, 1f, false) { vm.setSetting("$id.${G.BRI}", it) }
    LabeledSlider("Contraste", vm.settings["$id.${G.CON}"] ?: 0f, -1f, 1f, false) { vm.setSetting("$id.${G.CON}", it) }
    if (!G.isDefault(vm.settings, id)) SmallChoice("Restablecer color", false) { vm.setSettingsBatch(G.reset(id)) }
    SwitchRow("Modo manga: todos los efectos en blanco y negro", manga) { vm.setSetting("style.mono", if (it) 1f else 0f) }
}
