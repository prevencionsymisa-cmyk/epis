package com.episcan.app

import com.episcan.app.data.FormatoSimbolos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoSimbolosTest {
    // Texto real que devolvió la IA para unos guantes JUBA: todo en un solo párrafo
    private val parrafoSeguido =
        "EN 388:2016+A1:2018 (3110X): Abrasión nivel 3, Corte por cuchilla nivel 1, Desgarro nivel 1, Punción nivel 0, " +
            "Corte TDM nivel X. EN ISO 374-1:2016+A1:2018 TIPO A (AKLMNPST): Protección química con permeación >= 30 min " +
            "frente a A (Metanol), K (Hidróxido de sodio 40%), L (Ácido sulfúrico 96%), M (Ácido nítrico 65%), " +
            "N (Ácido acético 99%), P (Peróxido de hidrógeno 30%), S (Fluoruro de hidrógeno 40%) y T (Formaldehído 37%). " +
            "EN ISO 374-5:2016: Protección contra microorganismos, incluyendo bacterias, hongos y virus (VIRUS). " +
            "CE 0598 Cat. III: Cumplimiento del Reglamento UE 2016/425 para riesgos mortales o irreversibles, supervisado " +
            "por el Organismo Notificado 0598 (SGS Fimko Oy). Pictograma 'i': Exige leer el manual de instrucciones antes de su uso."

    @Test
    fun separaUnParrafoSeguidoEnUnBloquePorNorma() {
        val b = FormatoSimbolos.parsear(parrafoSeguido)
        assertEquals(5, b.size)
        assertEquals(
            listOf("EN 388:2016+A1:2018 (3110X)", "EN ISO 374-1:2016+A1:2018 TIPO A (AKLMNPST)", "EN ISO 374-5:2016", "CE 0598 Cat. III", "Pictograma 'i'"),
            b.map { it.titulo },
        )
    }

    @Test
    fun laNormaEn388QuedaComoListaDeCincoConceptos() {
        val en388 = FormatoSimbolos.parsear(parrafoSeguido)[0]
        assertEquals(
            listOf("Abrasión nivel 3", "Corte por cuchilla nivel 1", "Desgarro nivel 1", "Punción nivel 0", "Corte TDM nivel X"),
            en388.puntos,
        )
        assertNull(en388.texto)
    }

    @Test
    fun lasSustanciasQuimicasSeSeparanIncluidaLaUltimaConY() {
        val en374 = FormatoSimbolos.parsear(parrafoSeguido)[1]
        assertEquals(8, en374.puntos.size)
        assertEquals("Protección química con permeación >= 30 min frente a A (Metanol)", en374.puntos.first())
        assertEquals("S (Fluoruro de hidrógeno 40%)", en374.puntos[6])
        assertEquals("T (Formaldehído 37%)", en374.puntos.last())
    }

    @Test
    fun unaFraseConComasNoSeConvierteEnLista() {
        val microorganismos = FormatoSimbolos.parsear(parrafoSeguido)[2]
        assertTrue(microorganismos.puntos.isEmpty())
        assertEquals("Protección contra microorganismos, incluyendo bacterias, hongos y virus (VIRUS).", microorganismos.texto)
    }

    @Test
    fun elPuntoDeCatNoPartaLaFrase() {
        val ce = FormatoSimbolos.parsear(parrafoSeguido)[3]
        assertEquals("CE 0598 Cat. III", ce.titulo)
        assertTrue(ce.texto!!.startsWith("Cumplimiento del Reglamento UE 2016/425"))
    }

    @Test
    fun entiendeElFormatoConLineasYGuiones() {
        val texto = """
            EN 388:2016+A1:2018 (3110X)
            - Abrasión: nivel 3
            - Corte por cuchilla: nivel 1

            Pictograma de manual
            - Libro abierto con "i": leer las instrucciones
        """.trimIndent()
        val b = FormatoSimbolos.parsear(texto)
        assertEquals(2, b.size)
        assertEquals("EN 388:2016+A1:2018 (3110X)", b[0].titulo)
        assertEquals(listOf("Abrasión: nivel 3", "Corte por cuchilla: nivel 1"), b[0].puntos)
        assertEquals("Pictograma de manual", b[1].titulo)
        assertEquals(1, b[1].puntos.size)
    }

    @Test
    fun entiendeUnTituloConDosPuntosFinalYGuiones() {
        val texto = "EN 388 (Protección contra riesgos mecánicos) - Código 3X42B:\n- Resistencia a la abrasión: Nivel 3\n- Resistencia a la punción: Nivel 2"
        val b = FormatoSimbolos.parsear(texto)
        assertEquals(1, b.size)
        assertEquals("EN 388 (Protección contra riesgos mecánicos) - Código 3X42B", b[0].titulo)
        assertEquals(2, b[0].puntos.size)
    }

    @Test
    fun textoVacioNoDevuelveBloques() {
        assertTrue(FormatoSimbolos.parsear("").isEmpty())
        assertTrue(FormatoSimbolos.parsear("  \n  ").isEmpty())
    }

    @Test
    fun textoSinEstructuraSeMuestraTalCual() {
        val b = FormatoSimbolos.parsear("Sin marcado legible")
        assertEquals(1, b.size)
        assertNull(b[0].titulo)
        assertEquals("Sin marcado legible", b[0].texto)
    }

    @Test
    fun elTextoPlanoParaExcelTieneUnaLineaPorConcepto() {
        val plano = FormatoSimbolos.aTextoPlano(FormatoSimbolos.parsear(parrafoSeguido))
        val lineas = plano.lines()
        assertEquals("EN 388:2016+A1:2018 (3110X)", lineas[0])
        assertEquals("• Abrasión nivel 3", lineas[1])
        assertTrue("una línea en blanco entre normas", lineas.contains(""))
        assertTrue(plano.contains("CE 0598 Cat. III: Cumplimiento del Reglamento"))
    }
}
