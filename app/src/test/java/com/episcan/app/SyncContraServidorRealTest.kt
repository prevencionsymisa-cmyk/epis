package com.episcan.app

import com.episcan.app.data.local.DocumentoAdjunto
import com.episcan.app.data.local.EpiEntity
import com.episcan.app.sync.SyncManager
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prueba de integración: dos "móviles" y la web usan el SERVIDOR REAL (Node + Postgres) a la vez.
 * Solo se ejecuta si se define EPIS_SERVER_URL (y EPIS_TOKEN / EPIS_TOKEN_WEB); si no, se omite. Para lanzarla:
 *   cd server && node test/servidor-de-prueba.js        (deja el servidor escuchando en 127.0.0.1:3999)
 *   EPIS_SERVER_URL=http://127.0.0.1:3999 EPIS_TOKEN=... EPIS_TOKEN_WEB=... ./gradlew testDebugUnitTest --tests "*ContraServidorReal*"
 */
class SyncContraServidorRealTest {
    private val url = System.getenv("EPIS_SERVER_URL")
    private val tokenMovil = System.getenv("EPIS_TOKEN").orEmpty()
    private val tokenWeb = System.getenv("EPIS_TOKEN_WEB").orEmpty()
    private val http = OkHttpClient()

    /** Un móvil completo: su propia base de datos, sus fotos y su configuración. */
    private inner class Movil {
        val almacen = AlmacenFalso()
        val fotos = FotosFalsas()
        val documentos = FotosFalsas()
        val config = ConfigFalsa(url = url ?: "", token = tokenMovil)
        val sync = SyncManager(OkHttpClient(), almacen, fotos, documentos, config, permitirHttp = true)

        fun sincronizar() = runBlocking { sync.sincronizar() }

        fun nuevaFicha(nombre: String, fotoId: String? = null, foto: ByteArray = byteArrayOf(), pdf: Pair<DocumentoAdjunto, ByteArray>? = null) {
            if (fotoId != null) fotos.archivos[fotoId] = foto
            if (pdf != null) documentos.archivos[pdf.first.id] = pdf.second
            almacen.agregar(
                EpiEntity(
                    parteCuerpo = "Manos y Brazos", subcategoria = "Protección mecánica", nombreEpi = nombre, marca = "RECA",
                    fotos = if (fotoId != null) "/data/fotos/$fotoId" else "", actualizadoEn = System.nanoTime(),
                    documentos = EpiEntity.documentosAJson(listOfNotNull(pdf?.first)),
                ),
            )
        }

        fun editar(nombre: String, cambio: (EpiEntity) -> EpiEntity) {
            val i = almacen.filas.indexOfFirst { it.nombreEpi == nombre }
            almacen.filas[i] = cambio(almacen.filas[i]).copy(pendiente = true, actualizadoEn = System.nanoTime())
        }

        fun borrar(nombre: String) {
            val i = almacen.filas.indexOfFirst { it.nombreEpi == nombre }
            val f = almacen.filas[i]
            if (f.revision == 0L) almacen.filas.removeAt(i)
            else almacen.filas[i] = f.copy(eliminado = true, pendiente = true, actualizadoEn = System.nanoTime())
        }

        fun ficha(nombre: String) = almacen.filas.firstOrNull { it.nombreEpi == nombre && !it.eliminado }
    }

    private fun web(metodo: String, ruta: String, json: String? = null): Pair<Int, String> {
        val cuerpo = json?.toRequestBody("application/json".toMediaType())
        val peticion = Request.Builder().url("$url$ruta").header("Authorization", "Bearer $tokenWeb").method(metodo, cuerpo).build()
        http.newCall(peticion).execute().use { return it.code to it.body?.string().orEmpty() }
    }

