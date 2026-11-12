package com.zeta.fondo

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeta.fondo.engine.BitmapUtil
import com.zeta.fondo.engine.ExportCancelled
import com.zeta.fondo.engine.ExportRequest
import com.zeta.fondo.engine.ExportPlanner
import com.zeta.fondo.engine.ExportResult
import com.zeta.fondo.engine.Exporter
import com.zeta.fondo.engine.FlowField
import com.zeta.fondo.engine.MaskLayers
import com.zeta.fondo.engine.RenderHub
import com.zeta.fondo.engine.RenderParams
import com.zeta.fondo.export.ExportRunner
import com.zeta.fondo.export.ExportService
import com.zeta.fondo.model.Arrow
import com.zeta.fondo.model.EditorTab
import com.zeta.fondo.model.PreviewSize
import com.zeta.fondo.model.ClipEdits
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EventMode
import com.zeta.fondo.model.EffectTimeline
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.ExportSettings
import com.zeta.fondo.model.MaskStroke
import com.zeta.fondo.model.PointN
import com.zeta.fondo.model.Preset
import com.zeta.fondo.model.ProjectMeta
import com.zeta.fondo.model.ProjectStore
import com.zeta.fondo.model.ProjectSummary
import com.zeta.fondo.model.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

enum class Screen { HOME, EDITOR }

sealed class ExportState {
    object Idle : ExportState()
    data class Running(val progress: Float) : ExportState()
    data class Done(val result: ExportResult) : ExportState()
    data class Failed(val message: String) : ExportState()
}

private sealed class UndoAction {
    class EffectSwitches(
        val before: Map<String, Float>,
        val after: Map<String, Float>,
        val beforeClips: List<EffectClip>? = null,
        val afterClips: List<EffectClip>? = null,
        /** v40.1: claves que NO existían antes / después (deshacer las quita, no las pone a 0). */
        val absentBefore: Set<String> = emptySet(),
        val absentAfter: Set<String> = emptySet()
    ) : UndoAction()
    class AddArrow(val arrow: Arrow) : UndoAction()
    class AddAnchor(val point: PointN) : UndoAction()
    class AddStroke(val stroke: MaskStroke) : UndoAction()
    /** v44.25 · trazos quitados por la app (p. ej. Proteger automático viejo); ↶ los devuelve. */
    class RemoveStrokes(val strokes: List<MaskStroke>) : UndoAction()
    class RemoveArrow(val index: Int, val arrow: Arrow) : UndoAction()
    class RemoveAnchor(val index: Int, val point: PointN) : UndoAction()
    class ClearGuides(val arrows: List<Arrow>, val anchors: List<PointN>) : UndoAction()
    /** v44.23 · cambio en las capas de imagen (antes / después). */
    class Layers(val before: List<com.zeta.fondo.model.ImageLayer>, val after: List<com.zeta.fondo.model.ImageLayer>) : UndoAction()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("zeta_prefs", Context.MODE_PRIVATE)
    private val store = ProjectStore(app)
    private val saveMutex = Mutex()
    private var autoSaveJob: Job? = null

