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

/** Resultado del análisis de Gemini, aún sin validar por el técnico. */
data class ResultadoIa(
    val parteCuerpo: String = "",
    val nombreEpi: String = "",
    val marca: String = "",
    val modelo: String = "",
    val normativa: String = "",
    val simbolos: String = "",
    val distribuidor: String = "",
    val observaciones: String = "",
)
