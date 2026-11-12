package com.example.data.repository

import com.example.data.dao.AudioSettingsDao
import com.example.data.dao.SceneDao
import com.example.data.dao.SourceDao
import com.example.data.dao.StreamProfileDao
import com.example.data.model.AudioSettingsEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.data.model.StreamProfileEntity
import com.example.encoder.StreamKeyStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class StudioRepository(
    private val sceneDao: SceneDao,
    private val sourceDao: SourceDao,
    private val profileDao: StreamProfileDao,
    private val audioDao: AudioSettingsDao
) {
    val allScenes: Flow<List<SceneEntity>> = sceneDao.getAllScenes()
    val selectedScene: Flow<SceneEntity?> = sceneDao.getSelectedScene()
    val allProfiles: Flow<List<StreamProfileEntity>> = profileDao.getAllProfiles()
    val selectedProfile: Flow<StreamProfileEntity?> = profileDao.getSelectedProfile()
    val audioSettings: Flow<AudioSettingsEntity?> = audioDao.getAudioSettings()

    fun getSourcesForScene(sceneId: String): Flow<List<SourceEntity>> =
        sourceDao.getSourcesForScene(sceneId)

    suspend fun initializeDefaultsIfNeeded() {
        val existingScenes = allScenes.firstOrNull()
        if (existingScenes.isNullOrEmpty()) {
            val scene1Id = UUID.randomUUID().toString()
            val scene2Id = UUID.randomUUID().toString()
            val scene3Id = UUID.randomUUID().toString()

            // 1. Scene 1: Main Gameplay
            sceneDao.insertScene(
                SceneEntity(
                    id = scene1Id,
                    name = "Gameplay Live",
                    isSelected = true,
                    orderIndex = 0,
                    canvasWidth = 1280,
                    canvasHeight = 720,
                    isPortrait = false
                )
            )

            // Primary Screen Capture source for Scene 1
            sourceDao.insertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene1Id,
                    name = "Display Capture (Primary)",
                    type = SourceType.SCREEN,
                    isVisible = true,
                    isLocked = false,
                    zIndex = 0,
                    x = 0f,
                    y = 0f,
                    width = 1f,
                    height = 1f
                )
            )

            // Stream Watermark / Label text source
            sourceDao.insertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene1Id,
                    name = "Studio Watermark",
                    type = SourceType.TEXT,
                    isVisible = true,
                    isLocked = true,
                    zIndex = 1,
                    x = 0.03f,
                    y = 0.04f,
                    width = 0.28f,
                    height = 0.08f,
                    textContent = "VILICHT LIVE",
                    textColor = 0xFFC5A46DL,
                    textBgColor = 0xAA171715L
                )
            )

            // 2. Scene 2: Gameplay + Camera
            sceneDao.insertScene(
                SceneEntity(
                    id = scene2Id,
                    name = "Gameplay + Webcam",
                    isSelected = false,
                    orderIndex = 1,
                    canvasWidth = 1280,
                    canvasHeight = 720,
                    isPortrait = false
                )
            )
            sourceDao.insertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene2Id,
                    name = "Display Capture",
                    type = SourceType.SCREEN,
                    isVisible = true,
                    isLocked = false,
                    zIndex = 0,
                    x = 0f,
                    y = 0f,
                    width = 1f,
                    height = 1f
                )
            )
            sourceDao.insertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene2Id,
                    name = "Facecam Overlay",
                    type = SourceType.CAMERA,
                    isVisible = true,
                    isLocked = false,
                    zIndex = 1,
                    x = 0.72f,
                    y = 0.65f,
                    width = 0.25f,
                    height = 0.30f,
                    chromaKeyEnabled = false
                )
            )

            // 3. Scene 3: Starting Soon
            sceneDao.insertScene(
                SceneEntity(
                    id = scene3Id,
                    name = "Starting Soon",
                    isSelected = false,
                    orderIndex = 2,
                    canvasWidth = 1280,
                    canvasHeight = 720,
                    isPortrait = false
                )
            )
            sourceDao.insertSource(
                SourceEntity(
                    id = UUID.randomUUID().toString(),
                    sceneId = scene3Id,
                    name = "Starting Notice",
                    type = SourceType.TEXT,
                    isVisible = true,
                    isLocked = true,
                    zIndex = 0,
                    x = 0.25f,
                    y = 0.40f,
                    width = 0.50f,
                    height = 0.15f,
                    textContent = "STREAM STARTING SOON",
                    textColor = 0xFFF2F0E9L,
                    textBgColor = 0xCC20201EL
                )
            )
        }

        // Default RTMP Profiles
        val existingProfiles = allProfiles.firstOrNull()
        if (existingProfiles.isNullOrEmpty()) {
            val defaultKey = StreamKeyStorage.encrypt("live_stream_key_sample")
            profileDao.insertProfile(
                StreamProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Custom RTMP",
                    serverUrl = "rtmp://a.rtmp.youtube.com/live2",
                    encryptedStreamKey = defaultKey,
                    width = 1280,
                    height = 720,
                    fps = 30,
                    bitrateKbps = 3000,
                    audioBitrateKbps = 128,
                    isAutoMode = true,
                    isSelected = true
                )
            )
            profileDao.insertProfile(
                StreamProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = "Twitch",
                    serverUrl = "rtmp://live.twitch.tv/app",
                    encryptedStreamKey = defaultKey,
                    width = 1280,
                    height = 720,
                    fps = 30,
                    bitrateKbps = 3500,
                    audioBitrateKbps = 160,
                    isAutoMode = true,
                    isSelected = false
                )
            )
            profileDao.insertProfile(
                StreamProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = "TikTok LIVE",
                    serverUrl = "rtmp://live-push.tiktok.com/live",
                    encryptedStreamKey = defaultKey,
                    width = 720,
                    height = 1280,
                    fps = 30,
                    bitrateKbps = 3000,
                    audioBitrateKbps = 128,
                    isAutoMode = true,
                    isSelected = false
                )
            )
        }

        // Default Audio Settings
        val existingAudio = audioSettings.firstOrNull()
        if (existingAudio == null) {
            audioDao.saveAudioSettings(
                AudioSettingsEntity(
                    id = 1,
                    micVolume = 1.0f,
                    isMicMuted = false,
                    systemVolume = 0.8f,
                    isSystemMuted = false
                )
            )
        }
    }

    suspend fun selectScene(sceneId: String) = sceneDao.setSelectedScene(sceneId)
    suspend fun createScene(name: String, isPortrait: Boolean = false): SceneEntity {
        val count = allScenes.firstOrNull()?.size ?: 0
        val scene = SceneEntity(
            name = name,
            orderIndex = count,
            canvasWidth = if (isPortrait) 720 else 1280,
            canvasHeight = if (isPortrait) 1280 else 720,
            isPortrait = isPortrait
        )
        sceneDao.insertScene(scene)
        // Add default screen capture source to new scene
        sourceDao.insertSource(
            SourceEntity(
                sceneId = scene.id,
                name = "Display Capture",
                type = SourceType.SCREEN,
                zIndex = 0,
                x = 0f,
                y = 0f,
                width = 1f,
                height = 1f
            )
        )
        return scene
    }

    suspend fun duplicateScene(scene: SceneEntity): SceneEntity {
        val newScene = scene.copy(
            id = UUID.randomUUID().toString(),
            name = "${scene.name} (Copy)",
            isSelected = false
        )
        sceneDao.insertScene(newScene)
        val sources = sourceDao.getSourcesForScene(scene.id).firstOrNull() ?: emptyList()
        sources.forEach { src ->
            sourceDao.insertSource(
                src.copy(
                    id = UUID.randomUUID().toString(),
                    sceneId = newScene.id
                )
            )
        }
        return newScene
    }

    suspend fun updateScene(scene: SceneEntity) = sceneDao.updateScene(scene)
    suspend fun deleteScene(sceneId: String) {
        sourceDao.deleteSourcesBySceneId(sceneId)
        sceneDao.deleteSceneById(sceneId)
    }

    suspend fun addSource(source: SourceEntity) = sourceDao.insertSource(source)
    suspend fun updateSource(source: SourceEntity) = sourceDao.updateSource(source)
    suspend fun deleteSource(sourceId: String) = sourceDao.deleteSourceById(sourceId)

    suspend fun saveProfile(profile: StreamProfileEntity) = profileDao.insertProfile(profile)
    suspend fun updateProfile(profile: StreamProfileEntity) = profileDao.updateProfile(profile)
    suspend fun selectProfile(profileId: String) = profileDao.setSelectedProfile(profileId)
    suspend fun deleteProfile(profile: StreamProfileEntity) = profileDao.deleteProfile(profile)

    suspend fun saveAudioSettings(settings: AudioSettingsEntity) = audioDao.saveAudioSettings(settings)
}
