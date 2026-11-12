package com.zeta.fondo.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v44.23 · Capas de imagen: imágenes extra encima del fondo (personajes, objetos, luces, logos,
 * texturas…), cada una con su posición, tamaño, giro, opacidad y modo de mezcla.
 *
 * Coordenadas (iguales a las del resto del proyecto):
 *  - [x], [y]: centro de la capa en fracción de la imagen base (0..1, y hacia abajo);
 *  - [sx]: ancho de la capa en fracción del ANCHO de la imagen base;
 *  - [sy]: igual que sx pero para el alto: alto = sy · anchoBase / aspecto de la capa.
 *    Con sx = sy la imagen conserva su proporción; los tiradores de los lados las separan;
 *  - [rot]: grados, positivo = sentido horario en pantalla.
 *
 * Fotogramas clave por propiedad ("x", "y", "sx", "sy", "rot", "opacity") en SEGUNDOS del vídeo.
 * Los movimientos ([motion]) cierran el bucle exacto (ciclos enteros por vuelta).
 * Código puro: lo usan la interfaz, la vista previa, la exportación y las pruebas JVM.
 */
data class ImageLayer(
    val id: Long,
    val name: String,
    /** Archivo dentro de la carpeta del proyecto (p. ej. "layers/l_123.png"). */
    val file: String,
    /** Ancho / alto de la imagen original. */
    val aspect: Float,
    val x: Float = .5f,
    val y: Float = .5f,
    val sx: Float = .6f,
    val sy: Float = .6f,
    val rot: Float = 0f,
    val opacity: Float = 1f,
    val blend: Int = BlendModes.NORMAL,
    val hidden: Boolean = false,
    /** Detrás del personaje: no se dibuja donde está pintado Proteger. */
    val behind: Boolean = false,
    val flipX: Boolean = false,
    val motion: Int = ImageLayers.MOTION_NONE,
    /** Fuerza del movimiento (0..1). */
    val motionAmt: Float = .5f,
    /** Vueltas del movimiento por bucle (entero ≥ 1: el vídeo empalma sin salto). */
    val motionCycles: Int = 1,
    /** Tramo visible en segundos (dur ≤ 0 = todo el vídeo). */
    val startSec: Float = 0f,
    val durSec: Float = 0f,
    val keys: Map<String, List<Keyframe>> = emptyMap(),
    /** v44.24 · Profundidad en el 2.5D: [LayerParallax.AUTO] = pegada a lo que tiene debajo; 0..1 = a mano. */
    val depth: Float = LayerParallax.AUTO,
    /**
     * v44.27 · Recorte ([LayerCrop]): archivo ORIGINAL de la capa (null = [file] ya es el original).
     * [cropL]..[cropB] = el marco en 0..1 del original girado [cropRot] (0/90/180/270).
     */
    val src: String? = null,
    val cropL: Float = 0f,
    val cropT: Float = 0f,
    val cropR: Float = 1f,
    val cropB: Float = 1f,
    val cropRot: Int = 0,
    /** v44.27 · Capa de texto ([TextLayers]): el texto y su estilo; null = imagen. */
    val text: TextSpec? = null
)

/** Estado de una capa en un instante: lo que dibuja la GPU. */
data class LayerFrame(
    val x: Float, val y: Float,
    /** Medio ancho y medio alto en PÍXELES de la imagen base. */
    val halfW: Float, val halfH: Float,
    /** Radianes. */
    val rot: Float,
    val opacity: Float,
    val flipX: Boolean
)

object ImageLayers {
    const val MOTION_NONE = 0
    const val MOTION_FLOAT = 1
    const val MOTION_PULSE = 2
    const val MOTION_SWAY = 3
    const val MOTION_SPIN = 4
    const val MOTION_FLICKER = 5
    const val MOTION_BREATHE = 6
    const val MOTION_DRIFT = 7

    val motionNames = listOf("Ninguno", "Flotar", "Latir", "Balanceo", "Girar", "Parpadeo de luz", "Respirar luz", "Deriva")
    val motionHelp = listOf(
        "La capa se queda quieta (o con sus fotogramas clave)",
        "Sube y baja suave",
        "Crece y vuelve, como un latido",
        "Se inclina a un lado y al otro",
        "Da vueltas completas",
        "La luz parpadea como una lámpara o fuego",
        "La luz sube y baja despacio",
        "Se desplaza en un círculo pequeño"
    )

