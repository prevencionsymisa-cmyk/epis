package com.episcan.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Miniatura JPEG ya reducida, lista para incrustar en el Excel. */
class Miniatura(val bytes: ByteArray, val ancho: Int, val alto: Int)

class FilaExcel(
    val parteCuerpo: String,
    val nombreEpi: String,
    val observaciones: String,
    val marca: String,
    val modelo: String,
    val normativa: String,
    val simbolos: String,
    val distribuidor: String,
    val miniatura: Miniatura?,
)

/**
 * Generador de .xlsx (Office Open XML) sin dependencias: Apache POI no funciona bien en Android
 * (usa java.awt) y aquí solo se necesita una hoja con estilos, filtros e imágenes.
 */
object XlsxWriter {
    private val ENCABEZADOS = listOf(
        "Parte del cuerpo que protege",
        "Nombre del EPI",
        "Marca / Fabricante",
        "Modelo / Referencia",
        "Normativa(s)",
        "Símbolos, pictogramas y significado",
        "Distribuidor / Proveedor",
        "Miniatura",
    )
    private val ANCHOS = listOf(20.0, 34.0, 18.0, 20.0, 26.0, 62.0, 22.0, 20.0)
    private val ESTILO_CABECERA = listOf(S_CAB_NARANJA, S_CAB_PIZARRA, S_CAB_GRIS, S_CAB_GRIS, S_CAB_GRIS, S_CAB_INDIGO, S_CAB_GRIS, S_CAB_GRIS)

    private const val FILA_CABECERA = 4
    private const val PRIMERA_FILA_DATOS = 5
    private const val ALTO_MIN_CON_FOTO = 78.0 // pt: cabe una miniatura de 90 px
    private const val ALTO_MIN_SIN_FOTO = 30.0
    private const val ALTO_MAX = 409.0 // límite de Excel
    private const val EMU_POR_PIXEL = 9525L
    private const val MARGEN_FOTO_PX = 6

    private val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun letra(indice: Int) = ('A' + indice).toString()

    fun escribir(salida: OutputStream, filas: List<FilaExcel>, fechaEmision: String) {
        val conFoto = filas.withIndex().filter { it.value.miniatura != null }
        val ultimaFila = max(FILA_CABECERA, PRIMERA_FILA_DATOS + filas.size - 1)

        ZipOutputStream(salida).use { zip ->
            fun entrada(nombre: String, contenido: ByteArray) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido)
                zip.closeEntry()
            }
            fun entrada(nombre: String, contenido: String) = entrada(nombre, contenido.toByteArray(Charsets.UTF_8))

