package com.episcan.app.sync

import com.episcan.app.data.local.DocumentoAdjunto
import com.episcan.app.data.local.EpiEntity
import java.io.File

/** Ficha tal como viaja entre la app y el servidor (mismos nombres que el JSON de la API). */
data class EpiDto(
    val uid: String = "",
    /** Al enviar: la revisión que este móvil conocía de la ficha (0 si es nueva). */
    val baseRevision: Long = 0,
    /** Al recibir: la revisión actual en el servidor. */
    val revision: Long = 0,
    val eliminado: Boolean = false,
    val parteCuerpo: String = "",
    val subcategoria: String = "",
    val nombreEpi: String = "",
    val marca: String = "",
    val modelo: String = "",
    val normativa: String = "",
    val simbolos: String = "",
    val fichaTecnica: String = "",
    val distribuidor: String = "",
    val observaciones: String = "",
    /** Identificadores de las fotos (nombre del archivo, p. ej. "epi_….jpg"), no rutas. */
    val fotos: List<String> = emptyList(),
    /** PDFs adjuntos: identificador (nombre del archivo, "doc_….pdf") y nombre original. */
    val documentos: List<DocumentoAdjunto> = emptyList(),
    val creadoEn: Long = 0,
    val actualizadoEn: Long = 0,
)

class PeticionPush(val items: List<EpiDto>)

class ResultadoItemPush(val uid: String = "", val estado: String = "", val item: EpiDto? = null)

class RespuestaPush(val resultados: List<ResultadoItemPush> = emptyList())

class RespuestaPull(val items: List<EpiDto> = emptyList(), val revision: Long = 0, val hayMas: Boolean = false)

/** Lo que devuelve una sincronización, para informar al usuario. */
data class ResultadoSync(
    val enviadas: Int = 0,
    val recibidas: Int = 0,
    /** Nombres de las fichas que se editaron a la vez en otro sitio (se conservó la versión del servidor). */
    val conflictos: List<String> = emptyList(),
    val error: String? = null,
) {
    val correcta get() = error == null
}

/** Las fotos viajan por su nombre de archivo; en el móvil se guardan con la ruta completa. */
fun EpiEntity.aDto(): EpiDto = EpiDto(
    uid = uid,
    baseRevision = revision,
    eliminado = eliminado,
    parteCuerpo = parteCuerpo,
    subcategoria = subcategoria,
    nombreEpi = nombreEpi,
    marca = marca,
    modelo = modelo,
    normativa = normativa,
    simbolos = simbolos,
    fichaTecnica = fichaTecnica,
    distribuidor = distribuidor,
    observaciones = observaciones,
    fotos = listaFotos().map { File(it).name },
    documentos = listaDocumentos(),
    creadoEn = creadoEn,
)

/** Convierte lo que llega del servidor en una ficha local ya sincronizada (conserva el id local si existía). */
fun EpiDto.aEntidad(existente: EpiEntity?, directorioFotos: String): EpiEntity = EpiEntity(
    id = existente?.id ?: 0,
    uid = uid,
    parteCuerpo = parteCuerpo,
    subcategoria = subcategoria,
    nombreEpi = nombreEpi,
    marca = marca,
    modelo = modelo,
    normativa = normativa,
    simbolos = simbolos,
    fichaTecnica = fichaTecnica,
    distribuidor = distribuidor,
    observaciones = observaciones,
    fotos = fotos.joinToString(EpiEntity.SEPARADOR_FOTOS) { File(directorioFotos, it).path },
    // Si el JSON trae "documentos": null, Gson deja el campo a null aunque Kotlin diga que no puede serlo
    documentos = EpiEntity.documentosAJson(documentos.orEmpty().filter { it.id.isNotBlank() }),
    creadoEn = if (creadoEn > 0) creadoEn else existente?.creadoEn ?: System.currentTimeMillis(),
    revision = revision,
    actualizadoEn = System.currentTimeMillis(),
    pendiente = false,
    eliminado = false,
)

/** Lo que el SyncManager necesita de la base de datos local (así se prueba sin Android). */
interface AlmacenSync {
    suspend fun pendientes(): List<EpiEntity>

    /** Fichas vivas (para comprobar qué fotos faltan). */
    suspend fun vivas(): List<EpiEntity>

    /** Busca también entre las de borrado lógico. */
    suspend fun porUid(uid: String): EpiEntity?

    /** Marca la ficha como enviada, solo si no se ha vuelto a modificar (actualizadoEn coincide). */
    suspend fun marcarSincronizada(uid: String, revision: Long, actualizadoEn: Long)

    /** Inserta o reemplaza (según [EpiEntity.id]) una ficha que viene del servidor. */
    suspend fun guardarDelServidor(epi: EpiEntity)

    suspend fun borrarLocal(uid: String)

    /** Se ha apuntado a otro servidor: todo se reenviará como nuevo. */
    suspend fun reiniciarParaNuevoServidor()
}

interface AlmacenFotos {
    val directorio: String
    fun existe(id: String): Boolean
    fun leer(id: String): ByteArray?
    fun guardar(id: String, bytes: ByteArray)
    fun borrar(id: String)
}

interface ConfigSync {
    val url: String
    val token: String
    var ultimaRevision: Long
    var servidorSincronizado: String
}

/** Archivos en un directorio de la app: filesDir/fotos para las fotos y filesDir/documentos para los PDF. */
class FotosLocales(private val carpeta: File) : AlmacenFotos {
    override val directorio: String get() = carpeta.path

    override fun existe(id: String) = File(carpeta, id).isFile

    override fun leer(id: String): ByteArray? = File(carpeta, id).takeIf { it.isFile }?.readBytes()

    override fun guardar(id: String, bytes: ByteArray) {
        carpeta.mkdirs()
        // Se escribe en un temporal y se renombra: una descarga cortada nunca deja una foto a medias
        val destino = File(carpeta, id)
        val temporal = File(carpeta, "$id.tmp")
        temporal.writeBytes(bytes)
        if (!temporal.renameTo(destino)) {
            temporal.delete()
            error("No se pudo guardar el archivo $id")
        }
    }

    override fun borrar(id: String) {
        File(carpeta, id).delete()
    }
}
