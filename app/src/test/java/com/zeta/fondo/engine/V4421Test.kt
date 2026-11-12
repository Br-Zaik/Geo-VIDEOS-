package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.21 · Libro de efectos: fuego simulado + hojas/pétalos pintados (assets propios). */
class V4421Test {
    private fun root(): File = listOf(File("."), File("app")).first { File(it, "src/main/assets").isDirectory }
    private fun frag() = File(root(), "src/main/assets/shaders/fondo.frag").readText()

    @Test fun elLibroExisteYEsLigero() {
        val f = File(root(), "src/main/assets/${FxBook.PATH}")
        assertTrue("falta ${FxBook.PATH}", f.isFile)
        assertTrue("pesa ${f.length()} bytes", f.length() in 50_000..700_000)
        val head = f.readBytes().copyOfRange(0, 16)
        assertTrue("debe ser WebP", String(head, 0, 4) == "RIFF" && String(head, 8, 4) == "WEBP")
        assertTrue("VP8L = sin pérdida (los datos no se alteran)", String(head, 12, 4) == "VP8L")
    }

    @Test fun soloLoCarganLosEfectosQueLoUsan() {
        assertTrue(FxBook.needed(setOf("flame")))
        assertTrue(FxBook.needed(setOf("cam", "leaves")))
        assertFalse(FxBook.needed(setOf("cam", "rain", "snow")))
        val ids = Effects.all.map { it.id }
        for (u in FxBook.users) assertTrue("$u no es un efecto", u in ids)
    }

    @Test fun shaderUsaElLibroConVueltaProcedural() {
        val s = frag()
        assertTrue(s.contains("uniform sampler2D uFxBook;"))
        assertTrue(s.contains("uniform float uFxReady;"))
        assertTrue(s.contains("const vec2 FXB_SIZE = vec2(1024.0, 896.0);"))
        // cada uso tiene su versión procedural si el libro no está
        assertTrue(s.contains("if (uFxReady > 0.5) {\n        // v44.21 · FUEGO SIMULADO"))
        assertTrue(s.contains("vec2 fq = vec2((P.x - lean + curl) * 7.0 / sc"))
        // el libro se lee con nivel fijo dentro de bucles (sin derivadas implícitas)
        val helpers = s.substring(s.indexOf("vec2 fxFireCell("), s.indexOf("// v44.20 · pétalos que cruzan"))
        assertFalse(Regex("\\btexture\\(uFxBook").containsMatchIn(helpers))
        assertFalse(Regex("\\b(float|vec[234])\\s+half\\b").containsMatchIn(s))
    }

    @Test fun generadoresIncluidos() {
        val tools = File(root().canonicalFile.parentFile, "tools/fxgen")
        for (n in listOf("fire_sim.py", "sprites.py", "pack.py")) assertTrue("falta tools/fxgen/$n", File(tools, n).isFile)
    }
}