            entrada("[Content_Types].xml", contentTypes(conFoto.isNotEmpty()))
            entrada("_rels/.rels", relsRaiz())
            entrada("xl/workbook.xml", workbook(ultimaFila))
            entrada("xl/_rels/workbook.xml.rels", relsWorkbook())
            entrada("xl/styles.xml", estilos())
            entrada("xl/worksheets/sheet1.xml", hoja(filas, fechaEmision, ultimaFila, conFoto.isNotEmpty()))
            if (conFoto.isNotEmpty()) {
                entrada("xl/worksheets/_rels/sheet1.xml.rels", relsHoja())
                entrada("xl/drawings/drawing1.xml", dibujo(conFoto))
                entrada("xl/drawings/_rels/drawing1.xml.rels", relsDibujo(conFoto.size))
                conFoto.forEachIndexed { i, f -> entrada("xl/media/image${i + 1}.jpg", f.value.miniatura!!.bytes) }
            }
        }
    }

    // ---------------------------------------------------------------- partes del paquete

    private fun contentTypes(hayImagenes: Boolean) = xml(
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Default Extension="jpg" ContentType="image/jpeg"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
${if (hayImagenes) """<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""" else ""}
</Types>""",
    )

    private fun relsRaiz() = xml(
        """<Relationships xmlns="$NS_PKG_REL">
<Relationship Id="rId1" Type="$NS_REL/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""",
    )

    private fun workbook(ultimaFila: Int) = xml(
        """<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL">
<bookViews><workbookView xWindow="0" yWindow="0" windowWidth="28800" windowHeight="15000"/></bookViews>
<sheets><sheet name="EPIs" sheetId="1" r:id="rId1"/></sheets>
<definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">EPIs!${'$'}A${'$'}$FILA_CABECERA:${'$'}${letra(ENCABEZADOS.size - 1)}${'$'}$ultimaFila</definedName></definedNames>
</workbook>""",
    )

    private fun relsWorkbook() = xml(
        """<Relationships xmlns="$NS_PKG_REL">
<Relationship Id="rId1" Type="$NS_REL/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="$NS_REL/styles" Target="styles.xml"/>
</Relationships>""",
    )

    private fun relsHoja() = xml(
        """<Relationships xmlns="$NS_PKG_REL">
<Relationship Id="rId1" Type="$NS_REL/drawing" Target="../drawings/drawing1.xml"/>
</Relationships>""",
    )

    private fun relsDibujo(n: Int) = xml(
        """<Relationships xmlns="$NS_PKG_REL">
${(1..n).joinToString("\n") { """<Relationship Id="rId$it" Type="$NS_REL/image" Target="../media/image$it.jpg"/>""" }}
</Relationships>""",
    )

    // ---------------------------------------------------------------- estilos

    private const val S_BANNER = 1
    private const val S_SUBTITULO = 2
    private const val S_CAB_NARANJA = 3
    private const val S_CAB_PIZARRA = 4
    private const val S_CAB_GRIS = 5
    private const val S_CAB_INDIGO = 6
    private const val S_DATO = 7
    private const val S_DATO_ZEBRA = 8
    private const val S_DATO_NEGRITA = 9
    private const val S_DATO_ZEBRA_NEGRITA = 10

    private fun estilos(): String {
        fun fill(rgb: String) = """<fill><patternFill patternType="solid"><fgColor rgb="FF$rgb"/><bgColor indexed="64"/></patternFill></fill>"""
        val borde = """<border><left style="thin"><color rgb="FFCBD5E1"/></left><right style="thin"><color rgb="FFCBD5E1"/></right><top style="thin"><color rgb="FFCBD5E1"/></top><bottom style="thin"><color rgb="FFCBD5E1"/></bottom><diagonal/></border>"""
        fun xf(font: Int, fill: Int, border: Int, alineacion: String) =
            """<xf numFmtId="0" fontId="$font" fillId="$fill" borderId="$border" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">$alineacion</xf>"""

        val centroEnvuelto = """<alignment horizontal="center" vertical="center" wrapText="1"/>"""
        val datos = """<alignment horizontal="left" vertical="top" wrapText="1"/>"""

        return xml(
            """<styleSheet xmlns="$NS_MAIN">
<fonts count="5">
<font><sz val="10"/><color rgb="FF0F172A"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="14"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>
<font><i/><sz val="10"/><color rgb="FF475569"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="10"/><color rgb="FF0F172A"/><name val="Calibri"/><family val="2"/></font>
</fonts>
<fills count="10">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
${fill("0F172A")}
${fill("EA580C")}
${fill("1E293B")}
${fill("334155")}
${fill("4338CA")}
${fill("F8FAFC")}
${fill("FFFFFF")}
${fill("E2E8F0")}
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
$borde
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="11">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
${xf(1, 2, 0, centroEnvuelto)}
${xf(3, 9, 0, """<alignment horizontal="left" vertical="center" indent="1"/>""")}
${xf(2, 3, 1, centroEnvuelto)}
${xf(2, 4, 1, centroEnvuelto)}
${xf(2, 5, 1, centroEnvuelto)}
${xf(2, 6, 1, centroEnvuelto)}
${xf(0, 8, 1, datos)}
${xf(0, 7, 1, datos)}
${xf(4, 8, 1, datos)}
${xf(4, 7, 1, datos)}
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""",
        )
    }

    // ---------------------------------------------------------------- hoja

    private fun hoja(filas: List<FilaExcel>, fechaEmision: String, ultimaFila: Int, hayImagenes: Boolean): String {
        val nCols = ENCABEZADOS.size
        val ultima = letra(nCols - 1)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        sb.append("""<worksheet xmlns="$NS_MAIN" xmlns:r="$NS_REL">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        sb.append("""<dimension ref="A1:$ultima$ultimaFila"/>""")
        sb.append(
            """<sheetViews><sheetView showGridLines="0" tabSelected="1" workbookViewId="0">""" +
                """<pane ySplit="$FILA_CABECERA" topLeftCell="A$PRIMERA_FILA_DATOS" activePane="bottomLeft" state="frozen"/>""" +
                """<selection pane="bottomLeft" activeCell="A$PRIMERA_FILA_DATOS" sqref="A$PRIMERA_FILA_DATOS"/></sheetView></sheetViews>""",
        )
        sb.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        sb.append("<cols>")
        ANCHOS.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols>")

        sb.append("<sheetData>")
        // Fila 1: banner
        sb.append("""<row r="1" ht="30" customHeight="1">""")
        sb.append(celda("A1", S_BANNER, "REGISTRO TÉCNICO Y HOMOLOGACIÓN DE EQUIPOS DE PROTECCIÓN INDIVIDUAL (EPI)"))
        for (c in 1 until nCols) sb.append(celda("${letra(c)}1", S_BANNER, null))
        sb.append("</row>")
        // Fila 2: subtítulo con fecha y total
        sb.append("""<row r="2" ht="20" customHeight="1">""")
        sb.append(celda("A2", S_SUBTITULO, "Fecha de emisión: $fechaEmision   ·   Total de EPIs registrados: ${filas.size}"))
        for (c in 1 until nCols) sb.append(celda("${letra(c)}2", S_SUBTITULO, null))
        sb.append("</row>")
        sb.append("""<row r="3" ht="8" customHeight="1"/>""")
        // Fila 4: cabeceras
        sb.append("""<row r="$FILA_CABECERA" ht="34" customHeight="1">""")
        ENCABEZADOS.forEachIndexed { c, t -> sb.append(celda("${letra(c)}$FILA_CABECERA", ESTILO_CABECERA[c], t)) }
        sb.append("</row>")

        // Filas de datos con bandas alternas
        filas.forEachIndexed { i, f ->
            val r = PRIMERA_FILA_DATOS + i
            val zebra = i % 2 == 1
            val normal = if (zebra) S_DATO_ZEBRA else S_DATO
            val negrita = if (zebra) S_DATO_ZEBRA_NEGRITA else S_DATO_NEGRITA
            sb.append("""<row r="$r" ht="${altoFila(f)}" customHeight="1">""")
            sb.append(celda("A$r", negrita, f.parteCuerpo))
            sb.append(celdaNombre("B$r", normal, f.nombreEpi, f.observaciones))
            sb.append(celda("C$r", normal, f.marca))
            sb.append(celda("D$r", normal, f.modelo))
            sb.append(celda("E$r", normal, f.normativa))
            sb.append(celda("F$r", normal, f.simbolos))
            sb.append(celda("G$r", normal, f.distribuidor))
            sb.append(celda("H$r", normal, null)) // aquí flota la miniatura
            sb.append("</row>")
        }
        sb.append("</sheetData>")

        sb.append("""<autoFilter ref="A$FILA_CABECERA:$ultima$ultimaFila"/>""")
        sb.append("""<mergeCells count="2"><mergeCell ref="A1:${ultima}1"/><mergeCell ref="A2:${ultima}2"/></mergeCells>""")
        sb.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
        if (hayImagenes) sb.append("""<drawing r:id="rId1"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    /** Altura adaptativa: estima las líneas que ocupa el texto envuelto en cada columna. */
    private fun altoFila(f: FilaExcel): Double {
        val textos = listOf(
            f.parteCuerpo,
            if (f.observaciones.isBlank()) f.nombreEpi else f.nombreEpi + "\n" + f.observaciones,
            f.marca, f.modelo, f.normativa, f.simbolos, f.distribuidor,
        )
        val lineas = textos.mapIndexed { i, t -> lineasEstimadas(t, ANCHOS[i]) }.max()
        val minimo = if (f.miniatura != null) ALTO_MIN_CON_FOTO else ALTO_MIN_SIN_FOTO
        return min(ALTO_MAX, max(minimo, lineas * 13.5 + 6))
    }

    private fun lineasEstimadas(texto: String, anchoCol: Double): Int {
        val caracteresPorLinea = max(1.0, anchoCol * 1.1)
        return texto.split('\n').sumOf { max(1, ceil(it.length / caracteresPorLinea).toInt()) }
    }

    private fun celda(ref: String, estilo: Int, texto: String?): String =
        if (texto.isNullOrEmpty()) """<c r="$ref" s="$estilo"/>"""
        else """<c r="$ref" s="$estilo" t="inlineStr"><is><t xml:space="preserve">${esc(texto)}</t></is></c>"""

    /** Nombre en negrita y las notas descriptivas debajo, en gris y más pequeñas. */
    private fun celdaNombre(ref: String, estilo: Int, nombre: String, notas: String): String {
        if (nombre.isEmpty() && notas.isEmpty()) return """<c r="$ref" s="$estilo"/>"""
        val sb = StringBuilder("""<c r="$ref" s="$estilo" t="inlineStr"><is>""")
        sb.append("""<r><rPr><b/><sz val="10"/><color rgb="FF0F172A"/><rFont val="Calibri"/><family val="2"/></rPr><t xml:space="preserve">${esc(nombre)}</t></r>""")
        if (notas.isNotEmpty()) {
            sb.append("""<r><rPr><i/><sz val="9"/><color rgb="FF64748B"/><rFont val="Calibri"/><family val="2"/></rPr><t xml:space="preserve">${esc("\n$notas")}</t></r>""")
        }
        sb.append("</is></c>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- imágenes

    private fun dibujo(conFoto: List<IndexedValue<FilaExcel>>): String {
        val colFoto = ENCABEZADOS.size - 1
        val margen = MARGEN_FOTO_PX * EMU_POR_PIXEL
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        sb.append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="$NS_REL">""")
        conFoto.forEachIndexed { n, item ->
            val m = item.value.miniatura!!
            val cx = m.ancho * EMU_POR_PIXEL
            val cy = m.alto * EMU_POR_PIXEL
            val fila = PRIMERA_FILA_DATOS - 1 + item.index // base 0
            // twoCellAnchor: la imagen se oculta con la fila al filtrar y se reordena al ordenar
            sb.append("""<xdr:twoCellAnchor editAs="twoCell">""")
            sb.append("""<xdr:from><xdr:col>$colFoto</xdr:col><xdr:colOff>$margen</xdr:colOff><xdr:row>$fila</xdr:row><xdr:rowOff>$margen</xdr:rowOff></xdr:from>""")
            sb.append("""<xdr:to><xdr:col>$colFoto</xdr:col><xdr:colOff>${margen + cx}</xdr:colOff><xdr:row>$fila</xdr:row><xdr:rowOff>${margen + cy}</xdr:rowOff></xdr:to>""")
            sb.append("""<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="${n + 2}" name="Foto ${n + 1}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""")
            sb.append("""<xdr:blipFill><a:blip r:embed="rId${n + 1}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            sb.append("""<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>""")
            sb.append("<xdr:clientData/></xdr:twoCellAnchor>")
        }
        sb.append("</xdr:wsDr>")
        return sb.toString()
    }

    // ---------------------------------------------------------------- utilidades XML

    private fun xml(cuerpo: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n" + cuerpo

    /** Escapa XML y elimina caracteres de control que harían inválido el archivo. */
    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\n' || ch == '\t' -> sb.append(ch)
                ch.code < 0x20 -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
