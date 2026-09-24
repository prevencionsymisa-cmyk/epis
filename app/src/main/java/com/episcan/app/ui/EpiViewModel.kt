package com.episcan.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.episcan.app.EpiApp
import com.episcan.app.data.PARTES_CUERPO
import com.episcan.app.data.ResultadoIa
import com.episcan.app.data.local.EpiEntity
import com.episcan.app.data.remote.ErrorAnalisis
import com.episcan.app.export.FilaExcel
import com.episcan.app.export.XlsxWriter
import com.episcan.app.ota.DescargaOta
import com.episcan.app.ota.UpdateInfo
import com.episcan.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Pantalla { Inicio, Captura, Ajustes }

/** Datos editables de una ficha (nueva o existente) mientras se muestra el Bottom Sheet. */
data class EpiBorrador(
    val id: Long = 0,
    val parteCuerpo: String = "",
    val nombreEpi: String = "",
    val marca: String = "",
    val modelo: String = "",
    val normativa: String = "",
    val simbolos: String = "",
    val distribuidor: String = "",
    val observaciones: String = "",
    val fotos: List<String> = emptyList(),
    val creadoEn: Long = System.currentTimeMillis(),
    /** true si viene de una propuesta de la IA: se avisa de que hay que validarla. */
    val propuestaIa: Boolean = false,
)

data class OtaUi(
    val info: UpdateInfo,
    val progreso: Int? = null,
    val apk: File? = null,
    val error: String? = null,
)

class EpiViewModel(app: Application) : AndroidViewModel(app) {
    private val contenedor = (app as EpiApp).contenedor
    private val repo = contenedor.repositorio
    val ajustes = contenedor.ajustes

    var pantalla by mutableStateOf(Pantalla.Inicio)

    // ------------------------------------------------------------ catálogo
    val consulta = MutableStateFlow("")

    /** Zona seleccionada en los chips (null = todas). Si la zona se queda sin fichas se ignora. */
    val zonaFiltro = MutableStateFlow<String?>(null)

