package com.zeta.fondo.engine

import com.zeta.fondo.model.ClipEdits
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Keyframe
import com.zeta.fondo.model.Keyframes
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Wind
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Valores listos para los uniforms del shader. Inmutable: se reemplaza entero. */
class RenderParams(
    val studio: Map<String, FloatArray>,
    val aspect: Float,
    val flow: FloatArray,    // 3
    val drift: FloatArray,   // 2
    val ripple: FloatArray,  // 3
    val glint: FloatArray,   // 3
    val sway: FloatArray,    // 3
    val heat: FloatArray,    // 3
    val rain: FloatArray,    // 4
    val snow: FloatArray,    // 4
    val sakura: FloatArray,  // 4
    val stars: FloatArray,   // 4
    val flies: FloatArray,   // 4
    val fog: FloatArray,     // 4
    val rays: FloatArray,    // 4
    val energy: FloatArray,  // 4
    val speed: FloatArray,   // 4
    val cam: FloatArray,     // 3
    val mono: Float,
    val snap: Float,
    val motion: Map<String, FloatArray>,
    val extra: Map<String, FloatArray>,
    val effectMasks: Map<String, Int>,
    val copyMotion: Map<String, FloatArray>,
    val copyExtra: Map<String, FloatArray>,
    val copyMasks: Map<String, Int>,
    val timeline: Map<String, List<EffectClip>>,
    val loopSec: Float,
    /** Eventos: id -> [modo (EventMode), cada X s]. */
    val events: Map<String, FloatArray> = emptyMap(),
    /** v41: efectos encendidos = las partes del shader que hay que compilar (#ifdef FX_ID). */
    val features: Set<String> = emptySet(),
    /** v44.19 · post-proceso «Calidad cine» (fuera del bloque std140; ver CinePost). */
    val post: FloatArray = CinePlan.OFF
) {
    /** v41: clave de la variante de shader que necesita este juego de parámetros. */
    val variantKey: String by lazy { ShaderVariants.key(features) }

    /**
     * v40 · Todos los uniforms float del bloque ZetaParams con su valor. Es la ÚNICA
     * fuente para escribirlos (vista previa y exportación) y para comparar bloques.
     */
    val floatUniforms: Map<String, FloatArray> by lazy {
        val m = LinkedHashMap<String, FloatArray>()
        studio.forEach { (k, v) -> m[k] = v }
        m["uAspect"] = floatArrayOf(aspect)
        m["uMono"] = floatArrayOf(mono)
        m["uPixelSnap"] = floatArrayOf(snap)
        m["uFlow3"] = flow
        m["uDrift"] = drift
        m["uRipple"] = ripple
        m["uGlint"] = glint
        m["uSway"] = sway
        m["uHeat"] = heat
        m["uRain"] = rain
        m["uSnow"] = snow
        m["uSakura"] = sakura
        m["uStars"] = stars
        m["uFlies"] = flies
        m["uFog"] = fog
        m["uRays"] = rays
        m["uEnergy"] = energy
        m["uSpeed"] = speed
        m["uCam"] = cam
        motion.forEach { (id, v) -> m["uMotion${uniformName(id)}"] = v }
        extra.forEach { (id, v) -> m["uFx${uniformName(id)}"] = v }
        copyMotion.forEach { (id, v) -> m["uMotion${uniformName(id)}Copy"] = v }
        copyExtra.forEach { (id, v) -> m["uFx${uniformName(id)}Copy"] = v }
        m
    }

    /** Uniforms enteros (ranura de máscara de cada efecto). */
    val intUniforms: Map<String, Int> by lazy {
        val m = LinkedHashMap<String, Int>()
        copyMasks.forEach { (id, v) -> m["uMask${uniformName(id)}Copy"] = v }
        effectMasks.forEach { (id, v) -> m["uMask${uniformName(id)}"] = v }
        m
    }

    /**
     * v40 · Diferencias de cada bloque con ajustes propios (uid -> cambios). El renderer
     * las aplica cuadro a cuadro al bloque dominante de cada efecto.
     */
    var clipDiffs: Map<Long, ClipDiff> = emptyMap()
        private set

    /**
     * v44 · Fotogramas clave ya traducidos a uniforms: por bloque, una pista por parámetro
     * animado con el vector de cada uniform en cada clave. Cada cuadro solo interpola.
     */
    var keyTracks: Map<Long, List<KeyTrack>> = emptyMap()
    /** v44.23 · zonas con forma (uniforms sueltos uZoneA/uZoneB; ver ZoneShapes.uniforms). */
    var zones: FloatArray = ZONES_OFF
        private set

    companion object {
        /** v44.23 · sin zonas con forma. */
        val ZONES_OFF = FloatArray(com.zeta.fondo.model.ZoneShapes.SLOTS * 8)
        fun uniformName(id: String) = id.replaceFirstChar { it.uppercaseChar() }

        /** Compara dos juegos de uniforms y devuelve solo los componentes distintos. */
        fun diff(base: RenderParams, other: RenderParams): ClipDiff {
            val floats = LinkedHashMap<String, ClipDiff.Patch>()
            for ((name, b) in base.floatUniforms) {
                val o = other.floatUniforms[name] ?: continue
                val n = minOf(b.size, o.size)
                var changed: BooleanArray? = null
                for (i in 0 until n) if (b[i] != o[i]) {
                    if (changed == null) changed = BooleanArray(n)
                    changed[i] = true
                }
                if (changed != null) floats[name] = ClipDiff.Patch(o.copyOf(n), changed)
            }
            val ints = LinkedHashMap<String, Int>()
            for ((name, b) in base.intUniforms) {
                val o = other.intUniforms[name] ?: continue
                if (o != b) ints[name] = o
            }
            return ClipDiff(floats, ints)
        }

        // Caché: mientras se arrastra un bloque los ajustes no cambian; no se recalculan
        // las diferencias de cada bloque en cada cuadro del arrastre.
        private class DiffKey(val settings: Map<String, Float>, val overrides: Map<String, Float>,
                              val aspect: Float, val loopSec: Float) {
            override fun equals(other: Any?): Boolean = other is DiffKey && other.settings === settings &&
                other.overrides == overrides && other.aspect == aspect && other.loopSec == loopSec
            override fun hashCode(): Int = (System.identityHashCode(settings) * 31 + overrides.hashCode()) * 31 +
                aspect.hashCode() * 7 + loopSec.hashCode()
        }
        private val diffCache = object : LinkedHashMap<DiffKey, ClipDiff>(32, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DiffKey, ClipDiff>?): Boolean = size > 48
        }

        /** Desplazamiento que el shader interpreta como "detrás del personaje (Proteger)". */
        const val BEHIND_OFFSET = 128

        /** Ranura de máscara + 128 si el efecto debe quedar detrás del personaje. */
        fun maskSlot(s: Map<String, Float>, prefix: String): Int {
            val base = s["$prefix.mask"]?.toInt() ?: -1
            return if ((s["$prefix.behind"] ?: 0f) > .5f) base + BEHIND_OFFSET else base
        }

        /** Color propio del efecto (rgb 0..1, w = 1 si está activado). */
        fun tintOf(s: Map<String, Float>, id: String): FloatArray =
            if ((s["$id.tintOn"] ?: 0f) > .5f) floatArrayOf(
                (s["$id.tintR"] ?: 1f).coerceIn(0f, 1f), (s["$id.tintG"] ?: 1f).coerceIn(0f, 1f),
                (s["$id.tintB"] ?: 1f).coerceIn(0f, 1f), 1f)
            else floatArrayOf(0f, 0f, 0f, 0f)

        /**
         * [clips] puede incluir bloques ocultos: el efecto se apaga en esos tramos.
         * v44 · [muteUnclipped] (modo «Solo»): los efectos activos que no tienen ningún bloque
         * tampoco se dibujan, así solo se ve el bloque aislado.
         */
        fun from(s: Map<String, Float>, aspect: Float, loopSec: Float = 6f, clips: List<EffectClip> = emptyList(),
                 muteUnclipped: Boolean = false): RenderParams {
            val base = build(s, aspect, loopSec, clips, muteUnclipped)
            base.keyTracks = keyTracksFor(s, aspect, loopSec, clips)
            base.zones = com.zeta.fondo.model.ZoneShapes.uniforms(s)
            val own = clips.filter { it.overrides.isNotEmpty() && !it.hidden }
            if (own.isEmpty()) return base
            val diffs = LinkedHashMap<Long, ClipDiff>()
            for (clip in own) {
                val ov = clip.overrides.filterKeys { ClipEdits.isClipKey(clip.effectId, it) }
                if (ov.isEmpty()) continue
                val key = DiffKey(s, ov, aspect, loopSec)
                val cached = synchronized(diffCache) { diffCache[key] }
                val d = cached ?: diff(base, build(s + ov, aspect, loopSec, emptyList(), false)).also { made ->
                    synchronized(diffCache) { diffCache[key] = made }
                }
                if (!d.isEmpty) diffs[clip.uid] = d
            }
            base.clipDiffs = diffs
            return base
        }

        private val keyCache = object : LinkedHashMap<List<Any>, List<KeyTrack>>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any>, List<KeyTrack>>?): Boolean = size > 24
        }

        /** Traduce las claves de cada bloque a vectores de uniforms (con caché: arrastrar no recalcula). */
        private fun keyTracksFor(s: Map<String, Float>, aspect: Float, loopSec: Float, clips: List<EffectClip>): Map<Long, List<KeyTrack>> {
            val keyed = clips.filter { !it.hidden && it.keys.any { (k, v) -> v.isNotEmpty() && Keyframes.animatable(k) } }
            if (keyed.isEmpty()) return emptyMap()
            val out = LinkedHashMap<Long, List<KeyTrack>>()
            for (clip in keyed) {
                val ov = clip.overrides.filterKeys { ClipEdits.isClipKey(clip.effectId, it) }
                val cacheKey = listOf(System.identityHashCode(s), ov, clip.keys, aspect, loopSec)
                val cached = synchronized(keyCache) { keyCache[cacheKey] }
                val tracks = cached ?: run {
                    val cs = s + ov
                    val base = build(cs, aspect, loopSec, emptyList(), false).floatUniforms
                    clip.keys.filter { (k, v) -> v.isNotEmpty() && Keyframes.animatable(k) && k.startsWith("${clip.effectId}.") }
                        .map { (key, ks) ->
                            val per = ks.map { build(cs + (key to it.value), aspect, loopSec, emptyList(), false).floatUniforms }
                            val arrays = LinkedHashMap<String, Array<FloatArray>>()
                            val changed = LinkedHashMap<String, BooleanArray>()
                            for ((name, b) in base) {
                                val vs = per.map { it[name] ?: b }
                                var mask: BooleanArray? = null
                                for (v in vs) for (i in 0 until minOf(b.size, v.size)) if (v[i] != b[i]) {
                                    if (mask == null) mask = BooleanArray(b.size)
                                    mask[i] = true
                                }
                                if (mask != null) { arrays[name] = vs.map { it.copyOf(b.size) }.toTypedArray(); changed[name] = mask }
                            }
                            KeyTrack(key, ks, arrays, changed)
                        }.filter { it.arrays.isNotEmpty() }
                        .also { made -> synchronized(keyCache) { keyCache[cacheKey] = made } }
                }
                if (tracks.isNotEmpty()) out[clip.uid] = tracks
            }
            return out
        }

        /** v44.15 · efectos cuya 2.ª instancia tiene su propio `#ifdef FX_<ID>2` en el shader. */
        val copyFeatures = listOf("blast", "blood", "slash", "smoke")

        /**
         * v44.15 · El humo se puede dibujar detrás o delante del personaje. Antes el shader llevaba
         * el humo completo DOS veces (una por sitio); ahora solo se compila el sitio que se usa:
         * "smokeback" / "smokefront". Si algún bloque lo cambia con ajustes o claves, van los dos.
         */
        fun smokeLayers(s: Map<String, Float>, clips: List<EffectClip>): Set<String> {
            val values = mutableListOf(s["smoke.layer"] ?: Effects.default("smoke.layer"))
            for (c in clips) {
                c.overrides["smoke.layer"]?.let { values += it }
                if (c.keys["smoke.layer"]?.isNotEmpty() == true) return setOf("smokeback", "smokefront")
            }
            return buildSet {
                if (values.any { it < .5f }) add("smokeback")
                if (values.any { it >= .5f }) add("smokefront")
            }
        }

        /** Bloques por efecto; los ocultos se quitan (lista vacía = efecto apagado). */
        fun timelineOf(clips: List<EffectClip>, features: Set<String>, muteUnclipped: Boolean): Map<String, List<EffectClip>> {
            val map = LinkedHashMap<String, List<EffectClip>>()
            for ((id, list) in clips.groupBy { it.effectId }) map[id] = list.filter { !it.hidden }
            if (muteUnclipped) for (id in features) if (id !in map && id != "elements") map[id] = emptyList()
            return map
        }

        private fun build(s: Map<String, Float>, aspect: Float, loopSec: Float, clips: List<EffectClip>,
                          muteUnclipped: Boolean): RenderParams {
            fun v(k: String): Float = s[k] ?: Effects.default(k)
            fun on(id: String): Boolean = v("$id.on") > 0.5f
            fun cyc(id: String): Float = max(1, v("$id.cycles").roundToInt()).toFloat()
            fun v3(id: String, a: String, b: String) =
                floatArrayOf(if (on(id)) v("$id.$a") else 0f, v("$id.$b"), cyc(id))
            fun v4(id: String, a: String, b: String, conv: (Float) -> Float = { it }) =
                floatArrayOf(if (on(id)) v("$id.$a") else 0f, conv(v("$id.$b")), cyc(id), v("$id.zone"))

            val asp = if (aspect > 0f) aspect else 1f
            val drift = if (on("drift")) {
                val a = Math.toRadians(v("drift.dir").toDouble())
                val sp = v("drift.speed")
                floatArrayOf((cos(a) * sp / asp).toFloat(), (-sin(a) * sp).toFloat())
            } else floatArrayOf(0f, 0f)

            // v44: dirección + FUERZA (longitud del vector) y viento global (model/Wind.kt).
            val directional = Wind.directional
            val motion = directional.associateWith { id ->
                val (deg, force) = Wind.resolve(s, id)
                val angle = Math.toRadians(deg.toDouble())
                val origin = (s["$id.origin"] ?: 0f).toInt()
                val spots = arrayOf(floatArrayOf(0.5f, 0.5f), floatArrayOf(0.5f, 0f),
                    floatArrayOf(0.5f, 1f), floatArrayOf(0f, 0.5f), floatArrayOf(1f, 0.5f),
                    floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f),
                    floatArrayOf(1f, 1f))
                val pt = spots[origin.coerceIn(0, 8)]
                if (id == "rays" && !s.containsKey("$id.origin")) {
                    pt[0] = v("rays.x")
                    pt[1] = 0f
                }
                val defO = Wind.defaultOrigin[id]
                floatArrayOf((cos(angle) * force).toFloat(), (sin(angle) * force).toFloat(),
                    (if (origin == 0) s["$id.originX"] ?: defO?.first ?: pt[0] else pt[0]).coerceIn(0f, 1f),
                    (if (origin == 0) s["$id.originY"] ?: defO?.second ?: pt[1] else pt[1]).coerceIn(0f, 1f))
            }
            val extras = listOf("ash", "leaves", "embers", "dust", "bubbles", "electric", "focus",
                "shock", "flash", "tone", "grain", "ink", "comet", "runes", "hatch",
                "pulse", "smoke", "shards", "flame", "eyes", "blink", "cloth",
                "bokeh", "sparkle", "lightleak", "waterfall", "reflect", "blackaura", "inkdrip", "dramatic",
                "blast", "blood", "slash", "reveal").associateWith { id ->
                val def = Effects.all.first { it.id == id }
                fun param(key: String): Float = v("$id.$key")
                floatArrayOf(if (on(id)) param("int") else 0f,
                    param(def.params.firstOrNull { it.key != "int" && it.key != "cycles" && it.key != "zone" }?.key ?: "int"),
                    cyc(id), param("zone"))
            }
            // Instancia B: mismo motor, parámetros, máscara, origen y velocidad independientes.
            val copyIds = listOf("ash", "leaves", "embers", "dust", "bubbles", "electric",
                "focus", "shock", "flash", "tone", "grain", "ink")
            val copyExtras = copyIds.associateWith { id ->
                val def = Effects.all.first { it.id == id }
                val prefix = "$id.copy"
                fun param(k: String) = s["$prefix.$k"] ?: v("$id.$k")
                floatArrayOf(if ((s["$prefix.on"] ?: 0f) > 0.5f) param("int") else 0f,
                    param(def.params.firstOrNull { it.key != "int" && it.key != "cycles" && it.key != "zone" }?.key ?: "int"),
                    max(1, param("cycles").roundToInt()).toFloat(), param("zone"))
            }
            val copyMotions = copyIds.filter { it in directional }.associateWith { id ->
                val prefix = "$id.copy"
                val (deg, force) = Wind.resolve(s, id, prefix)
                val angle = Math.toRadians(deg.toDouble())
                val origin = (s["$prefix.origin"] ?: 0f).toInt().coerceIn(0, 8)
                val spots = arrayOf(floatArrayOf(0.5f, 0.5f), floatArrayOf(0.5f, 0f),
                    floatArrayOf(0.5f, 1f), floatArrayOf(0f, 0.5f), floatArrayOf(1f, 0.5f),
                    floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 1f),
                    floatArrayOf(1f, 1f))
                val spot = spots[origin]
                floatArrayOf((cos(angle) * force).toFloat(), (sin(angle) * force).toFloat(),
                    (if (origin == 0) s["$prefix.originX"] ?: spot[0] else spot[0]).coerceIn(0f, 1f),
                    (if (origin == 0) s["$prefix.originY"] ?: spot[1] else spot[1]).coerceIn(0f, 1f))
            }
            val featureSet: Set<String> = buildSet {
                for (e in Effects.all) if (e.toggleable && (on(e.id) || (s["${e.id}.copy.on"] ?: 0f) > 0.5f)) add(e.id)
                if ((0..3).any { (s["element$it.on"] ?: 0f) > 0.5f }) add("elements")
            }
            val timelineMap = timelineOf(clips, featureSet, muteUnclipped)
            // v44.15 · la 2.ª instancia (dos humos, explosiones, sangres o cortes a la vez) va en su
            // propio bloque del shader: solo se compila si hay dos bloques de ese efecto en la línea
            // de tiempo. Con uno solo, el shader de cada cuadro es mucho más ligero.
            val shaderFeatures: Set<String> = featureSet + copyFeatures.filter { id ->
                id in featureSet && (timelineMap[id]?.size ?: 0) >= 2 }.map { it + "2" } +
                smokeLayers(s, timelineMap["smoke"].orEmpty()).takeIf { "smoke" in featureSet }.orEmpty()
            return RenderParams(
                studio = buildMap {
                    put("uZoneMotion",floatArrayOf(if(on("zonemotion"))v("zonemotion.int") else 0f,
                        cyc("zonemotion"),v("zonemotion.variant"),Math.toRadians(v("zonemotion.direction").toDouble()).toFloat()))
                    put("uLoopControl", floatArrayOf((s["flow.mode"] ?: 0f).coerceIn(0f,2f),
                        (s["flow.blend"] ?: 1f).coerceIn(.05f,1f), (s["flow.phase"] ?: 0f).coerceIn(0f,1f),0f))
                    fun color(k:String,lo:Float=-1f,hi:Float=1f)=(s["adjust.$k"] ?: 0f).coerceIn(lo,hi)
                    put("uColorA",floatArrayOf(color("brightness",-.5f,.5f),color("contrast"),color("saturation"),color("exposure",-2f,2f)))
                    put("uColorB",floatArrayOf(color("temperature"),Math.toRadians(color("hue",-180f,180f).toDouble()).toFloat(),color("shadows"),color("highlights")))
                    put("uColorC",floatArrayOf(color("sharpen",0f),color("glow",0f),color("vignette",0f),color("grain",0f)))
                    put("uColorD",floatArrayOf(color("fade",0f),color("prism",0f),0f,0f))
                    for (id in listOf("sky", "water", "dispersion")) put("uStudio${id.replaceFirstChar { it.uppercaseChar() }}", floatArrayOf(
                        if (on(id)) v("$id.int") else 0f, cyc(id), v("$id.variant"), Math.toRadians(v("$id.direction").toDouble()).toFloat()))
                    put("uDispersionMore", floatArrayOf(v("dispersion.size"), v("dispersion.distance"), 0f, 0f))
                    put("uLightStyles", floatArrayOf(
                        v("rays.variant"), v("sunpath.variant"), v("energy.variant"), v("flame.variant")
                    ))
                    // v44 · cámara libre: posición, giro y zoom directos (se animan con claves).
                    put("uCamManual", floatArrayOf(v("cam.mx"), v("cam.my"), Math.toRadians(v("cam.mrot").toDouble()).toFloat(), v("cam.mzoom")))
                    put("uCameraMode", floatArrayOf(
                        s["cam.mode"] ?: 0f,
                        Math.toRadians((s["cam.direction"] ?: 0f).toDouble()).toFloat(),
                        0f, 0f
                    ))
                    put("uParallaxStyle", floatArrayOf(v("parallax.variant"), v("parallax.focus"),
                        0.002f + 0.012f * v("parallax.dilate").coerceIn(0f, 1f),
                        // v46: w = usar placa de fondo (FondoGl lo apaga si aún no hay placa)
                        if (v("parallax.plate") > .5f) 1f else 0f))
                    // v45: x = usar IA (FondoGl lo apaga si aún no hay mapa), y = capas (1 = continua),
                    // z = fuerza de los pinceles Acercar/Alejar, w = ver mapa (solo vista previa).
                    put("uDepthInfo", floatArrayOf(if (v("parallax.ai") > .5f) 1f else 0f,
                        v("parallax.layers").coerceIn(1f, 4f), v("parallax.fix").coerceIn(0f, 1f), 0f))
                    // v36: forma/comportamiento real de cada familia de efectos.
                    fun box(id: String) = floatArrayOf(s["$id.boxX0"] ?: 0f, s["$id.boxY0"] ?: 0f,
                        s["$id.boxX1"] ?: 0f, s["$id.boxY1"] ?: 0f)
                    put("uNaturalBox", box("natural"))
                    put("uFlameBox", box("flame"))
                    put("uSmokeShape", floatArrayOf(v("smoke.shape"), v("smoke.turb"), v("smoke.soft"), v("smoke.fill")))
                    put("uFlameShape", floatArrayOf(v("flame.shape"), v("flame.turb"), v("flame.soft"), v("flame.height")))
                    put("uFlameExtra", floatArrayOf(v("flame.sparks"), v("flame.smoke"), 0f, 0f))
                    put("uBlastShape", floatArrayOf(v("blast.shape"), v("blast.flash"), v("blast.ring"), v("blast.push")))
                    put("uBloodShape", floatArrayOf(v("blood.shape"),
                        Math.toRadians(v("blood.direction").toDouble()).toFloat(), v("blood.spread"), 0f))
                    put("uSlashShape", floatArrayOf(v("slash.shape"), v("slash.width"), 0f, 0f))
                    put("uEnergyShape", floatArrayOf(v("energy.shape"), v("energy.size"), v("energy.turb"), 0f))
                    put("uEyesShape", floatArrayOf(v("eyes.shape"), v("eyes.halo"), 0f, 0f))
                    put("uFogShape", floatArrayOf(v("fog.shape"), v("fog.turb"), 0f, 0f))
                    put("uRevealShape", floatArrayOf(v("reveal.shape"), v("reveal.edge"), 0f, 0f))
                    // Fase propia de los eventos (-1 = reloj del bucle). La línea de tiempo
                    // la reemplaza cuadro a cuadro en modo Una vez / Repetir.
                    put("uEventPhase", floatArrayOf(-1f, -1f, -1f, -1f))
                    put("uEventPhase2", floatArrayOf(-1f, -1f, -1f, -1f))
                    for (id in Effects.colorable) put("uTint${id.replaceFirstChar { it.uppercaseChar() }}", tintOf(s, id))
                    put("uParallax", floatArrayOf(
                        if (on("parallax")) v("parallax.amount") else 0f,
                        Math.toRadians(v("parallax.direction").toDouble()).toFloat(),
                        cyc("parallax"),
                        v("parallax.depth")
                    ))
                    put("uMesh", floatArrayOf(
                        if (on("mesh")) v("mesh.int") else 0f,
                        v("mesh.size"),
                        cyc("mesh"),
                        v("mesh.variant")
                    ))
                    put("uMeshDirection", floatArrayOf(
                        Math.toRadians(v("mesh.direction").toDouble()).toFloat(), 0f, 0f, 0f
                    ))
                    put("uNatural", floatArrayOf(
                        if (on("natural")) v("natural.int") else 0f,
                        Math.toRadians(v("natural.direction").toDouble()).toFloat(),
                        cyc("natural"),
                        v("natural.variant")
                    ))
                    put("uNaturalMore", floatArrayOf(
                        v("natural.flex"),
                        v("natural.variation"),
                        v("natural.gust"),
                        v("natural.anchor")
                    ))
                    put("uNaturalDynamics", floatArrayOf(
                        v("natural.weight"), v("natural.spring"), 0f, 0f
                    ))
                    put("uSmokeControl", floatArrayOf(
                        v("smoke.variant"), v("smoke.density"), v("smoke.rise"), v("smoke.layer")
                    ))
                    put("uBlastMore", floatArrayOf(
                        v("blast.variant"), v("blast.smoke"), v("blast.sparks"), 0f
                    ))
                    put("uBloodStyle", floatArrayOf(v("blood.variant"), 0f, 0f, 0f))
                    put("uSlashStyle", floatArrayOf(v("slash.variant"),
                        Math.toRadians(v("slash.direction").toDouble()).toFloat(), 0f, 0f))
                    put("uRevealStyle", floatArrayOf(v("reveal.variant"), 0f, 0f, 0f))
                    put("uLeavesStyle", floatArrayOf(
                        v("leaves.variant"),
                        s["leaves.copy.variant"] ?: v("leaves.variant"),
                        0f, 0f
                    ))
                    // v16: escenas anime con eventos en la línea de tiempo.
                    put("uLoopInfo", floatArrayOf(loopSec.coerceAtLeast(0.5f), -1f, -1f, 0f))
                    put("uBolt", floatArrayOf(if (on("bolt")) v("bolt.int") else 0f, v("bolt.x"), cyc("bolt"), v("bolt.flash")))
                    put("uGust", floatArrayOf(if (on("gust")) v("gust.int") else 0f, v("gust.start"), v("gust.dur"), cyc("gust")))
                    put("uGustMore", floatArrayOf(v("gust.debris"), 0f, 0f, 0f))
                    put("uTornado", floatArrayOf(if (on("tornado")) v("tornado.int") else 0f, v("tornado.size"), cyc("tornado"), v("tornado.variant")))
                    val hasChar = (s["appear.hasImage"] ?: 0f) > .5f
                    put("uAppear", floatArrayOf(if (on("appear") && hasChar) v("appear.opacity") else 0f,
                        v("appear.start"), v("appear.dur").coerceAtLeast(0.02f), v("appear.variant")))
                    put("uAppearPose", floatArrayOf(s["appear.x"] ?: .5f, s["appear.y"] ?: .62f,
                        (s["appear.size"] ?: .6f).coerceIn(.05f, 2f),
                        Math.toRadians((s["appear.rot"] ?: 0f).toDouble()).toFloat()))
                    put("uClouds3", floatArrayOf(if (on("clouds3")) v("clouds3.int") else 0f, v("clouds3.scale"), cyc("clouds3"), v("clouds3.variant")))
                    put("uShore", floatArrayOf(if (on("shore")) v("shore.int") else 0f, v("shore.freq"), cyc("shore"), v("shore.tint")))
                    put("uShoreMore", floatArrayOf(v("shore.foam"), v("shore.variant"), 0f, 0f))
                    put("uSunpath", floatArrayOf(if (on("sunpath")) v("sunpath.int") else 0f, v("sunpath.x"), v("sunpath.width"), cyc("sunpath")))
                    put("uImpact", floatArrayOf(if (on("impact")) v("impact.int") else 0f, v("impact.shake"), v("impact.start"), cyc("impact")))
                    put("uImpactMore", floatArrayOf(v("impact.flash"), 0f, 0f, 0f))
                    // v44 · efectos nuevos: (intensidad, valor principal, ciclos, zona=0) + «More».
                    put("uFxSand", floatArrayOf(if (on("sand")) v("sand.int") else 0f, v("sand.grains"), cyc("sand"), 0f))
                    put("uSandMore", floatArrayOf(v("sand.variant"), v("sand.veil"), 0f, 0f))
                    put("uFxCloudshadow", floatArrayOf(if (on("cloudshadow")) v("cloudshadow.int") else 0f, v("cloudshadow.size"), cyc("cloudshadow"), 0f))
                    put("uCloudshadowMore", floatArrayOf(v("cloudshadow.soft"), 0f, 0f, 0f))
                    put("uFxGlassrain", floatArrayOf(if (on("glassrain")) v("glassrain.int") else 0f, v("glassrain.size"), cyc("glassrain"), 0f))
                    put("uGlassrainMore", floatArrayOf(v("glassrain.fog"), v("glassrain.trails"), 0f, 0f))
                    put("uFxBirds", floatArrayOf(if (on("birds")) v("birds.int") else 0f, v("birds.count"), cyc("birds"), 0f))
                    put("uBirdsMore", floatArrayOf(v("birds.size"), v("birds.variant"), 0f, 0f))
                    put("uFxAurora", floatArrayOf(if (on("aurora")) v("aurora.int") else 0f, v("aurora.height"), cyc("aurora"), 0f))
                    put("uAuroraMore", floatArrayOf(v("aurora.variant"), v("aurora.base"), 0f, 0f))
                    put("uFxCitylights", floatArrayOf(if (on("citylights")) v("citylights.int") else 0f, v("citylights.blink"), cyc("citylights"), 0f))
                    put("uCitylightsMore", floatArrayOf(v("citylights.variant"), v("citylights.halo"), 0f, 0f))
                    put("uFxLensflare", floatArrayOf(if (on("lensflare")) v("lensflare.int") else 0f, v("lensflare.size"), cyc("lensflare"), 0f))
                    put("uLensflareMore", floatArrayOf(v("lensflare.variant"), 0f, 0f, 0f))
                    // v44.11 · manga / manhwa
                    put("uFxSymbols", floatArrayOf(if (on("symbols")) v("symbols.int") else 0f, v("symbols.size"), cyc("symbols"), 0f))
                    put("uSymbolsMore", floatArrayOf(v("symbols.variant"), 0f, 0f, 0f))
                    put("uFxGlowup", floatArrayOf(if (on("glowup")) v("glowup.int") else 0f, v("glowup.bloom"), cyc("glowup"), 0f))
                    put("uGlowupMore", floatArrayOf(v("glowup.variant"), v("glowup.motes"), 0f, 0f))
                    put("uFxInkweather", floatArrayOf(if (on("inkweather")) v("inkweather.int") else 0f, v("inkweather.amount"), cyc("inkweather"), 0f))
                    put("uInkweatherMore", floatArrayOf(v("inkweather.variant"), v("inkweather.thick"), 0f, 0f))
                    // v44.12 · más escenas
                    put("uFxDapple", floatArrayOf(if (on("dapple")) v("dapple.int") else 0f, v("dapple.size"), cyc("dapple"), 0f))
                    put("uDappleMore", floatArrayOf(v("dapple.variant"), v("dapple.soft"), 0f, 0f))
                    put("uFxCaustics", floatArrayOf(if (on("caustics")) v("caustics.int") else 0f, v("caustics.size"), cyc("caustics"), 0f))
                    put("uCausticsMore", floatArrayOf(v("caustics.variant"), 0f, 0f, 0f))
                    put("uFxSteam", floatArrayOf(if (on("steam")) v("steam.int") else 0f, v("steam.size"), cyc("steam"), 0f))
                    put("uSteamMore", floatArrayOf(v("steam.height"), 0f, 0f, 0f))
                    put("uFxFlicker", floatArrayOf(if (on("flicker")) v("flicker.int") else 0f, 0f, cyc("flicker"), 0f))
                    put("uFlickerMore", floatArrayOf(v("flicker.variant"), 0f, 0f, 0f))
                    // v44.13 · cielo, mar y luz de la imagen + acabado de cine
                    put("uFxSkydrift", floatArrayOf(if (on("skydrift")) v("skydrift.int") else 0f, v("skydrift.amount"), cyc("skydrift"), 0f))
                    put("uSkydriftMore", floatArrayOf(v("skydrift.horizon"), v("skydrift.life"), 0f, 0f))
                    put("uFxSeawaves", floatArrayOf(if (on("seawaves")) v("seawaves.int") else 0f, v("seawaves.size"), cyc("seawaves"), 0f))
                    put("uSeawavesMore", floatArrayOf(v("seawaves.horizon"), v("seawaves.glitter"), 0f, 0f))
                    put("uFxSunlight", floatArrayOf(if (on("sunlight")) v("sunlight.int") else 0f, v("sunlight.size"), cyc("sunlight"), 0f))
                    put("uSunlightMore", floatArrayOf(v("sunlight.variant"), v("sunlight.rays"), v("sunlight.warm"), 0f))
                    put("uFxFinish", if (on("finish")) floatArrayOf(v("finish.int"), v("finish.bloom"), v("finish.vignette"), v("finish.grain")) else floatArrayOf(0f, 0f, 0f, 0f))
                    put("uFinishMore", floatArrayOf(v("finish.variant"), if (on("finish")) v("finish.aberration") else 0f, 0f, 0f))
                    // v44.14 · luz y color de edit
                    put("uFxGradmap", floatArrayOf(if (on("gradmap")) v("gradmap.int") else 0f, v("gradmap.contrast"), 0f, 0f))
                    put("uGradmapMore", floatArrayOf(v("gradmap.variant"), 0f, 0f, 0f))
                    put("uFxAtmos", floatArrayOf(if (on("atmos")) v("atmos.int") else 0f, v("atmos.height"), cyc("atmos"), 0f))
                    put("uAtmosMore", floatArrayOf(v("atmos.variant"), 0f, 0f, 0f))
                    put("uFxLightdir", floatArrayOf(if (on("lightdir")) v("lightdir.int") else 0f, v("lightdir.spread"), cyc("lightdir"), 0f))
                    put("uLightdirMore", floatArrayOf(v("lightdir.variant"), v("lightdir.edge"), v("lightdir.shadow"), 0f))
                    put("uFxRimlight", floatArrayOf(if (on("rimlight")) v("rimlight.int") else 0f, v("rimlight.size"), cyc("rimlight"), 0f))
                    put("uRimlightMore", floatArrayOf(v("rimlight.variant"), 0f, 0f, 0f))
                    // v44.15 · efectos de editor de PC
                    put("uFxGlitch", floatArrayOf(if (on("glitch")) v("glitch.int") else 0f, v("glitch.freq"), cyc("glitch"), 0f))
                    put("uGlitchMore", floatArrayOf(v("glitch.variant"), 0f, 0f, 0f))
                    put("uFxRadialblur", floatArrayOf(if (on("radialblur")) v("radialblur.int") else 0f, 0f, cyc("radialblur"), 0f))
                    put("uRadialblurMore", floatArrayOf(v("radialblur.variant"), 0f, 0f, 0f))
                    put("uFxFocusblur", floatArrayOf(if (on("focusblur")) v("focusblur.int") else 0f, v("focusblur.width"), cyc("focusblur"), 0f))
                    put("uFocusblurMore", floatArrayOf(v("focusblur.variant"), v("focusblur.y"), 0f, 0f))
                    put("uFxDeepglow", floatArrayOf(if (on("deepglow")) v("deepglow.int") else 0f, v("deepglow.threshold"), v("deepglow.size"), 0f))
                    put("uDeepglowMore", floatArrayOf(v("deepglow.variant"), 0f, 0f, 0f))
                    put("uFxVhs", floatArrayOf(if (on("vhs")) v("vhs.int") else 0f, v("vhs.lines"), cyc("vhs"), v("vhs.noise")))
                    put("uFxFilmburn", floatArrayOf(if (on("filmburn")) v("filmburn.int") else 0f, v("filmburn.size"), cyc("filmburn"), 0f))
                    put("uFilmburnMore", floatArrayOf(v("filmburn.variant"), 0f, 0f, 0f))
                    put("uFxRaindrops", floatArrayOf(if (on("raindrops")) v("raindrops.int") else 0f, v("raindrops.amount"), cyc("raindrops"), 0f))
                    put("uRaindropsMore", floatArrayOf(v("raindrops.variant"), v("raindrops.size"), 0f, 0f))
                    // v44.11 · color propio de cada efecto (índice = EffectGrade.ids)
                    com.zeta.fondo.model.EffectGrade.ids.forEachIndexed { i, gid ->
                        put("uGrade[$i]", com.zeta.fondo.model.EffectGrade.uniform(s, gid))
                    }
                    // v44 · lluvia: charcos, gotas cercanas y neblina; estrellas fugaces.
                    put("uRainMore", floatArrayOf(v("rain.puddles"), v("rain.near"), v("rain.haze"), 0f))
                    put("uStarsMore", floatArrayOf(v("stars.shooting"), 0f, 0f, 0f))
                    put("uSnowMore", floatArrayOf(v("snow.swirl"), v("snow.haze"), 0f, 0f))
                    put("uFogMore", floatArrayOf(v("fog.height"), v("fog.veil"), 0f, 0f))
                    put("uLeavesMore", floatArrayOf(v("leaves.whirl"), 0f, 0f, 0f))
                    // v44 · viento global: ráfagas y turbulencia (solo si está activado).
                    val windOn = Wind.globalOn(s)
                    val wd = Math.toRadians((s["wind.direction"] ?: 0f).toDouble())
                    put("uWind", floatArrayOf(if (windOn) (s["wind.gust"] ?: 0f) else 0f, if (windOn) (s["wind.turb"] ?: 0f) else 0f,
                        cos(wd).toFloat(), sin(wd).toFloat()))
                    // v44 · segundas instancias: apagadas salvo que dos bloques se solapen.
                    for (names in FrameUniforms.secondInstance.values) for (n in names) put(n + "2", FloatArray(4))
                    put("uEventPhaseB", floatArrayOf(-1f, -1f, -1f, -1f))
                    for (i in 0..3) {
                        val id = "element$i"
                        put("uElementPose[$i]", floatArrayOf(s["$id.x"] ?: .5f, s["$id.y"] ?: .5f,
                            (s["$id.size"] ?: .3f).coerceIn(.04f,1.5f), Math.toRadians((s["$id.rotation"] ?: 0f).toDouble()).toFloat()))
                        put("uElementStyle[$i]", floatArrayOf(if ((s["$id.on"] ?: 0f) > .5f) (s["$id.opacity"] ?: .7f).coerceIn(0f,1f) else 0f,
                            (s["$id.type"] ?: 0f).coerceIn(0f,5f), (s["$id.cycles"] ?: 1f).roundToInt().coerceIn(1,6).toFloat(), 0f))
                    }
                },
                aspect = asp,
                flow = floatArrayOf(if (on("flow")) 1f else 0f, v("flow.amount"), cyc("flow")),
                drift = drift,
                ripple = v3("ripple", "amp", "freq"),
                glint = v3("glint", "int", "dens"),
                sway = v3("sway", "amp", "freq"),
                heat = v3("heat", "amp", "freq"),
                rain = v4("rain", "int", "angle") { Math.toRadians(it.toDouble()).toFloat() },
                snow = v4("snow", "int", "size"),
                sakura = v4("sakura", "int", "size"),
                stars = v4("stars", "int", "dens"),
                flies = v4("flies", "int", "dens"),
                fog = v4("fog", "int", "scale"),
                rays = v4("rays", "int", "x"),
                energy = v4("energy", "int", "dens"),
                speed = v4("speed", "int", "angle"),
                cam = if ((s["cam.mode"] ?: 0f) > 8.5f) floatArrayOf(if (on("cam")) 1f else 0f, 0f, cyc("cam"))
                else floatArrayOf(
                    if (on("cam")) v("cam.zoom") else 0f,
                    if (on("cam")) v("cam.pan") else 0f,
                    cyc("cam")
                ),
                mono = if (v("style.mono") > 0.5f) 1f else 0f,
                snap = if (v("style.snap") > 0.5f) 1f else 0f,
                motion = motion,
                extra = extras,
                effectMasks = Effects.all.associate { it.id to maskSlot(s, it.id) },
                copyMotion = copyMotions,
                copyExtra = copyExtras,
                copyMasks = copyIds.associateWith { id -> maskSlot(s, "$id.copy") },
                timeline = timelineMap,
                loopSec = loopSec.coerceAtLeast(0.5f),
                events = Effects.eventEffects.associateWith { id ->
                    floatArrayOf(s["$id.mode"] ?: 0f, (s["$id.every"] ?: 2f).coerceIn(.2f, 60f))
                },
                features = shaderFeatures,
                // v44.19 · Calidad cine: [activo, brillo, luces de la imagen, halo, grano].
                post = floatArrayOf(
                    if (v("cine.active") > .5f) 1f else 0f,
                    v("cine.bloom"), v("cine.image"), v("cine.halo"), v("cine.grain"),
                    // v44.20 · [5] estela, [6] luz sobre la escena, [7] look, [8] fuerza, [9] franjas
                    v("cine.mblur"), v("cine.spill"), v("cine.variant"), v("cine.lookamt"), v("cine.bars"))
            )
        }
    }
}

/** v44 · Un parámetro animado de un bloque: sus claves y el vector de cada uniform afectado en cada clave. */
class KeyTrack(val key: String, val keys: List<Keyframe>, val arrays: Map<String, Array<FloatArray>>,
               val changed: Map<String, BooleanArray>) {
    /** Escribe en [a] (uniform [name]) los componentes animados en el instante local [local]. */
    fun apply(name: String, a: FloatArray, local: Float) {
        val arrs = arrays[name] ?: return
        val mask = changed[name] ?: return
        val (i, w) = Keyframes.segment(keys, local)
        val j = (i + 1).coerceAtMost(arrs.size - 1)
        for (c in mask.indices) if (mask[c] && c < a.size) a[c] = arrs[i][c] + (arrs[j][c] - arrs[i][c]) * w
    }
}

/** v40 · Cambios de un bloque respecto a los ajustes del efecto (por componente). */
class ClipDiff(val floats: Map<String, Patch>, val ints: Map<String, Int>) {
    /** [values] = vector del bloque; solo se aplican los componentes con [changed]. */
    class Patch(val values: FloatArray, val changed: BooleanArray)
    val isEmpty: Boolean get() = floats.isEmpty() && ints.isEmpty()
}
