package com.episcan.app

import com.episcan.app.data.remote.EpiAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class EpiAnalyzerTest {
    private fun json(parteCuerpo: String, subcategoria: String, fichaTecnica: String = "") = """
        {
          "parteCuerpo": "$parteCuerpo",
          "subcategoria": "$subcategoria",
          "nombreEpi": "Guantes de protección mecánica",
          "marca": "RECA",
          "modelo": "PROTECT 204",
          "normativa": "EN 388:2016",
          "simbolos": "Abrasión nivel 4",
          "fichaTecnica": "$fichaTecnica",
          "distribuidor": "",
          "observaciones": ""
        }
    """.trimIndent()

    @Test
    fun subcategoriaExactaSeConserva() {
        val r = EpiAnalyzer.parsear(json("Manos y Brazos", "Protección mecánica"))
        assertEquals("Protección mecánica", r.subcategoria)
    }

    @Test
    fun subcategoriaConVarianteDeMayusculasYAcentosSeNormaliza() {
        val r = EpiAnalyzer.parsear(json("Manos y Brazos", "proteccion  MECANICA"))
        assertEquals("Protección mecánica", r.subcategoria)
    }

    @Test
    fun subcategoriaQueNoEstaEnLaListaDeEsaZonaQuedaVacia() {
        // "Ropa ignífuga" es de Tronco y Abdomen, no de Manos y Brazos
        val r = EpiAnalyzer.parsear(json("Manos y Brazos", "Ropa ignífuga / soldadura"))
        assertEquals("", r.subcategoria)
    }

    @Test
    fun zonaNoReconocidaDejaLaSubcategoriaVacia() {
        val r = EpiAnalyzer.parsear(json("Torso", "Protección mecánica"))
        assertEquals("", r.parteCuerpo)
        assertEquals("", r.subcategoria)
    }

    @Test
    fun fichaTecnicaSeRecortaYPasaTalCual() {
        val r = EpiAnalyzer.parsear(json("Manos y Brazos", "Protección mecánica", fichaTecnica = "  FT-204-ES  "))
        assertEquals("FT-204-ES", r.fichaTecnica)
    }

    @Test
    fun otraEsUnaOpcionValidaEnCualquierZona() {
        val r = EpiAnalyzer.parsear(json("Cabeza", "Otra / no clasificada"))
        assertEquals("Otra / no clasificada", r.subcategoria)
    }
}
