package com.episcan.app.data.remote

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @POST("v1beta/models/{modelo}:generateContent")
    suspend fun generarContenido(
        @Path("modelo") modelo: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body cuerpo: JsonObject,
    ): Response<JsonObject>
}
