package com.episcan.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Ficha técnica de un EPI. Es un catálogo de homologación: no lleva stock ni cantidades.
 */
@Entity(tableName = "epis")
data class EpiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificador global (UUID) que la ficha conserva en el servidor y en todos los móviles. */
    val uid: String = UUID.randomUUID().toString(),
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
    // ---- Sincronización con el servidor ----
    /** Última revisión del servidor conocida para esta ficha (0 = todavía no se ha enviado nunca). */
    val revision: Long = 0,
    /** Cuándo se modificó por última vez en este móvil (sirve para detectar cambios durante una sincronización). */
    val actualizadoEn: Long = System.currentTimeMillis(),
    /** true mientras haya cambios locales sin enviar al servidor. */
    val pendiente: Boolean = true,
    /** Borrado lógico: la ficha ya no se muestra, pero se conserva hasta comunicar el borrado al servidor. */
    val eliminado: Boolean = false,
) {
    fun listaFotos(): List<String> = fotos.split(SEPARADOR_FOTOS).filter { it.isNotBlank() }

    companion object {
        const val SEPARADOR_FOTOS = "|"
    }
}
