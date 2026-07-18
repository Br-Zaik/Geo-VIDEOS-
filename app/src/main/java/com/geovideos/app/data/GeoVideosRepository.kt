package com.geovideos.app.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

private val Context.geoVideosDataStore by preferencesDataStore(name = "geo_videos_store")

class GeoVideosRepository(private val context: Context) {

    private object Keys {
        val accountEmail = stringPreferencesKey("account_email")
        val accountName = stringPreferencesKey("account_name")
        val passwordSalt = stringPreferencesKey("password_salt")
        val passwordHash = stringPreferencesKey("password_hash")
        val signedIn = booleanPreferencesKey("signed_in")
        val customVideos = stringPreferencesKey("custom_videos")
        val favorites = stringSetPreferencesKey("favorites")
        val history = stringPreferencesKey("history")
    }

    val snapshot: Flow<StoredSnapshot> = context.geoVideosDataStore.data.map { preferences ->
        val email = preferences[Keys.accountEmail].orEmpty()
        val name = preferences[Keys.accountName].orEmpty()
        val signedIn = preferences[Keys.signedIn] ?: false

        StoredSnapshot(
            hasAccount = email.isNotBlank(),
            session = if (signedIn && email.isNotBlank()) {
                UserSession(email = email, displayName = name.ifBlank { email.substringBefore('@') })
            } else {
                null
            },
            customVideos = decodeVideos(preferences[Keys.customVideos].orEmpty()),
            favoriteIds = preferences[Keys.favorites]?.toSet().orEmpty(),
            historyIds = decodeStringList(preferences[Keys.history].orEmpty())
        )
    }

    suspend fun createAccount(displayName: String, email: String, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltEncoded = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = hashPassword(password, salt)

        context.geoVideosDataStore.edit { preferences ->
            preferences[Keys.accountEmail] = email.trim().lowercase()
            preferences[Keys.accountName] = displayName.trim()
            preferences[Keys.passwordSalt] = saltEncoded
            preferences[Keys.passwordHash] = hash
            preferences[Keys.signedIn] = true
        }
    }

    suspend fun login(email: String, password: String): Boolean {
        val preferences = context.geoVideosDataStore.data.first()
        val savedEmail = preferences[Keys.accountEmail].orEmpty()
        val savedSalt = preferences[Keys.passwordSalt].orEmpty()
        val savedHash = preferences[Keys.passwordHash].orEmpty()

        if (savedEmail.isBlank() || savedSalt.isBlank() || savedHash.isBlank()) return false
        if (!savedEmail.equals(email.trim(), ignoreCase = true)) return false

        val salt = runCatching { Base64.decode(savedSalt, Base64.NO_WRAP) }.getOrNull() ?: return false
        val valid = constantTimeEquals(savedHash, hashPassword(password, salt))

        if (valid) {
            context.geoVideosDataStore.edit { it[Keys.signedIn] = true }
        }
        return valid
    }

    suspend fun logout() {
        context.geoVideosDataStore.edit { it[Keys.signedIn] = false }
    }

    suspend fun deleteAccount() {
        context.geoVideosDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun addVideo(title: String, creator: String, source: String) {
        context.geoVideosDataStore.edit { preferences ->
            val videos = decodeVideos(preferences[Keys.customVideos].orEmpty()).toMutableList()
            videos.add(
                0,
                VideoItem(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    creator = creator.trim().ifBlank { "Mi biblioteca" },
                    source = source.trim(),
                    isBuiltIn = false
                )
            )
            preferences[Keys.customVideos] = encodeVideos(videos.take(100))
        }
    }

    suspend fun removeVideo(id: String) {
        context.geoVideosDataStore.edit { preferences ->
            val videos = decodeVideos(preferences[Keys.customVideos].orEmpty())
                .filterNot { it.id == id }
            preferences[Keys.customVideos] = encodeVideos(videos)

            val favorites = preferences[Keys.favorites]?.toMutableSet() ?: mutableSetOf()
            favorites.remove(id)
            preferences[Keys.favorites] = favorites

            val history = decodeStringList(preferences[Keys.history].orEmpty())
                .filterNot { it == id }
            preferences[Keys.history] = encodeStringList(history)
        }
    }

    suspend fun toggleFavorite(id: String) {
        context.geoVideosDataStore.edit { preferences ->
            val favorites = preferences[Keys.favorites]?.toMutableSet() ?: mutableSetOf()
            if (!favorites.add(id)) favorites.remove(id)
            preferences[Keys.favorites] = favorites
        }
    }

    suspend fun registerWatch(id: String) {
        context.geoVideosDataStore.edit { preferences ->
            val history = decodeStringList(preferences[Keys.history].orEmpty()).toMutableList()
            history.remove(id)
            history.add(0, id)
            preferences[Keys.history] = encodeStringList(history.take(50))
        }
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val result = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    private fun encodeVideos(videos: List<VideoItem>): String {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject()
                    .put("id", video.id)
                    .put("title", video.title)
                    .put("creator", video.creator)
                    .put("source", video.source)
            )
        }
        return array.toString()
    }

    private fun decodeVideos(raw: String): List<VideoItem> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    VideoItem(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        creator = item.optString("creator", "Mi biblioteca"),
                        source = item.getString("source"),
                        isBuiltIn = false
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeStringList(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    private fun decodeStringList(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }.getOrDefault(emptyList())
}
