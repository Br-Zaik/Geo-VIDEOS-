package com.geovideos.app

import android.accounts.Account
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.geovideos.app.ui.GeoVideosApp
import com.geovideos.app.ui.GeoVideosViewModel
import com.geovideos.app.ui.theme.GeoVideosTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {
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
                    onConnectGoogle = ::connectGoogle,
                    onSwitchGoogleAccount = ::switchGoogleAccount
                )
            }
        }

        if (savedInstanceState == null) {
            window.decorView.postDelayed({ connectGoogle() }, 350)
        }
    }

    private fun connectGoogle() {
        viewModel.beginAuthorization()
        val scopes = requestedScopes()
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        viewModel.onAuthorizationFailure("Google no pudo abrir el selector de cuentas.", false)
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
                if (error is ApiException) reportAuthorizationError(error)
                else viewModel.onAuthorizationFailure(error.message ?: "No se pudo conectar Google.", false)
            }
    }


    private fun switchGoogleAccount(email: String) {
        if (email.isBlank()) {
            viewModel.disconnect()
            connectGoogle()
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
                connectGoogle()
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
            "Google rechazó la firma de la APK. Activa YouTube Data API v3 y registra el paquete y SHA-1 indicados en la pantalla."
        } else {
            error.message ?: "Google no autorizó el acceso. Código ${error.statusCode}."
        }
        viewModel.onAuthorizationFailure(message, likelySetupProblem)
    }
}
