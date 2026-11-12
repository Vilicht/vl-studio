package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioMixerController
import com.example.audio.StreamAudioParams
import com.example.data.db.StudioDatabase
import com.example.data.model.AudioSettingsEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.data.model.StreamProfileEntity
import com.example.data.repository.StudioRepository
import com.example.encoder.AutoDetectionResult
import com.example.encoder.DeviceCapabilityDetector
import com.example.encoder.StreamKeyStorage
import com.example.encoder.StreamPreset
import com.example.service.LiveStudioState
import com.example.service.StreamingService
import com.example.service.StreamingStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StudioDatabase.getInstance(application)
    private val repository = StudioRepository(
        db.sceneDao(),
        db.sourceDao(),
        db.streamProfileDao(),
        db.audioSettingsDao()
    )

    val audioMixer = AudioMixerController(application)
    val autoDetection: AutoDetectionResult = DeviceCapabilityDetector.detectOptimalPreset(application)

    val allScenes: StateFlow<List<SceneEntity>> = repository.allScenes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedScene: StateFlow<SceneEntity?> = repository.selectedScene
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedSourceId = MutableStateFlow<String?>(null)
    val selectedSourceId: StateFlow<String?> = _selectedSourceId.asStateFlow()

    val currentSources: StateFlow<List<SourceEntity>> = selectedScene
        .flatMapLatest { scene ->
            if (scene != null) repository.getSourcesForScene(scene.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedSource: StateFlow<SourceEntity?> = combine(currentSources, _selectedSourceId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allProfiles: StateFlow<List<StreamProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedProfile: StateFlow<StreamProfileEntity?> = repository.selectedProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val audioSettings: StateFlow<AudioSettingsEntity?> = repository.audioSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val liveState: StateFlow<LiveStudioState> = StreamingService.studioState

    // UI dialog states
    val isSettingsOpen = MutableStateFlow(false)
    val isDiagnosticsOpen = MutableStateFlow(false)
    val isAddSourceOpen = MutableStateFlow(false)
    val isAddSceneOpen = MutableStateFlow(false)
    val isEditProfileOpen = MutableStateFlow(false)

    // Active preset
    val currentPreset = MutableStateFlow(autoDetection.selectedPreset)

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
            audioMixer.startMonitoring()
        }
        // Posisi layer game di editor -> mesin siaran (berlaku langsung di siaran)
        viewModelScope.launch {
            combine(currentSources, liveState) { srcs, live ->
                Triple(
                    srcs.firstOrNull { it.type == SourceType.SCREEN && it.isVisible },
                    srcs.firstOrNull { it.type == SourceType.CAMERA && it.isVisible },
                    live.status
                )
            }.collect { (game, cam, status) ->
                val active = status == StreamingStatus.PREVIEW ||
                    status == StreamingStatus.LIVE || status == StreamingStatus.PAUSED ||
                    status == StreamingStatus.CONNECTING || status == StreamingStatus.RECONNECTING
                if (active) {
                    val svc = StreamingService.activeServiceInstance
                    if (game != null) svc?.applyGameRect(game.x, game.y, game.width, game.height)
                    if (cam != null) svc?.applyWebcam(true, cam.x, cam.y, cam.width, cam.height)
                    else svc?.applyWebcam(false, 0f, 0f, 0f, 0f)
                }
            }
        }
    }

    fun selectScene(scene: SceneEntity) {
        viewModelScope.launch {
            repository.selectScene(scene.id)
            _selectedSourceId.value = null
        }
    }

    fun createScene(name: String, isPortrait: Boolean = false) {
        viewModelScope.launch {
            val scene = repository.createScene(name, isPortrait)
            repository.selectScene(scene.id)
        }
    }

    fun duplicateScene(scene: SceneEntity) {
        viewModelScope.launch {
            val duplicated = repository.duplicateScene(scene)
            repository.selectScene(duplicated.id)
        }
    }

    fun deleteScene(scene: SceneEntity) {
        viewModelScope.launch {
            repository.deleteScene(scene.id)
            val remaining = allScenes.value.filter { it.id != scene.id }
            remaining.firstOrNull()?.let { repository.selectScene(it.id) }
        }
    }

    fun toggleCanvasOrientation() {
        val scene = selectedScene.value ?: return
        val newPortrait = !scene.isPortrait
        viewModelScope.launch {
            repository.updateScene(
                scene.copy(
                    isPortrait = newPortrait,
                    canvasWidth = if (newPortrait) 720 else 1280,
                    canvasHeight = if (newPortrait) 1280 else 720
                )
            )
        }
    }

    fun selectSource(sourceId: String?) {
        _selectedSourceId.value = sourceId
    }

    fun addSource(name: String, type: SourceType, textContent: String? = null) {
        val scene = selectedScene.value ?: return
        val current = currentSources.value
        val newZIndex = (current.maxOfOrNull { it.zIndex } ?: 0) + 1

        val (w, h, x, y) = when (type) {
            SourceType.SCREEN -> listOf(1f, 1f, 0f, 0f)
            SourceType.CAMERA -> listOf(0.3f, 0.35f, 0.65f, 0.6f)
            SourceType.TEXT -> listOf(0.4f, 0.1f, 0.05f, 0.05f)
            SourceType.IMAGE -> listOf(0.3f, 0.3f, 0.35f, 0.35f)
            SourceType.WEB -> listOf(0.35f, 0.25f, 0.1f, 0.7f)
        }

        val source = SourceEntity(
            id = UUID.randomUUID().toString(),
            sceneId = scene.id,
            name = name,
            type = type,
            isVisible = true,
            isLocked = false,
            zIndex = newZIndex,
            x = x,
            y = y,
            width = w,
            height = h,
            textContent = textContent ?: if (type == SourceType.TEXT) "STREAM TITLE" else null
        )

        viewModelScope.launch {
            repository.addSource(source)
            _selectedSourceId.value = source.id
        }
    }

    fun updateSelectedSource(transform: (SourceEntity) -> SourceEntity) {
        val current = selectedSource.value ?: return
        val updated = transform(current)
        viewModelScope.launch {
            repository.updateSource(updated)
        }
    }

    fun deleteSource(source: SourceEntity) {
        viewModelScope.launch {
            repository.deleteSource(source.id)
            if (_selectedSourceId.value == source.id) {
                _selectedSourceId.value = null
            }
        }
    }

    fun duplicateSource(source: SourceEntity) {
        val scene = selectedScene.value ?: return
        val newSource = source.copy(
            id = UUID.randomUUID().toString(),
            sceneId = scene.id,
            name = "${source.name} (Copy)",
            x = (source.x + 0.04f).coerceAtMost(0.9f),
            y = (source.y + 0.04f).coerceAtMost(0.9f),
            zIndex = source.zIndex + 1
        )
        viewModelScope.launch {
            repository.addSource(newSource)
            _selectedSourceId.value = newSource.id
        }
    }

    fun reorderSource(source: SourceEntity, bringForward: Boolean) {
        val sources = currentSources.value.sortedBy { it.zIndex }
        val index = sources.indexOfFirst { it.id == source.id }
        if (index == -1) return

        val targetIndex = if (bringForward) index + 1 else index - 1
        if (targetIndex in sources.indices) {
            val other = sources[targetIndex]
            viewModelScope.launch {
                repository.updateSource(source.copy(zIndex = other.zIndex))
                repository.updateSource(other.copy(zIndex = source.zIndex))
            }
        }
    }

    fun selectPreset(preset: StreamPreset) {
        currentPreset.value = preset
        val profile = selectedProfile.value
        if (profile != null) {
            viewModelScope.launch {
                repository.updateProfile(
                    profile.copy(
                        width = preset.width,
                        height = preset.height,
                        fps = preset.fps,
                        bitrateKbps = preset.defaultBitrateKbps,
                        isAutoMode = false
                    )
                )
            }
        }
    }

    fun setAutoPresetMode() {
        currentPreset.value = autoDetection.selectedPreset
        val profile = selectedProfile.value
        if (profile != null) {
            viewModelScope.launch {
                repository.updateProfile(
                    profile.copy(
                        width = autoDetection.selectedPreset.width,
                        height = autoDetection.selectedPreset.height,
                        fps = autoDetection.selectedPreset.fps,
                        bitrateKbps = autoDetection.selectedPreset.defaultBitrateKbps,
                        isAutoMode = true
                    )
                )
            }
        }
    }

    fun saveProfile(profile: StreamProfileEntity, plainStreamKey: String) {
        val encryptedKey = StreamKeyStorage.encrypt(plainStreamKey)
        viewModelScope.launch {
            repository.saveProfile(profile.copy(encryptedStreamKey = encryptedKey))
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
        }
    }

    fun resumePreview() {
        StreamingService.activeServiceInstance?.resumePreview()
    }

    fun toggleMicMute() {
        audioMixer.isMicMuted = !audioMixer.isMicMuted
        val settings = audioSettings.value ?: AudioSettingsEntity()
        viewModelScope.launch {
            repository.saveAudioSettings(settings.copy(isMicMuted = audioMixer.isMicMuted))
        }
        StreamAudioParams.micMuted = audioMixer.isMicMuted
        StreamingService.activeServiceInstance?.setMicMuted(audioMixer.isMicMuted)
    }

    fun updateMicVolume(volume: Float) {
        audioMixer.micGain = volume
        StreamAudioParams.micGain = volume
        val settings = audioSettings.value ?: AudioSettingsEntity()
        viewModelScope.launch {
            repository.saveAudioSettings(settings.copy(micVolume = volume))
        }
    }

    fun updateSystemVolume(volume: Float) {
        audioMixer.systemVolume = volume
        StreamAudioParams.systemVolume = volume
        val settings = audioSettings.value ?: AudioSettingsEntity()
        viewModelScope.launch {
            repository.saveAudioSettings(settings.copy(systemVolume = volume))
        }
    }

    fun getDecryptedKey(encrypted: String): String {
        return StreamKeyStorage.decrypt(encrypted)
    }

    override fun onCleared() {
        super.onCleared()
        audioMixer.stopMonitoring()
    }
}
