package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.HeatmapCell
import com.example.toxicchat.androidapp.domain.model.WeeklyPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ln1p
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistributionChart(
    heatmapCells: List<HeatmapCell>,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Tutti, 1: Sopra soglia
    var tooltipText by remember { mutableStateOf<String?>(null) }

    val stats = remember(heatmapCells) {
        val totalByDay = LongArray(7) { 0L }
        val toxicByDay = LongArray(7) { 0L }

        heatmapCells.forEach {
            if (it.dayOfWeek in 1..7) {
                totalByDay[it.dayOfWeek - 1] += it.totalCount.toLong()
                toxicByDay[it.dayOfWeek - 1] += it.toxicCount.toLong()
            }
        }

        val sumTotal = totalByDay.sum().coerceAtLeast(1L)
        val sumToxic = toxicByDay.sum().coerceAtLeast(1L)

        Pair(
            totalByDay.map { it.toDouble() / sumTotal },
            toxicByDay.map { it.toDouble() / sumToxic }
        )
    }

    val currentData = if (selectedTab == 0) stats.first else stats.second
    val currentColor = if (selectedTab == 0) ChartTheme.ALL_MESSAGES_COLOR else ChartTheme.TOXIC_MESSAGES_COLOR
    val title = if (selectedTab == 0) "Distribuzione per giorno (tutti)" else "Distribuzione per giorno (tossici)"
    val caption = if (selectedTab == 0) "Quota messaggi totali (%) per giorno" else "Quota messaggi sopra soglia (%) per giorno"

    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentData, selectedTab) {
                        detectTapGestures { offset ->
                            val step = size.width / 7f
                            val index = (offset.x / step).toInt().coerceIn(0, 6)
                            val rawCount = if (selectedTab == 0) {
                                heatmapCells.filter { it.dayOfWeek == index + 1 }.sumOf { it.totalCount }
                            } else {
                                heatmapCells.filter { it.dayOfWeek == index + 1 }.sumOf { it.toxicCount }
                            }
                            tooltipText =
                                "${ChartTheme.DAYS_OF_WEEK[index]}: ${(currentData[index] * 100).toInt()}% ($rawCount messaggi)"
                        }
                    }
            ) {
                val barWidth = size.width / 14f
                val spacing = size.width / 14f
                val maxVal = (currentData.maxOrNull()?.coerceAtLeast(0.1) ?: 0.1).toFloat()

                currentData.forEachIndexed { i, value ->
                    val x = spacing / 2f + i * (barWidth + spacing)

                    drawRoundRect(
                        color = Color(0xFFF5F5F5),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    val h = (value.toFloat() / maxVal) * size.height

                    drawRoundRect(
                        color = currentColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ChartTheme.DAYS_OF_WEEK.forEach {
                Text(
                    it,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        tooltipText?.let {
            Surface(
                color = currentColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = currentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        } ?: Spacer(Modifier.height(20.dp))
    }
}

private fun formatPeriod(start: Long, end: Long, isMonthly: Boolean): String {
    val zone = ZoneId.systemDefault()
    return if (isMonthly) {
        Instant.ofEpochMilli(start)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALY))
            .replaceFirstChar { it.titlecase(Locale.ITALY) }
    } else {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ITALY).withZone(zone)
        val s = formatter.format(Instant.ofEpochMilli(start))
        val e = formatter.format(Instant.ofEpochMilli(end - 1000))
        "$s - $e"
    }
}

private fun formatVolumeCompact(value: Int): String {
    return when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0).replace(".", ",")
        value >= 1_000 -> "%.1fk".format(value / 1_000.0).replace(".", ",")
        else -> value.toString()
    }
}

private data class ChartPoint(
    val id: String,
    val label: String,
    val totalMessages: Int,
    val toxicMessages: Int,
    val toxicRate: Double,
    val startMillis: Long,
    val endMillis: Long
)

