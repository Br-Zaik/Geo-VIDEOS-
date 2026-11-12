package com.zeta.fondo.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * v44.28.1 · Nunca más «Platform declaration clash»: una propiedad «x» (aunque su set sea
 * privado) ya crea el método setX/getX en la JVM; si además se declara fun setX(...) con el
 * mismo tipo, GitHub no compila. Solo quedan los casos antiguos que compilan (privados sin
 * accesores o con otro tipo).
 */
class V44281Test {
    private fun dir(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun noSetterNameClashes() {
        val known = setOf(
            "ImportedAudioPreview.kt:setForeground", "RenderHub.kt:setFrameRate", "RenderHub.kt:setLoopSec",
            "RenderHub.kt:setPlaying", "RenderHub.kt:setPausedT", "RenderHub.kt:isPlaying", "SoundClips.kt:isSound")
        val found = HashSet<String>()
        dir("src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { f ->
            val s = f.readText()
            // Solo propiedades de clase u objeto (4 espacios o menos de sangría), no variables locales.
            val props = Regex("""(?m)^ {0,4}(?:(?:private|internal|public|protected|override|lateinit|@Volatile|open)\s+)*(?:var|val)\s+([a-zA-Z_]\w*)""")
                .findAll(s).map { it.groupValues[1] }.toSet()
            Regex("""\bfun\s+(set|get|is)([A-Z]\w*)\s*\(""").findAll(s).forEach { m ->
                val name = m.groupValues[2].replaceFirstChar { it.lowercase() }
                val names = if (m.groupValues[1] == "is") setOf(name, "is" + m.groupValues[2]) else setOf(name)
                if (names.any { it in props }) found += "${f.name}:${m.groupValues[1]}${m.groupValues[2]}"
            }
        }
        assertEquals(emptySet<String>(), found - known)
    }
}
