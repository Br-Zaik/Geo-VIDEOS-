package com.geovideos.app.ui

import android.app.Application
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovideos.app.data.GeoVideosRepository
import com.geovideos.app.data.UserSession
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainSection { HOME, SEARCH, LIBRARY, PROFILE }

data class GeoVideosUiState(
    val loading: Boolean = true,
    val hasAccount: Boolean = false,
    val session: UserSession? = null,
    val videos: List<VideoItem> = emptyList(),
    val customVideoIds: Set<String> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val historyIds: List<String> = emptyList(),
    val section: MainSection = MainSection.HOME,
    val selectedVideo: VideoItem? = null,
    val message: String? = null
)

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GeoVideosRepository(application)
    private val _uiState = MutableStateFlow(GeoVideosUiState())
    val uiState: StateFlow<GeoVideosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                val allVideos = BUILT_IN_VIDEOS + snapshot.customVideos
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        hasAccount = snapshot.hasAccount,
                        session = snapshot.session,
                        videos = allVideos,
                        customVideoIds = snapshot.customVideos.mapTo(mutableSetOf()) { it.id },
                        favoriteIds = snapshot.favoriteIds,
                        historyIds = snapshot.historyIds,
                        selectedVideo = current.selectedVideo?.let { selected ->
                            allVideos.firstOrNull { it.id == selected.id }
                        }
                    )
                }
            }
        }
    }

    fun createAccount(name: String, email: String, password: String) {
        when {
            name.trim().length < 2 -> showMessage("Escribe un nombre valido.")
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> showMessage("Escribe un correo valido.")
            password.length < 6 -> showMessage("La contrasena debe tener al menos 6 caracteres.")
            else -> viewModelScope.launch {
                repository.createAccount(name, email, password)
                showMessage("Cuenta creada en este dispositivo.")
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showMessage("Completa el correo y la contrasena.")
            return
        }

        viewModelScope.launch {
            val success = repository.login(email, password)
            if (!success) showMessage("Correo o contrasena incorrectos.")
        }
    }

    fun logout() {
        viewModelScope.launch {
            closePlayer()
            repository.logout()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            closePlayer()
            repository.deleteAccount()
        }
    }

    fun changeSection(section: MainSection) {
        _uiState.update { it.copy(section = section) }
    }

    fun play(video: VideoItem) {
        _uiState.update { it.copy(selectedVideo = video) }
        viewModelScope.launch { repository.registerWatch(video.id) }
    }

    fun closePlayer() {
        _uiState.update { it.copy(selectedVideo = null) }
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch { repository.toggleFavorite(videoId) }
    }

    fun addDirectVideo(title: String, creator: String, source: String) {
        val uri = runCatching { Uri.parse(source.trim()) }.getOrNull()
        when {
            title.trim().length < 2 -> showMessage("Escribe un titulo para el video.")
            uri == null || (uri.scheme != "https" && uri.scheme != "http") -> {
                showMessage("Usa un enlace directo http o https autorizado.")
            }
            else -> viewModelScope.launch {
                repository.addVideo(title, creator, source)
                showMessage("Video agregado a tu biblioteca.")
            }
        }
    }

    fun addLocalVideo(title: String, source: String) {
        if (!source.startsWith("content://")) {
            showMessage("No se pudo leer el archivo seleccionado.")
            return
        }
        viewModelScope.launch {
            repository.addVideo(title.ifBlank { "Video local" }, "En este dispositivo", source)
            showMessage("Video local agregado.")
        }
    }

    fun removeVideo(id: String) {
        if (id !in _uiState.value.customVideoIds) return
        viewModelScope.launch {
            repository.removeVideo(id)
            showMessage("Video eliminado de la biblioteca.")
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    companion object {
        private val BUILT_IN_VIDEOS = listOf(
            VideoItem(
                id = "demo-big-buck-bunny",
                title = "Big Buck Bunny",
                creator = "Blender Foundation - muestra",
                source = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                isBuiltIn = true
            ),
            VideoItem(
                id = "demo-elephants-dream",
                title = "Elephant Dream",
                creator = "Blender Foundation - muestra",
                source = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                isBuiltIn = true
            ),
            VideoItem(
                id = "demo-for-bigger-blazes",
                title = "For Bigger Blazes",
                creator = "Google Media Sample",
                source = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                isBuiltIn = true
            )
        )
    }
}
