package com.zeta.fondo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()

private val GlyphRain = Color(0xFF8FD3FF)
private val GlyphWater = Color(0xFF5FB8FF)
private val GlyphSakura = Color(0xFFFFA8C8)
private val GlyphLeaf = Color(0xFF7BD88F)
private val GlyphFire = Color(0xFFFF8A3D)
private val GlyphGold = Color(0xFFFFD66B)
private val GlyphEnergy = Color(0xFF7EC8FF)
private val GlyphMagic = Color(0xFFB794FF)
private val GlyphCloud = Color(0xFFE9ECF5)
private val GlyphGrey = Color(0xFFB8BCC8)

/** Tipo de dibujo para cada efecto o sonido. */
private fun glyphKind(id: String): String = when (id) {
    "rain", "rain_soft" -> "rain"
    "rain_heavy" -> "rainHeavy"
    "snow" -> "snow"
    "sakura" -> "petals"
    "stars" -> "stars"
    "flies" -> "flies"
    "fog", "smoke", "blackaura", "reveal" -> "fog"
    "blast" -> "impact"
    "slash" -> "bolt"
    "blood" -> "ink"
    "rays" -> "rays"
    "sunpath" -> "sunpath"
    "sky", "drift" -> "cloud"
    "clouds3" -> "clouds3"
    "water", "ripple", "river" -> "waves"
    "shore", "waves" -> "shore"
    "glint", "sparkle" -> "glint"
    "sway", "natural" -> "tree"
    "leaves" -> "leaves"
    "cloth", "mesh" -> "cloth"
    "heat" -> "heat"
    "flame", "fire" -> "flame"
    "embers" -> "embers"
    "energy" -> "aura"
    "pulse", "shock" -> "rings"
    "runes" -> "runes"
    "speed" -> "speed"
    "focus" -> "focus"
    "hatch" -> "hatch"
    "cam" -> "camera"
    "impact" -> "impact"
    "comet" -> "comet"
    "shards" -> "shards"
    "dispersion" -> "dispersion"
    "ash", "dust", "sand" -> "dust"
    "bubbles" -> "bubbles"
    "electric", "bolt", "lightning" -> "bolt"
    "thunder" -> "thunder"
    "flash", "cine" -> "flash"
    "tone" -> "tone"
    "grain" -> "grain"
    "ink", "style" -> "ink"
    "eyes" -> "eye"
    "blink" -> "blink"
    "gust", "wind", "gale" -> "gust"
    "tornado" -> "tornado"
    "appear" -> "appear"
    "elements", "parallax" -> "layers"
    "birds" -> "birds"
    "crickets" -> "crickets"
    "waterfall" -> "waterfall"
    "reflect" -> "waves"
    "bokeh" -> "flies"
    "lightleak" -> "rays"
    "inkdrip", "dramatic" -> "ink"
    "city" -> "city"
    else -> "sparkle"
}

/** Icono animado. [time] se lee al dibujar: solo se redibuja, no se recompone. */
@Composable
fun EffectGlyph(id: String, time: () -> Float, active: Boolean, modifier: Modifier = Modifier) {
    val kind = glyphKind(id)
    Canvas(modifier) {
        val t = if (active) time() else time() * 0.35f
        drawGlyph(kind, t, if (active) 1f else 0.78f)
    }
}

