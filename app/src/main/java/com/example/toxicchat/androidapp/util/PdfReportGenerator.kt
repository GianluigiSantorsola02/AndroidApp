package com.example.toxicchat.androidapp.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.toxicchat.androidapp.domain.model.ResponseTimeStats
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
 import kotlin.math.ln1p
import kotlin.math.roundToInt

enum class ReportPrivacyMode { PRIVATE, ANONYMOUS }

data class ParticipantStats(
    val name: String,
    val totalMessages: Int,
    val toxicMessages: Int,
    val maxTox: Float = 0f
) {
    val toxicityPercentage: Float = if (totalMessages > 0) (toxicMessages.toFloat() / totalMessages) * 100 else 0f
}

data class WeeklyTrendPoint(
    val weekLabel: String,
    val totalMessages: Int,
    val toxicPercentage: Float,
    val toxicCount: Int = 0
)

data class DayStats(
    val dayName: String,
    val totalMessages: Int,
    val toxicMessages: Int
)

data class ReportData(
    val fileName: String,
    val startDate: String,
    val endDate: String,
    val totalMessages: Int,
    val toxicMessages: Int,
    val participants: List<ParticipantStats>,
    val weeklyTrend: List<WeeklyTrendPoint>,
    val dailyDistribution: List<DayStats>,
    val heatmap: Array<FloatArray>,
    val weekWithMostCritical: String?,
    val peakCriticityDayTime: String?,
    val weekWithMostMessages: String?,
    val weekWithHighestToxicRate: String?,
    val mostCriticalDay: String?,
    val mostCriticalHour: String?,
    val maxToxicityScore: Float,
    val responseStats: ResponseTimeStats?,
    val topCriticalWeeks: List<WeeklyTrendPoint>,
    val isMonthly: Boolean = false // Indica se l'aggregazione è mensile (>20 settimane)
) {
    val globalToxicityPercentage: Float = if (totalMessages > 0) (toxicMessages.toFloat() / totalMessages) * 100 else 0f
}

class PdfReportGenerator {

    private val COLOR_PRIMARY = Color.parseColor("#006064")
    private val COLOR_RED = Color.parseColor("#D32F2F")
    private val COLOR_GRAY_LIGHT = Color.parseColor("#F5F5F5")
    private val COLOR_GRAY_BARS = Color.parseColor("#DADADA")
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#111111")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#555555")

    private val PAGE_WIDTH = 595
    private val PAGE_HEIGHT = 842
    private val MARGIN = 48f

