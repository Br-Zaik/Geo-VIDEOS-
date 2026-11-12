package com.zeta.fondo.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v38: el shader ligero declara todo lo que usa FondoGl.drawLite y es realmente ligero. */
class V38ShaderTest {
    private fun read(name: String): String = listOf(File("src/main/assets/shaders/$name"), File("app/src/main/assets/shaders/$name"))
        .first { it.isFile }.readText()

    @Test fun liteShaderDeclaresEveryUniformTheRendererSets() {
        val lite = read("lite.frag")
        for (u in listOf("uImage", "uMasks", "uViewOffset", "uViewScale", "uFlipY", "uShowMask", "uActiveMask", "uMaskAlpha")) {
            assertTrue("lite.frag no declara $u", Regex("uniform\\s+[\\w\\s]+\\b$u\\b").containsMatchIn(lite))
        }
        assertTrue(lite.startsWith("#version 300 es"))
    }

    @Test fun liteShaderIsSmallAndUsesTheSameViewMathAsTheFullShader() {
        val lite = read("lite.frag")
        val full = read("fondo.frag")
        assertTrue("el shader ligero debe ser corto", lite.lines().size < 60)
        assertTrue(full.lines().size > 1000)
        // Mismo encuadre: offset + s * escala, con origen arriba-izquierda.
        val view = "uViewOffset + s * uViewScale"
        assertTrue(lite.contains(view) && full.contains(view))
        assertTrue(lite.contains("mix(1.0 - vScreen.y, vScreen.y, uFlipY)") && full.contains("mix(1.0 - vScreen.y, vScreen.y, uFlipY)"))
    }

    @Test fun newEffectFunctionsArePeriodic() {
        val full = read("fondo.frag")
        // Las salpicaduras y el pétalo usan fract/cos de uT con ciclos enteros (bucle sin saltos).
        assertTrue(full.contains("float rainSplash(vec2 P, float cyc)"))
        assertTrue(full.contains("fract(uT * cyc + h.x)"))
        assertTrue(full.contains("float flip = cos(TAU * (uT * (1.0 + floor(h.y * 2.0)) + h.x));"))
    }
}
