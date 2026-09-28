package com.episcan.app.sync

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

private class ErrorSync(mensaje: String) : Exception(mensaje)

/**
 * Sincroniza el catálogo local con el servidor (API en Coolify), en los dos sentidos:
 *  1. ENVÍA los cambios locales pendientes (fichas nuevas, editadas y borradas) y sus fotos.
 *  2. RECIBE lo que haya cambiado en el servidor desde la última vez (otros móviles o la web).
 *  3. DESCARGA las fotos que falten.
 *
 * Conflictos: si una ficha se editó a la vez aquí y en otro sitio, gana la versión del servidor y se avisa.
 * Sin conexión no pasa nada: los cambios quedan pendientes y se envían en la siguiente sincronización.
 */
class SyncManager(
    private val cliente: OkHttpClient,
    private val almacen: AlmacenSync,
    private val fotos: AlmacenFotos,
    private val config: ConfigSync,
    /** Solo para tests con un servidor local sin TLS. */
    private val permitirHttp: Boolean = false,
) {
    private val gson = Gson()
    private val mutex = Mutex()
    private val fotosYaSubidas = mutableSetOf<String>()

    fun configurado() = config.url.isNotBlank() && config.token.isNotBlank()

    /** Una sola sincronización a la vez; las demás esperan su turno. */
    suspend fun sincronizar(): ResultadoSync = mutex.withLock {
        withContext(Dispatchers.IO) { ejecutar() }
    }

    private suspend fun ejecutar(): ResultadoSync {
        val base = config.url.trim().trimEnd('/')
        if (base.isEmpty() || config.token.isBlank()) return ResultadoSync(error = "Configura la dirección del servidor y el token en Ajustes")
        if (!permitirHttp && !base.startsWith("https://")) return ResultadoSync(error = "La dirección del servidor debe empezar por https://")

        return try {
            if (config.servidorSincronizado != base) {
                almacen.reiniciarParaNuevoServidor()
                config.ultimaRevision = 0
                config.servidorSincronizado = base
            }
            val (enviadas, conflictos) = enviar(base)
            val recibidas = recibir(base)
            descargarFotosFaltantes(base)
            ResultadoSync(enviadas = enviadas, recibidas = recibidas, conflictos = conflictos)
        } catch (e: ErrorSync) {
            ResultadoSync(error = e.message)
        } catch (e: JsonParseException) {
            ResultadoSync(error = "El servidor devolvió una respuesta no válida")
        } catch (e: IllegalArgumentException) {
            ResultadoSync(error = "La dirección del servidor no es válida")
        } catch (e: IOException) {
            ResultadoSync(error = "Sin conexión con el servidor")
        }
    }

    // ------------------------------------------------------------------ enviar

    private suspend fun enviar(base: String): Pair<Int, List<String>> {
        var pendientes = almacen.pendientes()
        // Una ficha creada y borrada antes de llegar a sincronizar: el servidor nunca la conoció
        pendientes.filter { it.eliminado && it.revision == 0L }.forEach { almacen.borrarLocal(it.uid) }
        pendientes = pendientes.filterNot { it.eliminado && it.revision == 0L }

        var enviadas = 0
        val conflictos = mutableListOf<String>()
        for (lote in pendientes.chunked(TAMANO_LOTE)) {
            lote.filterNot { it.eliminado }.forEach { subirFotos(base, it.listaFotos().map { ruta -> File(ruta).name }) }

            val cuerpo = gson.toJson(PeticionPush(lote.map { it.aDto() }))
            val respuesta = gson.fromJson(post("$base/api/sync/push", cuerpo), RespuestaPush::class.java)
            val locales = lote.associateBy { it.uid }

            for (resultado in respuesta.resultados) {
                val local = locales[resultado.uid] ?: continue
                val delServidor = resultado.item
                when (resultado.estado) {
                    "ok" -> {
                        if (local.eliminado) almacen.borrarLocal(local.uid)
                        else if (delServidor != null) almacen.marcarSincronizada(local.uid, delServidor.revision, local.actualizadoEn)
                        enviadas++
                    }
                    "conflicto" -> {
                        conflictos += local.nombreEpi
                        // Gana el servidor: se sustituye la versión local (o se elimina, si allí la borraron)
                        if (delServidor == null || delServidor.eliminado) {
                            local.listaFotos().forEach { fotos.borrar(File(it).name) }
                            almacen.borrarLocal(local.uid)
                        } else {
                            almacen.guardarDelServidor(delServidor.aEntidad(local, fotos.directorio))
                        }
                    }
                }
            }
        }
        return enviadas to conflictos
    }

    private fun subirFotos(base: String, ids: List<String>) {
        for (id in ids) {
            if (id in fotosYaSubidas) continue
            val bytes = fotos.leer(id) ?: continue
            if (!existeFotoEnServidor(base, id)) {
                llamar(
                    Request.Builder().url("$base/api/fotos/$id").header("Authorization", "Bearer ${config.token}")
                        .put(bytes.toRequestBody(JPEG)).build(),
                )
            }
            fotosYaSubidas += id
        }
    }

    private fun existeFotoEnServidor(base: String, id: String): Boolean =
        cliente.newCall(Request.Builder().url("$base/api/fotos/$id").head().build()).execute().use { r ->
            when {
                r.isSuccessful -> true
                r.code == 404 -> false
                else -> throw ErrorSync("El servidor respondió ${r.code} al comprobar una foto")
            }
        }

    // ------------------------------------------------------------------ recibir

    private suspend fun recibir(base: String): Int {
        var recibidas = 0
        while (true) {
            val respuesta = gson.fromJson(
                get("$base/api/sync/pull?desde=${config.ultimaRevision}&limite=$TAMANO_PAGINA"),
                RespuestaPull::class.java,
            )
            for (item in respuesta.items) if (aplicar(item)) recibidas++
            if (respuesta.revision > config.ultimaRevision) config.ultimaRevision = respuesta.revision
            if (!respuesta.hayMas) return recibidas
        }
    }

    /** Devuelve true si la ficha local cambió. */
    private suspend fun aplicar(item: EpiDto): Boolean {
        val local = almacen.porUid(item.uid)
        // Con cambios locales sin enviar no se pisa: el próximo envío detectará el conflicto
        if (local != null && local.pendiente) return false

        if (item.eliminado) {
            if (local == null) return false
            local.listaFotos().forEach { fotos.borrar(File(it).name) }
            almacen.borrarLocal(item.uid)
            return true
        }
        if (local != null && item.revision <= local.revision) return false
        almacen.guardarDelServidor(item.aEntidad(local, fotos.directorio))
        return true
    }

    // ------------------------------------------------------------------ fotos

    private suspend fun descargarFotosFaltantes(base: String) {
        for (ficha in almacen.vivas()) {
            for (ruta in ficha.listaFotos()) {
                val id = File(ruta).name
                if (fotos.existe(id)) continue
                try {
                    cliente.newCall(Request.Builder().url("$base/api/fotos/$id").build()).execute().use { r ->
                        if (r.isSuccessful) r.body?.bytes()?.takeIf { it.isNotEmpty() }?.let { fotos.guardar(id, it) }
                    }
                } catch (_: IOException) {
                    // Una foto que no baja no es motivo para fallar: se reintenta en la siguiente sincronización
                }
            }
        }
    }

    // ------------------------------------------------------------------ HTTP

    private fun get(url: String): String =
        llamar(Request.Builder().url(url).header("Authorization", "Bearer ${config.token}").build())

    private fun post(url: String, json: String): String =
        llamar(Request.Builder().url(url).header("Authorization", "Bearer ${config.token}").post(json.toRequestBody(JSON)).build())

    private fun llamar(peticion: Request): String =
        cliente.newCall(peticion).execute().use { r ->
            when {
                r.code == 401 -> throw ErrorSync("El servidor rechazó el token (401). Revísalo en Ajustes")
                !r.isSuccessful -> throw ErrorSync("El servidor respondió ${r.code}")
                else -> r.body?.string().orEmpty()
            }
        }

    private companion object {
        const val TAMANO_LOTE = 50
        const val TAMANO_PAGINA = 200
        val JSON = "application/json; charset=utf-8".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
    }
}
