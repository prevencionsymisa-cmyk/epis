package com.episcan.app

import com.episcan.app.data.ClasificadorSubcategoria
import com.episcan.app.data.PARTES_CUERPO
import com.episcan.app.data.SUBCATEGORIAS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClasificadorSubcategoriaTest {
    private fun c(zona: String, nombre: String, norma: String = "") = ClasificadorSubcategoria.clasificar(zona, nombre, norma)

    @Test
    fun todasLasReglasApuntanASubcategoriasQueExisten() {
        // Evita erratas: una regla con un texto que no esté en SUBCATEGORIAS dejaría el filtro roto
        ClasificadorSubcategoria.subcategoriasUsadas().forEach { (zona, usadas) ->
            usadas.forEach { assertTrue("$zona: «$it» no existe en SUBCATEGORIAS", it in SUBCATEGORIAS.getValue(zona)) }
        }
        assertEquals("hay reglas para las 8 zonas", PARTES_CUERPO.toSet(), ClasificadorSubcategoria.subcategoriasUsadas().keys)
    }

    @Test
    fun guantesMecanicosYDelFrioSonMecanicosPorAparecerPrimero() {
        assertEquals("Protección mecánica", c("Manos y Brazos", "Guante de protección contra riesgos mecánicos y el frío"))
    }

    @Test
    fun guantesQuimicosYMecanicosSonQuimicosPorAparecerPrimero() {
        assertEquals(
            "Protección química",
            c("Manos y Brazos", "Guantes de protección contra productos químicos, microorganismos y riesgos mecánicos"),
        )
    }

    @Test
    fun siElNombreNoDiceNadaSeUsaLaNormativa() {
        assertEquals("Protección mecánica", c("Manos y Brazos", "Guantes de protección", "EN 388:2016 · EN 511 · CE"))
        assertEquals("Protección térmica (calor o frío)", c("Manos y Brazos", "Guantes de trabajo", "EN 407:2020"))
    }

    @Test
    fun elNombreTienePrioridadSobreLaNormativa() {
        assertEquals("Protección química", c("Manos y Brazos", "Guantes contra productos químicos", "EN 388:2016 · EN ISO 374-1"))
    }

    @Test
    fun cascoDeAlturaEsDeMontanaAunqueDigaCasco() {
        assertEquals("Casco de montaña / trabajos en altura", c("Cabeza", "Casco de protección para trabajos en altura"))
        assertEquals("Casco de protección industrial", c("Cabeza", "Casco de protección para la industria"))
        assertEquals("Casco de protección industrial", c("Cabeza", "Casco de obra", "EN 397"))
    }

    @Test
    fun semimascaraFiltranteEsAutofiltranteYMediaMascaraConFiltrosNo() {
        assertEquals("Mascarilla autofiltrante (FFP)", c("Vías Respiratorias", "Semimáscara filtrante contra partículas"))
        assertEquals("Mascarilla autofiltrante (FFP)", c("Vías Respiratorias", "Mascarilla FFP2 NR D"))
        assertEquals("Media máscara con filtros", c("Vías Respiratorias", "Media máscara con filtros"))
        assertEquals("Máscara completa con filtros", c("Vías Respiratorias", "Máscara completa con filtros"))
    }

    @Test
    fun ojosYCara() {
        assertEquals("Gafas de montura universal", c("Ojos y Cara", "Gafas de protección ocular"))
        assertEquals("Gafas de montura integral", c("Ojos y Cara", "Gafas de montura integral"))
        assertEquals("Pantalla facial", c("Ojos y Cara", "Pantalla facial de protección"))
        assertEquals("Gafas o pantalla de soldadura", c("Ojos y Cara", "Pantalla de soldadura"))
    }

    @Test
    fun auditivaYPies() {
        assertEquals("Tapones auditivos", c("Auditiva", "Tapones de espuma"))
        assertEquals("Orejeras", c("Auditiva", "Orejeras de protección auditiva"))
        assertEquals("Orejeras acopladas a casco", c("Auditiva", "Orejeras acopladas a casco"))
        assertEquals("Calzado de seguridad (con puntera)", c("Pies y Piernas", "Calzado de seguridad", "EN ISO 20345 S3"))
        assertEquals("Botas impermeables / de agua", c("Pies y Piernas", "Botas de agua de PVC"))
    }

    @Test
    fun troncoYCaidas() {
        assertEquals("Ropa de alta visibilidad", c("Tronco y Abdomen", "Chaleco de alta visibilidad", "EN ISO 20471"))
        assertEquals("Arnés anticaídas", c("Cuerpo Entero / Caídas", "Arnés anticaídas de dos puntos"))
        assertEquals("Anticaídas retráctil o deslizante", c("Cuerpo Entero / Caídas", "Dispositivo anticaídas retráctil"))
    }

    @Test
    fun siNoReconoceNadaDevuelveNullYNoAdivina() {
        assertNull(c("Manos y Brazos", "Producto genérico", "CE"))
        assertNull(c("Manos y Brazos", "", ""))
        assertNull(c("Zona inventada", "Guantes mecánicos"))
    }
}
