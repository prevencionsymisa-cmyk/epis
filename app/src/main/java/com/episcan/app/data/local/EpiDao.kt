package com.episcan.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpiDao {
    // Las fichas con borrado lógico ("eliminado") nunca se muestran ni se exportan
    @Query("SELECT * FROM epis WHERE eliminado = 0 ORDER BY parteCuerpo COLLATE NOCASE, nombreEpi COLLATE NOCASE")
    fun observarTodos(): Flow<List<EpiEntity>>

    @Query("SELECT * FROM epis WHERE eliminado = 0 ORDER BY parteCuerpo COLLATE NOCASE, nombreEpi COLLATE NOCASE")
    suspend fun obtenerTodos(): List<EpiEntity>

    @Insert
    suspend fun insertar(epi: EpiEntity): Long

    @Update
    suspend fun actualizar(epi: EpiEntity)

    @Delete
    suspend fun eliminar(epi: EpiEntity)

    // ---- Sincronización ----
    @Query("SELECT COUNT(*) FROM epis WHERE pendiente = 1")
    fun observarPendientes(): Flow<Int>

    @Query("SELECT * FROM epis WHERE pendiente = 1")
    suspend fun pendientes(): List<EpiEntity>

    @Query("SELECT * FROM epis")
    suspend fun todasConEliminadas(): List<EpiEntity>

    @Query("SELECT * FROM epis WHERE uid = :uid LIMIT 1")
    suspend fun porUid(uid: String): EpiEntity?

    /** Solo marca como sincronizada si la ficha no se ha vuelto a modificar mientras se enviaba. */
    @Query("UPDATE epis SET pendiente = 0, revision = :revision WHERE uid = :uid AND actualizadoEn = :actualizadoEn")
    suspend fun marcarSincronizada(uid: String, revision: Long, actualizadoEn: Long): Int

    @Query("DELETE FROM epis WHERE uid = :uid")
    suspend fun borrarPorUid(uid: String)

    /** Al cambiar de servidor: todo lo que hay se vuelve a enviar como nuevo y se descartan los borrados pendientes. */
    @Query("DELETE FROM epis WHERE eliminado = 1")
    suspend fun descartarBorradosLogicos()

    @Query("UPDATE epis SET revision = 0, pendiente = 1")
    suspend fun marcarTodoComoPendiente()
}
