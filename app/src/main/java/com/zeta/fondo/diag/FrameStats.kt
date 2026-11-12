package com.zeta.fondo.diag

/**
 * v44 · Estadísticas de los últimos cuadros de la vista previa. Código puro (pruebas JVM).
 *
 * - intervalo: tiempo entre dos cuadros dibujados (de él salen los FPS reales);
 * - cpu: lo que tarda el hilo de OpenGL en preparar y enviar el cuadro (sin esperar a la GPU);
 * - gpu: tiempo de GPU medido con EXT_disjoint_timer_query. Si el teléfono no lo tiene,
 *   queda en null y la interfaz muestra «No disponible» (nunca se inventa).
 *
 * Los intervalos de más de [PAUSE_MS] (pausa, la app en segundo plano) no cuentan.
 */
class FrameStats(private val capacity: Int = 240) {
    private val interval = FloatArray(capacity)
    private val cpu = FloatArray(capacity)
    private val gpu = FloatArray(capacity)
    private val scale = FloatArray(capacity)
    private var n = 0
    private var head = 0
    private var gpuN = 0
    private var gpuHead = 0
    private var lastFrameNs = 0L
    /** Total de cuadros con intervalo válido desde el último reset (para la prueba de 60 s). */
    var frames = 0L; private set
    private var sumInterval = 0.0
    private var sumCpu = 0.0
    private var sumGpu = 0.0
    private var gpuFrames = 0L
    private var slow = 0L
    private var sumScale = 0.0

    @Synchronized fun reset() {
        n = 0; head = 0; gpuN = 0; gpuHead = 0; lastFrameNs = 0L
        frames = 0; sumInterval = 0.0; sumCpu = 0.0; sumGpu = 0.0; gpuFrames = 0; slow = 0; sumScale = 0.0
    }

    /** Un cuadro dibujado a las [nowNs], con [cpuMs] de trabajo en el hilo GL y escala [renderScale]. */
    @Synchronized fun frame(nowNs: Long, cpuMs: Float, renderScale: Float, targetMs: Float) {
        val prev = lastFrameNs
        lastFrameNs = nowNs
        if (prev == 0L) return
        val ms = (nowNs - prev) / 1e6f
        if (ms <= 0f || ms > PAUSE_MS) return
        interval[head] = ms
        cpu[head] = cpuMs
        scale[head] = renderScale
        head = (head + 1) % capacity
        if (n < capacity) n++
        frames++
        sumInterval += ms; sumCpu += cpuMs; sumScale += renderScale
        if (ms > targetMs * 1.5f) slow++
    }

    @Synchronized fun gpu(ms: Float) {
        if (ms < 0f || ms > 1000f) return
        gpu[gpuHead] = ms
        gpuHead = (gpuHead + 1) % capacity
        if (gpuN < capacity) gpuN++
        sumGpu += ms; gpuFrames++
    }

    data class Snapshot(
        val fps: Float?, val frameMs: Float?, val p95Ms: Float?, val cpuMs: Float?, val gpuMs: Float?,
        val scale: Float?, val frames: Long, val slowFrames: Long,
        /** Promedios desde el último reset (no solo la ventana reciente). */
        val totalFps: Float?, val totalCpuMs: Float?, val totalGpuMs: Float?, val totalScale: Float?
    )

    @Synchronized fun snapshot(): Snapshot {
        if (n == 0) return Snapshot(null, null, null, null, if (gpuN > 0) avg(gpu, gpuN) else null, null, frames, slow,
            null, null, if (gpuFrames > 0) (sumGpu / gpuFrames).toFloat() else null, null)
        val sorted = interval.copyOf(n).also { it.sort() }
        val p95 = sorted[((n - 1) * .95f).toInt()]
        val meanInterval = avg(interval, n)
        return Snapshot(
            fps = 1000f / meanInterval, frameMs = meanInterval, p95Ms = p95, cpuMs = avg(cpu, n),
            gpuMs = if (gpuN > 0) avg(gpu, gpuN) else null, scale = avg(scale, n), frames = frames, slowFrames = slow,
            totalFps = if (frames > 0) (1000.0 / (sumInterval / frames)).toFloat() else null,
            totalCpuMs = if (frames > 0) (sumCpu / frames).toFloat() else null,
            totalGpuMs = if (gpuFrames > 0) (sumGpu / gpuFrames).toFloat() else null,
            totalScale = if (frames > 0) (sumScale / frames).toFloat() else null
        )
    }

    private fun avg(a: FloatArray, count: Int): Float {
        var s = 0.0
        for (i in 0 until count) s += a[i]
        return (s / count).toFloat()
    }

    companion object {
        /** Un intervalo más largo es una pausa, no un cuadro lento. */
        const val PAUSE_MS = 500f
    }
}
