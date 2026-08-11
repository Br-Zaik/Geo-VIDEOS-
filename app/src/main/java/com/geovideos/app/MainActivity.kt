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
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {
    private val inPictureInPictureState = mutableStateOf(false)
    private val fullscreenRequestState = mutableIntStateOf(0)
    private val expandPlayerRequestState = mutableIntStateOf(0)
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
            deliverGoogleAuthorizationResult(result, interactive = true)
        } catch (error: ApiException) {
            reportAuthorizationError(error)
        } catch (error: Exception) {
            viewModel.onAuthorizationFailure(error.message ?: "No se pudo conectar Google.", false)
        }
    }

    private val youtubeSyncAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        try {
            val data = activityResult.data
                ?: error("Google cerró la autorización de YouTube.")
            val result = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(data)
            viewModel.onYouTubeSyncAuthorizationSuccess(result.accessToken)
        } catch (error: Exception) {
            viewModel.onYouTubeSyncAuthorizationFailure(
                error.message ?: "No se pudo autorizar la sincronización con YouTube."
            )
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
                    onRequestYouTubeSync = { requestYouTubeSyncAuthorization(allowResolution = true) },
                    isInPictureInPictureMode = inPictureInPictureState.value,
                    fullscreenRequestToken = fullscreenRequestState.intValue,
                    expandPlayerRequestToken = expandPlayerRequestState.intValue
                )
            }
        }

        handlePlaybackIntent(intent)

        if (savedInstanceState == null) {
            window.decorView.postDelayed(
                {
                    // Solo renovar silenciosamente una sesion que Geo Videos ya guardo.
                    // En una instalacion/sesion limpia, esperar a que el usuario pulse
                    // "Continuar con Google" para no saltarse el selector oficial.
                    if (viewModel.uiState.value.profile != null) {
                        requestGoogleAuthorization(allowResolution = false)
                    }
                },
                900
            )
        }

        // La autorización de escritura de YouTube es independiente del inicio de sesión.
        // Solo se renueva silenciosamente si el usuario ya la había concedido antes.
        if (savedInstanceState == null) {
            window.decorView.postDelayed(
                {
                    val state = viewModel.uiState.value
                    // YouTube write-sync is non-critical at startup. Do not compete with the
                    // first Home frame or with a video the user already opened.
                    if (state.youtubeSyncEnabled && state.profile != null && state.selectedVideo == null) {
                        requestYouTubeSyncAuthorization(allowResolution = false)
                    }
                },
                2_500
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

    private fun deliverGoogleAuthorizationResult(
        result: AuthorizationResult,
        interactive: Boolean
    ) {
        val account = runCatching { result.toGoogleSignInAccount() }.getOrNull()
        viewModel.onAuthorizationSuccess(
            token = result.accessToken,
            selectedEmail = account?.email,
            selectedName = account?.displayName,
            selectedPhotoUrl = account?.photoUrl?.toString(),
            interactive = interactive
        )
    }

    private fun requestGoogleAuthorization(allowResolution: Boolean) {
        if (allowResolution) viewModel.beginAuthorization()
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes())

        if (allowResolution) {
            // En una acción explícita siempre se muestra el selector. No se fija
            // una cuenta anterior: la cuenta que el usuario toque es la candidata.
            requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else {
            // La renovación silenciosa queda ligada al correo ya verificado.
            // Así Google no puede renovar otra cuenta elegible del dispositivo.
            viewModel.connectedAccountEmail()
                .takeIf { it.isNotBlank() }
                ?.let { requestBuilder.setAccount(Account(it, "com.google")) }
        }

        val request = requestBuilder.build()

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
                    deliverGoogleAuthorizationResult(result, interactive = allowResolution)
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


    private fun requestYouTubeSyncAuthorization(allowResolution: Boolean) {
        if (allowResolution) viewModel.beginYouTubeSyncAuthorization()
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_FORCE_SSL_SCOPE)))
        // Las acciones de escritura deben autorizarse sobre la MISMA cuenta ya verificada.
        // Sin esto Google podía reutilizar otra cuenta elegible del teléfono y la sincronización
        // de suscripciones/Me gusta/listas quedaba desligada de la cuenta mostrada por Geo Videos.
        viewModel.connectedAccountEmail()
            .takeIf { it.isNotBlank() }
            ?.let { requestBuilder.setAccount(Account(it, "com.google")) }
        val request = requestBuilder.build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (!allowResolution) {
                        viewModel.onYouTubeSyncAuthorizationUnavailable()
                        return@addOnSuccessListener
                    }
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        viewModel.onYouTubeSyncAuthorizationFailure(
                            "Google no pudo abrir el permiso de sincronización con YouTube."
                        )
                    } else {
                        youtubeSyncAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    viewModel.onYouTubeSyncAuthorizationSuccess(result.accessToken)
                }
            }
            .addOnFailureListener { error ->
                if (!allowResolution) {
                    viewModel.onYouTubeSyncAuthorizationUnavailable()
                } else {
                    viewModel.onYouTubeSyncAuthorizationFailure(
                        error.message ?: "No se pudo autorizar la sincronización con YouTube."
                    )
                }
            }
    }

    private fun switchGoogleAccount(_email: String) {
        // Cambiar de cuenta no debe revocar los permisos OAuth de la cuenta anterior.
        // Solo se borra el vínculo local y se vuelve a abrir SELECT_ACCOUNT.
        viewModel.disconnect()
        requestGoogleAuthorization(allowResolution = true)
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
        const val EXTRA_EXPAND_PLAYER = "com.geovideos.app.extra.EXPAND_PLAYER"
        private const val YOUTUBE_FORCE_SSL_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
    }
}
