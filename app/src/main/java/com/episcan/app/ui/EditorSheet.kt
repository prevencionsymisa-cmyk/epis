package com.episcan.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.episcan.app.data.PARTES_CUERPO
import java.io.File

/** Bottom Sheet de validación: el técnico revisa y corrige la ficha antes de guardarla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    borrador: EpiBorrador,
    onGuardar: (EpiBorrador) -> Unit,
    onCancelar: () -> Unit,
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var parte by remember(borrador) { mutableStateOf(borrador.parteCuerpo) }
    var nombre by remember(borrador) { mutableStateOf(borrador.nombreEpi) }
    var marca by remember(borrador) { mutableStateOf(borrador.marca) }
    var modelo by remember(borrador) { mutableStateOf(borrador.modelo) }
    var normativa by remember(borrador) { mutableStateOf(borrador.normativa) }
    var simbolos by remember(borrador) { mutableStateOf(borrador.simbolos) }
    var distribuidor by remember(borrador) { mutableStateOf(borrador.distribuidor) }
    var observaciones by remember(borrador) { mutableStateOf(borrador.observaciones) }
    var menuParte by remember { mutableStateOf(false) }
    val valido = parte.isNotBlank() && nombre.isNotBlank()

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

            if (borrador.fotos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(borrador.fotos, key = { it }) { ruta ->
                        AsyncImage(
                            model = File(ruta),
                            contentDescription = "Foto del EPI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                        )
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

            Campo("Nombre del EPI *", nombre, { nombre = it }, esError = nombre.isBlank())
            Campo("Notas descriptivas / observaciones", observaciones, { observaciones = it }, lineas = 2)
            Campo("Marca / Fabricante", marca, { marca = it })
            Campo("Modelo / Referencia", modelo, { modelo = it })
            Campo("Normativa(s) EN / ISO / marcado CE", normativa, { normativa = it }, lineas = 2)
            Campo("Símbolos, pictogramas y significado", simbolos, { simbolos = it }, lineas = 6)
            Campo("Distribuidor / Proveedor", distribuidor, { distribuidor = it })

            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancelar, modifier = Modifier.weight(1f).height(56.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        onGuardar(
                            borrador.copy(
                                parteCuerpo = parte, nombreEpi = nombre, marca = marca, modelo = modelo,
                                normativa = normativa, simbolos = simbolos, distribuidor = distribuidor,
                                observaciones = observaciones,
                            ),
                        )
                    },
                    enabled = valido,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

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
