package com.zeta.fondo.engine

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sonidos de ambiente sintetizados con código (ruido filtrado, envolventes y osciladores).
 * No usan grabaciones: no hay problemas de derechos y la app no pesa más.
 * Cada sonido se genera para UN bucle exacto (con fundido interno), así al repetirse no salta.
 * Trueno y relámpago caen en los mismos momentos que el efecto visual "Relámpago".
 */
object Ambient {

    data class Def(val id: String, val name: String)

    val all = listOf(
        Def("rain_soft", "Lluvia suave"),
        Def("rain_heavy", "Lluvia fuerte"),
        Def("thunder", "Trueno"),
        Def("lightning", "Relámpago"),
        Def("wind", "Viento suave"),
        Def("gale", "Ventarrón"),
        Def("sand", "Arena y brisa"),
        Def("waves", "Olas"),
        Def("river", "Río"),
        Def("waterfall", "Cascada"),
        Def("birds", "Pájaros"),
        Def("crickets", "Grillos"),
        Def("fire", "Fuego"),
        Def("leaves", "Hojas"),
        Def("city", "Ciudad lejana"),
        // v44: más ambientes sintetizados (siguen sin grabaciones de terceros)
        Def("owl", "Búho"),
        Def("drip", "Goteras"),
        Def("heartbeat", "Latidos"),
        Def("creak", "Crujidos"),
        Def("footsteps", "Pasos lejanos"),
        Def("train", "Tren"),
        Def("horn", "Claxon lejano"),
        Def("seagulls", "Gaviotas")
    )

    /** v44 · Sonidos de EVENTO (suenan una vez desde el inicio de su bloque). */
    val oneShots = listOf(Def("whoosh", "Whoosh"), Def("impact", "Impacto"), Def("boom", "Explosión"), Def("magic", "Magia"))

    fun isOneShot(id: String): Boolean = oneShots.any { it.id == id }

    private val shotBank = HashMap<String, ShortArray>()

    /** Evento sintetizado (estéreo intercalado, ~1-2 s). */
    @Synchronized
    fun shot(id: String, rate: Int): ShortArray = shotBank.getOrPut("$id|$rate") { synthShot(id, rate) }

    /** Ambiente de un bucle exacto para un id (cacheado como en [mix]). */
    @Synchronized
    fun loop(id: String, rate: Int, frames: Int, boltCount: Int): ShortArray? {
        if (all.none { it.id == id } || frames <= 0) return null
        val key = "$id|$rate|$frames|$boltCount"
        // v44.11: los bloques de sonido tienen su PROPIA caché. Antes compartían la de [mix], que
        // la vaciaba en cada lectura de audio (cada ~23 ms): el sonido se volvía a fabricar una y
        // otra vez y se oía entrecortado.
        loopBank.keys.removeAll { !it.endsWith("|$rate|$frames|$boltCount") }
        return loopBank.getOrPut(key) { synth(id, rate, frames, boltCount) }
    }

    /** Caché de [loop] (bloques de sonido de la línea de tiempo), separada de la de [mix]. */
    private val loopBank = HashMap<String, ShortArray>()

    /** ¿Ya está fabricado? (para preparar los sonidos ANTES de que suenen). */
    @Synchronized
    fun isLoopReady(id: String, rate: Int, frames: Int, boltCount: Int): Boolean =
        loopBank.containsKey("$id|$rate|$frames|$boltCount")

    /** Momento (fracción del bucle) del rayo k: el mismo que usa el shader. */
    fun boltPhase(k: Int, count: Int): Float = (k + 0.3f) / count.coerceAtLeast(1)

    private val bank = HashMap<String, ShortArray>()

