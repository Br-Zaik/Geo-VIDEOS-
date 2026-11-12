package com.geovideos.app

import android.app.Activity
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.Player
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.FloatingPlayerService
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.ui.GeoVideosApp
import com.geovideos.app.ui.GeoVideosViewModel
import com.geovideos.app.ui.theme.GeoVideosTheme

class MainActivity : ComponentActivity() {
    private val inPictureInPictureState = mutableStateOf(false)
    private val fullscreenRequestState = mutableIntStateOf(0)
    private val expandPlayerRequestState = mutableIntStateOf(0)
    private var videoPictureInPictureEnabled = false
    private var pendingFloatingVideo: VideoItem? = null
    private var pendingFloatingDataSaver: Boolean = false

    // Ventana emergente automática (como Daylitube): se abre al salir con un video sonando
    // y se cierra sola al volver a la app. La que se abre con el botón se queda.
    private var autoFloatingActive = false
    private var suppressAutoFloatingUntilMs = 0L
    private val floatingPromptState = mutableStateOf(false)
    private var awaitingFloatingPermission = false

    private val viewModel: GeoVideosViewModel by viewModels()

    // Un solo inicio de sesión: la página real de YouTube dentro de la app.
    private val youtubeLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectWithYouTubeSession(interactive = true)
        } else {
            viewModel.onAuthorizationFailure("Se canceló el inicio de sesión de YouTube.", false)
        }
    }

    private fun openYouTubeLogin() {
        viewModel.beginAuthorization()
        youtubeLoginLauncher.launch(Intent(this, YouTubeLoginActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoVideosTheme {
                GeoVideosApp(
                    viewModel = viewModel,
                    onConnectGoogle = ::openYouTubeLogin,
                    onSwitchGoogleAccount = {
                        viewModel.disconnect()
                        openYouTubeLogin()
                    },
                    onRequestYouTubeSync = { viewModel.onYouTubeSessionSyncRequested() },
                    isInPictureInPictureMode = inPictureInPictureState.value,
                    fullscreenRequestToken = fullscreenRequestState.intValue,
                    expandPlayerRequestToken = expandPlayerRequestState.intValue
                )
                if (floatingPromptState.value) {
                    FloatingPermissionDialog(
                        onAllow = {
                            floatingPromptState.value = false
                            GeoPlayerConnection.get(applicationContext).setAutoFloatingOnLeave(true)
                            awaitingFloatingPermission = true
                            requestFloatingPlayerPermission()
                        },
                        onDismiss = {
                            floatingPromptState.value = false
                            pendingFloatingVideo = null
                        }
                    )
                }
            }
        }

        handlePlaybackIntent(intent)
        window.decorView.postDelayed({ askBatteryExemptionOnce() }, 4_000L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaybackIntent(intent)
    }

    private fun handlePlaybackIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_FULLSCREEN_PLAYER, false) == true) {
            fullscreenRequestState.intValue += 1
            intent.removeExtra(EXTRA_OPEN_FULLSCREEN_PLAYER)
        }
        if (intent?.getBooleanExtra(EXTRA_EXPAND_PLAYER, false) == true) {
            expandPlayerRequestState.intValue += 1
            intent.removeExtra(EXTRA_EXPAND_PLAYER)
        }
    }

    fun canDrawFloatingPlayer(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    fun requestFloatingPlayerPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val packageUri = Uri.parse("package:$packageName")
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }

    fun openFloatingPlayer(video: VideoItem, dataSaver: Boolean): Boolean {
        if (!canDrawFloatingPlayer()) {
            pendingFloatingVideo = video
            pendingFloatingDataSaver = dataSaver
            // Primero se explica para qué es el permiso; "Permitir" abre el ajuste de Android.
            floatingPromptState.value = true
            return false
        }
        pendingFloatingVideo = null
        autoFloatingActive = false
        FloatingPlayerService.start(this, video, dataSaver)
        return true
    }

    /**
     * Ofrece activar "Mostrar sobre otras apps". Sin [force] se pregunta una sola vez
     * (al ver el primer video); con [force] es porque el usuario activó la opción.
     */
    fun offerFloatingPermission(force: Boolean = false) {
        if (canDrawFloatingPlayer() || floatingPromptState.value) return
        val prefs = getSharedPreferences(BACKGROUND_PREFS, MODE_PRIVATE)
        if (!force) {
            if (prefs.getBoolean(KEY_FLOATING_OFFERED, false)) return
            if (!GeoPlayerConnection.get(applicationContext).autoFloatingOnLeave.value) return
        }
        prefs.edit().putBoolean(KEY_FLOATING_OFFERED, true).apply()
        floatingPromptState.value = true
    }

    /** Abrir otra pantalla desde la app (ajustes, compartir, enlaces) no cuenta como "salir". */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        suppressAutoFloatingUntilMs = SystemClock.elapsedRealtime() + 2_500L
        super.startActivityForResult(intent, requestCode, options)
    }

    /** Inicio o cambio de app con un video sonando: sigue en la ventana emergente. */
    private fun maybeStartAutoFloating(): Boolean {
        if (isInPictureInPictureMode || isFinishing || isChangingConfigurations) return false
        if (SystemClock.elapsedRealtime() < suppressAutoFloatingUntilMs) return false
        if (FloatingPlayerService.isActiveOrStarting()) return false
        val connection = GeoPlayerConnection.get(applicationContext)
        if (!connection.autoFloatingOnLeave.value || connection.audioOnlyMode.value) return false
        if (!canDrawFloatingPlayer()) return false
        val state = viewModel.uiState.value
        val selected = state.selectedVideo ?: return false
        val controller = connection.controller.value ?: return false
        val playing = controller.isPlaying ||
            (controller.playWhenReady && controller.playbackState == Player.STATE_BUFFERING)
        if (!playing) return false
        val position = controller.currentPosition.coerceAtLeast(0L)
        val item = controller.currentMediaItem
        val video = if (item != null && item.mediaId.isNotBlank() && item.mediaId != selected.id) {
            // La reproducción automática ya pasó al siguiente video.
            VideoItem(
                id = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                channelTitle = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
                resumePositionMs = position
            )
        } else {
            selected.copy(resumePositionMs = position)
        }
        return runCatching {
            FloatingPlayerService.start(this, video, state.dataSaver)
            autoFloatingActive = true
            true
        }.getOrDefault(false)
    }

    private fun closeAutoFloatingWindow() {
        if (!autoFloatingActive) return
        autoFloatingActive = false
        // Solo se cierra la ventana: el video sigue en la app sin cortarse.
        FloatingPlayerService.stop(this)
    }

    override fun onStart() {
        super.onStart()
        GeoPlayerConnection.get(applicationContext).setAppInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        // Pantalla apagada o app atrás: el audio sigue (como YouTube Premium / Daylitube).
        if (!isInPictureInPictureMode) {
            val connection = GeoPlayerConnection.get(applicationContext)
            val state = viewModel.uiState.value
            if (state.section == com.geovideos.app.ui.MainSection.SHORTS && state.selectedVideo == null) {
                // Los Shorts se repiten solos: al salir se pausan, como en YouTube.
                connection.pause()
            } else {
                connection.setAppInBackground(true)
            }
        }
    }

    /**
     * Una sola vez: pedir a Android que no corte la app con la pantalla apagada
     * (algunos teléfonos detienen la música/descargas para ahorrar batería).
     */
    private fun askBatteryExemptionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val prefs = getSharedPreferences(BACKGROUND_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("battery_asked", false)) return
        val power = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("battery_asked", true).apply()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        closeAutoFloatingWindow()
        val granted = canDrawFloatingPlayer()
        val pending = pendingFloatingVideo
        if (pending != null && granted) {
            pendingFloatingVideo = null
            FloatingPlayerService.start(this, pending, pendingFloatingDataSaver)
            if (awaitingFloatingPermission) viewModel.showMessage("Ventana emergente activa.")
        } else if (awaitingFloatingPermission && granted) {
            viewModel.showMessage("Listo: al salir de la app, el video seguirá en una ventana emergente.")
        }
        awaitingFloatingPermission = false
    }

    fun setVideoPictureInPictureEnabled(enabled: Boolean) {
        videoPictureInPictureEnabled = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(enabled)
                    .setSeamlessResizeEnabled(true)
                    .build()
            )
        }
    }

    fun canUseVideoPictureInPicture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appOps = getSystemService(AppOpsManager::class.java) ?: return true
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }

    fun openVideoPictureInPictureSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val packageUri = Uri.parse("package:$packageName")
        runCatching {
            startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", packageUri))
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }

    fun enterVideoPictureInPicture(): Boolean {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            isInPictureInPictureMode ||
            !canUseVideoPictureInPicture()
        ) return false
        return runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(videoPictureInPictureEnabled)
                    .setSeamlessResizeEnabled(true)
            }
            enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (maybeStartAutoFloating()) return
        if (
            videoPictureInPictureEnabled &&
            canUseVideoPictureInPicture() &&
            Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S
        ) {
            enterVideoPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPictureState.value = isInPictureInPictureMode
    }

    companion object {
        const val EXTRA_OPEN_FULLSCREEN_PLAYER = "com.geovideos.app.extra.OPEN_FULLSCREEN_PLAYER"
        const val EXTRA_EXPAND_PLAYER = "com.geovideos.app.extra.EXPAND_PLAYER"
        private const val BACKGROUND_PREFS = "geo_background"
        private const val KEY_FLOATING_OFFERED = "floating_offered"
    }
}

@Composable
private fun FloatingPermissionDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null) },
        title = { Text("Ventana emergente") },
        text = {
            Text(
                "Para que el video siga en una ventanita encima de otras apps cuando salgas, " +
                    "activa \"Mostrar sobre otras apps\" para Geo Videos. Luego vuelve con atrás y ya queda listo."
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Permitir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } }
    )
}