    suspend fun generate(
        context: Context,
        data: ReportData,
        privacyMode: ReportPrivacyMode
    ): Uri? {
        val pdfDocument = PdfDocument()
        val processedData = if (privacyMode == ReportPrivacyMode.ANONYMOUS) anonymizeData(data) else data

        try {
            drawPage1Overview(pdfDocument, processedData)
            drawPage2WeeklyTrend(pdfDocument, processedData)
            drawPage3TemporalDistribution(pdfDocument, processedData)
            drawPage4Participants(pdfDocument, processedData)
            drawPage5CriticalMoments(pdfDocument, processedData)

            val datePart = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val fileName = "Report_Analisi_$datePart.pdf"
            
            return savePdf(context, pdfDocument, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    private fun anonymizeData(data: ReportData): ReportData {
        val anonymizedParticipants = data.participants
            .sortedByDescending { it.totalMessages }
            .mapIndexed { index, stats ->
                stats.copy(name = "Partecipante ${'A' + index}")
            }
        return data.copy(participants = anonymizedParticipants)
    }

    private fun drawPage1Overview(pdfDocument: PdfDocument, data: ReportData) {
        val page = startNewPage(pdfDocument, 1)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, "Report analisi conversazione")

        var y = 140f
        paint.textSize = 12f
        paint.color = COLOR_TEXT_PRIMARY
        canvas.drawText("Nome file: ${data.fileName}", MARGIN, y, paint); y += 22f
        canvas.drawText("Periodo: ${data.startDate} - ${data.endDate}", MARGIN, y, paint); y += 22f
        canvas.drawText("Partecipanti: ${data.participants.size}", MARGIN, y, paint); y += 60f

        drawStatBox(canvas, MARGIN, y, "Messaggi Totali", data.totalMessages.toString(), COLOR_PRIMARY)
        drawStatBox(canvas, MARGIN + 180f, y, "Messaggi critici", data.toxicMessages.toString(), COLOR_RED)
        drawStatBox(canvas, MARGIN + 360f, y, "% messaggi critici", "%.1f%%".format(data.globalToxicityPercentage), if (data.globalToxicityPercentage > 15) COLOR_RED else COLOR_PRIMARY)
        
        y += 100f
        val unitLabel = if (data.isMonthly) "Mese più critico" else "Settimana più critica"
        drawStatBox(canvas, MARGIN, y, unitLabel, data.weekWithMostCritical ?: "N/D", COLOR_TEXT_SECONDARY)
        drawStatBox(canvas, MARGIN + 230f, y, "Giorno e fascia più critici", data.peakCriticityDayTime ?: "N/D", COLOR_TEXT_SECONDARY)

        y += 120f
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Sintesi interpretativa", MARGIN, y, paint)
        y += 25f
        
        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = COLOR_TEXT_SECONDARY
        
        val interpretiveText = when {
            data.globalToxicityPercentage > 20 -> "L'analisi rileva un'elevata concentrazione di messaggi critici. Si consiglia una revisione qualitativa dei momenti di picco indicati nel report."
            data.globalToxicityPercentage > 5 -> "La conversazione presenta dinamiche di criticità localizzate in specifici archi temporali o tra determinati partecipanti."
            else -> "L'analisi mostra una conversazione con bassi livelli di criticità complessiva, con solo sporadici episodi critici isolati."
        }
        
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        drawWrappedText(canvas, interpretiveText, MARGIN, y, maxWidth, paint)

        drawFooter(canvas, 1)
        pdfDocument.finishPage(page)
    }

    private fun drawPage2WeeklyTrend(pdfDocument: PdfDocument, data: ReportData) {
        val page = startNewPage(pdfDocument, 2)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // FIX: Titolo dinamico
        val title = if (data.isMonthly) "Andamento mensile" else "Andamento settimanale"
        drawHeader(canvas, title)

        val chartRect = RectF(MARGIN, 160f, PAGE_WIDTH - MARGIN, 450f)
        drawWeeklyComboChart(canvas, chartRect, data.weeklyTrend)

        var y = 520f
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = COLOR_PRIMARY
        val insightHeader = if (data.isMonthly) "Insight mensili" else "Insight settimanali"
        canvas.drawText(insightHeader, MARGIN, y, paint)
        y += 30f
        
        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = COLOR_TEXT_PRIMARY
        val unit = if (data.isMonthly) "Mese" else "Settimana"
        canvas.drawText("• $unit con maggior volume: ${data.weekWithMostMessages}", MARGIN + 10, y, paint); y += 22f
        canvas.drawText("• $unit con % critica più alta: ${data.weekWithHighestToxicRate}", MARGIN + 10, y, paint); y += 22f
        val countLabel = if (data.isMonthly) "mesi analizzati" else "settimane analizzate"
        canvas.drawText("• Numero totale di $countLabel: ${data.weeklyTrend.size}", MARGIN + 10, y, paint)

        drawFooter(canvas, 2)
        pdfDocument.finishPage(page)
    }

    private fun drawPage3TemporalDistribution(pdfDocument: PdfDocument, data: ReportData) {
        val page = startNewPage(pdfDocument, 3)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, "Distribuzione per giorno e orario")

        val heatmapRect = RectF(MARGIN + 45f, 160f, PAGE_WIDTH - MARGIN - 20f, 450f)
        drawHeatmap(canvas, heatmapRect, data.heatmap)
        
        drawHeatmapLegend(canvas, MARGIN + 45f, 470f)

        var y = 550f
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = COLOR_PRIMARY
        canvas.drawText("Analisi delle ricorrenze", MARGIN, y, paint)
        y += 30f
        
        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = COLOR_TEXT_PRIMARY
        canvas.drawText("• Giorno con maggiore criticità: ${data.mostCriticalDay}", MARGIN + 10, y, paint); y += 22f
        canvas.drawText("• Fascia oraria più critica: ${data.mostCriticalHour}", MARGIN + 10, y, paint); y += 22f
        
        val recurrenceNote = if (data.mostCriticalHour?.contains("22:") == true || data.mostCriticalHour?.contains("23:") == true) {
            "Si nota una tendenza alla criticità nelle ore tarde della giornata."
        } else "La distribuzione oraria non mostra pattern di ricorrenza notturna anomali."
        canvas.drawText("• Nota: $recurrenceNote", MARGIN + 10, y, paint)

        drawFooter(canvas, 3)
        pdfDocument.finishPage(page)
    }

    private fun drawPage4Participants(pdfDocument: PdfDocument, data: ReportData) {
        val page = startNewPage(pdfDocument, 4)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, "Partecipanti")

        var y = 140f
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Quote di partecipazione e criticità", MARGIN, y, paint)
        y += 30f

        val chartHeight = 120f
        drawHorizontalBarChart(canvas, MARGIN, y, PAGE_WIDTH - 2*MARGIN, chartHeight, "Volume messaggi inviati (%)", data.participants, { it.totalMessages.toFloat() }, COLOR_PRIMARY)
        y += chartHeight + 40f
        drawHorizontalBarChart(canvas, MARGIN, y, PAGE_WIDTH - 2*MARGIN, chartHeight, "% messaggi critici (%)", data.participants, { it.toxicMessages.toFloat() }, COLOR_RED)
        y += chartHeight + 40f

        if (data.participants.size == 2 && data.responseStats != null) {
            paint.isFakeBoldText = true
            canvas.drawText("Tempi di risposta medi", MARGIN, y, paint)
            y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("${data.participants[0].name} -> ${data.participants[1].name}: ${"%.1f".format(data.responseStats.medianSelfToOtherMin)} min (mediana)", MARGIN + 10, y, paint); y += 20f
            canvas.drawText("${data.participants[1].name} -> ${data.participants[0].name}: ${"%.1f".format(data.responseStats.medianOtherToSelfMin)} min (mediana)", MARGIN + 10, y, paint); y += 30f
        }

        paint.isFakeBoldText = true
        canvas.drawText("Tabella di sintesi", MARGIN, y, paint)
        y += 20f
        paint.textSize = 10f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20, paint.apply { color = COLOR_GRAY_LIGHT })
        paint.color = COLOR_TEXT_PRIMARY
        canvas.drawText("Partecipante", MARGIN + 5, y + 14, paint)
        canvas.drawText("Messaggi inviati", MARGIN + 180, y + 14, paint)
        canvas.drawText("Messaggi critici", MARGIN + 280, y + 14, paint)
        canvas.drawText("% messaggi critici", MARGIN + 370, y + 14, paint)
        canvas.drawText("Tossicità max", MARGIN + 470, y + 14, paint)
        y += 20f
        
