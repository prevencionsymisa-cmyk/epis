package com.episcan.app

import com.episcan.app.data.FichaClave
import com.episcan.app.data.buscarDuplicado
import com.episcan.app.data.esDuplicado
import com.episcan.app.data.local.EpiEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicadosTest {
    private fun f(nombre: String = "", marca: String = "", modelo: String = "") = FichaClave(nombre, marca, modelo)

    @Test
    fun ignoraMayusculasAcentosEspaciosYGuiones() {
        assertTrue(esDuplicado(f(marca = "RECA", modelo = "PROTECT 204"), f(marca = "Reca ", modelo = "protect-204")))
        assertTrue(esDuplicado(f(marca = "Petzl", modelo = "Vértex Best"), f(marca = "PETZL", modelo = "vertex  best")))
    }

    @Test
    fun modelosDistintosNoSonDuplicado() {
        assertFalse(esDuplicado(f(marca = "RECA", modelo = "PROTECT 204"), f(marca = "RECA", modelo = "PROTECT 205")))
    }

    @Test
    fun mismoModeloConOtraMarcaNoSonDuplicado() {
        assertFalse(esDuplicado(f(marca = "3M", modelo = "9332+"), f(marca = "Moldex", modelo = "9332+")))
    }

    @Test
    fun sinMarcaSoloCuentaSiElModeloEsEspecifico() {
        assertTrue(esDuplicado(f(marca = "RECA", modelo = "PROTECT 204"), f(marca = "", modelo = "protect 204")))
        assertFalse("modelo genérico", esDuplicado(f(marca = "RECA", modelo = "204"), f(marca = "", modelo = "204")))
    }

    @Test
    fun sinModeloSeComparaNombreYMarca() {
        assertTrue(esDuplicado(f("Casco de obra", "Delta"), f("casco de  OBRA", "delta")))
        assertFalse(esDuplicado(f("Casco de obra", "Delta"), f("Casco de obra", "Otra")))
        assertFalse("un nombre vacío no coincide", esDuplicado(f("", "Delta"), f("", "Delta")))
    }

    @Test
    fun modeloPresenteEnUnoSoloNoCoincide() {
        assertFalse(esDuplicado(f("Guantes", "RECA", "PROTECT 204"), f("Guantes", "RECA", "")))
    }

    @Test
    fun buscarDuplicadoIgnoraLaFichaQueSeEdita() {
        val existentes = listOf(
            EpiEntity(id = 1, parteCuerpo = "Manos y Brazos", nombreEpi = "Guantes", marca = "RECA", modelo = "PROTECT 204"),
            EpiEntity(id = 2, parteCuerpo = "Cabeza", nombreEpi = "Casco", marca = "Delta", modelo = "X100 PRO"),
        )
        val candidata = f("Guantes", "reca", "Protect 204")
        assertEquals(1L, buscarDuplicado(candidata, existentes)?.id)
        assertNull("al editar la propia ficha no es duplicado de sí misma", buscarDuplicado(candidata, existentes, ignorarId = 1))
        assertNull(buscarDuplicado(f("Botas", "Otra", "ZZZ 999"), existentes))
    }
}