    /** Guarda después de una pausa de edición; no escribe JSON por cada píxel del pincel. */
    private fun scheduleAutosave() {
        if (meta == null) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(900L)
            saveProject(forceCurrent = true)
        }
    }
    val hub = RenderHub()
    val vertSrc: String = app.assets.open("shaders/fondo.vert").bufferedReader().use { it.readText() }
    val fragSrc: String = app.assets.open("shaders/fondo.frag").bufferedReader().use { it.readText() }
    /** v38: shader ligero (imagen + máscara) para el primer cuadro, el zoom y el pincel. */
    val liteSrc: String? = try { app.assets.open("shaders/lite.frag").bufferedReader().use { it.readText() } } catch (_: Exception) { null }
    /** v39: el shader completo compilado se guarda aquí (se borra solo al actualizar la app). */
    val shaderCacheDir: java.io.File = java.io.File(app.codeCacheDir, "shaders")
    /** false mientras el shader completo de efectos se compila tras abrir el editor. */
    var effectsReady by mutableStateOf(false)
        private set

    // ---------------- Navegación y avisos
    var screen by mutableStateOf(Screen.HOME)
    var projects by mutableStateOf<List<ProjectSummary>>(emptyList())
    var busy by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var glError by mutableStateOf<String?>(null)
    /** Thumbnail visible de inmediato mientras OpenGL compila/sube texturas. */
    var previewFallback by mutableStateOf<Bitmap?>(null)
        private set
    var previewReady by mutableStateOf(false)
        private set
    /** Comparación temporal de la imagen de entrada, sin cambiar ningún efecto del proyecto. */
    var showOriginal by mutableStateOf(false)
        private set
    fun updateOriginalPreview(show: Boolean) {
        showOriginal = show
        hub.showOriginal = show
    }

    // ---------------- Proyecto abierto
    var meta by mutableStateOf<ProjectMeta?>(null)
        private set
    val arrows = mutableStateListOf<Arrow>()
    val anchors = mutableStateListOf<PointN>()
    var masks: MaskLayers? = null
        private set
    var maskNames by mutableStateOf<List<String>>(listOf("Congelar", "Zona azul", "Zona verde"))
        private set
    var selectedMaskLayer by mutableStateOf(0)
        private set
    var settings by mutableStateOf(Effects.defaults())
        private set
    var exportSettings by mutableStateOf(ExportSettings())
        private set
    var effectClips by mutableStateOf<List<EffectClip>>(emptyList())
        private set
    var selectedTimelineClip by mutableStateOf<Long?>(null)
        private set

    // ---------------- Herramientas
    var tool by mutableStateOf(Tool.PAN)
    var eraser by mutableStateOf(false)
    var brushDp by mutableFloatStateOf(36f)
    var feather by mutableFloatStateOf(0.35f)
    var showGuides by mutableStateOf(false)
    var showMasks by mutableStateOf(true)
        private set
    var playing by mutableStateOf(false)
        private set
    var scrubT by mutableFloatStateOf(0f)
        private set
    var tab by mutableStateOf(EditorTab.TOOLS)
        private set
    var settingsSection by mutableStateOf(0)
    var fullscreen by mutableStateOf(false)
    var activeMotionEffect by mutableStateOf<String?>(null)
    var effectsCategory by mutableStateOf("Populares")
    /** v38: sección del editor de un efecto (Ajustes, Estilo, Color, Tiempo, Zona). */
    var effectSection by mutableStateOf("Ajustes")
    /** v38: panel auxiliar de Animar ("loop" = Bucle). */
    var animPanel by mutableStateOf("")

    var editingEffect by mutableStateOf<String?>(null)
        private set
    var paintingZone by mutableStateOf(false)
        private set
    var selectedElement by mutableStateOf(-1)
        private set
    var brushCursor by mutableStateOf<PointN?>(null)
    private data class EditSnapshot(val settings: Map<String, Float>, val strokes: List<MaskStroke>,
        val names: List<String>, val arrows: List<Arrow>, val anchors: List<PointN>, val undo: List<UndoAction>,
        val redo: List<UndoAction>, val effectClips: List<EffectClip>)
    private var editSnapshot: EditSnapshot? = null
    private fun beginEdit() {
        if (editSnapshot != null) return
        endStroke()
        val mk = masks ?: return
        editSnapshot = EditSnapshot(settings.toMap(), synchronized(mk.lock) { mk.strokes.flatten().toList() },
            mk.names.toList(), arrows.toList(), anchors.toList(), undoStack.toList(), redoStack.toList(), effectClips.toList())
    }
    /** Herramientas de movimiento conservan su propio panel; nunca saltan a Efectos. */
    fun editEffect(id: String, autoPaint: Boolean = true) {
        finishEdit(true); beginEdit(); editingEffect = id
        effectSection = "Ajustes"; animPanel = ""
        requestOptions()
        tab = if (id in setOf("natural", "parallax", "mesh", "cam", "zonemotion"))
            EditorTab.TOOLS else EditorTab.EFFECTS
        val currentMask = settings["$id.mask"]?.toInt()
        val firstLocalEffect = id in zoneEffects && !settings.containsKey("$id.ownedMask") &&
            (!settings.containsKey("$id.mask") || (id == "shore" && currentMask == -2))
        if (id != "elements") {
            if (id !in Effects.globalSettings) ensureTimelineActivation(id)
            if (!settings.containsKey("$id.mask")) {
                val legacy = when {
                    id in setOf("sway", "heat", "flame", "cloth") -> 2
                    id in setOf("drift", "ripple", "glint", "eyes", "blink") -> 1
                    (settings["$id.zone"] ?: 0f) > .5f -> 1
                    else -> -2
                }
                val used = masks?.strokes?.getOrNull(legacy)?.isNotEmpty() == true
                settings = settings + ("$id.mask" to (if (used) legacy.toFloat() else -2f))
                pushParams()
            }
        }
        // v44: desde el catálogo de Animar se prueba primero (zona = toda la imagen); la zona se
        // pinta luego en Ajustar › Zona. Ya no se abre el pincel antes de ver qué hace.
        if (firstLocalEffect && autoPaint) paintEffectZone()
    }
    /**
     * v42 · Animación de cámara de un toque: Acercar, Alejar, Latido, Flotar, Balanceo,
     * Temblar… Abre la vista previa con ✓ / ✕ y valores pensados para cada movimiento;
     * en Ajustar se cambia la fuerza, la velocidad y el resto de estilos.
     */
    fun applyCameraPreset(mode: Int) {
        if (editingEffect != "cam") editEffect("cam")
        val (zoom, pan) = when (mode) {
            0, 1 -> .08f to 0f
            4 -> .05f to .6f
            6 -> .07f to 0f
            7 -> .05f to .6f
            8 -> .08f to 0f
            else -> .06f to .5f
        }
        setSetting("cam.mode", mode.toFloat())
        setSetting("cam.zoom", zoom)
        setSetting("cam.pan", pan)
        effectSection = "Ajustes"
        if (mode == 9) showHint("Cámara libre: pon el cabezal, ajusta zoom/posición/giro y toca ◆. Repite en otro momento.", 4200L)
    }

    fun finishEdit(commit: Boolean) {
        endStroke()
        val saved = editSnapshot
        if (!commit && saved != null) {
            settings = saved.settings
            arrows.clear(); arrows.addAll(saved.arrows)
            anchors.clear(); anchors.addAll(saved.anchors); recomputeFlow()
            masks?.setAll(saved.strokes, saved.names)
            maskNames = masks?.names?.toList() ?: saved.names
            undoStack.clear(); undoStack.addAll(saved.undo)
            redoStack.clear(); redoStack.addAll(saved.redo)
            effectClips = saved.effectClips
            selectedTimelineClip = null
            canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty(); pushParams()
            refreshEffectBoxes()
        }
        if (commit && saved != null && (saved.settings !== settings || saved.effectClips != effectClips)) {
            // v40.1: ✓ Aplicar se puede deshacer de una vez (todos los ajustes de la edición).
            // Los cambios de ajustes intermedios se sustituyen por esta única entrada; los
            // trazos del pincel y las guías conservan sus propias entradas.
            if (undoStack.size >= saved.undo.size) {
                val added = ArrayList(undoStack.subList(saved.undo.size, undoStack.size))
                while (undoStack.size > saved.undo.size) undoStack.removeAt(undoStack.size - 1)
                undoStack.addAll(added.filter { it !is UndoAction.EffectSwitches })
            }
            val entry = settingsUndo(saved.settings, saved.effectClips)
            if (entry.before.isNotEmpty() || entry.after.isNotEmpty() || saved.effectClips != effectClips) pushUndo(entry)
            else { canUndo = undoStack.isNotEmpty() }
        }
        // v40: al aplicar (✓) un efecto se vuelve a la línea de tiempo con su bloque
        // seleccionado; al descartar (✕) se sigue en el catálogo.
        if (commit && editingEffect != null && editingEffect != "elements") optionsOpen = false
        editSnapshot = null; editingEffect = null; paintingZone = false; selectedElement = -1; brushCursor = null
        adjustOpen = false; editFromCatalog = false
        tool = Tool.PAN; showGuides = false; hub.showMask = false
        if (saved != null) {
            // Al cancelar, la restauración también debe persistirse por si ya
            // hubo un autoguardado mientras se editaba.
            autoSaveJob?.cancel()
            saveProject(forceCurrent = true)
        }
    }
    fun paintEffectZone() {
        val id = editingEffect ?: return
        val mk = masks ?: return
        var layer = (settings["$id.ownedMask"] ?: -1f).toInt()
        if (layer !in 3 until mk.count) {
            layer = mk.addLayer(Effects.all.firstOrNull { it.id == id }?.title ?: id)
            if (layer < 0) { message = "Límite de nueve zonas independientes por proyecto."; return }
            setSetting("$id.ownedMask", layer.toFloat())
            // Migrate an existing zone without altering the zones used by other effects.
            val previous = (settings["$id.mask"] ?: -1f).toInt()
            if (previous in 0 until layer) {
                val copy = mk.snapshot().filter { it.layer == previous }.map { old ->
                    MaskStroke(layer, old.radius, old.feather, old.erase, old.fill, old.image).also { it.points.addAll(old.points) }
                }
                copy.forEach { mk.addStroke(it) }
            }
        }
        maskNames = mk.names.toList(); setEffectMask(id, layer)
        requestOptions()
        if (playing) togglePlay()
        selectMask(layer); eraser = false; paintingZone = true; showGuides = true
    }
    fun wholeEffectZone() { editingEffect?.let { setEffectMask(it, -2) } }
    fun finishPainting() { endStroke(); paintingZone = false; brushCursor = null; tool = Tool.PAN; showGuides = false; hub.showMask = false }
    /** Animar › Congelar: pinta lo que no se mueve sin salir de Animar. */
    fun freezeZone() {
        finishEdit(true); beginEdit(); animPanel = ""
        if (playing) togglePlay()
        selectMask(0); eraser = false; showGuides = true; paintingZone = true
    }

    /** Animar: elige Ruta / Ancla / Borrar. Tocar otra vez la misma herramienta la suelta. */
    fun selectGuideTool(t: Tool) {
        val same = tool == t && editingEffect == null && !paintingZone
        finishEdit(true); animPanel = ""
        if (playing) togglePlay()
        tool = if (same) Tool.PAN else t
        showGuides = true
        hub.showMask = false
    }

    // v40: el lienzo tiene un tamaño FIJO. Solo cambia si el usuario elige otro tamaño
    // de vista previa (Ajustes › Vista) o con el botón de pantalla completa.
    var previewSize by mutableStateOf(PreviewSize.of(prefs.getString("previewSize", null)))
        private set
    fun changePreviewSize(size: PreviewSize) {
        if (size == previewSize) return
        previewSize = size
        prefs.edit().putString("previewSize", size.name).apply()
    }
    /**
     * v40: hoja de opciones de la herramienta (en la zona de trabajo inferior, nunca
     * encima del lienzo). Cerrada = se ve la línea de tiempo.
     */
    var optionsOpen by mutableStateOf(false)
        private set
    fun requestOptions() { optionsOpen = true }
    fun closeOptions() {
        optionsOpen = false
        if (animPanel.isNotEmpty()) animPanel = ""
        replacingClip = null
        soundTrialUid = null
    }

    // ============================================================ v41: navegación como un editor de vídeo
    /** false = barra principal (Animar, Proteger, Efectos, Audio, Ajustes); true = herramientas de la categoría. */
    var categoryOpen by mutableStateOf(false)
        private set
    /** Pista de la imagen seleccionada en la línea de tiempo (acciones: duración, formato, color…). */
    var imageSelected by mutableStateOf(false)
        private set
    /** En el catálogo: true = panel Ajustar del efecto que se está previsualizando. */
    var adjustOpen by mutableStateOf(false)
    /** La edición actual empezó tocando una miniatura del catálogo. */
    var editFromCatalog by mutableStateOf(false)
        private set
    /** Escala de la línea de tiempo en dp por segundo (<= 0: automática). */
    var timelinePps by mutableFloatStateOf(-1f)

    fun openCategory(t: EditorTab) {
        selectTab(t)
        imageSelected = false
        when (t) {
            EditorTab.EFFECTS -> { categoryOpen = false; optionsOpen = true }
            // v44: Animar abre su catálogo visual (como Efectos); ✕ deja la barra de herramientas.
            EditorTab.TOOLS -> { categoryOpen = true; optionsOpen = false; openAnimCatalog() }
            EditorTab.MASKS -> { categoryOpen = true; optionsOpen = false; protect() }
            else -> { categoryOpen = true; optionsOpen = false }
        }
    }

    /** Música y sonidos de ambiente (hoja propia). */
    fun openAudio() {
        selectTab(EditorTab.CANVAS)
        imageSelected = false
        categoryOpen = false
        settingsSection = 3
        optionsOpen = true
    }

    /** Ajustes del proyecto desde la pista de la imagen: 0 duración, 1 formato, 2 color. */
    fun openImageSetting(section: Int) {
        selectTab(EditorTab.CANVAS)
        categoryOpen = false
        settingsSection = section
        optionsOpen = true
    }

    /** Flecha «<»: vuelve a la barra principal dejando todo aplicado. */
    fun backToMain() {
        finishCropEdit()
        finishEdit(true)
        animCatalogOpen = false; animKey = null
        placingClip = false; replacingClip = null; adjustOpen = false; editFromCatalog = false
        selectedTimelineClip = null; imageSelected = false
        selectedLayer = null; blendPanelOpen = false; layerMotionOpen = false
        optionsOpen = false; animPanel = ""
        tool = Tool.PAN; showGuides = false; hub.showMask = false
        categoryOpen = false
    }

    fun selectImageTrack(selected: Boolean = true) {
        if (selected) {
            finishEdit(true)
            selectedTimelineClip = null
            selectedLayer = null
            placingClip = false
            optionsOpen = false
        }
        imageSelected = selected
    }

    /** Toque en una miniatura del catálogo: vista previa real; tocarla otra vez abre Ajustar. */
    fun previewFromCatalog(id: String) {
        if (editingEffect == id && editFromCatalog) { adjustOpen = true; return }
        if (editingEffect != null && editFromCatalog) finishEdit(false)
        editEffect(id)
        editFromCatalog = true
        adjustOpen = false
        tab = EditorTab.EFFECTS
        optionsOpen = true
    }

    /** «Ninguno»: descarta el efecto que se estaba probando. */
    fun cancelCatalogPreview() {
        if (editingEffect != null && editFromCatalog) finishEdit(false)
        adjustOpen = false
    }

    // ------------------------------------------------------------ v44: catálogo de Animar

    var animCatalogOpen by mutableStateOf(false)
    var animCategory by mutableStateOf(prefs.getString("animCategory", null) ?: com.zeta.fondo.model.AnimCatalog.categories.first())
    /** Entrada del catálogo que se está probando (null = ninguna). */
    var animKey by mutableStateOf<String?>(null)

    fun openAnimCatalog() {
        if (editingEffect != null) finishEdit(true)
        animCatalogOpen = true
        adjustOpen = false
        animKey = null
    }

    fun selectAnimCategory(c: String) {
        animCategory = c
        prefs.edit().putString("animCategory", c).apply()
    }

    /** Tocar una miniatura: se prueba sobre la imagen sin confirmar; tocarla otra vez = Ajustar. */
    fun previewAnim(e: com.zeta.fondo.model.AnimCatalog.Entry) {
        if (e.kind == com.zeta.fondo.model.AnimCatalog.Kind.TOOL) {
            discardAnim()
            when (e.tool) {
                "ARROW" -> { selectGuideTool(Tool.ARROW); showHint("Dibuja flechas sobre la imagen: el dibujo fluye en esa dirección") }
                "ANCHOR" -> { selectGuideTool(Tool.ANCHOR); showHint("Toca para fijar puntos que no se mueven") }
                else -> freezeZone()
            }
            return
        }
        val id = e.effectId
        // v44.10: Reemplazar desde Animar: el bloque cambia de efecto (mismo tiempo y pista) y
        // después se aplica el estilo tocado, como una prueba normal del catálogo.
        replacingClip?.let { uid ->
            if (replaceAnimMode) { replaceClipEffect(uid, id); replaceAnimMode = false }
        }
        if (animKey == e.key && editingEffect == id && editFromCatalog) { adjustOpen = true; return }
        if (editingEffect != null && editFromCatalog && editingEffect != id) finishEdit(false)
        if (editingEffect != id) editEffect(id, autoPaint = false)
        when {
            id == "cam" -> applyCameraPreset(e.variant)
            id == "natural" -> applyNaturalPreset(e.variant)
            id in com.zeta.fondo.model.EffectCatalog.styledPresets -> applyEffectStyle(id, e.variant)
            com.zeta.fondo.model.EffectCatalog.variants(id).isNotEmpty() -> setSetting("$id.variant", e.variant.toFloat())
        }
        animKey = e.key
        editFromCatalog = true
        adjustOpen = false
        tab = EditorTab.TOOLS
        animCatalogOpen = true
        if (!playing) togglePlay()
        if (e.needsZone && (settings["$id.mask"] ?: -2f).toInt() == -2)
            showHint("Se prueba en toda la imagen. En Ajustar › Zona pintas qué parte se mueve.", 3200L)
    }

    /** ✕ del catálogo de Animar: descarta la prueba sin tocar el proyecto. */
    fun discardAnim() {
        if (editingEffect != null && editFromCatalog) finishEdit(false)
        adjustOpen = false; editFromCatalog = false
        animKey = null
        animCatalogOpen = false
        if (replaceAnimMode) { replacingClip = null; replaceAnimMode = false; optionsOpen = false }
    }

    /** ✓ de una hoja: aplica lo que se estaba editando y vuelve a la línea de tiempo. */
    fun applyAndClose() {
        animCatalogOpen = false; animKey = null
        if (soundEditing != null) { soundEditing = null }
        if (editingEffect != null) finishEdit(true)
        adjustOpen = false; editFromCatalog = false
        optionsOpen = false; replacingClip = null; animPanel = ""
        soundTrialUid = null
        finishCropEdit()
    }

    /** Restablecer: valores de fábrica del efecto (o del bloque seleccionado). */
    fun resetEffectParams(id: String) {
        val def = Effects.all.firstOrNull { it.id == id } ?: return
        val target = fxTarget(id)
        if (target != null) {
            effectClips = effectClips.map { if (it.uid == target.uid) it.copy(overrides = emptyMap()) else it }
        } else {
            val keys = def.params.map { "$id.${it.key}" }.filter { !it.endsWith(".zone") }
            settings = settings + keys.associateWith { Effects.default(it) } + ("$id.tintOn" to 0f)
        }
        hub.boostPreview()
        pushParams()
        scheduleAutosave()
    }

    /** Duración del vídeo desde el tirador de la pista de la imagen. */
    fun setVideoDuration(sec: Int) {
        val s = sec.coerceIn(2, 60)
        if (s == exportSettings.loopSec) return
        updateExport(exportSettings.copy(loopSec = s))
        saveProject()
    }

    /** Pistas de la línea de tiempo visibles también mientras hay opciones abiertas. */
    var lanesWithOptions by mutableStateOf(prefs.getBoolean("lanesWithOptions", false))
        private set
    fun toggleLanesWithOptions() {
        lanesWithOptions = !lanesWithOptions
        prefs.edit().putBoolean("lanesWithOptions", lanesWithOptions).apply()
    }
    /** Ayuda (?) de la categoría abierta; null = cerrada. */
    var helpTab by mutableStateOf<EditorTab?>(null)

    // v39: indicación corta y temporal sobre el lienzo (sustituye los textos fijos).
    var hint by mutableStateOf<String?>(null)
        private set
    private var hintJob: Job? = null
    fun showHint(text: String, ms: Long = 2600L) {
        hint = text
        hintJob?.cancel()
        hintJob = viewModelScope.launch { delay(ms); if (hint == text) hint = null }
    }

    // v39: transparencia de la máscara al pintar (que no tape la imagen). Se recuerda.
    var maskOpacity by mutableFloatStateOf(prefs.getFloat("maskOpacity", .45f))
        private set
    fun changeMaskOpacity(v: Float, persist: Boolean) {
        maskOpacity = v.coerceIn(.1f, .9f)
        hub.maskAlpha = maskOpacity
        if (persist) prefs.edit().putFloat("maskOpacity", maskOpacity).apply()
    }

    // v39: tiempos REALES de apertura medidos en el teléfono (se muestran en Ajustes).
    private var openStartNs = 0L
    private var firstZoomPending = false
    var perfImageMs by mutableStateOf(-1L); private set
    var perfZoomMs by mutableStateOf(-1L); private set
    var perfEffectsMs by mutableStateOf(-1L); private set
    private fun perfStart() {
        openStartNs = System.nanoTime(); firstZoomPending = true
        perfImageMs = -1L; perfZoomMs = -1L; perfEffectsMs = -1L
    }
    private fun perfLog(what: String, ms: Long) { android.util.Log.i("ZetaPerf", "$what: $ms ms") }
    private fun perfElapsed(): Long = if (openStartNs == 0L) -1L else (System.nanoTime() - openStartNs) / 1_000_000L

    fun protect() {
        finishEdit(true); beginEdit(); tab = EditorTab.MASKS
        if (playing) togglePlay()
        selectMask(0); eraser = false; showGuides = true; paintingZone = true
    }
    fun addElement(type: Int) {
        val slot = (0..3).firstOrNull { (settings["element$it.on"] ?: 0f) < .5f }
        if (slot == null) { message = "Hasta cuatro elementos simultáneos."; return }
        settings = settings + mapOf("element$slot.on" to 1f, "element$slot.type" to type.toFloat(),
            "element$slot.x" to .5f, "element$slot.y" to .5f, "element$slot.size" to .3f,
            "element$slot.rotation" to 0f, "element$slot.opacity" to .7f, "element$slot.cycles" to 1f)
        selectedElement = slot; pushParams()
    }
    fun selectElement(slot: Int) { selectedElement = slot }
    fun transformElement(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        val id = "element$selectedElement"
        if (selectedElement < 0) return
        settings = settings + mapOf("$id.x" to ((settings["$id.x"] ?: .5f) + dx).coerceIn(0f,1f),
            "$id.y" to ((settings["$id.y"] ?: .5f) + dy).coerceIn(0f,1f),
            "$id.size" to ((settings["$id.size"] ?: .3f) * zoom).coerceIn(.04f, 1.5f),
            "$id.rotation" to ((((settings["$id.rotation"] ?: 0f) + rotation + 540f) % 360f) - 180f))
        pushParams()
    }

    // ---------------- Vista (zoom del editor)
    var viewScale by mutableFloatStateOf(1f)
        private set
    var viewX by mutableFloatStateOf(0f)
        private set
    var viewY by mutableFloatStateOf(0f)
        private set
    var cropW by mutableFloatStateOf(1f)
        private set
    var cropH by mutableFloatStateOf(1f)
        private set
    private var cropX = 0f
    private var cropY = 0f
    val previewAspect: Float get() = aspect * cropW / cropH

    // ---------------- Deshacer
    private val undoStack = ArrayList<UndoAction>()
    private val redoStack = ArrayList<UndoAction>()
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
        private set
    private var currentStroke: MaskStroke? = null
    private var flowJob: Job? = null

    // ---------------- Exportación
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set
    private var lastProgressPost = -1f

    val aspect: Float
        get() = meta?.let { it.w.toFloat() / it.h } ?: 1f

    init {
        // v44.17: la profundidad con IA y MI-GAN se quitaron. Se borran (una vez, en segundo plano)
        // sus modelos descargados o importados; solo se conserva el de «Mejorar imagen».
        Thread({
            for (d in listOf(java.io.File(app.filesDir, "models_dl"), java.io.File(app.filesDir, "models_user")))
                try { if (d.exists()) d.deleteRecursively() } catch (_: Throwable) {}
            try {
                java.io.File(app.noBackupFilesDir, "models").listFiles()?.forEach {
                    if (it.name != com.zeta.fondo.engine.SuperResolver.ASSET) it.delete()
                }
            } catch (_: Throwable) {}
        }, "zeta-limpiar-ia").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        hub.onError = { msg ->
            com.zeta.fondo.diag.ZetaLog.error("GL", msg)
            main.post { glError = msg }
        }
        hub.onGlWarning = { msg -> com.zeta.fondo.diag.ZetaLog.warn("GL", msg) }
        hub.onFramePending = { main.post { previewReady = false; effectsReady = false } }
        // Mantener la referencia del mismo bitmap que ya usa OpenGL (sin duplicar RAM).
        // Tras recrearse la superficie, se vuelve a mostrar como respaldo sin negro.
        hub.onFrameReady = { main.post {
            previewReady = true
            if (perfImageMs < 0 && meta != null) { perfImageMs = perfElapsed(); perfLog("imagen visible", perfImageMs) }
        } }
        hub.onCompiling = { busy -> main.post { effectsReady = !busy } }
        hub.onFullReady = { main.post {
            effectsReady = true
            if (perfEffectsMs < 0 && meta != null) { perfEffectsMs = perfElapsed(); perfLog("efectos listos", perfEffectsMs) }
        } }
        hub.maskAlpha = maskOpacity
        refreshProjects()
    }

    // ============================================================ Proyectos

    fun refreshProjects() {
        viewModelScope.launch {
            projects = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun importImage(uri: Uri, initialTab: EditorTab = EditorTab.TOOLS, initialCategory: String = "Populares", loopSec: Int = 6) {
        if (busy != null) return
        saveProject()
        busy = "Abriendo imagen…"
        perfStart()
        viewModelScope.launch {
            try {
                // v39: una sola decodificación; la vista previa se reutiliza al abrir y las
                // cachés (miniatura y edición) se escriben después en segundo plano.
                val created = withContext(Dispatchers.IO) {
                    store.createFast(uri, loopSec.coerceIn(2, 60), previewMaxForDevice(), if (lowResourceDevice) 2560 else 4096)
                }
                busy = null
                openProject(created.id, initialTab, initialCategory, created.preview, fromImport = true)
                launch(Dispatchers.IO) { store.writeCaches(created) }
            } catch (e: Throwable) {
                busy = null
                afterOpenLayers = emptyList()
                message = "No se pudo importar: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /**
     * v44.25 · Android pide memoria. En segundo plano (nivel ≥ 20) o con poca memoria (≥ 10): se
     * sueltan cachés que se vuelven a leer solas (miniaturas, sonidos, comparación de Mejorar).
     * Nunca se toca el proyecto ni lo que se está dibujando.
     */
    fun trimMemory(level: Int) {
        if (level < 10) return
        com.zeta.fondo.ui.ThumbCache.trim()
        com.zeta.fondo.ui.HomeThumbs.trim()
        com.zeta.fondo.engine.UserSounds.trim()
        if (level >= 20 && !srPanelOpen) srCompare = null
    }

    fun importAudio(uri: Uri) {
        val project = meta ?: return
        if (busy != null || exportState is ExportState.Running) return
        if (playing) togglePlay()
        busy = "Importando audio…"
        viewModelScope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    val temp = java.io.File(project.dir, "audio-import.tmp")
                    val result = com.zeta.fondo.engine.ImportedAudio.decode(getApplication(), uri, temp)
                    java.nio.file.Files.move(temp.toPath(), java.io.File(project.dir,
                        com.zeta.fondo.engine.ImportedAudio.FILE_NAME).toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    result
                }
                updateExport(exportSettings.copy(audioName = decoded.name, audioRate = decoded.rate,
                    audioChannels = decoded.channels, audioFrames = decoded.frames,
                    audioStartFrame = 0L, audioEnabled = true, soundVolume = 1f))
                saveProject()
                if (decoded.truncated) {
                    // Lo que de verdad se guardó (10 min; menos si el audio es de muy alta calidad).
                    val kept = (decoded.frames / decoded.rate.coerceAtLeast(1)).toInt()
                    message = "El audio es muy largo: se usan sus primeros ${kept / 60}:${(kept % 60).toString().padStart(2, '0')} min."
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                // v44.25: también la falta de memoria o un archivo raro (antes podía cerrar la app).
                message = "No se pudo importar el audio: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = null }
        }
    }

    fun removeAudio() {
        if (playing) togglePlay()
        updateExport(exportSettings.copy(audioName = "", audioFrames = 0L, audioStartFrame = 0L, audioEnabled = false))
        saveProject()
    }

    val lowResourceDevice: Boolean by lazy {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.isLowRamDevice || am.memoryClass <= 128
    }

    /** La imagen de edición se dimensiona para la RAM del teléfono, no para exportar. */
    private fun previewMaxForDevice(): Int {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return com.zeta.fondo.model.PreviewSizing.target(lowResourceDevice, am.memoryClass)
    }

    fun openProject(id: String, initialTab: EditorTab = EditorTab.TOOLS, initialCategory: String = "Populares",
                    preloaded: Bitmap? = null, fromImport: Boolean = false) {
        // v44.25: dos toques seguidos (o tocar otro proyecto mientras se importa) abrían dos
        // proyectos a la vez: doble memoria y podía quedar el equivocado.
        // v44.25: las demás imágenes compartidas se toman ya (si algo falla, no quedan pendientes
        // para el próximo proyecto que se importe).
        val moreLayers = if (fromImport) afterOpenLayers else emptyList()
        if (fromImport) afterOpenLayers = emptyList()
        if (opening || (!fromImport && busy != null)) return
        opening = true
        if (!fromImport) perfStart()
        busy = "Abriendo proyecto…"
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { store.load(id) }

                // v26: nunca mostramos el thumbnail pequeño en el editor porque produce
                // el salto borroso -> nítido. Cada proyecto guarda una caché de edición
                // suficientemente grande para verse nítida desde el primer cuadro.
                val quickPreview: Bitmap = preloaded?.takeIf { !it.isRecycled } ?: withContext(Dispatchers.IO) {
                    store.loadOrCreateEditPreview(loaded.meta, previewMaxForDevice())
                }
                val mk = withContext(Dispatchers.Default) {
                    MaskLayers.create(loaded.meta.w, loaded.meta.h).also {
                        val dir = loaded.meta.dir
                        it.imageSource = { name -> maskImage(dir, name) }
                        it.setAll(loaded.strokes, loaded.maskNames)
                    }
                }

                autoSaveJob?.cancel()
                editSnapshot = null; editingEffect = null; paintingZone = false; selectedElement = -1; brushCursor = null
                meta = loaded.meta
                arrows.clear()
                arrows.addAll(loaded.arrows)
                anchors.clear()
                anchors.addAll(loaded.anchors)
                masks = mk
                maskNames = mk.names.toList()
                selectedMaskLayer = 0
                hub.activeMask = 0
                settings = loaded.settings
                exportSettings = loaded.export
                effectClips = loaded.effectClips.map { EffectTimeline.normalized(it, loaded.export.loopSec.toFloat()) }
                // v44: los ambientes del bucle de proyectos antiguos pasan a ser bloques de sonido
                // (mismo sonido y volumen, cubriendo todo el vídeo).
                if (loaded.export.ambient.any { it.value > .01f }) {
                    effectClips = effectClips + com.zeta.fondo.model.SoundClips.fromLegacy(loaded.export.ambient,
                        loaded.export.loopSec.toFloat(), (effectClips.maxOfOrNull { it.uid } ?: 0L) + 1L)
                    exportSettings = exportSettings.copy(ambient = emptyMap())
                }
                selectedTimelineClip = null
                loadLayers(loaded.meta, loaded.layers)
                undoStack.clear()
                redoStack.clear()
                canUndo = false
                canRedo = false
                currentStroke = null
                exportState = ExportState.Idle
                glError = null
                cropEditing = false
                resetView()
                tool = Tool.PAN
                showGuides = false
                effectsCategory = initialCategory
                animPanel = ""; effectSection = "Ajustes"; settingsSection = 0
                hub.resizing = false; hint = null
                tab = initialTab
                // v40: cada proyecto abre con el lienzo fijo y la línea de tiempo (o el catálogo).
                placingClip = false; replacingClip = null
                // v41: se abre en la barra principal con la línea de tiempo (o el catálogo).
                categoryOpen = false; imageSelected = false; adjustOpen = false; editFromCatalog = false
                optionsOpen = initialTab == EditorTab.EFFECTS
                fullscreen = false
                activeMotionEffect = null
                // v37: abrir una imagen o un proyecto deja TODO en pausa en el inicio.
                playing = false
                hub.setPlaying(false)
                scrubT = 0f
                hub.setPausedT(0f)
                if (initialTab == EditorTab.MASKS) selectMask(0)

                // Mostrar el editor con una caché ya nítida. Esta misma imagen queda como
                // placeholder sobre OpenGL hasta el primer cuadro, sin destello borroso.
                previewFallback = quickPreview
                previewReady = false
                updateOriginalPreview(false)
                hub.image = quickPreview
                basePreview = quickPreview
                srPreviewJob?.cancel()
                refreshPreviewImage()   // v44.10: si la mejora está activa, la vista previa ya la muestra
                hub.character = null
                hub.masks = mk
                mk.onChange = { hub.invalidate(); main.post { maskRevision++; schedulePlateRefresh(); scheduleQuickDepthRefresh() } }
                hub.flow = null
                resetDepth()
                resetCutout()
                tapSelectFor = null; regionState = AiJob.Idle; sceneSuggestions = emptyList(); srCompare = null; srState = AiJob.Idle
                loadDepthCache(loaded.meta)
                // v44.17: nombres de la música / sonidos propios para los bloques de la línea de tiempo.
                com.zeta.fondo.model.SoundCatalog.userNames.clear()
                viewModelScope.launch(Dispatchers.IO) {
                    try { for (u in com.zeta.fondo.engine.UserSounds.list(loaded.meta.dir)) com.zeta.fondo.model.SoundCatalog.userNames[u.id] = u.name } catch (_: Throwable) {}
                }
                loadPlateCache(loaded.meta, mk)
                // v44.24: el modo «pintar máscara» solo con un pincel elegido. Antes quedaba activo al
                // abrir y ocultaba las capas de imagen y los efectos hasta tocar cualquier botón.
                hub.showMask = showMasks && !playing && tool.isBrush
                // v44.25: la máscara automática vieja (sola, sin trazos tuyos) tapaba la explosión
                // y los efectos «Detrás» en los fondos: se quita al abrir; ↶ la devuelve.
                val droppedAuto = dropStaleAutoProtect()
                pushParams()
                recomputeFlow()
                refreshEffectBoxes()
                screen = Screen.EDITOR
                busy = null
                if (droppedAuto) {
                    message = "Quité la máscara automática de Proteger: tapaba efectos en este fondo. Si la querías, toca ↶."
                    scheduleAutosave()
                }
                // v44.25: imágenes compartidas a la vez → las demás entran como capas.
                if (moreLayers.isNotEmpty()) addLayers(moreLayers)

                // v28: no reemplazar la textura de la imagen unos segundos después de abrir.
                // Ese cambio coincidía con el primer gesto de zoom y podía provocar un tirón
                // por decodificación + subida de textura. La caché nítida queda estable durante
                // toda la edición; la imagen original completa sigue reservada para exportar.
                launch {
                    val character = try {
                        withContext(Dispatchers.IO) { loadCharacter(loaded.meta.dir) }
                    } catch (_: Throwable) { null }
                    if (screen == Screen.EDITOR && meta?.id == loaded.meta.id) {
                        hub.character = character
                    }
                }
            } catch (e: Throwable) {
                busy = null
                message = "No se pudo abrir: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                opening = false
            }
        }
    }

    /** v44.25 · Hay un proyecto abriéndose (protege contra toques repetidos). */
    private var opening = false

    fun saveProject(forceCurrent: Boolean = false) {
        val m = meta ?: return
        val mk = masks ?: return
        // El guardado explícito de fondo incluye lo que se está pintando.
        // Un cierre inesperado no debe volver a la configuración anterior.
        val pending = if (forceCurrent) null else editSnapshot
        val a = ArrayList(pending?.arrows ?: arrows)
        val an = ArrayList(pending?.anchors ?: anchors)
        val st = pending?.strokes ?: mk.snapshot()
        val labels = pending?.names ?: mk.names.toList()
        val s = pending?.settings ?: settings
        val ex = exportSettings
        val clips = effectClips.toList()
        val ly = layers
        viewModelScope.launch {
            saveMutex.withLock {
                try { withContext(Dispatchers.IO) { store.save(m, a, an, st, s, ex, labels, clips, ly) } }
                catch (e: Exception) { message = "No se pudo guardar el proyecto: ${e.message}" }
            }
        }
    }

    fun closeEditor() {
        autoSaveJob?.cancel()
        if (busy != null) return
        if (exportState is ExportState.Running) {
            message = "Espera a que termine la exportación o cancélala."
            return
        }
        fullscreen = false
        finishEdit(true)
        saveProject()
        closeLayers(meta)
        soloClip = null
        resetDepth()
        resetCutout()
        tapSelectFor = null; regionState = AiJob.Idle; sceneSuggestions = emptyList(); srCompare = null; srJob?.cancel(); srState = AiJob.Idle
        screen = Screen.HOME
        meta = null
        effectClips = emptyList()
        selectedTimelineClip = null
        previewFallback = null
        previewReady = false
        updateOriginalPreview(false)
        hub.image = null
        basePreview = null
        srPreviewJob?.cancel()
        refreshProjects()
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refreshProjects()
        }
    }

    // ============================================================ Efectos

    fun selectTab(t: EditorTab) {
        finishCropEdit()
        finishEdit(true)
        placingClip = false
        replacingClip = null
        selectedTimelineClip = null
        optionsOpen = t == EditorTab.EFFECTS || t == EditorTab.CANVAS || t == EditorTab.MASKS
        tab = t
        if (t == EditorTab.MASKS && playing) togglePlay()
        showGuides = !playing && (t == EditorTab.TOOLS || t == EditorTab.MASKS)
        hub.showMask = false
    }

    fun protectPoints(erase: Boolean = false) {
        finishEdit(true); tab = EditorTab.MASKS
        if (playing) togglePlay()
        tool = if (erase) Tool.ERASE_GUIDE else Tool.ANCHOR
        showGuides = true; hub.showMask = false
    }

    fun editZoneMotion() { editEffect("zonemotion") }

    val activeEffectsCount: Int get() = com.zeta.fondo.model.EffectSwitches.active(settings).size
    fun removeAllEffects() {
        finishEdit(true)
        val before = com.zeta.fondo.model.EffectSwitches.active(settings)
        if (before.isEmpty()) return
        val after = com.zeta.fondo.model.EffectSwitches.disabled(settings)
        val beforeClips = effectClips.toList()
        settings = settings + after
        effectClips = emptyList()
        selectedTimelineClip = null
        pushUndo(UndoAction.EffectSwitches(before, after, beforeClips, emptyList())); pushParams(); saveProject()
    }

    fun addMask() {
        val mk = masks ?: return
        val i = mk.addLayer("Máscara ${maskNames.size - 2}")
        if (i < 0) { message = "Máximo ${MaskLayers.MAX_MASKS} máscaras para cuidar la memoria del dispositivo."; return }
        maskNames = mk.names.toList()
        selectMask(i)
    }

    fun selectMask(index: Int) {
        if (index !in maskNames.indices) return
        selectedMaskLayer = index
        hub.activeMask = index
        tool = when (index) {
            0 -> Tool.FREEZE
            1 -> Tool.ZONE1
            2 -> Tool.ZONE2
            else -> Tool.MASK
        }
        showMasks = true
        hub.showMask = true
    }

    fun renameMask(index: Int, label: String) {
        val mk = masks ?: return
        mk.renameLayer(index, label)
        maskNames = mk.names.toList()
    }

    fun setEffectMask(id: String, index: Int) {
        setSetting("$id.mask", index.toFloat())
        if (id == "natural" || id == "flame") refreshEffectBoxes()
    }

    fun resetColor() {
        settings = settings.filterKeys { !it.startsWith("adjust.") }
        pushParams()
        scheduleAutosave()
    }

    /** v44: varios ajustes a la vez (p. ej. dirección + fuerza del joystick) en un solo cambio. */
    fun setSettingsBatch(values: Map<String, Float>) {
        for ((id, group) in values.entries.groupBy({ it.key.substringBefore('.') }, { it.key to it.value }))
            updateFx(id, group.toMap())
    }

    fun setSetting(key: String, value: Float) {
        if (!value.isFinite()) return   // v44.25: un valor inválido nunca entra en el proyecto
        val v = if ((settings["loop.perfect"] ?: 0f) > .5f && key.endsWith(".cycles"))
            kotlin.math.round(value).coerceAtLeast(1f) else value
        if (canKeyframe(key) && autoKey(key, v)) return
        updateFx(key.substringBefore('.'), mapOf(key to v))
        if (key == "flow.reach") recomputeFlow()
    }

    // ============================================================ v40: ajustes por bloque

    /**
     * Lectura de ajustes de efecto que respeta el bloque en edición: con varios bloques del
     * mismo efecto, cada uno muestra y edita SUS valores (vm.fx["humo.size"]).
     */
    inner class FxView {
        operator fun get(key: String): Float? = ClipEdits.value(settings, fxTarget(key.substringBefore('.')), key)
    }
    val fx = FxView()

    /** Bloque cuyos ajustes se editan (null = el efecto entero). */
    fun fxTarget(id: String): EffectClip? = ClipEdits.target(clipsForEffect(id), selectedTimelineClip)

    /** Cambia ajustes de [id]: van al bloque en edición o, si no hay, al efecto entero. */
    private fun updateFx(id: String, values0: Map<String, Float>, clip: EffectClip? = fxTarget(id)) {
        val values = if (values0.values.all { it.isFinite() }) values0 else values0.filterValues { it.isFinite() }
        if (clip == null) settings = settings + values
        else {
            val own = values.filterKeys { ClipEdits.isClipKey(id, it) }
            val shared = values - own.keys
            if (shared.isNotEmpty()) settings = settings + shared
            if (own.isNotEmpty()) effectClips = effectClips.map {
                if (it.uid == clip.uid) it.copy(overrides = it.overrides + own) else it
            }
        }
        hub.boostPreview()
        pushParams()
        scheduleAutosave()
    }

    /** ¿El efecto [id] se puede colocar tocando la imagen (tiene origen en el motor)? */
    fun canPlace(id: String): Boolean = id in placeableEffects
    fun canScale(id: String): Boolean = Effects.all.firstOrNull { it.id == id }?.params?.any { it.key == "size" } == true &&
        id in placeableEffects
    fun canAim(id: String): Boolean = id in aimEffects

    /** Herramienta Posición: mover / agrandar / girar el bloque seleccionado sobre la imagen. */
    var placingClip by mutableStateOf(false)
        private set
    fun togglePlacing() {
        val clip = selectedClip() ?: return
        if (!canPlace(clip.effectId)) {
            // v40.1: efectos que viven en una zona pintada (fuego, agua, nubes…): Mover abre
            // directamente su sección Zona, donde se pinta o se elige dónde actúa.
            val uid = clip.uid
            editEffect(clip.effectId)
            selectedTimelineClip = uid
            effectSection = "Zona"
            showHint("Elige Círculo o Rectángulo y arrástralo, o pinta la zona")
            return
        }
        if (playing) togglePlay()
        placingClip = !placingClip
        if (placingClip) showHint("Arrastra el punto · aro = tamaño · flecha = dirección")
    }
    fun selectedClip(): EffectClip? = effectClips.firstOrNull { it.uid == selectedTimelineClip }

    /** Valor efectivo de un ajuste para un bloque concreto. */
    fun clipValue(clip: EffectClip, suffix: String): Float? {
        val key = "${clip.effectId}.$suffix"
        val target = ClipEdits.target(clipsForEffect(clip.effectId), clip.uid)
        return ClipEdits.value(settings, target, key) ?: Effects.all.firstOrNull { it.id == clip.effectId }
            ?.params?.firstOrNull { it.key == suffix }?.def
    }

    private var transformBefore: Pair<Map<String, Float>, List<EffectClip>>? = null
    fun beginClipTransform() {
        if (transformBefore == null) transformBefore = settings to effectClips.toList()
        hub.gestureActive = false
    }
    /** Mueve, escala o gira el bloque sobre la imagen (no toca el zoom del lienzo). */
    fun transformClip(uid: Long, originX: Float? = null, originY: Float? = null, size: Float? = null, direction: Float? = null) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        val id = clip.effectId
        val values = HashMap<String, Float>()
        if (originX != null && originY != null) {
            values["$id.origin"] = 0f
            values["$id.originX"] = originX.coerceIn(0f, 1f)
            values["$id.originY"] = originY.coerceIn(0f, 1f)
        }
        if (size != null) {
            val def = Effects.all.firstOrNull { it.id == id }?.params?.firstOrNull { it.key == "size" }
            if (def != null) values["$id.size"] = size.coerceIn(def.min, def.max)
        }
        if (direction != null) values["$id.direction"] = ((direction % 360f) + 360f) % 360f
        if (values.isEmpty()) return
        updateFx(id, values, ClipEdits.target(clipsForEffect(id), uid))
        hub.boostPreview(320)
    }
    fun endClipTransform() {
        val before = transformBefore ?: return
        transformBefore = null
        if (before.first === settings && before.second == effectClips) return
        pushUndo(settingsUndo(before.first, before.second))
        saveProject()
    }

    /** Sustituir: el catálogo elige el efecto nuevo para este bloque. */
    var replacingClip by mutableStateOf<Long?>(null)
        private set
    fun startReplace() {
        val clip = selectedClip() ?: return
        finishEdit(true)
        selectedTimelineClip = clip.uid
        // v44.10: un bloque de Animar (cámara, 2.5D, movimiento natural…) se reemplaza desde
        // el catálogo de Animar; un efecto, desde el de Efectos.
        val animIds = com.zeta.fondo.model.AnimCatalog.all()
            .filter { it.kind == com.zeta.fondo.model.AnimCatalog.Kind.STYLE }.map { it.effectId }.toSet()
        replaceAnimMode = clip.effectId in animIds
        replacingClip = clip.uid
        placingClip = false
        if (replaceAnimMode) {
            tab = EditorTab.TOOLS
            animKey = null; adjustOpen = false
        } else tab = EditorTab.EFFECTS
        optionsOpen = true
        showHint("Toca el efecto nuevo: ocupará el mismo tiempo y lugar.", 2600L)
    }
    private var replaceAnimMode = false
    /** ¿El reemplazo en curso se elige en el catálogo de Animar? */
    val replaceInAnim: Boolean get() = replacingClip != null && replaceAnimMode

    /** Aviso con botón Deshacer (p. ej. tras eliminar un bloque). */
    var undoNotice by mutableStateOf<String?>(null)
        private set
    private var undoNoticeJob: Job? = null
    private fun showUndoNotice(text: String) {
        undoNotice = text
        undoNoticeJob?.cancel()
        undoNoticeJob = viewModelScope.launch { delay(4500); if (undoNotice == text) undoNotice = null }
    }
    fun undoFromNotice() { undoNotice = null; undo() }

    /** Registra en Deshacer un cambio de bloques (y de ajustes, si los hubo). */
    private fun commitTimeline(beforeClips: List<EffectClip>, beforeSettings: Map<String, Float> = settings) {
        if (beforeClips == effectClips && beforeSettings === settings) return
        pushUndo(settingsUndo(beforeSettings, beforeClips))
    }

    /**
     * v40.1: entrada de Deshacer con SOLO las claves que cambiaron. Una clave que no
     * existía antes se quita al deshacer (no se pone a 0: "mask" = 0 sería la capa Proteger).
     */
    private fun settingsUndo(beforeSettings: Map<String, Float>, beforeClips: List<EffectClip>): UndoAction.EffectSwitches {
        val keys = if (beforeSettings === settings) emptyList()
            else (settings.keys + beforeSettings.keys).filter { beforeSettings[it] != settings[it] }
        return UndoAction.EffectSwitches(
            keys.mapNotNull { k -> beforeSettings[k]?.let { k to it } }.toMap(),
            keys.mapNotNull { k -> settings[k]?.let { k to it } }.toMap(),
            beforeClips, effectClips.toList(),
            absentBefore = keys.filterTo(HashSet()) { !beforeSettings.containsKey(it) },
            absentAfter = keys.filterTo(HashSet()) { !settings.containsKey(it) })
    }

    /**
     * Bucle perfecto: usa trayectorias periódicas y velocidades enteras para que el
     * primer y último fotograma coincidan sin tener que ajustar cada efecto a mano.
     */
    fun setPerfectLoop(enabled: Boolean) {
        val next = HashMap(settings)
        next["loop.perfect"] = if (enabled) 1f else 0f
        if (enabled) {
            next["flow.mode"] = 1f       // ida y vuelta seno/coseno: mismo inicio y final
            next["flow.phase"] = 0f
            next["flow.blend"] = 1f
            val keys = next.keys.filter { it.endsWith(".cycles") }
            for (k in keys) next[k] = kotlin.math.round(next[k] ?: 1f).coerceAtLeast(1f)
        }
        settings = next
        pushParams(); saveProject()
        message = if (enabled) "Bucle perfecto activado" else "Bucle perfecto desactivado"
    }

    /** Pinta una de las zonas base sin crear una máscara extra. Útil para el parallax 2.5D. */
    fun paintBuiltinMask(index: Int) {
        if (index !in 1..2) return
        if (playing) togglePlay()
        selectMask(index)
        eraser = false
        paintingZone = true
        showGuides = true
        hub.showMask = true
    }

    fun maskHasPaint(index: Int): Boolean {
        val mk = masks ?: return false
        return synchronized(mk.lock) { mk.strokes.getOrNull(index)?.any { !it.erase } == true }
    }

    /** Cambia con cada trazo: permite que la interfaz recalcule los avisos de zona. */
    var maskRevision by mutableIntStateOf(0)
        private set

    private val zoneLegacy1 = setOf("drift", "ripple", "glint", "eyes", "blink", "water")
    private val zoneLegacy2 = setOf("sway", "heat", "flame", "cloth", "natural", "mesh")
    private val needsZone = zoneLegacy1 + zoneLegacy2 + setOf("zonemotion", "waterfall", "reflect", "shore",
        "reveal", "dispersion", "sky")

    // ============================================================ v45 · Profundidad IA (2.5D)

    sealed class DepthState {
        object Idle : DepthState()
        data class Running(val stage: String) : DepthState()
        data class Ready(val model: String, val ms: Long, val contrast: Float, val fromCache: Boolean) : DepthState()
        data class Failed(val reason: String) : DepthState()
    }
    var depthState by mutableStateOf<DepthState>(DepthState.Idle)
        private set
    var depthView by mutableStateOf(false)
        private set
    private var depthJob: Job? = null
    private val depthEstimator by lazy { com.zeta.fondo.engine.DepthEstimator() }

    /** Hay mapa y el interruptor «Usar profundidad IA» está activo. */
    val depthUsable: Boolean get() = depthState is DepthState.Ready && (settings["parallax.ai"] ?: 1f) > .5f

    private fun resetDepth() {
        depthJob?.cancel(); depthJob = null
        hub.depth = null
        hub.depthView = false
        depthView = false
        depthState = DepthState.Idle
    }

    /** Al abrir: usa el mapa guardado si es de la misma imagen (no se repite la IA). */
    private fun loadDepthCache(m: com.zeta.fondo.model.ProjectMeta) {
        depthJob = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { depthEstimator.loadCached(m.dir, m.imageFile) }
            if (r != null && meta?.id == m.id) {
                hub.depth = com.zeta.fondo.engine.DepthMap(r.w, r.h, r.map, r.meta.model)
                depthState = DepthState.Ready(r.meta.model, r.meta.ms, r.meta.contrast, true)
            }
        }
        // v44.10: sin mapa guardado y con el 2.5D activo, se prepara el automático.
        depthJob?.invokeOnCompletion { main.post { if (meta?.id == m.id) ensureDepth() } }
    }

    /** Aviso que acompaña al mapa (p. ej. «la IA no cupo en la RAM; se usa el 2.5D rápido»). */
    var depthNotice by mutableStateOf<String?>(null)
        private set

    /**
     * v44.10 · Al activar el 2.5D (o abrir su panel) sin mapa, se calcula SOLO el «2.5D rápido»:
     * al instante, sin descargas y en cualquier teléfono. Así el efecto siempre se mueve.
     * La IA solo se usa si la pides con «Con IA (más preciso)».
     */
    fun ensureDepth() {
        if (depthState !is DepthState.Idle || hub.depth != null || depthJob?.isActive == true) return
        if ((settings["parallax.ai"] ?: 1f) <= .5f || !isEffectOn("parallax")) return
        if (meta == null || masks == null) return
        computeQuickDepth(auto = true)
    }

    /**
     * «2.5D rápido» SIN IA: al instante, sin descarga. v44.10: si no hay Proteger, separa solo al
     * personaje del fondo (fondo liso de las esquinas + caras detectadas).
     */
    fun computeQuickDepth(auto: Boolean = false, notice: String? = null) {
        val m = meta ?: return
        val mk = masks ?: return
        if (depthState is DepthState.Running) return
        depthJob?.cancel()
        quickProtectSig = protectSig()
        depthNotice = notice
        depthState = DepthState.Running(if (auto) "Preparando el 2.5D…" else "Calculando 2.5D rápido…")
        depthJob = viewModelScope.launch {
            try {
                val prot = if (maskHasPaint(0)) withContext(Dispatchers.Default) { layerBytes(mk, 0) } else null
                val faces: List<FloatArray> = if (prot != null) emptyList() else try {
                    val small = withContext(Dispatchers.IO) { BitmapUtil.decodeAtMost(m.imageFile, 384) }
                    val sw = small.width; val sh = small.height
                    val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh); small.recycle() }
                    detectFaces(px, sw, sh).first.map { floatArrayOf(it.x.toFloat() / sw, it.y.toFloat() / sh, it.w.toFloat() / sw, it.h.toFloat() / sh) }
                } catch (_: Throwable) { emptyList() }
                val r = withContext(Dispatchers.Default) { depthEstimator.estimateQuick(m.dir, m.imageFile, prot, mk.w, mk.h, faces) }
                if (meta?.id != m.id) return@launch
                hub.depth = com.zeta.fondo.engine.DepthMap(r.w, r.h, r.map, r.meta.model)
                depthState = DepthState.Ready(r.meta.model, r.meta.ms, r.meta.contrast, false)
                if ((settings["parallax.ai"] ?: 1f) <= .5f) setSetting("parallax.ai", 1f)
                pushParams()
            } catch (e: kotlinx.coroutines.CancellationException) { depthState = DepthState.Idle; throw e
            } catch (e: Throwable) {
                depthState = DepthState.Failed("No se pudo calcular el 2.5D rápido (${e.javaClass.simpleName}).")
            }
        }
    }

    private var quickProtectSig = -1
    private var quickRefreshJob: Job? = null
    private fun protectSig(): Int { val mk = masks ?: return 0; return synchronized(mk.lock) { mk.strokes.getOrNull(0)?.size ?: 0 } }

    /** v44.10 · Si el mapa es el rápido y cambia Proteger, se rehace solo (es instantáneo). */
    private fun scheduleQuickDepthRefresh() {
        if ((depthState as? DepthState.Ready)?.model != com.zeta.fondo.engine.DepthEstimator.QUICK) return
        if (protectSig() == quickProtectSig) return
        quickRefreshJob?.cancel()
        quickRefreshJob = viewModelScope.launch {
            kotlinx.coroutines.delay(900)
            if ((depthState as? DepthState.Ready)?.model == com.zeta.fondo.engine.DepthEstimator.QUICK && protectSig() != quickProtectSig)
                computeQuickDepth(auto = true)
        }
    }

    fun cancelDepth() {
        depthJob?.cancel()
        depthJob = null
    }

    /** Borra el mapa guardado (vuelve al 2.5D manual hasta recalcular). */
    fun discardDepth() {
        val m = meta ?: return
        resetDepth()
        depthNotice = null
        // Sin esto, el 2.5D automático volvería a calcularse al instante.
        setSetting("parallax.ai", 0f)
        viewModelScope.launch(Dispatchers.IO) { com.zeta.fondo.engine.DepthEstimator.clear(m.dir) }
        pushParams()
    }

    fun toggleDepthView() {
        depthView = !depthView
        hub.depthView = depthView
    }

    // ============================================================ v46 · Recorte de personaje y relleno del fondo

    sealed class AiJob {
        object Idle : AiJob()
        data class Running(val stage: String) : AiJob()
        data class Done(val text: String, val warning: String? = null) : AiJob()
        data class Failed(val reason: String) : AiJob()
    }
    var plateState by mutableStateOf<AiJob>(AiJob.Idle)
        private set
    private var plateJob: Job? = null
    /** Versión de la capa Proteger con la que se hizo la placa (para avisar si cambió). */
    private var plateProtectCrc = 0L

    private fun maskImage(dir: java.io.File, name: String): Bitmap? = try {
        BitmapFactory.decodeFile(java.io.File(dir, name).path)
    } catch (_: Throwable) { null }

    /** Bytes (0..255) de una capa de máscara, sin el relleno de fila. */
    private fun layerBytes(mk: MaskLayers, layer: Int): ByteArray = synchronized(mk.lock) {
        val bmp = mk.bitmaps[layer]
        val row = bmp.rowBytes
        val buf = java.nio.ByteBuffer.allocate(row * bmp.height)
        bmp.copyPixelsToBuffer(buf)
        val raw = buf.array()
        ByteArray(mk.w * mk.h) { i -> raw[(i / mk.w) * row + (i % mk.w)] }
    }

    private fun crcOf(b: ByteArray): Long = java.util.zip.CRC32().also { it.update(b) }.value


    /** ¿Cambió Proteger desde que se hizo la placa? */
    val plateStale: Boolean get() {
        val mk = masks ?: return false
        if (hub.plate == null) return false
        maskRevision // se relee al cambiar los trazos
        return crcOf(layerBytes(mk, 0)) != plateProtectCrc
    }

    /**
     * Fondo sin el personaje para el 2.5D: el hueco de Proteger se rellena (MI-GAN si el
     * usuario lo importó; si no, relleno pull-push). Se guarda en el proyecto.
     */
    fun computePlate() {
        val m = meta ?: return
        val mk = masks ?: return
        if (!maskHasPaint(0)) { plateState = AiJob.Failed("Primero recorta o pinta al personaje en Proteger."); return }
        plateJob?.cancel()
        plateState = AiJob.Running("Rellenando el fondo…")
        plateJob = viewModelScope.launch {
            try {
                val hole = withContext(Dispatchers.Default) { layerBytes(mk, 0) }
                val src = withContext(Dispatchers.IO) {
                    val b = BitmapUtil.decodeAtMost(m.imageFile, maxOf(mk.w, mk.h) * 2)
                    Bitmap.createScaledBitmap(b, mk.w, mk.h, true).also { if (it !== b) b.recycle() }
                }
                val t0 = System.nanoTime()
                val (argb, ai) = try { com.zeta.fondo.engine.BackgroundFill.fill(src, hole) } finally { src.recycle() }
                if (meta?.id != m.id) return@launch
                val crc = crcOf(hole)
                withContext(Dispatchers.IO) { savePlate(m.dir, mk.w, mk.h, argb, crc, ai) }
                plateProtectCrc = crc
                hub.plate = com.zeta.fondo.engine.PlateMap(mk.w, mk.h, argb, ai)
                val ms = (System.nanoTime() - t0) / 1_000_000
                plateState = AiJob.Done("Fondo rellenado en ${ms} ms.")
                pushParams()
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (plateState is AiJob.Running) plateState = AiJob.Idle
                throw e
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.error("cutout", "relleno falló", e)
                plateState = AiJob.Failed("No se pudo rellenar el fondo (${e.javaClass.simpleName}).")
            }
        }
    }

    private var plateRefreshJob: Job? = null

    /**
     * v49: si cambia Proteger y ya había placa de fondo, se rehace sola (con una pausa para no
     * repetirla en cada trazo). Así una placa vieja nunca «borra» al personaje.
     */
    private fun schedulePlateRefresh() {
        if (hub.plate == null) return
        plateRefreshJob?.cancel()
        plateRefreshJob = viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            if (hub.plate == null || plateState is AiJob.Running) return@launch
            val stale = withContext(Dispatchers.Default) { plateStale }
            if (!stale) return@launch
            if (!maskHasPaint(0)) { hub.plate = null; plateState = AiJob.Idle; pushParams(); return@launch }
            computePlate()
        }
    }

    /** Al abrir el 2.5D: si hay personaje en Proteger y no hay placa, se hace (es rápido). */
    fun ensurePlate() {
        if (hub.plate == null && plateState is AiJob.Idle && maskHasPaint(0) && isEffectOn("parallax") &&
            (settings["parallax.plate"] ?: 1f) > .5f) computePlate()
    }

    private fun savePlate(dir: java.io.File, w: Int, h: Int, argb: IntArray, crc: Long, ai: Boolean) {
        val bmp = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        val tmp = java.io.File(dir, "$PLATE_FILE.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        tmp.renameTo(java.io.File(dir, PLATE_FILE))
        java.io.File(dir, PLATE_META).writeText("version=1\nw=$w\nh=$h\ncrc=$crc\nai=$ai\n")
    }

    /** Al abrir el proyecto: placa guardada si corresponde a la Proteger actual. */
    private fun loadPlateCache(m: com.zeta.fondo.model.ProjectMeta, mk: MaskLayers) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val metaF = java.io.File(m.dir, PLATE_META)
                    val png = java.io.File(m.dir, PLATE_FILE)
                    if (!metaF.isFile || !png.isFile) return@withContext null
                    val kv = metaF.readLines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
                    val w = kv["w"]?.toIntOrNull() ?: return@withContext null
                    val h = kv["h"]?.toIntOrNull() ?: return@withContext null
                    val crc = kv["crc"]?.toLongOrNull() ?: return@withContext null
                    if (w != mk.w || h != mk.h || crcOf(layerBytes(mk, 0)) != crc) return@withContext null
                    val bmp = BitmapFactory.decodeFile(png.path) ?: return@withContext null
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    bmp.recycle()
                    Triple(com.zeta.fondo.engine.PlateMap(w, h, px, kv["ai"] == "true"), crc, kv["ai"] == "true")
                } catch (_: Throwable) { null }
            }
            if (r != null && meta?.id == m.id) {
                hub.plate = r.first
                plateProtectCrc = r.second
                plateState = AiJob.Done("Fondo rellenado (guardado).")
            }
        }
    }

    private fun resetCutout() {
        plateJob?.cancel(); plateJob = null
        hub.plate = null
        plateState = AiJob.Idle
        plateProtectCrc = 0L
    }


    // ============================================================ v47 · Caras, ojos, cielo, agua, sugerencias y mejora

    private val faceDetector by lazy {
        try {
            com.zeta.fondo.engine.AnimeFaceDetector.parse(
                getApplication<Application>().assets.open("detect/lbpcascade_animeface.xml").bufferedReader().use { it.readText() })
        } catch (e: Throwable) { com.zeta.fondo.diag.ZetaLog.error("faces", "cascada no disponible", e); null }
    }
    var regionState by mutableStateOf<AiJob>(AiJob.Idle)
        private set
    /** Efecto que recibirá la región del próximo toque en la imagen («Tocar para seleccionar»). */
    var tapSelectFor by mutableStateOf<String?>(null)
        private set
    var tapTolerance by mutableFloatStateOf(.5f)
    var sceneSuggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Imagen del proyecto a tamaño de máscara (ARGB) para las herramientas de región. */
    private suspend fun imageAtMaskSize(m: com.zeta.fondo.model.ProjectMeta, mk: MaskLayers): IntArray = withContext(Dispatchers.IO) {
        val b = BitmapUtil.decodeAtMost(m.imageFile, maxOf(mk.w, mk.h) * 2)
        val s = Bitmap.createScaledBitmap(b, mk.w, mk.h, true)
        if (s !== b) b.recycle()
        IntArray(mk.w * mk.h).also { s.getPixels(it, 0, mk.w, 0, 0, mk.w, mk.h); s.recycle() }
    }

    /** Mapa de profundidad (si lo hay) reescalado al tamaño de las máscaras. */
    private fun depthAtMaskSize(mk: MaskLayers): ByteArray? = hub.depth?.let {
        com.zeta.fondo.engine.DepthMath.resize(it.map, it.w, it.h, mk.w, mk.h)
    }

    /**
     * Pone [alpha] (0..255, tamaño de capa) como zona PROPIA del efecto [id], igual que si se
     * hubiera pintado: se deshace con ↶ y se repasa con el pincel. Devuelve false si no cabe.
     */
    private suspend fun applyAiZone(id: String, alpha: ByteArray, tag: String): Boolean {
        val m = meta ?: return false
        val mk = masks ?: return false
        var layer = (settings["$id.ownedMask"] ?: -1f).toInt()
        if (layer !in 3 until mk.count) {
            layer = mk.addLayer(Effects.all.firstOrNull { it.id == id }?.title ?: id)
            if (layer < 0) { message = "Límite de nueve zonas independientes por proyecto."; return false }
            setSetting("$id.ownedMask", layer.toFloat())
        }
        val name = "mask_${tag}_${System.currentTimeMillis()}.png"
        val bmp = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(IntArray(mk.w * mk.h) { i -> ((alpha[i].toInt() and 0xFF) shl 24) or 0xFFFFFF }, mk.w, mk.h, Bitmap.Config.ARGB_8888)
        }
        withContext(Dispatchers.IO) {
            val tmp = java.io.File(m.dir, "$name.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(java.io.File(m.dir, name))
        }
        mk.putImage(name, bmp)
        val stroke = MaskStroke(layer, 0f, 0f, false, image = name)
        mk.addStroke(stroke)
        pushUndo(UndoAction.AddStroke(stroke))
        maskNames = mk.names.toList()
        setEffectMask(id, layer)
        scheduleAutosave()
        refreshEffectBoxes()
        return true
    }

    // ============================================================ v44.16 · Proteger automático

    private var autoProtectJob: Job? = null
    /** Proyecto en el que ya se intentó (una vez por proyecto y sesión: no insiste si falla). */
    private var autoProtectTried: String? = null

    /** ¿Algún efecto encendido necesita la silueta del personaje (Proteger)? */
    /**
     * v44.25 · Efectos que NO funcionan sin personaje (luz de contorno, aura alrededor). Antes
     * también contaba cualquier efecto «Detrás»: en un paisaje se creaba sola una máscara falsa
     * que tapaba la explosión, la niebla, las nubes… Ahora «Detrás» nunca la crea a escondidas.
     */
    private fun needsCharacter(): Boolean {
        if (isEffectOn("rimlight")) return true
        if (isEffectOn("energy") && (settings["energy.shape"] ?: 0f).toInt() in setOf(1, 3)) return true
        return false
    }

    /**
     * Antes había que pintar Proteger a mano o el efecto no hacía nada. Ahora, la primera vez
     * que hace falta, se detecta al personaje (fondo liso + caras) y se pone en Proteger como
     * una capa más: se ve, se corrige con el pincel y se deshace con ↶.
     */
    private fun ensureAutoProtect() {
        val m = meta ?: return
        val mk = masks ?: return
        if (autoProtectTried == m.id || autoProtectJob?.isActive == true) return
        if ((settings["protect.auto"] ?: 0f) > .5f || maskHasPaint(0) || !needsCharacter()) return
        autoProtectTried = m.id
        autoProtectJob = detectCharacterIntoProtect(m, mk, null)
    }

    /**
     * Busca al personaje (sin IA: fondo liso, color, contornos y caras) y lo pone en Proteger como
     * un trazo más (se ve, se corrige con el pincel y se deshace con ↶). [done] recibe si lo halló.
     */
    private fun detectCharacterIntoProtect(m: com.zeta.fondo.model.ProjectMeta, mk: MaskLayers,
                                           done: ((Boolean) -> Unit)?): Job =
        viewModelScope.launch {
            try {
                val small = withContext(Dispatchers.IO) { BitmapUtil.decodeAtMost(m.imageFile, 384) }
                val sw = small.width; val sh = small.height
                val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh); small.recycle() }
                val faces = try {
                    detectFaces(px, sw, sh).first.map { floatArrayOf(it.x.toFloat() / sw, it.y.toFloat() / sh, it.w.toFloat() / sw, it.h.toFloat() / sh) }
                } catch (_: Throwable) { emptyList() }
                val alpha = withContext(Dispatchers.Default) {
                    com.zeta.fondo.engine.DepthMath.autoProtect(px, sw, sh, faces)
                        ?.let { com.zeta.fondo.engine.DepthMath.resize(it, sw, sh, mk.w, mk.h) }
                }
                if (meta?.id != m.id) { done?.invoke(false); return@launch }
                if (maskHasPaint(0)) { done?.invoke(true); return@launch }
                if (alpha == null) {
                    if (done != null) done(false) else message = "No se pudo separar al personaje solo: píntalo en Proteger."
                    return@launch
                }
                // v44.25: «mask_char_» (pedida por ti o por la luz de contorno / el aura). Solo las
                // «mask_auto_» de versiones anteriores se quitan al abrir (dropStaleAutoProtect).
                val name = "mask_char_${System.currentTimeMillis()}.png"
                val bmp = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(IntArray(mk.w * mk.h) { i -> ((alpha[i].toInt() and 0xFF) shl 24) or 0xFFFFFF }, mk.w, mk.h, Bitmap.Config.ARGB_8888)
                }
                withContext(Dispatchers.IO) {
                    val tmp = java.io.File(m.dir, "$name.tmp")
                    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    tmp.renameTo(java.io.File(m.dir, name))
                }
                mk.putImage(name, bmp)
                val stroke = MaskStroke(0, 0f, 0f, false, image = name)
                mk.addStroke(stroke)
                pushUndo(UndoAction.AddStroke(stroke))
                settings = settings + ("protect.auto" to 1f)
                scheduleAutosave()
                pushParams()
                if (done != null) done(true)
                else message = "Personaje detectado solo en Proteger. Si falta algo, corrígelo con el pincel."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.warn("protect", "automático: ${e.javaClass.simpleName} ${e.message}")
                done?.invoke(false)
            }
        }

    /**
     * v44.25 · Proyectos de versiones anteriores: si Proteger solo tiene la máscara que creó la app
     * (sin ningún trazo tuyo) y ningún efecto necesita personaje, se quita: era la que tapaba la
     * explosión y los efectos «Detrás» en los fondos. ↶ la devuelve. Lo pintado a mano no se toca,
     * ni las máscaras nuevas («mask_char_»: las que pides con «Buscar personaje solo»).
     */
    private fun dropStaleAutoProtect(): Boolean {
        val mk = masks ?: return false
        val list = synchronized(mk.lock) { mk.strokes.getOrNull(0)?.toList() } ?: return false
        if (list.isEmpty() || list.any { it.image?.startsWith("mask_auto_") != true }) return false
        if (needsCharacter()) return false
        list.forEach { mk.removeStroke(it) }
        settings = settings - "protect.auto"
        pushUndo(UndoAction.RemoveStrokes(list))
        return true
    }

    // ------------------------------------------------------------ v44.25 · «Detrás» con pregunta

    /** Efecto que pidió ir «Detrás» sin personaje pintado: la interfaz pregunta qué hacer. */
    var behindAsk by mutableStateOf<String?>(null)
        private set

    /** «Buscar personaje solo»: lo busca ahora; si no hay, el efecto sigue delante. */
    fun behindFindCharacter() {
        val id = behindAsk ?: return
        behindAsk = null
        val m = meta ?: return
        val mk = masks ?: return
        if (autoProtectJob?.isActive == true) { showHint("Ya estoy buscando al personaje…", 1500L); return }
        setSetting("$id.behind", 1f)
        // Sin «busy»: si algo se cancela, la pantalla nunca queda bloqueada.
        showHint("Buscando al personaje…", 1500L)
        autoProtectJob = detectCharacterIntoProtect(m, mk) { found ->
            if (meta?.id != m.id) return@detectCharacterIntoProtect
            if (found) message = "Personaje marcado en Proteger: el efecto pasa por detrás. Si falta algo, corrígelo con el pincel."
            else {
                setSetting("$id.behind", 0f)
                message = "No encontré un personaje en esta imagen: el efecto sigue delante. Si hay uno, píntalo en Proteger."
            }
        }
    }

    /** «Pintarlo yo»: el efecto queda detrás y se abre Proteger con el pincel. */
    fun behindPaintMyself() {
        val id = behindAsk ?: return
        behindAsk = null
        setSetting("$id.behind", 1f)
        openCategory(EditorTab.MASKS)
        showHint("Pinta al personaje: el efecto pasará por detrás de lo que pintes.", 2600L)
    }

    /** «Dejarlo delante». */
    fun behindKeepFront() { behindAsk = null }

    private val autoZoneTried = HashSet<String>()

    /**
     * v44.16 · Efectos que solo actúan en una zona (ojos, cielo, agua…): al encenderlos sin zona
     * pintada, la zona se propone sola UNA vez (la misma detección del botón «Detectar»).
     * Antes el efecto se encendía y no hacía nada hasta pintar.
     */
    private fun ensureAutoZones() {
        val m = meta ?: return
        if (masks == null || regionState is AiJob.Running) return
        for (id in autoZoneEffects) {
            val key = m.id + ":" + id
            if (key in autoZoneTried || !isEffectOn(id) || zoneProblem(id) == null) continue
            autoZoneTried += key
            when (id) {
                "eyes", "blink" -> aiFaceZone(id)
                "sky" -> aiSkyOrWater(id, false)
                else -> aiSkyOrWater(id, true)
            }
            return
        }
    }
    private val autoZoneEffects = listOf("eyes", "blink", "sky", "water", "reflect", "shore", "ripple", "glint", "waterfall")

    /**
     * v44.16 · Ajustes que recibe el render (no cambia lo guardado): el fuego sin zona pintada
     * arde a lo largo del borde inferior, como en su miniatura, en vez de no hacer nada.
     */
    // ============================================================ v44.22 · preparar efectos por adelantado
    private var warmJob: kotlinx.coroutines.Job? = null

    /**
     * v44.22 · Los efectos que se ven en el catálogo se preparan (compilan) en segundo plano,
     * sin ocupar la GPU ni la memoria: al tocar uno aparece casi al instante (antes tardaba
     * 1–3 s la primera vez y se veía el efecto anterior). En orden de aparición; pocos en
     * teléfonos modestos.
     */
    fun prewarmEffects(ids: List<String>) {
        warmJob?.cancel()
        val base = renderSettings()
        val asp = aspect
        val loop = exportSettings.loopSec.toFloat()
        val clips = effectClips.toList()
        val limit = if (lowResourceDevice) 6 else 10
        warmJob = viewModelScope.launch(Dispatchers.Default) {
            val out = ArrayList<Pair<String, Set<String>>>()
            for (id in ids) {
                if (out.size >= limit) break
                if (id in Effects.globalSettings || id == "elements") continue
                val def = Effects.all.firstOrNull { it.id == id } ?: continue
                if (!def.toggleable || (base["$id.on"] ?: 0f) > .5f) continue
                var s = base + ("$id.on" to 1f)
                if (!s.containsKey("$id.mask")) s = s + ("$id.mask" to -2f)
                val p = try { RenderParams.from(s, asp, loop, clips) } catch (_: Throwable) { continue }
                if (out.none { it.first == p.variantKey }) out += p.variantKey to p.features
                kotlinx.coroutines.yield()
            }
            hub.warmVariants = out
        }
    }

    private fun renderSettings(): Map<String, Float> {
        val s = settings
        if (isEffectOn("flame") && (s["flame.mask"] ?: -1f).toInt() == -1 && !maskHasPaint(2)) return s + ("flame.mask" to -2f)
        return s
    }

    /** Caras anime: primero con el criterio del README (5 vecinos); si no hay, más permisivo. */
    private suspend fun detectFaces(px: IntArray, w: Int, h: Int): Pair<List<com.zeta.fondo.engine.AnimeFaceDetector.Face>, Boolean> =
        withContext(Dispatchers.Default) {
            val det = faceDetector ?: return@withContext emptyList<com.zeta.fondo.engine.AnimeFaceDetector.Face>() to false
            val gray = com.zeta.fondo.engine.FaceRegions.gray(px)
            for (n in intArrayOf(5, 3, 2)) {
                val f = det.detect(gray, w, h, minNeighbors = n)
                if (f.isNotEmpty()) return@withContext f.take(3) to (n < 5)
            }
            emptyList<com.zeta.fondo.engine.AnimeFaceDetector.Face>() to false
        }

    /**
     * Propone los ojos (efectos «eyes» o «blink») o el pecho para la respiración («zonemotion»
     * en estilo Respiración) a partir de las caras detectadas. La cara nunca se deforma: solo
     * se crea la ZONA del efecto, editable con el pincel.
     */
    fun aiFaceZone(id: String) {
        val m = meta ?: return
        val mk = masks ?: return
        if (regionState is AiJob.Running) return
        regionState = AiJob.Running("Buscando caras…")
        viewModelScope.launch {
            try {
                val px = imageAtMaskSize(m, mk)
                val (faces, weak) = detectFaces(px, mk.w, mk.h)
                if (faces.isEmpty()) {
                    regionState = AiJob.Failed("No encontré ninguna cara (habitual en manga con tramas o caras de perfil). Pinta la zona con el pincel.")
                    return@launch
                }
                val gray = com.zeta.fondo.engine.FaceRegions.gray(px)
                val alpha = withContext(Dispatchers.Default) {
                    if (id == "zonemotion") {
                        val masksList = faces.map { com.zeta.fondo.engine.FaceRegions.rectMask(com.zeta.fondo.engine.FaceRegions.breathing(it, mk.w, mk.h), mk.w, mk.h) }
                        ByteArray(mk.w * mk.h) { i -> masksList.maxOf { it[i].toInt() and 0xFF }.toByte() }
                    } else com.zeta.fondo.engine.FaceRegions.ellipseMask(
                        faces.flatMap { com.zeta.fondo.engine.FaceRegions.eyes(gray, mk.w, mk.h, it) }, mk.w, mk.h)
                }
                if (!isEffectOn(id)) toggleEffect(id)
                if (id == "zonemotion") setSetting("zonemotion.variant", 2f)
                if (applyAiZone(id, alpha, if (id == "zonemotion") "pecho" else "ojos")) {
                    val what = if (id == "zonemotion") "pecho" else "ojos"
                    regionState = AiJob.Done("${faces.size} cara(s): zona de $what propuesta. Repásala con el pincel si hace falta.",
                        if (weak) "Detección dudosa: revisa que la zona esté sobre la cara correcta." else null)
                }
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.error("faces", "detección falló", e)
                regionState = AiJob.Failed("No se pudo buscar caras (${e.javaClass.simpleName}).")
            }
        }
    }

    /** Cielo o agua automáticos como zona del efecto [id] (nunca sobre Proteger). */
    fun aiSkyOrWater(id: String, water: Boolean) {
        val m = meta ?: return
        val mk = masks ?: return
        if (regionState is AiJob.Running) return
        regionState = AiJob.Running(if (water) "Buscando el agua…" else "Buscando el cielo…")
        viewModelScope.launch {
            try {
                val px = imageAtMaskSize(m, mk)
                val prot = withContext(Dispatchers.Default) { layerBytes(mk, 0) }
                val depth = depthAtMaskSize(mk)
                val r = withContext(Dispatchers.Default) {
                    if (water) com.zeta.fondo.engine.RegionTools.autoWater(px, mk.w, mk.h, depth, prot)
                    else com.zeta.fondo.engine.RegionTools.autoSky(px, mk.w, mk.h, depth, prot)
                }
                if (r == null) {
                    regionState = AiJob.Failed(if (water) "No encontré agua clara. Usa «Tocar para seleccionar» o el pincel." else "No encontré cielo. Usa «Tocar para seleccionar» o el pincel.")
                    return@launch
                }
                if (applyAiZone(id, r, if (water) "agua" else "cielo"))
                    regionState = AiJob.Done("${if (water) "Agua" else "Cielo"}: ${(com.zeta.fondo.engine.RegionTools.coverage(r) * 100).toInt()} % de la imagen. Repásalo con el pincel si hace falta.")
            } catch (e: Throwable) {
                regionState = AiJob.Failed("No se pudo seleccionar (${e.javaClass.simpleName}).")
            }
        }
    }

    /** Activa «Tocar para seleccionar»: el siguiente toque en la imagen crea la zona del efecto. */
    fun startTapSelect(id: String) {
        tapSelectFor = id
        if (playing) togglePlay()
        tool = Tool.PAN
        showHint("Toca la zona de la imagen (cielo, agua, pared…). Se selecciona lo parecido y conectado.", 3600L)
    }

    fun cancelTapSelect() { tapSelectFor = null }

    /** Toque en la imagen con «Tocar para seleccionar» activo. [uv] en 0..1 de la imagen. */
    fun tapSelectAt(uv: PointN) {
        val id = tapSelectFor ?: return
        tapSelectFor = null
        val m = meta ?: return
        val mk = masks ?: return
        regionState = AiJob.Running("Seleccionando…")
        viewModelScope.launch {
            try {
                val px = imageAtMaskSize(m, mk)
                val prot = withContext(Dispatchers.Default) { layerBytes(mk, 0) }
                val depth = depthAtMaskSize(mk)
                val x = (uv.x * mk.w).toInt().coerceIn(0, mk.w - 1); val y = (uv.y * mk.h).toInt().coerceIn(0, mk.h - 1)
                if ((prot[y * mk.w + x].toInt() and 0xFF) >= 128) {
                    regionState = AiJob.Failed("Ese punto está en Proteger (el personaje). Toca el fondo.")
                    return@launch
                }
                val r = withContext(Dispatchers.Default) {
                    com.zeta.fondo.engine.RegionTools.grow(px, mk.w, mk.h, x, y, tapTolerance, depth, prot)
                }
                val cov = com.zeta.fondo.engine.RegionTools.coverage(r)
                if (cov < .002f) { regionState = AiJob.Failed("Zona demasiado pequeña: sube la tolerancia o usa el pincel."); return@launch }
                if (applyAiZone(id, r, "toque")) regionState = AiJob.Done("Zona seleccionada (${(cov * 100).toInt()} % de la imagen). Toca otra vez para añadir más.")
            } catch (e: Throwable) {
                regionState = AiJob.Failed("No se pudo seleccionar (${e.javaClass.simpleName}).")
            }
        }
    }

    /** Sugerencias de escenas por reglas sencillas (color, cielo, agua, caras). Sin IA pesada. */
    fun computeSceneSuggestions() {
        val m = meta ?: return
        val mk = masks ?: return
        viewModelScope.launch {
            try {
                val names = withContext(Dispatchers.Default) {
                    val b = BitmapUtil.decodeAtMost(m.imageFile, 256)
                    val s = Bitmap.createScaledBitmap(b, 128, 128, true)
                    if (s !== b) b.recycle()
                    val px = IntArray(128 * 128).also { s.getPixels(it, 0, 128, 0, 0, 128, 128); s.recycle() }
                    val prot = layerBytes(mk, 0).let { com.zeta.fondo.engine.DepthMath.resize(it, mk.w, mk.h, 128, 128) }
                    val faces = faceDetector?.detect(com.zeta.fondo.engine.FaceRegions.gray(px), 128, 128)?.size ?: 0
                    com.zeta.fondo.engine.RegionTools.suggest(com.zeta.fondo.engine.RegionTools.traits(px, 128, 128, faces, prot))
                }
                sceneSuggestions = names.filter { n -> com.zeta.fondo.model.Presets.scenes.any { it.name == n } }
            } catch (_: Throwable) { sceneSuggestions = emptyList() }
        }
    }

    // ---- Mejorar imagen (superresolución, opcional y reversible)
    private val superRes by lazy { com.zeta.fondo.engine.SuperResolver(getApplication()) }
    val superResAvailable: Boolean by lazy { superRes.available }
    var srState by mutableStateOf<AiJob>(AiJob.Idle)
        private set
    var srProgress by mutableFloatStateOf(0f)
        private set
    private var srJob: Job? = null
    /**
     * v44.18 · Antes / después a tamaño de vista previa (misma imagen entera, mismo tamaño):
     * se comparan en el lienzo con una línea que se arrastra, como en Ibis Paint.
     */
    var srCompare by mutableStateOf<Pair<Bitmap, Bitmap>?>(null)
        private set
    /** El panel «Mejorar imagen» está abierto: el lienzo muestra el antes / después. */
    var srPanelOpen by mutableStateOf(false)
    /** Tamaño elegido: 1 = Original (mismo tamaño, más nítida), 2 = x2, 4 = x4. */
    var srScale by mutableStateOf(prefs.getInt("srScale", 2))
        private set
    fun chooseSrScale(s: Int) { srScale = s; prefs.edit().putInt("srScale", s).apply() }

    /** Tamaño que tendrá la imagen mejorada con [scale] (el lado largo no pasa de 4096 px). */
    fun srOutputSize(scale: Int): Pair<Int, Int>? {
        val m = meta ?: return null
        val k = com.zeta.fondo.engine.TileUpscale.targetScale(m.w, m.h, scale)
        return (m.w * k).toInt() to (m.h * k).toInt()
    }

    fun srFile(): java.io.File? = meta?.let { java.io.File(it.dir, com.zeta.fondo.engine.SuperResolver.FILE) }?.takeIf { it.isFile }
    /** ¿Se exporta con la imagen mejorada? (el original nunca se toca) */
    val srEnabled: Boolean get() = (settings["image.sr"] ?: 0f) > .5f && srFile() != null

    fun enhanceImage(scale: Int = srScale) {
        val m = meta ?: return
        if (srState is AiJob.Running) return
        if (!superResAvailable) { srState = AiJob.Failed("Este APK no incluye el modelo de mejora."); return }
        // v44.25: nunca a la vez que una exportación o una captura (memoria).
        if (ExportRunner.running || exportState is ExportState.Running) { message = "Espera a que termine la exportación."; return }
        if (capturing) { message = "Espera a que se guarde la captura."; return }
        srState = AiJob.Running("Mejorando la imagen…"); srProgress = 0f
        srJob = viewModelScope.launch {
            try {
                val src = withContext(Dispatchers.IO) { BitmapUtil.decodeAtMost(m.imageFile, 4096) }
                val t0 = System.nanoTime()
                val out = java.io.File(m.dir, com.zeta.fondo.engine.SuperResolver.FILE)
                val (w, h) = try {
                    superRes.enhance(src, out, scale) { p -> main.post { srProgress = p } }
                } finally { src.recycle() }
                setSetting("image.sr", 1f)
                refreshPreviewImage()
                buildSrCompare(m)
                val secs = (System.nanoTime() - t0) / 1_000_000_000f
                srState = AiJob.Done("Mejorada: ${w} × $h en ${String.format(java.util.Locale.US, "%.0f", secs)} s. Ya se usa en la vista previa y al exportar.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                srState = AiJob.Idle; throw e
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.error("superres", "mejora falló", e)
                srState = AiJob.Failed("No se pudo mejorar (${e.javaClass.simpleName}). Se sigue usando el original.")
            }
        }
    }

    fun cancelEnhance() { srJob?.cancel(); srJob = null }

    fun setUseEnhanced(on: Boolean) { setSetting("image.sr", if (on) 1f else 0f); refreshPreviewImage() }

    /** Imagen de edición original (sin mejorar), para volver a ella. */
    private var basePreview: Bitmap? = null
    private var srPreviewJob: Job? = null

    /**
     * v44.10 · La vista previa usa la imagen MEJORADA cuando «Mejorar imagen» está activo
     * (antes solo se aplicaba al exportar y no se veía ninguna diferencia al editar).
     * Se decodifica al tamaño de edición del teléfono: no gasta más RAM que el original.
     */
    private fun refreshPreviewImage() {
        val m = meta ?: return
        srPreviewJob?.cancel()
        val f = srFile()
        if (!srEnabled || f == null) { basePreview?.let { if (hub.image !== it) hub.image = it }; return }
        srPreviewJob = viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { BitmapUtil.decodePreview(f, previewMaxForDevice()) }
                if (meta?.id == m.id && srEnabled) hub.image = bmp
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.warn("superres", "vista previa mejorada: ${e.message}")
            }
        }
    }

    /** Borra la imagen mejorada (vuelve al original). */
    fun discardEnhanced() {
        val f = srFile()
        setSetting("image.sr", 0f)
        basePreview?.let { hub.image = it }
        srPreviewJob?.cancel()
        srCompare = null
        srState = AiJob.Idle
        viewModelScope.launch(Dispatchers.IO) { f?.delete() }
    }

    private suspend fun buildSrCompare(m: com.zeta.fondo.model.ProjectMeta) {
        val sr = srFile() ?: return
        srCompare = withContext(Dispatchers.IO) {
            try {
                val max = previewMaxForDevice()
                val before = BitmapUtil.decodePreview(m.imageFile, max)
                val after0 = BitmapUtil.decodePreview(sr, max)
                // Mismo tamaño exacto: la línea del medio separa la MISMA zona.
                val after = if (after0.width == before.width && after0.height == before.height) after0
                    else Bitmap.createScaledBitmap(after0, before.width, before.height, true).also { if (it !== after0) after0.recycle() }
                before to after
            } catch (_: Throwable) { null }
        }
    }

    /** Al abrir el panel: prepara el antes / después si ya había una imagen mejorada. */
    fun ensureSrCompare() {
        val m = meta ?: return
        if (srCompare != null || srFile() == null) return
        viewModelScope.launch { buildSrCompare(m) }
    }

    /** Imagen que usa la exportación: la mejorada si está activa; si no, el original. */
    private fun exportImage(m: com.zeta.fondo.model.ProjectMeta): Triple<java.io.File, Int, Int> {
        val f = srFile()
        if (srEnabled && f != null) {
            val (w, h) = BitmapUtil.bounds(f)
            if (w > 0 && h > 0) return Triple(f, w, h)
        }
        return Triple(m.imageFile, m.w, m.h)
    }

    /**
     * Motivo por el que un efecto activo NO se verá (le falta su zona pintada) o null.
     * Así la app no aparenta aplicar un efecto que en realidad no actúa.
     */
    fun zoneProblem(id: String): String? {
        if (!isEffectOn(id)) return null
        val title = Effects.all.firstOrNull { it.id == id }?.title?.substringBefore(" (") ?: id
        if (id == "parallax") return if (!maskHasPaint(1) && !maskHasPaint(2) && !depthUsable && depthState !is DepthState.Running)
            "2.5D: toca «2.5D rápido» en Ajustes o pinta fondo (azul) y primer plano (verde)." else null
        if (id == "energy" && (settings["energy.shape"] ?: 0f).toInt() in setOf(1, 3) && !maskHasPaint(0))
            return "Aura: pinta al personaje en Proteger."
        if (id == "rimlight" && !maskHasPaint(0)) return "Luz de contorno: pinta al personaje en Proteger."
        if (id !in needsZone) return null
        val slot = (settings["$id.mask"] ?: -1f).toInt()
        // v44.16: sin zona, el fuego arde a lo largo del borde inferior (ver renderSettings).
        if (id == "flame" && slot == -1) return null
        val layer = when {
            slot >= 128 -> slot - 128
            slot >= 0 -> slot
            slot == -2 || slot == 126 -> return null
            id in zoneLegacy1 -> 1
            id in zoneLegacy2 -> 2
            else -> return null
        }
        return if (layer < 0 || maskHasPaint(layer) || zoneShapeActive(id)) null
            else "«$title» necesita una zona: elige Círculo, Rectángulo o Pintar zona."
    }

    /** Aviso visible sobre el lienzo (efecto en edición o el primero con problema). */
    val zoneWarning: String?
        get() {
            maskRevision; settings
            if (paintingZone) return null
            val id = editingEffect
            return if (id != null) zoneProblem(id) else Effects.all.firstNotNullOfOrNull { zoneProblem(it.id) }
        }

    fun moveEffectOrigin(p: PointN) {
        val id = activeMotionEffect ?: return
        updateFx(id, mapOf("$id.origin" to 0f, "$id.originX" to p.x, "$id.originY" to p.y))
    }

    fun setMotionOrigin(id: String, origin: Int) {
        val angles = floatArrayOf(Float.NaN, 90f, 270f, 0f, 180f, 45f, 135f, 315f, 225f)
        val update = mapOf("$id.origin" to origin.coerceIn(0, 8).toFloat())
        updateFx(id, update + (if (origin in 1..8) mapOf("$id.direction" to angles[origin]) else emptyMap()))
    }

    fun applyPreset(p: Preset) {
        val beforeClips = effectClips.toList()
        val beforeSettings = settings
        // v44.25: sin personaje en Proteger, los efectos de la escena quedan «Delante» (igual que
        // al activarlos uno a uno); con personaje pintado, van detrás como en la escena.
        val values = if (maskHasPaint(0)) p.values
            else p.values.mapValues { (k, v) -> if (k.endsWith(".behind")) 0f else v }
        settings = settings + values
        autoScene(values.keys.filter { it.endsWith(".on") }.map { it.substringBefore('.') })
        // v44: los sonidos de la escena entran como bloques que cubren todo el vídeo.
        if (p.sounds.isNotEmpty()) {
            val total = exportSettings.loopSec.toFloat()
            var uid = nextClipUid()
            for ((sound, vol) in p.sounds) {
                val id = com.zeta.fondo.model.SoundClips.clipId(sound)
                if (effectClips.any { it.effectId == id }) continue
                effectClips = effectClips + EffectClip(uid++, id, 0f, total, overrides = mapOf("$id.vol" to vol))
            }
            commitTimeline(beforeClips, beforeSettings)
        }
        hub.boostPreview()
        pushParams()
        scheduleAutosave()
        message = "${p.emoji} ${p.name} aplicado"
    }

    /**
     * v24: estilos de un toque para los efectos que la gente usa como familias
     * (viento, lluvia, nieve, hojas y olas). Cambia varios parámetros juntos sin
     * crear otro efecto ni mover el bloque de la línea de tiempo.
     */
    /** Activa una familia visual. Las olas se editan por zona la primera vez para evitar franjas sobre toda la pantalla. */
    fun activateEffectStyle(id: String, style: Int) {
        if (id !in setOf("gust", "shore", "leaves", "rain", "snow")) return
        applyEffectStyle(id, style)
        if (id == "shore" && (!settings.containsKey("$id.mask") || (settings["$id.mask"] ?: -2f).toInt() == -2)) {
            editEffect(id)
            return
        }
        if (!settings.containsKey("$id.mask")) settings = settings + ("$id.mask" to -2f)
        activateOrSelectEffect(id)
        saveProject()
    }

    fun applyEffectStyle(id: String, style: Int) {
        val i = style.coerceIn(0, 3)
        val values: Map<String, Float> = when (id) {
            "gust" -> when (i) {
                0 -> mapOf("gust.int" to .25f, "gust.debris" to .12f, "gust.cycles" to 2f)
                1 -> mapOf("gust.int" to .65f, "gust.debris" to .32f, "gust.cycles" to 3f)
                2 -> mapOf("gust.int" to 1.0f, "gust.debris" to .62f, "gust.cycles" to 5f)
                else -> mapOf("gust.int" to 1.35f, "gust.debris" to .9f, "gust.cycles" to 7f)
            }
            "rain" -> when (i) {
                0 -> mapOf("rain.int" to .30f, "rain.angle" to 5f, "rain.cycles" to 4f)
                1 -> mapOf("rain.int" to .65f, "rain.angle" to 10f, "rain.cycles" to 7f)
                2 -> mapOf("rain.int" to .95f, "rain.angle" to 18f, "rain.cycles" to 11f)
                else -> mapOf("rain.int" to 1.15f, "rain.angle" to 28f, "rain.cycles" to 15f)
            }
            "snow" -> when (i) {
                0 -> mapOf("snow.int" to .35f, "snow.size" to .75f, "snow.cycles" to 1f, "snow.direction" to 90f)
                1 -> mapOf("snow.int" to .70f, "snow.size" to 1.0f, "snow.cycles" to 2f, "snow.direction" to 90f)
                2 -> mapOf("snow.int" to 1.0f, "snow.size" to 1.15f, "snow.cycles" to 4f, "snow.direction" to 105f)
                else -> mapOf("snow.int" to 1.15f, "snow.size" to .70f, "snow.cycles" to 8f, "snow.direction" to 135f)
            }
            "shore" -> when (i) {
                0 -> mapOf("shore.int" to .35f, "shore.freq" to 2f, "shore.cycles" to 1f)
                1 -> mapOf("shore.int" to .70f, "shore.freq" to 3f, "shore.cycles" to 2f)
                2 -> mapOf("shore.int" to 1.05f, "shore.freq" to 5f, "shore.cycles" to 3f)
                else -> mapOf("shore.int" to 1.35f, "shore.freq" to 7f, "shore.cycles" to 5f)
            }
            "leaves" -> emptyMap()
            else -> emptyMap()
        }
        updateFx(id, values + mapOf("$id.variant" to i.toFloat()))
    }

    /**
     * v25: cada material de Movimiento natural trae un comportamiento completo.
     * Conserva la máscara pintada; solo cambia física, fuerza y borde fijo recomendados.
     */
    fun applyNaturalPreset(style: Int) {
        val i = style.coerceIn(0, 5)
        val values = when (i) {
            0 -> mapOf( // Árbol: base firme, ramas suaves y hojas con variación amplia.
                "natural.variant" to 0f, "natural.anchor" to 3f, "natural.int" to .0062f,
                "natural.cycles" to 2f, "natural.flex" to .62f, "natural.variation" to .84f,
                "natural.gust" to .30f, "natural.weight" to 0.60f, "natural.spring" to 0.40f,
                "natural.direction" to 0f)
            1 -> mapOf( // Bandera: borde del poste firme y tela muy flexible.
                "natural.variant" to 1f, "natural.anchor" to 1f, "natural.int" to .0085f,
                "natural.cycles" to 2f, "natural.flex" to .94f, "natural.variation" to .46f,
                "natural.gust" to .44f, "natural.weight" to 0.25f, "natural.spring" to 0.85f,
                "natural.direction" to 0f)
            2 -> mapOf( // Pasto: raíz abajo, grupos pequeños fuera de fase.
                "natural.variant" to 2f, "natural.anchor" to 3f, "natural.int" to .0045f,
                "natural.cycles" to 3f, "natural.flex" to .74f, "natural.variation" to .90f,
                "natural.gust" to .24f, "natural.weight" to 0.30f, "natural.spring" to 0.75f,
                "natural.direction" to 0f)
            3 -> mapOf( // Cortina: fija arriba, ondulación larga.
                "natural.variant" to 3f, "natural.anchor" to 4f, "natural.int" to .0052f,
                "natural.cycles" to 2f, "natural.flex" to .90f, "natural.variation" to .34f,
                "natural.gust" to .18f, "natural.weight" to 0.35f, "natural.spring" to 0.70f,
                "natural.direction" to 0f)
            4 -> mapOf( // Cabello: raíz fija y puntas suaves, sin exagerar.
                "natural.variant" to 4f, "natural.anchor" to 4f, "natural.int" to .0044f,
                "natural.cycles" to 2f, "natural.flex" to .80f, "natural.variation" to .56f,
                "natural.gust" to .20f, "natural.weight" to 0.45f, "natural.spring" to 0.72f,
                "natural.direction" to 0f)
            else -> mapOf( // Ropa: unión al cuerpo firme y extremo libre más pesado.
                "natural.variant" to 5f, "natural.anchor" to 4f, "natural.int" to .0040f,
                "natural.cycles" to 2f, "natural.flex" to .64f, "natural.variation" to .44f,
                "natural.gust" to .20f, "natural.weight" to 0.75f, "natural.spring" to 0.34f,
                "natural.direction" to 0f)
        }
        settings = settings + values
        hub.boostPreview(360)
        pushParams()
    }

    fun clearEffects() {
        settings = Effects.allOff(settings)
        effectClips = emptyList()
        selectedTimelineClip = null
        pushParams()
    }

    fun updateExport(e: ExportSettings) {
        val old = exportSettings
        exportSettings = e
        val cropChanged = old.framing != e.framing || old.cropPos != e.cropPos || old.res != e.res ||
            old.customW != e.customW || old.customH != e.customH || old.cropCx != e.cropCx ||
            old.cropCy != e.cropCy || old.cropZoom != e.cropZoom
        if (cropChanged && !cropEditing) {
            val animate = old.framing != e.framing || old.res != e.res
            resetView(animate)
        }
        if (old.loopSec != e.loopSec) {
            val oldSec = old.loopSec.toFloat()
            val newSec = e.loopSec.toFloat()
            effectClips = effectClips.map { clip ->
                // v42: todo bloque que llegaba al final del vídeo sigue llegando al final
                // (antes solo los que cubrían el vídeo entero): alargar el vídeo alarga sus efectos.
                val reachedEnd = clip.startSec + clip.durationSec >= oldSec - .02f
                when {
                    reachedEnd && clip.startSec <= .01f -> clip.copy(startSec = 0f, durationSec = newSec)
                    reachedEnd && clip.startSec < newSec - .1f -> clip.copy(durationSec = newSec - clip.startSec)
                    else -> EffectTimeline.normalized(clip, newSec)
                }
            }
            hub.params = RenderParams.from(renderSettings(), aspect, newSec, renderClips(), soloActive)
        }
        if (exportState is ExportState.Done || exportState is ExportState.Failed) exportState = ExportState.Idle
        hub.setLoopSec(e.loopSec.toFloat())
        hub.setFrameRate(e.fps)
    }

    // v38: los deslizadores envían decenas de cambios por segundo. Los parámetros del
    // shader se recalculan como mucho una vez por mensaje del hilo principal (≈ un cuadro):
    // la interfaz no se traba y la vista previa siempre recibe el último valor.
    private var paramsPosted = false
    private val paramsTask = Runnable {
        paramsPosted = false
        // v44: durante la prueba de rendimiento la vista previa muestra los efectos de la prueba.
        if (benchmark?.running != true) hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
        // v44.10: activar el 2.5D sin mapa prepara al momento el automático (nunca se queda quieto).
        ensureDepth()
        // v44.16: si un efecto necesita al personaje y Proteger está vacío, se detecta solo.
        ensureAutoProtect()
        ensureAutoZones()
        // v44.10: deshacer/rehacer «Mejorar imagen» también cambia la vista previa.
        val base = basePreview
        if (base != null && srPreviewJob?.isActive != true) {
            val showingSr = hub.image != null && hub.image !== base
            if (showingSr != ((settings["image.sr"] ?: 0f) > .5f)) refreshPreviewImage()
        }
    }
    private fun pushParams() {
        if (layers.isNotEmpty() && hub.layers?.loopSec != exportSettings.loopSec.toFloat()) syncLayerStack()
        hub.setLoopSec(exportSettings.loopSec.toFloat())
        hub.setFrameRate(exportSettings.fps)
        if (!paramsPosted) { paramsPosted = true; main.post(paramsTask) }
    }
    /** Para exportar: asegura que hub.params refleja ya el último ajuste. */
    private fun flushParams() {
        if (paramsPosted) { main.removeCallbacks(paramsTask); paramsTask.run() }
    }

    // ============================================================ v36: zonas, color y modo


    /** Modo fácil: solo los controles principales. Avanzado: todos los parámetros finos. */
    var advancedMode by mutableStateOf(prefs.getBoolean("advanced", false))
        private set

    fun toggleAdvancedMode() {
        advancedMode = !advancedMode
        prefs.edit().putBoolean("advanced", advancedMode).apply()
        message = if (advancedMode) "Modo avanzado" else "Modo fácil"
    }

    private var boxJob: Job? = null

    /**
     * Mide el rectángulo realmente pintado de las zonas de Movimiento natural y Fuego.
     * El shader fija la raíz (o la base de las llamas) en el borde de ESA zona, no en
     * el borde de la imagen completa. Se guarda en los ajustes para que la
     * exportación use exactamente lo mismo que la vista previa.
     */
    private fun refreshEffectBoxes() {
        val mk = masks ?: return
        val snapshot = settings
        boxJob?.cancel()
        boxJob = viewModelScope.launch {
            delay(120L)
            val updates = withContext(Dispatchers.Default) {
                val out = HashMap<String, Float>()
                for (id in listOf("natural", "flame")) {
                    val slot = (snapshot["$id.mask"] ?: -1f).toInt()
                    val layer = when {
                        slot >= 0 -> slot
                        slot == -1 -> 2   // zona verde heredada
                        else -> -1        // toda la imagen
                    }
                    var b = if (layer >= 0) mk.paintedBounds(layer) else null
                    // v44.23: con forma, la caja también cubre el círculo / rectángulo.
                    val sh = com.zeta.fondo.model.ZoneShapes.of(snapshot, id)
                    val owned = (snapshot["$id.ownedMask"] ?: -1f).toInt()
                    if (sh != null && slot == owned && owned >= com.zeta.fondo.model.ZoneShapes.FIRST_OWN) {
                        val sb = com.zeta.fondo.model.ZoneShapes.bounds(sh, aspect)
                        b = if (b == null) sb else floatArrayOf(minOf(b[0], sb[0]), minOf(b[1], sb[1]), maxOf(b[2], sb[2]), maxOf(b[3], sb[3]))
                    }
                    val v = b ?: floatArrayOf(0f, 0f, 0f, 0f)
                    out["$id.boxX0"] = v[0]; out["$id.boxY0"] = v[1]
                    out["$id.boxX1"] = v[2]; out["$id.boxY1"] = v[3]
                }
                out
            }
            if (updates.any { (k, v) -> settings[k] != v }) {
                settings = settings + updates
                pushParams()
            }
        }
    }

    fun tintOf(id: String): Int? {
        if ((fx["$id.tintOn"] ?: 0f) <= .5f) return null
        fun c(k: String) = ((fx["$id.$k"] ?: 1f).coerceIn(0f, 1f) * 255f + .5f).toInt()
        return (c("tintR") shl 16) or (c("tintG") shl 8) or c("tintB")
    }

    /** Color propio del efecto (0xRRGGBB) o null para volver a la paleta del estilo. */
    fun setTint(id: String, rgb: Int?) {
        updateFx(id, if (rgb == null) mapOf("$id.tintOn" to 0f) else mapOf(
            "$id.tintOn" to 1f,
            "$id.tintR" to ((rgb shr 16) and 0xFF) / 255f,
            "$id.tintG" to ((rgb shr 8) and 0xFF) / 255f,
            "$id.tintB" to (rgb and 0xFF) / 255f))
    }

    fun isBehind(id: String): Boolean = (fx["$id.behind"] ?: 0f) > .5f

    /** Coloca el efecto detrás del personaje pintado en Proteger (o delante). */
    fun setBehind(id: String, behind: Boolean) {
        // v44.25: sin personaje pintado, «Detrás» no haría nada: se pregunta qué hacer.
        if (behind && !maskHasPaint(0)) { behindAsk = id; return }
        setSetting("$id.behind", if (behind) 1f else 0f)
    }

    /**
     * v42 · La animación por rutas (Animar › Ruta) aparece en la línea de tiempo como un
     * bloque «Animación», igual que un efecto: se puede mover, recortar, dividir o borrar.
     * Nace cubriendo todo el vídeo, así el movimiento no cambia al aparecer el bloque.
     */
    private fun syncMotionClip(): Boolean {
        val has = arrows.isNotEmpty()
        val own = effectClips.filter { it.effectId == "flow" }
        if (has && own.isEmpty()) {
            val total = exportSettings.loopSec.toFloat()
            effectClips = effectClips + EffectClip(nextClipUid(), "flow", 0f, total)
            if (!isEffectOn("flow")) settings = settings + ("flow.on" to 1f)
            return true
        }
        if (!has && own.isNotEmpty()) {
            effectClips = effectClips.filter { it.effectId != "flow" }
            if (selectedTimelineClip in own.map { it.uid }) selectedTimelineClip = null
            return true
        }
        return false
    }

    private fun recomputeFlow() {
        if (syncMotionClip()) pushParams()
        val a = ArrayList(arrows)
        val an = ArrayList(anchors)
        val asp = aspect
        val reach = settings["flow.reach"] ?: Effects.default("flow.reach")
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            val data: FlowField.Data = withContext(Dispatchers.Default) { FlowField.compute(a, an, asp, reach) }
            hub.flow = data
        }
    }

    // ============================================================ Reproducción

    fun togglePlay() {
        if (!playing && exportState is ExportState.Running) { showHint("Se está exportando: la vista previa sigue en pausa.", 1800L); return }
        if (paintingZone) finishPainting()
        if (playing) scrubT = hub.currentT()
        playing = !playing
        hub.setPlaying(playing)
        if (!playing) scrubT = hub.pausedT()
        showGuides = !playing && (tab == EditorTab.MASKS || (tab == EditorTab.TOOLS && tool != Tool.PAN))
        hub.showMask = showMasks && !playing && tool.isBrush
    }

    fun scrubTo(t: Float) {
        scrubT = t
        hub.boostPreview(260)
        hub.setPausedT(t)
    }

    fun toggleMasks() {
        showMasks = !showMasks
        hub.showMask = showMasks && !playing && tool.isBrush
    }

    // ============================================================ Vista

    fun resetView(animate: Boolean = false) {
        val m = meta
        val crop = if (m != null && !cropEditing) ExportPlanner.cropForSettings(m.w, m.h, exportSettings)
            else floatArrayOf(0f, 0f, 1f, 1f)
        animateCropTo(crop, animate)
    }

    private var cropAnim: Job? = null

    /** Cambia el encuadre mostrado; con animación suave (sin recrear la vista OpenGL). */
    private fun animateCropTo(target: FloatArray, animate: Boolean) {
        cropAnim?.cancel()
        if (!animate || meta == null) {
            cropX = target[0]; cropY = target[1]; cropW = target[2]; cropH = target[3]
            viewScale = 1f; viewX = cropX; viewY = cropY
            syncView()
            return
        }
        val from = floatArrayOf(cropX, cropY, cropW, cropH)
        cropAnim = viewModelScope.launch {
            val start = System.nanoTime()
            val durationMs = 260f
            while (true) {
                val k = ((System.nanoTime() - start) / 1e6f / durationMs).coerceIn(0f, 1f)
                val e = 1f - (1f - k) * (1f - k) * (1f - k)
                cropX = from[0] + (target[0] - from[0]) * e
                cropY = from[1] + (target[1] - from[1]) * e
                cropW = from[2] + (target[2] - from[2]) * e
                cropH = from[3] + (target[3] - from[3]) * e
                viewScale = 1f; viewX = cropX; viewY = cropY
                syncView()
                if (k >= 1f) break
                kotlinx.coroutines.delay(16)
            }
        }
    }

    // ---------------- Encuadre libre (arrastrar y pellizcar el marco)
    var cropEditing by mutableStateOf(false)
        private set

    fun startCropEdit() {
        if (meta == null) return
        if (!cropEditing) {
            cropEditing = true
            if (playing) togglePlay()
            animateCropTo(floatArrayOf(0f, 0f, 1f, 1f), true)
        }
    }

    fun finishCropEdit() {
        if (!cropEditing) return
        cropEditing = false
        resetView(true)
        saveProject()
    }

    /** Marco del encuadre en coordenadas de imagen: [x, y, ancho, alto]. */
    fun cropFrame(): FloatArray {
        val m = meta ?: return floatArrayOf(0f, 0f, 1f, 1f)
        return ExportPlanner.cropForSettings(m.w, m.h, exportSettings)
    }

    /** dx, dy en fracción de imagen; zoom multiplicativo (pellizco). */
    fun moveCrop(dx: Float, dy: Float, zoom: Float) {
        val m = meta ?: return
        val e = exportSettings
        val frame = ExportPlanner.cropForSettings(m.w, m.h, e)
        val newZoom = (e.cropZoom * zoom).coerceIn(1f, 6f)
        val base = ExportPlanner.baseCropSize(m.w, m.h, e)
        val sx = base[0] / newZoom
        val sy = base[1] / newZoom
        val cx = (frame[0] + frame[2] / 2f + dx).coerceIn(sx / 2f, 1f - sx / 2f)
        val cy = (frame[1] + frame[3] / 2f + dy).coerceIn(sy / 2f, 1f - sy / 2f)
        exportSettings = e.copy(cropCx = cx, cropCy = cy, cropZoom = newZoom)
        hub.invalidate()
    }

    fun resetCrop() {
        exportSettings = exportSettings.copy(cropCx = -1f, cropCy = -1f, cropZoom = 1f, cropPos = 0.5f)
        if (!cropEditing) resetView(true) else hub.invalidate()
    }

    private fun syncView() {
        // Una sola invalidación por gesto. Antes se invalidaba 4 veces por cada paso
        // de pinch/pan (aspecto, escala, X, Y), generando trabajo inútil en OpenGL.
        hub.updateView(
            displayAspect = previewAspect,
            width = cropW / viewScale,
            height = cropH / viewScale,
            scale = viewScale,
            x = viewX,
            y = viewY
        )
    }

    /** zoom = factor, pan y centroide en fracción del tamaño de la vista. */
    fun transformView(zoom: Float, panX: Float, panY: Float, cx: Float, cy: Float) {
        // Eventos sin movimiento y valores inválidos no deben activar renderizados.
        if (!zoom.isFinite() || !panX.isFinite() || !panY.isFinite() ||
            !cx.isFinite() || !cy.isFinite() || zoom <= 0f) return
        if (kotlin.math.abs(zoom - 1f) < .00008f &&
            kotlin.math.abs(panX) + kotlin.math.abs(panY) < .00006f) return
        val s0 = viewScale
        val s1 = (s0 * zoom).coerceIn(1f, 10f)
        val x = cx.coerceIn(0f, 1f)
        val y = cy.coerceIn(0f, 1f)
        val ux = viewX + x * cropW / s0
        val uy = viewY + y * cropH / s0
        val nx = (ux - (x + panX) * cropW / s1).coerceIn(cropX, (cropX + cropW - cropW / s1).coerceAtLeast(cropX))
        val ny = (uy - (y + panY) * cropH / s1).coerceIn(cropY, (cropY + cropH - cropH / s1).coerceAtLeast(cropY))
        if (kotlin.math.abs(nx - viewX) + kotlin.math.abs(ny - viewY) < .000005f &&
            kotlin.math.abs(s1 - s0) < .00005f) return
        viewX = nx
        viewY = ny
        viewScale = s1
        if (firstZoomPending) { firstZoomPending = false; perfZoomMs = perfElapsed(); perfLog("primer zoom", perfZoomMs) }
        hub.boostPreview(320)
        syncView()
    }

    fun screenToUv(nx: Float, ny: Float): PointN = PointN(
        (viewX + nx * cropW / viewScale).coerceIn(0f, 1f),
        (viewY + ny * cropH / viewScale).coerceIn(0f, 1f)
    )

    // ============================================================ Edición

    private fun pushUndo(a: UndoAction) {
        undoStack.add(a)
        if (undoStack.size > 200) undoStack.removeAt(0)
        redoStack.clear()
        canRedo = false
        canUndo = true
    }

    fun addArrow(a: Arrow) {
        arrows.add(a)
        pushUndo(UndoAction.AddArrow(a))
        recomputeFlow()
        scheduleAutosave()
    }

    fun addQuickDirection(direction: Int) {
        val vectors = arrayOf(
            PointN(0f, -0.22f), PointN(0.22f, -0.22f), PointN(0.22f, 0f),
            PointN(0.22f, 0.22f), PointN(0f, 0.22f), PointN(-0.22f, 0.22f),
            PointN(-0.22f, 0f), PointN(-0.22f, -0.22f)
        )
        val v = vectors[direction.coerceIn(0, 7)]
        addArrow(Arrow(0.5f - v.x * 0.5f, 0.5f - v.y * 0.5f, 0.5f + v.x * 0.5f, 0.5f + v.y * 0.5f))
        tool = Tool.ARROW
        message = "Flecha añadida: arrástrala o añade anclas."
    }

    fun addAnchor(p: PointN) {
        anchors.add(p)
        pushUndo(UndoAction.AddAnchor(p))
        recomputeFlow()
        scheduleAutosave()
    }

    fun removeArrowAt(i: Int) {
        if (i !in arrows.indices) return
        val a = arrows.removeAt(i)
        pushUndo(UndoAction.RemoveArrow(i, a))
        recomputeFlow()
        scheduleAutosave()
    }

    fun removeAnchorAt(i: Int) {
        if (i !in anchors.indices) return
        val p = anchors.removeAt(i)
        pushUndo(UndoAction.RemoveAnchor(i, p))
        recomputeFlow()
        scheduleAutosave()
    }

    fun clearGuides() {
        if (arrows.isEmpty() && anchors.isEmpty()) return
        pushUndo(UndoAction.ClearGuides(ArrayList(arrows), ArrayList(anchors)))
        arrows.clear()
        anchors.clear()
        recomputeFlow()
        scheduleAutosave()
    }

    /** radiusUv: radio del pincel en fracción de la altura de la imagen. */
    fun beginStroke(p: PointN, radiusUv: Float) {
        val mk = masks ?: return
        val layer = if (tool == Tool.MASK) selectedMaskLayer else tool.layer
        if (layer < 0) return
        val s = MaskStroke(layer, radiusUv, feather, eraser)
        s.points.add(p)
        mk.addStroke(s)
        hub.boostPreview(320)
        currentStroke = s
        pushUndo(UndoAction.AddStroke(s))
    }

    fun continueStroke(p: PointN) {
        val s = currentStroke ?: return
        val last = s.points.lastOrNull()
        if (last != null && hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) < 0.0015) return
        masks?.extendStroke(s, p)
        hub.boostPreview(320)
    }

    fun endStroke() {
        if (currentStroke != null) { scheduleAutosave(); refreshEffectBoxes() }
        currentStroke = null
    }

    fun cancelStroke() {
        val s = currentStroke ?: return
        currentStroke = null
        masks?.removeStroke(s)
        val last = undoStack.lastOrNull()
        if (last is UndoAction.AddStroke && last.stroke === s) undoStack.removeAt(undoStack.size - 1)
        canUndo = undoStack.isNotEmpty()
    }

    /** Rellena (clear=false) o vacía (clear=true) toda la capa de la herramienta actual. */
    fun fillLayer(clear: Boolean) {
        val mk = masks ?: return
        val layer = if (tool == Tool.MASK) selectedMaskLayer else tool.layer
        if (layer < 0) return
        val s = MaskStroke(layer, 0f, 0f, clear, fill = true)
        mk.addStroke(s)
        pushUndo(UndoAction.AddStroke(s))
        scheduleAutosave()
        refreshEffectBoxes()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val a = undoStack.removeAt(undoStack.size - 1)
        when (a) {
            is UndoAction.EffectSwitches -> {
                settings = settings + a.before - a.absentBefore
                a.beforeClips?.let { clips -> effectClips = clips; selectedTimelineClip = selectedTimelineClip?.takeIf { uid -> clips.any { it.uid == uid } } }
                pushParams()
            }
            is UndoAction.AddArrow -> {
                val i = arrows.lastIndexOf(a.arrow)
                if (i >= 0) arrows.removeAt(i)
                recomputeFlow()
            }
            is UndoAction.AddAnchor -> {
                val i = anchors.lastIndexOf(a.point)
                if (i >= 0) anchors.removeAt(i)
                recomputeFlow()
            }
            is UndoAction.AddStroke -> masks?.removeStroke(a.stroke)
            is UndoAction.RemoveStrokes -> masks?.let { mk -> a.strokes.forEach { mk.addStroke(it) } }
            is UndoAction.RemoveArrow -> {
                arrows.add(a.index.coerceIn(0, arrows.size), a.arrow)
                recomputeFlow()
            }
            is UndoAction.RemoveAnchor -> {
                anchors.add(a.index.coerceIn(0, anchors.size), a.point)
                recomputeFlow()
            }
            is UndoAction.ClearGuides -> {
                arrows.addAll(a.arrows)
                anchors.addAll(a.anchors)
                recomputeFlow()
            }
            is UndoAction.Layers -> applyLayers(a.before)
        }
        redoStack.add(a)
        if (redoStack.size > 200) redoStack.removeAt(0)
        canRedo = true
        canUndo = undoStack.isNotEmpty()
        scheduleAutosave()
        if (a is UndoAction.AddStroke || a is UndoAction.RemoveStrokes) refreshEffectBoxes()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val a = redoStack.removeAt(redoStack.size - 1)
        when (a) {
            is UndoAction.EffectSwitches -> {
                settings = settings + a.after - a.absentAfter
                a.afterClips?.let { clips -> effectClips = clips; selectedTimelineClip = selectedTimelineClip?.takeIf { uid -> clips.any { it.uid == uid } } }
                pushParams()
            }
            is UndoAction.AddArrow -> {
                arrows.add(a.arrow)
                recomputeFlow()
            }
            is UndoAction.AddAnchor -> {
                anchors.add(a.point)
                recomputeFlow()
            }
            is UndoAction.AddStroke -> masks?.addStroke(a.stroke)
            is UndoAction.RemoveStrokes -> masks?.let { mk -> a.strokes.forEach { mk.removeStroke(it) } }
            is UndoAction.RemoveArrow -> {
                val i = arrows.indexOf(a.arrow)
                if (i >= 0) arrows.removeAt(i)
                recomputeFlow()
            }
            is UndoAction.RemoveAnchor -> {
                val i = anchors.indexOf(a.point)
                if (i >= 0) anchors.removeAt(i)
                recomputeFlow()
            }
            is UndoAction.ClearGuides -> {
                arrows.clear()
                anchors.clear()
                recomputeFlow()
            }
            is UndoAction.Layers -> applyLayers(a.after)
        }
        undoStack.add(a)
        if (undoStack.size > 200) undoStack.removeAt(0)
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        scheduleAutosave()
        if (a is UndoAction.AddStroke || a is UndoAction.RemoveStrokes) refreshEffectBoxes()
    }

    // ============================================================ v16: escenas, personaje, ambiente

    /**
     * Efectos localizados. La primera vez se abre una zona propia en vez de cubrir toda la imagen.
     * La misma máscara también puede reutilizarse en otros efectos desde su panel.
     */
    val zoneEffects = setOf(
        "sky", "water", "dispersion", "zonemotion", "clouds3", "shore", "sunpath", "tornado", "mesh", "natural",
        "ripple", "glint", "sway", "heat", "flame", "eyes", "blink", "cloth", "smoke",
        "energy", "electric", "shock", "pulse", "runes", "bokeh", "sparkle", "lightleak",
        "waterfall", "reflect", "blackaura", "inkdrip", "dramatic",
        "blast", "blood", "slash", "reveal"
    )

    /** v40: efectos con origen en el motor (se colocan tocando la imagen). */
    private val placeableEffects = setOf("tornado", "blast", "blood", "slash", "smoke", "energy", "rays",
        "focus", "speed", "electric", "shock", "runes", "birds", "lensflare", "citylights", "symbols", "steam", "sunlight", "radialblur", "focusblur")
    /** v40: efectos con dirección propia (flecha en la herramienta Posición). */
    private val aimEffects = setOf("smoke", "blast", "blood", "slash", "speed")

    /** Efectos con momento en la línea de tiempo heredada. */
    val eventEffects = listOf("gust", "appear", "impact")

    /** Todos los bloques de un efecto, ordenados como aparecen en el vídeo. */
    // ------------------------------------------------------------ v44: imán de la línea de tiempo
    var magnetOn by mutableStateOf(prefs.getBoolean("magnetOn", true)); private set
    var magnetHaptic by mutableStateOf(prefs.getBoolean("magnetHaptic", true)); private set
    var magnetDp by mutableFloatStateOf(prefs.getFloat("magnetDp", com.zeta.fondo.ui.MagnetState.DEFAULT_DP)); private set

    fun toggleMagnet() {
        setMagnet(on = !magnetOn)
        showHint(if (magnetOn) "Imán activado" else "Imán desactivado: los bloques quedan donde los sueltes", 1600L)
    }

    fun setMagnet(on: Boolean = magnetOn, haptic: Boolean = magnetHaptic, dp: Float = magnetDp) {
        magnetOn = on; magnetHaptic = haptic; magnetDp = dp.coerceIn(4f, 16f)
        prefs.edit().putBoolean("magnetOn", magnetOn).putBoolean("magnetHaptic", magnetHaptic)
            .putFloat("magnetDp", magnetDp).apply()
    }

    /** Bloque cuyo tiempo exacto se está editando (diálogo de 0,01 s). */
    var preciseTimeClip by mutableStateOf<Long?>(null)

    // ------------------------------------------------------------ v44: Ocultar y Solo

    /**
     * Bloque aislado con «Solo» (temporal, NO se guarda en el proyecto). Mientras está activo
     * la vista previa dibuja solo ese bloque: los demás efectos y la animación se ocultan.
     * La música sigue sonando. La exportación nunca usa «Solo»: exporta todos los bloques
     * visibles (el panel de exportar lo avisa).
     */
    var soloClip by mutableStateOf<Long?>(null)
        private set
    val soloActive: Boolean get() = soloClip != null && effectClips.any { it.uid == soloClip && !com.zeta.fondo.model.SoundClips.isSound(it.effectId) }
    /** «Solo» puesto en un bloque de sonido (solo se oye ese). */
    val soundSoloActive: Boolean get() = soloClip != null && effectClips.any { it.uid == soloClip && com.zeta.fondo.model.SoundClips.isSound(it.effectId) }

    /** Bloques que ve la VISTA PREVIA (ocultos y «Solo» aplicados). */
    fun renderClips(list: List<EffectClip> = effectClips): List<EffectClip> {
        val solo = soloClip?.takeIf { s -> list.any { it.uid == s && !com.zeta.fondo.model.SoundClips.isSound(it.effectId) } } ?: return list
        return list.map { if (it.uid != solo && !it.hidden) it.copy(hidden = true) else it }
    }

    fun toggleSolo(uid: Long) {
        soloClip = if (soloClip == uid) null else uid
        hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
        if (soloClip != null) showHint("Solo: se ve únicamente este bloque. Toca «Mostrar todos» para salir.")
    }

    fun clearSolo() {
        if (soloClip == null) return
        soloClip = null
        hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
    }

    /** Ojo del bloque: ocultar/mostrar. Se guarda en el proyecto y se puede deshacer. */
    fun toggleClipHidden(uid: Long) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        val before = effectClips.toList()
        effectClips = effectClips.map { if (it.uid == uid) it.copy(hidden = !it.hidden) else it }
        commitTimeline(before)
        hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
        saveProject()
        val name = Effects.all.firstOrNull { it.id == clip.effectId }?.title?.substringBefore(" (") ?: "Bloque"
        showHint(if (!clip.hidden) "$name oculto (no se exporta)" else "$name visible")
    }

    // ------------------------------------------------------------ v44: fotogramas clave

    /** Bloque que recibe las claves de [id]: el seleccionado, el que se edita o el único. */
    fun keyClip(id: String): EffectClip? = selectedClip()?.takeIf { it.effectId == id }
        ?: fxTarget(id) ?: clipsForEffect(id).let { list ->
            val now = scrubT * exportSettings.loopSec
            list.firstOrNull { now >= it.startSec - .01f && now <= it.endSec + .01f } ?: list.firstOrNull()
        }

    /** Segundo local del cabezal dentro del bloque (null si está fuera). */
    fun keyLocal(clip: EffectClip): Float? {
        val now = (if (playing) hub.currentT() else scrubT) * exportSettings.loopSec
        val local = now - clip.startSec
        return if (local < -.01f || local > clip.durationSec + .01f) null else local.coerceIn(0f, clip.durationSec)
    }

    fun canKeyframe(key: String): Boolean = com.zeta.fondo.model.Keyframes.animatable(key) &&
        key.substringBefore('.') !in Effects.globalSettings && key.substringBefore('.') != "elements"

    fun keysOf(key: String): List<com.zeta.fondo.model.Keyframe> = keyClip(key.substringBefore('.'))?.keys?.get(key).orEmpty()

    /** Valor animado de [key] en el cabezal (null si no tiene claves). */
    fun keyedValue(key: String): Float? {
        val clip = keyClip(key.substringBefore('.')) ?: return null
        val ks = clip.keys[key] ?: return null
        val local = keyLocal(clip) ?: (scrubT * exportSettings.loopSec - clip.startSec).coerceIn(0f, clip.durationSec)
        return com.zeta.fondo.model.Keyframes.valueAt(ks, local)
    }

    /** ¿Hay una clave de [key] justo en el cabezal? */
    fun keyAtPlayhead(key: String): com.zeta.fondo.model.Keyframe? {
        val clip = keyClip(key.substringBefore('.')) ?: return null
        val local = keyLocal(clip) ?: return null
        return com.zeta.fondo.model.Keyframes.at(clip.keys[key], local)
    }

    private fun updateKeys(clip: EffectClip, key: String, keys: List<com.zeta.fondo.model.Keyframe>) {
        effectClips = effectClips.map {
            if (it.uid != clip.uid) it
            else it.copy(keys = if (keys.isEmpty()) it.keys - key else it.keys + (key to keys))
        }
        hub.boostPreview()
        pushParams()
        scheduleAutosave()
    }

    /** Rombo: añade una clave en el cabezal con el valor actual, o la quita si ya hay una. */
    fun toggleKeyframe(key: String) {
        val id = key.substringBefore('.')
        val clip = keyClip(id) ?: run { message = "Primero añade el efecto a la línea de tiempo."; return }
        val local = keyLocal(clip) ?: run { message = "Coloca el cabezal dentro del bloque para poner una clave."; return }
        if (playing) togglePlay()
        val ks = clip.keys[key].orEmpty()
        if (com.zeta.fondo.model.Keyframes.at(ks, local) != null) {
            updateKeys(clip, key, com.zeta.fondo.model.Keyframes.remove(ks, local))
            showHint("Clave quitada", 1200L)
        } else {
            val v = keyedValue(key) ?: fx[key] ?: if (com.zeta.fondo.model.SoundClips.isSound(id))
                (clip.overrides[key] ?: com.zeta.fondo.model.SoundClips.defaults[key.substringAfter('.')] ?: 0f) else Effects.default(key)
            updateKeys(clip, key, com.zeta.fondo.model.Keyframes.put(ks, local, v))
            showHint(if (ks.isEmpty()) "Primera clave. Mueve el cabezal y cambia el valor: se crea otra clave sola." else "Clave añadida", 2600L)
        }
    }

    fun setKeyCurve(key: String, curve: Int) {
        val clip = keyClip(key.substringBefore('.')) ?: return
        val local = keyLocal(clip) ?: return
        val ks = clip.keys[key].orEmpty()
        val k = com.zeta.fondo.model.Keyframes.at(ks, local) ?: return
        updateKeys(clip, key, com.zeta.fondo.model.Keyframes.put(ks, k.t, k.value, curve))
    }

    fun clearKeyframes(key: String) {
        val clip = keyClip(key.substringBefore('.')) ?: return
        updateKeys(clip, key, emptyList())
        showHint("Animación de este control borrada", 1600L)
    }

    /**
     * Si [key] ya está animado, cambiar su valor crea o actualiza la clave del cabezal
     * (como en un editor de vídeo). Devuelve true si lo gestionó.
     */
    private fun autoKey(key: String, value: Float): Boolean {
        val clip = keyClip(key.substringBefore('.')) ?: return false
        val ks = clip.keys[key] ?: return false
        if (ks.isEmpty()) return false
        val local = keyLocal(clip) ?: return false
        if (playing) togglePlay()
        updateKeys(clip, key, com.zeta.fondo.model.Keyframes.put(ks, local, value))
        return true
    }

    fun clipsForEffect(id: String): List<EffectClip> = effectClips.filter { it.effectId == id }.sortedBy { it.startSec }

    private fun nextClipUid(): Long = (effectClips.maxOfOrNull { it.uid } ?: 0L) + 1L

    fun selectTimelineClip(uid: Long?) {
        val next = uid?.takeIf { id -> effectClips.any { it.uid == id } }
        if (next != selectedTimelineClip) placingClip = false
        // v40: al tocar un bloque se edita por contexto: se cierran las opciones de la
        // categoría y abajo aparecen las acciones del bloque (como en un editor de vídeo).
        if (next != null && editingEffect == null && replacingClip == null && !paintingZone) optionsOpen = false
        if (next != null) { imageSelected = false; selectedLayer = null; blendPanelOpen = false; layerMotionOpen = false }
        selectedTimelineClip = next
    }

    private fun clipsOverlap(aStart: Float, aDuration: Float, bStart: Float, bDuration: Float): Boolean {
        val aEnd = aStart + aDuration
        val bEnd = bStart + bDuration
        return aStart < bEnd - .015f && bStart < aEnd - .015f
    }

    /**
     * Pistas efectivas para proyectos antiguos y nuevos. Los clips v20-v22 no guardaban
     * pista: se colocan automáticamente en el primer hueco libre sin alterar su tiempo.
     */
    private fun resolvedTimelineTracks(excludeUid: Long? = null): Map<Long, Int> {
        val assigned = LinkedHashMap<Long, Int>()
        val sorted = effectClips.filter { it.uid != excludeUid }
            .sortedWith(compareBy<EffectClip> { it.startSec }.thenBy { it.uid })
        for (clip in sorted) {
            var lane = clip.track
            fun laneFree(candidate: Int): Boolean = effectClips.none { other ->
                other.uid != clip.uid && assigned[other.uid] == candidate &&
                    clipsOverlap(clip.startSec, clip.durationSec, other.startSec, other.durationSec)
            }
            if (lane < 0 || !laneFree(lane)) {
                lane = 0
                while (!laneFree(lane)) lane++
            }
            assigned[clip.uid] = lane
        }
        return assigned
    }

    fun timelineTrackOf(uid: Long): Int = resolvedTimelineTracks()[uid] ?: 0

    /** Solo permite dejar un bloque en una pista si hay espacio temporal real. */
    fun isTimelineTrackFree(uid: Long, startSec: Float, durationSec: Float, track: Int): Boolean {
        if (track < 0) return false
        val tracks = resolvedTimelineTracks(uid)
        return effectClips.none { other ->
            other.uid != uid && tracks[other.uid] == track &&
                clipsOverlap(startSec, durationSec, other.startSec, other.durationSec)
        }
    }

    /** Busca la pista libre más cercana; nunca impone un número fijo de pistas. */
    fun suggestTimelineTrack(uid: Long, startSec: Float, durationSec: Float, preferredTrack: Int): Int {
        val preferred = preferredTrack.coerceAtLeast(0)
        if (isTimelineTrackFree(uid, startSec, durationSec, preferred)) return preferred
        val radius = effectClips.size + 2 // con N clips siempre existe una pista libre dentro de N+1
        for (d in 1..radius) {
            val up = preferred - d
            if (up >= 0 && isTimelineTrackFree(uid, startSec, durationSec, up)) return up
            val down = preferred + d
            if (isTimelineTrackFree(uid, startSec, durationSec, down)) return down
        }
        return preferred + radius
    }

    /** Confirma tiempo + pista en una sola operación para que mover arriba/abajo no salte. */
    fun setEffectClipPlacement(uid: Long, startSec: Float, durationSec: Float, track: Int) {
        val total = exportSettings.loopSec.toFloat()
        val before = effectClips.toList()
        val safeTrack = suggestTimelineTrack(uid, startSec, durationSec, track)
        effectClips = effectClips.map { clip ->
            if (clip.uid == uid) {
                // v44: recortar por la izquierda (el final no se mueve) deja las claves en su
                // tiempo real; mover el bloque entero las lleva con él.
                val leftTrim = kotlin.math.abs((startSec + durationSec) - clip.endSec) < 1e-3f &&
                    kotlin.math.abs(startSec - clip.startSec) > 1e-4f
                val keys = if (leftTrim) com.zeta.fondo.model.Keyframes.shift(clip.keys, startSec - clip.startSec) else clip.keys
                EffectTimeline.normalized(clip.copy(startSec = startSec, durationSec = durationSec, track = safeTrack, keys = keys), total)
            } else clip
        }
        selectedTimelineClip = uid
        hub.params = RenderParams.from(renderSettings(), aspect, total, renderClips(), soloActive)
        // v40: mover y recortar se pueden deshacer.
        commitTimeline(before)
    }

    private fun legacyTimelineClips(id: String): List<EffectClip> {
        val total = exportSettings.loopSec.toFloat().coerceAtLeast(2f)
        var uid = nextClipUid()
        fun clip(start: Float, dur: Float): EffectClip = EffectTimeline.normalized(
            EffectClip(uid++, id, start, dur), total)
        return when (id) {
            "gust" -> {
                val dur = (settings["gust.dur"] ?: .45f).coerceIn(.1f, 1f)
                if (dur >= .999f) listOf(clip(0f, total)) else
                    listOf(clip((settings["gust.start"] ?: .1f) * total, dur * total))
            }
            "appear" -> {
                val start = (settings["appear.start"] ?: .3f).coerceIn(0f, .95f) * total
                listOf(clip(start, (total - start).coerceAtLeast(.25f)))
            }
            "impact" -> {
                val n = (settings["impact.cycles"] ?: 1f).toInt().coerceIn(1, 4)
                val phase = (settings["impact.start"] ?: .3f).coerceIn(0f, .95f)
                (0 until n).map { k -> clip(((k + phase) / n) * total, minOf(.85f, total / n)) }
            }
            "bolt" -> {
                val n = (settings["bolt.cycles"] ?: 1f).toInt().coerceIn(1, 6)
                (0 until n).map { k -> clip(com.zeta.fondo.engine.Ambient.boltPhase(k, n) * total, minOf(.7f, total / n)) }
            }
            else -> listOf(clip(0f, total))   // proyectos antiguos: se respeta lo que tenían
        }
    }

    /**
     * v37: los efectos ambientales (humo, agua, nubes, fuego, lluvia…) cubren TODO el
     * vídeo por defecto: antes nacían como un bloque de 2 s en el centro y parecía que
     * "se apagaban y volvían a empezar". Los eventos (explosión, corte…) nacen en modo
     * Una vez, en la posición del cabezal, con su duración natural.
     */
    private fun defaultExtraClip(id: String): EffectClip {
        val total = exportSettings.loopSec.toFloat().coerceAtLeast(2f)
        if (id !in Effects.eventEffects) {
            // v44.10: el efecto nuevo cubre desde el inicio hasta el final (o hasta 6 s si el
            // vídeo es más largo), sin huecos. Luego se acorta con sus tiradores.
            val (start, dur) = ClipEdits.defaultSpan(total)
            return EffectTimeline.normalized(EffectClip(nextClipUid(), id, start, dur), total)
        }
        if (!settings.containsKey("$id.mode")) settings = settings + ("$id.mode" to EventMode.ONCE.toFloat())
        val duration = Effects.eventDuration(id).coerceIn(.08f, total)
        val start = (scrubT * total).coerceIn(0f, (total - duration).coerceAtLeast(0f))
        return EffectTimeline.normalized(EffectClip(nextClipUid(), id, start, duration), total)
    }

    fun eventMode(id: String): Int = (settings["$id.mode"] ?: EventMode.LOOP.toFloat()).toInt()

    /** Continuo (N veces por bucle) · Una vez (en su bloque) · Repetir (cada X s). */
    fun setEventMode(id: String, mode: Int) {
        val total = exportSettings.loopSec.toFloat()
        val before = effectClips.toList()
        settings = settings + ("$id.mode" to mode.toFloat()) +
            (if (mode == EventMode.REPEAT && !settings.containsKey("$id.every")) mapOf("$id.every" to 2f) else emptyMap())
        val clips = clipsForEffect(id)
        val target = clips.firstOrNull { it.uid == selectedTimelineClip } ?: clips.firstOrNull()
        if (target != null) {
            val placed = when (mode) {
                EventMode.ONCE -> {
                    val d = Effects.eventDuration(id).coerceAtMost(total)
                    val start = if (target.durationSec >= total - .05f) (scrubT * total).coerceIn(0f, total - d) else target.startSec
                    target.copy(startSec = start, durationSec = d)
                }
                else -> target.copy(startSec = 0f, durationSec = total)
            }
            effectClips = effectClips.map { if (it.uid == target.uid) EffectTimeline.normalized(placed, total) else it }
            selectedTimelineClip = target.uid
        }
        pushUndo(UndoAction.EffectSwitches(emptyMap(), emptyMap(), before, effectClips.toList()))
        hub.boostPreview()
        pushParams()
        scheduleAutosave()
    }

    /**
     * v44.13 · Coloca solos los efectos de paisaje la primera vez: horizonte (Nubes de tu imagen,
     * Mar en movimiento) y sol o luna (Luz del sol) leyendo la imagen. Luego el usuario manda.
     */
    private fun autoScene(ids: Collection<String>) {
        val m = meta ?: return
        val needH = ids.filter { it == "skydrift" || it == "seawaves" }.filter { (settings["$it.auto"] ?: 0f) < .5f }
        val needS = "sunlight" in ids && (settings["sunlight.auto"] ?: 0f) < .5f
        // v44.17: los rayos de luz salen del SOL de la imagen (si hay uno), no de una esquina.
        val needR = listOf("rays", "lensflare").filter { it in ids && (settings["$it.auto"] ?: 0f) < .5f && !settings.containsKey("$it.origin") }
        if (needH.isEmpty() && !needS && needR.isEmpty()) return
        viewModelScope.launch {
            var sunLum = 0f
            val r = try {
                withContext(Dispatchers.Default) {
                    val b = BitmapUtil.decodeAtMost(m.imageFile, 160)
                    val w = b.width; val h = b.height
                    val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h); b.recycle() }
                    val lum = com.zeta.fondo.model.SceneAnalysis.luminance(px)
                    val sun = com.zeta.fondo.model.SceneAnalysis.brightest(lum, w, h)
                    sunLum = lum[((sun.second * h).toInt().coerceIn(0, h - 1)) * w + (sun.first * w).toInt().coerceIn(0, w - 1)]
                    com.zeta.fondo.model.SceneAnalysis.horizon(lum, w, h) to sun
                }
            } catch (e: Throwable) { com.zeta.fondo.diag.ZetaLog.warn("scene", "análisis: ${e.message}"); return@launch }
            if (meta?.id != m.id) return@launch
            val add = HashMap<String, Float>()
            for (id in needH) { add["$id.auto"] = 1f; r.first?.let { add["$id.horizon"] = it } }
            if (needS) {
                add["sunlight.auto"] = 1f
                add["sunlight.origin"] = 0f; add["sunlight.originX"] = r.second.first; add["sunlight.originY"] = r.second.second
            }
            for (id in needR) {
                add["$id.auto"] = 1f
                if (com.zeta.fondo.model.SceneAnalysis.isLightSource(sunLum, r.second.second)) {
                    add["$id.origin"] = 0f; add["$id.originX"] = r.second.first; add["$id.originY"] = r.second.second
                }
            }
            settings = settings + add
            pushParams(); scheduleAutosave()
            if (r.first != null && needH.isNotEmpty()) showHint("Horizonte detectado. Puedes ajustarlo en Ajustes › Horizonte.", 2600L)
        }
    }

    private fun ensureTimelineActivation(id: String) {
        if ((settings["$id.on"] ?: 0f) < .5f) autoScene(listOf(id))
        val wasOn = (settings["$id.on"] ?: 0f) > .5f
        if (!wasOn) settings = settings + com.zeta.fondo.model.EffectDefaults.firstActivation(id, settings, maskHasPaint(0)) + ("$id.on" to 1f)
        if (!settings.containsKey("$id.mask") && id != "appear" && id != "impact") {
            settings = settings + ("$id.mask" to -2f)
        }
        if (clipsForEffect(id).isEmpty()) {
            // v40: un efecto NUEVO nace con su bloque natural (los eventos, en el cabezal y en
            // modo Una vez). Solo un proyecto antiguo con el efecto ya activo y sin bloques
            // se convierte con sus tiempos heredados.
            val first = if (wasOn) legacyTimelineClips(id).firstOrNull() ?: defaultExtraClip(id) else defaultExtraClip(id)
            effectClips = effectClips + first
        }
        val own = clipsForEffect(id)
        if (own.none { it.uid == selectedTimelineClip }) selectedTimelineClip = own.firstOrNull()?.uid
        pushParams()
    }

    /**
     * Duplica un bloque conservando exactamente la configuración del efecto.
     * v29: la copia nace EN EL MISMO TIEMPO que el original y en la primera pista
     * libre inmediatamente inferior. Así queda visualmente alineada, como en CapCut,
     * y luego el usuario decide si la mueve.
     */
    fun duplicateEffectClip(uid: Long): Long? {
        val source = effectClips.firstOrNull { it.uid == uid } ?: return null
        val total = exportSettings.loopSec.toFloat().coerceAtLeast(2f)
        val before = effectClips.toList()
        val newUid = nextClipUid()
        val sourceTrack = timelineTrackOf(source.uid)
        // v40.1: como en un editor de vídeo, la copia va JUSTO DESPUÉS del original (o justo
        // antes si no cabe). Solo si el bloque ocupa todo el vídeo se coloca debajo, en el
        // mismo tiempo: dos copias simultáneas del mismo efecto se ven como una más intensa.
        val dur = source.durationSec
        val after = source.endSec
        val beforeStart = source.startSec - dur
        val (start, track) = when {
            after + dur <= total + .001f ->
                after to suggestTimelineTrack(newUid, after, dur.coerceAtMost(total - after), sourceTrack)
            beforeStart >= -.001f ->
                beforeStart.coerceAtLeast(0f) to suggestTimelineTrack(newUid, beforeStart.coerceAtLeast(0f), dur, sourceTrack)
            else -> {
                var t = sourceTrack + 1
                while (!isTimelineTrackFree(newUid, source.startSec, dur, t)) t++
                source.startSec to t
            }
        }
        val copy = EffectTimeline.normalized(source.copy(uid = newUid, startSec = start, track = track), total)
        effectClips = effectClips + copy
        selectedTimelineClip = copy.uid
        pushUndo(UndoAction.EffectSwitches(emptyMap(), emptyMap(), before, effectClips.toList()))
        pushParams()
        saveProject()
        val overlaps = copy.startSec < source.endSec - .01f && source.startSec < copy.endSec - .01f
        message = if (overlaps) "Bloque duplicado debajo (mismo tiempo): se dibujan los dos." else "Bloque duplicado a continuación."
        return copy.uid
    }

    /**
     * Añade otra aparición. Si ya hay un bloque del mismo efecto, el botón + ahora
     * duplica el seleccionado (o el más cercano) en vez de crear uno con duración genérica.
     */
    fun addEffectClip(id: String) {
        if (id in Effects.globalSettings || id == "elements") return
        var existing = clipsForEffect(id)
        // Proyectos antiguos podían tener el efecto encendido sin bloques. Al migrar,
        // conservamos una sola aparición base y después la duplicamos.
        if (existing.isEmpty() && isEffectOn(id)) {
            legacyTimelineClips(id).firstOrNull()?.let { effectClips = effectClips + it }
            existing = clipsForEffect(id)
        }
        if (!isEffectOn(id)) settings = settings + ("$id.on" to 1f)
        if (!settings.containsKey("$id.mask") && id != "appear" && id != "impact")
            settings = settings + ("$id.mask" to -2f)

        val source = existing.firstOrNull { it.uid == selectedTimelineClip }
            ?: existing.minByOrNull { kotlin.math.abs(it.startSec - scrubT * exportSettings.loopSec) }
        if (source != null) {
            duplicateEffectClip(source.uid)
            return
        }

        val before = effectClips.toList()
        val added = defaultExtraClip(id)
        effectClips = effectClips + added
        selectedTimelineClip = added.uid
        pushUndo(UndoAction.EffectSwitches(emptyMap(), emptyMap(), before, effectClips.toList()))
        pushParams(); saveProject()
    }

    /**
     * Toque normal en una tarjeta de efecto, estilo editor de vídeo:
     * - si no existe, crea UN solo bloque;
     * - si ya existe, únicamente lo selecciona;
     * - nunca duplica ni elimina por accidente.
     * Las repeticiones se crean exclusivamente con el botón +.
     */
    fun activateOrSelectEffect(id: String) {
        if (id in Effects.globalSettings || id == "elements") { editEffect(id); return }
        val existing = clipsForEffect(id)
        if (existing.isNotEmpty()) {
            val total = exportSettings.loopSec.toFloat().coerceAtLeast(2f)
            val at = scrubT.coerceIn(0f, .999f) * total
            val chosen = existing.firstOrNull { at >= it.startSec && at <= it.startSec + it.durationSec }
                ?: existing.minByOrNull { kotlin.math.abs(it.startSec - at) }
                ?: existing.first()
            selectedTimelineClip = chosen.uid
            return
        }
        if (isEffectOn(id)) {
            // Compatibilidad con un proyecto previo sin timeline guardada: convertir a un único bloque.
            val migrated = legacyTimelineClips(id).firstOrNull() ?: defaultExtraClip(id)
            effectClips = effectClips + migrated
            selectedTimelineClip = migrated.uid
            pushParams(); saveProject()
            return
        }
        if (id == "appear") { editEffect(id); return }
        if (id in zoneEffects && !settings.containsKey("$id.mask")) { editEffect(id); return }
        settings = settings + ("$id.on" to 1f) +
            (if (!settings.containsKey("$id.mask") && id != "impact") mapOf("$id.mask" to -2f) else emptyMap())
        val clip = defaultExtraClip(id)
        effectClips = effectClips + clip
        selectedTimelineClip = clip.uid
        pushParams(); saveProject()
    }

    /** Convierte el bloque seleccionado en continuo; no crea una segunda copia inesperada. */
    fun addWholeVideoEffectClip(id: String) {
        if (id in Effects.globalSettings || id == "elements") return
        val total = exportSettings.loopSec.toFloat()
        if (!isEffectOn(id)) settings = settings + ("$id.on" to 1f)
        if (!settings.containsKey("$id.mask") && id != "appear" && id != "impact")
            settings = settings + ("$id.mask" to -2f)
        val clips = clipsForEffect(id)
        val target = clips.firstOrNull { it.uid == selectedTimelineClip } ?: clips.firstOrNull()
        if (target != null) {
            effectClips = effectClips.map { if (it.uid == target.uid) it.copy(startSec = 0f, durationSec = total) else it }
            selectedTimelineClip = target.uid
        } else {
            val clip = EffectClip(nextClipUid(), id, 0f, total)
            effectClips = effectClips + clip
            selectedTimelineClip = clip.uid
        }
        pushParams(); saveProject()
    }

    /** Intensidad propia de cada bloque, incluso cuando varios del mismo efecto se solapan. */
    fun setEffectClipStrength(uid: Long, strength: Float) {
        if (effectClips.none { it.uid == uid }) return
        effectClips = effectClips.map { clip ->
            if (clip.uid == uid) clip.copy(strength = strength.coerceIn(0f, 2f)) else clip
        }
        selectedTimelineClip = uid
        hub.boostPreview(320)
        hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
        // Mientras hay un panel en edición, finishEdit(true) guarda todos los cambios.
        if (editingEffect == null) saveProject()
    }

    fun setEffectClipTiming(uid: Long, startSec: Float, durationSec: Float) {
        val total = exportSettings.loopSec.toFloat()
        effectClips = effectClips.map { clip ->
            if (clip.uid == uid) EffectTimeline.normalized(clip.copy(startSec = startSec, durationSec = durationSec), total) else clip
        }
        selectedTimelineClip = uid
        hub.boostPreview(320)
        // La duración del proyecto no cambió: actualizarla aquí reiniciaba innecesariamente
        // el reloj del preview en cada píxel del arrastre. Solo reconstruimos los parámetros.
        hub.params = RenderParams.from(renderSettings(), aspect, total, renderClips(), soloActive)
    }

    /**
     * Vista previa ligera mientras se arrastra un bloque de la línea de tiempo.
     * No toca el estado Compose ni guarda el proyecto; así el dedo mueve el bloque sin
     * recomponer toda la pantalla. La posición definitiva se confirma al soltar.
     */
    fun previewEffectClipMove(uid: Long, startSec: Float) {
        val original = effectClips.firstOrNull { it.uid == uid } ?: return
        previewEffectClipTiming(uid, startSec, original.durationSec)
    }

    /** Preview sin recomposición para mover o redimensionar un bloque. */
    fun previewEffectClipTiming(uid: Long, startSec: Float, durationSec: Float) {
        val total = exportSettings.loopSec.toFloat()
        val original = effectClips.firstOrNull { it.uid == uid } ?: return
        val preview = EffectTimeline.normalized(
            original.copy(startSec = startSec, durationSec = durationSec), total)
        val previewClips = effectClips.map { if (it.uid == uid) preview else it }
        hub.boostPreview(320)
        hub.params = RenderParams.from(renderSettings(), aspect, total, renderClips(previewClips), soloActive)
    }

    fun moveEffectClip(uid: Long, startSec: Float) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        setEffectClipTiming(uid, startSec, clip.durationSec)
    }

    fun removeEffectClip(uid: Long) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        val beforeClips = effectClips.toList()
        val beforeSettings = settings
        effectClips = effectClips.filterNot { it.uid == uid }
        val remaining = clipsForEffect(clip.effectId)
        if (remaining.isEmpty()) settings = settings + ("${clip.effectId}.on" to 0f)
        else if (remaining.size == 1 && remaining[0].overrides.isNotEmpty()) {
            // Queda un solo bloque: sus ajustes propios pasan a ser los del efecto.
            val last = remaining[0]
            settings = settings + last.overrides.filterKeys { ClipEdits.isClipKey(last.effectId, it) }
            effectClips = effectClips.map { if (it.uid == last.uid) it.copy(overrides = emptyMap()) else it }
        }
        selectedTimelineClip = null
        placingClip = false
        commitTimeline(beforeClips, beforeSettings)
        pushParams(); saveProject()
        showUndoNotice("${Effects.all.firstOrNull { it.id == clip.effectId }?.title?.substringBefore(" (") ?: "Efecto"} eliminado")
    }

    /** v40: divide el bloque seleccionado en el cabezal (dos bloques independientes). */
    fun splitEffectClip(uid: Long) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        val at = scrubT * exportSettings.loopSec
        val raw = ClipEdits.split(clip, at, nextClipUid())
        if (raw == null) { message = "Coloca el cabezal dentro del bloque para dividirlo."; return }
        // v44: las claves se reparten y ambas mitades reciben el valor exacto del corte.
        val parts = if (clip.keys.isEmpty()) raw else {
            val (kl, kr) = com.zeta.fondo.model.Keyframes.split(clip.keys, at - clip.startSec)
            raw.first.copy(keys = kl) to raw.second.copy(keys = kr)
        }
        val before = effectClips.toList()
        effectClips = effectClips.flatMap { if (it.uid == uid) listOf(parts.first, parts.second) else listOf(it) }
        selectedTimelineClip = parts.second.uid
        commitTimeline(before)
        pushParams(); saveProject()
    }

    /** v40: cambia el efecto del bloque conservando su tiempo, pista y posición. */
    fun replaceClipEffect(uid: Long, newId: String) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        replacingClip = null
        if (newId == clip.effectId || newId == "elements" || newId in Effects.globalSettings) { optionsOpen = false; return }
        val beforeClips = effectClips.toList()
        val beforeSettings = settings
        val replaced = ClipEdits.replaceEffect(clip, newId)
        effectClips = effectClips.map { if (it.uid == uid) replaced else it }
        var next = settings + ("$newId.on" to 1f)
        if (!next.containsKey("$newId.mask")) next = next + ("$newId.mask" to -2f)
        if (newId in Effects.eventEffects && !next.containsKey("$newId.mode")) next = next + ("$newId.mode" to EventMode.ONCE.toFloat())
        if (clipsForEffect(clip.effectId).isEmpty()) next = next + ("${clip.effectId}.on" to 0f)
        settings = next
        selectedTimelineClip = uid
        optionsOpen = false
        commitTimeline(beforeClips, beforeSettings)
        pushParams(); saveProject()
        message = "Efecto sustituido"
    }

    fun removeEffectClips(id: String) {
        effectClips = effectClips.filterNot { it.effectId == id }
        selectedTimelineClip = null
        settings = settings + ("$id.on" to 0f)
        pushParams(); saveProject()
    }

    val boltCount: Int get() = if ((settings["bolt.on"] ?: 0f) > .5f) (settings["bolt.cycles"] ?: 1f).toInt().coerceIn(1, 6) else 0

    fun isEffectOn(id: String): Boolean = (settings["$id.on"] ?: 0f) > .5f

    /** Toque corto en la tarjeta: activa/quita el efecto completo. El botón + añade repeticiones. */
    fun toggleEffect(id: String) {
        if (id == "elements") { editEffect(id); return }
        if (id == "appear" && !isEffectOn(id)) { editEffect(id); return }
        val on = isEffectOn(id)
        if (!on && id in zoneEffects && !settings.containsKey("$id.mask")) { editEffect(id); return }
        val before = mapOf("$id.on" to (if (on) 1f else 0f))
        val after = mapOf("$id.on" to (if (on) 0f else 1f))
        val beforeClips = effectClips.toList()
        settings = settings + after + (if (!on && !settings.containsKey("$id.mask")) mapOf("$id.mask" to -2f) else emptyMap())
        if (on) {
            effectClips = effectClips.filterNot { it.effectId == id }
            if (selectedTimelineClip?.let { uid -> effectClips.none { it.uid == uid } } != false) selectedTimelineClip = null
        } else if (clipsForEffect(id).isEmpty()) {
            val first = defaultExtraClip(id)
            effectClips = effectClips + first
            selectedTimelineClip = first.uid
        }
        pushUndo(UndoAction.EffectSwitches(before, after, beforeClips, effectClips.toList()))
        pushParams()
        saveProject()
    }

    /** Mueve el momento (0..1) de un efecto de evento desde la línea de tiempo. */
    fun setEventStart(id: String, t: Float) {
        settings = settings + ("$id.start" to t.coerceIn(0f, 0.95f))
        pushParams()
    }

    /** Toca la imagen para colocar el centro u origen del efecto. */
    fun pickOrigin(id: String) {
        activeMotionEffect = id
        tool = Tool.ORIGIN
        if (playing) togglePlay()
        showGuides = true
        message = "Toca la imagen para elegir el origen."
    }

    var hasCharacter by mutableStateOf(false)
        private set

    private fun loadCharacter(dir: java.io.File): Bitmap? {
        val f = java.io.File(dir, CHARACTER_FILE)
        val bmp = if (f.isFile) try { BitmapUtil.decodeAtMost(f, 2048) } catch (e: Exception) { null } else null
        main.post { hasCharacter = bmp != null }
        return bmp
    }

    /** Copia el PNG del personaje al proyecto (conserva la transparencia). */
    fun importCharacter(uri: Uri) {
        val project = meta ?: return
        if (busy != null) return   // v44.25: dos toques seguidos ya no cargan dos veces
        busy = "Cargando personaje…"
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    // v44.25: se decodifica ya al tamaño final (antes entero: un PNG enorme cerraba la app).
                    val scaled = BitmapUtil.decodeUriAtMost(resolver, uri, 2048)
                    // Escritura segura: un corte a mitad no deja un PNG roto.
                    val f = java.io.File(project.dir, CHARACTER_FILE)
                    val tmp = java.io.File(project.dir, "$CHARACTER_FILE.tmp")
                    tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) error("No se pudo guardar el personaje") }
                    scaled
                }
                hub.character = bmp
                hasCharacter = true
                settings = settings + mapOf("appear.hasImage" to 1f, "appear.on" to 1f)
                if (clipsForEffect("appear").isEmpty()) effectClips = effectClips + legacyTimelineClips("appear")
                pushParams()
                saveProject()
                if (!bmp.hasAlpha()) message = "La imagen no tiene fondo transparente: usa un PNG recortado para mejor resultado."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                // v44.25: también la falta de memoria (antes cerraba la app).
                message = "No se pudo cargar el personaje: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = null }
        }
    }

    fun transformCharacter(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        settings = settings + mapOf(
            "appear.x" to ((settings["appear.x"] ?: .5f) + dx).coerceIn(-0.2f, 1.2f),
            "appear.y" to ((settings["appear.y"] ?: .62f) + dy).coerceIn(-0.2f, 1.2f),
            "appear.size" to ((settings["appear.size"] ?: .6f) * zoom).coerceIn(.05f, 2f),
            "appear.rot" to ((((settings["appear.rot"] ?: 0f) + rotation + 540f) % 360f) - 180f))
        pushParams()
    }

    /** Enciende o apaga un sonido de ambiente (volumen inicial 70 %). */
    fun toggleAmbient(id: String) {
        // v44: los ambientes son bloques de sonido de la línea de tiempo.
        val existing = effectClips.filter { it.effectId == com.zeta.fondo.model.SoundClips.clipId(id) }
        if (existing.isNotEmpty()) {
            val before = effectClips.toList()
            effectClips = effectClips - existing.toSet()
            commitTimeline(before); saveProject()
        } else addSound(id)
    }

    // ------------------------------------------------------------ v44: sonidos en la línea de tiempo

    /** Bloque de sonido abierto en Ajustar (null = ninguno). */
    var soundEditing by mutableStateOf<Long?>(null)
    var soundCategory by mutableStateOf(com.zeta.fondo.model.SoundCatalog.categories.first())

    /** Ajustes de audio con los bloques de sonido actuales. [forExport]: sin «Solo». */
    fun audioSettings(forExport: Boolean = false): ExportSettings {
        val solo = if (forExport) null else soloClip
        return exportSettings.copy(
            sounds = com.zeta.fondo.model.SoundClips.tracks(effectClips, settings, solo),
            soundDir = meta?.dir?.path ?: "",
            musicMuted = !forExport && com.zeta.fondo.model.SoundClips.musicMuted(effectClips, soloClip))
    }

    /** Añade un sonido en el cabezal: los ambientes hasta el final; los eventos, un golpe corto. */
    /**
     * v44.12 · Bloque de sonido que se está PROBANDO en el panel de Audio. Tocar otro sonido lo
     * cambia (no se suman); tocar el mismo lo quita. ✓ lo deja. Para sumar otro sonido se vuelve
     * a entrar en Audio.
     */
    var soundTrialUid by mutableStateOf<Long?>(null)
        private set

    fun isTrialSound(sound: String): Boolean = soundTrialUid?.let { uid ->
        effectClips.firstOrNull { it.uid == uid }?.effectId == com.zeta.fondo.model.SoundClips.clipId(sound) } == true

    fun trySound(sound: String) {
        val newId = com.zeta.fondo.model.SoundClips.clipId(sound)
        val trial = soundTrialUid?.let { uid -> effectClips.firstOrNull { it.uid == uid } }
        if (trial != null) {
            val before = effectClips.toList()
            effectClips = effectClips.filterNot { it.uid == trial.uid }
            soundTrialUid = null
            if (trial.effectId == newId) {        // el mismo: se quita
                selectedTimelineClip = null
                commitTimeline(before); saveProject()
                showHint("Sonido quitado", 1200L)
                return
            }
        }
        addSound(sound)
        soundTrialUid = selectedTimelineClip
        if (!playing) togglePlay()
        showHint("Probando este sonido. Toca otro para cambiarlo o ✓ para dejarlo. Para sumar otro, vuelve a abrir Audio.", 3600L)
    }

    fun addSound(sound: String) {
        val total = exportSettings.loopSec.toFloat()
        val event = com.zeta.fondo.engine.Ambient.isOneShot(sound) || com.zeta.fondo.model.SoundCatalog.find(sound)?.event == true
        val now = (scrubT * total).coerceIn(0f, total)
        // v44.11: como los efectos, un ambiente nuevo cubre de 0 s al final (máx. 6 s). Los
        // golpes cortos (whoosh, impacto, explosión, magia) siguen en la línea blanca.
        val span = ClipEdits.defaultSpan(total)
        val start = if (event) now.coerceAtMost((total - .5f).coerceAtLeast(0f)) else span.first
        val dur = if (event) minOf(1.6f, total - start) else span.second
        val id = com.zeta.fondo.model.SoundClips.clipId(sound)
        val ov = if (event) mapOf("$id.fadein" to 0f, "$id.fadeout" to .05f) else emptyMap()
        val uid = nextClipUid()
        val before = effectClips.toList()
        val clip = EffectClip(uid, id, start, dur.coerceAtLeast(ClipEdits.MIN_DURATION), overrides = ov)
        effectClips = effectClips + clip.copy(track = suggestTimelineTrack(uid, clip.startSec, clip.durationSec, 0))
        selectedTimelineClip = uid
        commitTimeline(before)
        saveProject()
        showHint("${com.zeta.fondo.model.SoundCatalog.title(sound)} añadido. Arrástralo o recórtalo en la línea de tiempo.", 2200L)
    }

    /** v44.17 · Música como bloque: empieza en 0 s y dura lo que dura la canción (máx. el vídeo). */
    private fun addMusicClip(sound: String, seconds: Float) {
        val total = exportSettings.loopSec.toFloat()
        val dur = com.zeta.fondo.model.SoundClips.musicSpan(seconds, total)
        val id = com.zeta.fondo.model.SoundClips.clipId(sound)
        val uid = nextClipUid()
        val before = effectClips.toList()
        val clip = EffectClip(uid, id, 0f, dur, overrides = mapOf("$id.vol" to 1f, "$id.fadein" to .15f, "$id.fadeout" to .4f))
        effectClips = effectClips + clip.copy(track = suggestTimelineTrack(uid, clip.startSec, clip.durationSec, 0))
        selectedTimelineClip = uid
        commitTimeline(before)
        saveProject()
        showHint("Música añadida (${String.format(java.util.Locale.US, "%.1f", dur)} s). Muévela con pulsación larga o recórtala como un efecto.", 2600L)
    }

    fun openSoundClip(uid: Long) {
        if (effectClips.none { it.uid == uid }) return
        if (editingEffect != null) finishEdit(true)
        beginEdit()
        soundEditing = uid
        selectedTimelineClip = uid
    }

    fun closeSoundClip(commit: Boolean) {
        soundEditing = null
        finishEdit(commit)
    }

    /** Valor actual (bloque, efecto o por defecto) de un ajuste de sonido. */
    fun soundValue(clip: EffectClip, suffix: String): Float {
        val key = "${clip.effectId}.$suffix"
        val local = keyLocal(clip)
        clip.keys[key]?.takeIf { it.isNotEmpty() && local != null }?.let { return com.zeta.fondo.model.Keyframes.valueAt(it, local!!)!! }
        return clip.overrides[key] ?: settings[key] ?: com.zeta.fondo.model.SoundClips.defaults[suffix] ?: 0f
    }

    fun setSoundValue(uid: Long, suffix: String, value: Float) {
        val clip = effectClips.firstOrNull { it.uid == uid } ?: return
        val key = "${clip.effectId}.$suffix"
        if (canKeyframe(key) && autoKey(key, value)) return
        effectClips = effectClips.map { if (it.uid == uid) it.copy(overrides = it.overrides + (key to value)) else it }
        scheduleAutosave()
    }

    // ------------------------------------------------------------ v44: Beat Sync

    /** Golpes de la música dentro del vídeo (segundos). Vacío = sin analizar. */
    var beatMarkers by mutableStateOf<List<Float>>(emptyList())
        private set
    var beatSensitivity by mutableFloatStateOf(.5f)

    /** Analiza la parte de la música que suena en el vídeo (sin IA) y marca sus golpes. */
    fun detectBeats() {
        val project = meta ?: return
        val e = exportSettings
        if (e.audioName.isBlank() || e.audioFrames <= 0L) { message = "Primero importa una música."; return }
        if (busy != null) return
        busy = "Buscando los golpes de la música…"
        val sens = beatSensitivity
        viewModelScope.launch {
            try {
                val beats = withContext(Dispatchers.Default) {
                    val reader = com.zeta.fondo.engine.PcmReader(java.io.File(project.dir, com.zeta.fondo.engine.ImportedAudio.FILE_NAME), e)
                    reader.use {
                        val frames = (e.loopSec.toLong() * e.audioRate).toInt()
                        val pcm = it.read(0L, frames)
                        com.zeta.fondo.engine.BeatDetect.onsets(com.zeta.fondo.engine.BeatDetect.toMono(pcm, e.audioChannels), e.audioRate, sens)
                    }
                }
                beatMarkers = beats.filter { it < e.loopSec - .05f }
                showHint(if (beatMarkers.isEmpty()) "No se encontraron golpes claros. Sube la sensibilidad."
                    else "${beatMarkers.size} golpes marcados en la línea de tiempo (el imán se pega a ellos).", 2600L)
            } catch (t: Throwable) {
                message = "No se pudo analizar la música: ${t.message}"
            } finally { busy = null }
        }
    }

    fun clearBeats() { beatMarkers = emptyList() }

    /** Añade un bloque corto de [effectId] (destello, impacto, onda…) en cada golpe marcado. */
    fun addEffectOnBeats(effectId: String) {
        if (beatMarkers.isEmpty()) return
        val total = exportSettings.loopSec.toFloat()
        val before = effectClips.toList()
        val beforeSettings = settings
        var uid = nextClipUid()
        val dur = when (effectId) { "impact" -> .45f; "shock" -> .5f; else -> .3f }
        val clips = beatMarkers.map { t ->
            EffectClip(uid++, effectId, t.coerceAtMost(total - dur), dur)
        }
        var next = settings + ("$effectId.on" to 1f)
        if (!next.containsKey("$effectId.mask")) next = next + ("$effectId.mask" to -2f)
        if (effectId in Effects.eventEffects) next = next + ("$effectId.mode" to EventMode.ONCE.toFloat())
        settings = next
        effectClips = effectClips + clips
        commitTimeline(before, beforeSettings)
        pushParams(); saveProject()
        showHint("${clips.size} bloques añadidos en los golpes. Puedes moverlos o borrarlos como cualquier bloque.", 2600L)
    }

    /**
     * Grabación propia del usuario (hasta 60 s) como sonido del proyecto.
     * v44.17 · [asMusic] = «+ Agregar música»: la música entra como un BLOQUE más (igual que un
     * efecto), desde 0 s y con su duración real (no ocupa todo el vídeo ni se repite sola).
     */
    fun importUserSound(uri: Uri, asMusic: Boolean = false) {
        val project = meta ?: return
        if (busy != null) return
        if (playing) togglePlay()
        busy = if (asMusic) "Importando música…" else "Importando sonido…"
        viewModelScope.launch {
            try {
                var seconds = 0f
                var cut = false
                val id = withContext(Dispatchers.IO) {
                    val dir = java.io.File(project.dir, com.zeta.fondo.engine.UserSounds.DIR).apply { mkdirs() }
                    val n = (dir.listFiles { f -> f.name.endsWith(".pcm") }?.size ?: 0) + 1
                    val sid = "${com.zeta.fondo.model.SoundClips.USER}$n"
                    val pcm = java.io.File(dir, "$sid.pcm")
                    // v44.25: solo se convierten los primeros MAX_SECONDS (antes, todo y luego se recortaba).
                    val d = com.zeta.fondo.engine.ImportedAudio.decode(getApplication(), uri, pcm,
                        com.zeta.fondo.engine.UserSounds.MAX_SECONDS)
                    cut = d.truncated
                    // Máximo 60 s: el resto se descarta (los sonidos se repiten sin clic).
                    val maxBytes = d.rate.toLong() * d.channels * 2 * com.zeta.fondo.engine.UserSounds.MAX_SECONDS
                    if (pcm.length() > maxBytes) java.io.RandomAccessFile(pcm, "rw").use { it.setLength(maxBytes) }
                    java.io.File(dir, "$sid.txt").writeText("${d.rate},${d.channels},${d.name.replace(',', ' ').take(40)}")
                    seconds = pcm.length().toFloat() / (d.rate.toLong() * d.channels * 2).coerceAtLeast(1)
                    com.zeta.fondo.model.SoundCatalog.userNames[sid] = d.name
                    sid
                }
                if (asMusic) addMusicClip(id, seconds) else addSound(id)
                // v44.25: se avisa del recorte (el vídeo dura como máximo 60 s).
                if (cut) message = if (asMusic) "Se usa el primer minuto de la música: el vídeo dura como máximo 60 s."
                    else "Se usa el primer minuto del sonido."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                message = (if (asMusic) "No se pudo importar la música: " else "No se pudo importar el sonido: ") +
                    (e.message ?: e.javaClass.simpleName)
            } finally { busy = null }
        }
    }

    fun setAmbientVolume(id: String, v: Float) {
        val e = exportSettings
        updateExport(e.copy(ambient = e.ambient + (id to v.coerceIn(0.02f, 1f))))
    }

    // ============================================================ v44: Diagnóstico

    /** Estado de la prueba de rendimiento (null = nunca ejecutada en esta sesión). */
    data class BenchState(
        val running: Boolean,
        val stage: Int,
        val stageSecLeft: Int,
        val waitingShader: Boolean,
        val results: List<com.zeta.fondo.diag.Benchmark.StageResult>,
        val report: String?
    )
    var benchmark by mutableStateOf<BenchState?>(null)
    private var benchJob: Job? = null

    fun appVersion(): String = try {
        val app = getApplication<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) { "?" }

    /**
     * Prueba 1 → 3 → 6 efectos, [com.zeta.fondo.diag.Benchmark.STAGE_SEC] s cada una, sobre la
     * imagen abierta. NO toca el proyecto: solo cambia lo que dibuja la vista previa y al
     * terminar (o al cancelar) se restaura todo. No se guarda nada mientras dura.
     */
    fun startBenchmark(stageSec: Int = com.zeta.fondo.diag.Benchmark.STAGE_SEC) {
        if (benchmark?.running == true || meta == null) return
        if (exportState is ExportState.Running) { message = "Espera a que termine la exportación."; return }
        val app = getApplication<Application>()
        val wasPlaying = playing
        benchmark = BenchState(true, 0, stageSec, true, emptyList(), null)
        hub.diagnostics = true
        benchJob = viewModelScope.launch {
            val results = ArrayList<com.zeta.fondo.diag.Benchmark.StageResult>()
            try {
                if (!playing) togglePlay()
                for ((i, count) in com.zeta.fondo.diag.Benchmark.stages.withIndex()) {
                    val ids = com.zeta.fondo.diag.Benchmark.effectsFor(count)
                    val s = Effects.defaults().mapValues { (k, v) -> if (k.endsWith(".on")) 0f else v } +
                        ids.associate { "$it.on" to 1f }
                    hub.params = RenderParams.from(s, aspect, exportSettings.loopSec.toFloat(), emptyList())
                    // Esperar a que el shader de esta combinación esté compilado (máx. 20 s).
                    val waitStart = System.nanoTime()
                    benchmark = benchmark?.copy(stage = i, stageSecLeft = stageSec, waitingShader = true)
                    val key = hub.params.variantKey
                    var waited = 0
                    while (hub.readyVariantKey != key && waited < 200) { delay(100); waited++ }
                    val waitMs = (System.nanoTime() - waitStart) / 1_000_000L
                    delay(1500) // estabilizar antes de medir
                    val thermalStart = com.zeta.fondo.diag.Benchmark.thermalName(com.zeta.fondo.diag.DeviceInfo.thermalStatus(app))
                    hub.stats.reset()
                    var left = stageSec
                    while (left > 0) {
                        benchmark = benchmark?.copy(stageSecLeft = left, waitingShader = false)
                        delay(1000); left--
                    }
                    results += com.zeta.fondo.diag.Benchmark.StageResult(count, stageSec, waitMs, hub.stats.snapshot(),
                        thermalStart, com.zeta.fondo.diag.Benchmark.thermalName(com.zeta.fondo.diag.DeviceInfo.thermalStatus(app)),
                        com.zeta.fondo.diag.DeviceInfo.ramAvailMb(app), true)
                    benchmark = benchmark?.copy(results = results.toList())
                }
            } finally {
                val partial = results.toList()
                hub.params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), renderClips(), soloActive)
                if (!wasPlaying && playing) togglePlay()
                val header = com.zeta.fondo.diag.DeviceInfo.header(app, hub.gpuInfo, appVersion())
                val report = com.zeta.fondo.diag.Benchmark.report(header, partial)
                com.zeta.fondo.diag.ZetaLog.info("Prueba", "Prueba de rendimiento: ${partial.size} de 3 etapas")
                benchmark = BenchState(false, partial.size, 0, false, partial, report)
            }
        }
    }

    fun stopBenchmark() {
        if (benchmark?.running == true) benchJob?.cancel()
    }

    /** Texto del informe (prueba + registro) en un archivo compartible. */
    fun shareDiagnostics(includeLog: Boolean) {
        val app = getApplication<Application>()
        val text = buildString {
            val b = benchmark?.report
            if (b != null) append(b) else {
                append("Zeta Motion · Informe de diagnóstico\n")
                for ((k, v) in com.zeta.fondo.diag.DeviceInfo.header(app, hub.gpuInfo, appVersion())) append(k).append(": ").append(v).append('\n')
                append("Calidad cine: ").append(hub.cineInfo.ifBlank { "sin usar" }).append('\n')
                append("Cuadros sin efectos: ").append(hub.blankFrames).append('\n')
                append("(No se ha ejecutado la prueba de rendimiento en esta sesión.)\n")
            }
            if (includeLog) {
                append("\n\n==== Registro de errores ====\n")
                append(com.zeta.fondo.diag.ZetaLog.readAll().ifBlank { "(vacío)" })
            }
        }
        try {
            val dir = java.io.File(app.cacheDir, "exports").apply { mkdirs() }
            val f = java.io.File(dir, "ZetaMotion_diagnostico.txt")
            f.writeText(text)
            com.zeta.fondo.export.ShareUtil.shareText(app, f)
        } catch (t: Throwable) {
            message = "No se pudo crear el informe: ${t.message}"
        }
    }

    // ============================================================ Exportar

    fun startExport() {
        val m = meta ?: return
        val mk = masks ?: return
        if (exportState is ExportState.Running) return
        if (ExportRunner.running) { message = "Ya hay una exportación en curso."; return }
        // v44.25: dos trabajos pesados a la vez agotaban la memoria en teléfonos modestos.
        if (srState is AiJob.Running) { message = "Espera a que termine «Mejorar imagen» para exportar."; return }
        if (capturing) { message = "Espera a que se guarde la captura."; return }
        flushParams()
        saveProject()
        lastProgressPost = -1f
        // v44.12: al exportar, la vista previa y su sonido se PAUSAN (antes seguían
        // reproduciéndose detrás y competían con la exportación).
        if (playing) togglePlay()
        exportState = ExportState.Running(0f)
        hub.renderEnabled = false

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val req = ExportRequest(
            imageFile = exportImage(m).first,
            imgW = exportImage(m).second,
            imgH = exportImage(m).third,
            name = "ZetaMotion_$stamp",
            settings = audioSettings(forExport = true),
            params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), effectClips),
            flow = hub.flow ?: FlowField.compute(ArrayList(arrows), ArrayList(anchors), aspect, settings["flow.reach"] ?: 0.25f),
            masks = mk,
            characterFile = java.io.File(m.dir, CHARACTER_FILE),
            boltCount = boltCount,
            depth = hub.depth,
            plate = hub.plate,
            layers = exportLayerStack(m)
        )
        val app = getApplication<Application>()
        val exporter = Exporter(app, vertSrc, fragSrc)
        // v44: el trabajo vive en el ámbito de la APLICACIÓN y un servicio en primer plano
        // mantiene la notificación: salir del editor o de la app ya no corta la exportación.
        ExportRunner.cancelFlag.set(false)
        ExportRunner.progress = 0f
        ExportRunner.running = true
        ExportService.start(app)
        ExportRunner.scope.launch {
            var title = "Vídeo exportado"
            var text = "Guardado en la Galería"
            try {
                val result = exporter.run(req, ExportRunner.cancelFlag) { p -> postProgress(p) }
                if (result.galleryPath.isNotBlank()) text = "Guardado en ${result.galleryPath}"
                main.post { exportState = ExportState.Done(result) }
            } catch (e: ExportCancelled) {
                title = "Exportación cancelada"; text = "No se guardó ningún vídeo"
                main.post { exportState = ExportState.Idle; message = "Exportación cancelada." }
            } catch (e: OutOfMemoryError) {
                com.zeta.fondo.diag.ZetaLog.error("Exportar", "Sin memoria al exportar", e)
                title = "No se pudo exportar"; text = "Sin memoria suficiente para esa resolución"
                main.post { exportState = ExportState.Failed("No hay memoria suficiente para esa resolución. Prueba con una menor.") }
            } catch (e: Throwable) {
                title = "No se pudo exportar"; text = e.message ?: e.toString()
                main.post { exportState = ExportState.Failed(e.message ?: e.toString()) }
            } finally {
                ExportRunner.running = false
                ExportService.stop(app)
                // Solo se avisa con notificación si el usuario no está mirando el editor.
                if (!appVisible) ExportService.finished(app, title, text)
                main.post { hub.renderEnabled = true }
            }
        }
    }

    // ============================================================ v44.23 · Zonas con forma
    //
    // Círculo o rectángulo que se arrastra, agranda y gira sobre la imagen (como en Alight
    // Motion) en vez de pintar la zona con el dedo. Se puede combinar con lo pintado.

    /** Forma de la zona del efecto [id] guardada (aunque esté sin usar). */
    fun zoneShapeOf(id: String): com.zeta.fondo.model.ZoneShapes.Shape? = com.zeta.fondo.model.ZoneShapes.of(settings, id)

    /** ¿La forma de [id] está en uso (es la zona elegida del efecto)? */
    fun zoneShapeActive(id: String): Boolean {
        if (com.zeta.fondo.model.ZoneShapes.of(settings, id) == null) return false
        val owned = (settings["$id.ownedMask"] ?: -1f).toInt()
        return owned >= com.zeta.fondo.model.ZoneShapes.FIRST_OWN && (settings["$id.mask"] ?: -2f).toInt() == owned
    }

    /** Zona propia del efecto (la crea si no existe). null = no quedan zonas libres. */
    private fun ensureOwnedZone(id: String): Int? {
        val mk = masks ?: return null
        var layer = (settings["$id.ownedMask"] ?: -1f).toInt()
        if (layer !in com.zeta.fondo.model.ZoneShapes.FIRST_OWN until mk.count) {
            layer = mk.addLayer(Effects.all.firstOrNull { it.id == id }?.title ?: id)
            if (layer < 0) { message = "Límite de nueve zonas independientes por proyecto."; return null }
            setSetting("$id.ownedMask", layer.toFloat())
            maskNames = mk.names.toList()
        }
        return layer
    }

    /** Usa un círculo o un rectángulo como zona del efecto (y lo deja listo para arrastrar). */
    fun setZoneShape(id: String, type: Int) {
        val slot = ensureOwnedZone(id) ?: return
        if (playing) togglePlay()
        val values = HashMap<String, Float>()
        if (com.zeta.fondo.model.ZoneShapes.of(settings, id) == null) values += com.zeta.fondo.model.ZoneShapes.initial(id, type, aspect)
        values["$id.zshape"] = type.toFloat()
        values["$id.mask"] = slot.toFloat()
        updateFx(id, values, null)
        if (paintingZone) finishPainting()
        tool = Tool.PAN
        if (id == "natural" || id == "flame") refreshEffectBoxes()
        maskRevision++
        showHint("Arrastra la forma · esquinas = tamaño · lados = estirar · punto rosa = girar", 3600L)
    }

    /** Quita la forma (la zona queda solo con lo pintado). */
    fun removeZoneShape(id: String) {
        updateFx(id, mapOf("$id.zshape" to 0f), null)
        if (id == "natural" || id == "flame") refreshEffectBoxes()
        maskRevision++
    }

    /** Arrastre de la forma (un solo «deshacer» por gesto, como Mover). */
    fun beginZoneTransform() = beginClipTransform()
    fun transformZone(id: String, values: Map<String, Float>) {
        val clean = values.mapValues { (k, v) ->
            when (k.substringAfter('.')) {
                "zx", "zy" -> v.coerceIn(-.5f, 1.5f)
                "zw", "zh" -> v.coerceIn(.01f, 3f)
                "zsoft" -> v.coerceIn(0f, 1f)
                else -> v
            }
        }
        updateFx(id, clean, null)
        hub.boostPreview(320)
    }
    fun endZoneTransform(id: String) {
        endClipTransform()
        if (id == "natural" || id == "flame") refreshEffectBoxes()
    }

    /** Bloque que mueve la herramienta Posición: el seleccionado o, editando, el del efecto. */
    fun placementClip(): EffectClip? = selectedClip()
        ?: editingEffect?.let { id -> clipsForEffect(id).firstOrNull { it.uid == selectedTimelineClip } ?: clipsForEffect(id).firstOrNull() }

    // ============================================================ v44.23 · Capas de imagen
    //
    // Imágenes extra encima del fondo, como las capas de Alight Motion o ibisPaint: se mueven,
    // agrandan y giran con tiradores, tienen opacidad, 29 modos de mezcla, movimientos propios
    // y fotogramas clave. Todo pasa por el mismo motor en la vista previa y al exportar.

    /** Capas de abajo arriba (la última se dibuja delante). */
    var layers by mutableStateOf<List<com.zeta.fondo.model.ImageLayer>>(emptyList())
        private set
    var selectedLayer by mutableStateOf<Long?>(null)
        private set
    /** Hoja «Mezcla y opacidad» de la capa seleccionada. */
    var blendPanelOpen by mutableStateOf(false)
    /** Hoja «Movimiento» de la capa seleccionada. */
    var layerMotionOpen by mutableStateOf(false)
    /** Diálogo de tiempo (inicio y duración) de una capa. */
    var layerTimeOpen by mutableStateOf<Long?>(null)
    /** Miniaturas pequeñas de cada imagen (línea de tiempo). */
    var layerThumbs by mutableStateOf<Map<String, Bitmap>>(emptyMap())
        private set
    var addingLayer by mutableStateOf(false)
        private set
    /** Imágenes de la vista previa por archivo (las usa la GPU). */
    private val layerBitmaps = HashMap<String, Bitmap>()
    private var layerEditBefore: List<com.zeta.fondo.model.ImageLayer>? = null

    fun selectedLayerObj(): com.zeta.fondo.model.ImageLayer? = selectedLayer?.let { id -> layers.firstOrNull { it.id == id } }

    private fun layerSec(): Float = scrubT.coerceIn(0f, 1f) * exportSettings.loopSec.toFloat()

    private fun syncLayerStack() {
        val m = meta
        hub.layers = if (m == null || layers.isEmpty()) null else
            com.zeta.fondo.engine.LayerStack(layers, exportSettings.loopSec.toFloat(), m.w, m.h, HashMap(layerBitmaps))
    }

    private fun loadLayers(m: ProjectMeta, list: List<com.zeta.fondo.model.ImageLayer>) {
        layerCrop = null
        textEditor = null
        // v44.25: no se reciclan a mano: el hilo de dibujo podía estar subiéndolas a la GPU en ese
        // mismo instante (cuadro perdido o parpadeo). Sin referencias, el sistema las libera solo.
        layerBitmaps.clear()
        layers = list
        selectedLayer = null; blendPanelOpen = false; layerMotionOpen = false; layerTimeOpen = null
        layerThumbs = emptyMap()
        hub.layers = null
        if (list.isEmpty()) return
        viewModelScope.launch {
            for (file in list.map { it.file }.distinct()) loadLayerBitmap(m, file)
        }
    }

    /** Decodifica la imagen de una capa para la vista previa y su miniatura. */
    private suspend fun loadLayerBitmap(m: ProjectMeta, file: String) {
        val side = com.zeta.fondo.model.ImageLayers.maxSide(lowResourceDevice, false)
        val pair = withContext(Dispatchers.IO) {
            try {
                val f = java.io.File(m.dir, file)
                val b = BitmapUtil.decodeAtMost(f, side)
                val th = 96
                val tw = (th * b.width / b.height.coerceAtLeast(1)).coerceIn(12, 360)
                b to Bitmap.createScaledBitmap(b, tw, th, true)
            } catch (e: Throwable) { null }
        }
        if (meta?.id != m.id || pair == null) { pair?.first?.recycle(); pair?.second?.recycle(); return }
        // v44.25: la anterior no se recicla aquí (la GPU puede estar usándola); se libera sola.
        layerBitmaps[file] = pair.first
        layerThumbs = layerThumbs + (file to pair.second)
        syncLayerStack()
    }

    private fun closeLayers(m: ProjectMeta?) {
        layerCrop = null
        textEditor = null
        val keep = layers
        hub.layers = null
        layerBitmaps.clear()   // v44.25: sin reciclar a mano (ver loadLayers)
        layers = emptyList(); selectedLayer = null; blendPanelOpen = false; layerMotionOpen = false
        layerThumbs = emptyMap()
        if (m != null) viewModelScope.launch(Dispatchers.IO) { try { store.cleanLayerFiles(m, keep) } catch (_: Throwable) {} }
    }

    /** Cambia la lista sin deshacer (lo usan deshacer/rehacer). */
    private fun applyLayers(list: List<com.zeta.fondo.model.ImageLayer>) {
        layers = list
        if (selectedLayer != null && list.none { it.id == selectedLayer }) { selectedLayer = null; blendPanelOpen = false; layerMotionOpen = false }
        val m = meta
        if (m != null) for (f in list.map { it.file }.distinct()) if (f !in layerBitmaps) viewModelScope.launch { loadLayerBitmap(m, f) }
        syncLayerStack()
    }

    /** Cambio con deshacer y guardado. */
    private fun commitLayers(list: List<com.zeta.fondo.model.ImageLayer>) {
        val before = layers
        if (before == list) return
        applyLayers(list)
        pushUndo(UndoAction.Layers(before, list))
        scheduleAutosave()
    }

    private fun updateLayer(id: Long, f: (com.zeta.fondo.model.ImageLayer) -> com.zeta.fondo.model.ImageLayer) {
        commitLayers(layers.map { if (it.id == id) com.zeta.fondo.model.ImageLayers.sanitize(f(it)) else it })
    }

    fun selectLayer(id: Long?) {
        val next = id?.takeIf { i -> layers.any { it.id == i } }
        if (next != null) {
            finishEdit(true)
            selectedTimelineClip = null
            imageSelected = false
            placingClip = false
            optionsOpen = false
        }
        if (next != selectedLayer) { blendPanelOpen = false; layerMotionOpen = false }
        selectedLayer = next
    }

    /** Agrega la imagen elegida como capa nueva (delante de todo) y la deja seleccionada. */
    fun addLayer(uri: Uri) = addLayers(listOf(uri))

    /**
     * v44.25 · Agrega una o VARIAS imágenes encima (capas). Se cargan UNA POR UNA, cada una ya al
     * tamaño justo (≤ 2048 px) y se guarda en el proyecto antes de pasar a la siguiente: la memoria
     * nunca guarda más de una foto grande a la vez. Todas entran como un solo paso (↶ las quita).
     * Si se eligen más de las que caben en este teléfono, se agregan las primeras y se avisa.
     */
    fun addLayers(uris: List<Uri>) {
        val m = meta ?: return
        if (uris.isEmpty()) return
        val limit = com.zeta.fondo.model.ImageLayers.maxLayers(lowResourceDevice)
        val room = limit - layers.size
        if (room <= 0) { message = "Máximo $limit imágenes por proyecto en este teléfono."; return }
        if (addingLayer || busy != null) return
        val unique = uris.distinct()
        val todo = unique.take(room)
        addingLayer = true
        busy = if (todo.size > 1) "Agregando imágenes… (1 de ${todo.size})" else "Agregando imagen…"
        viewModelScope.launch {
            val added = ArrayList<com.zeta.fondo.model.ImageLayer>()
            var failed = 0
            try {
                for ((n, uri) in todo.withIndex()) {
                    if (meta?.id != m.id) return@launch
                    if (todo.size > 1) busy = "Agregando imágenes… (${n + 1} de ${todo.size})"
                    val layer = try { importLayerFile(m, uri, layers.size + added.size + 1, (layers + added).maxOfOrNull { it.id } ?: 0L, n) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Throwable) {
                            failed++
                            com.zeta.fondo.diag.ZetaLog.warn("Capas", "No se pudo agregar: ${e.javaClass.simpleName} ${e.message}")
                            null
                        }
                    if (layer != null) { added += layer; loadLayerBitmap(m, layer.file) }
                }
                if (meta?.id != m.id || added.isEmpty()) {
                    if (added.isEmpty()) message = "No se pudo agregar la imagen."
                    return@launch
                }
                commitLayers(layers + added)
                selectLayer(added.last().id)
                message = when {
                    unique.size > todo.size -> "Se agregaron ${added.size}: máximo $limit imágenes por proyecto en este teléfono."
                    failed > 0 -> "Se agregaron ${added.size} de ${todo.size} (alguna no se pudo leer)."
                    added.size > 1 -> "Se agregaron ${added.size} imágenes."
                    else -> null
                }
                if (added.size == 1 && failed == 0 && unique.size <= todo.size) {
                    // v44.27: la primera vez se avisa del recorte (una sola vez).
                    if (!prefs.getBoolean("cropTip", false)) {
                        prefs.edit().putBoolean("cropTip", true).apply()
                        showHint("¿Te sobra una parte? Toca ✂ Recortar, justo debajo de la imagen.", 4500L)
                    } else showHint("Arrastra la imagen para moverla · esquinas = tamaño · punto de arriba = girar · Mezcla = cómo se combina")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                message = "No se pudo agregar la imagen: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                addingLayer = false
                busy = null
            }
        }
    }

    /** Copia una imagen al proyecto (≤ 2048 px, con el giro de la foto) y devuelve su capa. */
    private suspend fun importLayerFile(m: ProjectMeta, uri: Uri, position: Int, maxId: Long, batchIndex: Int): com.zeta.fondo.model.ImageLayer {
        val id = maxId + 1L
        val app = getApplication<Application>()
        val (file, aspect, rawName) = withContext(Dispatchers.IO) {
            val resolver = app.contentResolver
            val name = try {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            // v44.25: al tamaño justo y con el giro / espejo de la foto (antes, en algunos casos,
            // se decodificaba entera: con una foto grande no alcanzaba la memoria).
            val bmp: Bitmap = BitmapUtil.decodeUriAtMost(resolver, uri, 2048, store.readOrientation(uri))
            try {
                // Con transparencia → PNG (la conserva); sin transparencia → JPEG (rápido y liviano).
                val png = bmp.hasAlpha()
                val rel = "${com.zeta.fondo.model.LayerJson.DIR}/l_${System.currentTimeMillis()}_$id.${if (png) "png" else "jpg"}"
                val out = java.io.File(m.dir, rel)
                out.parentFile?.mkdirs()
                // Escritura segura: un corte a mitad no deja una imagen rota en el proyecto.
                val tmp = java.io.File(out.path + ".tmp")
                tmp.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 95, it) }
                if (!tmp.renameTo(out)) { tmp.delete(); error("No se pudo guardar la imagen") }
                Triple(rel, bmp.width.toFloat() / bmp.height.coerceAtLeast(1), name)
            } finally { bmp.recycle() }
        }
        val s = com.zeta.fondo.model.ImageLayers.initialScale(aspect, m.w, m.h)
        // Varias a la vez: cada una un poco desplazada para que no queden exactamente encima
        // (la primera, o una sola, en el centro como siempre).
        val shift = (batchIndex % 4) * .05f
        return com.zeta.fondo.model.ImageLayer(id = id,
            name = com.zeta.fondo.model.ImageLayers.displayName(rawName, position),
            file = file, aspect = aspect, sx = s, sy = s, x = .5f + shift, y = .5f + shift)
    }

    /** v44.25 · Imágenes compartidas a la vez: la primera abre el proyecto; el resto entra como capas. */
    private var afterOpenLayers: List<Uri> = emptyList()

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty() || busy != null) return
        afterOpenLayers = uris.drop(1)
        importImage(uris.first())
    }

    fun removeLayer(id: Long) {
        commitLayers(layers.filterNot { it.id == id })
        if (selectedLayer == id) selectLayer(null)
        showUndoNotice("Imagen eliminada")
    }

    fun duplicateLayer(id: Long) {
        val l = layers.firstOrNull { it.id == id } ?: return
        val limit = com.zeta.fondo.model.ImageLayers.maxLayers(lowResourceDevice)
        if (layers.size >= limit) { message = "Máximo $limit imágenes por proyecto en este teléfono."; return }
        val nid = (layers.maxOfOrNull { it.id } ?: 0L) + 1L
        val copy = l.copy(id = nid, name = l.name.take(34) + " (copia)", x = l.x + .04f, y = l.y + .04f)
        val i = layers.indexOfFirst { it.id == id }
        commitLayers(layers.toMutableList().apply { add(i + 1, copy) })
        selectLayer(nid)
    }

    /** +1 = una posición hacia delante; −1 = hacia atrás. */
    fun moveLayer(id: Long, dir: Int) {
        val i = layers.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in layers.indices) return
        commitLayers(layers.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun canMoveLayer(id: Long, dir: Int): Boolean {
        val i = layers.indexOfFirst { it.id == id }
        return i >= 0 && (i + dir) in layers.indices
    }

    fun toggleLayerHidden(id: Long) = updateLayer(id) { it.copy(hidden = !it.hidden) }
    fun toggleLayerBehind(id: Long) {
        updateLayer(id) { it.copy(behind = !it.behind) }
        val l = layers.firstOrNull { it.id == id }
        if (l?.behind == true && masks?.let { mk -> synchronized(mk.lock) { mk.strokes.getOrNull(0)?.isNotEmpty() } } != true)
            showHint("Pinta al personaje con Proteger para que la imagen quede detrás de él")
    }
    fun flipLayer(id: Long) = updateLayer(id) { it.copy(flipX = !it.flipX) }

    fun setLayerBlend(id: Long, mode: Int) {
        updateLayer(id) { it.copy(blend = mode.coerceIn(0, com.zeta.fondo.model.BlendModes.COUNT - 1)) }
    }

    /** Opacidad (0..1) en el cabezal: si está animada, crea/actualiza la clave. */
    fun setLayerOpacity(id: Long, v: Float, live: Boolean) {
        val sec = layerSec()
        val next = layers.map { if (it.id == id) com.zeta.fondo.model.ImageLayers.edit(it, sec, mapOf("opacity" to v.coerceIn(0f, 1f))) else it }
        if (live) { if (layerEditBefore == null) layerEditBefore = layers; applyLayers(next) }
        else { endLayerEdit(); commitLayers(next) }
    }

    fun setLayerMotion(id: Long, motion: Int) = updateLayer(id) { it.copy(motion = motion) }
    fun setLayerMotionAmt(id: Long, v: Float, live: Boolean) {
        val next = layers.map { if (it.id == id) it.copy(motionAmt = v.coerceIn(0f, 1f)) else it }
        if (live) { if (layerEditBefore == null) layerEditBefore = layers; applyLayers(next) } else { applyLayers(next); endLayerEdit() }
    }
    fun setLayerMotionCycles(id: Long, n: Int) = updateLayer(id) { it.copy(motionCycles = n.coerceIn(1, 8)) }
    /** v44.24 · Profundidad de la capa en el 2.5D (LayerParallax.AUTO = pegada a lo de debajo). */
    fun setLayerDepth(id: Long, d: Float) = updateLayer(id) { it.copy(depth = d) }

    fun setLayerSpan(id: Long, start: Float, dur: Float) {
        val total = exportSettings.loopSec.toFloat()
        val st = start.coerceIn(0f, (total - .1f).coerceAtLeast(0f))
        val d = dur.coerceIn(.1f, total - st)
        updateLayer(id) { it.copy(startSec = if (d >= total - .001f) 0f else st, durSec = if (st <= .001f && d >= total - .001f) 0f else d) }
    }

    fun renameLayer(id: Long, name: String) {
        val n = name.trim().take(40)
        if (n.isNotEmpty()) updateLayer(id) { it.copy(name = n) }
    }

    // ---- Mover / tamaño / giro con los tiradores (un solo «deshacer» por gesto)

    /** Estado de la capa en el cabezal SIN su movimiento automático (lo que muestran los tiradores). */
    fun layerHandleFrame(l: com.zeta.fondo.model.ImageLayer): com.zeta.fondo.model.LayerFrame? {
        val m = meta ?: return null
        return com.zeta.fondo.model.ImageLayers.frame(l.copy(motion = 0, durSec = 0f), scrubT, exportSettings.loopSec.toFloat(), m.w, m.h)
    }

    fun beginLayerTransform() {
        if (playing) togglePlay()
        if (layerEditBefore == null) layerEditBefore = layers
    }

    fun transformLayer(id: Long, values: Map<String, Float>) {
        val sec = layerSec()
        applyLayers(layers.map { if (it.id == id) com.zeta.fondo.model.ImageLayers.sanitize(com.zeta.fondo.model.ImageLayers.edit(it, sec, values)) else it })
        hub.boostPreview(260)
    }

    fun endLayerTransform() = endLayerEdit()

    private fun endLayerEdit() {
        val before = layerEditBefore ?: return
        layerEditBefore = null
        if (before != layers) { pushUndo(UndoAction.Layers(before, layers)); scheduleAutosave() }
    }

    /** Centra la capa y le devuelve su tamaño inicial y sin giro. */
    fun resetLayerTransform(id: Long) {
        val m = meta ?: return
        val l = layers.firstOrNull { it.id == id } ?: return
        val s = com.zeta.fondo.model.ImageLayers.initialScale(l.aspect, m.w, m.h)
        val sec = layerSec()
        updateLayer(id) { com.zeta.fondo.model.ImageLayers.edit(it, sec, mapOf("x" to .5f, "y" to .5f, "sx" to s, "sy" to s, "rot" to 0f)) }
    }

    /** Que la capa cubra toda la imagen (para fondos, texturas o luces a pantalla completa). */
    fun fillLayer(id: Long) {
        val m = meta ?: return
        val l = layers.firstOrNull { it.id == id } ?: return
        val sx = com.zeta.fondo.model.ImageLayers.coverScale(l.aspect, m.w, m.h)
        val sec = layerSec()
        updateLayer(id) { com.zeta.fondo.model.ImageLayers.edit(it, sec, mapOf("x" to .5f, "y" to .5f, "sx" to sx, "sy" to sx, "rot" to 0f)) }
    }

    // ---- v44.27 · Recortar una capa (en el mismo lugar, sin otra pantalla)

    /** Recorte en curso: la capa, su archivo original, una vista previa y sus medidas reales. */
    class LayerCropSession(val layerId: Long, val srcFile: String, val preview: Bitmap, val srcW: Int, val srcH: Int)

    var layerCrop by mutableStateOf<LayerCropSession?>(null)
        private set
    /** Marco en 0..1 de la imagen original girada [layerCropRot]. */
    var layerCropRect by mutableStateOf(com.zeta.fondo.model.CropRect.FULL)
        private set
    var layerCropRot by mutableStateOf(0)
        private set
    /** Proporción elegida (ancho/alto; 0 = libre). */
    var layerCropRatio by mutableStateOf(0f)
        private set
    private var layerCropOpening = false

    /** Abre el marco sobre la imagen ORIGINAL de la capa (con el recorte que ya tenga). */
    fun startLayerCrop(id: Long) {
        val m = meta ?: return
        val l0 = layers.firstOrNull { it.id == id } ?: return
        if (layerCrop != null || layerCropOpening || busy != null || addingLayer) return
        if (playing) togglePlay()
        blendPanelOpen = false; layerMotionOpen = false; layerTimeOpen = null
        layerCropOpening = true
        val srcFile = l0.src ?: l0.file
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    val f = java.io.File(m.dir, srcFile)
                    val (w, h) = BitmapUtil.bounds(f)
                    if (w <= 0 || h <= 0) null else Triple(BitmapUtil.decodeAtMost(f, 1280), w, h)
                }
                val l = layers.firstOrNull { it.id == id }
                if (meta?.id != m.id || l == null) return@launch
                if (r == null) { message = "No se pudo abrir la imagen para recortar."; return@launch }
                layerCropRect = com.zeta.fondo.model.LayerCrop.current(l)
                layerCropRot = com.zeta.fondo.model.LayerCrop.currentRot(l)
                layerCropRatio = 0f
                selectedLayer = id
                layerCrop = LayerCropSession(id, srcFile, r.first, r.second, r.third)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                message = "No se pudo abrir la imagen para recortar."
            } finally {
                layerCropOpening = false
            }
        }
    }

    fun moveLayerCropFrame(r: com.zeta.fondo.model.CropRect) {
        if (layerCrop == null) return
        layerCropRect = com.zeta.fondo.model.LayerCrop.clamp(r)
    }

    /** Proporción fija (o libre): el marco se ajusta al más grande que cabe, en el mismo centro. */
    fun chooseLayerCropRatio(ratio: Float) {
        val s = layerCrop ?: return
        layerCropRatio = ratio
        if (ratio > 0f) {
            val (rw, rh) = com.zeta.fondo.model.LayerCrop.rotatedSize(s.srcW, s.srcH, layerCropRot)
            layerCropRect = com.zeta.fondo.model.LayerCrop.fitRatio(layerCropRect, ratio, rw, rh)
        }
    }

    /** Gira 90° como se ve (con espejo, el giro va al revés en el original para que se vea igual). */
    fun rotateLayerCrop() {
        val s = layerCrop ?: return
        val flip = layers.firstOrNull { it.id == s.layerId }?.flipX == true
        val steps = if (flip) 3 else 1
        var r = layerCropRect
        repeat(steps) { r = com.zeta.fondo.model.LayerCrop.rotate90(r) }
        val rot = com.zeta.fondo.model.LayerCrop.normRot(layerCropRot + 90 * steps)
        if (layerCropRatio > 0f) {
            val (rw, rh) = com.zeta.fondo.model.LayerCrop.rotatedSize(s.srcW, s.srcH, rot)
            r = com.zeta.fondo.model.LayerCrop.fitRatio(r, layerCropRatio, rw, rh)
        }
        layerCropRot = rot
        layerCropRect = r
    }

    /** «Original»: la imagen completa, sin giro (se aplica con ✓). */
    fun resetLayerCrop() {
        if (layerCrop == null) return
        layerCropRect = com.zeta.fondo.model.CropRect.FULL
        layerCropRot = 0
        layerCropRatio = 0f
    }

    fun cancelLayerCrop() {
        // La vista previa no se recicla a mano: la pantalla puede estar dibujándola (se libera sola).
        layerCrop = null
    }

    /** ✓: guarda la copia recortada (el original no se toca) y deja la parte elegida en su lugar. */
    fun applyLayerCrop() {
        val s = layerCrop ?: return
        if (busy != null) return
        val m = meta ?: run { layerCrop = null; return }
        val l = layers.firstOrNull { it.id == s.layerId } ?: run { layerCrop = null; return }
        val rect = com.zeta.fondo.model.LayerCrop.clamp(layerCropRect)
        val rot = com.zeta.fondo.model.LayerCrop.normRot(layerCropRot)
        val cur = com.zeta.fondo.model.LayerCrop.current(l)
        val same = rot == com.zeta.fondo.model.LayerCrop.currentRot(l) &&
            kotlin.math.abs(rect.l - cur.l) < 1e-4f && kotlin.math.abs(rect.t - cur.t) < 1e-4f &&
            kotlin.math.abs(rect.r - cur.r) < 1e-4f && kotlin.math.abs(rect.b - cur.b) < 1e-4f
        if (same) { layerCrop = null; return }
        val full = com.zeta.fondo.model.LayerCrop.isFull(rect, rot)
        busy = "Recortando…"
        viewModelScope.launch {
            try {
                val newFile = if (full) s.srcFile else withContext(Dispatchers.IO) { writeLayerCrop(m, s, rect, rot, l.id) }
                val fresh = layers.firstOrNull { it.id == s.layerId }
                if (meta?.id != m.id || fresh == null) return@launch
                if (newFile !in layerBitmaps) loadLayerBitmap(m, newFile)
                if (meta?.id != m.id) return@launch
                val updated = com.zeta.fondo.model.LayerCrop.place(fresh, s.srcW, s.srcH, rect, rot, newFile,
                    m.w.toFloat() / m.h.coerceAtLeast(1), layerSec())
                layerCrop = null
                commitLayers(layers.map { if (it.id == fresh.id) updated else it })
                selectedLayer = fresh.id
                if (full) showHint("Imagen completa otra vez.", 1600L)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.warn("Capas", "No se pudo recortar: ${e.javaClass.simpleName} ${e.message}")
                message = "No se pudo recortar la imagen."
            } finally {
                busy = null
            }
        }
    }

    /** Copia recortada (y girada) del original: PNG si tiene transparencia, si no JPEG. */
    private fun writeLayerCrop(m: ProjectMeta, s: LayerCropSession, rect: com.zeta.fondo.model.CropRect, rot: Int, id: Long): String {
        val src = BitmapUtil.decodeAtMost(java.io.File(m.dir, s.srcFile), 4096)
        try {
            val px = com.zeta.fondo.model.LayerCrop.sourcePixels(src.width, src.height, rect, rot)
            val mtx = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
            val out = Bitmap.createBitmap(src, px[0], px[1], px[2], px[3], mtx, true)
            try {
                val png = out.hasAlpha()
                val rel = "${com.zeta.fondo.model.LayerJson.DIR}/l_${System.currentTimeMillis()}_${id}c.${if (png) "png" else "jpg"}"
                val f = java.io.File(m.dir, rel)
                f.parentFile?.mkdirs()
                val tmp = java.io.File(m.dir, "$rel.tmp")
                tmp.outputStream().use { out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 95, it) }
                if (!tmp.renameTo(f)) { tmp.delete(); error("No se pudo guardar el recorte") }
                return rel
            } finally {
                if (out !== src) out.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    /** Gira la capa 90° exactos (sentido del reloj), con deshacer. */
    fun rotateLayer90(id: Long) {
        val l = layers.firstOrNull { it.id == id } ?: return
        val cur = com.zeta.fondo.model.ImageLayers.valueAt(l, "rot", layerSec())
        var next = (kotlin.math.floor(cur / 90f + 1e-3f) + 1f) * 90f
        if (l.keys["rot"].isNullOrEmpty() && next >= 360f) next -= 360f
        beginLayerTransform()
        transformLayer(id, mapOf("rot" to next))
        endLayerTransform()
    }

    // ---- v44.27 · Texto (una capa más: los efectos pasan por detrás de las letras)

    /** Texto que se está escribiendo: [layerId] null = texto nuevo. */
    class TextEdit(val layerId: Long?, val spec: com.zeta.fondo.model.TextSpec)

    var textEditor by mutableStateOf<TextEdit?>(null)
        private set
    /** El último estilo usado: el siguiente texto nuevo empieza igual. */
    private var lastTextStyle = com.zeta.fondo.model.TextSpec("")

    fun openNewText() {
        if (meta == null || busy != null || addingLayer || layerCrop != null) return
        val limit = com.zeta.fondo.model.ImageLayers.maxLayers(lowResourceDevice)
        if (layers.size >= limit) { message = "Máximo $limit imágenes y textos por proyecto en este teléfono."; return }
        if (playing) togglePlay()
        textEditor = TextEdit(null, lastTextStyle.copy(text = ""))
    }

    fun editTextLayer(id: Long) {
        val t = layers.firstOrNull { it.id == id }?.text ?: return
        if (busy != null || layerCrop != null) return
        if (playing) togglePlay()
        blendPanelOpen = false; layerMotionOpen = false
        textEditor = TextEdit(id, t)
    }

    fun updateTextDraft(spec: com.zeta.fondo.model.TextSpec) {
        val e = textEditor ?: return
        textEditor = TextEdit(e.layerId, com.zeta.fondo.model.TextLayers.sanitize(spec))
    }

    fun closeTextEditor() { textEditor = null }

    /** ✓: dibuja el texto UNA vez (imagen transparente) y lo agrega o lo cambia, con deshacer. */
    fun applyTextEditor() {
        val e = textEditor ?: return
        if (busy != null) return
        val m = meta ?: run { textEditor = null; return }
        val spec = com.zeta.fondo.model.TextLayers.sanitize(e.spec)
        if (spec.text.isBlank()) { textEditor = null; return }
        val old = e.layerId?.let { id -> layers.firstOrNull { it.id == id } }
        if (e.layerId != null && old == null) { textEditor = null; return }
        if (old != null && old.text == spec) { textEditor = null; return }
        val limit = com.zeta.fondo.model.ImageLayers.maxLayers(lowResourceDevice)
        if (old == null && layers.size >= limit) { message = "Máximo $limit imágenes y textos por proyecto en este teléfono."; return }
        lastTextStyle = spec.copy(text = "")
        busy = if (old == null) "Creando texto…" else "Actualizando texto…"
        viewModelScope.launch {
            try {
                val id = old?.id ?: ((layers.maxOfOrNull { it.id } ?: 0L) + 1L)
                val (file, aspect) = withContext(Dispatchers.IO) { writeTextImage(m, spec, id) }
                if (meta?.id != m.id) return@launch
                loadLayerBitmap(m, file)
                if (meta?.id != m.id) return@launch
                textEditor = null
                if (old == null) {
                    val s0 = com.zeta.fondo.model.ImageLayers.initialScale(aspect, m.w, m.h)
                    val layer = com.zeta.fondo.model.ImageLayers.sanitize(com.zeta.fondo.model.ImageLayer(
                        id = id, name = com.zeta.fondo.model.TextLayers.displayName(spec.text), file = file,
                        aspect = aspect, sx = s0, sy = s0, text = spec))
                    commitLayers(layers + layer)
                    selectLayer(id)
                    if (!prefs.getBoolean("textTip", false)) {
                        prefs.edit().putBoolean("textTip", true).apply()
                        showHint("Los efectos pasan por detrás de las letras. ✎ Editar, debajo del texto, lo cambia.", 4500L)
                    }
                } else {
                    val fresh = layers.firstOrNull { it.id == old.id } ?: return@launch
                    commitLayers(layers.map { if (it.id == fresh.id) com.zeta.fondo.model.TextLayers.keepLetterSize(fresh, aspect, file, spec) else it })
                    selectLayer(fresh.id)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Throwable) {
                // El editor sigue abierto: no se pierde lo escrito.
                com.zeta.fondo.diag.ZetaLog.warn("Texto", "No se pudo crear: ${e.javaClass.simpleName} ${e.message}")
                message = "No se pudo crear el texto."
            } finally {
                busy = null
            }
        }
    }

    /** Imagen del texto (PNG transparente, lado largo ≤ 2048) dentro del proyecto. */
    private fun writeTextImage(m: ProjectMeta, spec: com.zeta.fondo.model.TextSpec, id: Long): Pair<String, Float> {
        val bmp = com.zeta.fondo.engine.TextRender.render(spec, 2048)
        try {
            val rel = "${com.zeta.fondo.model.LayerJson.DIR}/t_${System.currentTimeMillis()}_$id.png"
            val f = java.io.File(m.dir, rel)
            f.parentFile?.mkdirs()
            val tmp = java.io.File(m.dir, "$rel.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(f)) { tmp.delete(); error("No se pudo guardar el texto") }
            return rel to bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        } finally {
            bmp.recycle()
        }
    }

    // ---- Fotogramas clave

    fun layerHasKeyHere(id: Long, props: Collection<String>): Boolean =
        layers.firstOrNull { it.id == id }?.let { com.zeta.fondo.model.ImageLayers.hasKeyAt(it, layerSec(), props) } == true

    fun layerAnimated(id: Long, props: Collection<String>): Boolean =
        layers.firstOrNull { it.id == id }?.let { com.zeta.fondo.model.ImageLayers.animated(it, props) } == true

    /** Rombo: sin clave en el cabezal → la pone con el valor actual; con clave → la quita. */
    fun toggleLayerKey(id: Long, props: Collection<String>) {
        if (playing) togglePlay()
        val sec = layerSec()
        updateLayer(id) { l ->
            if (com.zeta.fondo.model.ImageLayers.hasKeyAt(l, sec, props)) com.zeta.fondo.model.ImageLayers.removeKeys(l, sec, props)
            else com.zeta.fondo.model.ImageLayers.putKeys(l, sec, props.associateWith { com.zeta.fondo.model.ImageLayers.valueAt(l, it, sec) })
        }
    }

    fun setLayerKeyCurve(id: Long, props: Collection<String>, curve: Int) {
        val sec = layerSec()
        updateLayer(id) { com.zeta.fondo.model.ImageLayers.setCurve(it, sec, props, curve) }
    }

    fun clearLayerKeys(id: Long, props: Collection<String>) {
        val sec = layerSec()
        updateLayer(id) { l ->
            var out = l
            for (p in props) {
                val v = com.zeta.fondo.model.ImageLayers.valueAt(l, p, sec)
                out = com.zeta.fondo.model.ImageLayers.withBase(out.copy(keys = out.keys - p), p, v)
            }
            out
        }
    }

    fun layerKeyCurve(id: Long, props: Collection<String>): Int? {
        val l = layers.firstOrNull { it.id == id } ?: return null
        val sec = layerSec()
        return props.firstNotNullOfOrNull { com.zeta.fondo.model.Keyframes.at(l.keys[it], sec)?.curve }
    }

    /** Salta al fotograma clave anterior (−1) o siguiente (+1) de la capa. */
    fun seekLayerKey(id: Long, dir: Int) {
        val l = layers.firstOrNull { it.id == id } ?: return
        val total = exportSettings.loopSec.toFloat().coerceAtLeast(.1f)
        val sec = layerSec()
        val times = com.zeta.fondo.model.ImageLayers.keyTimes(l)
        val target = if (dir < 0) times.lastOrNull { it < sec - .02f } else times.firstOrNull { it > sec + .02f }
        target ?: return
        if (playing) togglePlay()
        scrubTo((target / total).coerceIn(0f, .999f))
    }

    fun openBlendPanel() {
        if (playing) togglePlay()
        layerMotionOpen = false
        blendPanelOpen = true
    }

    /** Para exportar: las capas con sus archivos (se decodifican a resolución de exportación). */
    private fun exportLayerStack(m: ProjectMeta): com.zeta.fondo.engine.LayerStack? {
        if (layers.none { !it.hidden }) return null
        return com.zeta.fondo.engine.LayerStack(layers, exportSettings.loopSec.toFloat(), m.w, m.h,
            files = layers.associate { it.file to java.io.File(m.dir, it.file) })
    }

    // ============================================================ v44.16 · Captura

    var capturing by mutableStateOf(false)
        private set

    /**
     * Guarda el cuadro que se ve ahora (con todos los efectos, a la calidad de exportación) como
     * imagen PNG en Galería › Pictures/ZetaMotion/Capturas. No para la vista previa.
     */
    fun captureFrame() {
        val m = meta ?: return
        val mk = masks ?: return
        if (capturing) return
        if (ExportRunner.running) { message = "Espera a que termine la exportación."; return }
        if (srState is AiJob.Running) { message = "Espera a que termine «Mejorar imagen»."; return }
        flushParams()
        if (playing) togglePlay()
        capturing = true
        val t = scrubT
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val img = exportImage(m)
        val req = ExportRequest(
            imageFile = img.first, imgW = img.second, imgH = img.third,
            name = "Capturas",
            settings = audioSettings(forExport = true).copy(format = 1),
            params = RenderParams.from(renderSettings(), aspect, exportSettings.loopSec.toFloat(), effectClips),
            flow = hub.flow ?: FlowField.compute(ArrayList(arrows), ArrayList(anchors), aspect, settings["flow.reach"] ?: 0.25f),
            masks = mk,
            characterFile = java.io.File(m.dir, CHARACTER_FILE),
            boltCount = boltCount,
            depth = hub.depth,
            plate = hub.plate,
            layers = exportLayerStack(m)
        )
        val app = getApplication<Application>()
        val exporter = Exporter(app, vertSrc, fragSrc)
        ExportRunner.scope.launch {
            val msg = try {
                val r = exporter.still(req, t, "captura_$stamp.png")
                "Captura guardada en ${r.galleryPath} (${r.width}×${r.height})"
            } catch (e: OutOfMemoryError) {
                "Sin memoria para la captura a esa resolución: baja la resolución en Exportar."
            } catch (e: Throwable) {
                com.zeta.fondo.diag.ZetaLog.warn("captura", "${e.javaClass.simpleName} ${e.message}")
                "No se pudo guardar la captura (${e.message ?: e.javaClass.simpleName})."
            }
            main.post { capturing = false; message = msg }
        }
    }

    fun notificationsAsked(): Boolean = prefs.getBoolean("notifAsked", false)
    fun markNotificationsAsked() { prefs.edit().putBoolean("notifAsked", true).apply() }

    /** v44: la app está en primer plano (lo actualiza MainActivity). */
    @Volatile var appVisible = true

    private fun postProgress(p: Float) {
        if (p - lastProgressPost < 0.005f && p < 1f) return
        val notify = (p * 100f).toInt() != (lastProgressPost * 100f).toInt()
        lastProgressPost = p
        ExportRunner.progress = p
        if (notify) ExportService.update(getApplication(), p)
        main.post {
            if (exportState is ExportState.Running) exportState = ExportState.Running(p)
        }
    }

    fun cancelExport() {
        ExportRunner.cancel()
    }

    fun dismissExportResult() {
        exportState = ExportState.Idle
    }

    /**
     * Resolución de edición adaptativa. Mantiene suficiente detalle para hacer zoom
     * sin obligar a teléfonos modestos a conservar una textura 4K durante toda la edición.
     */
    private fun previewTargetMaxSide(): Int {
        val app = getApplication<Application>()
        val dm = app.resources.displayMetrics
        val screenLong = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(720)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val cap = when {
            am?.isLowRamDevice == true -> 2304
            (am?.memoryClass ?: 256) < 256 -> 2560
            else -> 3072
        }
        return (screenLong * 2).coerceIn(2048, cap)
    }

    override fun onCleared() {
        // v44: la exportación NO se cancela al cerrarse la pantalla: sigue en el servicio
        // con su notificación (Cancelar está ahí).
        super.onCleared()
    }

    companion object {
        const val EDIT_PREVIEW_MAX = 2560
        const val CHARACTER_FILE = "character.png"
        const val PLATE_FILE = "plate.png"
        const val PLATE_META = "plate.txt"
    }
}
