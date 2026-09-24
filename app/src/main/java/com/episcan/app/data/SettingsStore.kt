package com.episcan.app.data

import android.content.Context
import androidx.core.content.edit
import com.episcan.app.BuildConfig

/** Preferencias locales. Los valores de BuildConfig actúan como valor por defecto. */
class SettingsStore(context: Context) {
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

    var otaAutoComprobar: Boolean
        get() = prefs.getBoolean(K_OTA_AUTO, true)
        set(v) = prefs.edit { putBoolean(K_OTA_AUTO, v) }

    private companion object {
        const val K_API_KEY = "gemini_api_key"
        const val K_MODELO = "gemini_model"
        const val K_OTA_URL = "ota_url"
        const val K_OTA_AUTO = "ota_auto"
    }
}
