package com.episcan.app.data.remote

import android.util.Base64
import com.episcan.app.data.PARTES_CUERPO
import com.episcan.app.data.ResultadoIa
import com.episcan.app.data.SettingsStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ErrorAnalisis(mensaje: String) : Exception(mensaje)

/** Envía las fotos comprimidas a Gemini Vision y devuelve la ficha propuesta. */
class EpiAnalyzer(
    private val api: GeminiApi,
    private val ajustes: SettingsStore,
) {
    private val gson = Gson()

    suspend fun analizar(fotos: List<File>): ResultadoIa {
        val clave = ajustes.geminiApiKey
        if (clave.isBlank()) throw ErrorAnalisis("Falta la clave de Gemini. Introdúcela en Ajustes.")
        if (fotos.isEmpty()) throw ErrorAnalisis("Añade al menos una foto.")

        val cuerpo = withContext(Dispatchers.IO) { construirPeticion(fotos) }
        val respuesta = enviarConReintentos(ajustes.geminiModel, clave, cuerpo)
        return interpretar(respuesta)
    }

    private suspend fun enviarConReintentos(modelo: String, clave: String, cuerpo: JsonObject): JsonObject {
        var ultimoError = "Error desconocido"
        repeat(MAX_INTENTOS) { intento ->
            try {
                val r = api.generarContenido(modelo, clave, cuerpo)
                if (r.isSuccessful) return r.body() ?: throw ErrorAnalisis("Respuesta vacía de Gemini.")
                val detalle = extraerMensajeError(r.errorBody()?.string())
                ultimoError = "Gemini respondió ${r.code()}: $detalle"
                // Solo se reintenta ante saturación o fallos transitorios del servidor
                if (r.code() !in CODIGOS_REINTENTABLES) throw ErrorAnalisis(ultimoError)
            } catch (e: IOException) {
                ultimoError = "Sin conexión o tiempo de espera agotado (${e.message ?: "red"})."
            }
            if (intento < MAX_INTENTOS - 1) delay(2_000L * (intento + 1))
        }
        throw ErrorAnalisis(ultimoError)
    }

    private fun extraerMensajeError(cruda: String?): String {
        if (cruda.isNullOrBlank()) return "sin detalle"
        return runCatching {
            gson.fromJson(cruda, JsonObject::class.java).getAsJsonObject("error").get("message").asString
        }.getOrDefault(cruda.take(200))
    }

    private fun construirPeticion(fotos: List<File>): JsonObject {
        val partes = JsonArray()
        partes.add(JsonObject().apply { addProperty("text", "Analiza el EPI que aparece en las ${fotos.size} fotografía(s) adjunta(s) (son del mismo equipo) y devuelve la ficha técnica.") })
        fotos.forEach { f ->
            val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            partes.add(JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", "image/jpeg")
                    addProperty("data", b64)
                })
            })
        }
        return JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", INSTRUCCIONES) }) })
            })
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", partes)
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.1)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", esquema())
            })
        }
    }

    private fun esquema(): JsonObject {
        fun texto(desc: String) = JsonObject().apply {
            addProperty("type", "STRING")
            addProperty("description", desc)
        }
        return JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", JsonObject().apply {
                add("parteCuerpo", JsonObject().apply {
                    addProperty("type", "STRING")
                    add("enum", JsonArray().apply { PARTES_CUERPO.forEach { add(it) } })
                })
                add("nombreEpi", texto("Denominación técnica oficial del EPI"))
                add("marca", texto("Marca o fabricante visible; cadena vacía si no se ve"))
                add("modelo", texto("Referencia o modelo serigrafiado; cadena vacía si no se ve"))
                add("normativa", texto("Normas EN/ISO y marcado CE detectados, separados por ' · '"))
                add("simbolos", texto("Explicación técnica detallada de cada pictograma y código"))
                add("distribuidor", texto("Distribuidor/importador solo si consta en el etiquetado; si no, cadena vacía"))
                add("observaciones", texto("Colores, talla, acabado y notas relevantes"))
            })
            add("required", JsonArray().apply {
                listOf("parteCuerpo", "nombreEpi", "marca", "modelo", "normativa", "simbolos", "distribuidor", "observaciones")
                    .forEach { add(it) }
            })
        }
    }

    private fun interpretar(resp: JsonObject): ResultadoIa {
        val bloqueo = resp.getAsJsonObject("promptFeedback")?.get("blockReason")?.asString
        if (bloqueo != null) throw ErrorAnalisis("Gemini bloqueó la petición ($bloqueo).")

        val candidato = resp.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
            ?: throw ErrorAnalisis("Gemini no devolvió ningún resultado.")
        val texto = candidato.getAsJsonObject("content")?.getAsJsonArray("parts")
            ?.mapNotNull { p -> p.asJsonObject.takeIf { it.get("thought")?.asBoolean != true }?.get("text")?.asString }
            ?.joinToString("")
            .orEmpty()
        if (texto.isBlank()) {
            val motivo = candidato.get("finishReason")?.asString ?: "desconocido"
            throw ErrorAnalisis("Respuesta vacía de Gemini (motivo: $motivo).")
        }
        return parsear(texto)
    }

    internal fun parsear(textoJson: String): ResultadoIa {
        val limpio = textoJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val r = try {
            gson.fromJson(limpio, ResultadoIa::class.java)
        } catch (e: Exception) {
            throw ErrorAnalisis("No se pudo interpretar la respuesta de Gemini.")
        } ?: throw ErrorAnalisis("Respuesta de Gemini sin contenido.")
        // Gson puede dejar nulos los campos ausentes aunque sean no-nulables en Kotlin
        @Suppress("USELESS_ELVIS")
        return ResultadoIa(
            parteCuerpo = normalizarParte(r.parteCuerpo ?: ""),
            nombreEpi = (r.nombreEpi ?: "").trim(),
            marca = (r.marca ?: "").trim(),
            modelo = (r.modelo ?: "").trim(),
            normativa = (r.normativa ?: "").trim(),
            simbolos = (r.simbolos ?: "").trim(),
            distribuidor = (r.distribuidor ?: "").trim(),
            observaciones = (r.observaciones ?: "").trim(),
        )
    }

    private fun normalizarParte(valor: String): String =
        PARTES_CUERPO.firstOrNull { it.equals(valor.trim(), ignoreCase = true) } ?: ""

    private companion object {
        const val MAX_INTENTOS = 3
        val CODIGOS_REINTENTABLES = setOf(429, 500, 502, 503, 504)

        val INSTRUCCIONES = """
Eres un técnico superior en Prevención de Riesgos Laborales experto en la normativa de Equipos de Protección Individual (Reglamento UE 2016/425 y normas EN/ISO armonizadas).
Recibes fotografías de un mismo EPI (etiquetas, serigrafías, marcado CE, pictogramas) y debes rellenar su ficha técnica de homologación.

REGLAS ESTRICTAS
1. Lee con rigor lo que está impreso: marcado CE (y nº de organismo notificado si aparece), categoría de riesgo (I, II o III), pictogramas, normas y los dígitos/letras de rendimiento que las acompañan, marca, modelo y código comercial.
2. NO inventes nada. Si un dato no es legible o no aparece en las fotos, devuelve cadena vacía (o "No legible" dentro de 'simbolos' si un código concreto no se distingue). Es preferible un campo vacío a uno erróneo: un técnico validará la ficha.
3. 'parteCuerpo' debe ser exactamente uno de los valores permitidos por el esquema.
4. 'nombreEpi' es la denominación técnica oficial (p. ej. "Guantes de protección contra riesgos mecánicos", "Calzado de seguridad", "Gafas de protección ocular", "Casco de protección para la industria", "Semimáscara filtrante contra partículas").
5. 'normativa' lista las normas con su año si consta (p. ej. "EN 388:2016+A1:2018 · EN ISO 21420:2020 · CE Cat. II"), separadas por " · ".
6. 'simbolos' es la parte más importante: explica CADA pictograma, letra y dígito presente y su significado concreto PARA ESTE EQUIPO, en frases cortas separadas por saltos de línea. Ejemplos de desglose:
   - EN 388 (guantes mecánicos): cuatro dígitos + letra. 1º abrasión (0-4), 2º corte por cuchilla (0-5), 3º desgarro (0-4), 4º punción (0-4); la letra (A-F) es el corte ISO 13997 (TDM) y P indica protección contra impactos. Indica el valor concreto de cada uno, p. ej. "Abrasión nivel 4: resiste 8000 ciclos".
   - EN 407 (calor y llama), EN 511 (frío), EN 374 (químicos: tipos A/B/C y letras de sustancias; virus/bacterias), EN 421 (radiación).
   - EN ISO 20345 / 20347 / 20346 (calzado): categoría SB, S1, S2, S3, S4, S5 y requisitos adicionales (P antiperforación, E absorción de energía en talón, A antiestático, WR resistencia al agua, HRO calor de contacto, CI aislamiento del frío, SRA/SRB/SRC antideslizamiento, AN protección de tobillo, M metatarso, WRU/FO). Indica también el marcado de la puntera (200 J).
   - EN 166 (protección ocular): campo de uso (3 gotas líquidas, 4 polvo grueso, 5 gas y polvo fino, 8 arco eléctrico, 9 metal fundido), resistencia mecánica (S, F, B, A), marca del fabricante, y filtros EN 169/170/172 con su número de escala. EN 175 soldadura, EN 379.
   - EN 352 (auditiva): SNR/H/M/L en dB y clase de aplicación; EN 458.
   - EN 149 (mascarillas autofiltrantes FFP1/FFP2/FFP3, NR/R, D dolomita), EN 143 (filtros P1-P3), EN 14387 (filtros gas: tipo A/B/E/K y clase 1-3), EN 140/136 (medias máscaras y máscaras completas).
   - EN 397 (casco de industria: -30 °C/-20 °C, 440 V ac, MM metal fundido, LD deformación lateral), EN 12492 (montañismo), EN 812 (gorra antigolpes).
   - EN 361 (arnés anticaídas), EN 362 (conectores), EN 353/355/354/358/795 (anticaídas, absorbedores, amarres, posicionamiento, anclajes), EN 341 (descensores). Incluye carga máxima si aparece.
   - EN ISO 20471 (alta visibilidad: clase 1/2/3), EN ISO 11612, EN ISO 11611, EN 1149-5, EN 13034 / EN 14605 (ropa contra químicos, tipos 3-6), EN 343 (lluvia).
   - Otros pictogramas: libro abierto con "i" = leer el manual de instrucciones, fecha de caducidad, talla, temperatura de almacenamiento, etc.
   Si el equipo no tiene un pictograma o norma de los anteriores, describe los que sí tenga.
7. 'distribuidor': solo si el distribuidor o importador consta en el etiquetado. Si no consta, cadena vacía. No lo deduzcas.
8. 'observaciones': colores, talla, material visible, versión, y cualquier advertencia (caducidad, uso limitado, marcado poco legible).
9. Escribe todo en español, con texto plano (sin Markdown).
        """.trimIndent()
    }
}
