package com.geovideos.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.geovideos.app.data.YouTubeSessionStore

/**
 * Abre la página real de inicio de sesión de Google/YouTube. Cuando YouTube ya tiene
 * la sesión abierta, guarda las cookies cifradas y vuelve a Geo Videos.
 * Geo Videos nunca ve ni guarda tu contraseña: la escribes en la página de Google.
 */
class YouTubeLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val hint = TextView(this).apply {
            text = "Inicia sesión con tu cuenta de YouTube. Geo Videos no guarda tu contraseña."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B1B24"))
            textSize = 14f
            gravity = Gravity.CENTER
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google rechaza a veces los navegadores integrados; se usa el agente normal
            // de Chrome sin la marca "wv".
            settings.userAgentString = settings.userAgentString
                .replace("; wv)", ")")
                .replace(" Version/4.0", "")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    captureSessionIfReady(url.orEmpty())
                }
            }
        }
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })

        if (savedInstanceState == null) {
            // Empezar limpio para que no se mezcle con una sesión anterior.
            cookieManager.removeAllCookies { webView.loadUrl(LOGIN_URL) }
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun captureSessionIfReady(url: String) {
        if (finished) return
        val host = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("")
        if (!host.endsWith("youtube.com")) return
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://www.youtube.com").orEmpty()
        if (!YouTubeSessionStore.hasAuthCookie(cookies)) return
        cookieManager.flush()
        YouTubeSessionStore(this).save(cookies)
        finished = true
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
                "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
                "%26app%3Ddesktop%26hl%3Des-419%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"
    }
}
