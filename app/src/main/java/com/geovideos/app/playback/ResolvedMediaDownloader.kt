package com.geovideos.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.geovideos.app.MainActivity
import com.geovideos.app.R
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.absoluteValue

internal enum class GeoDownloadStage {
    QUEUED,
    VIDEO,
    AUDIO,
    MERGING,
    PUBLISHING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

internal data class GeoDownloadStatus(
    val id: Long,
    val videoId: String,
    val title: String,
    val height: Int,
    val stage: GeoDownloadStage,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localUri: String? = null,
    val error: String? = null,
    val speedBytesPerSecond: Long = 0L,
    val remainingTimeMs: Long = -1L
) {
    val progress: Float?
        get() = totalBytes.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toFloat() / it.toFloat()).coerceIn(0f, 1f) }

    val isActive: Boolean
        get() = stage in setOf(
            GeoDownloadStage.QUEUED,
            GeoDownloadStage.VIDEO,
            GeoDownloadStage.AUDIO,
            GeoDownloadStage.MERGING,
            GeoDownloadStage.PUBLISHING
        )
}

private data class GeoDownloadRequest(
    val id: Long,
    val videoId: String,
    val title: String,
    val height: Int,
    val videoUrl: String,
    val videoMime: String?,
    val videoExtension: String,
    val audioUrl: String?,
    val audioMime: String?,
    val audioExtension: String?,
    val videoBytes: Long,
    val audioBytes: Long,
    val totalBytes: Long,
    val relativeFolder: String
)

internal fun enqueueResolvedMediaDownload(
    context: Context,
    video: VideoItem,
    option: DownloadStreamOption,
    relativeFolder: String = "GeoVideos"
): Long {
    val appContext = context.applicationContext
    GeoDownloadStore.findReusable(appContext, video.id, option.height)?.let { existing ->
        when (existing.stage) {
            GeoDownloadStage.PAUSED -> {
                resumeResolvedMediaDownload(appContext, existing.id)
                return existing.id
            }
            GeoDownloadStage.FAILED -> {
                File(appContext.filesDir, "geo_downloads/${existing.id.absoluteValue}").deleteRecursively()
                GeoDownloadStore.remove(appContext, existing.id)
            }
            else -> return existing.id
        }
    }

    val id = GeoDownloadStore.nextId()
    val total = option.estimatedSizeBytes.takeIf { it > 0L }
        ?: listOf(option.videoSizeBytes, option.audioSizeBytes)
            .filter { it > 0L }
            .sum()
            .takeIf { it > 0L }
        ?: -1L
    val request = GeoDownloadRequest(
        id = id,
        videoId = video.id,
        title = video.title.ifBlank { "Geo Video" },
        height = option.height,
        videoUrl = option.uri,
        videoMime = option.mimeType,
        videoExtension = option.extension,
        audioUrl = option.audioUri,
        audioMime = option.audioMimeType,
        audioExtension = option.audioExtension,
        videoBytes = option.videoSizeBytes,
        audioBytes = option.audioSizeBytes,
        totalBytes = total,
        relativeFolder = relativeFolder
    )
    GeoDownloadStore.saveRequest(appContext, request)
    GeoDownloadStore.saveStatus(
        appContext,
        GeoDownloadStatus(
            id = id,
            videoId = video.id,
            title = request.title,
            height = option.height,
            stage = GeoDownloadStage.QUEUED,
            bytesDownloaded = 0L,
            totalBytes = total
        )
    )
    ResolvedMediaDownloadService.send(appContext, ResolvedMediaDownloadService.ACTION_ENQUEUE, id)
    return id
}

internal fun queryResolvedMediaDownload(context: Context, id: Long): GeoDownloadStatus? =
    GeoDownloadStore.readStatus(context.applicationContext, id)

internal fun pauseResolvedMediaDownload(context: Context, id: Long) {
    ResolvedMediaDownloadService.send(context.applicationContext, ResolvedMediaDownloadService.ACTION_PAUSE, id)
}

