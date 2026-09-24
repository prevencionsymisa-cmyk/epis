package com.episcan.app.data

import com.episcan.app.data.local.EpiDao
import com.episcan.app.data.local.EpiEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class EpiRepository(private val dao: EpiDao) {
    fun observarTodos(): Flow<List<EpiEntity>> = dao.observarTodos()

    suspend fun obtenerTodos(): List<EpiEntity> = dao.obtenerTodos()

    suspend fun guardar(epi: EpiEntity) {
        if (epi.id == 0L) dao.insertar(epi) else dao.actualizar(epi)
    }

    /** Borra la ficha y las fotos que le pertenecen. */
    suspend fun eliminar(epi: EpiEntity) {
        dao.eliminar(epi)
        borrarArchivos(epi.listaFotos())
    }

    suspend fun borrarArchivos(rutas: List<String>) = withContext(Dispatchers.IO) {
        rutas.forEach { runCatching { File(it).delete() } }
    }
}
