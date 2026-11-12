package com.zeta.fondo.engine

import android.graphics.Bitmap
import com.zeta.fondo.model.Effects

/**
 * Puente entre la interfaz (hilo principal) y el hilo de OpenGL de la vista previa.
 * Todo son referencias @Volatile que se reemplazan enteras: el hilo de OpenGL
 * detecta el cambio comparando identidad y vuelve a subir la textura.
 */
class RenderHub {
    private val revisionCounter = java.util.concurrent.atomic.AtomicLong()
    val revision: Long get() = revisionCounter.get()
    /** Lo llama la vista previa para dibujar en cuanto cambia algo (sin sondear cada 16 ms). */
    @Volatile var onInvalidate: (() -> Unit)? = null
    fun invalidate() {
        revisionCounter.incrementAndGet()
        onInvalidate?.invoke()
    }
    @Volatile var showOriginal = false
        set(value) { if (field != value) { field = value; invalidate() } }
    @Volatile var image: Bitmap? = null
        set(value) { field = value; invalidate() }
    /** Personaje PNG del efecto Aparición (null = sin personaje). */
    @Volatile var character: Bitmap? = null
        set(value) { field = value; invalidate() }
    /** Proporción ancho/alto del encuadre mostrado; el renderer centra el cuadro sin recrear la superficie. */
    @Volatile private var batchingView = false
    @Volatile var displayAspect = 1f
        set(value) {
            if (field == value) return
            field = value
            if (!batchingView) invalidate()
        }
    /** v45: mapa de profundidad IA del proyecto (null = 2.5D manual). */
    @Volatile var depth: DepthMap? = null
        set(value) { field = value; invalidate() }
    /** v46: fondo rellenado detrás del personaje (null = sin placa). */
    @Volatile var plate: PlateMap? = null
        set(value) { field = value; invalidate() }
    /** v45: «Ver mapa de profundidad» en la vista previa. */
    @Volatile var depthView = false
        set(value) { if (field != value) { field = value; invalidate() } }
    /** v44.23: capas de imagen encima del fondo (null = ninguna). */
    @Volatile var layers: LayerStack? = null
        set(value) { field = value; invalidate() }
    @Volatile var flow: FlowField.Data? = null
        set(value) { field = value; invalidate() }
    @Volatile var masks: MaskLayers? = null
        set(value) { field = value; invalidate() }
    @Volatile var params: RenderParams = RenderParams.from(Effects.defaults(), 1f)
        set(value) { field = value; invalidate() }

    @Volatile var viewScale = 1f
        set(value) {
            if (field == value) return
            field = value
            if (!batchingView) invalidate()
        }
    @Volatile var viewX = 0f
        set(value) {
            if (field == value) return
            field = value
            if (!batchingView) invalidate()
        }
    @Volatile var viewY = 0f
        set(value) {
            if (field == value) return
            field = value
            if (!batchingView) invalidate()
        }
    @Volatile var showMask = false   // v44.24: nunca empieza en modo pintar
        set(value) { field = value; invalidate() }
    /** v39: transparencia de la máscara mientras se pinta. */
    @Volatile var maskAlpha = .45f
        set(value) { if (field != value) { field = value; invalidate() } }
    @Volatile var activeMask = 0
        set(value) { field = value; invalidate() }
    @Volatile var renderEnabled = true
        set(value) { field = value; invalidate() }

    // v25: durante gestos (zoom/pan, pincel, timeline y sliders) la vista previa
    // pide cuadros a 60 fps durante un breve intervalo. Fuera de interacción vuelve
    // automáticamente a una cadencia más ligera para no calentar ni saturar el móvil.
    @Volatile private var interactionBoostUntilNs = 0L
    /** Último toque del usuario (zoom, pincel, deslizadores): decide cuándo hay calma. */
    @Volatile var lastInteractionNs = 0L
        private set
    fun boostPreview(ms: Long = 240L) {
        val now = System.nanoTime()
        lastInteractionNs = now
        interactionBoostUntilNs = maxOf(interactionBoostUntilNs, now + ms * 1_000_000L)
        invalidate()
    }
    /** Milisegundos sin interacción (sin gestos activos). */
    fun idleMs(): Long = if (gestureActive) 0L else (System.nanoTime() - lastInteractionNs) / 1_000_000L

    /** Pide un cuadro más tarde (lo usa el renderer para compilar efectos en un momento tranquilo). */
    @Volatile var onRenderLater: ((Long) -> Unit)? = null
    /** Se llama cuando el shader completo de efectos ya está listo. */
    @Volatile var onFullReady: (() -> Unit)? = null
    /** v41: true mientras se compila la variante del shader con los efectos actuales. */
    @Volatile var onCompiling: ((Boolean) -> Unit)? = null
    fun highFpsRequested(): Boolean = System.nanoTime() < interactionBoostUntilNs

    /**
     * Gesto de dos dedos en curso. Si el teléfono no llega a dibujar los efectos a
     * tiempo (medido por el renderer), mientras dura el gesto se muestra solo la imagen:
     * el zoom responde al instante y los efectos vuelven al soltar.
     */
    /** Fracción de la altura de la superficie tapada por el panel inferior (0..0.7). */
    @Volatile var bottomInset = 0f
        set(value) { if (field != value) { field = value; invalidate() } }

