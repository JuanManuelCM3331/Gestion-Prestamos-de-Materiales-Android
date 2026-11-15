package com.example.prestamo_lab_ctma.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.model.SolicitudPrestamo
import java.io.File
import java.io.FileOutputStream

/**
 * Generador y exportador del Certificado Oficial de Préstamo en formato PNG.
 */
object CertificadoGenerator {

    fun generarBitmapCertificado(
        solicitud: SolicitudPrestamo,
        equipo: Equipo?
    ): Bitmap {
        val width = 1080
        val height = 1480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fondo blanco institucional
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // Marco exterior verde SENA CTMA
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRoundRect(RectF(32f, 32f, width - 32f, height - 32f), 28f, 28f, borderPaint)

        // Encabezado superior
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(32f, 32f, width - 32f, 260f), 28f, 28f, headerPaint)
        canvas.drawRect(RectF(32f, 180f, width - 32f, 260f), headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C8E6C9")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("CERTIFICADO DE PRÉSTAMO DE EQUIPO", width / 2f, 125f, titlePaint)
        canvas.drawText("SENA - Centro de Tecnología de la Manufactura Avanzada (CTMA)", width / 2f, 185f, subtitlePaint)
        canvas.drawText("Código de Verificación: ${solicitud.codigoCertificado}", width / 2f, 232f, subtitlePaint)

        // Badge de Estado
        val badgeColor = when {
            solicitud.estado.esActivo() -> Color.parseColor("#2E7D32")
            solicitud.estado.esEntregado() -> Color.parseColor("#1565C0")
            else -> Color.parseColor("#C62828")
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(360f, 300f, 720f, 370f), 35f, 35f, badgePaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ESTADO: ${solicitud.estado.etiquetaEspanol()}", width / 2f, 346f, badgeTextPaint)

        // Secciones de datos
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
        }

        var y = 440f
        fun drawField(label: String, value: String) {
            canvas.drawText(label, 90f, y, labelPaint)
            y += 42f
            val clipped = if (value.length > 52) value.take(52) + "..." else value
            canvas.drawText(clipped.ifBlank { "No especificado" }, 90f, y, valuePaint)
            y += 22f
            canvas.drawLine(90f, y, width - 90f, y, linePaint)
            y += 46f
        }

        canvas.drawText("1. DATOS DEL EQUIPO Y CANTIDAD", 90f, y, sectionTitlePaint)
        y += 50f
        val nombreEq = solicitud.equipoNombre.ifBlank { equipo?.nombre ?: "Equipo #${solicitud.equipoId}" }
        val catEq = solicitud.equipoCategoria.ifBlank { equipo?.categoria?.name ?: "GENERAL" }
        drawField("Equipo Solicitado:", "$nombreEq ($catEq)")
        drawField("Cantidad de Unidades Prestadas:", "${solicitud.cantidad} unidad(es)")

        y += 15f
        canvas.drawText("2. DATOS DEL SOLICITANTE Y DESTINO", 90f, y, sectionTitlePaint)
        y += 50f
        drawField(
            "Solicitante:",
            solicitud.nombreSolicitante.ifBlank { "Aprendiz / Usuario CTMA" } +
                (if (solicitud.documentoSolicitante.isNotBlank()) " — Doc: ${solicitud.documentoSolicitante}" else "")
        )
        drawField("Ambiente / Destino:", solicitud.ambienteDestino)
        drawField("Motivo / Observaciones:", solicitud.proposito)

        y += 15f
        canvas.drawText("3. FECHAS Y CONTROL DE TIEMPO", 90f, y, sectionTitlePaint)
        y += 50f
        drawField("Fecha de Registro del Préstamo:", solicitud.fechaPrestamo.take(19).replace("T", " "))
        drawField("Fecha Estimada de Devolución:", solicitud.fechaDevolucionEstimada.take(19).replace("T", " "))

        // Pie de firma / sello digital
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 23f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Documento generado digitalmente por PréstamoLab CTMA • Solicitud #${solicitud.id}",
            width / 2f,
            height - 95f,
            footerPaint
        )
        canvas.drawText(
            "Conserve este certificado como soporte de salida y devolución de materiales.",
            width / 2f,
            height - 58f,
            footerPaint
        )

        return bitmap
    }

    /**
     * Exporta el certificado como imagen PNG en la galería del dispositivo (Pictures/PrestamoLabCTMA)
     * y retorna la ruta o URI legible para mostrar confirmación al usuario.
     */
    fun exportarCertificadoPng(
        context: Context,
        solicitud: SolicitudPrestamo,
        equipo: Equipo?
    ): Result<String> {
        return try {
            val bitmap = generarBitmapCertificado(solicitud, equipo)
            val fileName = "Certificado_${solicitud.codigoCertificado.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrestamoLabCTMA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return guardarEnCache(context, bitmap, fileName)

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Galería: Pictures/PrestamoLabCTMA/$fileName")
            } else {
                guardarEnCache(context, bitmap, fileName)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun guardarEnCache(context: Context, bitmap: Bitmap, fileName: String): Result<String> {
        val dir = File(context.filesDir, "certificados").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Result.success(file.absolutePath)
    }
}
