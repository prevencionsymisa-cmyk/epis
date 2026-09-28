package com.episcan.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ficha técnica de un EPI. Es un catálogo de homologación: no lleva stock ni cantidades.
 */
@Entity(tableName = "epis")
data class EpiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parteCuerpo: String,
    /** Subtipo dentro de la parte del cuerpo (p. ej. "Protección mecánica"). Siempre uno de [com.episcan.app.data.SUBCATEGORIAS]. */
    val subcategoria: String = "",
    val nombreEpi: String,
    val marca: String = "",
    val modelo: String = "",
    val normativa: String = "",
    val simbolos: String = "",
    /** Referencia o código de la ficha técnica del fabricante (no el texto completo del documento). */
    val fichaTecnica: String = "",
    val distribuidor: String = "",
    val observaciones: String = "",
    /** Rutas absolutas de las fotos comprimidas, separadas por '|'. La primera es la miniatura. */
    val fotos: String = "",
    val creadoEn: Long = System.currentTimeMillis(),
) {
    fun listaFotos(): List<String> = fotos.split(SEPARADOR_FOTOS).filter { it.isNotBlank() }

    companion object {
        const val SEPARADOR_FOTOS = "|"
    }
}