    /** v39: el lienzo está cambiando de tamaño (panel abriéndose/cerrándose). */
    @Volatile var resizing = false
        set(value) { if (field != value) { field = value; invalidate() } }

    @Volatile var gestureActive = false
        set(value) {
            if (field != value) {
                field = value
                lastInteractionNs = System.nanoTime()
                invalidate()
            }
        }

    @Volatile var viewWidth = 1f
    @Volatile var viewHeight = 1f

    /** Actualiza zoom/pan como un único estado para no despertar el renderer varias veces. */
    fun updateView(displayAspect: Float, width: Float, height: Float, scale: Float, x: Float, y: Float) {
        batchingView = true
        try {
            this.displayAspect = displayAspect
            viewWidth = width
            viewHeight = height
            viewScale = scale
            viewX = x
            viewY = y
        } finally {
            batchingView = false
        }
        invalidate()
    }

    @Volatile var onError: ((String) -> Unit)? = null
    /** Avisos ligeros para que Compose muestre el thumbnail mientras OpenGL prepara el primer cuadro. */
    @Volatile var onFramePending: (() -> Unit)? = null
    @Volatile var onFrameReady: (() -> Unit)? = null

    @Volatile private var loopSec = 6f
    // v37: el editor SIEMPRE abre en pausa; solo el botón Play inicia la reproducción.
    @Volatile private var playing = false
    @Volatile private var pausedT = 0f
    @Volatile private var startNs = System.nanoTime()

    @Volatile private var frameRate = 30

    /**
     * Fase del bucle en la MISMA rejilla de fotogramas que la exportación:
     * t = índice / (segundos × fps). Así cada cuadro de la vista previa es idéntico
     * al cuadro correspondiente del MP4 y el último cuadro nunca repite el primero.
     */
    fun currentT(): Float {
        val raw = if (!playing) pausedT.toDouble() else {
            val sec = (System.nanoTime() - startNs) / 1e9
            val t = (sec / loopSec) % 1.0
            if (t < 0) 0.0 else t
        }
        return LoopClock.quantize(raw, loopSec, frameRate)
    }

    fun setFrameRate(fps: Int) {
        val f = fps.coerceIn(12, 120)
        if (f != frameRate) { frameRate = f; invalidate() }
    }

    fun setLoopSec(sec: Float) {
        val next = if (sec > 0.5f) sec else 0.5f
        // Sin cambio real no se toca el reloj: antes cada ajuste de un control
        // recolocaba el cabezal y la reproducción daba pequeños saltos atrás.
        if (next == loopSec) return
        val t = currentT()
        loopSec = next
        startNs = System.nanoTime() - (t * loopSec * 1e9).toLong()
    }

    fun setPlaying(p: Boolean) {
        if (p == playing) return
        if (!p) {
            pausedT = currentT()
            playing = false
        } else {
            startNs = System.nanoTime() - (pausedT * loopSec * 1e9).toLong()
            playing = true
        }
    }

    fun setPausedT(t: Float) {
        pausedT = t.coerceIn(0f, 0.9999f)
        invalidate()
    }

    fun pausedT(): Float = pausedT

    /** v42: reproduciendo (el renderer baja la resolución si el teléfono no llega). */
    fun isPlaying(): Boolean = playing
    /** v42: cuadros por segundo de la reproducción. */
    fun frameRate(): Int = frameRate

    // ------------------------------------------------------------ v44: diagnóstico
    /** Estadísticas de la vista previa (las escribe el hilo GL, las lee Diagnóstico). */
    val stats = com.zeta.fondo.diag.FrameStats()
    /** Medir también la GPU (consultas de tiempo). Solo mientras Diagnóstico está abierto. */
    @Volatile var diagnostics = false
    /** «GPU · versión GL» del teléfono, leído al crear la superficie. */
    @Volatile var gpuInfo: String = ""
    /** v44.19 · Estado de «Calidad cine» (formato de la pirámide o motivo del fallo). */
    @Volatile var cineInfo: String = ""
    /** v44.22 · variantes probables (clave, efectos) para preparar en segundo plano. */
    /** v44.22 · cuadros de la vista previa que no pudieron dibujar los efectos (Diagnóstico). */
    @Volatile var blankFrames: Int = 0
    @Volatile var warmVariants: List<Pair<String, Set<String>>> = emptyList()
    /** null = aún no se sabe; true/false = el teléfono tiene (o no) temporizador de GPU. */
    @Volatile var gpuTimerAvailable: Boolean? = null
    /** Variante del shader que la vista previa ya está dibujando (null = ninguna aún). */
    @Volatile var readyVariantKey: String? = null
    /** Cuadros por segundo que la vista previa pide de verdad al reproducir (con el tope de gama baja). */
    @Volatile var previewFps = 30
    /** Escala de render del último cuadro (1 = resolución completa de la vista previa). */
    @Volatile var lastRenderScale = 1f
    /** Último error de OpenGL recuperable (para el registro). */
    @Volatile var onGlWarning: ((String) -> Unit)? = null
}
