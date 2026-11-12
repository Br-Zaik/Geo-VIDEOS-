package com.zeta.fondo.diag

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v44 · Registro LOCAL de errores (OpenGL, shaders, MediaCodec, cierres inesperados).
 *
 * - Se escribe solo en la carpeta privada de la app; nunca se envía a ningún servidor.
 * - Tamaño limitado: al pasar de [MAX_BYTES] el archivo actual pasa a «.1» y se empieza otro.
 * - No guarda imágenes, nombres de archivos del usuario ni contenido de proyectos.
 * - El usuario puede compartirlo desde Ajustes › Diagnóstico.
 */
object ZetaLog {
    const val MAX_BYTES = 256 * 1024L
    @Volatile private var file: File? = null
    private val lock = Any()
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Carpeta del registro (dentro de [base], normalmente filesDir). */
    fun init(base: File) {
        val dir = File(base, "diag")
        dir.mkdirs()
        file = File(dir, "registro.txt")
    }

    fun current(): File? = file

    fun info(tag: String, msg: String) = write("I", tag, msg, null)
    fun warn(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun error(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    /** Texto completo (registro anterior + actual), para mostrar o compartir. */
    fun readAll(): String {
        val f = file ?: return ""
        synchronized(lock) {
            val old = File(f.parentFile, f.name + ".1")
            val sb = StringBuilder()
            if (old.exists()) sb.append(old.readText()).append('\n')
            if (f.exists()) sb.append(f.readText())
            return sb.toString()
        }
    }

    fun clear() {
        val f = file ?: return
        synchronized(lock) {
            f.delete()
            File(f.parentFile, f.name + ".1").delete()
        }
    }

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        val f = file ?: return
        val line = buildString {
            synchronized(stamp) { append(stamp.format(Date())) }
            append(' ').append(level).append('/').append(tag).append(": ").append(msg.take(4000))
            if (t != null) {
                append('\n').append(t.javaClass.name).append(": ").append(t.message ?: "")
                for (e in t.stackTrace.take(24)) append("\n    at ").append(e.toString())
                var c = t.cause
                var depth = 0
                while (c != null && depth < 3) {
                    append("\nCausado por ").append(c.javaClass.name).append(": ").append(c.message ?: "")
                    for (e in c.stackTrace.take(8)) append("\n    at ").append(e.toString())
                    c = c.cause; depth++
                }
            }
            append('\n')
        }
        try {
            synchronized(lock) {
                if (f.exists() && f.length() > MAX_BYTES) {
                    val old = File(f.parentFile, f.name + ".1")
                    old.delete()
                    f.renameTo(old)
                }
                f.appendText(line)
            }
        } catch (_: Throwable) {
            // El registro nunca debe provocar otro fallo.
        }
    }

    /**
     * Guarda los cierres inesperados en el registro y luego deja actuar al manejador
     * del sistema (la app se cierra igual que antes; no se oculta el fallo).
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(previous))
    }

    private class CrashHandler(val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            error("Cierre", "Cierre inesperado en el hilo «${t.name}»", e)
            previous?.uncaughtException(t, e)
        }
    }
}
