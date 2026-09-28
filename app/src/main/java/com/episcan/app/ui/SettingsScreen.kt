package com.episcan.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.episcan.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: EpiViewModel, snackbar: SnackbarHostState) {
    val ajustes = vm.ajustes
    var clave by remember { mutableStateOf(ajustes.geminiApiKey) }
    var verClave by remember { mutableStateOf(false) }
    var modelo by remember { mutableStateOf(ajustes.geminiModel) }
    var urlOta by remember { mutableStateOf(ajustes.otaUrl) }
    var autoOta by remember { mutableStateOf(ajustes.otaAutoComprobar) }
    var syncUrl by remember { mutableStateOf(ajustes.url) }
    var syncToken by remember { mutableStateOf(ajustes.token) }
    var verToken by remember { mutableStateOf(false) }
    var syncAuto by remember { mutableStateOf(ajustes.syncAuto) }
    val pendientesSync by vm.pendientesSync.collectAsState()
    val sinSubcategoria by vm.sinSubcategoria.collectAsState()
    var confirmarClasificar by remember { mutableStateOf(false) }

    fun guardar() {
        ajustes.geminiApiKey = clave
        ajustes.geminiModel = modelo
        ajustes.otaUrl = urlOta
        ajustes.otaAutoComprobar = autoOta
        ajustes.url = syncUrl
        ajustes.token = syncToken
        ajustes.syncAuto = syncAuto
    }

    BackHandler { guardar(); vm.pantalla = Pantalla.Inicio }

    if (confirmarClasificar) {
        AlertDialog(
            onDismissRequest = { confirmarClasificar = false },
            title = { Text("¿Clasificar las fichas?") },
            text = {
                Text(
                    "Se asignará la subcategoría a las $sinSubcategoria fichas que no la tienen, leyendo su nombre y su norma. " +
                        "No se modifica ninguna que ya la tenga, y después puedes corregir cualquiera desde su ficha.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmarClasificar = false; vm.clasificarSubcategorias() },
                    modifier = Modifier.height(48.dp),
                ) { Text("Clasificar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarClasificar = false }, modifier = Modifier.height(48.dp)) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { guardar(); vm.pantalla = Pantalla.Inicio }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Análisis con IA (Gemini)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("Clave de API de Gemini") },
                singleLine = true,
                visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Se guarda solo en este dispositivo. Créala gratis en Google AI Studio.") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = verClave, onCheckedChange = { verClave = it })
                Text("  Mostrar clave")
            }
            OutlinedTextField(
                value = modelo,
                onValueChange = { modelo = it },
                label = { Text("Modelo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Por ejemplo gemini-2.5-flash o gemini-1.5-flash") },
            )

            Text("Catálogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Asigna la subcategoría a las fichas que aún no la tienen, según su nombre y su normativa. " +
                    "No cambia las que ya la tienen y deja vacías las que no reconoce.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmarClasificar = true },
                enabled = sinSubcategoria > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(if (sinSubcategoria > 0) "Clasificar fichas sin subcategoría ($sinSubcategoria)" else "Todas las fichas tienen subcategoría")
            }

            Text("Sincronización con el servidor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Guarda el catálogo en tu servidor para verlo y editarlo desde la web, y compartirlo entre móviles. " +
                    "Sin conexión la app funciona igual y envía los cambios cuando pueda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = syncUrl,
                onValueChange = { syncUrl = it },
                label = { Text("Dirección del servidor (https)") },
                placeholder = { Text("https://api-epis.midominio.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = syncToken,
                onValueChange = { syncToken = it },
                label = { Text("Token de acceso") },
                singleLine = true,
                visualTransformation = if (verToken) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Se guarda solo en este móvil. Te lo da quien administra el servidor.") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = verToken, onCheckedChange = { verToken = it })
                Text("  Mostrar token")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = syncAuto, onCheckedChange = { syncAuto = it })
                Text("  Sincronizar automáticamente")
            }
            Button(
                onClick = { guardar(); vm.sincronizar(manual = true) },
                enabled = !vm.sincronizando,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (vm.sincronizando) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Sincronizar ahora")
            }
            Text(
                if (pendientesSync == 0) "Todo lo de este móvil está enviado" else "$pendientesSync fichas con cambios sin enviar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Actualizaciones (OTA)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = urlOta,
                onValueChange = { urlOta = it },
                label = { Text("URL del JSON de versiones (https)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoOta, onCheckedChange = { autoOta = it })
                Text("  Buscar actualizaciones al abrir la app")
            }
            Button(
                onClick = { guardar(); vm.comprobarActualizaciones(manual = true) },
                enabled = !vm.comprobandoOta,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (vm.comprobandoOta) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Buscar actualizaciones ahora")
            }
            Text(
                "Versión instalada: ${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
