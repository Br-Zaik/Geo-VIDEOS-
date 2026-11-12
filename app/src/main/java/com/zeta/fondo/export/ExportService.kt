package com.zeta.fondo.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zeta.fondo.MainActivity
import com.zeta.fondo.R
import com.zeta.fondo.diag.ZetaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v44 · La exportación ya no depende de la pantalla del editor.
 *
 * - El trabajo corre en [ExportRunner.scope] (de la aplicación), no en el ViewModel: si el
 *   usuario sale a otra app, gira el teléfono o se cierra la actividad, sigue exportando.
 * - Mientras dura, [ExportService] es un servicio en primer plano con notificación de
 *   progreso y botón «Cancelar». Tipo «mediaProcessing» en Android 15+ y «dataSync» antes.
 * - Si Android no deja iniciar el servicio, la exportación continúa igual que en la v43
 *   (con la pantalla abierta) y se anota en el registro.
 * - EGL, MediaCodec y MediaMuxer se liberan en el finally de Exporter, como antes.
 */
object ExportRunner {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cancelFlag = AtomicBoolean(false)
    @Volatile var running = false
    @Volatile var progress = 0f

    fun cancel() { cancelFlag.set(true) }
}

class ExportService : Service() {

    override fun onBind(i: Intent): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        com.zeta.fondo.engine.FxBook.init(applicationContext.assets)   // v44.21 · por si el servicio arranca solo
        when (i?.action) {
            ACTION_CANCEL -> {
                ExportRunner.cancel()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopInForeground()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!ExportRunner.running) { stopSelf(); return START_NOT_STICKY }
        try {
            val type = if (Build.VERSION.SDK_INT >= 35) FGS_TYPE_MEDIA_PROCESSING else FGS_TYPE_DATA_SYNC
            ServiceCompat.startForeground(this, NOTIF_ID, progressNotification(this, ExportRunner.progress), type)
        } catch (t: Throwable) {
            ZetaLog.warn("Servicio", "No se pudo pasar la exportación a primer plano; sigue en la app", t)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15+: el sistema pide parar un servicio de procesamiento muy largo. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ZetaLog.warn("Servicio", "Android detuvo la exportación por tiempo (tipo $fgsType)")
        ExportRunner.cancel()
        stopInForeground()
        stopSelf()
    }

    override fun onTimeout(startId: Int) { onTimeout(startId, 0) }

    private fun stopInForeground() {
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
    }

    companion object {
        const val CHANNEL = "export"
        const val NOTIF_ID = 4401
        const val DONE_ID = 4402
        const val ACTION_CANCEL = "com.zeta.fondo.export.CANCEL"
        const val ACTION_STOP = "com.zeta.fondo.export.STOP"
        const val FGS_TYPE_DATA_SYNC = 1
        const val FGS_TYPE_MEDIA_PROCESSING = 0x2000

        fun start(ctx: Context) {
            try {
                ensureChannel(ctx)
                val i = Intent(ctx, ExportService::class.java)
                ctx.startForegroundService(i)
            } catch (t: Throwable) {
                ZetaLog.warn("Servicio", "No se pudo iniciar el servicio de exportación", t)
            }
        }

        fun stop(ctx: Context) {
            try { ctx.startService(Intent(ctx, ExportService::class.java).setAction(ACTION_STOP)) }
            catch (_: Throwable) { try { ctx.stopService(Intent(ctx, ExportService::class.java)) } catch (_: Throwable) { } }
        }

        /** Progreso en la notificación (como mucho cada 1 %, desde cualquier hilo). */
        fun update(ctx: Context, p: Float) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, progressNotification(ctx, p))
            } catch (_: Throwable) { }
        }

        /** Aviso final (guardado, fallo o cancelado); se puede tocar para volver a la app. */
        fun finished(ctx: Context, title: String, text: String) {
            try {
                ensureChannel(ctx)
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(DONE_ID, NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_export)
                    .setContentTitle(title).setContentText(text)
                    .setContentIntent(openApp(ctx)).setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW).build())
            } catch (_: Throwable) { }
        }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun progressNotification(ctx: Context, p: Float): Notification {
            val cancel = PendingIntent.getService(ctx, 1,
                Intent(ctx, ExportService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val pct = (p * 100f).toInt().coerceIn(0, 100)
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_export)
                .setContentTitle("Exportando vídeo")
                .setContentText("$pct %")
                .setProgress(100, pct, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp(ctx))
                .addAction(0, "Cancelar", cancel)
                .build()
        }

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL, "Exportación", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Progreso de la exportación de vídeo"
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }
}
