package com.zeta.fondo.model

import org.json.JSONArray
import org.json.JSONObject

/** v44.23 · Guardado de las capas de imagen en project.json (lectura tolerante). */
object LayerJson {
    /** Carpeta de las imágenes de las capas dentro del proyecto. */
    const val DIR = "layers"

    fun toJson(layers: List<ImageLayer>): JSONArray {
        val arr = JSONArray()
        for (l in layers) {
            val o = JSONObject()
                .put("id", l.id)
                .put("name", l.name)
                .put("file", l.file)
                .put("aspect", l.aspect.jsonSafe(1f))
                .put("x", l.x.jsonSafe(.5f))
                .put("y", l.y.jsonSafe(.5f))
                .put("sx", l.sx.jsonSafe(.5f))
                .put("sy", l.sy.jsonSafe(.5f))
                .put("rot", l.rot.jsonSafe())
                .put("op", l.opacity.jsonSafe(1f))
                .put("blend", l.blend)
            if (l.hidden) o.put("hidden", true)
            if (l.behind) o.put("behind", true)
            if (l.flipX) o.put("flipX", true)
            if (l.motion != 0) o.put("motion", l.motion).put("mamt", l.motionAmt.jsonSafe(.5f)).put("mcyc", l.motionCycles)
            if (l.durSec > 0f) o.put("start", l.startSec.jsonSafe()).put("dur", l.durSec.jsonSafe())
            if (l.depth >= 0f && l.depth.isFinite()) o.put("depth", l.depth.toDouble())
            // v44.27: recorte (el original se guarda aparte).
            l.src?.let { src ->
                o.put("src", src)
                o.put("crop", JSONArray().put(l.cropL.jsonSafe()).put(l.cropT.jsonSafe())
                    .put(l.cropR.jsonSafe(1f)).put(l.cropB.jsonSafe(1f)))
                if (l.cropRot != 0) o.put("crot", l.cropRot)
            }
            // v44.27: capa de texto (la imagen ya dibujada es «file»; esto es para volver a editarlo).
            l.text?.let { t ->
                o.put("text", JSONObject().put("s", t.text).put("f", t.font).put("c", t.color)
                    .put("o", t.outline).put("oc", t.outlineColor).put("sh", t.shadow))
            }
            if (l.keys.isNotEmpty()) {
                val ko = JSONObject()
                for ((k, ks) in l.keys) {
                    if (ks.isEmpty()) continue
                    val a = JSONArray()
                    for (f in Keyframes.encode(ks)) a.put(f.jsonSafe())
                    ko.put(k, a)
                }
                o.put("keys", ko)
            }
            arr.put(o)
        }
        return arr
    }

    /** Solo nombres de archivo seguros dentro de la carpeta de capas. */
    fun safeFile(f: String): Boolean = f.startsWith("$DIR/") && !f.contains("..") && f.count { it == '/' } == 1 && f.length < 120

    fun fromJson(arr: JSONArray?): List<ImageLayer> {
        if (arr == null) return emptyList()
        val out = ArrayList<ImageLayer>()
        val seen = HashSet<Long>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val file = o.optString("file", "")
            if (!safeFile(file)) continue
            var id = o.optLong("id", i + 1L)
            while (!seen.add(id)) id++
            val keys = LinkedHashMap<String, List<Keyframe>>()
            o.optJSONObject("keys")?.let { ko ->
                val it = ko.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (k !in ImageLayers.keyProps) continue
                    val a = ko.optJSONArray(k) ?: continue
                    val list = Keyframes.decode(FloatArray(a.length()) { j -> a.optDouble(j, 0.0).toFloat() })
                    if (list.isNotEmpty()) keys[k] = list
                }
            }
            val src = o.optString("src", "").takeIf { it.isNotEmpty() && safeFile(it) }
            val crop = o.optJSONArray("crop")
            val text = o.optJSONObject("text")?.let { t ->
                TextSpec(t.optString("s", ""), t.optInt("f", 0), t.optInt("c", TextLayers.WHITE), t.optInt("o", 2),
                    t.optInt("oc", TextLayers.BLACK), t.optBoolean("sh", true))
            }
            fun c(i: Int, d: Double) = (crop?.optDouble(i, d) ?: d).toFloat()
            out += ImageLayers.sanitize(ImageLayer(
                id = id,
                name = o.optString("name", "Imagen ${i + 1}"),
                file = file,
                aspect = o.optDouble("aspect", 1.0).toFloat(),
                x = o.optDouble("x", .5).toFloat(),
                y = o.optDouble("y", .5).toFloat(),
                sx = o.optDouble("sx", .6).toFloat(),
                sy = o.optDouble("sy", o.optDouble("sx", .6)).toFloat(),
                rot = o.optDouble("rot", 0.0).toFloat(),
                opacity = o.optDouble("op", 1.0).toFloat(),
                blend = o.optInt("blend", 0),
                hidden = o.optBoolean("hidden", false),
                behind = o.optBoolean("behind", false),
                flipX = o.optBoolean("flipX", false),
                motion = o.optInt("motion", 0),
                motionAmt = o.optDouble("mamt", .5).toFloat(),
                motionCycles = o.optInt("mcyc", 1),
                startSec = o.optDouble("start", 0.0).toFloat(),
                durSec = o.optDouble("dur", 0.0).toFloat(),
                keys = keys,
                depth = o.optDouble("depth", LayerParallax.AUTO.toDouble()).toFloat(),
                src = src,
                cropL = c(0, 0.0), cropT = c(1, 0.0), cropR = c(2, 1.0), cropB = c(3, 1.0),
                cropRot = o.optInt("crot", 0),
                text = text
            ))
        }
        return out
    }
}
