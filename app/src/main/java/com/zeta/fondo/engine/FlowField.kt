package com.zeta.fondo.engine

import android.util.Half
import com.zeta.fondo.model.Arrow
import com.zeta.fondo.model.PointN
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Convierte flechas y anclas en una cuadrícula suave de vectores (textura RG16F).
 * Cada celda mezcla las flechas cercanas (más peso cuanto más cerca) y un
 * "fondo quieto" que hace que el movimiento se apague lejos de las flechas.
 */
object FlowField {

    class Data(val w: Int, val h: Int, val halfs: ShortArray)

    private const val LONG = 128
    private const val EPS2 = 0.0004f

    fun compute(arrows: List<Arrow>, anchors: List<PointN>, aspect: Float, reach: Float): Data {
        val gw: Int
        val gh: Int
        if (aspect >= 1f) {
            gw = LONG
            gh = max(8, (LONG / aspect).roundToInt())
        } else {
            gh = LONG
            gw = max(8, (LONG * aspect).roundToInt())
        }
        val out = ShortArray(gw * gh * 2)
        if (arrows.isEmpty()) return Data(gw, gh, out)

        val n = arrows.size
        val ax0 = FloatArray(n) { arrows[it].x0 * aspect }
        val ay0 = FloatArray(n) { arrows[it].y0 }
        val ax1 = FloatArray(n) { arrows[it].x1 * aspect }
        val ay1 = FloatArray(n) { arrows[it].y1 }
        val vx = FloatArray(n) { arrows[it].x1 - arrows[it].x0 }
        val vy = FloatArray(n) { arrows[it].y1 - arrows[it].y0 }
        val m = anchors.size
        val px = FloatArray(m) { anchors[it].x * aspect }
        val py = FloatArray(m) { anchors[it].y }
        val r = max(0.02f, reach)
        val bg = 1f / (r * r)

        var idx = 0
        for (j in 0 until gh) {
            val cy = (j + 0.5f) / gh
            for (i in 0 until gw) {
                val cx = (i + 0.5f) / gw * aspect
                var sw = bg
                var sx = 0f
                var sy = 0f
                for (k in 0 until n) {
                    val d2 = segDist2(cx, cy, ax0[k], ay0[k], ax1[k], ay1[k])
                    val w = 1f / (d2 + EPS2)
                    sw += w
                    sx += w * vx[k]
                    sy += w * vy[k]
                }
                for (k in 0 until m) {
                    val dx = cx - px[k]
                    val dy = cy - py[k]
                    sw += 2f / (dx * dx + dy * dy + EPS2)
                }
                out[idx] = Half.toHalf(sx / sw)
                out[idx + 1] = Half.toHalf(sy / sw)
                idx += 2
            }
        }
        return Data(gw, gh, out)
    }

    private fun segDist2(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val l2 = dx * dx + dy * dy
        var t = if (l2 > 1e-9f) ((x - x0) * dx + (y - y0) * dy) / l2 else 0f
        if (t < 0f) t = 0f
        if (t > 1f) t = 1f
        val qx = x0 + t * dx - x
        val qy = y0 + t * dy - y
        return qx * qx + qy * qy
    }
}
