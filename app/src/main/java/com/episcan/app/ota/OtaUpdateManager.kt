package com.episcan.app.ota

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.episcan.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** JSON remoto de versiones. `sha256` es opcional y, si viene, se verifica tras la descarga. */
data class UpdateInfo(
    @SerializedName("latestVersionCode") val latestVersionCode: Int = 0,
    @SerializedName("latestVersionName") val latestVersionName: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("mandatory") val mandatory: Boolean = false,
    @SerializedName("sha256") val sha256: String? = null,
)

sealed interface DescargaOta {
    data class Progreso(val porcentaje: Int) : DescargaOta
    data class Completada(val apk: File) : DescargaOta
    data class Fallo(val mensaje: String) : DescargaOta
}

/**
 * Actualizaciones Over-The-Air para distribución corporativa (sideloading):
 * comprueba un JSON remoto, descarga el APK con DownloadManager e inicia el instalador del sistema.
 */
class OtaUpdateManager(
    context: Context,
    private val cliente: OkHttpClient,
) {
    private val contexto = context.applicationContext
    private val gson = Gson()

    /** Devuelve la actualización disponible o null si ya se está en la última versión. */
    suspend fun comprobar(urlManifiesto: String): UpdateInfo? = withContext(Dispatchers.IO) {
        require(urlManifiesto.startsWith("https://")) { "La URL de actualizaciones debe usar https" }
        val peticion = Request.Builder().url(urlManifiesto).header("Cache-Control", "no-cache").build()
        cliente.newCall(peticion).execute().use { r ->
            if (!r.isSuccessful) throw IOException("El servidor respondió ${r.code}")
            val info = gson.fromJson(r.body?.string().orEmpty(), UpdateInfo::class.java)
                ?: throw IOException("JSON de versiones vacío")
            require(info.apkUrl.startsWith("https://")) { "apkUrl debe usar https" }
            if (info.latestVersionCode > BuildConfig.VERSION_CODE) info else null
        }
    }

    /** Descarga el APK a getExternalFilesDir(DIRECTORY_DOWNLOADS) con notificación de progreso. */
    fun descargar(info: UpdateInfo): Flow<DescargaOta> = flow {
        val gestor = contexto.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val nombre = "epi-scan-v${info.latestVersionName.ifBlank { info.latestVersionCode.toString() }}.apk"
        limpiarApksAntiguos()
        File(contexto.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nombre).delete()

        val solicitud = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("EPI Scan ${info.latestVersionName}")
            .setDescription("Descargando actualización")
            .setMimeType(MIME_APK)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(contexto, Environment.DIRECTORY_DOWNLOADS, nombre)
        val id = gestor.enqueue(solicitud)
        var terminada = false

        try {
            while (true) {
                gestor.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) {
                        emit(DescargaOta.Fallo("La descarga fue cancelada"))
                        terminada = true
                        return@flow
                    }
                    when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            terminada = true
                            val apk = File(contexto.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nombre)
                            val fallo = verificarIntegridad(apk, info.sha256)
                            if (fallo != null) {
                                apk.delete()
                                emit(DescargaOta.Fallo(fallo))
                            } else {
                                emit(DescargaOta.Completada(apk))
                            }
                            return@flow
                        }
                        DownloadManager.STATUS_FAILED -> {
                            terminada = true
                            val motivo = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            emit(DescargaOta.Fallo("Descarga fallida (código $motivo)"))
                            return@flow
                        }
                        else -> {
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val actual = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            emit(DescargaOta.Progreso(if (total > 0) (actual * 100 / total).toInt() else 0))
                        }
                    }
                }
                delay(500)
            }
        } finally {
            // Si se cancela el flujo a mitad de descarga, se retira de DownloadManager
            if (!terminada) withContext(NonCancellable) { gestor.remove(id) }
        }
    }.flowOn(Dispatchers.IO)

    private fun verificarIntegridad(apk: File, sha256Esperado: String?): String? {
        if (!apk.exists() || apk.length() == 0L) return "El APK descargado está vacío"
        if (sha256Esperado.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val real = digest.digest().joinToString("") { "%02x".format(it) }
        return if (real.equals(sha256Esperado.trim(), ignoreCase = true)) null
        else "La huella SHA-256 del APK no coincide; no se instalará"
    }

    private fun limpiarApksAntiguos() {
        contexto.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles { f -> f.extension == "apk" }
            ?.forEach { it.delete() }
    }

    /**
     * Lanza el instalador del sistema. Devuelve false si antes hay que conceder
     * "Instalar apps desconocidas" (permiso especial REQUEST_INSTALL_PACKAGES): se abre esa pantalla de Ajustes.
     */
    fun instalar(apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !contexto.packageManager.canRequestPackageInstalls()) {
            contexto.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${contexto.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
        return true
    }

    private companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
    }
}
