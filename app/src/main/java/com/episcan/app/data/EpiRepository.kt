package com.episcan.app.data

import com.episcan.app.data.local.EpiDao
import com.episcan.app.data.local.EpiEntity
import com.episcan.app.sync.AlmacenSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** [directorioDocumentos]: carpeta donde se guardan los PDF adjuntos (filesDir/documentos). */
class EpiRepository(private val dao: EpiDao, private val directorioDocumentos: File) : AlmacenSync {
    fun observarTodos(): Flow<List<EpiEntity>> = dao.observarTodos()

    /** Cambios locales que aún no se han enviado al servidor. */
    fun observarPendientes(): Flow<Int> = dao.observarPendientes()

    suspend fun obtenerTodos(): List<EpiEntity> = dao.obtenerTodos()

    /** Guarda una ficha local: queda pendiente de enviarse al servidor. */
    suspend fun guardar(epi: EpiEntity) {
        val ficha = epi.copy(actualizadoEn = System.currentTimeMillis(), pendiente = true)
        if (ficha.id == 0L) dao.insertar(ficha) else dao.actualizar(ficha)
    }

    /**
     * Borra la ficha y las fotos y PDF que le pertenecen. Si el servidor ya la conocía se conserva un borrado
     * lógico hasta comunicárselo; si nunca se envió, se elimina sin más.
     */
    suspend fun eliminar(epi: EpiEntity) {
        // Se relee: la copia que tiene la pantalla puede llevar una revisión anterior a la última sincronización
        val actual = dao.porUid(epi.uid) ?: epi
        if (actual.revision == 0L) {
            dao.eliminar(actual)
        } else {
            dao.actualizar(actual.copy(eliminado = true, pendiente = true, actualizadoEn = System.currentTimeMillis()))
        }
        borrarArchivos(actual.listaFotos() + actual.listaDocumentos().map { rutaDocumento(it.id).path })
    }

    /** Archivo local de un PDF adjunto (puede no existir todavía si aún no se ha descargado). */
    fun rutaDocumento(id: String): File = File(directorioDocumentos, id)

    suspend fun borrarArchivos(rutas: List<String>) = withContext(Dispatchers.IO) {
        rutas.forEach { runCatching { File(it).delete() } }
    }

    // ---- AlmacenSync ----
    override suspend fun pendientes(): List<EpiEntity> = dao.pendientes()

    override suspend fun vivas(): List<EpiEntity> = dao.obtenerTodos()

    override suspend fun porUid(uid: String): EpiEntity? = dao.porUid(uid)

    override suspend fun marcarSincronizada(uid: String, revision: Long, actualizadoEn: Long) {
        dao.marcarSincronizada(uid, revision, actualizadoEn)
    }

    override suspend fun guardarDelServidor(epi: EpiEntity) {
        if (epi.id == 0L) dao.insertar(epi) else dao.actualizar(epi)
    }

    override suspend fun borrarLocal(uid: String) = dao.borrarPorUid(uid)

    override suspend fun reiniciarParaNuevoServidor() {
        dao.descartarBorradosLogicos()
        dao.marcarTodoComoPendiente()
    }
}
