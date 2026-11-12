package com.geovideos.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.geovideos.app.playback.GeoPlayerConnection

/**
 * Reproductor web de YouTube dentro de la app (como las apps tipo Daylitube/Litube).
 * Es el último recurso: usa la misma página de YouTube con tu cuenta, así que funciona
 * aunque YouTube rechace todas las otras formas de reproducir.
 */
class WebPlayerActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var root: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (videoId.isBlank()) {
            finish()
            return
        }
        val startSeconds = intent.getLongExtra(EXTRA_START_SECONDS, 0L).coerceAtLeast(0L)
        // Que no suenen dos reproductores a la vez.
        runCatching { GeoPlayerConnection.get(applicationContext).pause() }

        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty()
                    val allowed = host.endsWith("youtube.com") || host.endsWith("google.com") ||
                        host.endsWith("youtu.be") || host.endsWith("gstatic.com")
                    return !allowed
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    // El video sigue sonando si apagas la pantalla o sales un momento.
                    view.evaluateJavascript(KEEP_PLAYING_JS, null)
                }
            }
            webChromeClient = chromeClient(container)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        webView = view
        root = container
        setContentView(container)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView?.invoke()
                    webView?.canGoBack() == true -> webView?.goBack()
                    else -> finish()
                }
            }
        })

        val time = if (startSeconds > 0L) "&t=${startSeconds}s" else ""
        view.loadUrl("https://m.youtube.com/watch?v=$videoId$time")
    }

    private var hideCustomView: (() -> Unit)? = null

    private fun chromeClient(container: FrameLayout): WebChromeClient = object : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView?.let { onHideCustomView() }
            customView = view
            customViewCallback = callback
            container.addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            setSystemBarsHidden(true)
        }

        override fun onHideCustomView() {
            customView?.let { container.removeView(it) }
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setSystemBarsHidden(false)
        }
    }.also { client -> hideCustomView = { client.onHideCustomView() } }

    private fun setSystemBarsHidden(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        super.onStop()
        // Sin llamar a webView.onPause(): así el audio sigue con la pantalla apagada.
        webView?.evaluateJavascript(KEEP_PLAYING_JS, null)
    }

    override fun onDestroy() {
        webView?.let { view ->
            root?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "com.geovideos.app.extra.WEB_VIDEO_ID"
        private const val EXTRA_START_SECONDS = "com.geovideos.app.extra.WEB_START_SECONDS"

        /** La página cree que siempre está a la vista (si no, YouTube pausa al salir). */
        private const val KEEP_PLAYING_JS =
            "(function(){try{" +
                "Object.defineProperty(document,'hidden',{get:function(){return false},configurable:true});" +
                "Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'},configurable:true});" +
                "if(!window.__geoKeep){window.__geoKeep=true;" +
                "document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);" +
                "window.addEventListener('blur',function(e){e.stopImmediatePropagation();},true);}" +
                "}catch(e){}})();"

        fun open(context: Context, videoId: String, startSeconds: Long = 0L) {
            val intent = Intent(context, WebPlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_START_SECONDS, startSeconds)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
