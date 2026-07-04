package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fm_presets")
data class FmPresetEntity(
    @PrimaryKey val frequency: Float,
    val stationName: String,
    val isFavorite: Boolean,
    val lastRssi: Int,
    val ptyLabel: String
)

@Dao
interface FmPresetDao {
    @Query("SELECT * FROM fm_presets ORDER BY frequency ASC")
    fun getAllPresets(): Flow<List<FmPresetEntity>>

    @Query("SELECT * FROM fm_presets WHERE isFavorite = 1 ORDER BY frequency ASC")
    fun getFavorites(): Flow<List<FmPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FmPresetEntity)

    @Delete
    suspend fun deletePreset(preset: FmPresetEntity)

    @Query("DELETE FROM fm_presets WHERE frequency = :freq")
    suspend fun deleteByFrequency(freq: Float)
}