    /** Sonidos activos, ya sintetizados, con su volumen. Cada arreglo: estéreo intercalado, un bucle. */
    @Synchronized
    fun mix(ambient: Map<String, Float>, rate: Int, frames: Int, boltCount: Int): List<Pair<ShortArray, Float>> {
        if (frames <= 0) return emptyList()
        val suffix = "|$rate|$frames|$boltCount"
        val active = ambient.filter { it.value > 0.01f && all.any { d -> d.id == it.key } }
        // Solo se guardan en memoria los sonidos del formato actual.
        bank.keys.removeAll { !it.endsWith(suffix) || it.substringBefore('|') !in active.keys }
        return active.map { (id, vol) ->
            val key = id + suffix
            val data = bank[key] ?: synth(id, rate, frames, boltCount).also { bank[key] = it }
            data to vol
        }
    }

    // ------------------------------------------------------------------ utilidades

    private fun coef(fc: Float, rate: Int): Float = (1.0 - exp(-2.0 * PI * fc / rate)).toFloat()

    private class Lp(var a: Float) {
        var y = 0f
        fun run(x: Float): Float { y += a * (x - y); return y }
    }

    /** Filtro de estado variable: salida pasa-banda con resonancia. */
    private class Svf {
        var low = 0f
        var band = 0f
        fun band(x: Float, fc: Float, rate: Int, damp: Float): Float {
            val f = (2.0 * sin(PI * min(fc, rate * 0.2f) / rate)).toFloat()
            low += f * band
            val high = x - low - damp * band
            band += f * high
            return band
        }
    }

    private fun Random.n(): Float = nextFloat() * 2f - 1f

    /** Oscilación lenta periódica en el bucle (0..1). */
    private fun slow(i: Int, frames: Int, k: Int, ph: Double): Float =
        (0.5 + 0.5 * sin(2.0 * PI * k * i / frames + ph)).toFloat()

    private fun addPan(l: FloatArray, r: FloatArray, at: Int, v: Float, pan: Float, wrap: Int) {
        val idx = if (wrap > 0) ((at % wrap) + wrap) % wrap else at
        if (idx < 0 || idx >= l.size) return
        l[idx] += v * (1f - pan)
        r[idx] += v * pan
    }

    // ------------------------------------------------------------------ síntesis