    /** Propiedades animables con fotogramas clave. */
    val keyProps = listOf("x", "y", "sx", "sy", "rot", "opacity")
    /** Las que mueve la herramienta Mover (una sola clave «posición» en la interfaz). */
    val transformProps = listOf("x", "y", "sx", "sy", "rot")

    /** Capas como máximo (memoria de la GPU): gama baja 4, resto 8. */
    fun maxLayers(lowMemory: Boolean) = if (lowMemory) 4 else 8

    /** Lado largo máximo de la textura de una capa en la vista previa y al exportar. */
    fun maxSide(lowMemory: Boolean, export: Boolean) = when {
        export -> 2048
        lowMemory -> 1024
        else -> 1536
    }

    fun base(layer: ImageLayer, prop: String): Float = when (prop) {
        "x" -> layer.x
        "y" -> layer.y
        "sx" -> layer.sx
        "sy" -> layer.sy
        "rot" -> layer.rot
        "opacity" -> layer.opacity
        else -> 0f
    }

    fun withBase(layer: ImageLayer, prop: String, v: Float): ImageLayer = when (prop) {
        "x" -> layer.copy(x = v)
        "y" -> layer.copy(y = v)
        "sx" -> layer.copy(sx = v)
        "sy" -> layer.copy(sy = v)
        "rot" -> layer.copy(rot = v)
        "opacity" -> layer.copy(opacity = v)
        else -> layer
    }

    /** Valor de [prop] en el segundo [sec] (claves si las hay; si no, el valor fijo). */
    fun valueAt(layer: ImageLayer, prop: String, sec: Float): Float =
        layer.keys[prop]?.takeIf { it.isNotEmpty() }?.let { Keyframes.valueAt(it, sec) } ?: base(layer, prop)

