package com.episcan.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.episcan.app.data.local.DocumentoAdjunto
import java.io.File

/**
 * Abre el PDF con la app de PDF que tenga el móvil. Devuelve un mensaje de error para el usuario,
 * o null si se abrió.
 */
fun abrirPdf(contexto: Context, archivo: File): String? {
    if (!archivo.isFile) return "El documento aún no se ha descargado. Sincroniza y vuelve a intentarlo"
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        contexto.startActivity(Intent.createChooser(intent, "Abrir PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        "No hay ninguna app para abrir PDF en este móvil"
    }
}

/** Fila táctil (mín. 52 dp) con el nombre del PDF. [onQuitar] solo en el editor. */
@Composable
fun FilaDocumento(
    doc: DocumentoAdjunto,
    descargado: Boolean,
    onAbrir: () -> Unit,
    onQuitar: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir),
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(start = 12.dp, end = if (onQuitar == null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(doc.nombre, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (descargado) "Toca para abrir" else "Pendiente de descargar (se baja al sincronizar)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (onQuitar != null) {
                IconButton(onClick = onQuitar, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Quitar ${doc.nombre}")
                }
            }
        }
    }
}
