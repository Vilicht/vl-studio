package com.example.data.db

import android.content.Context
import androidx.room.*
import com.example.data.dao.AudioSettingsDao
import com.example.data.dao.SceneDao
import com.example.data.dao.SourceDao
import com.example.data.dao.StreamProfileDao
import com.example.data.model.AudioSettingsEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SourceEntity
import com.example.data.model.SourceType
import com.example.data.model.StreamProfileEntity

class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = runCatching {
        SourceType.valueOf(value)
    }.getOrDefault(SourceType.SCREEN)
}

@Database(
    entities = [
        SceneEntity::class,
        SourceEntity::class,
        StreamProfileEntity::class,
        AudioSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun sourceDao(): SourceDao
    abstract fun streamProfileDao(): StreamProfileDao
    abstract fun audioSettingsDao(): AudioSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "vilicht_studio.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