    @Test
    fun dosMovilesYLaWebSeSincronizanConElServidorReal() {
        assumeTrue("EPIS_SERVER_URL no definida: prueba omitida", !url.isNullOrBlank())
        val id = System.nanoTime().toString() // así la prueba se puede repetir sobre el mismo servidor
        val a = Movil()
        val b = Movil()
        val foto = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

        val pdf = "%PDF-1.4\n% ficha técnica de prueba\n%%EOF\n".toByteArray(Charsets.ISO_8859_1)
        val doc = DocumentoAdjunto(id = "doc_a1-$id-000.pdf", nombre = "Ficha técnica RECA.pdf")

        // 1. El móvil A crea dos fichas (una con foto y PDF) y las envía
        a.nuevaFicha("Guantes A1 $id", fotoId = "epi_a1-$id-000.jpg", foto = foto, pdf = doc to pdf)
        a.nuevaFicha("Guantes A2 $id")
        val r1 = a.sincronizar()
        assertTrue(r1.error, r1.correcta)
        assertEquals(2, r1.enviadas)
        assertTrue("todo enviado", a.almacen.filas.none { it.pendiente })
        assertTrue("revisiones asignadas por el servidor", a.almacen.filas.all { it.revision > 0 })

        // 2. El móvil B recibe las dos fichas y descarga la foto (byte a byte igual)
        val r2 = b.sincronizar()
        assertTrue(r2.error, r2.correcta)
        assertNotNull(b.ficha("Guantes A1 $id"))
        assertNotNull(b.ficha("Guantes A2 $id"))
        assertArrayEquals(foto, b.fotos.archivos["epi_a1-$id-000.jpg"])
        assertEquals(listOf(doc), b.ficha("Guantes A1 $id")!!.listaDocumentos())
        assertArrayEquals("y descarga el PDF", pdf, b.documentos.archivos[doc.id])

        // 3. B edita una ficha y A recibe el cambio
        b.editar("Guantes A2 $id") { it.copy(marca = "Editada por B") }
        assertTrue(b.sincronizar().correcta)
        assertTrue(a.sincronizar().correcta)
        assertEquals("Editada por B", a.ficha("Guantes A2 $id")!!.marca)

        // 4. La web edita (con la revisión que ve) y el móvil A lo recibe
        val lista = JsonParser.parseString(web("GET", "/api/epis?q=${"Guantes A1 $id".replace(" ", "%20")}").second).asJsonObject
        val enWeb = lista.getAsJsonArray("items")[0].asJsonObject
        assertEquals("Manos y Brazos", enWeb["parteCuerpo"].asString)
        enWeb.addProperty("baseRevision", enWeb["revision"].asInt)
        enWeb.addProperty("fichaTecnica", "FT-DESDE-LA-WEB")
        val (codigoPut, _) = web("PUT", "/api/epis/${enWeb["uid"].asString}", enWeb.toString())
        assertEquals(200, codigoPut)
        assertTrue(a.sincronizar().correcta)
        assertEquals("FT-DESDE-LA-WEB", a.ficha("Guantes A1 $id")!!.fichaTecnica)
        assertEquals("la edición de la web conserva el PDF", listOf(doc), a.ficha("Guantes A1 $id")!!.listaDocumentos())

        // 5. Conflicto: A y B editan la misma ficha sin conexión; B sincroniza primero; gana el servidor (B) y A se entera
        assertTrue(b.sincronizar().correcta) // B se pone al día con lo de la web
        a.editar("Guantes A2 $id") { it.copy(modelo = "Modelo de A") }
        b.editar("Guantes A2 $id") { it.copy(modelo = "Modelo de B") }
        assertTrue(b.sincronizar().correcta)
        val rConflicto = a.sincronizar()
        assertTrue(rConflicto.error, rConflicto.correcta)
        assertEquals(listOf("Guantes A2 $id"), rConflicto.conflictos)
        assertEquals("Modelo de B", a.ficha("Guantes A2 $id")!!.modelo)
        assertTrue(a.almacen.filas.none { it.pendiente })

        // 6. A borra una ficha y B se entera; la web ya no la lista
        a.borrar("Guantes A1 $id")
        assertTrue(a.sincronizar().correcta)
        assertTrue(a.almacen.filas.none { it.nombreEpi == "Guantes A1 $id" })
        assertTrue(b.sincronizar().correcta)
        assertNull(b.ficha("Guantes A1 $id"))
        assertNull("sus fotos también se borran del móvil B", b.fotos.archivos["epi_a1-$id-000.jpg"])
        assertNull("y sus PDF", b.documentos.archivos[doc.id])
        val tras = JsonParser.parseString(web("GET", "/api/epis?q=Guantes%20A1%20$id").second).asJsonObject
        assertEquals(0, tras["total"].asInt)

        // 7. Una ficha creada en la web aparece en los móviles
        val (codigoPost, cuerpo) = web(
            "POST", "/api/epis",
            """{"parteCuerpo":"Cabeza","subcategoria":"Casco de protección industrial","nombreEpi":"Casco desde la web $id","marca":"Delta"}""",
        )
        assertEquals(201, codigoPost)
        assertTrue(cuerpo.contains("Casco desde la web"))
        assertTrue(a.sincronizar().correcta)
        assertEquals("Delta", a.ficha("Casco desde la web $id")!!.marca)

        // 8. Sin nada nuevo, una sincronización no envía ni recibe nada
        val alDia = a.sincronizar()
        assertEquals(0, alDia.enviadas)
        assertEquals(0, alDia.recibidas)
    }
}
