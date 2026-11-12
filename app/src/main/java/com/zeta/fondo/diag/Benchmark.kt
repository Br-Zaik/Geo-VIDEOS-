package com.zeta.fondo.diag

import java.util.Locale

/**
 * v44 · Prueba local de rendimiento: 1, 3 y 6 efectos durante [Benchmark.STAGE_SEC] s cada una.
 * Solo se ejecuta cuando el usuario la pide en Ajustes › Diagnóstico. Código puro (pruebas JVM).
 */
object Benchmark {
    const val STAGE_SEC = 60
    /** Efectos de cada etapa: se añaden en este orden (1 → 3 → 6). */
    val effects = listOf("rain", "snow", "fog", "smoke", "flame", "leaves")
    val stages = intArrayOf(1, 3, 6)

    fun effectsFor(count: Int): List<String> = effects.take(count.coerceIn(1, effects.size))

    data class StageResult(
        val effects: Int,
        val seconds: Int,
        val compileWaitMs: Long,
        val stats: FrameStats.Snapshot,
        val thermalStart: String,
        val thermalEnd: String,
        val ramAvailMbEnd: Long?,
        val completed: Boolean
    )

    /** «No disponible» cuando un contador no existe en este teléfono. Nunca se inventa. */
    fun na(v: Float?, fmt: String = "%.1f"): String = if (v == null || v.isNaN()) "No disponible" else String.format(Locale.US, fmt, v)

    fun report(header: List<Pair<String, String>>, results: List<StageResult>): String = buildString {
        append("Zeta Motion · Informe de diagnóstico\n")
        append("====================================\n")
        for ((k, v) in header) append(k).append(": ").append(v).append('\n')
        append('\n')
        append("Prueba de vista previa (cada etapa: ").append(STAGE_SEC).append(" s reproduciendo)\n")
        if (results.isEmpty()) append("Sin etapas completadas.\n")
        for (r in results) {
            val s = r.stats
            append("\n— ").append(r.effects).append(if (r.effects == 1) " efecto" else " efectos")
                .append(" (").append(effectsFor(r.effects).joinToString(", ")).append(")")
            if (!r.completed) append(" · INTERRUMPIDA")
            append('\n')
            append("  Duración medida: ").append(r.seconds).append(" s · cuadros: ").append(s.frames).append('\n')
            append("  Espera de compilación del shader: ").append(r.compileWaitMs).append(" ms\n")
            append("  FPS medios: ").append(na(s.totalFps)).append('\n')
            append("  Cuadro p95 (ventana final): ").append(na(s.p95Ms)).append(" ms\n")
            append("  Cuadros lentos (>1,5× objetivo): ").append(s.slowFrames).append('\n')
            append("  CPU hilo GL por cuadro: ").append(na(s.totalCpuMs, "%.2f")).append(" ms\n")
            append("  GPU por cuadro: ").append(na(s.totalGpuMs, "%.2f")).append(if (s.totalGpuMs != null) " ms\n" else "\n")
            append("  Escala media de render: ").append(na(s.totalScale?.times(100f), "%.0f")).append(if (s.totalScale != null) " %\n" else "\n")
            append("  Estado térmico: ").append(r.thermalStart).append(" → ").append(r.thermalEnd).append('\n')
            append("  RAM libre al final: ").append(r.ramAvailMbEnd?.let { "$it MB" } ?: "No disponible").append('\n')
        }
        append("\nNotas:\n")
        append("- Los FPS de la vista previa tienen como techo los fps pedidos por el reproductor.\n")
        append("- «CPU» es el tiempo del hilo de OpenGL para preparar cada cuadro; no espera a la GPU.\n")
        append("- «GPU» solo aparece si el teléfono tiene GL_EXT_disjoint_timer_query.\n")
        append("- La exportación siempre usa la resolución elegida; la escala solo afecta a la vista previa.\n")
    }

    /** Texto del estado térmico de PowerManager (API 29+). */
    fun thermalName(status: Int?): String = when (status) {
        null -> "No disponible"
        0 -> "Normal"
        1 -> "Leve"
        2 -> "Moderado"
        3 -> "Grave"
        4 -> "Crítico"
        5 -> "Emergencia"
        6 -> "Apagado"
        else -> "Desconocido ($status)"
    }
}
