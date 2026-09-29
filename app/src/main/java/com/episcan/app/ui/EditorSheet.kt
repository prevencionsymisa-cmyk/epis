package com.episcan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.episcan.app.data.FichaClave
import com.episcan.app.data.PARTES_CUERPO
import com.episcan.app.data.buscarDuplicado
import com.episcan.app.data.local.DocumentoAdjunto
import com.episcan.app.data.local.EpiEntity
import com.episcan.app.data.subcategoriasDe
import java.io.File

/** Bottom Sheet de validación: el técnico revisa y corrige la ficha antes de guardarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    borrador: EpiBorrador,
    existentes: List<EpiEntity>,
    onGuardar: (EpiBorrador) -> Unit,
    onFusionar: (EpiBorrador, EpiEntity) -> Unit,
    onCancelar: () -> Unit,
    fotos: List<String> = borrador.fotos,
    procesandoFoto: Boolean = false,
    onAnadirFotos: (List<Uri>, List<File>) -> Unit = { _, _ -> },
    onQuitarFoto: (String) -> Unit = {},
    documentos: List<DocumentoAdjunto> = emptyList(),
    adjuntandoDocumento: Boolean = false,
    documentoDescargado: (DocumentoAdjunto) -> Boolean = { true },
    onAdjuntarDocumento: (Uri) -> Unit = {},
    onQuitarDocumento: (DocumentoAdjunto) -> Unit = {},
    onAbrirDocumento: (DocumentoAdjunto) -> Unit = {},
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Selector de archivos del sistema, filtrado a PDF (Descargas, Drive, correo…)
    val elegirPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAdjuntarDocumento(uri)
    }
    // Fotos: galería (varias a la vez) o cámara del sistema, que guarda en un temporal de caché
    val contexto = LocalContext.current
    val huecoFotos = (MAX_FOTOS_EDITOR - fotos.size).coerceAtLeast(0)
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_FOTOS_EDITOR)) { uris ->
        if (uris.isNotEmpty()) onAnadirFotos(uris, emptyList())
    }
    var temporalCamara by remember { mutableStateOf<File?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { hecha ->
        val temporal = temporalCamara ?: return@rememberLauncherForActivityResult
        temporalCamara = null
        if (hecha && temporal.length() > 0) onAnadirFotos(listOf(Uri.fromFile(temporal)), listOf(temporal)) else temporal.delete()
    }
    fun abrirCamara() {
        val temporal = File(File(contexto.cacheDir, "capturas").apply { mkdirs() }, "captura_${System.currentTimeMillis()}.jpg")
        temporalCamara = temporal
        camara.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", temporal))
    }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) abrirCamara()
    }
    var parte by remember(borrador) { mutableStateOf(borrador.parteCuerpo) }
    var subcategoria by remember(borrador) { mutableStateOf(borrador.subcategoria) }
    var nombre by remember(borrador) { mutableStateOf(borrador.nombreEpi) }
    var marca by remember(borrador) { mutableStateOf(borrador.marca) }
    var modelo by remember(borrador) { mutableStateOf(borrador.modelo) }
    var normativa by remember(borrador) { mutableStateOf(borrador.normativa) }
    var simbolos by remember(borrador) { mutableStateOf(borrador.simbolos) }
    var fichaTecnica by remember(borrador) { mutableStateOf(borrador.fichaTecnica) }
    var distribuidor by remember(borrador) { mutableStateOf(borrador.distribuidor) }
    var observaciones by remember(borrador) { mutableStateOf(borrador.observaciones) }
    var menuParte by remember { mutableStateOf(false) }
    var menuSubcategoria by remember { mutableStateOf(false) }
    val opcionesSubcategoria = remember(parte) { subcategoriasDe(parte) }
    // Si se cambia la zona, la subcategoría anterior deja de ser válida
    LaunchedEffect(parte) {
        if (subcategoria !in opcionesSubcategoria) subcategoria = ""
    }
    val valido = parte.isNotBlank() && nombre.isNotBlank()
    // Se recalcula al teclear: si el técnico corrige marca o modelo, el aviso aparece o desaparece
    val duplicado = remember(nombre, marca, modelo, existentes) {
        if (nombre.isBlank() && modelo.isBlank()) null
        else buscarDuplicado(FichaClave(nombre, marca, modelo), existentes, ignorarId = borrador.id)
    }
    val esNueva = borrador.id == 0L

    ModalBottomSheet(onDismissRequest = onCancelar, sheetState = estado) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (borrador.id == 0L) "Verificar y añadir EPI" else "Modificar EPI",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (borrador.propuestaIa) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Propuesta generada por IA. Contrasta la marca, la norma y los códigos con la etiqueta real antes de guardar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (duplicado != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Column {
                                Text(
                                    "Posible duplicado: este EPI ya está en el catálogo",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    listOf(duplicado.nombreEpi, duplicado.marca, duplicado.modelo).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    duplicado.parteCuerpo,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        if (esNueva) {
                            Button(
                                onClick = {
                                    onFusionar(
                                        borrador.copy(
                                            subcategoria = subcategoria, marca = marca, modelo = modelo, normativa = normativa,
                                            simbolos = simbolos, fichaTecnica = fichaTecnica, distribuidor = distribuidor,
                                            observaciones = observaciones,
                                        ),
                                        duplicado,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text("Añadir mis fotos a la ficha existente") }
                            Text(
                                "Si es una variante distinta (otra talla, color o versión), puedes guardarla como ficha nueva.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            Text(
                                "Al modificar esta ficha coincide con otra. Revisa que no estén repetidas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Fotos: se pueden añadir y quitar también al modificar un EPI ya guardado
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Fotos (${fotos.size} de $MAX_FOTOS_EDITOR) · la primera es la miniatura",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (fotos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fotos, key = { it }) { ruta ->
                            Box {
                                AsyncImage(
                                    model = File(ruta),
                                    contentDescription = "Foto del EPI",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                                )
                                IconButton(
                                    onClick = { onQuitarFoto(ruta) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
                                ) { Icon(Icons.Default.Close, "Quitar foto", tint = Color.White, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val concedido = ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (concedido) abrirCamara() else permisoCamara.launch(Manifest.permission.CAMERA)
                        },
                        enabled = huecoFotos > 0 && !procesandoFoto,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Text("  Cámara")
                    }
                    OutlinedButton(
                        onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = huecoFotos > 0 && !procesandoFoto,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (procesandoFoto) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AddPhotoAlternate, null)
                        Text("  Galería")
                    }
                }
            }

            // 1. Parte del cuerpo
            ExposedDropdownMenuBox(expanded = menuParte, onExpandedChange = { menuParte = it }) {
                OutlinedTextField(
                    value = parte,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Parte del cuerpo que protege *") },
                    isError = parte.isBlank(),
                    supportingText = { if (parte.isBlank()) Text("Selecciona una zona") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuParte) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menuParte, onDismissRequest = { menuParte = false }) {
                    PARTES_CUERPO.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p) },
                            onClick = { parte = p; menuParte = false },
                            modifier = Modifier.height(52.dp),
                        )
                    }
                }
            }

            // Subcategoría: depende de la zona elegida arriba
            ExposedDropdownMenuBox(expanded = menuSubcategoria, onExpandedChange = { if (parte.isNotBlank()) menuSubcategoria = it }) {
                OutlinedTextField(
                    value = subcategoria,
                    onValueChange = {},
                    readOnly = true,
                    enabled = parte.isNotBlank(),
                    label = { Text("Subcategoría") },
                    placeholder = { if (parte.isBlank()) Text("Selecciona antes la parte del cuerpo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuSubcategoria) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menuSubcategoria, onDismissRequest = { menuSubcategoria = false }) {
                    opcionesSubcategoria.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = { subcategoria = s; menuSubcategoria = false },
                            modifier = Modifier.height(52.dp),
                        )
                    }
                }
            }

            Campo("Nombre del EPI *", nombre, { nombre = it }, esError = nombre.isBlank())
            Campo("Notas descriptivas / observaciones", observaciones, { observaciones = it }, lineas = 2)
            Campo("Marca / Fabricante", marca, { marca = it })
            Campo("Modelo / Referencia", modelo, { modelo = it })
            Campo("Normativa(s) EN / ISO / marcado CE", normativa, { normativa = it }, lineas = 2)
            Campo("Símbolos, pictogramas y significado", simbolos, { simbolos = it }, lineas = 6)
            Campo("Ficha técnica (código o referencia del fabricante)", fichaTecnica, { fichaTecnica = it })

            // PDF de la ficha técnica del distribuidor o fabricante
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                documentos.forEach { doc ->
                    FilaDocumento(
                        doc = doc,
                        descargado = documentoDescargado(doc),
                        onAbrir = { onAbrirDocumento(doc) },
                        onQuitar = { onQuitarDocumento(doc) },
                    )
                }
                OutlinedButton(
                    onClick = { elegirPdf.launch(arrayOf("application/pdf")) },
                    enabled = !adjuntandoDocumento,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (adjuntandoDocumento) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  Adjuntando…")
                    } else {
                        Icon(Icons.Default.AttachFile, null)
                        Text(if (documentos.isEmpty()) "  Adjuntar ficha técnica (PDF)" else "  Adjuntar otro PDF")
                    }
                }
            }
            Campo("Distribuidor / Proveedor", distribuidor, { distribuidor = it })

            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(56.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        onGuardar(
                            borrador.copy(
                                parteCuerpo = parte, subcategoria = subcategoria, nombreEpi = nombre, marca = marca,
                                modelo = modelo, normativa = normativa, simbolos = simbolos, fichaTecnica = fichaTecnica,
                                distribuidor = distribuidor, observaciones = observaciones,
                            ),
                        )
                    },
                    enabled = valido && !adjuntandoDocumento && !procesandoFoto,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(if (duplicado != null && esNueva) "Guardar como nueva" else "Guardar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Igual que MAX_FOTOS del ViewModel. */
private const val MAX_FOTOS_EDITOR = 6

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    lineas: Int = 1,
    esError: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = esError,
        minLines = lineas,
        maxLines = if (lineas == 1) 2 else 12,
        modifier = Modifier.fillMaxWidth(),
    )
}
