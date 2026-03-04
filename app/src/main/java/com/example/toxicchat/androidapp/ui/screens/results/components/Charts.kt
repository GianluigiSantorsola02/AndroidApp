package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.HeatmapCell
import com.example.toxicchat.androidapp.domain.model.WeeklyPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.sqrt

// --- DISTRIBUZIONE PER GIORNO CON TOGGLE ---

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
        
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; tooltipText = null },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Tutti", style = MaterialTheme.typography.labelMedium) }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; tooltipText = null },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Sopra soglia", style = MaterialTheme.typography.labelMedium) }
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(currentData, selectedTab) {
                detectTapGestures { offset ->
                    val step = size.width / 7f
                    val index = (offset.x / step).toInt().coerceIn(0, 6)
                    val rawCount = if (selectedTab == 0) {
                        heatmapCells.filter { it.dayOfWeek == index + 1 }.sumOf { it.totalCount }
                    } else {
                        heatmapCells.filter { it.dayOfWeek == index + 1 }.sumOf { it.toxicCount }
                    }
                    tooltipText = "${ChartTheme.DAYS_OF_WEEK[index]}: ${(currentData[index] * 100).toInt()}% ($rawCount messaggi)"
                }
            }) {
                val barWidth = size.width / 14f
                val spacing = size.width / 14f
                val maxVal = (currentData.maxOrNull()?.coerceAtLeast(0.1) ?: 0.1).toFloat()
                
                currentData.forEachIndexed { i, value ->
                    val x = spacing / 2f + i * (barWidth + spacing)
                    drawRoundRect(Color(0xFFF5F5F5), Offset(x, 0f), Size(barWidth, size.height), CornerRadius(4.dp.toPx()))
                    val h = (value.toFloat() / maxVal) * size.height
                    drawRoundRect(currentColor, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            ChartTheme.DAYS_OF_WEEK.forEach { Text(it, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium) }
        }
        
        Spacer(Modifier.height(8.dp))
        tooltipText?.let { 
            Surface(color = currentColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = currentColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        } ?: Spacer(Modifier.height(20.dp))
    }
}

// --- TREND SETTIMANALE ---

private fun formatWeekId(weekId: String): String {
    return try {
        val parts = weekId.split("-W")
        val year = parts[0].toInt()
        val week = parts[1].toInt()
        val firstDay = LocalDate.of(year, 1, 4) // ISO standard: 4th Jan is always in Week 1
            .with(WeekFields.ISO.weekBasedYear(), year.toLong())
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(WeekFields.ISO.dayOfWeek(), 1L)
        val lastDay = firstDay.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yy")
        "${firstDay.format(fmt)} - ${lastDay.format(fmt)}"
    } catch (e: Exception) {
        weekId
    }
}

@Composable
fun TrendComboChart(series: List<WeeklyPoint>, modifier: Modifier = Modifier) {
    var tooltipText by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Trend settimanale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Volume messaggi e % sopra soglia", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(series) {
                detectTapGestures { offset ->
                    if (series.isEmpty()) return@detectTapGestures
                    val index = (offset.x / (size.width / series.size.toFloat())).toInt().coerceIn(0, series.size - 1)
                    val p = series[index]
                    tooltipText = "${formatWeekId(p.weekId)}: ${(p.toxicRate * 100).toInt()}% (${p.toxicMessages}/${p.totalMessages})"
                }
            }) {
                if (series.isEmpty()) return@Canvas
                val maxVol = series.maxOf { it.totalMessages }.coerceAtLeast(1).toFloat()
                val stepX = size.width / series.size.toFloat()
                
                // Volume in background (Grigio)
                series.forEachIndexed { i, p ->
                    val h = (p.totalMessages.toFloat() / maxVol) * size.height
                    drawRect(Color.LightGray.copy(alpha = 0.3f), Offset(i * stepX + 2f, size.height - h), Size(stepX - 4f, h))
                }
                
                // Linea Trend (ROSSO)
                val points = series.mapIndexed { i, p -> Offset(i * stepX + stepX / 2f, size.height - (p.toxicRate.toFloat().coerceIn(0f, 1f) * size.height)) }
                for (i in 0 until points.size - 1) {
                    drawLine(ChartTheme.TOXIC_MESSAGES_COLOR, points[i], points[i + 1], strokeWidth = 2.dp.toPx())
                    drawCircle(ChartTheme.TOXIC_MESSAGES_COLOR, 3.dp.toPx(), points[i])
                }
                if (points.isNotEmpty()) drawCircle(ChartTheme.TOXIC_MESSAGES_COLOR, 3.dp.toPx(), points.last())
            }
        }
        if (series.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatWeekId(series.first().weekId), fontSize = 9.sp, color = Color.Gray)
                Text(formatWeekId(series.last().weekId), fontSize = 9.sp, color = Color.Gray)
            }
        }
        tooltipText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ChartTheme.TOXIC_MESSAGES_COLOR) }
    }
}

// --- HEATMAP ---

@Composable
fun HeatmapGrid(cells: List<HeatmapCell>, modifier: Modifier = Modifier) {
    var tooltipText by remember { mutableStateOf<String?>(null) }
    val maxRate = (cells.maxOfOrNull { it.toxicRate } ?: 0.0) * 100
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Heatmap Giorno × Ora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Quota sopra soglia (%)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text("0%  · ● ⬤  ${"%.0f".format(maxRate)}%+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2.4f)) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(cells) {
                detectTapGestures { offset ->
                    val w = size.width / 24f
                    val h = size.height / 7f
                    val hour = (offset.x / w).toInt().coerceIn(0, 23)
                    val dow = (offset.y / h).toInt() + 1
                    cells.find { it.dayOfWeek == dow && it.hour == hour }?.let {
                        tooltipText = "Giorno $dow ore ${it.hour}:00 — ${(it.toxicRate * 100).toInt()}%"
                    }
                }
            }) {
                if (cells.isEmpty()) return@Canvas
                val w = size.width / 24f
                val h = size.height / 7f
                cells.forEach { c ->
                    val alpha = if (c.toxicRate <= 0.0) 0.05f else sqrt(c.toxicRate.toFloat()).coerceIn(0.1f, 1f)
                    drawRoundRect(
                        color = ChartTheme.TOXIC_MESSAGES_COLOR.copy(alpha = alpha), 
                        topLeft = Offset(c.hour * w + 1f, (c.dayOfWeek - 1) * h + 1f), 
                        size = Size(w - 2f, h - 2f), 
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", fontSize = 9.sp, color = Color.Gray); Text("12:00", fontSize = 9.sp, color = Color.Gray); Text("23:59", fontSize = 9.sp, color = Color.Gray)
        }
        tooltipText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ChartTheme.TOXIC_MESSAGES_COLOR) }
    }
}
