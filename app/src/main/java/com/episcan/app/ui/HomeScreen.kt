package com.episcan.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.episcan.app.data.local.EpiEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: EpiViewModel,
    snackbar: SnackbarHostState,
    onCompartir: () -> Unit,
    onGuardarExcel: () -> Unit,
) {
    val epis by vm.epis.collectAsState()
    val todos by vm.todos.collectAsState()
    val zonas by vm.conteoZonas.collectAsState()
    val zonaElegida by vm.zonaFiltro.collectAsState()
    val total by vm.total.collectAsState()
    val consulta by vm.consulta.collectAsState()
    var menuExportar by remember { mutableStateOf(false) }
    var fotosAbiertas by remember { mutableStateOf<List<String>?>(null) }
    var detalleId by remember { mutableStateOf<Long?>(null) }

    // La zona elegida solo cuenta si todavía tiene fichas
    val zonaActiva = zonaElegida?.takeIf { z -> zonas.any { it.first == z } }
    // Se busca en la lista completa: si la ficha se borra o cambia, la hoja se actualiza o se cierra sola
    val detalle = todos.firstOrNull { it.id == detalleId }
    val filtrando = zonaActiva != null || consulta.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catálogo de EPIs ($total)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExportar = true },
                            enabled = total > 0 && !vm.exportando,
                            modifier = Modifier.size(52.dp),
                        ) {
                            if (vm.exportando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.Default.FileDownload, contentDescription = "Exportar a Excel")
                        }
                        DropdownMenu(expanded = menuExportar, onDismissRequest = { menuExportar = false }) {
                            DropdownMenuItem(
                                text = { Text("Compartir Excel (.xlsx)") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = { menuExportar = false; onCompartir() },
                            )
                            DropdownMenuItem(
                                text = { Text("Guardar Excel en el dispositivo…") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = { menuExportar = false; onGuardarExcel() },
                            )
                        }
                    }
                    IconButton(onClick = { vm.pantalla = Pantalla.Ajustes }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.abrirCaptura() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Añadir EPI", style = MaterialTheme.typography.titleMedium) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(64.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = consulta,
                onValueChange = { vm.consulta.value = it },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                placeholder = { Text("Buscar por nombre, marca, norma…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (consulta.isNotEmpty()) {
                        IconButton(onClick = { vm.consulta.value = "" }) { Icon(Icons.Default.Close, "Borrar búsqueda") }
                    }
                },
                singleLine = true,
            )

            // Chips de zona del cuerpo: solo aparecen las zonas que tienen fichas
            if (zonas.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    item {
                        FilterChip(
                            selected = zonaActiva == null,
                            onClick = { vm.zonaFiltro.value = null },
                            label = { Text("Todos ($total)") },
                        )
                    }
                    items(zonas, key = { it.first }) { (zona, n) ->
                        FilterChip(
                            selected = zonaActiva == zona,
                            onClick = { vm.zonaFiltro.value = if (zonaActiva == zona) null else zona },
                            label = { Text("$zona ($n)") },
                        )
                    }
                }
            }

            if (filtrando && epis.isNotEmpty()) {
                Text(
                    "Mostrando ${epis.size} de $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            if (epis.isEmpty()) {
                EstadoVacio(hayFiltro = filtrando)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(epis, key = { it.id }) { epi ->
                        FilaEpi(epi = epi, onClick = { detalleId = epi.id })
                    }
                }
            }
        }
    }

    if (detalle != null) {
        DetalleSheet(
            epi = detalle,
            onCerrar = { detalleId = null },
            onEditar = { detalleId = null; vm.editar(detalle) },
            onEliminar = { detalleId = null; vm.pedirEliminar(detalle) },
            onVerFotos = { fotosAbiertas = detalle.listaFotos() },
        )
    }

    vm.aEliminar?.let { epi ->
        AlertDialog(
            onDismissRequest = vm::descartarEliminar,
            title = { Text("¿Eliminar este EPI?") },
            text = { Text("Se borrará «${epi.nombreEpi}» y sus fotos del catálogo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = vm::confirmarEliminar, modifier = Modifier.height(48.dp)) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::descartarEliminar, modifier = Modifier.height(48.dp)) { Text("Cancelar") }
            },
        )
    }

    fotosAbiertas?.let { VisorFotos(it) { fotosAbiertas = null } }
}

@Composable
private fun EstadoVacio(hayFiltro: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CameraAlt, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text(
                if (hayFiltro) "Ningún EPI coincide con el filtro" else "Todavía no hay EPIs",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!hayFiltro) {
                Text(
                    "Pulsa «Añadir EPI», fotografía la etiqueta y la IA propondrá la ficha técnica para que la valides.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fila compacta (~90 dp): miniatura, zona, nombre y una línea de marca · modelo · norma. */
@Composable
private fun FilaEpi(epi: EpiEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val primera = epi.listaFotos().firstOrNull()
            if (primera != null) {
                AsyncImage(
                    model = File(primera),
                    contentDescription = "Foto de ${epi.nombreEpi}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.outline) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BadgeParte(epi.parteCuerpo, compacto = true)
                Text(
                    epi.nombreEpi,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val resumen = listOf(epi.marca, epi.modelo, epi.normativa).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    resumen.ifBlank { "Sin datos de marca ni norma" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Ficha completa. Los bloques siguen el orden de las columnas del Excel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetalleSheet(
    epi: EpiEntity,
    onCerrar: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
    onVerFotos: () -> Unit,
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estado) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BadgeParte(epi.parteCuerpo)
            Column {
                Text(epi.nombreEpi, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (epi.observaciones.isNotBlank()) {
                    Text(epi.observaciones, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato("Marca", epi.marca, Modifier.weight(1f))
                Dato("Modelo", epi.modelo, Modifier.weight(1f))
            }
            Dato("Normativa", epi.normativa, Modifier.fillMaxWidth())

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
                    .padding(12.dp),
            ) {
                Text("Símbolos y significado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Text(epi.simbolos.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
            }

            Dato("Distribuidor", epi.distribuidor, Modifier.fillMaxWidth())

            val fotos = epi.listaFotos()
            if (fotos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fotos, key = { it }) { ruta ->
                        AsyncImage(
                            model = File(ruta),
                            contentDescription = "Foto del EPI (toca para ampliar)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onVerFotos),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEliminar,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("  Eliminar", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onEditar, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Default.Edit, null)
                    Text("  Modificar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BadgeParte(parte: String, compacto: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colorParte(parte))
            .padding(horizontal = if (compacto) 8.dp else 12.dp, vertical = if (compacto) 1.dp else 4.dp),
    ) {
        Text(
            parte.ifBlank { "Sin clasificar" },
            color = Color.White,
            style = if (compacto) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(valor.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VisorFotos(rutas: List<String>, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val estado = rememberPagerState { rutas.size }
            HorizontalPager(estado, Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = File(rutas[i]),
                    contentDescription = "Foto ${i + 1} de ${rutas.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onCerrar, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp)) {
                Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
            }
            if (rutas.size > 1) {
                Text(
                    "${estado.currentPage + 1} / ${rutas.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
}
