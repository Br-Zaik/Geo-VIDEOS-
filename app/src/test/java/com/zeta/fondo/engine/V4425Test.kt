package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectDefaults
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.ExportSettings
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.LayerJson
import com.zeta.fondo.model.jsonSafe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.exp
import kotlin.math.hypot

/**
 * v44.25 · Explosión y efectos «Detrás» sin máscara a escondidas, detector que no confunde un
 * paisaje con un personaje, guardado a prueba de valores inválidos, memoria, 16 KB, varias
 * imágenes y optimización.
 */
class V4425Test {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val vm by lazy { file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText() }
    private fun fn(src: String, name: String) = src.substringAfter(name).substringBefore("\n    fun ").substringBefore("\n    private fun ")

    // ---------------------------------------------------------------- «Detrás» y Proteger automático

    @Test fun behindNeverCreatesAHiddenMask() {
        val needs = vm.substringAfter("private fun needsCharacter(): Boolean {").substringBefore("\n    }")
        assertFalse("«Detrás» ya no pide Proteger automático", needs.contains(".behind"))
        assertTrue(needs.contains("rimlight"))
        assertFalse(vm.contains("needsProtect"))
        val setBehind = vm.substringAfter("fun setBehind(id: String, behind: Boolean) {").substringBefore("\n    }")
        assertTrue("sin personaje se pregunta", setBehind.contains("if (behind && !maskHasPaint(0)) { behindAsk = id; return }"))
        assertTrue(file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText().contains("if (vm.behindAsk != null) BehindAskDialog(vm)"))
        val dialog = file("src/main/java/com/zeta/fondo/ui/BehindAskDialog.kt").readText()
        for (label in listOf("Buscar personaje solo", "Pintarlo yo", "Dejarlo delante")) assertTrue(label, dialog.contains(label))
    }

    @Test fun effectsStartInFrontWithoutACharacter() {
        for (id in EffectDefaults.behindByDefault) {
            assertFalse(id, EffectDefaults.firstActivation(id, Effects.defaults()).containsKey("$id.behind"))
            assertEquals(id, 1f, EffectDefaults.firstActivation(id, Effects.defaults(), hasCharacter = true)["$id.behind"]!!, 0f)
        }
        assertTrue(vm.contains("EffectDefaults.firstActivation(id, settings, maskHasPaint(0))"))
        // Escenas: sin personaje, todo «Delante» (también lo que ya estaba detrás).
        assertTrue(fn(vm, "fun applyPreset(p: Preset) {").contains("mapValues { (k, v) -> if (k.endsWith(\".behind\")) 0f else v }"))
    }

    @Test fun oldAutomaticMaskIsRemovedOnOpenAndCanBeUndone() {
        val drop = vm.substringAfter("private fun dropStaleAutoProtect(): Boolean {").substringBefore("\n    }")
        assertTrue("solo si TODO es automático", drop.contains("list.any { it.image?.startsWith(\"mask_auto_\") != true }"))
        assertTrue(drop.contains("if (needsCharacter()) return false"))
        assertTrue("↶ la devuelve", drop.contains("pushUndo(UndoAction.RemoveStrokes(list))"))
        val open = vm.substringAfter("fun openProject(").substringBefore("fun saveProject(")
        assertTrue(open.contains("val droppedAuto = dropStaleAutoProtect()"))
        assertTrue(vm.contains("is UndoAction.RemoveStrokes -> masks?.let { mk -> a.strokes.forEach { mk.addStroke(it) } }"))
        assertTrue(vm.contains("is UndoAction.RemoveStrokes -> masks?.let { mk -> a.strokes.forEach { mk.removeStroke(it) } }"))
    }