    /** Catálogo completo, sin filtros (lo usa la hoja de detalle para reflejar ediciones al instante). */
    val todos: StateFlow<List<EpiEntity>> = repo.observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Zonas con al menos una ficha y cuántas tiene cada una, en el orden de [PARTES_CUERPO]. */
    val conteoZonas: StateFlow<List<Pair<String, Int>>> = repo.observarTodos().map { lista ->
        PARTES_CUERPO.map { zona -> zona to lista.count { it.parteCuerpo == zona } }.filter { it.second > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val epis: StateFlow<List<EpiEntity>> = combine(repo.observarTodos(), consulta, zonaFiltro) { lista, q, zona ->
        val t = q.trim()
        val zonaActiva = zona?.takeIf { z -> lista.any { it.parteCuerpo == z } }
        lista.filter { e ->
            (zonaActiva == null || e.parteCuerpo == zonaActiva) &&
                (t.isEmpty() || listOf(e.parteCuerpo, e.nombreEpi, e.marca, e.modelo, e.normativa, e.simbolos, e.distribuidor, e.observaciones)
                    .any { it.contains(t, ignoreCase = true) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Int> = repo.observarTodos().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val mensajes: SharedFlow<String> = _mensajes.asSharedFlow()
    fun mensaje(texto: String) { _mensajes.tryEmit(texto) }

    // ------------------------------------------------------------ captura
    val fotosBorrador = mutableStateListOf<String>()
    var procesandoFoto by mutableStateOf(false)
        private set
    var analizando by mutableStateOf(false)
        private set
    var errorAnalisis by mutableStateOf<String?>(null)
        private set
    private var trabajoAnalisis: Job? = null

    fun abrirCaptura() {
        errorAnalisis = null
        pantalla = Pantalla.Captura
    }

    /** Comprime cada imagen (1280 px, JPEG 75 %) y la añade al borrador. Los temporales de cámara se borran. */
    fun anadirFotos(uris: List<Uri>, temporales: List<File> = emptyList()) {
        val hueco = MAX_FOTOS - fotosBorrador.size
        if (hueco <= 0) {
            temporales.forEach { it.delete() }
            mensaje("Máximo $MAX_FOTOS fotos por EPI")
            return
        }
        viewModelScope.launch {
            procesandoFoto = true
            val dir = File(getApplication<Application>().filesDir, "fotos")
            val nuevas = withContext(Dispatchers.IO) {
                uris.take(hueco).mapNotNull { uri ->
                    val destino = File(dir, "epi_${UUID.randomUUID()}.jpg")
                    runCatching { ImageUtils.guardarComprimida(getApplication(), uri, destino) }
                        .map { destino.path }
                        .onFailure { destino.delete() }
                        .getOrNull()
                }.also { temporales.forEach { it.delete() } }
            }
            if (nuevas.size < uris.take(hueco).size) mensaje("Alguna imagen no se pudo procesar")
            if (uris.size > hueco) mensaje("Solo se añadieron $hueco (máximo $MAX_FOTOS)")
            fotosBorrador.addAll(nuevas)
            procesandoFoto = false
        }
    }

    fun quitarFotoBorrador(ruta: String) {
        fotosBorrador.remove(ruta)
        viewModelScope.launch { repo.borrarArchivos(listOf(ruta)) }
    }

    /** Sale de la pantalla de captura descartando las fotos que no se hayan usado. */
    fun cerrarCaptura() {
        trabajoAnalisis?.cancel()
        analizando = false
        val descartadas = fotosBorrador.toList()
        fotosBorrador.clear()
        viewModelScope.launch { repo.borrarArchivos(descartadas) }
        pantalla = Pantalla.Inicio
    }

    fun analizar() {
        if (fotosBorrador.isEmpty() || analizando) return
        errorAnalisis = null
        analizando = true
        trabajoAnalisis = viewModelScope.launch {
            try {
                val r = contenedor.analizador.analizar(fotosBorrador.map { File(it) })
                abrirEditorDesdeBorrador(r, propuestaIa = true)
            } catch (e: ErrorAnalisis) {
                errorAnalisis = e.message
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorAnalisis = "Error inesperado: ${e.message}"
            } finally {
                analizando = false
            }
        }
    }

    fun cancelarAnalisis() {
        trabajoAnalisis?.cancel()
        analizando = false
    }

    /** Permite continuar sin IA (sin conexión, sin clave, análisis fallido). */
    fun rellenarManualmente() = abrirEditorDesdeBorrador(ResultadoIa(), propuestaIa = false)

    private fun abrirEditorDesdeBorrador(r: ResultadoIa, propuestaIa: Boolean) {
        editor = EpiBorrador(
            parteCuerpo = r.parteCuerpo, nombreEpi = r.nombreEpi, marca = r.marca, modelo = r.modelo,
            normativa = r.normativa, simbolos = r.simbolos, distribuidor = r.distribuidor,
            observaciones = r.observaciones, fotos = fotosBorrador.toList(), propuestaIa = propuestaIa,
        )
        fotosBorrador.clear() // ahora las fotos pertenecen al editor
        pantalla = Pantalla.Inicio
    }

    // ------------------------------------------------------------ edición
    var editor by mutableStateOf<EpiBorrador?>(null)
        private set

    fun editar(epi: EpiEntity) {
        editor = EpiBorrador(
            id = epi.id, parteCuerpo = epi.parteCuerpo, nombreEpi = epi.nombreEpi, marca = epi.marca,
            modelo = epi.modelo, normativa = epi.normativa, simbolos = epi.simbolos,
            distribuidor = epi.distribuidor, observaciones = epi.observaciones,
            fotos = epi.listaFotos(), creadoEn = epi.creadoEn,
        )
    }

    fun guardar(b: EpiBorrador) {
        viewModelScope.launch {
            repo.guardar(
                EpiEntity(
                    id = b.id, parteCuerpo = b.parteCuerpo, nombreEpi = b.nombreEpi.trim(), marca = b.marca.trim(),
                    modelo = b.modelo.trim(), normativa = b.normativa.trim(), simbolos = b.simbolos.trim(),
                    distribuidor = b.distribuidor.trim(), observaciones = b.observaciones.trim(),
                    fotos = b.fotos.joinToString(EpiEntity.SEPARADOR_FOTOS), creadoEn = b.creadoEn,
                ),
            )
            editor = null
            mensaje(if (b.id == 0L) "EPI añadido al catálogo" else "Cambios guardados")
        }
    }

    fun cancelarEditor() {
        val e = editor ?: return
        editor = null
        // Una ficha nueva descartada no debe dejar fotos huérfanas en disco
        if (e.id == 0L) viewModelScope.launch { repo.borrarArchivos(e.fotos) }
    }

    // ------------------------------------------------------------ eliminación
    var aEliminar by mutableStateOf<EpiEntity?>(null)
        private set

    fun pedirEliminar(epi: EpiEntity) { aEliminar = epi }
    fun descartarEliminar() { aEliminar = null }
    fun confirmarEliminar() {
        val e = aEliminar ?: return
        aEliminar = null
        viewModelScope.launch {
            repo.eliminar(e)
            mensaje("EPI eliminado")
        }
    }

    // ------------------------------------------------------------ exportación a Excel
    var exportando by mutableStateOf(false)
        private set

    /** Genera el .xlsx en caché y entrega el archivo a [alTerminar]. */
    fun exportarExcel(alTerminar: (File) -> Unit) {
        if (exportando) return
        viewModelScope.launch {
            exportando = true
            try {
                val archivo = withContext(Dispatchers.IO) { generarExcel() }
                alTerminar(archivo)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                mensaje("No se pudo generar el Excel: ${e.message}")
            } finally {
                exportando = false
            }
        }
    }

    private suspend fun generarExcel(): File {
        val lista = repo.obtenerTodos()
        val filas = lista.map { e ->
            FilaExcel(
                parteCuerpo = e.parteCuerpo, nombreEpi = e.nombreEpi, observaciones = e.observaciones,
                marca = e.marca, modelo = e.modelo, normativa = e.normativa, simbolos = e.simbolos,
                distribuidor = e.distribuidor,
                miniatura = e.listaFotos().firstOrNull()?.let { ImageUtils.miniatura(File(it)) },
            )
        }
        val ahora = Date()
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val nombre = "Registro_EPIs_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(ahora) + ".xlsx"
        val archivo = File(dir, nombre)
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")).format(ahora)
        archivo.outputStream().buffered().use { XlsxWriter.escribir(it, filas, fecha) }
        return archivo
    }

    // ------------------------------------------------------------ OTA
    var ota by mutableStateOf<OtaUi?>(null)
        private set
    var comprobandoOta by mutableStateOf(false)
        private set
    private var trabajoDescarga: Job? = null

    fun comprobarActualizaciones(manual: Boolean) {
        if (comprobandoOta) return
        val url = ajustes.otaUrl
        if (url.isBlank()) {
            if (manual) mensaje("Configura la URL de actualizaciones en Ajustes")
            return
        }
        viewModelScope.launch {
            comprobandoOta = true
            try {
                val info = contenedor.ota.comprobar(url)
                if (info != null) ota = OtaUi(info)
                else if (manual) mensaje("Ya tienes la última versión")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (manual) mensaje("No se pudo comprobar: ${e.message}")
            } finally {
                comprobandoOta = false
            }
        }
    }

    fun descargarActualizacion() {
        val actual = ota ?: return
        if (trabajoDescarga?.isActive == true) return
        ota = actual.copy(progreso = 0, error = null)
        trabajoDescarga = viewModelScope.launch {
            contenedor.ota.descargar(actual.info).collect { estado ->
                val base = ota ?: return@collect
                ota = when (estado) {
                    is DescargaOta.Progreso -> base.copy(progreso = estado.porcentaje)
                    is DescargaOta.Fallo -> base.copy(progreso = null, error = estado.mensaje)
                    is DescargaOta.Completada -> {
                        instalarApk(estado.apk)
                        base.copy(progreso = null, apk = estado.apk)
                    }
                }
            }
        }
    }

    fun instalarActualizacion() {
        ota?.apk?.let { instalarApk(it) }
    }

    private fun instalarApk(apk: File) {
        val lanzado = runCatching { contenedor.ota.instalar(apk) }.getOrElse {
            mensaje("No se pudo abrir el instalador: ${it.message}")
            return
        }
        if (!lanzado) mensaje("Activa «Permitir de esta fuente» y vuelve a pulsar Instalar")
    }

    fun descartarActualizacion() {
        trabajoDescarga?.cancel()
        ota = null
    }

    private companion object {
        const val MAX_FOTOS = 6
    }
}
