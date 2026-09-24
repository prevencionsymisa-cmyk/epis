package com.episcan.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CaptureScreen(vm: EpiViewModel) {
    val context = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) vm.anadirFotos(uris)
    }
    var captura by remember { mutableStateOf<ImageCapture?>(null) }
    var linterna by remember { mutableStateOf(false) }

    BackHandler { vm.cerrarCaptura() }
    LaunchedEffect(Unit) { if (!permiso) pedirPermiso.launch(Manifest.permission.CAMERA) }

    fun disparar() {
        val ic = captura ?: return
        if (vm.procesandoFoto) return
        val temporal = File(context.cacheDir, "captura_${System.currentTimeMillis()}.jpg")
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(temporal).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    vm.anadirFotos(listOf(Uri.fromFile(temporal)), listOf(temporal))
                }

                override fun onError(e: ImageCaptureException) {
                    temporal.delete()
                    vm.mensaje("No se pudo tomar la foto: ${e.message}")
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // Zona de cámara
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (permiso) {
                    VistaCamara(Modifier.fillMaxSize(), linterna = linterna, onCapturaLista = { captura = it })
                    Text(
                        "Toca la pantalla para enfocar · acércate a la etiqueta",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                } else {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "Se necesita permiso de cámara para fotografiar los EPIs. También puedes adjuntar fotos de la galería.",
                            color = Color.White, textAlign = TextAlign.Center,
                        )
                        Button(onClick = { pedirPermiso.launch(Manifest.permission.CAMERA) }, modifier = Modifier.height(56.dp)) {
                            Text("Conceder permiso de cámara")
                        }
                    }
                }
                // Barra superior
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BotonCircular(onClick = { vm.cerrarCaptura() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
                    if (permiso) {
                        BotonCircular(onClick = { linterna = !linterna }) {
                            Icon(if (linterna) Icons.Default.FlashOn else Icons.Default.FlashOff, "Linterna", tint = Color.White)
                        }
                    }
                }
            }

            // Panel inferior: miniaturas + acciones
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.fotosBorrador.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vm.fotosBorrador.toList(), key = { it }) { ruta ->
                                Box(Modifier.size(76.dp)) {
                                    AsyncImage(
                                        model = File(ruta),
                                        contentDescription = "Foto tomada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                    )
                                    IconButton(
                                        onClick = { vm.quitarFotoBorrador(ruta) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                                    ) { Icon(Icons.Default.Close, "Quitar foto", tint = Color.White, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    } else {
                        Text(
                            "Fotografía la etiqueta, el marcado CE y los pictogramas. Puedes añadir varias fotos del mismo EPI.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.height(56.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Text("  Galería")
                        }
                        // Disparador
                        Box(
                            Modifier.size(76.dp).border(4.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = ::disparar,
                                enabled = permiso && captura != null && !vm.procesandoFoto,
                                modifier = Modifier.fillMaxSize(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    disabledContainerColor = MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                if (vm.procesandoFoto) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
                            }
                        }
                        Text("${vm.fotosBorrador.size}/6", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
                    }

                    vm.errorAnalisis?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = vm::analizar,
                        enabled = vm.fotosBorrador.isNotEmpty() && !vm.analizando && !vm.procesandoFoto,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Analizar con IA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = vm::rellenarManualmente,
                        enabled = !vm.analizando && !vm.procesandoFoto,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Rellenar la ficha manualmente") }
                }
            }
        }

        if (vm.analizando) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Analizando marcado, normas y símbolos…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = vm::cancelarAnalisis) { Text("Cancelar", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun BotonCircular(onClick: () -> Unit, contenido: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(4.dp).size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
    ) { contenido() }
}

/** Preview de CameraX con autofoco continuo, toque para enfocar y linterna. */
@Composable
private fun VistaCamara(modifier: Modifier, linterna: Boolean, onCapturaLista: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camara by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(ciclo) {
        val proveedor = context.proveedorDeCamara()
        val preview = Preview.Builder().also { forzarAutofoco(it) }.build().also { it.setSurfaceProvider(vista.surfaceProvider) }
        val captura = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) // prioriza nitidez sobre velocidad
            .also { forzarAutofoco(it) }
            .build()
        proveedor.unbindAll()
        camara = proveedor.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura)
        onCapturaLista(captura)
    }
    LaunchedEffect(camara, linterna) {
        if (camara?.cameraInfo?.hasFlashUnit() == true) camara?.cameraControl?.enableTorch(linterna)
    }

    AndroidView(
        factory = { vista },
        modifier = modifier,
        update = { v ->
            v.setOnTouchListener { vv, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    val punto = v.meteringPointFactory.createPoint(ev.x, ev.y)
                    camara?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(punto).build())
                    vv.performClick()
                }
                true
            }
        },
    )
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun <T> forzarAutofoco(builder: ExtendableBuilder<T>) {
    // Autofoco continuo optimizado para foto: mejora la nitidez en etiquetas y serigrafías pequeñas
    Camera2Interop.Extender(builder).setCaptureRequestOption(
        CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
    )
}

private suspend fun Context.proveedorDeCamara(): ProcessCameraProvider = suspendCoroutine { cont ->
    val futuro = ProcessCameraProvider.getInstance(this)
    futuro.addListener({ cont.resume(futuro.get()) }, ContextCompat.getMainExecutor(this))
}