    private fun synth(id: String, rate: Int, frames: Int, boltCount: Int): ShortArray {
        val fade = min(frames / 4, rate / 2).coerceAtLeast(1)
        val total = frames + fade
        val l = FloatArray(total)
        val r = FloatArray(total)
        val rnd = Random(id.hashCode().toLong() * 7919L + 17L)
        val sec = total.toFloat() / rate

        fun drops(perSec: Float, fLo: Float, fHi: Float, ampLo: Float, ampHi: Float, durSec: Float) {
            val count = (sec * perSec).roundToInt()
            val dur = (durSec * rate).toInt().coerceAtLeast(8)
            repeat(count) {
                val pos = rnd.nextInt(total)
                val f = fLo + rnd.nextFloat() * (fHi - fLo)
                val amp = ampLo + rnd.nextFloat() * (ampHi - ampLo)
                val pan = rnd.nextFloat()
                for (j in 0 until dur) {
                    val at = pos + j
                    if (at >= total) break
                    val v = (sin(2.0 * PI * f * j / rate) * exp(-j / (dur * 0.25))).toFloat() * amp
                    l[at] += v * (1f - pan); r[at] += v * pan
                }
            }
        }

        fun crackles(perSec: Float, ampLo: Float, ampHi: Float) {
            val count = (sec * perSec).roundToInt()
            repeat(count) {
                val pos = rnd.nextInt(total)
                val dur = (rate * (0.0015f + rnd.nextFloat() * 0.004f)).toInt().coerceAtLeast(4)
                val amp = ampLo + rnd.nextFloat() * (ampHi - ampLo)
                val pan = rnd.nextFloat()
                var prev = 0f
                for (j in 0 until dur) {
                    val at = pos + j
                    if (at >= total) break
                    val n = rnd.n()
                    val v = (n - prev) * amp * exp(-j / (dur * 0.3f))
                    prev = n
                    l[at] += v * (1f - pan); r[at] += v * pan
                }
            }
        }

        when (id) {
            "rain_soft", "rain_heavy" -> {
                val heavy = id == "rain_heavy"
                val lpL = Lp(coef(if (heavy) 6000f else 3500f, rate)); val lpR = Lp(lpL.a)
                val hpL = Lp(coef(400f, rate)); val hpR = Lp(hpL.a)
                val rumL = Lp(coef(250f, rate)); val rumR = Lp(rumL.a)
                val gain = if (heavy) 0.5f else 0.32f
                for (i in 0 until total) {
                    var a = lpL.run(rnd.n()); a -= hpL.run(a)
                    var b = lpR.run(rnd.n()); b -= hpR.run(b)
                    l[i] = a * gain; r[i] = b * gain
                    if (heavy) { l[i] += rumL.run(rnd.n()) * 1.1f; r[i] += rumR.run(rnd.n()) * 1.1f }
                }
                drops(if (heavy) 70f else 26f, 1800f, 5000f, 0.04f, if (heavy) 0.1f else 0.14f, 0.012f)
            }
            "wind", "gale" -> {
                val gale = id == "gale"
                val a1 = Lp(0f); val a2 = Lp(0f); val b1 = Lp(0f); val b2 = Lp(0f)
                val svf = Svf()
                for (i in 0 until total) {
                    val g = 0.6f * slow(i, frames, 1, 0.0) + 0.4f * slow(i, frames, 3, 1.0)
                    val fc = if (gale) 500f + 1500f * g else 330f + 520f * g
                    val c = coef(fc, rate)
                    a1.a = c; a2.a = c; b1.a = c; b2.a = c
                    val amp = if (gale) 0.9f + 1.1f * g else 0.7f + 0.9f * g
                    l[i] = a2.run(a1.run(rnd.n())) * amp
                    r[i] = b2.run(b1.run(rnd.n())) * amp
                    if (gale) {
                        val w = svf.band(rnd.n(), 600f + 520f * g, rate, 0.08f) * 0.05f * g
                        l[i] += w; r[i] += w * 0.8f
                    }
                }
            }
            "sand" -> {
                val lp1 = Lp(coef(2500f, rate)); val lp2 = Lp(lp1.a)
                for (i in 0 until total) {
                    val g = 0.6f + 0.4f * slow(i, frames, 2, 0.5)
                    val a = rnd.n(); val b = rnd.n()
                    l[i] = (a - lp1.run(a)) * 0.12f * g
                    r[i] = (b - lp2.run(b)) * 0.12f * g
                }
                crackles(20f, 0.02f, 0.06f)
            }
            "waves" -> {
                val count = max(1, (frames / (6.5f * rate)).roundToInt())
                val period = frames.toFloat() / count
                val a1 = Lp(0f); val a2 = Lp(0f); val b1 = Lp(0f); val b2 = Lp(0f)
                val washL = Lp(coef(400f, rate)); val washR = Lp(washL.a)
                for (i in 0 until total) {
                    val ph = (i % period) / period
                    val env = if (ph < 0.35f) (ph / 0.35f) * (ph / 0.35f) else exp(-(ph - 0.35f) * 5f)
                    val c = coef(250f + 3000f * env, rate)
                    a1.a = c; a2.a = c; b1.a = c; b2.a = c
                    val amp = 0.15f + 1.1f * env
                    l[i] = a2.run(a1.run(rnd.n())) * amp + washL.run(rnd.n()) * 0.25f
                    r[i] = b2.run(b1.run(rnd.n())) * amp + washR.run(rnd.n()) * 0.25f
                }
            }
            "river" -> {
                val sl = Svf(); val sr = Svf()
                val bub = Lp(coef(6f, rate))
                for (i in 0 until total) {
                    val m = 0.7f + 6f * abs(bub.run(rnd.n()))
                    l[i] = sl.band(rnd.n(), 900f, rate, 1.0f) * 0.22f * m
                    r[i] = sr.band(rnd.n(), 1100f, rate, 1.0f) * 0.22f * m
                }
            }
            "waterfall" -> {
                val lpL = Lp(coef(5000f, rate)); val lpR = Lp(lpL.a)
                val rumL = Lp(coef(120f, rate)); val rumR = Lp(rumL.a)
                for (i in 0 until total) {
                    l[i] = lpL.run(rnd.n()) * 0.42f + rumL.run(rnd.n()) * 1.6f
                    r[i] = lpR.run(rnd.n()) * 0.42f + rumR.run(rnd.n()) * 1.6f
                }
            }
            "birds" -> {
                val calls = max(1, (sec / 2f).roundToInt())
                repeat(calls) {
                    var pos = rnd.nextInt(total)
                    val chirps = 2 + rnd.nextInt(4)
                    val base = 2500f + rnd.nextFloat() * 1700f
                    val sweep = (rnd.nextFloat() - 0.4f) * 1800f
                    val pan = 0.15f + rnd.nextFloat() * 0.7f
                    val amp = 0.12f + rnd.nextFloat() * 0.08f
                    repeat(chirps) {
                        val dur = (rate * (0.06f + rnd.nextFloat() * 0.06f)).toInt()
                        var phase = 0.0
                        for (j in 0 until dur) {
                            val at = pos + j
                            if (at >= total) break
                            val t = j.toFloat() / dur
                            val f = base + sweep * t
                            phase += 2.0 * PI * f / rate
                            val env = sin(PI * t).toFloat()
                            val v = ((sin(phase) + 0.3 * sin(2 * phase)).toFloat()) * env * amp
                            l[at] += v * (1f - pan); r[at] += v * pan
                        }
                        pos += dur + (rate * (0.05f + rnd.nextFloat() * 0.06f)).toInt()
                    }
                }
            }
            "crickets" -> {
                val crickets = listOf(Triple(4300f, 0.45f, 0.25f), Triple(4750f, 0.52f, 0.75f))
                for ((f, every, pan) in crickets) {
                    val step = (every * rate).toInt()
                    val offset = rnd.nextInt(step)
                    var start = offset
                    while (start < total) {
                        for (p in 0 until 3) {
                            val ps = start + (p * 0.03f * rate).toInt()
                            val dur = (0.018f * rate).toInt()
                            for (j in 0 until dur) {
                                val at = ps + j
                                if (at >= total) break
                                val env = sin(PI * j / dur).toFloat()
                                val v = sin(2.0 * PI * f * j / rate).toFloat() * env * 0.07f
                                l[at] += v * (1f - pan); r[at] += v * pan
                            }
                        }
                        start += step
                    }
                }
            }
            "fire" -> {
                val rumL = Lp(coef(180f, rate)); val rumR = Lp(rumL.a)
                val hiss = Lp(coef(4000f, rate))
                for (i in 0 until total) {
                    val h = rnd.n()
                    val hs = (h - hiss.run(h)) * 0.03f
                    l[i] = rumL.run(rnd.n()) * 1.3f + hs
                    r[i] = rumR.run(rnd.n()) * 1.3f + hs
                }
                crackles(15f, 0.2f, 0.5f)
            }
            "leaves" -> {
                val hpL = Lp(coef(1800f, rate)); val hpR = Lp(hpL.a)
                val lpL = Lp(coef(7000f, rate)); val lpR = Lp(lpL.a)
                val env = FloatArray(total)
                val bursts = max(1, sec.roundToInt())
                repeat(bursts) {
                    val pos = rnd.nextInt(total)
                    val dur = (rate * (0.2f + rnd.nextFloat() * 0.5f)).toInt()
                    val amp = 0.15f + rnd.nextFloat() * 0.15f
                    for (j in 0 until dur) {
                        val at = pos + j
                        if (at >= total) break
                        env[at] += (0.5f - 0.5f * kotlin.math.cos(2.0 * PI * j / dur).toFloat()) * amp
                    }
                }
                for (i in 0 until total) {
                    val a = rnd.n(); val b = rnd.n()
                    l[i] = lpL.run(a - hpL.run(a)) * (0.03f + env[i])
                    r[i] = lpR.run(b - hpR.run(b)) * (0.03f + env[i])
                }
            }
            "owl" -> {
                // «Uh… uh-uh»: tono grave con un poco de vibrato, cada ~6 s.
                val calls = max(1, (sec / 6f).roundToInt())
                repeat(calls) { c ->
                    val pos = (c * total / calls) + rnd.nextInt((rate * 1.5f).toInt())
                    val pan = 0.2f + rnd.nextFloat() * 0.6f
                    val f0 = 360f + rnd.nextFloat() * 40f
                    for ((n, at) in listOf(0f, 0.55f, 0.85f).withIndex()) {
                        val start = pos + (at * rate).toInt()
                        val dur = ((if (n == 0) 0.45f else 0.30f) * rate).toInt()
                        var ph = 0.0
                        for (j in 0 until dur) {
                            val t = j.toFloat() / dur
                            val f = f0 * (1f - 0.08f * t) * (1f + 0.01f * sin(2.0 * PI * 6.0 * j / rate).toFloat())
                            ph += 2.0 * PI * f / rate
                            val env = sin(PI * t).toFloat()
                            addPan(l, r, start + j, (sin(ph) + 0.25 * sin(2 * ph)).toFloat() * env * 0.25f, pan, 0)
                        }
                    }
                }
                val lpL = Lp(coef(300f, rate)); val lpR = Lp(lpL.a)
                for (i in 0 until total) { l[i] += lpL.run(rnd.n()) * 0.05f; r[i] += lpR.run(rnd.n()) * 0.05f }
            }
            "drip" -> {
                drops(1.6f, 900f, 1600f, 0.25f, 0.45f, 0.05f)
                drops(0.7f, 2200f, 3200f, 0.15f, 0.25f, 0.03f)
            }
            "heartbeat" -> {
                // ~66 pulsos/min, número ENTERO de latidos por bucle (empalma).
                val beats = max(1, (frames / (rate * 0.91f)).roundToInt())
                val period = frames.toFloat() / beats
                for (b in 0 until beats + 1) {
                    for ((k, off) in listOf(0f, 0.28f).withIndex()) {
                        val start = (b * period + off * rate).toInt()
                        val dur = (0.16f * rate).toInt()
                        for (j in 0 until dur) {
                            val t = j.toFloat() / rate
                            val v = sin(2.0 * PI * (if (k == 0) 55.0 else 48.0) * t).toFloat() * exp(-t / 0.05f) * (if (k == 0) 0.9f else 0.6f)
                            addPan(l, r, start + j, v, 0.5f, 0)
                        }
                    }
                }
            }
            "creak" -> {
                val n = max(1, (sec / 5f).roundToInt())
                repeat(n) {
                    val pos = rnd.nextInt(total)
                    val dur = (rate * (0.6f + rnd.nextFloat() * 0.8f)).toInt()
                    val svf = Svf()
                    val pan = rnd.nextFloat()
                    val f0 = 300f + rnd.nextFloat() * 500f
                    for (j in 0 until dur) {
                        val t = j.toFloat() / dur
                        // tirones irregulares (stick-slip) a través de un resonador
                        val pulse = if (rnd.nextFloat() < 0.02f + 0.04f * sin(PI * t).toFloat()) rnd.n() * 3f else 0f
                        val v = svf.band(pulse, f0 * (1f + 0.4f * t), rate, 0.03f) * sin(PI * t).toFloat() * 0.2f
                        addPan(l, r, pos + j, v, pan, 0)
                    }
                }
            }
            "footsteps" -> {
                val stepsPerLoop = max(2, (frames / (rate * 0.62f)).roundToInt())
                val period = frames.toFloat() / stepsPerLoop
                for (k in 0 until stepsPerLoop + 1) {
                    val start = (k * period).toInt()
                    val dur = (0.09f * rate).toInt()
                    val lp = Lp(coef(700f, rate))
                    val pan = if (k % 2 == 0) 0.42f else 0.58f
                    for (j in 0 until dur) {
                        val v = lp.run(rnd.n()) * exp(-j / (0.02f * rate)) * 0.9f
                        addPan(l, r, start + j, v, pan, 0)
                    }
                }
            }
            "train" -> {
                val rumL = Lp(coef(140f, rate)); val rumR = Lp(rumL.a)
                for (i in 0 until total) { l[i] = rumL.run(rnd.n()) * 1.5f; r[i] = rumR.run(rnd.n()) * 1.5f }
                // traqueteo: pares de golpes con ritmo regular que cierra el bucle
                val pairs = max(2, (frames / (rate * 0.9f)).roundToInt())
                val period = frames.toFloat() / pairs
                for (k in 0 until pairs + 1) for (off in listOf(0f, 0.12f)) {
                    val start = (k * period + off * rate).toInt()
                    val dur = (0.05f * rate).toInt()
                    val lp = Lp(coef(1200f, rate))
                    for (j in 0 until dur) addPan(l, r, start + j, lp.run(rnd.n()) * exp(-j / (0.012f * rate)) * 0.6f, 0.5f, 0)
                }
            }
            "horn" -> {
                val n = max(1, (sec / 7f).roundToInt())
                repeat(n) {
                    val pos = rnd.nextInt(total)
                    val dur = (rate * (0.25f + rnd.nextFloat() * 0.35f)).toInt()
                    val pan = rnd.nextFloat()
                    val lp = Lp(coef(1800f, rate))
                    for (j in 0 until dur) {
                        val t = j.toFloat() / rate
                        val sq = (if (sin(2.0 * PI * 420.0 * t) > 0) 1f else -1f) + (if (sin(2.0 * PI * 528.0 * t) > 0) 1f else -1f)
                        val env = min(1f, j / (0.01f * rate)) * min(1f, (dur - j) / (0.02f * rate))
                        addPan(l, r, pos + j, lp.run(sq) * env * 0.08f, pan, 0)
                    }
                }
                val rum = Lp(coef(200f, rate))
                for (i in 0 until total) { val v = rum.run(rnd.n()) * 0.6f; l[i] += v; r[i] += v }
            }
            "seagulls" -> {
                val n = max(1, (sec / 3f).roundToInt())
                repeat(n) {
                    var pos = rnd.nextInt(total)
                    val pan = rnd.nextFloat()
                    repeat(2 + rnd.nextInt(3)) {
                        val dur = (rate * (0.18f + rnd.nextFloat() * 0.12f)).toInt()
                        var ph = 0.0
                        val f0 = 1500f + rnd.nextFloat() * 500f
                        for (j in 0 until dur) {
                            val t = j.toFloat() / dur
                            val f = f0 * (1f - 0.35f * t)
                            ph += 2.0 * PI * f / rate
                            val env = sin(PI * t).toFloat()
                            val v = (sin(ph) + 0.5 * sin(2 * ph) + 0.3 * sin(3 * ph)).toFloat() * env * 0.08f
                            addPan(l, r, pos + j, v, pan, 0)
                        }
                        pos += dur + (rate * 0.08f).toInt()
                    }
                }
                val lp = Lp(coef(400f, rate))
                for (i in 0 until total) { val v = lp.run(rnd.n()) * 0.15f; l[i] += v; r[i] += v }
            }
            "city" -> {
                val rumL = Lp(coef(220f, rate)); val rumR = Lp(rumL.a)
                val midL = Lp(coef(800f, rate)); val midR = Lp(midL.a)
                for (i in 0 until total) {
                    l[i] = rumL.run(rnd.n()) * 1.4f + midL.run(rnd.n()) * 0.08f
                    r[i] = rumR.run(rnd.n()) * 1.4f + midR.run(rnd.n()) * 0.08f
                }
                val cars = max(1, (sec / 4f).roundToInt())
                repeat(cars) {
                    val pos = rnd.nextInt(total)
                    val dur = (rate * 3f).toInt()
                    val lp = Lp(coef(600f, rate))
                    val panFrom = rnd.nextFloat()
                    for (j in 0 until dur) {
                        val at = pos + j
                        if (at >= total) break
                        val t = j.toFloat() / dur
                        val v = lp.run(rnd.n()) * sin(PI * t).toFloat() * 0.35f
                        val pan = panFrom + (1f - 2f * panFrom) * t
                        l[at] += v * (1f - pan); r[at] += v * pan
                    }
                }
            }
        }

        // Fundido de igual potencia: el final del bucle empalma con el inicio.
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        for (i in 0 until frames) {
            if (i < fade) {
                val t = i.toFloat() / fade
                val a = sqrt(t); val b = sqrt(1f - t)
                outL[i] = l[i] * a + l[frames + i] * b
                outR[i] = r[i] * a + r[frames + i] * b
            } else {
                outL[i] = l[i]; outR[i] = r[i]
            }
        }

        // Eventos sincronizados con el relámpago visual (se suman de forma circular).
        if (id == "thunder" || id == "lightning") {
            val n = boltCount.coerceAtLeast(1)
            for (k in 0 until n) {
                val at = (boltPhase(k, n) * frames).toInt()
                if (id == "lightning") {
                    val dur = (0.2f * rate).toInt()
                    val hp = Lp(coef(1500f, rate))
                    val pan = 0.3f + rnd.nextFloat() * 0.4f
                    for (j in 0 until dur) {
                        val x = rnd.n()
                        val v = (x - hp.run(x)) * exp(-j / (0.04f * rate)) * 1.4f
                        addPan(outL, outR, at + j, v, pan, frames)
                    }
                } else {
                    val start = at + (0.15f * rate).toInt()
                    val dur = (3.2f * rate).toInt()
                    val lp1 = Lp(coef(150f, rate)); val lp2 = Lp(lp1.a)
                    val wob = Lp(coef(3f, rate))
                    val pan = 0.35f + rnd.nextFloat() * 0.3f
                    for (j in 0 until dur) {
                        val t = j.toFloat() / rate
                        val attack = min(1f, t / 0.08f)
                        val env = attack * exp(-t / 0.9f) * (0.6f + 2.5f * abs(wob.run(rnd.n())))
                        val v = lp2.run(lp1.run(rnd.n())) * env * 6f
                        addPan(outL, outR, start + j, v, pan, frames)
                    }
                }
            }
        }

        // Nivel uniforme: los ruidos se ajustan por energía (RMS) y los eventos por pico.
        val rmsTarget = mapOf("rain_soft" to 0.10f, "rain_heavy" to 0.16f, "wind" to 0.11f, "gale" to 0.17f,
            "sand" to 0.05f, "waves" to 0.13f, "river" to 0.09f, "waterfall" to 0.16f, "fire" to 0.08f,
            "leaves" to 0.05f, "city" to 0.08f, "train" to 0.12f)
        val peakTarget = mapOf("birds" to 0.55f, "crickets" to 0.35f, "thunder" to 0.85f, "lightning" to 0.8f,
            "owl" to 0.5f, "drip" to 0.5f, "heartbeat" to 0.8f, "creak" to 0.5f, "footsteps" to 0.55f,
            "horn" to 0.45f, "seagulls" to 0.5f)
        var sum = 0.0
        var peak = 1e-6f
        for (i in 0 until frames) {
            sum += (outL[i] * outL[i] + outR[i] * outR[i]).toDouble()
            peak = max(peak, max(abs(outL[i]), abs(outR[i])))
        }
        val rms = sqrt(sum / (2.0 * frames)).toFloat().coerceAtLeast(1e-6f)
        var scale = rmsTarget[id]?.let { it / rms } ?: ((peakTarget[id] ?: 0.5f) / peak)
        if (peak * scale > 0.95f) scale = 0.95f / peak
        for (i in 0 until frames) { outL[i] *= scale; outR[i] *= scale }

        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            out[i * 2] = (outL[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out[i * 2 + 1] = (outR[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    // ------------------------------------------------------------------ v44: eventos

    private fun synthShot(id: String, rate: Int): ShortArray {
        val sec = when (id) { "boom" -> 2.2f; "magic" -> 1.6f; "impact" -> 1.0f; else -> 0.9f }
        val n = (sec * rate).toInt()
        val l = FloatArray(n); val r = FloatArray(n)
        val rnd = Random(id.hashCode().toLong() * 31L + 5L)
        when (id) {
            "whoosh" -> {
                val svfL = Svf(); val svfR = Svf()
                for (j in 0 until n) {
                    val t = j.toFloat() / n
                    val env = sin(PI * t).toFloat().let { it * it }
                    val fc = 300f + 2500f * t
                    val pan = t
                    val v = svfL.band(rnd.n(), fc, rate, 0.4f) * env * 0.6f
                    val w = svfR.band(rnd.n(), fc * 1.05f, rate, 0.4f) * env * 0.6f
                    l[j] = v * (1.2f - pan); r[j] = w * (0.2f + pan)
                }
            }
            "impact" -> {
                val lp = Lp(coef(900f, rate))
                for (j in 0 until n) {
                    val t = j.toFloat() / rate
                    val body = sin(2.0 * PI * (70.0 - 30.0 * min(1f, t / 0.3f)) * t).toFloat() * exp(-t / 0.18f)
                    val crack = lp.run(rnd.n()) * exp(-t / 0.03f) * 2f
                    l[j] = body + crack; r[j] = body + crack
                }
            }
            "boom" -> {
                val lp1 = Lp(coef(260f, rate)); val lp2 = Lp(lp1.a)
                for (j in 0 until n) {
                    val t = j.toFloat() / rate
                    val env = min(1f, t / 0.02f) * exp(-t / 0.7f)
                    val v = lp2.run(lp1.run(rnd.n())) * env * 7f + sin(2.0 * PI * 45.0 * t).toFloat() * exp(-t / 0.35f) * 0.8f
                    l[j] = v; r[j] = v * 0.95f
                }
            }
            "magic" -> {
                var ph = 0.0
                for (j in 0 until n) {
                    val t = j.toFloat() / n
                    val f = 600f + 1600f * t
                    ph += 2.0 * PI * f / rate
                    val shimmer = (sin(ph) + 0.5 * sin(ph * 2.01) + 0.25 * sin(ph * 3.98)).toFloat()
                    val env = sin(PI * t).toFloat() * (0.6f + 0.4f * sin(2.0 * PI * 9.0 * j / rate).toFloat())
                    val sp = if (rnd.nextFloat() < 0.002f) rnd.n() * 0.8f else 0f
                    l[j] = shimmer * env * 0.25f + sp; r[j] = shimmer * env * 0.25f * (0.8f + 0.2f * t) + sp
                }
            }
        }
        var peak = 1e-6f
        for (j in 0 until n) peak = max(peak, max(abs(l[j]), abs(r[j])))
        val k = 0.85f / peak
        val out = ShortArray(n * 2)
        for (j in 0 until n) {
            out[j * 2] = (l[j] * k).coerceIn(-1f, 1f).times(32767f).toInt().toShort()
            out[j * 2 + 1] = (r[j] * k).coerceIn(-1f, 1f).times(32767f).toInt().toShort()
        }
        return out
    }
}