private fun DrawScope.drawGlyph(kind: String, t: Float, alpha: Float) {
    val w = size.width
    val h = size.height
    val s = size.minDimension
    val c = Offset(w / 2f, h / 2f)
    val sw = s * 0.07f
    fun col(base: Color) = base.copy(alpha = base.alpha * alpha)
    fun wave(x: Float) = sin(x * TAU)

    when (kind) {
        "rain", "rainHeavy" -> {
            val n = if (kind == "rainHeavy") 7 else 5
            for (i in 0 until n) {
                val x = w * (0.12f + 0.8f * i / (n - 1).toFloat())
                val y = ((t * (1.6f + i % 3 * 0.3f) + i * 0.37f) % 1f) * h * 1.3f - h * 0.2f
                drawLine(col(GlyphRain), Offset(x + s * 0.06f, y), Offset(x - s * 0.04f, y + s * 0.26f),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            }
        }
        "snow" -> for (i in 0 until 6) {
            val x = w * (0.15f + 0.7f * ((i * 0.41f) % 1f)) + sin((t * 2f + i * 0.3f) * TAU) * s * 0.06f
            val y = ((t * 0.8f + i * 0.23f) % 1f) * h
            drawCircle(col(Color.White), s * (0.05f + (i % 2) * 0.025f), Offset(x, y))
        }
        "petals" -> for (i in 0 until 4) {
            val x = ((t * 0.7f + i * 0.27f) % 1f) * w
            val y = ((t * 0.9f + i * 0.31f) % 1f) * h
            rotate((t * 360f * (1 + i % 2)) + i * 40f, Offset(x, y)) {
                drawOval(col(GlyphSakura), Offset(x - s * 0.1f, y - s * 0.05f), Size(s * 0.2f, s * 0.1f))
            }
        }
        "stars" -> for (i in 0 until 4) {
            val p = Offset(w * listOf(0.25f, 0.7f, 0.45f, 0.8f)[i], h * listOf(0.3f, 0.25f, 0.7f, 0.65f)[i])
            val k = 0.5f + 0.5f * sin((t * 2f + i * 0.25f) * TAU)
            val r = s * (0.07f + 0.08f * k)
            drawLine(col(GlyphGold), p - Offset(r, 0f), p + Offset(r, 0f), strokeWidth = sw * 0.5f, cap = StrokeCap.Round)
            drawLine(col(GlyphGold), p - Offset(0f, r), p + Offset(0f, r), strokeWidth = sw * 0.5f, cap = StrokeCap.Round)
            drawCircle(col(Color.White), s * 0.035f, p)
        }
        "flies" -> for (i in 0 until 4) {
            val p = Offset(w * (0.5f + 0.32f * sin((t + i * 0.25f) * TAU)), h * (0.5f + 0.3f * sin((t * 2f + i * 0.4f) * TAU)))
            val k = 0.4f + 0.6f * (0.5f + 0.5f * sin((t * 3f + i * 0.3f) * TAU))
            drawCircle(col(Color(0xFFD8FF7A)).copy(alpha = 0.3f * k * alpha), s * 0.12f, p)
            drawCircle(col(Color(0xFFEFFFB0)).copy(alpha = k * alpha), s * 0.04f, p)
        }
        "fog" -> for (i in 0 until 3) {
            val x = ((t * (0.3f + i * 0.15f) + i * 0.3f) % 1f) * w * 1.4f - w * 0.3f
            val y = h * (0.3f + i * 0.22f)
            drawRoundRect(col(GlyphCloud).copy(alpha = 0.55f * alpha), Offset(x, y - s * 0.07f), Size(w * 0.55f, s * 0.14f),
                CornerRadius(s * 0.07f))
        }
        "rays" -> {
            drawCircle(col(GlyphGold), s * 0.12f, Offset(w * 0.5f, h * 0.1f))
            for (i in 0 until 5) {
                val a = (-60f + i * 30f + sin(t * TAU) * 6f) * PI.toFloat() / 180f
                val end = Offset(w * 0.5f + sin(a) * s * 0.85f, h * 0.1f + cos(a) * s * 0.85f)
                drawLine(col(GlyphGold).copy(alpha = 0.55f * alpha), Offset(w * 0.5f, h * 0.1f), end,
                    strokeWidth = sw * (1.2f + 0.6f * sin((t + i * 0.2f) * TAU)), cap = StrokeCap.Round)
            }
        }
        "sunpath" -> {
            drawCircle(col(GlyphGold), s * 0.13f, Offset(w * 0.5f, h * 0.2f))
            drawLine(col(GlyphWater), Offset(0f, h * 0.42f), Offset(w, h * 0.42f), strokeWidth = sw * 0.5f)
            for (i in 0 until 7) {
                val y = h * (0.5f + i * 0.07f)
                val k = 0.5f + 0.5f * sin((t * 3f + i * 0.37f) * TAU)
                val half = s * (0.05f + i * 0.03f) * (0.5f + k)
                drawLine(col(Color.White).copy(alpha = k * alpha), Offset(w / 2f - half, y), Offset(w / 2f + half, y),
                    strokeWidth = sw * 0.6f, cap = StrokeCap.Round)
            }
        }
        "cloud", "clouds3" -> {
            fun cloud(x: Float, y: Float, k: Float, color: Color) {
                drawCircle(color, s * 0.16f * k, Offset(x - s * 0.14f * k, y))
                drawCircle(color, s * 0.22f * k, Offset(x, y - s * 0.07f * k))
                drawCircle(color, s * 0.15f * k, Offset(x + s * 0.17f * k, y + s * 0.01f * k))
                drawRoundRect(color, Offset(x - s * 0.3f * k, y), Size(s * 0.6f * k, s * 0.14f * k), CornerRadius(s * 0.07f * k))
            }
            if (kind == "clouds3") {
                cloud(((t * 0.5f) % 1f) * w * 1.4f - w * 0.2f, h * 0.35f, 0.6f, col(GlyphGrey))
                cloud(((t + 0.4f) % 1f) * w * 1.6f - w * 0.3f, h * 0.65f, 0.95f, col(GlyphCloud))
            } else cloud(w * 0.5f + sin(t * TAU) * s * 0.12f, h * 0.55f, 1f, col(GlyphCloud))
        }
        "waves", "shore" -> for (row in 0 until 3) {
            val path = Path()
            val y0 = h * (0.3f + row * 0.22f)
            for (i in 0..20) {
                val x = w * i / 20f
                val y = y0 + sin((i / 20f * 1.5f + t * (1f + row * 0.4f)) * TAU) * s * 0.06f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, if (kind == "shore" && row == 2) col(Color.White) else col(GlyphWater), style = Stroke(sw, cap = StrokeCap.Round))
        }
        "glint", "sparkle" -> for (i in 0 until 3) {
            val p = Offset(w * listOf(0.3f, 0.65f, 0.5f)[i], h * listOf(0.35f, 0.45f, 0.72f)[i])
            val k = 0.5f + 0.5f * sin((t * 2f + i * 0.33f) * TAU)
            val r = s * (0.06f + 0.14f * k)
            drawLine(col(Color.White), p - Offset(r, 0f), p + Offset(r, 0f), strokeWidth = sw * 0.55f, cap = StrokeCap.Round)
            drawLine(col(Color.White), p - Offset(0f, r * 0.6f), p + Offset(0f, r * 0.6f), strokeWidth = sw * 0.55f, cap = StrokeCap.Round)
        }
        "tree" -> {
            drawLine(col(Color(0xFF9C6B45)), Offset(w / 2f, h * 0.95f), Offset(w / 2f, h * 0.55f), strokeWidth = sw * 1.4f)
            val sway = sin(t * TAU) * s * 0.06f
            drawCircle(col(GlyphLeaf), s * 0.28f, Offset(w / 2f + sway, h * 0.38f))
            drawCircle(col(GlyphLeaf).copy(alpha = 0.8f * alpha), s * 0.18f, Offset(w / 2f - s * 0.2f + sway * 0.7f, h * 0.5f))
        }
        "leaves" -> for (i in 0 until 3) {
            val x = ((t * 0.8f + i * 0.33f) % 1f) * w * 1.2f - w * 0.1f
            val y = h * (0.3f + i * 0.2f) + sin((t * 2f + i * 0.3f) * TAU) * s * 0.08f
            rotate(t * 360f + i * 90f, Offset(x, y)) {
                drawOval(col(if (i == 1) Color(0xFFD9B26A) else GlyphLeaf), Offset(x - s * 0.12f, y - s * 0.05f), Size(s * 0.24f, s * 0.1f))
            }
        }
        "cloth" -> for (row in 0 until 3) {
            val path = Path()
            for (i in 0..16) {
                val y = h * (0.15f + 0.7f * i / 16f)
                val x = w * (0.3f + row * 0.2f) + sin((i / 16f + t * 1.5f + row * 0.2f) * TAU) * s * 0.07f * (i / 16f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, col(GlyphMagic), style = Stroke(sw, cap = StrokeCap.Round))
        }
        "heat" -> for (row in 0 until 3) {
            val path = Path()
            for (i in 0..16) {
                val y = h * (0.9f - 0.8f * i / 16f)
                val x = w * (0.3f + row * 0.2f) + sin((i / 8f - t * 2f) * TAU) * s * 0.05f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, col(GlyphFire).copy(alpha = 0.8f * alpha), style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
        }
        "flame" -> {
            val k = 0.9f + 0.1f * sin(t * TAU * 3f)
            val path = Path().apply {
                moveTo(w / 2f, h * (0.1f + (1f - k) * 0.5f))
                cubicTo(w * 0.85f, h * 0.45f, w * 0.8f, h * 0.9f, w / 2f, h * 0.9f)
                cubicTo(w * 0.2f, h * 0.9f, w * 0.15f, h * 0.5f, w / 2f, h * (0.1f + (1f - k) * 0.5f))
            }
            drawPath(path, Brush.verticalGradient(listOf(col(GlyphGold), col(GlyphFire), col(Color(0xFFE0442B)))))
            drawCircle(col(Color(0xFFFFF3C4)), s * 0.1f, Offset(w / 2f, h * 0.7f))
        }
        "embers" -> for (i in 0 until 6) {
            val x = w * (0.2f + 0.6f * ((i * 0.37f) % 1f)) + sin((t * 2f + i * 0.2f) * TAU) * s * 0.05f
            val y = h - ((t * 1.2f + i * 0.19f) % 1f) * h
            drawCircle(col(if (i % 2 == 0) GlyphFire else GlyphGold), s * 0.045f, Offset(x, y))
        }
        "aura" -> {
            for (i in 0 until 8) {
                val a = i * TAU / 8f + t * TAU * 0.25f
                val r1 = s * 0.18f
                val r2 = s * (0.32f + 0.1f * sin((t * 3f + i * 0.2f) * TAU))
                drawLine(col(GlyphEnergy), c + Offset(cos(a) * r1, sin(a) * r1), c + Offset(cos(a) * r2, sin(a) * r2),
                    strokeWidth = sw * 0.7f, cap = StrokeCap.Round)
            }
            drawCircle(col(GlyphEnergy).copy(alpha = 0.35f * alpha), s * 0.2f, c)
        }
        "rings" -> for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            drawCircle(col(GlyphEnergy).copy(alpha = (1f - p) * alpha), s * (0.08f + 0.4f * p), c, style = Stroke(sw * 0.7f))
        }
        "runes" -> {
            drawCircle(col(GlyphMagic), s * 0.38f, c, style = Stroke(sw * 0.6f))
            drawCircle(col(GlyphMagic), s * 0.26f, c, style = Stroke(sw * 0.4f))
            rotate(t * 360f, c) {
                for (i in 0 until 6) {
                    val a = i * TAU / 6f
                    drawCircle(col(GlyphMagic), s * 0.04f, c + Offset(cos(a) * s * 0.32f, sin(a) * s * 0.32f))
                }
            }
        }
        "speed" -> for (i in 0 until 6) {
            val y = h * (0.12f + i * 0.15f)
            val off = ((t * 2f + i * 0.37f) % 1f)
            drawLine(col(Color.White), Offset(w * (off - 0.4f), y), Offset(w * off + w * 0.1f, y), strokeWidth = sw * 0.5f, cap = StrokeCap.Round)
        }
        "focus" -> for (i in 0 until 12) {
            val a = i * TAU / 12f
            val k = 0.55f + 0.1f * sin((t * 2f + i * 0.13f) * TAU)
            drawLine(col(Color.White), c + Offset(cos(a) * s * 0.48f, sin(a) * s * 0.48f),
                c + Offset(cos(a) * s * 0.48f * k, sin(a) * s * 0.48f * k), strokeWidth = sw * 0.45f, cap = StrokeCap.Round)
        }
        "hatch" -> for (i in -3..6) {
            val x = w * (i * 0.16f + (t * 0.16f) % 0.16f)
            drawLine(col(Color.White).copy(alpha = 0.7f * alpha), Offset(x, h), Offset(x + h * 0.6f, 0f), strokeWidth = sw * 0.4f)
        }
        "camera", "impact" -> {
            val shake = if (kind == "impact") {
                val e = ((1f - (t * 2f % 1f)).let { it * it * it })
                Offset(sin(t * TAU * 12f) * s * 0.05f * e, cos(t * TAU * 9f) * s * 0.04f * e)
            } else Offset.Zero
            val k = if (kind == "impact") 1f else 0.85f + 0.15f * (0.5f + 0.5f * sin(t * TAU))
            val fw = w * 0.8f * k
            val fh = h * 0.6f * k
            drawRoundRect(col(Color.White), Offset(c.x - fw / 2f, c.y - fh / 2f) + shake, Size(fw, fh),
                CornerRadius(s * 0.06f), style = Stroke(sw * 0.7f))
            drawCircle(col(if (kind == "impact") GlyphFire else Color.White), s * 0.08f, c + shake)
        }
        "comet" -> {
            val p = (t % 1f)
            val head = Offset(w * (1.1f - p * 1.3f), h * (-0.1f + p * 1.2f))
            drawLine(Brush.linearGradient(listOf(col(GlyphEnergy).copy(alpha = 0f), col(GlyphEnergy)),
                head + Offset(s * 0.45f, -s * 0.45f), head), head + Offset(s * 0.45f, -s * 0.45f), head,
                strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(col(Color.White), s * 0.06f, head)
        }
        "shards", "dispersion" -> for (i in 0 until 5) {
            val x = w * (0.2f + 0.6f * ((i * 0.43f) % 1f)) + (if (kind == "dispersion") ((t + i * 0.2f) % 1f) * s * 0.3f else 0f)
            val y = if (kind == "dispersion") h * (0.25f + i * 0.13f) else ((t + i * 0.21f) % 1f) * h
            rotate(45f + t * 180f, Offset(x, y)) {
                drawRect(col(if (kind == "shards") GlyphEnergy else GlyphMagic), Offset(x - s * 0.05f, y - s * 0.05f), Size(s * 0.1f, s * 0.1f))
            }
        }
        "dust" -> for (i in 0 until 8) {
            val x = ((t * 0.5f + i * 0.13f) % 1f) * w
            val y = h * (0.2f + 0.6f * ((i * 0.61f) % 1f)) + sin((t + i * 0.2f) * TAU) * s * 0.05f
            drawCircle(col(Color(0xFFE8D9B5)), s * 0.03f, Offset(x, y))
        }
        "bubbles" -> for (i in 0 until 4) {
            val x = w * (0.2f + i * 0.2f) + sin((t * 2f + i * 0.3f) * TAU) * s * 0.04f
            val y = h - ((t + i * 0.27f) % 1f) * h
            drawCircle(col(GlyphRain), s * (0.05f + i % 2 * 0.03f), Offset(x, y), style = Stroke(sw * 0.4f))
        }
        "bolt", "thunder" -> {
            if (kind == "thunder") {
                drawCircle(col(GlyphGrey), s * 0.16f, Offset(w * 0.35f, h * 0.3f))
                drawCircle(col(GlyphGrey), s * 0.2f, Offset(w * 0.58f, h * 0.26f))
            }
            val on = (t * 3f % 1f) < 0.5f
            val path = Path().apply {
                moveTo(w * 0.58f, h * 0.12f); lineTo(w * 0.36f, h * 0.55f); lineTo(w * 0.52f, h * 0.55f)
                lineTo(w * 0.4f, h * 0.92f); lineTo(w * 0.68f, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
            }
            drawPath(path, col(if (on) GlyphGold else GlyphGold.copy(alpha = 0.45f)))
        }
        "flash" -> {
            val k = 1f - (t * 2f % 1f)
            for (i in 0 until 8) {
                val a = i * TAU / 8f
                drawLine(col(Color.White).copy(alpha = k * alpha), c + Offset(cos(a), sin(a)) * s * 0.12f,
                    c + Offset(cos(a), sin(a)) * s * (0.25f + 0.2f * k), strokeWidth = sw * 0.6f, cap = StrokeCap.Round)
            }
            drawCircle(col(Color.White), s * 0.1f * (0.6f + 0.4f * k), c)
        }
        "tone" -> for (i in 0 until 4) for (j in 0 until 4) {
            val r = s * (0.03f + 0.04f * (0.5f + 0.5f * sin((t + (i + j) * 0.12f) * TAU)))
            drawCircle(col(Color.White), r, Offset(w * (0.2f + i * 0.2f), h * (0.2f + j * 0.2f)))
        }
        "grain" -> for (i in 0 until 24) {
            val k = ((i * 7 + (t * 12f).toInt() * 13) % 24) / 24f
            drawCircle(col(Color.White).copy(alpha = k * alpha), s * 0.025f, Offset(w * ((i * 0.37f) % 1f), h * ((i * 0.61f) % 1f)))
        }
        "ink" -> {
            drawCircle(col(Color.White), s * 0.36f, c)
            drawArc(col(Color.Black), 90f + sin(t * TAU) * 20f, 180f, true, Offset(c.x - s * 0.36f, c.y - s * 0.36f), Size(s * 0.72f, s * 0.72f))
        }
        "eye", "blink" -> {
            val open = if (kind == "blink") abs(cos(t * TAU * 0.5f)).coerceIn(0.08f, 1f) else 1f
            val path = Path().apply {
                moveTo(w * 0.12f, c.y)
                quadraticBezierTo(w / 2f, c.y - s * 0.42f * open, w * 0.88f, c.y)
                quadraticBezierTo(w / 2f, c.y + s * 0.42f * open, w * 0.12f, c.y)
            }
            drawPath(path, col(Color.White), style = Stroke(sw * 0.6f))
            if (open > 0.3f) {
                val glow = 0.6f + 0.4f * sin(t * TAU * 2f)
                drawCircle(col(GlyphEnergy).copy(alpha = if (kind == "eye") glow * alpha else alpha), s * 0.13f * open, c)
            }
        }
        "gust" -> for (i in 0 until 3) {
            val path = Path()
            val off = (t * 1.5f + i * 0.33f) % 1f
            for (k in 0..14) {
                val x = w * (k / 14f * 0.7f + off * 0.6f - 0.3f)
                val y = h * (0.28f + i * 0.22f) + sin((k / 14f + i * 0.3f) * TAU) * s * 0.06f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, col(Color.White).copy(alpha = 0.85f * alpha), style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
        }
        "tornado" -> rotate(t * 360f, c) {
            for (i in 0 until 4) {
                val r = s * (0.1f + i * 0.1f)
                drawArc(col(GlyphGrey), i * 60f, 200f, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f),
                    style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
            }
        }
        "appear" -> {
            val k = (t * 1.2f % 1f).coerceIn(0f, 1f)
            val a = (k * 1.6f).coerceIn(0f, 1f)
            drawCircle(col(GlyphCloud).copy(alpha = (1f - a) * 0.7f * alpha), s * (0.25f + 0.2f * k), Offset(c.x, h * 0.62f))
            drawCircle(col(Color.White).copy(alpha = a * alpha), s * 0.12f, Offset(c.x, h * 0.28f))
            drawRoundRect(col(Color.White).copy(alpha = a * alpha), Offset(c.x - s * 0.17f, h * 0.44f), Size(s * 0.34f, h * 0.42f),
                CornerRadius(s * 0.12f))
        }
        "layers" -> for (i in 0 until 3) {
            val off = sin((t + i * 0.2f) * TAU) * s * 0.03f
            drawRoundRect(col(listOf(GlyphMagic, GlyphEnergy, GlyphSakura)[i]).copy(alpha = 0.75f * alpha),
                Offset(w * (0.15f + i * 0.12f) + off, h * (0.15f + i * 0.14f)), Size(w * 0.5f, h * 0.45f), CornerRadius(s * 0.06f))
        }
        "birds" -> for (i in 0 until 3) {
            val x = ((t * 0.6f + i * 0.3f) % 1f) * w * 1.2f - w * 0.1f
            val y = h * (0.3f + i * 0.2f)
            val flap = sin((t * 4f + i * 0.3f) * TAU) * s * 0.07f
            drawLine(col(Color.White), Offset(x - s * 0.12f, y - flap), Offset(x, y), strokeWidth = sw * 0.6f, cap = StrokeCap.Round)
            drawLine(col(Color.White), Offset(x, y), Offset(x + s * 0.12f, y - flap), strokeWidth = sw * 0.6f, cap = StrokeCap.Round)
        }
        "crickets" -> {
            drawOval(col(GlyphLeaf), Offset(w * 0.2f, h * 0.55f), Size(w * 0.35f, h * 0.18f))
            for (i in 1..3) {
                val k = ((t * 2f + i * 0.2f) % 1f)
                drawArc(col(Color.White).copy(alpha = (1f - k) * alpha), -60f, 120f, false,
                    Offset(w * 0.45f - s * 0.12f * i, h * 0.5f - s * 0.12f * i), Size(s * 0.24f * i, s * 0.24f * i),
                    style = Stroke(sw * 0.4f))
            }
        }
        "waterfall" -> {
            drawRect(col(GlyphGrey).copy(alpha = 0.5f * alpha), Offset(0f, 0f), Size(w * 0.25f, h))
            drawRect(col(GlyphGrey).copy(alpha = 0.5f * alpha), Offset(w * 0.75f, 0f), Size(w * 0.25f, h))
            for (i in 0 until 4) {
                val x = w * (0.32f + i * 0.12f)
                val y = ((t * 2f + i * 0.3f) % 1f) * h
                drawLine(col(GlyphRain), Offset(x, y - s * 0.25f), Offset(x, y), strokeWidth = sw * 0.6f, cap = StrokeCap.Round)
            }
            drawOval(col(Color.White).copy(alpha = 0.6f * alpha), Offset(w * 0.25f, h * 0.82f), Size(w * 0.5f, h * 0.14f))
        }
        "city" -> {
            val heights = listOf(0.45f, 0.7f, 0.55f, 0.8f, 0.5f)
            heights.forEachIndexed { i, k ->
                drawRect(col(GlyphGrey).copy(alpha = 0.8f * alpha), Offset(w * i / 5f + 1f, h * (1f - k)), Size(w / 5f - 2f, h * k))
            }
            val blink = (t * 2f % 1f) < 0.5f
            if (blink) drawCircle(col(GlyphGold), s * 0.035f, Offset(w * 0.7f, h * 0.35f))
        }
        else -> drawCircle(col(Color.White), s * 0.2f, c)
    }
}
