package com.episcan.app

import android.app.Application
import com.episcan.app.data.EpiRepository
import com.episcan.app.data.SettingsStore
import com.episcan.app.data.local.AppDatabase
import com.episcan.app.data.remote.EpiAnalyzer
import com.episcan.app.data.remote.GeminiApi
import com.episcan.app.ota.OtaUpdateManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias manual (la app es lo bastante pequeña como para no necesitar Hilt). */
class AppContainer(app: Application) {
    val ajustes = SettingsStore(app)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build() // sin interceptor de logs: la clave de Gemini viaja en una cabecera

    private val geminiApi: GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeminiApi::class.java)

    val repositorio = EpiRepository(AppDatabase.crear(app).epiDao())
    val analizador = EpiAnalyzer(geminiApi, ajustes)
    val ota = OtaUpdateManager(app, http)
}

class EpiApp : Application() {
    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
    }
}
