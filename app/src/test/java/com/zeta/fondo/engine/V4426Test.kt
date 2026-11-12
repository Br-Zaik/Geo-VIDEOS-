package com.zeta.fondo.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v44.26 · Mínimo Android 10: sin los caminos de Android 8 y 9 (imágenes, Galería, permisos).
 * Las protecciones de gama baja (poca memoria) se mantienen: dependen de la RAM, no de la edad.
 */
class V4426Test {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun main() = file("src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }

    @Test fun minimumIsAndroid10() {
        val g = file("build.gradle.kts").readText()
        assertTrue(g.contains("minSdk = 29"))
        // La versión sube con cada entrega (v44.27 = 74).
        assertTrue(Regex("versionCode = (\\d+)").find(g)!!.groupValues[1].toInt() >= 73)
    }

    @Test fun noChecksForAndroidOlderThan10() {
        val old = Regex("""SDK_INT\s*(<|>=)\s*(2[0-9])\b""")
        for (f in main()) {
            val hit = old.find(f.readText())
            assertTrue("${f.name}: ${hit?.value}", hit == null)
        }
    }

    @Test fun galleryNeedsNoStoragePermission() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        val gallery = file("src/main/java/com/zeta/fondo/export/Gallery.kt").readText()
        assertFalse(gallery.contains("getExternalStoragePublicDirectory"))
        assertTrue(gallery.contains("MediaStore.MediaColumns.RELATIVE_PATH"))
        val panels = file("src/main/java/com/zeta/fondo/ui/Panels.kt").readText()
        assertFalse(panels.contains("WRITE_EXTERNAL_STORAGE"))
    }

    @Test fun imagesAlwaysUseTheExactSizeDecoder() {
        val bu = file("src/main/java/com/zeta/fondo/engine/BitmapUtil.kt").readText()
        assertTrue(bu.contains("fun decodePreview(file: File, maxSide: Int): Bitmap = decodeAtMost(file, maxSide)"))
        assertFalse(bu.contains("Build.VERSION"))
        // El respaldo (archivo raro para ImageDecoder) sigue existiendo.
        assertTrue(bu.substringAfter("fun decodeAtMost(").contains("BitmapFactory.decodeFile(file.absolutePath, o)"))
    }

    @Test fun lowMemoryProtectionsStay() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("val lowResourceDevice: Boolean by lazy"))
        assertTrue(vm.contains("if (lowResourceDevice) 2560 else 4096"))
        assertTrue(vm.contains("ImageLayers.maxLayers(lowResourceDevice)"))
    }
}
