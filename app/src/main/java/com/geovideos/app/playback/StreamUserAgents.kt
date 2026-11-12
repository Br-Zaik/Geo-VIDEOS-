package com.geovideos.app.playback

/**
 * Cada enlace de YouTube trae en "c=" el cliente que lo pidió (VISIONOS, ANDROID, TVHTML5...).
 * Al bajar el video hay que presentarse con ese MISMO cliente: si no, googlevideo responde
 * 403 y el video se queda cargando. NewPipeExtractor 0.27.1 usa VISIONOS, y Geo Videos lo
 * bajaba con un User-Agent de Chrome: por eso desde la v54 casi nada reproducía.
 */
internal object StreamUserAgents {
    const val DEFAULT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // Igual que YoutubeParsingHelper.getVisionOsUserAgent de NewPipeExtractor 0.27.1.
    private const val VISIONOS =
        "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; PE)"
    private const val ANDROID = "com.google.android.youtube/21.03.36 (Linux; U; Android 15; PE) gzip"
    private const val TV =
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1"
    private const val SAFARI =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"

    private val clientParam = Regex("[?&/]c[=/]([A-Z0-9_]+)")
    private val idParam = Regex("[?&]id=([^&]+)")

    /** User-Agent de la página escondida de YouTube (WebPageCapture). */
    @Volatile
    var pageUserAgent: String = ""

    /** Flujos que entregó la página escondida: se bajan con el mismo User-Agent de esa página. */
    private val pageStreams = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun streamId(url: String): String? = idParam.find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    fun registerPageStream(url: String) {
        val id = streamId(url) ?: return
        if (pageStreams.size > 400) pageStreams.clear()
        pageStreams[id] = System.currentTimeMillis()
    }

    fun forUrl(url: String): String {
        if (!url.contains("googlevideo.com") && !url.contains("youtube.com/api/manifest")) return DEFAULT
        val page = pageUserAgent
        if (page.isNotBlank()) {
            val id = streamId(url)
            if (id != null && pageStreams.containsKey(id)) return page
        }
        return when (clientParam.find(url)?.groupValues?.getOrNull(1)) {
            "VISIONOS" -> VISIONOS
            "ANDROID", "ANDROID_VR" -> ANDROID
            "TVHTML5" -> TV
            "WEB", "WEB_EMBEDDED_PLAYER", "WEB_SAFARI" -> SAFARI
            else -> DEFAULT
        }
    }
}
