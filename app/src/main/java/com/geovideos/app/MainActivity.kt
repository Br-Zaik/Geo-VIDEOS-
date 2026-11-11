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
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.FloatingPlayerService
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
            }
        }

        handlePlaybackIntent(intent)

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
            requestFloatingPlayerPermission()
            return false
        }
        pendingFloatingVideo = null
        FloatingPlayerService.start(this, video, dataSaver)
        return true
    }

    override fun onResume() {
        super.onResume()
        val pending = pendingFloatingVideo ?: return
        if (canDrawFloatingPlayer()) {
            pendingFloatingVideo = null
            FloatingPlayerService.start(this, pending, pendingFloatingDataSaver)
        }
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
    }
}
