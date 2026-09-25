package com.episcan.app.data

import com.episcan.app.data.local.EpiEntity
import java.text.Normalizer

/** Datos mínimos de una ficha para decidir si coincide con otra. */
class FichaClave(val nombre: String, val marca: String, val modelo: String)

private val MARCAS_DIACRITICAS = Regex("\\p{M}+")
private val NO_ALFANUMERICO = Regex("[^a-z0-9]")

/** Minúsculas, sin acentos, sin espacios ni signos: "Reca  Protect-204" y "RECA PROTECT 204" quedan iguales. */
internal fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
        .replace(MARCAS_DIACRITICAS, "")
        .replace(NO_ALFANUMERICO, "")

/** Un modelo tan corto ("2", "A1") es demasiado genérico para identificar un EPI sin marca. */
private const val LARGO_MINIMO_MODELO_SIN_MARCA = 5

/**
 * Criterio de coincidencia:
 * - Con modelo: mismo modelo y misma marca (si a una de las dos le falta la marca, basta con que
 *   el modelo sea lo bastante específico).
 * - Sin modelo: mismo nombre y misma marca.
 */
internal fun esDuplicado(a: FichaClave, b: FichaClave): Boolean {
    val marcaA = normalizar(a.marca)
    val marcaB = normalizar(b.marca)
    val modeloA = normalizar(a.modelo)
    val modeloB = normalizar(b.modelo)

    if (modeloA.isNotEmpty() || modeloB.isNotEmpty()) {
        if (modeloA.isEmpty() || modeloA != modeloB) return false
        val algunaSinMarca = marcaA.isEmpty() || marcaB.isEmpty()
        return marcaA == marcaB || (algunaSinMarca && modeloA.length >= LARGO_MINIMO_MODELO_SIN_MARCA)
    }

    val nombreA = normalizar(a.nombre)
    return nombreA.isNotEmpty() && nombreA == normalizar(b.nombre) && marcaA == marcaB
}

/** Primera ficha existente que coincide con [candidata], ignorando la ficha con id [ignorarId] (la que se está editando). */
fun buscarDuplicado(candidata: FichaClave, existentes: List<EpiEntity>, ignorarId: Long = 0): EpiEntity? =
    existentes.firstOrNull { it.id != ignorarId && esDuplicado(candidata, FichaClave(it.nombreEpi, it.marca, it.modelo)) }
