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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

    fun guardar() {
        ajustes.geminiApiKey = clave
        ajustes.geminiModel = modelo
        ajustes.otaUrl = urlOta
        ajustes.otaAutoComprobar = autoOta
    }

    BackHandler { guardar(); vm.pantalla = Pantalla.Inicio }

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
