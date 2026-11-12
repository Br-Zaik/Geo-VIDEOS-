package com.zeta.fondo.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/** v44 · Lecturas del teléfono para Diagnóstico. Lo que no exista devuelve null. */
object DeviceInfo {
    fun ramAvailMb(ctx: Context): Long? = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.availMem / (1024 * 1024)
    } catch (_: Throwable) { null }

    fun ramTotalMb(ctx: Context): Long? = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / (1024 * 1024)
    } catch (_: Throwable) { null }

    /** Memoria Java usada por la app ahora mismo (MB). */
    fun heapUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }

    fun heapMaxMb(): Long = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    /** Estado térmico; null si no se puede leer. */
    fun thermalStatus(ctx: Context): Int? = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
    } catch (_: Throwable) { null }

    fun header(ctx: Context, gpu: String, appVersion: String): List<Pair<String, String>> = listOf(
        "Versión de la app" to appVersion,
        "Teléfono" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "GPU" to gpu.ifBlank { "No disponible" },
        "RAM total" to (ramTotalMb(ctx)?.let { "$it MB" } ?: "No disponible"),
        "Memoria máxima de la app" to "${heapMaxMb()} MB"
    )
}
