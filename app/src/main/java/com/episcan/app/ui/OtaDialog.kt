package com.episcan.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Aviso de nueva versión con las notas de la versión. Si es obligatoria no se puede descartar. */
@Composable
fun OtaDialog(estado: OtaUi, onDescargar: () -> Unit, onInstalar: () -> Unit, onDescartar: () -> Unit) {
    val info = estado.info
    val descargando = estado.progreso != null
    val listo = estado.apk != null

    AlertDialog(
        onDismissRequest = { if (!info.mandatory) onDescartar() },
        properties = DialogProperties(dismissOnBackPress = !info.mandatory, dismissOnClickOutside = !info.mandatory),
        title = {
            Text(if (info.mandatory) "Actualización obligatoria" else "Nueva versión disponible", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Versión ${info.latestVersionName}", style = MaterialTheme.typography.titleMedium)
                if (info.releaseNotes.isNotBlank()) {
                    Text(info.releaseNotes, modifier = Modifier.padding(top = 8.dp))
                }
                if (descargando) {
                    LinearProgressIndicator(
                        progress = { (estado.progreso ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Text("Descargando… ${estado.progreso}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (listo) {
                    Text(
                        "Descarga completada. Si Android no abre el instalador, concede el permiso «Instalar apps desconocidas» y pulsa Instalar.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                estado.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            when {
                listo -> TextButton(onClick = onInstalar, modifier = Modifier.height(48.dp)) { Text("Instalar", fontWeight = FontWeight.Bold) }
                descargando -> Unit
                else -> TextButton(onClick = onDescargar, modifier = Modifier.height(48.dp)) {
                    Text(if (estado.error != null) "Reintentar" else "Descargar", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!info.mandatory) TextButton(onClick = onDescartar, modifier = Modifier.height(48.dp)) { Text("Más tarde") }
        },
    )
}
