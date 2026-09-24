package com.episcan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpiDao {
    @Query("SELECT * FROM epis ORDER BY parteCuerpo COLLATE NOCASE, nombreEpi COLLATE NOCASE")
    fun observarTodos(): Flow<List<EpiEntity>>

    @Query("SELECT * FROM epis ORDER BY parteCuerpo COLLATE NOCASE, nombreEpi COLLATE NOCASE")
    suspend fun obtenerTodos(): List<EpiEntity>

    @Insert
    suspend fun insertar(epi: EpiEntity): Long

    @Update
    suspend fun actualizar(epi: EpiEntity)

    @Delete
    suspend fun eliminar(epi: EpiEntity)
}
