package com.geovideos.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Etiqueta "Nuevo" como YouTube:
 * - Shorts: solo si se subió hace poco (menos de 24 h) o YouTube ya lo marca como nuevo.
 * - Videos: solo si YouTube lo marca.
 * - En cuanto lo abres, deja de decir "Nuevo".
 * La fecha de subida de cada Short se pide una sola vez y se guarda.
 */
object NewBadges {
    private const val PREFS = "geo_new_badges"
    private const val KEY_PUBLISHED = "published"
    private const val KEY_SEEN = "seen"
    private const val FRESH_MS = 24L * 60L * 60L * 1000L
    private const val RETRY_MS = 6L * 60L * 60L * 1000L
    private const val MAX_PUBLISHED = 800
    private const val MAX_SEEN = 600
    private const val MAX_PER_ROUND = 16

    private val published = ConcurrentHashMap<String, Long>()
    private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val gate = Semaphore(2)

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Devuelve la fecha de subida (milisegundos) de un video, o null si no se pudo saber. */
    @Volatile
    var fetcher: (suspend (String) -> Long?)? = null

    private val _version = MutableStateFlow(0)
    /** Cambia cada vez que una etiqueta "Nuevo" aparece o desaparece. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        runCatching {
            val saved = JSONObject(store.getString(KEY_PUBLISHED, "{}") ?: "{}")
            saved.keys().forEach { id -> published[id] = saved.optLong(id) }
        }
        runCatching {
            val saved = JSONArray(store.getString(KEY_SEEN, "[]") ?: "[]")
            for (index in 0 until saved.length()) saved.optString(index).takeIf { it.isNotBlank() }?.let(seen::add)
        }
    }

    /** "Nuevo" en un Short: subido hace poco (o YouTube lo marca) y todavía no lo abriste. */
    fun showOnShort(video: VideoItem): Boolean =
        video.id !in seen && (video.isNew || isRecent(video.id))

    /** "Nuevo" en un video normal: YouTube lo marca y todavía no lo abriste. */
    fun showOnVideo(video: VideoItem): Boolean = video.isNew && video.id !in seen

    private fun isRecent(id: String): Boolean {
        val uploadedAt = published[id] ?: return false
        if (uploadedAt <= 0L) return false
        val age = System.currentTimeMillis() - uploadedAt
        // Unos minutos "en el futuro" pasan si el reloj del celular está algo atrasado.
        return age in -10L * 60_000L..FRESH_MS
    }

    fun markSeen(id: String) {
        if (id.isBlank() || !seen.add(id)) return
        if (seen.size > MAX_SEEN) seen.take(seen.size - MAX_SEEN).forEach(seen::remove)
        prefs?.edit()?.putString(KEY_SEEN, JSONArray(seen.toList()).toString())?.apply()
        _version.value += 1
    }

    /** Pide (de a 2 a la vez) la fecha de subida de los Shorts que todavía no se conocen. */
    suspend fun resolve(ids: List<String>) {
        val fetch = fetcher ?: return
        val now = System.currentTimeMillis()
        val todo = ids.asSequence()
            .distinct()
            .filter { it.length == 11 && !published.containsKey(it) && it !in seen }
            .filter { (failedAt[it] ?: 0L) + RETRY_MS < now }
            .take(MAX_PER_ROUND)
            .filter { inFlight.add(it) }
            .toList()
        if (todo.isEmpty()) return
        var changed = false
        try {
            coroutineScope {
                todo.map { id ->
                    async(Dispatchers.IO) {
                        val uploadedAt = gate.withPermit {
                            try {
                                fetch(id)
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (uploadedAt != null && uploadedAt > 0L) {
                            published[id] = uploadedAt
                            changed = true
                            if (isRecent(id)) _version.value += 1
                        } else {
                            failedAt[id] = System.currentTimeMillis()
                        }
                    }
                }.awaitAll()
            }
        } finally {
            todo.forEach(inFlight::remove)
            if (changed) save()
        }
    }

    private fun save() {
        val store = prefs ?: return
        if (published.size > MAX_PUBLISHED) {
            // Lo más viejo nunca vuelve a ser "Nuevo": se puede olvidar sin problema.
            published.entries.sortedBy { it.value }
                .take(published.size - MAX_PUBLISHED)
                .forEach { published.remove(it.key) }
        }
        val json = JSONObject()
        published.forEach { (id, time) -> json.put(id, time) }
        store.edit().putString(KEY_PUBLISHED, json.toString()).apply()
    }
}
