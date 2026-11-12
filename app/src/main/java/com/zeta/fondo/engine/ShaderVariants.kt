package com.zeta.fondo.engine

/**
 * v41 · Variantes del shader de efectos.
 *
 * En la v40 se compilaba SIEMPRE el shader con los 65 efectos (≈140 KB de GLSL). En un
 * teléfono modesto eso tarda muchos segundos y, como se hacía en el hilo de OpenGL, la
 * imagen se quedaba en negro y Android mostraba «no responde». Ahora cada efecto está
 * entre `#ifdef FX_<ID>` y solo se compila lo que el proyecto usa: sin efectos, el
 * shader es casi tan pequeño como el ligero; con 2-3 efectos, unas 10 veces menor.
 *
 * Código puro (sin Android): se prueba en JVM.
 */
object ShaderVariants {
    /** Clave estable de una combinación de efectos (orden alfabético). */
    fun key(features: Collection<String>): String = features.toSortedSet().joinToString(",")

    /** Fuente del shader con un `#define FX_<ID> 1` por efecto activo, tras la línea #version. */
    fun source(frag: String, features: Collection<String>): String {
        val nl = frag.indexOf('\n')
        val first = if (nl < 0) frag else frag.substring(0, nl)
        val rest = if (nl < 0) "" else frag.substring(nl + 1)
        val sb = StringBuilder(frag.length + features.size * 24)
        sb.append(first).append('\n')
        for (f in features.toSortedSet()) {
            if (f.all { it.isLetterOrDigit() }) sb.append("#define FX_").append(f.uppercase()).append(" 1\n")
        }
        sb.append(rest)
        return sb.toString()
    }

    /** Efectos que el shader sabe omitir (los nombres de sus `#ifdef FX_...`). */
    fun declaredFeatures(frag: String): Set<String> =
        Regex("#ifdef FX_([A-Z0-9]+)").findAll(frag).map { it.groupValues[1].lowercase() }.toSet()
}

/**
 * v41 · Posición de cada miembro del bloque `ZetaParams` según las reglas std140.
 *
 * Con std140 la distribución NO depende de qué miembros use cada variante, así que todas
 * comparten el mismo búfer. Se calcula leyendo la declaración del bloque (antes se
 * preguntaba al driver, que en las variantes no informa de los miembros sin usar).
 */
class Std140Layout private constructor(
    /** nombre → [desplazamiento en bytes, salto entre elementos (arrays) o 0, 1 si es int]. */
    val members: Map<String, IntArray>,
    /** Tamaño total del bloque, múltiplo de 16. */
    val size: Int
) {
    companion object {
        fun parse(frag: String, blockName: String = "ZetaParams"): Std140Layout {
            val start = frag.indexOf("uniform $blockName")
            require(start >= 0) { "El shader no declara el bloque $blockName" }
            val open = frag.indexOf('{', start)
            val close = frag.indexOf("};", open)
            val body = frag.substring(open + 1, close)
            val members = LinkedHashMap<String, IntArray>()
            var offset = 0
            val decl = Regex("^\\s*(float|int|vec2|vec3|vec4)\\s+([^;]+);")
            for (raw in body.lines()) {
                val line = raw.substringBefore("//")
                val m = decl.find(line) ?: continue
                val type = m.groupValues[1]
                val (size, align) = when (type) {
                    "float", "int" -> 4 to 4
                    "vec2" -> 8 to 8
                    "vec3" -> 12 to 16
                    else -> 16 to 16
                }
                val isInt = if (type == "int") 1 else 0
                for (part in m.groupValues[2].split(',')) {
                    val name = part.trim()
                    if (name.isEmpty()) continue
                    val arr = Regex("^(\\w+)\\s*\\[\\s*(\\d+)\\s*]$").find(name)
                    if (arr != null) {
                        val count = arr.groupValues[2].toInt()
                        val stride = 16                       // std140: cada elemento alineado a vec4
                        offset = roundUp(offset, 16)
                        val info = intArrayOf(offset, stride, isInt)
                        members[arr.groupValues[1]] = info
                        members[arr.groupValues[1] + "[0]"] = info
                        offset += stride * count
                    } else {
                        offset = roundUp(offset, align)
                        members[name] = intArrayOf(offset, 0, isInt)
                        offset += size
                    }
                }
            }
            return Std140Layout(members, roundUp(offset, 16).coerceAtLeast(16))
        }

        private fun roundUp(v: Int, a: Int): Int = (v + a - 1) / a * a
    }
}
