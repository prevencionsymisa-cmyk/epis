package com.episcan.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.episcan.app.ui.CaptureScreen
import com.episcan.app.ui.EditorSheet
import com.episcan.app.ui.EpiTheme
import com.episcan.app.ui.EpiViewModel
import com.episcan.app.ui.HomeScreen
import com.episcan.app.ui.OtaDialog
import com.episcan.app.ui.Pantalla
import com.episcan.app.ui.SettingsScreen
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {
    private val vm: EpiViewModel by viewModels()

    /** Excel pendiente de copiar al destino que elija el usuario (Storage Access Framework). */
    private var excelPendiente: File? = null

    private val guardarComo = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { uri ->
        val origen = excelPendiente
        excelPendiente = null
        if (uri == null || origen == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { salida -> origen.inputStream().use { it.copyTo(salida) } }
                        ?: error("sin flujo de salida")
                }.isSuccess
            }
            vm.mensaje(if (ok) "Excel guardado" else "No se pudo guardar el archivo")
        }
    }

    private val actualizacionPlay = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EpiTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Raiz()
                }
            }
        }
        comprobarPlayInAppUpdates()
    }

    @Composable
    private fun Raiz() {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.mensajes.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) { if (vm.ajustes.otaAutoComprobar) vm.comprobarActualizaciones(manual = false) }

        when (vm.pantalla) {
            Pantalla.Inicio -> HomeScreen(
                vm = vm,
                snackbar = snackbar,
                onCompartir = { vm.exportarExcel(::compartirExcel) },
                onGuardarExcel = {
                    vm.exportarExcel { archivo ->
                        excelPendiente = archivo
                        guardarComo.launch(archivo.name)
                    }
                },
            )
            Pantalla.Captura -> CaptureScreen(vm)
            Pantalla.Ajustes -> SettingsScreen(vm, snackbar)
        }

        vm.editor?.let { EditorSheet(borrador = it, onGuardar = vm::guardar, onCancelar = vm::cancelarEditor) }
        vm.ota?.let {
            OtaDialog(it, onDescargar = vm::descargarActualizacion, onInstalar = vm::instalarActualizacion, onDescartar = vm::descartarActualizacion)
        }
    }

    private fun compartirExcel(archivo: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        val envio = Intent(Intent.ACTION_SEND)
            .setType(MIME_XLSX)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Registro técnico de EPIs")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(envio, "Compartir registro de EPIs"))
    }

    /** Canal oficial opcional. Solo actúa si PLAY_IN_APP_UPDATES=true y la app se instaló desde Google Play. */
    private fun comprobarPlayInAppUpdates() {
        if (!BuildConfig.PLAY_IN_APP_UPDATES) return
        val gestor = AppUpdateManagerFactory.create(this)
        gestor.registerListener { estado ->
            if (estado.installStatus() == InstallStatus.DOWNLOADED) gestor.completeUpdate()
        }
        gestor.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                gestor.startUpdateFlowForResult(info, actualizacionPlay, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
            }
        }
    }
}