    @Test fun characterYouAskForIsNeverRemovedLater() {
        // «Buscar personaje solo» (o la luz de contorno) guarda «mask_char_»: al volver a abrir el
        // proyecto NO se quita; solo las «mask_auto_» de versiones anteriores.
        val detect = vm.substringAfter("private fun detectCharacterIntoProtect(").substringBefore("private fun dropStaleAutoProtect")
        assertTrue(detect.contains("val name = \"mask_char_\${System.currentTimeMillis()}.png\""))
        assertFalse(detect.contains("\"mask_auto_"))
        val drop = vm.substringAfter("private fun dropStaleAutoProtect(): Boolean {").substringBefore("\n    }")
        assertFalse(drop.contains("mask_char_"))
    }

    @Test fun androidEightImportsDoNotFail() {
        // Leer solo las medidas devuelve null A PROPÓSITO: antes eso se tomaba como error y en
        // Android 8 (o si ImageDecoder fallaba) toda capa / personaje / foto girada fallaba.
        val bu = file("src/main/java/com/zeta/fondo/engine/BitmapUtil.kt").readText()
        val uriFn = bu.substringAfter("fun decodeUriAtMost(").substringBefore("private fun decodeTarget(")
        assertTrue(uriFn.contains("val head = cr.openInputStream(uri) ?: throw IOException(\"No se pudo abrir la imagen\")"))
        assertTrue(uriFn.contains("head.use { BitmapFactory.decodeStream(it, null, o) }"))
        assertFalse(Regex("""decodeStream\(it, null, o\) \}\s*\?:""").containsMatchIn(uriFn))
    }

