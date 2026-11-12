package com.geovideos.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la sesión de YouTube (cookies del inicio de sesión dentro de la app).
 * Las cookies dan acceso completo a la cuenta, por eso se cifran con una clave del
 * Android Keystore que nunca sale del teléfono.
 */
class YouTubeSessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedCookies: String? = null

    fun save(cookies: String) {
        val clean = cookies.trim()
        if (clean.isBlank()) return
        val encrypted = runCatching { encrypt(clean) }.getOrNull() ?: return
        preferences.edit().putString(KEY_COOKIES, encrypted).apply()
        cachedCookies = clean
    }

    fun load(): String {
        cachedCookies?.let { return it }
        val stored = preferences.getString(KEY_COOKIES, null) ?: return ""
        val decrypted = runCatching { decrypt(stored) }.getOrNull().orEmpty()
        cachedCookies = decrypted
        return decrypted
    }

    fun isConnected(): Boolean = hasAuthCookie(load())

    /** Comprobación rápida sin descifrar (para el arranque). */
    fun hasStoredSession(): Boolean = preferences.contains(KEY_COOKIES)

    fun clear() {
        cachedCookies = ""
        preferences.edit().remove(KEY_COOKIES).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(stored: String): String {
        val separator = stored.indexOf(':')
        if (separator <= 0) return ""
        val iv = Base64.decode(stored.substring(0, separator), Base64.NO_WRAP)
        val body = Base64.decode(stored.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_NAME = "geo_youtube_session"
        private const val KEY_COOKIES = "cookies_v1"
        private const val KEY_ALIAS = "geo_youtube_session_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun hasAuthCookie(cookies: String): Boolean =
            cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")
    }
}