internal fun resumeResolvedMediaDownload(context: Context, id: Long) {
    val appContext = context.applicationContext
    val current = GeoDownloadStore.readStatus(appContext, id) ?: return
    GeoDownloadStore.saveStatus(appContext, current.copy(stage = GeoDownloadStage.QUEUED, error = null))
    ResolvedMediaDownloadService.send(appContext, ResolvedMediaDownloadService.ACTION_ENQUEUE, id)
}

internal fun cancelResolvedMediaDownload(context: Context, id: Long, deletePublishedFile: Boolean = true) {
    val appContext = context.applicationContext
    if (deletePublishedFile) {
        GeoDownloadStore.readStatus(appContext, id)?.localUri?.let { uri ->
            runCatching { appContext.contentResolver.delete(Uri.parse(uri), null, null) }
        }
    }
    ResolvedMediaDownloadService.send(appContext, ResolvedMediaDownloadService.ACTION_CANCEL, id)
}

internal fun openResolvedMediaDownload(context: Context, id: Long): Boolean {
    val uri = GeoDownloadStore.readStatus(context.applicationContext, id)
        ?.localUri
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

class ResolvedMediaDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val controlStages = ConcurrentHashMap<Long, GeoDownloadStage>()
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        if (id == 0L) {
            val recoverable = GeoDownloadStore.recoverableIds(this)
            if (recoverable.isEmpty()) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            recoverable.forEach(::startDownload)
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_ENQUEUE -> startDownload(id)
            ACTION_PAUSE -> pauseDownload(id)
            ACTION_CANCEL -> cancelDownload(id)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        super.onDestroy()
    }

    private fun startDownload(id: Long) {
        if (jobs[id]?.isActive == true) return
        controlStages.remove(id)
        val request = GeoDownloadStore.readRequest(this, id) ?: return
        val current = GeoDownloadStore.readStatus(this, id)
            ?: GeoDownloadStatus(
                id,
                request.videoId,
                request.title,
                request.height,
                GeoDownloadStage.QUEUED,
                0L,
                request.totalBytes
            )
        startForeground(notificationId(id), buildNotification(current.copy(stage = GeoDownloadStage.QUEUED)))
        jobs[id] = scope.launch {
            runDownload(request)
        }.also { job ->
            job.invokeOnCompletion {
                jobs.remove(id)
                if (controlStages[id] == GeoDownloadStage.CANCELED) controlStages.remove(id)
                if (jobs.isEmpty()) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    private fun pauseDownload(id: Long) {
        val current = GeoDownloadStore.readStatus(this, id) ?: return
        controlStages[id] = GeoDownloadStage.PAUSED
        jobs.remove(id)?.cancel()
        val paused = current.copy(stage = GeoDownloadStage.PAUSED, error = null)
        GeoDownloadStore.saveStatus(this, paused)
        notificationManager.notify(notificationId(id), buildNotification(paused))
    }

    private fun cancelDownload(id: Long) {
        controlStages[id] = GeoDownloadStage.CANCELED
        val current = GeoDownloadStore.readStatus(this, id)
        if (current != null) {
            GeoDownloadStore.saveStatus(this, current.copy(stage = GeoDownloadStage.CANCELED, error = null))
        }
        jobs.remove(id)?.cancel()
        jobDirectory(id).deleteRecursively()
        GeoDownloadStore.remove(this, id)
        notificationManager.cancel(notificationId(id))
        if (jobs.isEmpty()) stopSelf()
    }

    private suspend fun runDownload(request: GeoDownloadRequest) {
        val wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoVideos:ResolvedDownload")
            .apply { acquire(MAX_WAKE_LOCK_MS) }
        val id = request.id
        val directory = jobDirectory(id)
        val videoFile = File(directory, "video.${safeExtension(request.videoExtension, "mp4")}")
        val audioFile = File(directory, "audio.${safeExtension(request.audioExtension, "m4a")}")
        val finalExtension = if (request.videoExtension.equals("webm", true)) "webm" else "mp4"
        val muxedFile = File(directory, "result.$finalExtension")
        try {
            if (!directory.mkdirs() && !directory.isDirectory) error("No se pudo preparar la descarga.")
            updateStatus(request, GeoDownloadStage.VIDEO, downloadedBytes(videoFile, audioFile))
            if (request.videoBytes <= 0L || videoFile.length() < request.videoBytes) {
                downloadResumable(request, request.videoUrl, videoFile, GeoDownloadStage.VIDEO, videoFile, audioFile)
            }

            val completedFile = if (request.audioUrl.isNullOrBlank()) {
                videoFile
            } else {
                updateStatus(request, GeoDownloadStage.AUDIO, downloadedBytes(videoFile, audioFile))
                if (request.audioBytes <= 0L || audioFile.length() < request.audioBytes) {
                    downloadResumable(request, request.audioUrl, audioFile, GeoDownloadStage.AUDIO, videoFile, audioFile)
                }
                updateStatus(request, GeoDownloadStage.MERGING, request.totalBytes.takeIf { it > 0L }
                    ?: downloadedBytes(videoFile, audioFile))
                muxFiles(videoFile, audioFile, muxedFile, finalExtension)
                muxedFile
            }

            updateStatus(request, GeoDownloadStage.PUBLISHING, request.totalBytes.takeIf { it > 0L }
                ?: downloadedBytes(videoFile, audioFile))
            val finalUri = publishVideo(this, request, completedFile, finalExtension)
            val completed = GeoDownloadStatus(
                id = id,
                videoId = request.videoId,
                title = request.title,
                height = request.height,
                stage = GeoDownloadStage.COMPLETED,
                bytesDownloaded = request.totalBytes.takeIf { it > 0L }
                    ?: downloadedBytes(videoFile, audioFile),
                totalBytes = request.totalBytes,
                localUri = finalUri
            )
            GeoDownloadStore.saveStatus(this, completed)
            notificationManager.notify(notificationId(id), buildNotification(completed))
            directory.deleteRecursively()
        } catch (cancelled: CancellationException) {
            val stage = GeoDownloadStore.readStatus(this, id)?.stage
            if (stage != GeoDownloadStage.PAUSED && stage != GeoDownloadStage.CANCELED) {
                markFailed(request, "La descarga fue interrumpida.")
            }
            throw cancelled
        } catch (error: Exception) {
            if (controlStages[id] == GeoDownloadStage.PAUSED ||
                controlStages[id] == GeoDownloadStage.CANCELED
            ) return
            val message = when {
                error.message?.contains("HTTP 403", true) == true ->
                    "El enlace temporal venció. Abre el video y vuelve a iniciar la descarga."
                error.message?.contains("track", true) == true ||
                    error.message?.contains("mux", true) == true ->
                    "El video y el audio no pudieron unirse en este dispositivo."
                else -> error.message?.takeIf { it.isNotBlank() }
                    ?: "No se pudo completar la descarga."
            }
            markFailed(request, message)
        } finally {
            if (wakeLock.isHeld) runCatching { wakeLock.release() }
        }
    }

    private suspend fun downloadResumable(
        request: GeoDownloadRequest,
        url: String,
        target: File,
        stage: GeoDownloadStage,
        videoFile: File,
        audioFile: File
    ) = withContext(Dispatchers.IO) {
        val existing = target.length().coerceAtLeast(0L)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 25_000
            readTimeout = 45_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val response = connection.responseCode
            if (response == 416 && existing > 0L) {
                return@withContext
            }
            if (response !in 200..299) error("HTTP $response")
            val append = existing > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (existing > 0L && !append) target.delete()
            var lastUpdate = System.currentTimeMillis()
            var sampleStartedAt = lastUpdate
            var sampleStartedBytes = downloadedBytes(videoFile, audioFile)
            connection.inputStream.buffered(DOWNLOAD_BUFFER_SIZE).use { input ->
                FileOutputStream(target, append).buffered(DOWNLOAD_BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= STATUS_UPDATE_INTERVAL_MS) {
                            lastUpdate = now
                            val currentBytes = downloadedBytes(videoFile, audioFile)
                            val elapsed = (now - sampleStartedAt).coerceAtLeast(1L)
                            val speed = ((currentBytes - sampleStartedBytes).coerceAtLeast(0L) * 1000L) / elapsed
                            val remaining = if (request.totalBytes > 0L && speed > 0L) {
                                ((request.totalBytes - currentBytes).coerceAtLeast(0L) * 1000L) / speed
                            } else {
                                -1L
                            }
                            updateStatus(request, stage, currentBytes, speed, remaining)
                            if (elapsed >= SPEED_SAMPLE_WINDOW_MS) {
                                sampleStartedAt = now
                                sampleStartedBytes = currentBytes
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (!target.exists() || target.length() <= 0L) error("El archivo descargado está vacío.")
            updateStatus(request, stage, downloadedBytes(videoFile, audioFile))
        } finally {
            connection.disconnect()
        }
    }

    private fun updateStatus(
        request: GeoDownloadRequest,
        stage: GeoDownloadStage,
        downloaded: Long,
        speedBytesPerSecond: Long = 0L,
        remainingTimeMs: Long = -1L
    ) {
        if (controlStages.containsKey(request.id)) return
        val current = GeoDownloadStatus(
            id = request.id,
            videoId = request.videoId,
            title = request.title,
            height = request.height,
            stage = stage,
            bytesDownloaded = downloaded,
            totalBytes = request.totalBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            remainingTimeMs = remainingTimeMs
        )
        GeoDownloadStore.saveStatus(this, current)
        notificationManager.notify(notificationId(request.id), buildNotification(current))
    }

    private fun markFailed(request: GeoDownloadRequest, message: String) {
        val current = GeoDownloadStore.readStatus(this, request.id)
        val status = GeoDownloadStatus(
            id = request.id,
            videoId = request.videoId,
            title = request.title,
            height = request.height,
            stage = GeoDownloadStage.FAILED,
            bytesDownloaded = current?.bytesDownloaded ?: 0L,
            totalBytes = request.totalBytes,
            error = message
        )
        GeoDownloadStore.saveStatus(this, status)
        notificationManager.notify(notificationId(request.id), buildNotification(status))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private fun buildNotification(status: GeoDownloadStatus): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = status.localUri
            ?.takeIf { status.stage == GeoDownloadStage.COMPLETED && it.isNotBlank() }
            ?.let { rawUri ->
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(rawUri), "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ?: openAppIntent
        val openApp = PendingIntent.getActivity(
            this,
            notificationId(status.id),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${status.title} · ${status.height}p")
            .setContentText(statusLabel(status))
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(status.isActive)
            .setAutoCancel(status.stage == GeoDownloadStage.COMPLETED)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        status.progress?.let { progress ->
            if (status.isActive) builder.setProgress(100, (progress * 100).toInt(), false)
        } ?: run {
            if (status.isActive) builder.setProgress(0, 0, true)
        }

        when {
            status.isActive -> {
                builder.addAction(
                    0,
                    "Pausar",
                    servicePendingIntent(ACTION_PAUSE, status.id, 11)
                )
                builder.addAction(
                    0,
                    "Cancelar",
                    servicePendingIntent(ACTION_CANCEL, status.id, 12)
                )
            }
            status.stage == GeoDownloadStage.PAUSED || status.stage == GeoDownloadStage.FAILED -> {
                builder.addAction(
                    0,
                    "Continuar",
                    servicePendingIntent(ACTION_ENQUEUE, status.id, 13)
                )
                builder.addAction(
                    0,
                    "Cancelar",
                    servicePendingIntent(ACTION_CANCEL, status.id, 14)
                )
            }
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, id: Long, salt: Int): PendingIntent {
        val intent = Intent(this, ResolvedMediaDownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, id)
        }
        val requestCode = notificationId(id) + salt
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action == ACTION_ENQUEUE) {
            PendingIntent.getForegroundService(this, requestCode, intent, pendingFlags)
        } else {
            PendingIntent.getService(this, requestCode, intent, pendingFlags)
        }
    }

    private fun statusLabel(status: GeoDownloadStatus): String {
        val percent = status.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
        val transfer = buildString {
            if (status.speedBytesPerSecond > 0L) append(" · ${formatRate(status.speedBytesPerSecond)}")
            if (status.remainingTimeMs > 0L) append(" · ${formatRemaining(status.remainingTimeMs)}")
        }
        return when (status.stage) {
            GeoDownloadStage.QUEUED -> "Preparando descarga"
            GeoDownloadStage.VIDEO -> "Descargando video$percent$transfer"
            GeoDownloadStage.AUDIO -> "Descargando audio$percent$transfer"
            GeoDownloadStage.MERGING -> "Uniendo video y audio"
            GeoDownloadStage.PUBLISHING -> "Guardando en Películas/GeoVideos"
            GeoDownloadStage.PAUSED -> "Descarga pausada$percent"
            GeoDownloadStage.COMPLETED -> "Descarga completada"
            GeoDownloadStage.FAILED -> status.error ?: "Falló la descarga"
            GeoDownloadStage.CANCELED -> "Descarga cancelada"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Descargas de Geo Videos",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progreso de videos descargados"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun jobDirectory(id: Long): File =
        File(filesDir, "geo_downloads/${id.absoluteValue}")

    companion object {
        internal const val ACTION_ENQUEUE = "com.geovideos.app.download.ENQUEUE"
        internal const val ACTION_PAUSE = "com.geovideos.app.download.PAUSE"
        internal const val ACTION_CANCEL = "com.geovideos.app.download.CANCEL"
        private const val EXTRA_ID = "download_id"
        private const val CHANNEL_ID = "geo_video_downloads"

        internal fun send(context: Context, action: String, id: Long) {
            val intent = Intent(context, ResolvedMediaDownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action == ACTION_ENQUEUE) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                runCatching { context.startService(intent) }
            }
        }
    }
}

private object GeoDownloadStore {
    private const val PREFS = "geo_resolved_downloads"
    private const val REQUEST_PREFIX = "request_"
    private const val STATUS_PREFIX = "status_"
    private val ids = AtomicLong(System.currentTimeMillis().coerceAtLeast(2L))

    fun nextId(): Long = -ids.incrementAndGet().absoluteValue.coerceAtLeast(2L)

    fun saveRequest(context: Context, request: GeoDownloadRequest) {
        prefs(context).edit().putString(REQUEST_PREFIX + request.id, request.toJson().toString()).apply()
    }

    fun readRequest(context: Context, id: Long): GeoDownloadRequest? =
        prefs(context).getString(REQUEST_PREFIX + id, null)
            ?.let { raw -> runCatching { requestFromJson(JSONObject(raw)) }.getOrNull() }

    fun saveStatus(context: Context, status: GeoDownloadStatus) {
        prefs(context).edit().putString(STATUS_PREFIX + status.id, status.toJson().toString()).apply()
    }

    fun readStatus(context: Context, id: Long): GeoDownloadStatus? =
        prefs(context).getString(STATUS_PREFIX + id, null)
            ?.let { raw -> runCatching { statusFromJson(JSONObject(raw)) }.getOrNull() }

    fun findReusable(context: Context, videoId: String, height: Int): GeoDownloadStatus? =
        prefs(context).all
            .asSequence()
            .filter { it.key.startsWith(STATUS_PREFIX) }
            .mapNotNull { (_, value) ->
                (value as? String)?.let { raw -> runCatching { statusFromJson(JSONObject(raw)) }.getOrNull() }
            }
            .filter { it.videoId == videoId && it.height == height }
            .filter { it.stage != GeoDownloadStage.CANCELED }
            .maxByOrNull { it.id.absoluteValue }

    fun recoverableIds(context: Context): List<Long> = prefs(context).all
        .asSequence()
        .filter { it.key.startsWith(STATUS_PREFIX) }
        .mapNotNull { (_, value) ->
            (value as? String)?.let { raw -> runCatching { statusFromJson(JSONObject(raw)) }.getOrNull() }
        }
        .filter { status ->
            status.stage in setOf(
                GeoDownloadStage.QUEUED,
                GeoDownloadStage.VIDEO,
                GeoDownloadStage.AUDIO,
                GeoDownloadStage.MERGING,
                GeoDownloadStage.PUBLISHING
            )
        }
        .map { it.id }
        .distinct()
        .toList()

    fun remove(context: Context, id: Long) {
        prefs(context).edit()
            .remove(REQUEST_PREFIX + id)
            .remove(STATUS_PREFIX + id)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun GeoDownloadRequest.toJson() = JSONObject()
        .put("id", id)
        .put("videoId", videoId)
        .put("title", title)
        .put("height", height)
        .put("videoUrl", videoUrl)
        .put("videoMime", videoMime)
        .put("videoExtension", videoExtension)
        .put("audioUrl", audioUrl)
        .put("audioMime", audioMime)
        .put("audioExtension", audioExtension)
        .put("videoBytes", videoBytes)
        .put("audioBytes", audioBytes)
        .put("totalBytes", totalBytes)
        .put("relativeFolder", relativeFolder)

    private fun requestFromJson(json: JSONObject) = GeoDownloadRequest(
        id = json.getLong("id"),
        videoId = json.optString("videoId"),
        title = json.optString("title", "Geo Video"),
        height = json.optInt("height"),
        videoUrl = json.getString("videoUrl"),
        videoMime = json.optNullableString("videoMime"),
        videoExtension = json.optString("videoExtension", "mp4"),
        audioUrl = json.optNullableString("audioUrl"),
        audioMime = json.optNullableString("audioMime"),
        audioExtension = json.optNullableString("audioExtension"),
        videoBytes = json.optLong("videoBytes", -1L),
        audioBytes = json.optLong("audioBytes", -1L),
        totalBytes = json.optLong("totalBytes", -1L),
        relativeFolder = json.optString("relativeFolder", "GeoVideos")
    )

    private fun GeoDownloadStatus.toJson() = JSONObject()
        .put("id", id)
        .put("videoId", videoId)
        .put("title", title)
        .put("height", height)
        .put("stage", stage.name)
        .put("bytesDownloaded", bytesDownloaded)
        .put("totalBytes", totalBytes)
        .put("localUri", localUri)
        .put("error", error)
        .put("speedBytesPerSecond", speedBytesPerSecond)
        .put("remainingTimeMs", remainingTimeMs)

    private fun statusFromJson(json: JSONObject) = GeoDownloadStatus(
        id = json.getLong("id"),
        videoId = json.optString("videoId"),
        title = json.optString("title", "Geo Video"),
        height = json.optInt("height"),
        stage = runCatching { GeoDownloadStage.valueOf(json.optString("stage")) }
            .getOrDefault(GeoDownloadStage.FAILED),
        bytesDownloaded = json.optLong("bytesDownloaded", 0L),
        totalBytes = json.optLong("totalBytes", -1L),
        localUri = json.optNullableString("localUri"),
        error = json.optNullableString("error"),
        speedBytesPerSecond = json.optLong("speedBytesPerSecond", 0L),
        remainingTimeMs = json.optLong("remainingTimeMs", -1L)
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
}

private fun downloadedBytes(videoFile: File, audioFile: File): Long =
    videoFile.length().coerceAtLeast(0L) + audioFile.length().coerceAtLeast(0L)

private fun safeExtension(value: String?, fallback: String): String =
    value.orEmpty().lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: fallback

private fun muxFiles(videoFile: File, audioFile: File, output: File, extension: String) {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var started = false
    try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)
        val videoTrack = findTrack(videoExtractor, "video/")
        val audioTrack = findTrack(audioExtractor, "audio/")
        if (videoTrack < 0 || audioTrack < 0) error("Mux tracks missing")

        val outputFormat = if (extension.equals("webm", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        } else {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
        muxer = MediaMuxer(output.absolutePath, outputFormat)
        val muxVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
        val muxAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
        muxer.start()
        started = true
        copyTrack(videoExtractor, videoTrack, muxer, muxVideoTrack)
        copyTrack(audioExtractor, audioTrack, muxer, muxAudioTrack)
    } finally {
        if (started) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { videoExtractor.release() }
        runCatching { audioExtractor.release() }
    }
    if (!output.exists() || output.length() <= 0L) error("Mux output empty")
}

private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
    for (index in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(index)
            .getString(android.media.MediaFormat.KEY_MIME)
            .orEmpty()
        if (mime.startsWith(prefix)) return index
    }
    return -1
}

private fun copyTrack(
    extractor: MediaExtractor,
    sourceTrack: Int,
    muxer: MediaMuxer,
    destinationTrack: Int
) {
    extractor.selectTrack(sourceTrack)
    val format = extractor.getTrackFormat(sourceTrack)
    val maxInput = if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
        format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
    } else {
        2 * 1024 * 1024
    }
    val buffer = ByteBuffer.allocateDirect(maxInput.coerceAtMost(12 * 1024 * 1024))
    val info = MediaCodec.BufferInfo()
    while (true) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
        info.flags = extractor.sampleFlags
        muxer.writeSampleData(destinationTrack, buffer, info)
        if (!extractor.advance()) break
    }
    extractor.unselectTrack(sourceTrack)
}

private fun publishVideo(
    context: Context,
    request: GeoDownloadRequest,
    source: File,
    extension: String
): String {
    val name = "${safeFileName(request.title)}_${request.height}p.$extension"
    val mime = if (extension.equals("webm", true)) "video/webm" else "video/mp4"
    val safeFolder = request.relativeFolder
        .split('/')
        .map(::safeFolderSegment)
        .filter { it.isNotBlank() }
        .joinToString("/")
        .ifBlank { "GeoVideos" }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$safeFolder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo crear el archivo final")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(source).buffered().use { input -> input.copyTo(output, DOWNLOAD_BUFFER_SIZE) }
            } ?: error("No se pudo escribir el archivo final")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    val directory = safeFolder.split('/').fold(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    ) { parent, segment -> File(parent, segment) }
    if (!directory.mkdirs() && !directory.isDirectory) error("No se pudo crear Películas/$safeFolder")
    val target = uniqueFile(directory, name)
    FileInputStream(source).buffered().use { input ->
        FileOutputStream(target).buffered().use { output -> input.copyTo(output, DOWNLOAD_BUFFER_SIZE) }
    }
    MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        target
    ).toString()
}

private fun safeFileName(value: String): String = value
    .replace(Regex("[\\/:*?\"<>|]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(90)
    .ifBlank { "Geo Video" }

private fun safeFolderSegment(value: String): String = value
    .replace(Regex("[^A-Za-z0-9 _.-]"), "_")
    .trim()
    .take(40)

private fun uniqueFile(directory: File, desiredName: String): File {
    val base = desiredName.substringBeforeLast('.', desiredName)
    val extension = desiredName.substringAfterLast('.', "mp4")
    var candidate = File(directory, desiredName)
    var index = 1
    while (candidate.exists()) {
        candidate = File(directory, "${base}_$index.$extension")
        index++
    }
    return candidate
}

private fun formatRate(bytesPerSecond: Long): String {
    val mb = bytesPerSecond.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(java.util.Locale.getDefault(), "%.1f MB/s", mb)
    } else {
        String.format(java.util.Locale.getDefault(), "%.0f KB/s", bytesPerSecond / 1024.0)
    }
}

private fun formatRemaining(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(1L)
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return if (minutes > 0L) "${minutes}m ${remainder}s restantes" else "${remainder}s restantes"
}

private fun notificationId(id: Long): Int =
    (id.absoluteValue % 1_000_000L).toInt().coerceAtLeast(2000)

private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024
private const val STATUS_UPDATE_INTERVAL_MS = 500L
private const val SPEED_SAMPLE_WINDOW_MS = 2_000L
private const val MAX_WAKE_LOCK_MS = 6L * 60L * 60L * 1000L
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
