package com.episcan.app.data

import android.content.Context
import androidx.core.content.edit
import com.episcan.app.BuildConfig
import com.episcan.app.sync.ConfigSync

/** Preferencias locales. Los valores de BuildConfig actúan como valor por defecto. */
class SettingsStore(context: Context) : ConfigSync {
    private val prefs = context.getSharedPreferences("episcan_prefs", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = leer(K_API_KEY, BuildConfig.GEMINI_API_KEY)
        set(v) = guardar(K_API_KEY, v, BuildConfig.GEMINI_API_KEY)

    var geminiModel: String
        get() = leer(K_MODELO, BuildConfig.GEMINI_MODEL)
        set(v) = guardar(K_MODELO, v, BuildConfig.GEMINI_MODEL)

    var otaUrl: String
        get() = leer(K_OTA_URL, BuildConfig.OTA_UPDATE_URL)
        set(v) = guardar(K_OTA_URL, v, BuildConfig.OTA_UPDATE_URL)

    var otaAutoComprobar: Boolean
        get() = prefs.getBoolean(K_OTA_AUTO, true)
        set(v) = prefs.edit { putBoolean(K_OTA_AUTO, v) }

    // ---- Sincronización con el servidor (API en Coolify) ----
    /** Dirección de la API, p. ej. https://api-epis.midominio.com */
    override var url: String
        get() = leer(K_SYNC_URL, BuildConfig.SYNC_URL)
        set(v) = guardar(K_SYNC_URL, v, BuildConfig.SYNC_URL)

    /** Secreto compartido con el servidor. Nunca va en el APK: se escribe en Ajustes de cada móvil. */
    override var token: String
        get() = prefs.getString(K_SYNC_TOKEN, null).orEmpty()
        set(v) = prefs.edit { putString(K_SYNC_TOKEN, v.trim()) }

    var syncAuto: Boolean
        get() = prefs.getBoolean(K_SYNC_AUTO, true)
        set(v) = prefs.edit { putBoolean(K_SYNC_AUTO, v) }

    override var ultimaRevision: Long
        get() = prefs.getLong(K_SYNC_REVISION, 0)
        set(v) = prefs.edit { putLong(K_SYNC_REVISION, v) }

    /** Servidor con el que se sincronizó por última vez; si cambia, se vuelve a enviar todo. */
    override var servidorSincronizado: String
        get() = prefs.getString(K_SYNC_SERVIDOR, null).orEmpty()
        set(v) = prefs.edit { putString(K_SYNC_SERVIDOR, v) }

    /** Un valor guardado vacío se ignora: así no tapa el que trae la compilación. */
    private fun leer(clave: String, defecto: String): String =
        prefs.getString(clave, null)?.takeIf { it.isNotBlank() } ?: defecto

    /**
     * Solo se guarda lo que el usuario ha cambiado de verdad. Si queda vacío o igual al valor por defecto
     * se borra, para que las compilaciones futuras puedan actualizar ese valor por defecto.
     */
    private fun guardar(clave: String, valor: String, defecto: String) {
        val v = valor.trim()
        prefs.edit { if (v.isEmpty() || v == defecto) remove(clave) else putString(clave, v) }
    }

    private companion object {
        const val K_API_KEY = "gemini_api_key"
        const val K_MODELO = "gemini_model"
        const val K_OTA_URL = "ota_url"
        const val K_OTA_AUTO = "ota_auto"
        const val K_SYNC_URL = "sync_url"
        const val K_SYNC_TOKEN = "sync_token"
        const val K_SYNC_AUTO = "sync_auto"
        const val K_SYNC_REVISION = "sync_revision"
        const val K_SYNC_SERVIDOR = "sync_servidor"
    }
}
