package com.episcan.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta industrial: naranja seguridad + azul pizarra, con contraste alto para uso en planta.
private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFF1E293B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF4338CA),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF64748B),
    error = Color(0xFFB91C1C),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF7C2D12),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E1B4B),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF94A3B8),
    error = Color(0xFFFCA5A5),
)

@Composable
fun EpiTheme(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
        content = contenido,
    )
}

/** Color del badge de cada zona del cuerpo (texto blanco encima, todos con contraste suficiente). */
fun colorParte(parte: String): Color = when (parte) {
    "Cabeza" -> Color(0xFFB45309)
    "Ojos y Cara" -> Color(0xFF0E7490)
    "Auditiva" -> Color(0xFF6D28D9)
    "Vías Respiratorias" -> Color(0xFF047857)
    "Manos y Brazos" -> Color(0xFFC2410C)
    "Pies y Piernas" -> Color(0xFF78350F)
    "Tronco y Abdomen" -> Color(0xFF1D4ED8)
    "Cuerpo Entero / Caídas" -> Color(0xFFB91C1C)
    else -> Color(0xFF475569)
}
