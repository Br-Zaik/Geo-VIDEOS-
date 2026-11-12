package com.zeta.fondo.model

/**
 * v44.24 · Lista de las piezas de código abierto que van dentro de la app y el archivo con el
 * texto completo de su licencia (en assets/licenses). Es lo que piden MIT, BSD-3 y Apache 2.0:
 * nombre, autor y el texto de la licencia visibles dentro de la app.
 */
object OpenSourceLicenses {
    data class Item(
        val name: String,
        val author: String,
        val license: String,
        /** Para qué la usa Zeta Motion (una línea). */
        val use: String,
        /** Ruta dentro de assets. */
        val file: String
    )

    val items = listOf(
        Item("Real-ESRGAN (realesr-animevideov3)", "Xintao Wang", "BSD-3-Clause",
            "Mejorar imagen · modelo convertido a formato ONNX", "licenses/Real-ESRGAN_BSD-3.txt"),
        Item("ONNX Runtime", "Microsoft", "MIT",
            "Mejorar imagen · motor que ejecuta el modelo", "licenses/onnxruntime_MIT.txt"),
        Item("lbpcascade_animeface", "nagadomi", "MIT",
            "Detectar ojos y caras anime", "licenses/lbpcascade_animeface_MIT.txt"),
        Item("Android Jetpack (AndroidX, Compose, Material)", "Google / The Android Open Source Project", "Apache 2.0",
            "Interfaz de la app", "licenses/Apache-2.0.txt"),
        Item("Kotlin y kotlinx.coroutines", "JetBrains", "Apache 2.0",
            "Lenguaje de la app", "licenses/Apache-2.0.txt")
    )

    /**
     * Une las líneas cortadas de un párrafo para que el texto se acomode al ancho de cualquier
     * pantalla (los archivos vienen cortados a 80 columnas). Los párrafos se separan con una
     * línea en blanco, como en el original.
     */
    fun reflow(text: String): String {
        val paragraphs = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) { paragraphs.add(cur.toString()); cur.setLength(0) }
        }
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) { flush(); continue }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(line)
        }
        flush()
        return paragraphs.joinToString("\n\n")
    }
}
