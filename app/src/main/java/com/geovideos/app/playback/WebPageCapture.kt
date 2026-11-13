package com.geovideos.app.playback

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Pide el video como lo hace un navegador de verdad (igual que Muka/Daylitube):
 * abre m.youtube.com/watch?v=ID en una página ESCONDIDA con tu sesión, deja que el propio
 * código de YouTube haga la petición (pasa el control de "no eres un robot") y copia lo que
 * YouTube le responde: la lista de calidades y el código de seguridad (pot) que usa la página
 * para bajar el video. Con eso el reproductor propio de Geo Videos reproduce y descarga.
 */
internal object WebPageCapture {

    private const val CAPTURE_TIMEOUT_MS = 20_000L
    private const val POT_WAIT_MS = 7_000L
    private const val CACHE_TTL_MS = 3 * 60 * 60_000L

    private var appContext: Context? = null
    private val mutex = Mutex()
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    /** Enlaces de registro (historial de YouTube) que dio la página para cada video. */
    private val trackingByVideo = ConcurrentHashMap<String, String>()

    fun playbackTracking(videoId: String): JSONObject? =
        trackingByVideo[videoId]?.let { runCatching { JSONObject(it) }.getOrNull() }

    // Estado de la captura en curso (la página escondida atiende un video a la vez).
    @Volatile private var targetVideoId: String = ""
    @Volatile private var responseDeferred: CompletableDeferred<String>? = null
    /** id del flujo (parámetro "id=" de googlevideo) → pot que usó la página. */
    private val potsByStream = ConcurrentHashMap<String, String>()
    @Volatile private var anyPot: String = ""
    /** Último código de seguridad que sirvió: se reusa si la página no alcanza a dar uno nuevo. */
    @Volatile private var lastPot: String = ""
    /** Captura de precarga en curso: se cancela apenas tocas un video. */
    @Volatile private var backgroundJob: Job? = null

    /** Resumen de la última captura (para "Copiar diagnóstico"). */
    @Volatile
    var lastNote: String = ""

