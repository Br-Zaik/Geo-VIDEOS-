package com.zeta.fondo.engine

import android.opengl.GLES30

/**
 * v44 · Tiempo de GPU por cuadro con GL_EXT_disjoint_timer_query, sin bloquear.
 *
 * Se usan [RING] consultas en anillo: la del cuadro N se lee varios cuadros después y solo
 * si GL_QUERY_RESULT_AVAILABLE ya es cierto, así nunca se espera a la GPU. Si el driver
 * informa de un «disjoint» (cambio de frecuencia, otra app…) ese valor se descarta.
 * Si la extensión no existe, [available] es false y no se hace nada.
 */
class GpuTimer {
    var available = false; private set
    private val ids = IntArray(RING)
    private val pending = BooleanArray(RING)
    private var index = 0
    private var open = false

    /** Llamar con el contexto actual. */
    fun init() {
        release()
        val ext = try { GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: "" } catch (_: Throwable) { "" }
        if (!ext.contains("GL_EXT_disjoint_timer_query")) { available = false; return }
        try {
            GLES30.glGenQueries(RING, ids, 0)
            available = ids.all { it != 0 }
            clearErrors()
        } catch (_: Throwable) { available = false }
        pending.fill(false)
        index = 0
    }

    fun begin() {
        if (!available || open || pending[index]) return
        GLES30.glBeginQuery(GL_TIME_ELAPSED_EXT, ids[index])
        if (GLES30.glGetError() != GLES30.GL_NO_ERROR) { available = false; return }
        open = true
    }

    fun end() {
        if (!open) return
        GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
        pending[index] = true
        index = (index + 1) % RING
        open = false
    }

    /** Devuelve los ms de las consultas ya terminadas (puede no devolver ninguno). */
    fun poll(out: (Float) -> Unit) {
        if (!available) return
        val tmp = IntArray(1)
        val disjoint = IntArray(1)
        GLES30.glGetIntegerv(GL_GPU_DISJOINT_EXT, disjoint, 0)
        for (k in 0 until RING) {
            if (!pending[k]) continue
            GLES30.glGetQueryObjectuiv(ids[k], GLES30.GL_QUERY_RESULT_AVAILABLE, tmp, 0)
            if (tmp[0] == 0) continue
            GLES30.glGetQueryObjectuiv(ids[k], GLES30.GL_QUERY_RESULT, tmp, 0)
            pending[k] = false
            if (disjoint[0] != 0) continue
            // Resultado en nanosegundos (32 bits sin signo: hasta ~4 s, de sobra para un cuadro).
            val ns = tmp[0].toLong() and 0xffffffffL
            out(ns / 1e6f)
        }
    }

    fun release() {
        if (available) try { GLES30.glDeleteQueries(RING, ids, 0) } catch (_: Throwable) { }
        available = false
        open = false
        pending.fill(false)
    }

    private fun clearErrors() { var g = 0; while (GLES30.glGetError() != GLES30.GL_NO_ERROR && g++ < 16) { } }

    companion object {
        const val RING = 4
        const val GL_TIME_ELAPSED_EXT = 0x88BF
        const val GL_GPU_DISJOINT_EXT = 0x8FBB
    }
}
