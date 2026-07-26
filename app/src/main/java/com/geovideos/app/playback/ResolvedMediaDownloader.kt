package com.geovideos.app.playback

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

internal fun enqueueResolvedMediaDownload(
    context: Context,
    video: VideoItem,
    option: DownloadStreamOption,
    relativeFolder: String = "GeoVideos",
    onCompleted: (downloadId: Long, localUri: String) -> Unit = { _, _ -> },
    onFailed: (message: String) -> Unit = {}
): Long = MediaDownloadQueue.enqueue(context, video, option, relativeFolder, onCompleted, onFailed)

private object MediaDownloadQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicLong(System.currentTimeMillis().coerceAtLeast(1L))
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(
        context: Context,
        video: VideoItem,
        option: DownloadStreamOption,
        relativeFolder: String,
        onCompleted: (Long, String) -> Unit,
        onFailed: (String) -> Unit
    ): Long {
        val audioUrl = option.audioUri
        val id = ids.incrementAndGet()
        val appContext = context.applicationContext

        scope.launch {
            val jobDir = File(appContext.cacheDir, "geo_download_$id")
            val videoFile = File(jobDir, "video.${extensionFromMime(option.mimeType, option.extension)}")
            val audioFile = File(jobDir, "audio.${extensionFromMime(option.audioMimeType, option.audioExtension ?: "m4a")}")
            val outputExtension = if (option.extension.equals("webm", true)) "webm" else "mp4"
            val muxedFile = File(jobDir, "result.$outputExtension")

            try {
                if (!jobDir.mkdirs() && !jobDir.isDirectory) error("No se pudo preparar la descarga.")
                downloadToFile(option.uri, videoFile)
                val completedFile = if (audioUrl.isNullOrBlank()) {
                    videoFile
                } else {
                    downloadToFile(audioUrl, audioFile)
                    muxFiles(videoFile, audioFile, muxedFile, outputExtension)
                    muxedFile
                }
                val finalUri = publishVideo(
                    appContext, video, option, completedFile, outputExtension, relativeFolder
                )
                mainHandler.post { onCompleted(id, finalUri) }
            } catch (error: Exception) {
                val message = when {
                    error.message?.contains("403") == true ->
                        "La fuente rechazó la descarga. Vuelve a abrir el video e inténtalo otra vez."
                    error.message?.contains("mux", ignoreCase = true) == true ->
                        "Esa combinación de video y audio no pudo unirse en este teléfono."
                    else -> "No se pudo completar la descarga en ${option.height}p."
                }
                mainHandler.post { onFailed(message) }
            } finally {
                jobDir.deleteRecursively()
            }
        }
        return id
    }

    private fun downloadToFile(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 25_000
            readTimeout = 45_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Referer", "https://www.youtube.com/")
        }
        try {
            val response = connection.responseCode
            if (response !in 200..299) error("HTTP $response")
            connection.inputStream.buffered().use { input ->
                FileOutputStream(target).buffered().use { output -> input.copyTo(output, DOWNLOAD_BUFFER_SIZE) }
            }
            if (!target.exists() || target.length() <= 0L) error("Archivo vacío")
        } finally {
            connection.disconnect()
        }
    }

    private fun muxFiles(videoFile: File, audioFile: File, output: File, extension: String) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) error("Mux tracks missing")

            val outputFormat = if (extension.equals("webm", true)) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }
            muxer = MediaMuxer(output.absolutePath, outputFormat)
            val muxVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()

            copyTrack(videoExtractor, videoTrack, muxer, muxVideoTrack)
            copyTrack(audioExtractor, audioTrack, muxer, muxAudioTrack)
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
        if (!output.exists() || output.length() <= 0L) error("Mux output empty")
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
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
        val buffer = ByteBuffer.allocateDirect(maxInput.coerceAtMost(8 * 1024 * 1024))
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
        video: VideoItem,
        option: DownloadStreamOption,
        source: File,
        extension: String,
        relativeFolder: String
    ): String {
        val title = video.title.ifBlank { "Geo Video" }
        val name = "${safeFileName(title)}_${option.height}p.$extension"
        val mime = if (extension.equals("webm", true)) "video/webm" else "video/mp4"
        val safeFolder = relativeFolder
            .split('/')
            .map { safeFolderSegment(it) }
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
        if (!directory.mkdirs() && !directory.isDirectory) {
            error("No se pudo crear Películas/$safeFolder")
        }
        val target = uniqueFile(directory, name)
        FileInputStream(source).buffered().use { input ->
            FileOutputStream(target).buffered().use { output -> input.copyTo(output, DOWNLOAD_BUFFER_SIZE) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(target).toString()
    }

    private fun uniqueFile(directory: File, preferredName: String): File {
        val direct = File(directory, preferredName)
        if (!direct.exists()) return direct
        val base = preferredName.substringBeforeLast('.')
        val ext = preferredName.substringAfterLast('.', "mp4")
        var index = 2
        while (true) {
            val candidate = File(directory, "${base}_$index.$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
}

private fun safeFileName(title: String): String = title
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .trim('_')
    .ifBlank { "GeoVideo" }
    .take(72)

private fun extensionFromMime(mimeType: String?, fallback: String): String = when {
    mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
    mimeType?.contains("mp4", ignoreCase = true) == true -> "mp4"
    mimeType?.contains("m4a", ignoreCase = true) == true -> "m4a"
    else -> fallback.ifBlank { "mp4" }
}


private fun safeFolderSegment(value: String): String = value
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .trim('_')