    /**
     * Paisaje al atardecer como el de las capturas: cielo con nubes, brillo del sol en el centro,
     * árboles de borde suave a los lados y baldosas (textura en toda la imagen).
     */
    private fun sunsetLandscape(w: Int, h: Int, seed: Long): IntArray {
        val rnd = java.util.Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val fy = y.toFloat() / h; val fx = x.toFloat() / w
            var r = 120 + 110 * fy; var g = 90 + 70 * fy; var b = 150 - 60 * fy
            if (fy < .5f) { val c = 14 * kotlin.math.sin(x * .35f + y * .9f) * kotlin.math.sin(y * .5f); r += c; g += c; b += c }
            else if ((x + (y / 6) * 3) % 8 == 0 || y % 6 == 0) { r -= 22; g -= 22; b -= 22 }
            val glow = exp(-((fx - .5f) * (fx - .5f) + (fy - .45f) * (fy - .45f)) / .03f)
            r += 120 * glow; g += 100 * glow; b += 40 * glow
            val tree = (1f - ((minOf(fx, 1f - fx) - .06f) / .14f).coerceIn(0f, 1f)) * (if (fy < .8f) 1f else .5f)
            val leaf = rnd.nextInt(40) - 20
            r = r * (1 - tree) + (45f + leaf) * tree; g = g * (1 - tree) + (55f + leaf) * tree; b = b * (1 - tree) + (50f + leaf) * tree
            r += rnd.nextInt(12) - 6; g += rnd.nextInt(12) - 6; b += rnd.nextInt(12) - 6
            (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }

    /** Personaje dibujado con contorno de tinta sobre un fondo con detalle. */
    private fun inkedCharacter(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val bar = (x / 6) % 3
        var c = (0xFF shl 24) or ((140 + bar * 20) shl 16) or ((150 + bar * 15) shl 8) or (170 + bar * 10)
        val cx = w / 2f
        val head = hypot(x - cx, y - h * .2f) / (w * .13f)
        val body = y > h * .3f && kotlin.math.abs(x - cx) < w * (.18f + .08f * (y - h * .3f) / h)
        if (head < 1f || body) c = (0xFF shl 24) or (235 shl 16) or (190 shl 8) or 160
        if (kotlin.math.abs(head - 1f) < .08f) c = (0xFF shl 24) or (20 shl 16) or (18 shl 8) or 24
        if (y > h * .3f && kotlin.math.abs(kotlin.math.abs(x - cx) - w * (.18f + .08f * (y - h * .3f) / h)) < 1.5f)
            c = (0xFF shl 24) or (20 shl 16) or (18 shl 8) or 24
        c
    }

    @Test fun landscapeIsNotACharacterButADrawnCharacterIs() {
        val w = 120; val h = 160
        for (seed in 1L..4L) {
            assertNull("paisaje $seed: sin personaje", DepthMath.subjectAuto(sunsetLandscape(w, h, seed), w, h))
            assertNull("apaisado $seed", DepthMath.subjectAuto(sunsetLandscape(h, w, seed), h, w))
        }
        val c = DepthMath.subjectAuto(inkedCharacter(w, h), w, h)
        assertNotNull("personaje con contorno", c); c!!
        assertTrue(c[(h * .55f).toInt() * w + w / 2] > .7f)
        assertTrue(c[(h * .55f).toInt() * w + 3] < .3f)
    }

    @Test fun silhouetteMustFollowRealEdges() {
        val w = 80; val h = 80
        // un corte fuerte en x = 40 y textura suave en toda la imagen
        val lum = FloatArray(w * h) { i -> val x = i % w; val y = i / w
            (if (x < 40) .2f else .8f) + .05f * kotlin.math.sin(x * 1.3f) * kotlin.math.sin(y * 1.7f) }
        val sharp = BooleanArray(w * h) { i -> i % w < 40 }          // borde = el corte real
        val soft = BooleanArray(w * h) { i -> i / w < 40 }           // borde por la textura, sin contorno
        assertTrue(SubjectSegment.edgeSupport(lum, sharp, w, h) > SubjectSegment.MIN_EDGE_SUPPORT)
        assertTrue(SubjectSegment.edgeSupport(lum, soft, w, h) < SubjectSegment.MIN_EDGE_SUPPORT)
        val ss = file("src/main/java/com/zeta/fondo/engine/SubjectSegment.kt").readText()
        assertTrue(ss.contains("if (side > SIDE_ROWS * upto) return false"))
    }

    // ---------------------------------------------------------------- guardado y memoria

    @Test fun invalidNumbersNeverBreakSaving() {
        assertEquals(1.0, Float.NaN.jsonSafe(1f), 0.0)
        assertEquals(0.0, Float.POSITIVE_INFINITY.jsonSafe(), 0.0)
        assertEquals(.25, .25f.jsonSafe(), 1e-9)
        // Con el org.json de Android un NaN lanzaba excepción y el proyecto no se guardaba.
        val bad = ImageLayer(1, "x", "layers/a.png", Float.NaN, x = Float.NaN, sx = Float.POSITIVE_INFINITY,
            opacity = Float.NaN, depth = Float.POSITIVE_INFINITY)
        val json = LayerJson.toJson(listOf(bad)).toString()
        assertFalse(json.contains("NaN")); assertFalse(json.contains("Infinity"))
        ExportSettings(cropZoom = Float.NaN, soundVolume = Float.NaN).toJson()   // no lanza
        val store = file("src/main/java/com/zeta/fondo/model/ProjectStore.kt").readText()
        assertTrue(store.contains("for ((k, v) in settings) if (v.isFinite()) so.put(k, v.toDouble())"))
        assertTrue(store.contains("private fun r4(v: Float): Double = if (v.isFinite())"))
        assertTrue(fn(vm, "fun setSetting(key: String, value: Float) {").contains("if (!value.isFinite()) return"))
    }

    @Test fun bigPhotosAreDecodedAtTheirFinalSize() {
        val bu = file("src/main/java/com/zeta/fondo/engine/BitmapUtil.kt").readText()
        assertTrue(bu.contains("fun decodeUriAtMost(cr: ContentResolver, uri: Uri, maxSide: Int, orientation: Int = 1): Bitmap"))
        assertTrue("Android 9+: tamaño exacto", bu.substringAfter("fun decodeAtMost(").contains("decodeTarget(ImageDecoder.createSource(file), maxSide)"))
        val store = file("src/main/java/com/zeta/fondo/model/ProjectStore.kt").readText()
        assertTrue(store.contains("BitmapUtil.decodeUriAtMost(cr, uri, rotatedMax, orientation)"))
        // EXIF completo: giros y espejos (cámara frontal); si el decodificador la giró por su cuenta
        // (PNG/WebP/HEIC), también se guarda derecha → vista previa, vídeo y máscaras coinciden.
        assertTrue(store.contains("fun readOrientation(uri: Uri): Int"))
        assertFalse(store.contains("readRotation") || vm.contains("readRotation"))
        assertTrue(store.contains("if (turned) p.recycle() else preview = p"))
        assertTrue(vm.contains("BitmapUtil.decodeUriAtMost(resolver, uri, 2048, store.readOrientation(uri))"))
        for (line in listOf("2 -> setScale(-1f, 1f)", "3 -> setRotate(180f)", "4 -> { setRotate(180f); postScale(-1f, 1f) }",
                "5 -> { setRotate(90f); postScale(-1f, 1f) }", "6 -> setRotate(90f)", "7 -> { setRotate(-90f); postScale(-1f, 1f) }",
                "8 -> setRotate(-90f)")) assertTrue(line, bu.contains(line))
        assertFalse("ya no se decodifica hasta 16K", store.contains(">= 8192"))
        val character = fn(vm, "fun importCharacter(uri: Uri) {")
        assertTrue(character.contains("BitmapUtil.decodeUriAtMost(resolver, uri, 2048)"))
        assertTrue(character.contains("if (busy != null) return"))
        assertTrue(character.contains("catch (e: Throwable)"))
        assertFalse(character.contains("decodeStream(it)"))
    }

    // ---------------------------------------------------------------- toques rápidos y trabajos pesados

    @Test fun rapidTapsAndHeavyJobs() {
        val open = vm.substringAfter("fun openProject(").substringBefore("viewModelScope.launch")
        assertTrue(open.contains("if (opening || (!fromImport && busy != null)) return"))
        val export = fn(vm, "fun startExport() {")
        assertTrue(export.contains("if (srState is AiJob.Running)"))
        assertTrue(export.contains("if (capturing)"))
        val enhance = fn(vm, "fun enhanceImage(scale: Int = srScale) {")
        assertTrue(enhance.contains("ExportRunner.running"))
        assertTrue(fn(vm, "fun captureFrame() {").contains("if (srState is AiJob.Running)"))
    }

    @Test fun longAudioIsCutWhileDecoding() {
        val a = file("src/main/java/com/zeta/fondo/engine/ImportedAudio.kt").readText()
        assertTrue(a.contains("fun decode(context: Context, uri: Uri, target: File, maxSeconds: Int = MAX_SECONDS): Decoded"))
        assertFalse("ya no se rechazan las de más de 10 minutos", a.contains("Elige una grabación de hasta 10 minutos"))
        assertTrue(vm.contains("com.zeta.fondo.engine.UserSounds.MAX_SECONDS)"))
        assertTrue(vm.contains("if (decoded.truncated)"))
    }

    @Test fun layerBitmapsAreNotRecycledWhileTheGpuMayUseThem() {
        val load = vm.substringAfter("private fun loadLayers(").substringBefore("\n    }")
        val close = vm.substringAfter("private fun closeLayers(").substringBefore("\n    }")
        assertFalse(load.contains("recycle()"))
        assertFalse(close.contains("recycle()"))
    }

    // ---------------------------------------------------------------- optimización

    @Test fun shaderQueueDropsStaleRequestsAndRecompilesEvictedOnes() {
        val c = file("src/main/java/com/zeta/fondo/engine/ShaderCompiler.kt").readText()
        val req = c.substringAfter("fun request(key: String, features: Set<String>) {").substringBefore("\n    }")
        assertTrue("una variante descartada se vuelve a preparar", req.contains("if (!delivered.remove(key)) return"))
        assertTrue("pedidos viejos fuera", req.contains("while (queue.size > MAX_PENDING)"))
        assertTrue(req.contains("requested.remove(stale)"))
        assertTrue(c.contains("delivered.add(k)"))
    }

    @Test fun memoryIsReleasedWhenAndroidAsks() {
        assertTrue(file("src/main/java/com/zeta/fondo/MainActivity.kt").readText().contains("vm.trimMemory(level)"))
        val trim = fn(vm, "fun trimMemory(level: Int) {")
        assertTrue(trim.contains("ThumbCache.trim()")); assertTrue(trim.contains("HomeThumbs.trim()"))
        assertTrue(trim.contains("UserSounds.trim()"))
        val home = file("src/main/java/com/zeta/fondo/ui/HomeScreen.kt").readText()
        assertTrue(home.contains("GridCells.Adaptive(minSize = 136.dp)"))
        assertTrue(home.contains("HomeThumbs.get(key)"))
    }

    // ---------------------------------------------------------------- Google Play

    @Test fun playStoreReady() {
        val g = file("build.gradle.kts").readText()
        assertTrue("16 KB", g.contains("onnxruntime-android:1.23.2"))
        assertTrue(g.contains("isMinifyEnabled = true")); assertTrue(g.contains("isShrinkResources = true"))
        val rules = file("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keep class ai.onnxruntime.** { *; }"))
        assertTrue(rules.contains("-keep class com.zeta.fondo.** { *; }"))
        assertTrue(rules.contains("-dontobfuscate"))
        val wf = listOf(File("../.github/workflows/build.yml"), File(".github/workflows/build.yml")).firstOrNull { it.exists() }
        if (wf != null) {
            val y = wf.readText()
            assertTrue(y.contains("zipalign\" -c -P 16"))
            assertTrue(y.contains("readelf -lW"))
            // Con «set -euo pipefail», la comprobación nunca puede detener la compilación.
            assertTrue(y.contains("(readelf -lW \"\$so\" 2>/dev/null || true)"))
            assertTrue(y.contains("| head -n 1 || true)\""))
        }
    }

