package com.episcan.app.data

import android.content.Context
import androidx.core.content.edit
import com.episcan.app.BuildConfig

/** Preferencias locales. Los valores de BuildConfig actúan como valor por defecto. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("episcan_prefs", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString(K_API_KEY, null) ?: BuildConfig.GEMINI_API_KEY
        set(v) = prefs.edit { putString(K_API_KEY, v.trim()) }

    var geminiModel: String
        get() = (prefs.getString(K_MODELO, null) ?: BuildConfig.GEMINI_MODEL).ifBlank { BuildConfig.GEMINI_MODEL }
        set(v) = prefs.edit { putString(K_MODELO, v.trim()) }

    var otaUrl: String
        get() = prefs.getString(K_OTA_URL, null) ?: BuildConfig.OTA_UPDATE_URL
        set(v) = prefs.edit { putString(K_OTA_URL, v.trim()) }

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
