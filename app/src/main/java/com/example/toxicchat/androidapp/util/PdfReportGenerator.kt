package com.example.toxicchat.androidapp.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

enum class ReportPrivacyMode { PRIVATE, ANONYMOUS }

data class ParticipantStats(
    val name: String,
    val totalMessages: Int,
    val toxicMessages: Int
) {
    val toxicityPercentage: Float = if (totalMessages > 0) (toxicMessages.toFloat() / totalMessages) * 100 else 0f
}

data class WeeklyTrendPoint(
    val weekLabel: String,
    val totalMessages: Int,
    val toxicPercentage: Float
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
    val heatmap: Array<FloatArray>
) {
    val globalToxicityPercentage: Float = if (totalMessages > 0) (toxicMessages.toFloat() / totalMessages) * 100 else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ReportData
        return fileName == other.fileName && startDate == other.startDate && endDate == other.endDate &&
                totalMessages == other.totalMessages && toxicMessages == other.toxicMessages &&
                participants == other.participants && weeklyTrend == other.weeklyTrend &&
                dailyDistribution == other.dailyDistribution && heatmap.contentDeepEquals(other.heatmap)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + startDate.hashCode()
        result = 31 * result + endDate.hashCode()
        result = 31 * result + totalMessages
        result = 31 * result + toxicMessages
        result = 31 * result + participants.hashCode()
        result = 31 * result + weeklyTrend.hashCode()
        result = 31 * result + dailyDistribution.hashCode()
        result = 31 * result + heatmap.contentDeepHashCode()
        return result
    }
}

class PdfReportGenerator {

    private val COLOR_GREEN = Color.parseColor("#2E7D32")
    private val COLOR_RED = Color.parseColor("#D32F2F")
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
            drawOverviewPage(pdfDocument, processedData)
            drawParticipantsPage(pdfDocument, processedData)
            drawWeeklyTrendPage(pdfDocument, processedData)
            drawTemporalDistributionPage(pdfDocument, processedData)

            return savePdf(context, pdfDocument, "Report_Tossicita_${System.currentTimeMillis()}.pdf")
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

    private fun drawOverviewPage(pdfDocument: PdfDocument, data: ReportData) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Report Analisi Tossicità Conversazione", MARGIN, 80f, paint)

        paint.textSize = 14f
        paint.isFakeBoldText = false
        var y = 140f
        canvas.drawText("Nome file analizzato: ${data.fileName}", MARGIN, y, paint); y += 25f
        canvas.drawText("Periodo analizzato: ${data.startDate} - ${data.endDate}", MARGIN, y, paint); y += 25f
        canvas.drawText("Numero partecipanti: ${data.participants.size}", MARGIN, y, paint); y += 60f

        drawStatBox(canvas, MARGIN, y, "Messaggi Totali", data.totalMessages.toString(), COLOR_GREEN)
        drawStatBox(canvas, MARGIN + 180f, y, "Messaggi Sopra Soglia", data.toxicMessages.toString(), COLOR_RED)
        drawStatBox(canvas, MARGIN + 360f, y, "% Tossicità Globale", "%.1f%%".format(data.globalToxicityPercentage), if (data.globalToxicityPercentage > 10) COLOR_RED else COLOR_GREEN)

        y += 120f
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 10f
        canvas.drawText("Formula: Percentuale tossicità = (Messaggi sopra soglia / Messaggi totali) × 100", MARGIN, y, paint)

