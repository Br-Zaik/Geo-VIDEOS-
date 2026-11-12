package com.zeta.fondo.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface

/**
 * Contexto OpenGL ES 3 independiente de la pantalla.
 * forVideo = true  -> superficie "grabable" para el codificador de vídeo.
 * forVideo = false -> pbuffer para renderizar a una textura y leer PNG.
 * No llama a eglTerminate para no afectar a la vista previa.
 */
class EglCore(forVideo: Boolean) {

    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private val context: EGLContext

    init {
        if (display == EGL14.EGL_NO_DISPLAY) throw RuntimeException("EGL: no hay pantalla disponible")
        val v = IntArray(2)
        if (!EGL14.eglInitialize(display, v, 0, v, 1)) throw RuntimeException("EGL: no se pudo iniciar")
        config = choose(forVideo, true) ?: choose(forVideo, false)
            ?: throw RuntimeException("EGL: el teléfono no ofrece una configuración compatible")
        val attrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attrs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) throw RuntimeException("EGL: no se pudo crear el contexto OpenGL ES 3")
    }

    private fun choose(forVideo: Boolean, withAlpha: Boolean): EGLConfig? {
        val list = ArrayList<Int>()
        list.addAll(listOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8))
        if (withAlpha) list.addAll(listOf(EGL14.EGL_ALPHA_SIZE, 8))
        list.addAll(listOf(EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR))
        if (forVideo) {
            list.addAll(listOf(EGL_RECORDABLE_ANDROID, 1))
        } else {
            list.addAll(listOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT))
        }
        list.add(EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val ok = EGL14.eglChooseConfig(display, list.toIntArray(), 0, configs, 0, 1, num, 0)
        return if (ok && num[0] > 0) configs[0] else null
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        val s = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE) throw RuntimeException("EGL: no se pudo crear la superficie del vídeo")
        return s
    }

    fun createPbuffer(w: Int, h: Int): EGLSurface {
        val attrs = intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE)
        val s = EGL14.eglCreatePbufferSurface(display, config, attrs, 0)
        if (s == null || s == EGL14.EGL_NO_SURFACE) throw RuntimeException("EGL: no se pudo crear el pbuffer")
        return s
    }

    fun makeCurrent(s: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, s, s, context)) throw RuntimeException("EGL: makeCurrent falló")
    }

    fun swap(s: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, s)

    fun setPresentationTime(s: EGLSurface, ns: Long) {
        EGLExt.eglPresentationTimeANDROID(display, s, ns)
    }

    fun releaseSurface(s: EGLSurface) {
        EGL14.eglDestroySurface(display, s)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
