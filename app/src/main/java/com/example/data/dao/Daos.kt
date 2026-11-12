package com.example.data.dao

import androidx.room.*
import com.example.data.model.AudioSettingsEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SourceEntity
import com.example.data.model.StreamProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes ORDER BY orderIndex ASC")
    fun getAllScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM scenes WHERE id = :id LIMIT 1")
    suspend fun getSceneById(id: String): SceneEntity?

    @Query("SELECT * FROM scenes WHERE isSelected = 1 LIMIT 1")
    fun getSelectedScene(): Flow<SceneEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Query("UPDATE scenes SET isSelected = (id = :selectedId)")
    suspend fun setSelectedScene(selectedId: String)

    @Delete
    suspend fun deleteScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun deleteSceneById(id: String)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    fun getSourcesForScene(sceneId: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Delete
    suspend fun deleteSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSourceById(id: String)

    @Query("DELETE FROM sources WHERE sceneId = :sceneId")
    suspend fun deleteSourcesBySceneId(sceneId: String)
}

@Dao
interface StreamProfileDao {
    @Query("SELECT * FROM stream_profiles")
    fun getAllProfiles(): Flow<List<StreamProfileEntity>>

    @Query("SELECT * FROM stream_profiles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedProfile(): Flow<StreamProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: StreamProfileEntity)

    @Update
    suspend fun updateProfile(profile: StreamProfileEntity)

    @Query("UPDATE stream_profiles SET isSelected = (id = :selectedId)")
    suspend fun setSelectedProfile(selectedId: String)

    @Delete
    suspend fun deleteProfile(profile: StreamProfileEntity)
}

@Dao
interface AudioSettingsDao {
    @Query("SELECT * FROM audio_settings WHERE id = 1 LIMIT 1")
    fun getAudioSettings(): Flow<AudioSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAudioSettings(settings: AudioSettingsEntity)
}
