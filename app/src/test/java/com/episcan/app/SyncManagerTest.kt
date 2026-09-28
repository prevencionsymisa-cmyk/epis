package com.episcan.app

import com.episcan.app.data.local.EpiEntity
import com.episcan.app.sync.AlmacenFotos
import com.episcan.app.sync.AlmacenSync
import com.episcan.app.sync.ConfigSync
import com.episcan.app.sync.EpiDto
import com.episcan.app.sync.SyncManager
import com.episcan.app.sync.aDto
import com.episcan.app.sync.aEntidad
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------- dobles de prueba

internal class AlmacenFalso : AlmacenSync {
    val filas = mutableListOf<EpiEntity>()
    private var siguienteId = 1L

    fun agregar(e: EpiEntity): EpiEntity = e.copy(id = siguienteId++).also { filas += it }
    fun ficha(uid: String) = filas.firstOrNull { it.uid == uid }

    override suspend fun pendientes() = filas.filter { it.pendiente }
    override suspend fun vivas() = filas.filter { !it.eliminado }
    override suspend fun porUid(uid: String) = ficha(uid)
    override suspend fun marcarSincronizada(uid: String, revision: Long, actualizadoEn: Long) {
        val i = filas.indexOfFirst { it.uid == uid && it.actualizadoEn == actualizadoEn }
        if (i >= 0) filas[i] = filas[i].copy(pendiente = false, revision = revision)
    }
    override suspend fun guardarDelServidor(epi: EpiEntity) {
        if (epi.id == 0L) agregar(epi) else filas[filas.indexOfFirst { it.id == epi.id }] = epi
    }
    override suspend fun borrarLocal(uid: String) {
        filas.removeAll { it.uid == uid }
    }
    override suspend fun reiniciarParaNuevoServidor() {
        filas.removeAll { it.eliminado }
        filas.replaceAll { it.copy(revision = 0, pendiente = true) }
    }
}

internal class FotosFalsas : AlmacenFotos {
    val archivos = mutableMapOf<String, ByteArray>()
    override val directorio = "/data/fotos"
    override fun existe(id: String) = id in archivos
    override fun leer(id: String) = archivos[id]
    override fun guardar(id: String, bytes: ByteArray) { archivos[id] = bytes }
    override fun borrar(id: String) { archivos.remove(id) }
}

internal class ConfigFalsa(override var url: String, override val token: String = "token-secreto-123") : ConfigSync {
    override var ultimaRevision = 0L
    override var servidorSincronizado = ""
}

private class Peticion(val metodo: String, val ruta: String, val cuerpo: ByteArray, val autorizacion: String?)

class SyncManagerTest {
    private val gson = Gson()
    private lateinit var servidor: MockWebServer
    private val peticiones = mutableListOf<Peticion>()
    private var manejador: (Peticion) -> MockResponse = { MockResponse().setResponseCode(404) }

    private val almacen = AlmacenFalso()
    private val fotos = FotosFalsas()
    private lateinit var config: ConfigFalsa
    private lateinit var sync: SyncManager