@Composable
fun TrendComboChart(
    series: List<WeeklyPoint>,
    modifier: Modifier = Modifier
) {
    val isMonthly = series.size > 20

    val displayPoints = remember(series, isMonthly) {
        if (!isMonthly) {
            series.map {
                ChartPoint(
                    id = it.weekId,
                    label = it.weekId,
                    totalMessages = it.totalMessages,
                    toxicMessages = it.toxicMessages,
                    toxicRate = it.toxicRate,
                    startMillis = it.startMillis,
                    endMillis = it.endMillisExclusive
                )
            }
        } else {
            series.groupBy {
                val dt = Instant.ofEpochMilli(it.startMillis).atZone(ZoneId.systemDefault())
                "${dt.year}-${dt.monthValue}"
            }.map { (key, weeks) ->
                val total = weeks.sumOf { it.totalMessages }
                val toxic = weeks.sumOf { it.toxicMessages }
                val start = weeks.minOf { it.startMillis }
                val end = weeks.maxOf { it.endMillisExclusive }
                val monthLabel = Instant.ofEpochMilli(start)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM", Locale.ITALY))
                    .replaceFirstChar { c -> c.lowercase(Locale.ITALY) }

                ChartPoint(
                    id = key,
                    label = monthLabel,
                    totalMessages = total,
                    toxicMessages = toxic,
                    toxicRate = if (total > 0) toxic.toDouble() / total else 0.0,
                    startMillis = start,
                    endMillis = end
                )
            }.sortedBy { it.startMillis }
        }
    }

    // Inizializzato a null per non avere nessuna colonna evidenziata all'inizio
    var selectedPoint by remember(displayPoints) {
        mutableStateOf<ChartPoint?>(null)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMonthly) "Andamento mensile" else "Andamento settimanale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isMonthly) {
                        "Messaggi totali e % di messaggi tossici"
                    } else {
                        "Messaggi totali e % di messaggi tossici"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendItem("Volume", Color.LightGray.copy(alpha = 0.6f))
                LegendItem("% tossici", ChartTheme.TOXIC_MESSAGES_COLOR)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayPoints) {
                        detectTapGestures { offset ->
                            if (displayPoints.isEmpty()) return@detectTapGestures
                            val stepX = size.width / displayPoints.size.toFloat()
                            val index = (offset.x / stepX).toInt().coerceIn(0, displayPoints.size - 1)
                            selectedPoint = displayPoints[index]
                        }
                    }
            ) {
                if (displayPoints.isEmpty()) return@Canvas

                val maxVol = displayPoints.maxOf { it.totalMessages }.coerceAtLeast(1)
                val logMaxVol = ln1p(maxVol.toDouble()).toFloat()

                val stepX = size.width / displayPoints.size.toFloat()
                val topPadding = 34.dp.toPx()
                val bottomAxisPadding = 22.dp.toPx()
                val effectiveHeight = size.height - topPadding - bottomAxisPadding

                // Barre volume
                displayPoints.forEachIndexed { i, p ->
                    val vol = p.totalMessages
                    val normalizedHeight = if (isMonthly) {
                        (ln1p(vol.toDouble()).toFloat() / logMaxVol).coerceIn(0f, 1f)
                    } else {
                        (vol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                    }

                    val h = normalizedHeight * effectiveHeight
                    val barWidth = (stepX * 0.62f).coerceAtLeast(6.dp.toPx())
                    val x = i * stepX + (stepX - barWidth) / 2f
                    val y = size.height - bottomAxisPadding - h

                    // Colore della colonna: se selezionata, diventa più scura (grigio scuro invece di chiaro)
                    val isSelected = selectedPoint?.id == p.id
                    val barColor = if (isSelected) Color.Gray.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.35f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }

                // Linea % tossici (sempre lineare)
                val points = displayPoints.mapIndexed { i, p ->
                    Offset(
                        x = i * stepX + stepX / 2f,
                        y = size.height - bottomAxisPadding - (p.toxicRate.toFloat().coerceIn(0f, 1f) * effectiveHeight)
                    )
                }

                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = ChartTheme.TOXIC_MESSAGES_COLOR,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = ChartTheme.TOXIC_MESSAGES_COLOR,
                        radius = 3.5.dp.toPx(),
                        center = points[i]
                    )
                }

                if (points.isNotEmpty()) {
                    drawCircle(
                        color = ChartTheme.TOXIC_MESSAGES_COLOR,
                        radius = 3.5.dp.toPx(),
                        center = points.last()
                    )
                }
            }

            // Etichette volume ancorate alle barre
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(bottom = 22.dp)
            ) {
                val maxVol = displayPoints.maxOfOrNull { it.totalMessages }?.coerceAtLeast(1) ?: 1
                val logMaxVol = ln1p(maxVol.toDouble()).toFloat()
                val effectiveHeightDp = 220.dp - 34.dp - 22.dp

                displayPoints.forEach { p ->
                    val normalizedHeight = if (isMonthly) {
                        (ln1p(p.totalMessages.toDouble()).toFloat() / logMaxVol).coerceIn(0f, 1f)
                    } else {
                        (p.totalMessages.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                    }

                    val barHeight = effectiveHeightDp * normalizedHeight

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = if (isMonthly) formatVolumeCompact(p.totalMessages) else p.totalMessages.toString(),
                            fontSize = 8.sp,
                            color = Color(0xFF5F6368),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = barHeight + 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                selectedPoint?.let { p ->
                    Text(
                        text = formatPeriod(p.startMillis, p.endMillis, isMonthly),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Messaggi tossici: ${p.toxicMessages} su ${p.totalMessages} (${(p.toxicRate * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.toxicRate > 0.15) ChartTheme.TOXIC_MESSAGES_COLOR else Color.Gray
                    )
                } ?: Text(
                    text = if (isMonthly) {
                        "Tocca una colonna per i dettagli del mese"
                    } else {
                        "Tocca una colonna per i dettagli della settimana"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlobalCriticalityHeatmap(
    cells: List<HeatmapCell>,
    onCellClick: (HeatmapCell) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val toxicColor = Color(0xFFD32F2F)
    val baseColor = Color(0xFFF5F5F5)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Livello di criticità: ", fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(10.dp).background(baseColor, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(10.dp).background(toxicColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(10.dp).background(toxicColor, RoundedCornerShape(2.dp)))
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(32.dp).height(160.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.height(20.dp),
                        textAlign = TextAlign.Start
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cells) {
                                detectTapGestures { offset ->
                                    val cellW = size.width / 24f
                                    val cellH = size.height / 7f
                                    val h = (offset.x / cellW).toInt().coerceIn(0, 23)
                                    val d = (offset.y / cellH).toInt().coerceIn(0, 6)
                                    val dayNum = d + 1
                                    val cell = cells.find { it.dayOfWeek == dayNum && it.hour == h }
                                        ?: HeatmapCell(dayNum, h, 0, 0, 0.0)
                                    onCellClick(cell)
                                }
                            }
                    ) {
                        val cellW = size.width / 24f
                        val cellH = size.height / 7f

                        for (d in 0..6) {
                            for (h in 0..23) {
                                drawRoundRect(
                                    color = baseColor,
                                    topLeft = Offset(h * cellW + 1.dp.toPx(), d * cellH + 1.dp.toPx()),
                                    size = Size(cellW - 2.dp.toPx(), cellH - 2.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }

                        cells.forEach { cell ->
                            if (cell.dayOfWeek in 1..7 && cell.hour in 0..23 && cell.toxicRate > 0) {
                                val alpha = sqrt(cell.toxicRate.toFloat()).coerceIn(0.15f, 1f)
                                drawRoundRect(
                                    color = toxicColor.copy(alpha = alpha),
                                    topLeft = Offset(
                                        cell.hour * cellW + 1.dp.toPx(),
                                        (cell.dayOfWeek - 1) * cellH + 1.dp.toPx()
                                    ),
                                    size = Size(cellW - 2.dp.toPx(), cellH - 2.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("00:00", fontSize = 9.sp, color = Color.Gray)
                    Text("12:00", fontSize = 9.sp, color = Color.Gray)
                    Text("23:59", fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun HeatmapGrid(
    cells: List<HeatmapCell>,
    onCellClick: (HeatmapCell) -> Unit = {},
    modifier: Modifier = Modifier
) {
    GlobalCriticalityHeatmap(
        cells = cells,
        onCellClick = onCellClick,
        modifier = modifier
    )
}