        paint.isFakeBoldText = false
        data.participants.forEach { p ->
            if (y > PAGE_HEIGHT - 80) return@forEach
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint.apply { strokeWidth = 0.5f; color = COLOR_GRAY_BARS })
            paint.color = COLOR_TEXT_PRIMARY
            canvas.drawText(p.name, MARGIN + 5, y + 15, paint)
            canvas.drawText(p.totalMessages.toString(), MARGIN + 180, y + 15, paint)
            canvas.drawText(p.toxicMessages.toString(), MARGIN + 280, y + 15, paint)
            canvas.drawText("%.1f%%".format(p.toxicityPercentage), MARGIN + 370, y + 15, paint)
            canvas.drawText("%.2f".format(p.maxTox), MARGIN + 470, y + 15, paint)
            y += 20f
        }

        drawFooter(canvas, 4)
        pdfDocument.finishPage(page)
    }

    private fun drawPage5CriticalMoments(pdfDocument: PdfDocument, data: ReportData) {
        val page = startNewPage(pdfDocument, 5)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, "Momenti di maggiore criticità")

        var y = 140f
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Analisi dei picchi rilevati", MARGIN, y, paint)
        y += 40f

        drawStatBox(canvas, MARGIN, y, "Massima tossicità rilevata", "%.2f".format(data.maxToxicityScore), COLOR_RED)
        y += 80f

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Settimane con maggiore concentrazione critica:", MARGIN, y, paint)
        y += 25f
        paint.isFakeBoldText = false
        paint.textSize = 11f
        
        val criticalWeeks = data.topCriticalWeeks.filter { it.toxicCount > 0 }
        
        if (criticalWeeks.isEmpty()) {
            canvas.drawText("Nel periodo analizzato non sono emerse settimane con criticità rilevata.", MARGIN + 10, y, paint)
            y += 22f
        } else {
            criticalWeeks.take(5).forEach { week ->
                val msgWord = if (week.toxicCount == 1) "messaggio critico" else "messaggi critici"
                canvas.drawText("• ${week.weekLabel}: ${week.toxicCount} $msgWord (${"%.1f%%".format(week.toxicPercentage)})", MARGIN + 10, y, paint)
                y += 22f
            }
        }

        y += 30f
        paint.isFakeBoldText = true
        canvas.drawText("Partecipanti maggiormente coinvolti nei picchi:", MARGIN, y, paint)
        y += 25f
        paint.isFakeBoldText = false
        data.participants.sortedByDescending { it.toxicMessages }.take(3).forEach { p ->
            if (p.toxicMessages > 0) {
                canvas.drawText("• ${p.name}: autore del ${((p.toxicMessages.toFloat() / data.toxicMessages.coerceAtLeast(1)) * 100).roundToInt()}% della criticità totale", MARGIN + 10, y, paint)
                y += 22f
            }
        }

        y += 40f
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 10f
        val closingNote = "Questo report fornisce una sintesi statistica e non sostituisce una valutazione umana del contesto delle conversazioni."
        canvas.drawText(closingNote, MARGIN, y, paint)

        drawFooter(canvas, 5)
        pdfDocument.finishPage(page)
    }

    private fun startNewPage(pdfDocument: PdfDocument, pageNumber: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return pdfDocument.startPage(pageInfo)
    }

    private fun drawHeader(canvas: Canvas, title: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_PRIMARY
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(title, MARGIN, 80f, paint)
        paint.strokeWidth = 2f
        canvas.drawLine(MARGIN, 95f, PAGE_WIDTH - MARGIN, 95f, paint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 9f
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generato il: $dateStr", MARGIN, PAGE_HEIGHT - 30f, paint)
        val pageStr = "Pagina $pageNumber di 5"
        val w = paint.measureText(pageStr)
        canvas.drawText(pageStr, PAGE_WIDTH - MARGIN - w, PAGE_HEIGHT - 30f, paint)
    }

    private fun drawStatBox(canvas: Canvas, x: Float, y: Float, label: String, value: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 10f
        canvas.drawText(label, x, y, paint)
        paint.color = color
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(value, x, y + 28f, paint)
    }

    private fun drawWeeklyComboChart(canvas: Canvas, rect: RectF, trend: List<WeeklyTrendPoint>) {
        if (trend.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barSpacing = 1.3f
        val numPoints = trend.size
        val barWidth = rect.width() / (numPoints.coerceAtLeast(1) * barSpacing)
        
        // FIX: Scala logaritmica per l'asse Y del volume
        val maxVolume = trend.maxOfOrNull { it.totalMessages }?.toFloat() ?: 1f
        val logMax = ln1p(maxVolume.toDouble()).toFloat()

        trend.forEachIndexed { i, point ->
            val x = rect.left + i * barWidth * barSpacing
            
            // FIX: Calcolo altezza logaritmica
            val logVol = ln1p(point.totalMessages.toDouble()).toFloat()
            val h = (logVol / logMax) * rect.height()
            
            paint.color = COLOR_GRAY_BARS
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, rect.bottom - h, x + barWidth, rect.bottom, paint)
            
            // FIX: Data label reale sopra la barra logaritmica
            paint.color = COLOR_TEXT_SECONDARY
            paint.textSize = 8f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(point.totalMessages.toString(), x + barWidth / 2, rect.bottom - h - 5f, paint)
            
            // X Axis Labels
            if (numPoints < 15 || i % (numPoints / 8).coerceAtLeast(1) == 0) {
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 7f
                canvas.save()
                canvas.rotate(-45f, x, rect.bottom + 8f)
                canvas.drawText(point.weekLabel, x, rect.bottom + 12f, paint)
                canvas.restore()
            }
        }

        // Linea percentuale (lineare)
        paint.color = COLOR_RED
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        paint.textAlign = Paint.Align.LEFT
        val path = Path()
        trend.forEachIndexed { i, point ->
            val x = rect.left + i * barWidth * barSpacing + barWidth / 2
            val y = rect.bottom - (point.toxicPercentage / 100f).coerceIn(0f, 1f) * rect.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        
        // Legenda
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        paint.color = COLOR_GRAY_BARS
        canvas.drawRect(rect.left, rect.top - 25f, rect.left + 15, rect.top - 15f, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Volume totale", rect.left + 20, rect.top - 16f, paint)
        paint.color = COLOR_RED
        canvas.drawCircle(rect.left + 120, rect.top - 20f, 3f, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("% messaggi critici", rect.left + 130, rect.top - 16f, paint)
    }

    private fun drawHeatmap(canvas: Canvas, rect: RectF, matrix: Array<FloatArray>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellW = rect.width() / 24f
        val cellH = rect.height() / 7f
        val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

        for (d in 0..6) {
            paint.color = COLOR_TEXT_SECONDARY
            paint.textSize = 9f
            canvas.drawText(days[d], rect.left - 35f, rect.top + d * cellH + cellH / 2 + 4f, paint)
            for (h in 0..23) {
                val toxicPct = if (d < matrix.size && h < matrix[d].size) matrix[d][h] else 0f
                paint.color = if (toxicPct > 0) {
                    val alpha = (toxicPct / 100f * 255).toInt().coerceIn(20, 255)
                    Color.argb(alpha, 211, 47, 47)
                } else COLOR_GRAY_LIGHT
                canvas.drawRect(rect.left + h * cellW + 1f, rect.top + d * cellH + 1f, rect.left + (h + 1) * cellW - 1f, rect.top + (d + 1) * cellH - 1f, paint)
            }
        }
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 8f
        for (h in 0..23 step 4) canvas.drawText("${h}h", rect.left + h * cellW, rect.bottom + 15f, paint)
    }

    private fun drawHeatmapLegend(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 9f
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Intensità criticità: ", x, y + 10, paint)
        
        val startX = x + 100f
        val step = 20f
        for (i in 0..4) {
            val alpha = (i * 50 + 55).coerceIn(0, 255)
            paint.color = Color.argb(alpha, 211, 47, 47)
            canvas.drawRect(startX + i * step, y, startX + (i + 1) * step, y + 12, paint)
        }
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Min", startX, y + 25, paint)
        canvas.drawText("Max", startX + 4 * step, y + 25, paint)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(" ")
        val line = StringBuilder()
        var curY = y
        for (word in words) {
            val testLine = if (line.isEmpty()) word else line.toString() + " " + word
            val width = paint.measureText(testLine)
            if (width > maxWidth) {
                canvas.drawText(line.toString(), x, curY, paint)
                line.setLength(0)
                line.append(word)
                curY += paint.textSize * 1.5f
            } else {
                line.append(if (line.isEmpty()) word else " $word")
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, curY, paint)
        }
    }

    private fun drawHorizontalBarChart(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, title: String, participants: List<ParticipantStats>, valueSelector: (ParticipantStats) -> Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        paint.color = COLOR_TEXT_PRIMARY
        canvas.drawText(title, x, y, paint)
        
        val total = participants.sumOf { valueSelector(it).toDouble() }.toFloat().coerceAtLeast(1f)
        var curY = y + 20f
        val barHeight = 12f
        
        participants.sortedByDescending { valueSelector(it) }.take(5).forEach { p ->
            val pVal = valueSelector(p)
            if (pVal <= 0) return@forEach
            val pct = pVal / total
            paint.isFakeBoldText = false
            paint.textSize = 9f
            canvas.drawText(p.name, x, curY + 9f, paint)
            
            paint.color = color
            canvas.drawRect(x + 100f, curY, x + 100f + (width - 150f) * pct, curY + barHeight, paint)
            
            paint.color = COLOR_TEXT_SECONDARY
            canvas.drawText("${(pct * 100).roundToInt()}%", x + width - 30f, curY + 9f, paint)
            curY += 20f
        }
    }

    private fun savePdf(context: Context, pdfDocument: PdfDocument, fileName: String): Uri? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            return uri?.also {
                resolver.openOutputStream(it)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            try {
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                return Uri.fromFile(file)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
    }
}
