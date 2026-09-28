package com.episcan.app.data

/** Zonas del cuerpo permitidas para clasificar un EPI (mismo valor que devuelve Gemini). */
val PARTES_CUERPO = listOf(
    "Cabeza",
    "Ojos y Cara",
    "Auditiva",
    "Vías Respiratorias",
    "Manos y Brazos",
    "Pies y Piernas",
    "Tronco y Abdomen",
    "Cuerpo Entero / Caídas",
)

/** Se añade siempre como última opción de cada zona, para lo que no encaje en ninguna subcategoría. */
const val SUBCATEGORIA_OTRA = "Otra / no clasificada"

/**
 * Subtipos por zona del cuerpo. Lista cerrada (no texto libre) para que el filtrado por
 * subcategoría en el catálogo quede siempre limpio, sin variantes casi duplicadas.
 */
val SUBCATEGORIAS: Map<String, List<String>> = linkedMapOf(
    "Cabeza" to listOf(
        "Casco de protección industrial",
        "Casco de montaña / trabajos en altura",
        "Gorro o gorra antigolpes",
        "Casco dieléctrico",
    ),
    "Ojos y Cara" to listOf(
        "Gafas de montura universal",
        "Gafas de montura integral",
        "Pantalla facial",
        "Gafas o pantalla de soldadura",
    ),
    "Auditiva" to listOf(
        "Tapones auditivos",
        "Orejeras",
        "Orejeras acopladas a casco",
    ),
    "Vías Respiratorias" to listOf(
        "Mascarilla autofiltrante (FFP)",
        "Media máscara con filtros",
        "Máscara completa con filtros",
        "Equipo de aire suministrado o autónomo",
    ),
    "Manos y Brazos" to listOf(
        "Protección mecánica",
        "Protección química",
        "Protección térmica (calor o frío)",
        "Protección dieléctrica",
        "Manguitos y manoplas",
    ),
    "Pies y Piernas" to listOf(
        "Calzado de seguridad (con puntera)",
        "Calzado de protección (sin puntera)",
        "Botas impermeables / de agua",
        "Polainas y cubrebotas",
    ),
    "Tronco y Abdomen" to listOf(
        "Ropa de alta visibilidad",
        "Ropa de protección química",
        "Ropa ignífuga / soldadura",
        "Delantal de protección",
        "Ropa de protección térmica",
    ),
    "Cuerpo Entero / Caídas" to listOf(
        "Arnés anticaídas",
        "Conectores y eslingas",
        "Anticaídas retráctil o deslizante",
        "Línea de vida y anclajes",
        "Cinturón de posicionamiento",
    ),
).mapValues { (_, subs) -> subs + SUBCATEGORIA_OTRA }

/** Subcategorías válidas para una zona; lista vacía si la zona no es una de [PARTES_CUERPO]. */
fun subcategoriasDe(zona: String): List<String> = SUBCATEGORIAS[zona].orEmpty()

/** Resultado del análisis de Gemini, aún sin validar por el técnico. */
data class ResultadoIa(
    val parteCuerpo: String = "",
    val subcategoria: String = "",
    val nombreEpi: String = "",
    val marca: String = "",
    val modelo: String = "",
    val normativa: String = "",
    val simbolos: String = "",
    val fichaTecnica: String = "",
    val distribuidor: String = "",
    val observaciones: String = "",
)
