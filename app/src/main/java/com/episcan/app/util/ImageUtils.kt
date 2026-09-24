package com.episcan.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.episcan.app.export.Miniatura
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    /** Lado más largo máximo de las capturas enviadas a Gemini. */
    const val LADO_MAX = 1280
    const val CALIDAD_JPEG = 75

    /**
     * Lee la imagen de [origen] (archivo o URI de galería), corrige la orientación EXIF,
     * la reduce a [LADO_MAX] px y la guarda en [destino] como JPEG al [CALIDAD_JPEG] %.
     */
    fun guardarComprimida(context: Context, origen: Uri, destino: File) {
        val cr = context.contentResolver

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(origen)?.use { BitmapFactory.decodeStream(it, null, limites) }
        require(limites.outWidth > 0 && limites.outHeight > 0) { "La imagen no es válida" }

        // Submuestreo por potencias de 2 para no cargar en memoria una foto de 50 MP
        var muestreo = 1
        while (max(limites.outWidth, limites.outHeight) / (muestreo * 2) >= LADO_MAX) muestreo *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
        val original = cr.openInputStream(origen)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: error("No se pudo decodificar la imagen")

        val orientacion = runCatching {
            cr.openInputStream(origen)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matriz = matrizExif(orientacion)
        val escala = min(1f, LADO_MAX.toFloat() / max(original.width, original.height))
        matriz.postScale(escala, escala)
        val final = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)

        destino.parentFile?.mkdirs()
        FileOutputStream(destino).use { final.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it) }
        if (final !== original) original.recycle()
        final.recycle()
    }

    private fun matrizExif(orientacion: Int) = Matrix().apply {
        when (orientacion) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }

    /** Miniatura JPEG pequeña para incrustar en el Excel. */
    fun miniatura(archivo: File, maxAncho: Int = 120, maxAlto: Int = 90): Miniatura? = runCatching {
        if (!archivo.exists()) return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, limites)
        if (limites.outWidth <= 0) return null

        var muestreo = 1
        while (limites.outWidth / (muestreo * 2) >= maxAncho && limites.outHeight / (muestreo * 2) >= maxAlto) muestreo *= 2
        val bmp = BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestreo })
            ?: return null

        val k = min(maxAncho.toFloat() / bmp.width, maxAlto.toFloat() / bmp.height).coerceAtMost(1f)
        val w = max(1, (bmp.width * k).toInt())
        val h = max(1, (bmp.height * k).toInt())
        val reducido = if (k < 1f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
        val salida = ByteArrayOutputStream()
        reducido.compress(Bitmap.CompressFormat.JPEG, 70, salida)
        Miniatura(salida.toByteArray(), reducido.width, reducido.height)
    }.getOrNull()
}
