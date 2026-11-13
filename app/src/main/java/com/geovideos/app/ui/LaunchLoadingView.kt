package com.geovideos.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import kotlin.math.exp

/**
 * Carga al abrir la app, como YouTube: fondo oscuro, play blanco y una barra roja que avanza.
 * Tiene el mismo dibujo y la misma posición que la pantalla de arranque del sistema
 * (res/drawable/splash_loading.xml), así el paso de una a otra no se nota.
 */
internal class LaunchLoadingView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCCCCC.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF0033.toInt() }
    private val playPath = Path()
    private val startedAt = SystemClock.uptimeMillis()
    private var finishing = false
    private var finishFrom = 0f
    private var finishStartedAt = 0L

    init {
        setBackgroundColor(0xFF0F0F0F.toInt())
        // Mientras carga, los toques no pasan a la pantalla de abajo.
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun progressAt(now: Long): Float {
        // Avanza rápido al principio y cada vez más despacio (nunca llega sola al final).
        val elapsed: Float = (now - startedAt).toFloat()
        val waiting: Float = 0.2f + 0.7f * (1f - exp(-elapsed / 1400f))
        if (!finishing) return waiting
        val step = ((now - finishStartedAt) / FILL_MS.toFloat()).coerceIn(0f, 1f)
        return finishFrom + (1f - finishFrom) * step
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dp = density
        val cx = width / 2f
        val cy = height / 2f

        playPath.reset()
        playPath.moveTo(cx - 85f * dp, cy - 7f * dp)
        playPath.lineTo(cx - 73f * dp, cy)
        playPath.lineTo(cx - 85f * dp, cy + 7f * dp)
        playPath.close()
        canvas.drawPath(playPath, playPaint)

        val left = cx - 65f * dp
        val right = cx + 85f * dp
        val top = cy - 1.5f * dp
        val bottom = cy + 1.5f * dp
        canvas.drawRect(left, top, right, bottom, trackPaint)
        val filled: Float = (right - left) * progressAt(now)
        canvas.drawRect(left, top, left + filled, bottom, redPaint)

        if (!finishing || now - finishStartedAt < FILL_MS) postInvalidateOnAnimation()
    }

    /** Completa la barra, se desvanece suave y se quita sola. */
    fun finish(onDone: () -> Unit) {
        if (finishing) return
        val now = SystemClock.uptimeMillis()
        finishFrom = progressAt(now)
        finishStartedAt = now
        finishing = true
        invalidate()
        animate()
            .alpha(0f)
            .setStartDelay(FILL_MS)
            .setDuration(FADE_MS)
            .withEndAction {
                (parent as? ViewGroup)?.removeView(this)
                onDone()
            }
            .start()
    }

    private companion object {
        const val FILL_MS = 180L
        const val FADE_MS = 220L
    }
}
