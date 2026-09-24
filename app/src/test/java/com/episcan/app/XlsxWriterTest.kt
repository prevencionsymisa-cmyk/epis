package com.episcan.app

import com.episcan.app.export.FilaExcel
import com.episcan.app.export.Miniatura
import com.episcan.app.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {
    // JPEG real de 120x90 px (src/test/resources)
    private val jpegMinimo: ByteArray =
        javaClass.getResourceAsStream("/miniatura.jpg")!!.use { it.readBytes() }

    private fun fila(n: Int, conFoto: Boolean) = FilaExcel(
        parteCuerpo = "Manos y Brazos",
        nombreEpi = "Guantes de protección mecánica $n & <precisión>",
        observaciones = "Talla 9, color gris\ncon puños",
        marca = "RECA",
        modelo = "PROTECT 204",
        normativa = "EN 388:2016 · CE Cat. II",
        simbolos = "Abrasión nivel 4\nCorte nivel 1\nDesgarro nivel 2\nPunción nivel 3\u0001",
        distribuidor = "",
        miniatura = if (conFoto) Miniatura(jpegMinimo, 120, 90) else null,
    )

    private fun generar(filas: List<FilaExcel>): Map<String, ByteArray> {
        val salida = ByteArrayOutputStream()
        XlsxWriter.escribir(salida, filas, "24/09/2026 10:00")
        val partes = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(salida.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { partes[it.name] = zip.readBytes() }
        }
        // Volcado opcional para inspeccionar con otras herramientas (openpyxl, Excel…)
        System.getProperty("xlsx.salida")?.takeIf { it.isNotBlank() && filas.isNotEmpty() }?.let { File(it).writeBytes(salida.toByteArray()) }
        return partes
    }

    @Test
    fun todasLasPartesXmlSonBienFormadas() {
        val partes = generar(listOf(fila(1, true), fila(2, false), fila(3, true)))
        val fabrica = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        partes.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (nombre, bytes) ->
            try {
                fabrica.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                throw AssertionError("XML mal formado en $nombre: ${e.message}")
            }
        }
    }

    @Test
    fun incluyeEstructuraEsperadaConImagenes() {
        val partes = generar(listOf(fila(1, true), fila(2, false), fila(3, true)))
        assertEquals("[Content_Types].xml", partes.keys.first())
        listOf("xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/drawings/drawing1.xml",
            "xl/media/image1.jpg", "xl/media/image2.jpg").forEach { assertTrue("falta $it", it in partes) }
        assertTrue("sobra image3", "xl/media/image3.jpg" !in partes)

        val hoja = partes.getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(hoja.contains("REGISTRO TÉCNICO Y HOMOLOGACIÓN DE EQUIPOS DE PROTECCIÓN INDIVIDUAL (EPI)"))
        assertTrue(hoja.contains("Total de EPIs registrados: 3"))
        assertTrue(hoja.contains("""<pane ySplit="4" topLeftCell="A5""""))
        assertTrue(hoja.contains("""<autoFilter ref="A4:H7"/>"""))
        assertTrue("escapa & y <", hoja.contains("&amp; &lt;precisión&gt;"))
        assertTrue("elimina caracteres de control", !hoja.contains('\u0001'))
        // La miniatura de la fila 3 (índice 2) cuelga de la fila 7 (base 0 = 6)
        assertTrue(partes.getValue("xl/drawings/drawing1.xml").decodeToString().contains("<xdr:row>6</xdr:row>"))
    }

    @Test
    fun sinFilasGeneraSoloCabecera() {
        val partes = generar(emptyList())
        assertTrue("xl/drawings/drawing1.xml" !in partes)
        val hoja = partes.getValue("xl/worksheets/sheet1.xml").decodeToString()
        assertTrue(hoja.contains("Total de EPIs registrados: 0"))
        assertTrue(hoja.contains("""<autoFilter ref="A4:H4"/>"""))
    }
}