        drawFooter(canvas)
        pdfDocument.finishPage(page)
    }

    private fun drawParticipantsPage(pdfDocument: PdfDocument, data: ReportData) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Distribuzione per Partecipante", MARGIN, 80f, paint)

        val chartTop = 120f
        val chartBottom = 400f
        val barMaxHeight = chartBottom - chartTop
        val participantsToShow = data.participants.take(10)
        val barWidth = (PAGE_WIDTH - 2 * MARGIN) / (participantsToShow.size.coerceAtLeast(1) * 1.5f)
        
        participantsToShow.forEachIndexed { index, p ->
            val x = MARGIN + index * barWidth * 1.5f
            val maxMsg = data.participants.maxOfOrNull { it.totalMessages }?.toFloat() ?: 1f
            val totalHeight = (p.totalMessages / maxMsg) * barMaxHeight
            val toxicHeight = (p.toxicMessages / maxMsg) * barMaxHeight

            paint.color = COLOR_GREEN
            canvas.drawRect(x, chartBottom - totalHeight, x + barWidth, chartBottom, paint)
            paint.color = COLOR_RED
            canvas.drawRect(x, chartBottom - toxicHeight, x + barWidth, chartBottom, paint)

            paint.color = COLOR_TEXT_PRIMARY
            paint.textSize = 8f
            canvas.save()
            canvas.rotate(-45f, x, chartBottom + 10f)
            canvas.drawText(p.name, x, chartBottom + 15f, paint)
            canvas.restore()
        }

        var legY = 430f
        paint.textSize = 9f
        paint.color = COLOR_GREEN
        canvas.drawRect(MARGIN, legY, MARGIN + 10, legY + 10, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Volume messaggi totali", MARGIN + 15, legY + 9, paint)
        
        paint.color = COLOR_RED
        canvas.drawRect(MARGIN + 150, legY, MARGIN + 160, legY + 10, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Messaggi sopra soglia (tossici)", MARGIN + 165, legY + 9, paint)

        var yTable = 480f
        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Nome", MARGIN, yTable, paint)
        canvas.drawText("Tossici / Totali", MARGIN + 250f, yTable, paint)
        canvas.drawText("%", MARGIN + 400f, yTable, paint)
        yTable += 10f
        canvas.drawLine(MARGIN, yTable, PAGE_WIDTH - MARGIN, yTable, paint)
        yTable += 20f
        paint.isFakeBoldText = false
        
        data.participants.forEach { p ->
            if (yTable > PAGE_HEIGHT - 80f) return@forEach
            canvas.drawText(p.name, MARGIN, yTable, paint)
            canvas.drawText("${p.toxicMessages} / ${p.totalMessages}", MARGIN + 250f, yTable, paint)
            canvas.drawText("%.1f%%".format(p.toxicityPercentage), MARGIN + 400f, yTable, paint)
            yTable += 20f
        }

        drawFooter(canvas)
        pdfDocument.finishPage(page)
    }

    private fun drawWeeklyTrendPage(pdfDocument: PdfDocument, data: ReportData) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Trend Settimanale", MARGIN, 80f, paint)

        val chartRect = RectF(MARGIN, 150f, PAGE_WIDTH - MARGIN, 500f)
        drawWeeklyChart(canvas, chartRect, data.weeklyTrend)

        val legY = 550f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = COLOR_GRAY_BARS
        canvas.drawRect(MARGIN, legY, MARGIN + 20, legY + 12, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Volume settimanale (messaggi totali)", MARGIN + 25, legY + 10, paint)

        paint.color = COLOR_RED
        paint.strokeWidth = 2f
        canvas.drawLine(MARGIN + 250, legY + 6, MARGIN + 275, legY + 6, paint)
        canvas.drawCircle(MARGIN + 262.5f, legY + 6, 3f, paint.apply { style = Paint.Style.FILL })
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Percentuale tossicità (%)", MARGIN + 280, legY + 10, paint)

        drawFooter(canvas)
        pdfDocument.finishPage(page)
    }

    private fun drawTemporalDistributionPage(pdfDocument: PdfDocument, data: ReportData) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 4).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Distribuzione Temporale", MARGIN, 80f, paint)

        paint.textSize = 8f
        paint.color = COLOR_TEXT_SECONDARY
        val dayChartRect = RectF(MARGIN + 30f, 120f, PAGE_WIDTH - MARGIN, 350f)
        canvas.drawText("100%", MARGIN, dayChartRect.top + 5f, paint)
        canvas.drawText("50%", MARGIN, (dayChartRect.top + dayChartRect.bottom) / 2 + 5f, paint)
        canvas.drawText("0%", MARGIN, dayChartRect.bottom + 5f, paint)

        drawDailyDistribution(canvas, dayChartRect, data.dailyDistribution)

        var curY = 380f
        paint.textSize = 9f
        paint.color = COLOR_GREEN
        canvas.drawRect(MARGIN + 30f, curY, MARGIN + 40f, curY + 10f, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Messaggi Totali", MARGIN + 45f, curY + 9f, paint)
        
        paint.color = COLOR_RED
        canvas.drawRect(MARGIN + 150f, curY, MARGIN + 160f, curY + 10f, paint)
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Messaggi Tossici", MARGIN + 165f, curY + 9f, paint)

        curY += 40f
        paint.color = COLOR_TEXT_PRIMARY
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("Heatmap Giorno × Ora (% Tossicità)", MARGIN, curY, paint)
        
        val heatmapRect = RectF(MARGIN + 45f, curY + 30f, PAGE_WIDTH - MARGIN - 20f, 700f)
        drawHeatmap(canvas, heatmapRect, data.heatmap)

        val legY = 730f
        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("Intensità colore = quota messaggi tossici sul totale orario:", MARGIN, legY, paint)
        
        val gradientW = 150f
        for (i in 0..100 step 5) {
            val alpha = (i / 100f * 255).toInt()
            paint.color = Color.argb(alpha, 211, 47, 47)
            canvas.drawRect(MARGIN + (i/100f * gradientW), legY + 10, MARGIN + ((i+5)/100f * gradientW), legY + 25, paint)
        }
        paint.color = COLOR_TEXT_SECONDARY
        canvas.drawText("0%", MARGIN, legY + 40, paint)
        canvas.drawText("100%", MARGIN + gradientW - 25, legY + 40, paint)

        drawFooter(canvas)
        pdfDocument.finishPage(page)
    }

    private fun drawStatBox(canvas: Canvas, x: Float, y: Float, label: String, value: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 10f
        canvas.drawText(label, x, y, paint)
        paint.color = color
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(value, x, y + 30f, paint)
    }

    private fun drawWeeklyChart(canvas: Canvas, rect: RectF, trend: List<WeeklyTrendPoint>) {
        if (trend.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barSpacing = 1.3f
        val numPoints = trend.size
        val barWidth = rect.width() / (numPoints.coerceAtLeast(1) * barSpacing)
        val maxVolume = trend.maxOfOrNull { it.totalMessages }?.toFloat() ?: 1f

        // Diradamento etichette: mostra un'etichetta ogni X settimane se sono troppe
        val labelInterval = when {
            numPoints > 40 -> 8
            numPoints > 20 -> 4
            numPoints > 10 -> 2
            else -> 1
        }

        trend.forEachIndexed { i, point ->
            val x = rect.left + i * barWidth * barSpacing
            val h = (point.totalMessages / maxVolume) * rect.height()
            paint.color = COLOR_GRAY_BARS
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, rect.bottom - h, x + barWidth, rect.bottom, paint)
            
            if (i % labelInterval == 0) {
                paint.color = COLOR_TEXT_SECONDARY
                paint.textSize = 7f
                canvas.save()
                canvas.rotate(-45f, x, rect.bottom + 8f)
                canvas.drawText(point.weekLabel, x, rect.bottom + 12f, paint)
                canvas.restore()
            }
        }

        paint.color = COLOR_RED
        paint.strokeWidth = 1.5f
        paint.style = Paint.Style.STROKE
        val path = Path()
        trend.forEachIndexed { i, point ->
            val x = rect.left + i * barWidth * barSpacing + barWidth / 2
            val y = rect.bottom - (point.toxicPercentage / 100f).coerceIn(0f, 1f) * rect.height()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            
            // Disegna il punto solo se non ci sono troppi dati (per evitare "macchie")
            if (numPoints < 60) {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_RED; style = Paint.Style.FILL }
                canvas.drawCircle(x, y, if (numPoints > 30) 1.5f else 2.5f, dotPaint)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawDailyDistribution(canvas: Canvas, rect: RectF, daily: List<DayStats>) {
        if (daily.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barWidth = rect.width() / (daily.size.coerceAtLeast(1) * 1.5f)
        val maxVal = daily.maxOfOrNull { it.totalMessages }?.toFloat() ?: 1f

        daily.forEachIndexed { i, d ->
            val x = rect.left + i * barWidth * 1.5f
            val hTotal = (d.totalMessages / maxVal) * rect.height()
            val hToxic = (d.toxicMessages / maxVal) * rect.height()
            
            paint.color = COLOR_GREEN
            canvas.drawRect(x, rect.bottom - hTotal, x + barWidth, rect.bottom, paint)
            paint.color = COLOR_RED
            canvas.drawRect(x, rect.bottom - hToxic, x + barWidth, rect.bottom, paint)
            
            paint.color = COLOR_TEXT_SECONDARY
            paint.textSize = 10f
            canvas.drawText(d.dayName.take(3), x, rect.bottom + 15f, paint)
        }
    }

    private fun drawHeatmap(canvas: Canvas, rect: RectF, matrix: Array<FloatArray>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellW = rect.width() / 24f
        val cellH = rect.height() / 7f
        val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

        paint.color = Color.parseColor("#F5F5F5")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        for (d in 0..7) canvas.drawLine(rect.left, rect.top + d * cellH, rect.right, rect.top + d * cellH, paint)
        for (h in 0..24) canvas.drawLine(rect.left + h * cellW, rect.top, rect.left + h * cellW, rect.bottom, paint)

        for (d in 0..6) {
            paint.color = COLOR_TEXT_SECONDARY
            paint.textSize = 9f
            paint.style = Paint.Style.FILL
            canvas.drawText(days[d], rect.left - 35f, rect.top + d * cellH + cellH / 2 + 4f, paint)
            
            for (h in 0..23) {
                val toxicPct = if (d < matrix.size && h < matrix[d].size) matrix[d][h] else 0f
                if (toxicPct > 0) {
                    val alpha = (toxicPct / 100f * 255).toInt().coerceIn(0, 255)
                    paint.color = Color.argb(alpha, 211, 47, 47)
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(
                        rect.left + h * cellW + 0.5f,
                        rect.top + d * cellH + 0.5f,
                        rect.left + (h + 1) * cellW - 0.5f,
                        rect.top + (d + 1) * cellH - 0.5f,
                        paint
                    )
                }
            }
        }

        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 8f
        for (h in 0..23 step 4) {
            canvas.drawText("${h}h", rect.left + h * cellW, rect.bottom + 15f, paint)
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = COLOR_TEXT_SECONDARY
        paint.textSize = 9f
        val footer1 = "Analisi automatica generata dall’app."
        val footer2 = "Il report contiene esclusivamente dati aggregati."
        val w1 = paint.measureText(footer1)
        val w2 = paint.measureText(footer2)
        canvas.drawText(footer1, (PAGE_WIDTH - w1) / 2, PAGE_HEIGHT - 35f, paint)
        canvas.drawText(footer2, (PAGE_WIDTH - w2) / 2, PAGE_HEIGHT - 20f, paint)
    }

    private fun savePdf(context: Context, pdfDocument: PdfDocument, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
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
