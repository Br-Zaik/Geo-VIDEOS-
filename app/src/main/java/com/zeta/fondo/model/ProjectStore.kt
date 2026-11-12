package com.zeta.fondo.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import com.zeta.fondo.engine.BitmapUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ProjectSummary(
    val id: String,
    val name: String,
    val thumb: File,
    val modified: Long,
    val w: Int,
    val h: Int
)

data class ProjectMeta(
    val id: String,
    val name: String,
    val dir: File,
    val imageFile: File,
    val w: Int,
    val h: Int
)

class LoadedProject(
    val meta: ProjectMeta,
    val arrows: List<Arrow>,
    val anchors: List<PointN>,
    val strokes: List<MaskStroke>,
    val settings: Map<String, Float>,
    val export: ExportSettings,
    val maskNames: List<String>,
    val effectClips: List<EffectClip>,
    /** v44.23: capas de imagen (de abajo arriba). */
    val layers: List<ImageLayer> = emptyList()
)

/**
 * Cada proyecto es una carpeta: source.img (copia EXACTA de tu imagen, sin
 * recomprimir), thumb.jpg y project.json con flechas, máscaras y ajustes.
 */
class ProjectStore(private val ctx: Context) {

    private val root = File(ctx.filesDir, "projects")

    fun list(): List<ProjectSummary> {
        root.mkdirs()
        val dirs = root.listFiles() ?: return emptyList()
        return dirs.filter { it.isDirectory && (File(it, JSON).exists() || File(it, "$JSON.bak").exists()) }.mapNotNull { dir ->
            try {
                val jf = File(dir, JSON)
                val j = JSONObject(AtomicFile(jf).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
                ProjectSummary(
                    dir.name, j.optString("name", dir.name), File(dir, THUMB),
                    jf.lastModified(), j.getInt("w"), j.getInt("h")
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.modified }
    }

    /** v44.25 · Lado largo máximo de una foto girada al importarla (se re-codifica). */
    val ROTATED_MAX = 4096

    /** Resultado de importar: id del proyecto y la vista previa ya decodificada (se reutiliza al abrir). */
    class Created(val id: String, val preview: Bitmap)

    fun create(uri: Uri, loopSec: Int = 6): String = createFast(uri, loopSec, PreviewSizing.target(false, 192)).also { writeCaches(it) }.id

    /**
     * v39 · Importación con UNA sola decodificación del original:
     *  1. lee el tamaño y la orientación;
     *  2. copia el archivo tal cual (o lo gira si la foto lo pide);
     *  3. decodifica la vista previa al tamaño final del dispositivo [previewMax];
     *  4. guarda el proyecto mínimo (JSON) y devuelve la vista previa para abrir YA.
     * La miniatura y la caché de edición se escriben después, en segundo plano
     * ([writeCaches]), reutilizando ese mismo bitmap: nada se decodifica dos veces.
     * Antes: miniatura 480 px + caché 1920 px (2 decodificaciones + 2 compresiones) y,
     * al abrir, otra decodificación del original si el teléfono pedía 2048/2560 px.
     */
    fun createFast(uri: Uri, loopSec: Int, previewMax: Int, rotatedMax: Int = ROTATED_MAX): Created {
        root.mkdirs()
        val id = System.currentTimeMillis().toString()
        val dir = File(root, id)
        dir.mkdirs()
        try {
            val cr = ctx.contentResolver
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val opened = cr.openInputStream(uri) ?: throw IOException("No se pudo abrir la imagen")
            opened.use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth <= 0 || o.outHeight <= 0) throw IOException("Ese archivo no es una imagen compatible")

            val img = File(dir, IMAGE)
            var w = o.outWidth
            var h = o.outHeight
            // v44.25: la etiqueta EXIF completa (giros Y espejos de la cámara frontal).
            val orientation = readOrientation(uri)
            var preview: Bitmap? = null
            if (orientation == 1) {
                // Copia byte a byte: conserva la calidad y resolución originales
                val input = cr.openInputStream(uri) ?: throw IOException("No se pudo leer la imagen")
                input.use { i -> img.outputStream().buffered(256 * 1024).use { o -> i.copyTo(o, 256 * 1024) } }
                val p = BitmapUtil.decodePreview(img, previewMax)
                // v44.25: si el decodificador la giró por un EXIF que ExifInterface no leyó (p. ej.
                // PNG o WebP), se guarda ya derecha (abajo): así la vista previa, la exportación y
                // las máscaras usan siempre la misma imagen y las mismas medidas.
                val turned = p.width != p.height && w != h && (p.width >= p.height) != (w >= h)
                if (turned) p.recycle() else preview = p
            }
            if (preview == null) {
                // v44.25: foto girada o en espejo → una sola decodificación, ya derecha y con su lado
                // largo ≤ [rotatedMax] (4096 = de sobra para exportar en 4K). Antes se decodificaba
                // entera (hasta 16K) y se hacía otra copia girada: ~400 MB con 50 MP → no abría.
                val rot = BitmapUtil.decodeUriAtMost(cr, uri, rotatedMax, orientation)
                // v41: una foto JPEG girada se guarda como JPEG de máxima calidad (PNG de 12 MP
                // tardaba varios segundos en comprimirse y hacía esperar al abrir).
                val jpeg = (cr.getType(uri) ?: "").contains("jpeg") || (cr.getType(uri) ?: "").contains("jpg")
                img.outputStream().buffered(256 * 1024).use {
                    rot.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (jpeg) 98 else 100, it)
                }
                w = rot.width
                h = rot.height
                // La vista previa sale del bitmap ya girado (sin volver a leer el PNG).
                val k = minOf(1f, previewMax.toFloat() / maxOf(w, h))
                val p = if (k < 1f) Bitmap.createScaledBitmap(rot, maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()), true) else rot
                if (p !== rot) rot.recycle()
                preview = p
            }

            val name = "Fondo " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
            val j = JSONObject().put("name", name).put("w", w).put("h", h)
                .put("export", ExportSettings(loopSec = loopSec.coerceIn(2, 60)).toJson())
            // También el primer guardado de un proyecto debe resistir cierres abruptos.
            val initial = AtomicFile(File(dir, JSON))
            val output = initial.startWrite()
            try {
                output.write(j.toString().toByteArray(Charsets.UTF_8))
                initial.finishWrite(output)
            } catch (error: Throwable) {
                initial.failWrite(output)
                throw error
            }
            return Created(id, preview!!)
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Miniatura (lista de proyectos) y caché de edición a partir de la vista previa ya decodificada. */
    fun writeCaches(created: Created) {
        val dir = File(root, created.id)
        val p = created.preview
        if (p.isRecycled || !dir.isDirectory) return
        try {
            val cache = File(dir, EDIT_PREVIEW)
            if (!cache.isFile) {
                val tmp = File(dir, "${EDIT_PREVIEW}.tmp")
                tmp.outputStream().buffered(256 * 1024).use { p.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                if (!tmp.renameTo(cache)) tmp.delete()
            }
            val thumbFile = File(dir, THUMB)
            if (!thumbFile.isFile) {
                val k = minOf(1f, 480f / maxOf(p.width, p.height))
                val th = if (k < 1f) Bitmap.createScaledBitmap(p, maxOf(1, (p.width * k).toInt()), maxOf(1, (p.height * k).toInt()), true) else p
                thumbFile.outputStream().use { th.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                if (th !== p) th.recycle()
            }
        } catch (_: Throwable) {
            // Las cachés son opcionales: se recrean al abrir si faltan.
        }
    }

    /**
     * v44.25 · Etiqueta EXIF de orientación tal cual (1 normal; 3/6/8 giros; 2/4/5/7 espejos).
     * 1 si no tiene o no se puede leer. Antes solo se leían los giros: una foto en espejo de la
     * cámara frontal se veía distinta en la vista previa y en el vídeo.
     */
    fun readOrientation(uri: Uri): Int = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }?.takeIf { it in 2..8 } ?: 1
    } catch (e: Exception) {
        1
    }


    fun loadOrCreateEditPreview(meta: ProjectMeta, maxSide: Int): Bitmap {
        val cached = File(meta.dir, EDIT_PREVIEW)
        if (cached.isFile) {
            try {
                // Si el teléfono tiene capacidad y se acerca mucho, conserva una
                // preview más nítida. Los móviles de poca RAM siguen usando 1600 px.
                val (cw, ch) = BitmapUtil.bounds(cached)
                val (iw, ih) = BitmapUtil.bounds(meta.imageFile)
                // v39: una caché algo menor que el objetivo (p. ej. 1920 para 2048/2560) se
                // reutiliza: re-decodificar el original al abrir costaba segundos.
                if (PreviewSizing.cacheIsEnough(maxOf(cw, ch), maxSide, maxOf(iw, ih))) {
                    return BitmapUtil.decodePreview(cached, maxSide)
                }
            } catch (_: Throwable) {
                cached.delete()
            }
        }
        val bmp = BitmapUtil.decodePreview(meta.imageFile, maxSide)
        // Si es un proyecto antiguo, se crea una vez y los siguientes accesos son inmediatos.
        try {
            val tmp = File(meta.dir, "${EDIT_PREVIEW}.tmp")
            tmp.outputStream().buffered(256 * 1024).use { stream ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 97, stream)
            }
            if (!tmp.renameTo(cached)) tmp.delete()
        } catch (_: Throwable) {
            // La caché es opcional; nunca impedimos abrir el proyecto.
        }
        return bmp
    }

    fun load(id: String): LoadedProject {
        val dir = File(root, id)
        val j = JSONObject(AtomicFile(File(dir, JSON)).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        val w = j.getInt("w")
        val h = j.getInt("h")
        val meta = ProjectMeta(id, j.optString("name", "Fondo"), dir, File(dir, IMAGE), w, h)

        val arrows = ArrayList<Arrow>()
        j.optJSONArray("arrows")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONArray(i)
                arrows.add(Arrow(e.f(0), e.f(1), e.f(2), e.f(3)))
            }
        }
        val anchors = ArrayList<PointN>()
        j.optJSONArray("anchors")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.getJSONArray(i)
                anchors.add(PointN(e.f(0), e.f(1)))
            }
        }
        val strokes = ArrayList<MaskStroke>()
        j.optJSONArray("strokes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val s = MaskStroke(
                    o.getInt("l"),
                    o.getDouble("r").toFloat(),
                    o.optDouble("f", 0.3).toFloat(),
                    o.optBoolean("e", false),
                    o.optBoolean("fill", false),
                    o.optString("img", "").takeIf { it.isNotBlank() && !it.contains('/') && !it.contains("..") }
                )
                o.optJSONArray("p")?.let { p ->
                    var k = 0
                    while (k + 1 < p.length()) {
                        s.points.add(PointN(p.f(k), p.f(k + 1)))
                        k += 2
                    }
                }
                strokes.add(s)
            }
        }
        val settings = HashMap(Effects.defaults())
        j.optJSONObject("settings")?.let { o ->
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                settings[k] = o.optDouble(k, 0.0).toFloat()
            }
        }
        val export = ExportSettings.fromJson(j.optJSONObject("export"))
        val maskNames = ArrayList<String>()
        j.optJSONArray("maskNames")?.let { arr ->
            for (i in 0 until arr.length()) maskNames.add(arr.optString(i, "Máscara $i"))
        }
        val effectClips = ArrayList<EffectClip>()
        j.optJSONArray("effectClips")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("effectId", "")
                if (id.isBlank()) continue
                effectClips.add(EffectClip(
                    uid = o.optLong("uid", (i + 1).toLong()),
                    effectId = id,
                    startSec = o.optDouble("startSec", 0.0).toFloat(),
                    durationSec = o.optDouble("durationSec", export.loopSec.toDouble()).toFloat(),
                    track = o.optInt("track", -1),
                    strength = o.optDouble("strength", 1.0).toFloat().coerceIn(0f, 2f),
                    // v44: proyectos anteriores no tienen «hidden» → visible.
                    hidden = o.optBoolean("hidden", false),
                    // v44: fotogramas clave {"clave": [t, valor, curva, t, valor, curva…]}.
                    keys = o.optJSONObject("keys")?.let { ko ->
                        val m = LinkedHashMap<String, List<Keyframe>>()
                        val it = ko.keys()
                        while (it.hasNext()) {
                            val k = it.next()
                            if (!ClipEdits.isClipKey(id, k)) continue
                            val arr = ko.optJSONArray(k) ?: continue
                            val flat = FloatArray(arr.length()) { j -> arr.optDouble(j, 0.0).toFloat() }
                            val list = Keyframes.decode(flat)
                            if (list.isNotEmpty()) m[k] = list
                        }
                        m
                    } ?: emptyMap(),
                    overrides = o.optJSONObject("overrides")?.let { ov ->
                        val m = LinkedHashMap<String, Float>()
                        val keys = ov.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (ClipEdits.isClipKey(id, k)) m[k] = ov.optDouble(k, 0.0).toFloat()
                        }
                        m
                    } ?: emptyMap()
                ))
            }
        }
        // v44.23: capas cuyo archivo existe (si falta la imagen, la capa no se carga).
        val layers = LayerJson.fromJson(j.optJSONArray("layers")).filter { File(dir, it.file).isFile }
        return LoadedProject(meta, arrows, anchors, strokes, settings, export, maskNames, effectClips, layers)
    }

    @Synchronized
    fun save(
        meta: ProjectMeta,
        arrows: List<Arrow>,
        anchors: List<PointN>,
        strokes: List<MaskStroke>,
        settings: Map<String, Float>,
        export: ExportSettings,
        maskNames: List<String>,
        effectClips: List<EffectClip> = emptyList(),
        layers: List<ImageLayer> = emptyList()
    ) {
        val j = JSONObject()
            .put("name", meta.name)
            .put("w", meta.w)
            .put("h", meta.h)
        val a = JSONArray()
        for (ar in arrows) a.put(JSONArray().put(r4(ar.x0)).put(r4(ar.y0)).put(r4(ar.x1)).put(r4(ar.y1)))
        j.put("arrows", a)
        val an = JSONArray()
        for (p in anchors) an.put(JSONArray().put(r4(p.x)).put(r4(p.y)))
        j.put("anchors", an)
        val st = JSONArray()
        for (s in strokes) {
            val pts = JSONArray()
            for (p in s.points) {
                pts.put(r4(p.x))
                pts.put(r4(p.y))
            }
            st.put(
                JSONObject()
                    .put("l", s.layer)
                    .put("r", s.radius.jsonSafe(.05f))
                    .put("f", s.feather.jsonSafe(.5f))
                    .put("e", s.erase)
                    .put("fill", s.fill)
                    .put("p", pts)
                    .also { o -> s.image?.let { o.put("img", it) } }
            )
        }
        j.put("strokes", st)
        j.put("maskNames", JSONArray(maskNames))
        val so = JSONObject()
        for ((k, v) in settings) if (v.isFinite()) so.put(k, v.toDouble())   // v44.25: sin NaN
        j.put("settings", so)
        j.put("export", export.toJson())
        val clips = JSONArray()
        for (clip in effectClips) {
            val o = JSONObject()
                .put("uid", clip.uid)
                .put("effectId", clip.effectId)
                .put("startSec", clip.startSec.jsonSafe())
                .put("durationSec", clip.durationSec.jsonSafe(1f))
                .put("track", clip.track)
                .put("strength", clip.strength.jsonSafe(1f))
            if (clip.hidden) o.put("hidden", true)
            if (clip.keys.isNotEmpty()) {
                val ko = JSONObject()
                for ((k, ks) in clip.keys) {
                    if (ks.isEmpty()) continue
                    val arr = JSONArray()
                    for (f in Keyframes.encode(ks)) arr.put(f.jsonSafe())
                    ko.put(k, arr)
                }
                o.put("keys", ko)
            }
            // v40: ajustes propios del bloque (los proyectos antiguos no los tienen).
            if (clip.overrides.isNotEmpty()) {
                val ov = JSONObject()
                for ((k, v) in clip.overrides) if (v.isFinite()) ov.put(k, v.toDouble())
                o.put("overrides", ov)
            }
            clips.put(o)
        }
        j.put("effectClips", clips)
        if (layers.isNotEmpty()) j.put("layers", LayerJson.toJson(layers))

        // Android AtomicFile conserva una copia previa si se corta la alimentación
        // o el proceso mientras se escriben los ajustes del proyecto.
        val file = AtomicFile(File(meta.dir, JSON))
        val stream = file.startWrite()
        try {
            stream.write(j.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    /**
     * v44.23 · Al cerrar el proyecto: borra las imágenes de capas que ya no usa ninguna capa.
     * (No se hace al guardar: «Deshacer» puede devolver una capa eliminada.)
     */
    fun cleanLayerFiles(meta: ProjectMeta, layers: List<ImageLayer>) {
        val files = File(meta.dir, LayerJson.DIR).listFiles() ?: return
        // v44.27: también el original de las capas recortadas («Original» lo vuelve a usar).
        val used = layers.flatMap { listOfNotNull(it.file, it.src) }.map { it.substringAfter('/') }.toSet()
        for (f in files) if (f.name !in used) f.delete()
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    private fun r4(v: Float): Double = if (v.isFinite()) (v * 10000f).roundToInt() / 10000.0 else 0.0   // v44.25: NaN no rompe el guardado

    private fun JSONArray.f(i: Int): Float = getDouble(i).toFloat()

    companion object {
        const val JSON = "project.json"
        const val THUMB = "thumb.jpg"
        const val EDIT_PREVIEW = "edit_preview.jpg"

        const val IMAGE = "source.img"
    }
}