    // ---------------------------------------------------------------- varias imágenes

    @Test fun severalImagesAtOnce() {
        val add = fn(vm, "fun addLayers(uris: List<Uri>) {")
        assertTrue(add.contains("val todo = unique.take(room)"))
        assertTrue("una por una", add.contains("for ((n, uri) in todo.withIndex())"))
        assertTrue("un solo paso de deshacer", add.contains("commitLayers(layers + added)"))
        assertTrue(file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText().contains("PickMultipleVisualMedia("))
        val main = file("src/main/java/com/zeta/fondo/MainActivity.kt").readText()
        assertTrue(main.contains("Intent.ACTION_SEND_MULTIPLE"))
        assertTrue(file("src/main/AndroidManifest.xml").readText().contains("android.intent.action.SEND_MULTIPLE"))
        // Las compartidas pendientes se toman al empezar a abrir: si algo falla, no se cuelan en
        // el siguiente proyecto importado.
        val open = vm.substringAfter("fun openProject(").substringBefore("fun saveProject(")
        val take = open.indexOf("val moreLayers = if (fromImport) afterOpenLayers else emptyList()")
        assertTrue(take >= 0 && take < open.indexOf("if (opening || (!fromImport && busy != null)) return"))
        assertTrue(open.contains("if (moreLayers.isNotEmpty()) addLayers(moreLayers)"))
        // Inicio: miniatura nueva cuando el proyecto cambia (se mira la caché con la clave nueva).
        val home = file("src/main/java/com/zeta/fondo/ui/HomeScreen.kt").readText()
        assertTrue(home.contains("HomeThumbs.get(key)?.let { value = it; return@produceState }"))
        assertFalse(home.contains("if (value != null) return@produceState"))
    }
}
