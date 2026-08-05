package com.geovideos.app

import android.accounts.Account
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.FloatingPlayerService
import com.geovideos.app.ui.GeoVideosApp
import com.geovideos.app.ui.GeoVideosViewModel
import com.geovideos.app.ui.theme.GeoVideosTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {
    private val inPictureInPictureState = mutableStateOf(false)
    private val fullscreenRequestState = mutableIntStateOf(0)
    private var videoPictureInPictureEnabled = false
    private var pendingFloatingVideo: VideoItem? = null
    private var pendingFloatingDataSaver: Boolean = false

    private val viewModel: GeoVideosViewModel by viewModels()

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val data = activityResult.data
                ?: error("Google cerró el selector sin devolver una cuenta.")
            val result = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(data)
            viewModel.onAuthorizationSuccess(result.accessToken)
        } catch (error: ApiException) {
            reportAuthorizationError(error)
        } catch (error: Exception) {
            viewModel.onAuthorizationFailure(error.message ?: "No se pudo conectar Google.", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoVideosTheme {
                GeoVideosApp(
                    viewModel = viewModel,
                    onConnectGoogle = { requestGoogleAuthorization(allowResolution = true) },
                    onSwitchGoogleAccount = ::switchGoogleAccount,
                    isInPictureInPictureMode = inPictureInPictureState.value,
                    fullscreenRequestToken = fullscreenRequestState.intValue
                )
            }
        }

        handlePlaybackIntent(intent)

        if (savedInstanceState == null) {
            window.decorView.postDelayed(
                { requestGoogleAuthorization(allowResolution = false) },
                350
            )
        }
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

    private fun requestGoogleAuthorization(allowResolution: Boolean) {
        if (allowResolution) viewModel.beginAuthorization()
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes())
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (!allowResolution) {
                        viewModel.onSilentAuthorizationUnavailable()
                        return@addOnSuccessListener
                    }
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        viewModel.onAuthorizationFailure(
                            "Google no pudo abrir el selector de cuentas.",
                            false
                        )
                    } else {
                        authorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    viewModel.onAuthorizationSuccess(result.accessToken)
                }
            }
            .addOnFailureListener { error ->
                if (!allowResolution) {
                    viewModel.onSilentAuthorizationFailure(
                        error.message ?: "No se pudo renovar la sesión en segundo plano."
                    )
                } else if (error is ApiException) {
                    reportAuthorizationError(error)
                } else {
                    viewModel.onAuthorizationFailure(
                        error.message ?: "No se pudo conectar Google.",
                        false
                    )
                }
            }
    }

    private fun switchGoogleAccount(email: String) {
        if (email.isBlank()) {
            viewModel.disconnect()
            requestGoogleAuthorization(allowResolution = true)
            return
        }
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(email, "com.google"))
            .setScopes(requestedScopes())
            .build()
        Identity.getAuthorizationClient(this)
            .revokeAccess(request)
            .addOnCompleteListener {
                viewModel.disconnect()
                requestGoogleAuthorization(allowResolution = true)
            }
    }

    private fun requestedScopes(): List<Scope> = listOf(
        Scope("openid"),
        Scope("email"),
        Scope("profile"),
        Scope("https://www.googleapis.com/auth/userinfo.email"),
        Scope("https://www.googleapis.com/auth/userinfo.profile"),
        Scope("https://www.googleapis.com/auth/youtube.readonly")
    )

    private fun reportAuthorizationError(error: ApiException) {
        val likelySetupProblem = error.statusCode == 10 ||
            error.message.orEmpty().contains("DEVELOPER_ERROR", ignoreCase = true)
        val message = if (likelySetupProblem) {
            "Google rechazó la firma de la APK. Revisa el paquete y SHA-1 registrados."
        } else {
            error.message ?: "Google no autorizó el acceso. Código ${error.statusCode}."
        }
        viewModel.onAuthorizationFailure(message, likelySetupProblem)
    }

    companion object {
        const val EXTRA_OPEN_FULLSCREEN_PLAYER = "com.geovideos.app.extra.OPEN_FULLSCREEN_PLAYER"
    }
}
