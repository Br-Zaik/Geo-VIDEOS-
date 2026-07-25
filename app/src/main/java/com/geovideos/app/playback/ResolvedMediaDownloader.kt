package com.geovideos.app.playback

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.geovideos.app.data.VideoItem

internal fun enqueueResolvedMediaDownload(
    context: Context,
    video: VideoItem,
    option: DownloadStreamOption
): Long {
    val uri = runCatching { Uri.parse(option.uri) }.getOrNull() ?: return -1L
    if (uri.scheme != "http" && uri.scheme != "https") return -1L

    val safeTitle = video.title.ifBlank { "Geo Video" }
    val safeName = safeTitle
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifBlank { "GeoVideo" }
        .take(72)
    val suffix = option.height.takeIf { it > 0 }?.let { "_${it}p" }.orEmpty()
    val fileName = "$safeName$suffix.${option.extension}"

    val request = DownloadManager.Request(uri)
        .setTitle("${safeTitle} · ${option.label}")
        .setDescription("Descargando desde Geo Videos")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MOVIES,
            "GeoVideos/$fileName"
        )
    option.mimeType?.takeIf { it.isNotBlank() }?.let(request::setMimeType)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return runCatching { manager.enqueue(request) }.getOrDefault(-1L)
}
