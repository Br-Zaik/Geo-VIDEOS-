package com.zeta.fondo

import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.zeta.fondo.ui.Dusk1
import com.zeta.fondo.ui.EditorScreen
import com.zeta.fondo.ui.FondoTheme
import com.zeta.fondo.ui.HomeScreen
import com.zeta.fondo.ui.Ink
import com.zeta.fondo.ui.Night
import com.zeta.fondo.ui.Peach
import kotlinx.coroutines.delay

/** v44.24 · Tope de la escala de letra del sistema dentro de la app. */
private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        // v44: registro local de errores y cierres (solo en el teléfono; nada se envía).
        com.zeta.fondo.diag.ZetaLog.init(filesDir)
        com.zeta.fondo.diag.ZetaLog.installCrashHandler()
        com.zeta.fondo.engine.FxBook.init(applicationContext.assets)   // v44.21
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // v44.24: con la letra del sistema muy grande los botones de un editor se montan; se
            // respeta hasta 1,15× (sigue creciendo un poco) y se mantiene el diseño en todo teléfono.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(MAX_FONT_SCALE))) {
                FondoTheme {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 15) com.zeta.fondo.diag.ZetaLog.warn("Memoria", "Android pide liberar memoria (nivel $level)")
        // v44.25: se suelta lo que se puede volver a crear; así Android cierra menos la app en
        // segundo plano y el teléfono va más suelto.
        vm.trimMemory(level)
    }

    override fun onStart() {
        super.onStart()
        vm.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        vm.appVisible = false
        vm.saveProject(forceCurrent = true)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: return
                vm.importImage(uri)
            }
            // v44.25: varias imágenes compartidas a la vez: la primera abre el proyecto y las
            // demás entran como capas encima (hasta el máximo del teléfono).
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.filterNotNull().orEmpty()
                if (uris.isNotEmpty()) vm.importImages(uris)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    // v44.24: barras del sistema + muesca/cámara (en horizontal la muesca queda a un lado y
    // tapaba botones; systemBarsPadding no la incluye).
    val insets = if (vm.fullscreen && vm.screen == Screen.EDITOR) Modifier
        else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .then(insets)
    ) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.EDITOR -> EditorScreen(vm)
        }

        vm.message?.let { msg ->
            // v37: aviso discreto bajo la barra superior. No intercepta toques (no es
            // clicable), así nunca bloquea herramientas, y desaparece solo.
            LaunchedEffect(msg) {
                delay(2600)
                if (vm.message == msg) vm.message = null
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp, start = 24.dp, end = 24.dp)
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dusk1.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(msg, color = Ink, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3)
            }
        }

        vm.busy?.let { text ->
            // v39: sin pantalla negra. Velo suave (la pantalla anterior sigue visible) y una
            // tarjeta pequeña con el progreso; solo bloquea toques para evitar dobles aperturas.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Night.copy(alpha = 0.35f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Dusk1.copy(alpha = 0.96f))
                    .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Peach, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = Ink, fontSize = 14.sp)
                }
            }
        }
    }
}