    /** Visibilidad del tramo (fundido corto de 0,1 s en los bordes si no ocupa todo el vídeo). */
    fun spanGate(layer: ImageLayer, sec: Float, loopSec: Float): Float {
        if (layer.durSec <= 0f) return 1f
        val total = loopSec.coerceAtLeast(.1f)
        val start = layer.startSec.coerceIn(0f, total)
        val end = (start + layer.durSec).coerceAtMost(total)
        if (sec < start || sec > end) return 0f
        if (start <= .001f && end >= total - .001f) return 1f
        val fade = min(.10f, (end - start) * .18f).coerceAtLeast(.015f)
        fun s(v: Float): Float { val x = v.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
        return min(if (start <= .001f) 1f else s((sec - start) / fade), if (end >= total - .001f) 1f else s((end - sec) / fade))
    }

    /** Ruido suave periódico (parpadeo de luz): suma de senos con frecuencias enteras. */
    private fun flicker(t: Float, cycles: Int): Float {
        val p = 2.0 * PI * t
        val k = cycles.coerceAtLeast(1)
        val v = .5 * sin(p * 7 * k) + .3 * sin(p * 13 * k + 1.7) + .2 * sin(p * 23 * k + 4.1)
        return (.5 + .5 * v).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Estado de [layer] en la fase [t] (0..1) de un bucle de [loopSec] segundos, sobre una imagen
     * base de [baseW]×[baseH] píxeles. Mismo resultado en la vista previa y en la exportación.
     */
    fun frame(layer: ImageLayer, t: Float, loopSec: Float, baseW: Int, baseH: Int): LayerFrame {
        val sec = t.coerceIn(0f, 1f) * loopSec.coerceAtLeast(.1f)
        var x = valueAt(layer, "x", sec)
        var y = valueAt(layer, "y", sec)
        var sx = valueAt(layer, "sx", sec)
        var sy = valueAt(layer, "sy", sec)
        var rot = valueAt(layer, "rot", sec)
        var op = valueAt(layer, "opacity", sec)
        val amt = layer.motionAmt.coerceIn(0f, 1f)
        val cyc = layer.motionCycles.coerceIn(1, 8)
        val ph = (2.0 * PI * t * cyc).toFloat()
        val aspect = baseW.toFloat() / baseH.coerceAtLeast(1)
        when (layer.motion) {
            MOTION_FLOAT -> y += sin(ph) * .035f * amt
            MOTION_PULSE -> { val k = 1f + .12f * amt * (.5f - .5f * cos(ph)); sx *= k; sy *= k }
            MOTION_SWAY -> rot += sin(ph) * 14f * amt
            MOTION_SPIN -> rot += 360f * t * cyc * (if (amt < .5f) -1f else 1f)
            MOTION_FLICKER -> op *= 1f - .75f * amt * (1f - flicker(t, cyc))
            MOTION_BREATHE -> op *= 1f - .6f * amt * (.5f - .5f * cos(ph))
            MOTION_DRIFT -> { x += sin(ph) * .03f * amt / aspect.coerceAtLeast(.2f); y += (cos(ph) - 1f) * .03f * amt }
        }
        op *= spanGate(layer, sec, loopSec)
        val bw = baseW.coerceAtLeast(1).toFloat()
        val a = layer.aspect.coerceIn(.02f, 50f)
        return LayerFrame(
            x, y,
            abs(sx) * bw * .5f,
            abs(sy) * bw / a * .5f,
            (rot * PI / 180.0).toFloat(),
            op.coerceIn(0f, 1f),
            layer.flipX
        )
    }

    /** Esquinas de la capa en coordenadas 0..1 de la imagen base (para la caja con tiradores). */
    fun corners(f: LayerFrame, baseW: Int, baseH: Int): List<Pair<Float, Float>> {
        val c = cos(f.rot); val s = sin(f.rot)
        val bw = baseW.coerceAtLeast(1).toFloat(); val bh = baseH.coerceAtLeast(1).toFloat()
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (u, v) ->
            val lx = u * f.halfW; val ly = v * f.halfH
            (f.x + (c * lx - s * ly) / bw) to (f.y + (s * lx + c * ly) / bh)
        }
    }

    /**
     * ¿El punto (u, v) de la imagen base cae dentro de la capa? Devuelve las coordenadas locales
     * (−1..1) o null. Sirve para elegir la capa que se toca en el lienzo.
     */
    fun localOf(f: LayerFrame, u: Float, v: Float, baseW: Int, baseH: Int): Pair<Float, Float>? {
        val px = (u - f.x) * baseW; val py = (v - f.y) * baseH
        val c = cos(f.rot); val s = sin(f.rot)
        val lx = (c * px + s * py) / max(f.halfW, 1e-3f)
        val ly = (-s * px + c * py) / max(f.halfH, 1e-3f)
        return if (abs(lx) <= 1f && abs(ly) <= 1f) lx to ly else null
    }

    /** Tamaño inicial: que la capa quepa holgada en la imagen (60 % del lado que limite). */
    fun initialScale(layerAspect: Float, baseW: Int, baseH: Int): Float {
        val baseAspect = baseW.toFloat() / baseH.coerceAtLeast(1)
        val a = layerAspect.coerceIn(.02f, 50f)
        // ancho = s·bw; alto = s·bw/a ≤ .6·bh → s ≤ .6·a/baseAspect
        return min(.6f, .6f * a / baseAspect).coerceIn(.05f, 1.5f)
    }

    /** Escala para CUBRIR toda la imagen base sin deformar (ancho ≥ base y alto ≥ base). */
    fun coverScale(layerAspect: Float, baseW: Int, baseH: Int): Float {
        val baseAspect = baseW.toFloat() / baseH.coerceAtLeast(1)
        return max(1f, layerAspect.coerceIn(.02f, 50f) / baseAspect)
    }

    // ------------------------------------------------------------------ fotogramas clave

    /** Pone (o reemplaza) la clave de cada [props] en [sec] con los valores de [values]. */
    fun putKeys(layer: ImageLayer, sec: Float, values: Map<String, Float>, curve: Int? = null): ImageLayer {
        val keys = layer.keys.toMutableMap()
        for ((p, v) in values) keys[p] = Keyframes.put(keys[p].orEmpty(), sec, v, curve)
        return layer.copy(keys = keys)
    }

    fun removeKeys(layer: ImageLayer, sec: Float, props: Collection<String>): ImageLayer {
        val keys = layer.keys.toMutableMap()
        for (p in props) {
            val left = Keyframes.remove(keys[p].orEmpty(), sec)
            if (left.isEmpty()) keys.remove(p) else keys[p] = left
        }
        return layer.copy(keys = keys)
    }

    fun hasKeyAt(layer: ImageLayer, sec: Float, props: Collection<String>): Boolean =
        props.any { Keyframes.at(layer.keys[it], sec) != null }

    fun animated(layer: ImageLayer, props: Collection<String>): Boolean = props.any { !layer.keys[it].isNullOrEmpty() }

    /**
     * Aplica un cambio de la herramienta Mover o de un deslizador: si la propiedad está animada
     * se guarda como clave en el cabezal (como en Alight Motion); si no, cambia el valor fijo.
     */
    fun edit(layer: ImageLayer, sec: Float, values: Map<String, Float>): ImageLayer {
        var out = layer
        val keyed = HashMap<String, Float>()
        for ((p, v) in values) {
            if (!layer.keys[p].isNullOrEmpty()) keyed[p] = v else out = withBase(out, p, v)
        }
        if (keyed.isNotEmpty()) out = putKeys(out, sec, keyed)
        return out
    }

    fun setCurve(layer: ImageLayer, sec: Float, props: Collection<String>, curve: Int): ImageLayer {
        val keys = layer.keys.toMutableMap()
        for (p in props) {
            val list = keys[p] ?: continue
            keys[p] = list.map { if (abs(it.t - sec) <= Keyframes.SNAP_SEC) it.copy(curve = curve) else it }
        }
        return layer.copy(keys = keys)
    }

    /** Todos los tiempos con clave (para la tira de claves). */
    fun keyTimes(layer: ImageLayer): List<Float> = layer.keys.values.flatten().map { it.t }.distinct().sorted()

    /** Mezcla y opacidad a prueba de proyectos dañados. */
    fun sanitize(layer: ImageLayer): ImageLayer = layer.copy(
        x = layer.x.finiteOr(.5f).coerceIn(-2f, 3f),
        y = layer.y.finiteOr(.5f).coerceIn(-2f, 3f),
        sx = layer.sx.finiteOr(.6f).coerceIn(.005f, 20f),
        sy = layer.sy.finiteOr(.6f).coerceIn(.005f, 20f),
        rot = layer.rot.finiteOr(0f).coerceIn(-3600f, 3600f),
        opacity = layer.opacity.finiteOr(1f).coerceIn(0f, 1f),
        blend = layer.blend.coerceIn(0, BlendModes.COUNT - 1),
        aspect = layer.aspect.finiteOr(1f).coerceIn(.02f, 50f),
        motion = layer.motion.coerceIn(0, motionNames.size - 1),
        motionAmt = layer.motionAmt.finiteOr(.5f).coerceIn(0f, 1f),
        motionCycles = layer.motionCycles.coerceIn(1, 8),
        startSec = layer.startSec.finiteOr(0f).coerceAtLeast(0f),
        durSec = layer.durSec.finiteOr(0f).coerceAtLeast(0f),
        keys = layer.keys.filterKeys { it in keyProps }.filterValues { it.isNotEmpty() },
        depth = if (!layer.depth.isFinite() || layer.depth < 0f) LayerParallax.AUTO else layer.depth.coerceIn(0f, 1f),
        text = layer.text?.let { TextLayers.sanitize(it) }
    ).let { l ->
        // v44.27: el marco del recorte siempre válido; sin original guardado, sin recorte.
        if (l.src == null || l.src == l.file) l.copy(src = null, cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f, cropRot = 0)
        else LayerCrop.clamp(CropRect(l.cropL, l.cropT, l.cropR, l.cropB)).let { c ->
            l.copy(cropL = c.l, cropT = c.t, cropR = c.r, cropB = c.b, cropRot = LayerCrop.normRot(l.cropRot))
        }
    }

    private fun Float.finiteOr(d: Float) = if (isFinite()) this else d

    /** Nombre corto para la línea de tiempo a partir del nombre del archivo elegido. */
    fun displayName(raw: String?, index: Int): String {
        val base = raw?.substringAfterLast('/')?.substringBeforeLast('.')?.trim().orEmpty()
        return if (base.isBlank()) "Imagen $index" else base.take(40)
    }
}