    @Before
    fun preparar() {
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = Peticion(request.method.orEmpty(), request.path.orEmpty(), request.body.readByteArray(), request.getHeader("Authorization"))
                peticiones += p
                return manejador(p)
            }
        }
        servidor.start()
        config = ConfigFalsa(servidor.url("/").toString().trimEnd('/'))
        config.servidorSincronizado = config.url // como si ya se hubiera sincronizado antes con este servidor
        sync = SyncManager(OkHttpClient(), almacen, fotos, config, permitirHttp = true)
    }

    @After
    fun cerrar() {
        runCatching { servidor.shutdown() }
    }

    private fun json(objeto: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(gson.toJson(objeto))

    private fun pull(items: List<EpiDto> = emptyList(), revision: Long = 0, hayMas: Boolean = false) =
        json(mapOf("items" to items, "revision" to revision, "hayMas" to hayMas))

    private fun resultadoPush(vararg r: Triple<String, String, EpiDto?>) =
        json(mapOf("resultados" to r.map { (uid, estado, item) -> mapOf("uid" to uid, "estado" to estado, "item" to item) }))

    private fun local(uid: String, nombre: String = "Guantes $uid", revision: Long = 0, pendiente: Boolean = true, fotos: String = "", eliminado: Boolean = false) =
        almacen.agregar(
            EpiEntity(uid = uid, parteCuerpo = "Manos y Brazos", nombreEpi = nombre, revision = revision, pendiente = pendiente, fotos = fotos, eliminado = eliminado, actualizadoEn = 1000),
        )

    private fun dtoServidor(uid: String, revision: Long, nombre: String = "Guantes $uid", marca: String = "", fotos: List<String> = emptyList(), eliminado: Boolean = false) =
        EpiDto(uid = uid, revision = revision, parteCuerpo = "Manos y Brazos", nombreEpi = nombre, marca = marca, fotos = fotos, eliminado = eliminado, creadoEn = 5)

    private fun sincronizar() = runBlocking { sync.sincronizar() }

    private fun cuerpoJson(p: Peticion) = JsonParser.parseString(String(p.cuerpo)).asJsonObject

    // ---------------------------------------------------------------- enviar

    @Test
    fun enviaUnaFichaNuevaConSuFotoYLaMarcaComoSincronizada() {
        val rutaFoto = "/data/user/0/app/files/fotos/f1.jpg"
        fotos.archivos["f1.jpg"] = byteArrayOf(1, 2, 3)
        local("uid-1", fotos = rutaFoto)
        manejador = { p ->
            when {
                p.metodo == "HEAD" -> MockResponse().setResponseCode(404)
                p.metodo == "PUT" -> MockResponse().setResponseCode(201)
                p.metodo == "POST" -> resultadoPush(Triple("uid-1", "ok", dtoServidor("uid-1", 7)))
                else -> pull(revision = 7)
            }
        }

        val r = sincronizar()

        assertTrue(r.error, r.correcta)
        assertEquals(1, r.enviadas)
        val f = almacen.ficha("uid-1")!!
        assertFalse(f.pendiente)
        assertEquals(7, f.revision)

        val put = peticiones.single { it.metodo == "PUT" }
        assertEquals("/api/fotos/f1.jpg", put.ruta)
        assertArrayEquals(byteArrayOf(1, 2, 3), put.cuerpo)
        assertEquals("Bearer token-secreto-123", put.autorizacion)

        val push = peticiones.single { it.metodo == "POST" }
        assertEquals("Bearer token-secreto-123", push.autorizacion)
        val item = cuerpoJson(push).getAsJsonArray("items")[0].asJsonObject
        assertEquals("uid-1", item["uid"].asString)
        assertEquals(0, item["baseRevision"].asInt)
        assertEquals(listOf("f1.jpg"), item["fotos"].asJsonArray.map { it.asString }) // nombres, nunca rutas del móvil
    }

    @Test
    fun noVuelveASubirUnaFotoQueYaEstaEnElServidor() {
        fotos.archivos["f1.jpg"] = byteArrayOf(1)
        local("uid-1", fotos = "/x/f1.jpg")
        manejador = { p ->
            when (p.metodo) {
                "HEAD" -> MockResponse().setResponseCode(200)
                "POST" -> resultadoPush(Triple("uid-1", "ok", dtoServidor("uid-1", 2)))
                else -> pull(revision = 2)
            }
        }
        assertTrue(sincronizar().correcta)
        assertTrue(peticiones.none { it.metodo == "PUT" })
    }

    @Test
    fun enUnConflictoGanaElServidorYSeAvisa() {
        local("uid-1", nombre = "Mi versión", revision = 3)
        manejador = { p ->
            if (p.metodo == "POST") resultadoPush(Triple("uid-1", "conflicto", dtoServidor("uid-1", 9, nombre = "Versión de la web", marca = "Web")))
            else pull(revision = 9)
        }

        val r = sincronizar()

        assertEquals(listOf("Mi versión"), r.conflictos)
        assertEquals(0, r.enviadas)
        val f = almacen.ficha("uid-1")!!
        assertEquals("Versión de la web", f.nombreEpi)
        assertEquals("Web", f.marca)
        assertEquals(9, f.revision)
        assertFalse(f.pendiente)
    }

    @Test
    fun siEnElServidorLaBorraronYLocalmenteSeEditoSeEliminaLaLocal() {
        val e = local("uid-1", revision = 3)
        fotos.archivos["a.jpg"] = byteArrayOf(1)
        almacen.filas[0] = e.copy(fotos = "/x/a.jpg")
        manejador = { p ->
            when (p.metodo) {
                "HEAD" -> MockResponse().setResponseCode(200) // la foto ya está en el servidor (un HEAD real no lleva cuerpo)
                "POST" -> resultadoPush(Triple("uid-1", "conflicto", dtoServidor("uid-1", 9, eliminado = true)))
                else -> pull(revision = 9)
            }
        }
        val r = sincronizar()
        assertTrue("error inesperado: ${r.error}", r.correcta)
        assertEquals(1, r.conflictos.size)
        assertNull(almacen.ficha("uid-1"))
        assertFalse(fotos.existe("a.jpg"))
    }

    @Test
    fun unBorradoLocalYaConocidoPorElServidorSeEnviaYLaFilaSeElimina() {
        local("uid-1", revision = 4, eliminado = true)
        manejador = { p ->
            if (p.metodo == "POST") resultadoPush(Triple("uid-1", "ok", dtoServidor("uid-1", 8, eliminado = true)))
            else pull(revision = 8)
        }
        val r = sincronizar()
        assertEquals(1, r.enviadas)
        val item = cuerpoJson(peticiones.single { it.metodo == "POST" }).getAsJsonArray("items")[0].asJsonObject
        assertTrue(item["eliminado"].asBoolean)
        assertEquals(4, item["baseRevision"].asInt)
        assertNull(almacen.ficha("uid-1"))
    }

    @Test
    fun unaFichaCreadaYBorradaSinSincronizarNoSeEnviaNiSeConserva() {
        local("uid-1", revision = 0, eliminado = true)
        manejador = { pull(revision = 0) }
        assertTrue(sincronizar().correcta)
        assertTrue(peticiones.none { it.metodo == "POST" })
        assertNull(almacen.ficha("uid-1"))
    }

    @Test
    fun sinCambiosPendientesNoHaceNingunEnvio() {
        local("uid-1", revision = 5, pendiente = false)
        manejador = { pull(revision = 5) }
        val r = sincronizar()
        assertTrue(r.correcta)
        assertTrue(peticiones.none { it.metodo == "POST" })
    }

    // ---------------------------------------------------------------- recibir

    @Test
    fun recibeFichasNuevasYActualizadasYIgnoraLasAntiguas() {
        local("uid-vieja", revision = 2, pendiente = false)
        local("uid-vigente", nombre = "Antes", revision = 2, pendiente = false)
        local("uid-igual", revision = 6, pendiente = false)
        manejador = {
            pull(
                items = listOf(
                    dtoServidor("uid-nueva", 10, nombre = "Nueva de la web"),
                    dtoServidor("uid-vigente", 11, nombre = "Después"),
                    dtoServidor("uid-igual", 6, nombre = "No debe cambiar"), // misma revisión: ya la tengo
                ),
                revision = 11,
            )
        }

        val r = sincronizar()

        assertEquals(2, r.recibidas)
        assertEquals("Nueva de la web", almacen.ficha("uid-nueva")!!.nombreEpi)
        assertFalse(almacen.ficha("uid-nueva")!!.pendiente)
        assertEquals("Después", almacen.ficha("uid-vigente")!!.nombreEpi)
        assertEquals("Guantes uid-igual", almacen.ficha("uid-igual")!!.nombreEpi)
        assertEquals(11, config.ultimaRevision)
    }

    @Test
    fun laPeticionDePullUsaLaUltimaRevisionGuardada() {
        config.ultimaRevision = 42
        manejador = { pull(revision = 42) }
        sincronizar()
        assertEquals("/api/sync/pull?desde=42&limite=200", peticiones.single { it.metodo == "GET" }.ruta)
    }

    @Test
    fun noPisaUnaFichaConCambiosLocalesSinEnviar() {
        // Se envía primero: el servidor responde ok y la deja sincronizada, así que para este caso se simula
        // un cambio local posterior mediante una respuesta de push que no menciona la ficha.
        local("uid-1", nombre = "Edición local", revision = 2, pendiente = true)
        manejador = { p ->
            if (p.metodo == "POST") json(mapOf("resultados" to emptyList<Any>()))
            else pull(items = listOf(dtoServidor("uid-1", 9, nombre = "Del servidor")), revision = 9)
        }
        sincronizar()
        assertEquals("Edición local", almacen.ficha("uid-1")!!.nombreEpi)
        assertTrue(almacen.ficha("uid-1")!!.pendiente)
    }

    @Test
    fun unBorradoEnElServidorEliminaLaFichaLocalYSusFotos() {
        local("uid-1", revision = 2, pendiente = false, fotos = "/x/a.jpg")
        fotos.archivos["a.jpg"] = byteArrayOf(9)
        manejador = { pull(items = listOf(dtoServidor("uid-1", 8, eliminado = true)), revision = 8) }

        val r = sincronizar()

        assertEquals(1, r.recibidas)
        assertNull(almacen.ficha("uid-1"))
        assertFalse(fotos.existe("a.jpg"))
    }

    @Test
    fun pideVariasPaginasMientrasHayaMas() {
        var pagina = 0
        manejador = {
            pagina++
            if (pagina == 1) pull(items = listOf(dtoServidor("a", 1)), revision = 1, hayMas = true)
            else pull(items = listOf(dtoServidor("b", 2)), revision = 2, hayMas = false)
        }
        val r = sincronizar()
        assertEquals(2, r.recibidas)
        assertEquals(2, config.ultimaRevision)
        val rutas = peticiones.filter { it.metodo == "GET" }.map { it.ruta }
        assertEquals(listOf("/api/sync/pull?desde=0&limite=200", "/api/sync/pull?desde=1&limite=200"), rutas)
    }

    // ---------------------------------------------------------------- fotos

    @Test
    fun descargaLasFotosQueFaltanYToleraLasQueNoExisten() {
        manejador = { p ->
            when {
                p.ruta.startsWith("/api/fotos/existe.jpg") -> MockResponse().setBody(okio.Buffer().write(byteArrayOf(5, 6, 7)))
                p.ruta.startsWith("/api/fotos/") -> MockResponse().setResponseCode(404)
                else -> pull(items = listOf(dtoServidor("uid-1", 3, fotos = listOf("existe.jpg", "no-esta.jpg"))), revision = 3)
            }
        }
        val r = sincronizar()
        assertTrue(r.correcta)
        assertArrayEquals(byteArrayOf(5, 6, 7), fotos.archivos["existe.jpg"])
        assertFalse(fotos.existe("no-esta.jpg"))
        // La ficha guarda rutas completas en el móvil
        assertEquals("/data/fotos/existe.jpg|/data/fotos/no-esta.jpg", almacen.ficha("uid-1")!!.fotos.replace('\\', '/'))
    }

    @Test
    fun noVuelveADescargarUnaFotoQueYaTiene() {
        local("uid-1", revision = 3, pendiente = false, fotos = "/data/fotos/ya.jpg")
        fotos.archivos["ya.jpg"] = byteArrayOf(1)
        manejador = { pull(revision = 3) }
        sincronizar()
        assertTrue(peticiones.none { it.ruta.startsWith("/api/fotos/") })
    }

    // ---------------------------------------------------------------- errores y configuración

    @Test
    fun conTokenIncorrectoAvisaYNoTocaNada() {
        local("uid-1")
        manejador = { MockResponse().setResponseCode(401) }
        val r = sincronizar()
        assertFalse(r.correcta)
        assertTrue(r.error!!.contains("401"))
        assertTrue(almacen.ficha("uid-1")!!.pendiente)
        assertEquals(0, config.ultimaRevision)
    }

    @Test
    fun sinConexionDejaLosCambiosPendientes() {
        local("uid-1")
        servidor.shutdown()
        val r = sincronizar()
        assertFalse(r.correcta)
        assertEquals("Sin conexión con el servidor", r.error)
        assertTrue(almacen.ficha("uid-1")!!.pendiente)
    }

    @Test
    fun unErrorDelServidorNoMarcaNadaComoEnviado() {
        local("uid-1")
        manejador = { MockResponse().setResponseCode(500) }
        val r = sincronizar()
        assertTrue(r.error!!.contains("500"))
        assertTrue(almacen.ficha("uid-1")!!.pendiente)
    }

    @Test
    fun unaRespuestaQueNoEsJsonDaUnErrorClaro() {
        local("uid-1")
        manejador = { MockResponse().setBody("<html>proxy de la empresa</html>") }
        val r = sincronizar()
        assertFalse(r.correcta)
        assertNotNull(r.error)
        assertTrue(almacen.ficha("uid-1")!!.pendiente)
    }

    @Test
    fun sinConfiguracionNoHaceNada() {
        val vacio = SyncManager(OkHttpClient(), almacen, fotos, ConfigFalsa(url = ""), permitirHttp = true)
        assertFalse(vacio.configurado())
        val r = runBlocking { vacio.sincronizar() }
        assertFalse(r.correcta)
        assertTrue(peticiones.isEmpty())
    }

    @Test
    fun rechazaUnaDireccionSinHttpsEnProduccion() {
        val estricto = SyncManager(OkHttpClient(), almacen, fotos, config, permitirHttp = false)
        val r = runBlocking { estricto.sincronizar() }
        assertTrue(r.error!!.contains("https"))
        assertTrue(peticiones.isEmpty())
    }

    @Test
    fun alCambiarDeServidorSeReenvíaTodoComoNuevo() {
        config.servidorSincronizado = "https://otro-servidor.example"
        config.ultimaRevision = 99
        local("uid-1", revision = 5, pendiente = false)
        local("uid-borrada", revision = 5, eliminado = true)
        manejador = { p ->
            if (p.metodo == "POST") resultadoPush(Triple("uid-1", "ok", dtoServidor("uid-1", 1))) else pull(revision = 1)
        }

        sincronizar()

        val items = cuerpoJson(peticiones.single { it.metodo == "POST" }).getAsJsonArray("items")
        assertEquals("la borrada se descarta y la viva se reenvía", 1L, items.size().toLong())
        assertEquals(0, items[0].asJsonObject["baseRevision"].asInt)
        assertEquals(config.url, config.servidorSincronizado)
        assertNull(almacen.ficha("uid-borrada"))
        assertEquals(1, config.ultimaRevision)
    }

    // ---------------------------------------------------------------- conversión

    @Test
    fun laConversionUsaNombresDeFotoEnElServidorYRutasEnElMovil() {
        val e = EpiEntity(uid = "u", parteCuerpo = "Cabeza", nombreEpi = "Casco", fotos = "/a/b/x.jpg|/a/b/y.jpg", revision = 4)
        val dto = e.aDto()
        assertEquals(listOf("x.jpg", "y.jpg"), dto.fotos)
        assertEquals(4, dto.baseRevision)

        val vuelta = dto.copy(revision = 6).aEntidad(existente = e.copy(id = 12), directorioFotos = "/data/fotos")
        assertEquals(12, vuelta.id)
        assertEquals(6, vuelta.revision)
        assertFalse(vuelta.pendiente)
        assertEquals("/data/fotos/x.jpg|/data/fotos/y.jpg", vuelta.fotos.replace('\\', '/'))
    }
}