    private var webView: WebView? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        rememberedWorks = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_WORKS, false)
    }

    /** La página escondida ya funcionó antes (se recuerda al reabrir la app: ahorra segundos). */
    @Volatile
    var rememberedWorks: Boolean = false
        private set

    fun rememberWorks(works: Boolean) {
        if (rememberedWorks == works) return
        rememberedWorks = works
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_WORKS, works)?.apply()
    }

    /** Deja lista la página escondida al abrir la app, así el primer video no espera a crearla. */
    suspend fun prewarm() {
        val context = appContext ?: return
        if (mutex.isLocked) return
        withContext(Dispatchers.Main) {
            if (webView == null) {
                webView = createWebView(context).also { view ->
                    StreamUserAgents.pageUserAgent = view.settings.userAgentString.orEmpty()
                    view.loadUrl("about:blank")
                }
            }
        }
    }

    private const val PREFS = "geo_web_capture"
    private const val KEY_WORKS = "web_page_works"

    /** Videos cuyo enlace falló: la próxima vez se espera un código de seguridad nuevo. */
    private val needsFresh: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun invalidate(videoId: String) {
        cache.remove(videoId)
        needsFresh.add(videoId)
    }

    /** Enlaces que la propia página ya pidió (firma y "n" resueltos por YouTube): itag → enlace. */
    private val pageResolvedUrls = ConcurrentHashMap<Int, String>()

    /** Quita del enlace de la página los datos de "este pedacito" (rango, contador...). */
    private fun cleanPageUrl(raw: android.net.Uri): String {
        val skip = setOf("range", "rn", "rbuf", "alr", "ump", "srfvp", "cpn", "cver")
        val builder = raw.buildUpon().clearQuery()
        raw.queryParameterNames.filter { it !in skip }.forEach { name ->
            raw.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
        return builder.build().toString()
    }

    /**
     * streamingData lista para el reproductor (con el pot ya puesto en cada enlace).
     * Con [background] (precarga) solo trabaja si la página está libre y cede ante un video tocado.
     */
    suspend fun streamingData(videoId: String, background: Boolean = false): JSONObject? {
        if (videoId.isBlank()) return null
        cached(videoId)?.let { return it }
        val context = appContext ?: return null
        if (background) {
            if (mutex.isLocked) return null
        } else {
            backgroundJob?.cancel()
        }
        return mutex.withLock {
            cached(videoId)?.let { return@withLock it }
            if (background) backgroundJob = kotlin.coroutines.coroutineContext[Job]
            val startedAt = System.currentTimeMillis()
            try {
                val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { capture(context, videoId) }
                if (result != null) {
                    cache[videoId] = result.toString() to (System.currentTimeMillis() + CACHE_TTL_MS)
                } else if (!background) {
                    lastNote = "Página: sin respuesta útil en ${System.currentTimeMillis() - startedAt} ms\n"
                }
                result
            } finally {
                if (background) backgroundJob = null
                withContext(NonCancellable + Dispatchers.Main) { stopPage() }
            }
        }
    }

    private fun cached(videoId: String): JSONObject? {
        val entry = cache[videoId] ?: return null
        if (entry.second < System.currentTimeMillis()) {
            cache.remove(videoId)
            return null
        }
        return runCatching { JSONObject(entry.first) }.getOrNull()
    }

    private suspend fun capture(context: Context, videoId: String): JSONObject? {
        val deferred = CompletableDeferred<String>()
        potsByStream.clear()
        pageResolvedUrls.clear()
        anyPot = ""
        val fresh = needsFresh.remove(videoId)
        responseDeferred = deferred
        targetVideoId = videoId
        withContext(Dispatchers.Main) {
            val view = webView ?: createWebView(context).also { webView = it }
            StreamUserAgents.pageUserAgent = view.settings.userAgentString.orEmpty()
            view.loadUrl("https://m.youtube.com/watch?v=$videoId")
        }
        val text = deferred.await()
        val player = JSONObject(text)
        val playability = player.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status.isNotBlank() && !status.equals("OK", ignoreCase = true)) {
            val reason = playability?.optString("reason").orEmpty()
                .ifBlank { playability?.optJSONObject("errorScreen")?.toString()?.take(120).orEmpty() }
            throw IOException("Got error $status: \"$reason\"")
        }
        player.optJSONObject("playbackTracking")?.let { tracking ->
            if (trackingByVideo.size > 200) trackingByVideo.clear()
            trackingByVideo[videoId] = tracking.toString()
        }
        val data = player.optJSONObject("streamingData") ?: return null
        val streamIds = streamIdsOf(data)
        // Esperar a que la página empiece a bajar el video para copiar su código de seguridad.
        var pot = ""
        // Si ya hay un código que sirvió, no se hace esperar: solo un momento por uno nuevo.
        val potWait = if (lastPot.isNotBlank() && !fresh) 600L else POT_WAIT_MS
        val waited = withTimeoutOrNull(potWait) {
            while (true) {
                pot = streamIds.firstNotNullOfOrNull { potsByStream[it] }.orEmpty()
                if (pot.isNotBlank()) break
                delay(150L)
            }
            true
        }
        val potSource = when {
            pot.isNotBlank() -> "del video"
            anyPot.isNotBlank() -> "de la página"
            lastPot.isNotBlank() -> "reusado"
            else -> "ninguno"
        }
        if (pot.isBlank()) pot = anyPot.ifBlank { if (fresh) "" else lastPot }
        if (pot.isNotBlank()) lastPot = pot
        val usable = injectPot(data, pot)
        // Los formatos que la página ya pidió van con su enlace listo (no hace falta descifrarlos):
        // sirven de respaldo seguro si el descifrado propio falla.
        markPageResolved(usable)
        lastNote = "Página: respuesta OK, código de seguridad $potSource, " +
            "${usable.optJSONArray("adaptiveFormats")?.length() ?: 0} formatos\n"
        // Formatos sin enlace (solo para el modo SABR) no sirven para el reproductor propio.
        val hasLinks = listOf("formats", "adaptiveFormats").any { key ->
            val array = usable.optJSONArray(key) ?: return@any false
            (0 until array.length()).any { index ->
                val format = array.optJSONObject(index)
                format != null && (format.optString("url").isNotBlank() ||
                    format.optString("signatureCipher").isNotBlank() || format.optString("cipher").isNotBlank())
            }
        }
        if (!hasLinks && usable.optString("hlsManifestUrl").isBlank()) return null
        return usable
    }

    /** Agrega &pot=… a cada enlace y avisa qué User-Agent usar con esos enlaces. */
    private fun injectPot(data: JSONObject, pot: String): JSONObject {
        val copy = JSONObject(data.toString())
        for (key in listOf("formats", "adaptiveFormats")) {
            val array = copy.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                val format = array.optJSONObject(index) ?: continue
                val url = format.optString("url")
                if (url.isNotBlank()) {
                    StreamUserAgents.registerPageStream(url)
                    if (pot.isNotBlank() && !url.contains("&pot=")) format.put("url", "$url&pot=$pot")
                    continue
                }
                val cipherKey = if (format.optString("signatureCipher").isNotBlank()) "signatureCipher" else "cipher"
                val cipher = format.optString(cipherKey)
                if (cipher.isBlank()) continue
                val parts = cipher.split("&").map { part ->
                    val separator = part.indexOf('=')
                    if (separator <= 0 || part.substring(0, separator) != "url") return@map part
                    val inner = java.net.URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                    StreamUserAgents.registerPageStream(inner)
                    val withPot = if (pot.isNotBlank() && !inner.contains("&pot=")) "$inner&pot=$pot" else inner
                    "url=" + java.net.URLEncoder.encode(withPot, "UTF-8")
                }
                format.put(cipherKey, parts.joinToString("&"))
            }
        }
        return copy
    }

    private fun markPageResolved(data: JSONObject) {
        if (pageResolvedUrls.isEmpty()) return
        for (key in listOf("formats", "adaptiveFormats")) {
            val array = data.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                val format = array.optJSONObject(index) ?: continue
                val resolved = pageResolvedUrls[format.optInt("itag", -1)] ?: continue
                format.put("geoPageUrl", resolved)
            }
        }
    }

    private fun streamIdsOf(data: JSONObject): Set<String> {
        val ids = HashSet<String>()
        for (key in listOf("formats", "adaptiveFormats")) {
            val array = data.optJSONArray(key) ?: continue
            for (index in 0 until array.length()) {
                val format = array.optJSONObject(index) ?: continue
                val url = format.optString("url").ifBlank {
                    val cipher = format.optString("signatureCipher").ifBlank { format.optString("cipher") }
                    cipher.split("&").firstOrNull { it.startsWith("url=") }
                        ?.let { java.net.URLDecoder.decode(it.removePrefix("url="), "UTF-8") }
                        .orEmpty()
                }
                StreamUserAgents.streamId(url)?.let(ids::add)
            }
        }
        return ids
    }

    private fun stopPage() {
        targetVideoId = ""
        responseDeferred = null
        runCatching { webView?.loadUrl("about:blank") }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(context: Context): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadsImagesAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        // Tamaño de un teléfono aunque no se vea, para que la página funcione normal.
        measure(
            android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY)
        )
        layout(0, 0, 720, 1280)
        addJavascriptInterface(Bridge(), "GeoCapture")
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.orEmpty().contains("youtube.com")) view.evaluateJavascript(CAPTURE_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url.orEmpty().contains("youtube.com")) view.evaluateJavascript(CAPTURE_JS, null)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val host = url.host.orEmpty()
                if (host.endsWith("googlevideo.com") && url.path.orEmpty().contains("videoplayback")) {
                    val pot = url.getQueryParameter("pot").orEmpty()
                    if (request.method.equals("GET", ignoreCase = true) &&
                        url.getQueryParameter("sabr") == null
                    ) {
                        url.getQueryParameter("itag")?.toIntOrNull()?.let { itag ->
                            runCatching { cleanPageUrl(url) }.getOrNull()?.let { clean ->
                                StreamUserAgents.registerPageStream(clean)
                                pageResolvedUrls[itag] = clean
                            }
                        }
                    }
                    if (pot.isNotBlank()) {
                        anyPot = pot
                        url.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { potsByStream[it] = pot }
                        // Ya tenemos el código: la página escondida no necesita bajar el video.
                        return emptyResponse()
                    }
                    return null
                }
                // Imágenes y anuncios no hacen falta: la página carga más rápido y gasta menos datos.
                if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) return emptyResponse()
                val path = url.path.orEmpty()
                if (host.endsWith("youtube.com") && BLOCKED_PATHS.any { path.startsWith(it) }) return emptyResponse()
                return null
            }
        }
    }

    private val BLOCKED_HOSTS = listOf(
        "i.ytimg.com", "yt3.ggpht.com", "yt3.googleusercontent.com", "doubleclick.net",
        "googlesyndication.com", "googleadservices.com", "google-analytics.com", "googletagservices.com",
        "fonts.gstatic.com", "fonts.googleapis.com"
    )

    /** Pedidos de la página que no hacen falta para sacar el video (estadísticas, anuncios). */
    private val BLOCKED_PATHS = listOf(
        "/youtubei/v1/log_event", "/api/stats/qoe", "/api/stats/ads", "/generate_204",
        "/pagead/", "/ptracking", "/youtubei/v1/notification", "/youtubei/v1/guide"
    )

    private fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "application/octet-stream",
        null,
        204,
        "No Content",
        emptyMap(),
        java.io.ByteArrayInputStream(ByteArray(0))
    )

    private class Bridge {
        @JavascriptInterface
        fun onPlayer(text: String?) {
            val body = text ?: return
            val target = targetVideoId
            if (target.isBlank()) return
            val id = runCatching {
                JSONObject(body).optJSONObject("videoDetails")?.optString("videoId").orEmpty()
            }.getOrDefault("")
            if (id == target) responseDeferred?.complete(body)
        }
    }

    /** Silencia la página escondida y copia la respuesta del reproductor de YouTube. */
    private const val CAPTURE_JS = """
(function(){
  if (window.__geoCap) return; window.__geoCap = 1;
  var sent = '';
  function mute(){ try { document.querySelectorAll('video').forEach(function(v){ v.muted = true; v.volume = 0; }); } catch(e){} }
  function send(r){
    try {
      if (!r || !r.videoDetails || !r.playabilityStatus) return;
      var id = r.videoDetails.videoId || '';
      var ok = r.playabilityStatus.status === 'OK';
      if (ok && !r.streamingData) return;
      if (sent === id + ok) return;
      sent = id + ok;
      GeoCapture.onPlayer(JSON.stringify({ videoDetails: { videoId: id }, playabilityStatus: r.playabilityStatus, streamingData: r.streamingData || null, playbackTracking: r.playbackTracking || null }));
    } catch(e){}
  }
  try {
    var of = window.fetch;
    if (of) window.fetch = function(input, init){
      var u = (typeof input === 'string') ? input : ((input && input.url) || '');
      var p = of.apply(this, arguments);
      if (String(u).indexOf('/youtubei/v1/player') >= 0) {
        p.then(function(res){ res.clone().json().then(send).catch(function(){}); }).catch(function(){});
      }
      return p;
    };
    var oo = XMLHttpRequest.prototype.open, os = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(m, u){ this.__geoU = u; return oo.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function(){
      var x = this;
      if (String(x.__geoU || '').indexOf('/youtubei/v1/player') >= 0) {
        x.addEventListener('load', function(){ try { send(JSON.parse(x.responseText)); } catch(e){} });
      }
      return os.apply(this, arguments);
    };
  } catch(e){}
  var n = 0;
  var t = setInterval(function(){
    n++; mute();
    try {
      var p = document.getElementById('movie_player');
      var r = (p && p.getPlayerResponse && p.getPlayerResponse()) || window.ytInitialPlayerResponse;
      send(r);
      var v = document.querySelector('video');
      if (v && v.paused) { v.muted = true; var pr = v.play(); if (pr && pr.catch) pr.catch(function(){}); }
    } catch(e){}
    if (n > 60) clearInterval(t);
  }, 250);
})();
"""
}
