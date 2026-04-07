package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln1p

@Composable
fun EventTimelineChart(
    events: List<MessageEvent>,
    activeParticipants: List<String>,
    allParticipants: List<String>,
    isGroup: Boolean,
    groupTitle: String? = null,
    startMillis: Long,
    endMillis: Long,
    selectedDayMillis: Long? = null,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorSottoSoglia = Color(0xFFBDBDBD) 
    val colorTossico = Color(0xFFFFA000)    
    val colorAltamenteTossico = Color(0xFFD32F2F)

    val strokeSottoSoglia = Color(0xFF757575)
    val strokeTossico = Color(0xFFE65100)
    val strokeAltamenteTossico = Color(0xFF8E0000)

    val leftColumnWidth = 64.dp
    val rightColumnWidth = 64.dp

    val filteredActive = remember(activeParticipants) {
        activeParticipants.filter { it.isNotBlank() && !it.equals("Sistema", ignoreCase = true) }
    }

    val displayLanes = remember(filteredActive, allParticipants, isGroup) {
        filteredActive.map { speaker ->
            val recipient = if (!isGroup) {
                allParticipants.firstOrNull { !it.equals(speaker, ignoreCase = true) } ?: "Altro"
            } else {
                "Gruppo"
            }
            speaker to recipient
        }
    }

    val laneIndexMap = remember(filteredActive) {
        filteredActive.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    }

    val safeEndMillis = if (endMillis > startMillis) endMillis else startMillis + 86400000L * 7
    val timeSpan = (safeEndMillis - startMillis).coerceAtLeast(1L)

    // Helper per categorizzare la gravità
    fun getSeverityCategory(score: Double): Int = when {
        score < 0.50 -> 0 // Safe
        score < 0.80 -> 1 // Toxic
        else -> 2         // Critical
    }

    // CAMPIONAMENTO E DENSITÀ PER CATEGORIA
    val densityBins = 168 // 1 bin per ora
    val samplingBins = 56 // 1 bin ogni 3 ore per il campionamento dei safe
    
    // Mappa densità globale (calcolata su tutti gli eventi prima del filtraggio)
    val categoryDensityMap = remember(events, startMillis, timeSpan) {
        val counts = mutableMapOf<Triple<String, Int, Int>, Int>()
        events.forEach { event ->
            val progress = (event.timestampEpochMillis - startMillis).toFloat() / timeSpan
            val bin = (progress * densityBins).toInt().coerceIn(0, densityBins - 1)
            val cat = getSeverityCategory(event.toxScore ?: 0.0)
            val key = Triple(event.speakerRaw.trim().lowercase(), bin, cat)
            counts[key] = (counts[key] ?: 0) + 1
        }
        counts
    }

    val processedEvents = remember(events, startMillis, timeSpan) {
        val safeCounter = mutableMapOf<Pair<String, Int>, Int>()
        events.filter { event ->
            val score = event.toxScore ?: 0.0
            if (score >= 0.50) return@filter true // Tossici e Critici: 100% visibili
            
            val progress = (event.timestampEpochMillis - startMillis).toFloat() / timeSpan
            val bin = (progress * samplingBins).toInt().coerceIn(0, samplingBins - 1)
            val key = event.speakerRaw.trim().lowercase() to bin
            
            val currentSafeCount = safeCounter[key] ?: 0
            if (currentSafeCount < 1) { // 1 pallino rappresentativo per fascia oraria safe
                safeCounter[key] = currentSafeCount + 1
                true
            } else {
                false
            }
        }.sortedBy { it.toxScore ?: 0.0 } // Ordine crescente: i rossi (valori alti) sono gli ultimi e stanno sopra
    }

    val sendPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Send)
    val inboxPainter = rememberVectorPainter(Icons.Filled.Inbox)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Sotto soglia", colorSottoSoglia, strokeSottoSoglia)
                LegendItem("Tossico", colorTossico, strokeTossico)
                LegendItem("Critico", colorAltamenteTossico, strokeAltamenteTossico)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconLegendItem("Mittente", sendPainter)
                IconLegendItem("Destinatario", inboxPainter)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (filteredActive.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Nessun dato disponibile", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.width(leftColumnWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                filteredActive.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(startMillis, timeSpan) {
                        detectTapGestures { offset ->
                            val horizontalPadding = 16.dp.toPx()
                            val chartWidth = size.width - 2 * horizontalPadding
                            val tappedX = offset.x - horizontalPadding
                            if (tappedX in 0f..chartWidth) {
                                val dayIndex = (tappedX / chartWidth * 7).toInt().coerceIn(0, 6)
                                onDayClick(startMillis + dayIndex * 86400000L)
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val rowCount = filteredActive.size
                val rowHeight = height / rowCount.toFloat()
                val horizontalPadding = 16.dp.toPx()
                val chartWidth = width - 2 * horizontalPadding

                selectedDayMillis?.let { sel ->
                    val dayIdx = ((sel - startMillis) / 86400000L).toInt()
                    if (dayIdx in 0..6) {
                        drawRect(
                            color = Color(0xFF006064).copy(alpha = 0.05f),
                            topLeft = Offset(horizontalPadding + (dayIdx / 7f) * chartWidth, 0f),
                            size = Size(chartWidth / 7f, height)
                        )
                    }
                }

                for (i in 0..7) {
                    val x = horizontalPadding + (i / 7f) * chartWidth
                    drawLine(color = Color.LightGray.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, height), 1.dp.toPx())
                }

                for (i in 0 until rowCount) {
                    val y = i * rowHeight + rowHeight / 2f
                    drawLine(color = Color.LightGray.copy(alpha = 0.2f), Offset(horizontalPadding, y), Offset(width - horizontalPadding, y), 1.dp.toPx())
                    
                    val iconSizePx = 14.dp.toPx()
                    translate(left = horizontalPadding - iconSizePx - 4.dp.toPx(), top = y - iconSizePx / 2f) {
                        with(sendPainter) { draw(Size(iconSizePx, iconSizePx), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Gray.copy(alpha = 0.8f))) }
                    }
                    translate(left = width - horizontalPadding + 4.dp.toPx(), top = y - iconSizePx / 2f) {
                        with(inboxPainter) { draw(Size(iconSizePx, iconSizePx), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Gray.copy(alpha = 0.8f))) }
                    }
                }

                // Pass 1: Fill
                processedEvents.forEach { event ->
                    val speakerKey = event.speakerRaw.trim().lowercase()
                    val pIndex = laneIndexMap[speakerKey] ?: return@forEach
                    val progress = (event.timestampEpochMillis - startMillis).toFloat() / timeSpan
                    val x = horizontalPadding + progress * chartWidth
                    val y = pIndex * rowHeight + rowHeight / 2f
                    
                    val score = event.toxScore ?: 0.0
                    val baseColor = when {
                        score < 0.50 -> colorSottoSoglia
                        score < 0.80 -> colorTossico
                        else -> colorAltamenteTossico
                    }

                    // Recupero densità reale per categoria
                    val bin = (progress * densityBins).toInt().coerceIn(0, densityBins - 1)
                    val cat = getSeverityCategory(score)
                    val density = categoryDensityMap[Triple(speakerKey, bin, cat)] ?: 1
                    
                    // Alpha ridotto (minore trasparenza) per rossi e gialli
                    val alpha = when {
                        cat > 0 -> 0.95f // Meno trasparente per tossici e critici
                        density <= 1 -> 0.70f
                        density <= 4 -> 0.55f
                        else -> 0.40f
                    }

                    val baseRadius = if (score >= 0.50) 4.5.dp.toPx() else 3.2.dp.toPx()
                    val extraRadius = ln1p(density.toDouble()).toFloat() * 1.5.dp.toPx()
                    val radius = (baseRadius + extraRadius).coerceAtMost(10.dp.toPx())

                    drawCircle(
                        color = baseColor.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x.coerceIn(horizontalPadding, width - horizontalPadding), y)
                    )
                }

                // Pass 2: Stroke
                processedEvents.forEach { event ->
                    val speakerKey = event.speakerRaw.trim().lowercase()
                    val pIndex = laneIndexMap[speakerKey] ?: return@forEach
                    val progress = (event.timestampEpochMillis - startMillis).toFloat() / timeSpan
                    val x = horizontalPadding + progress * chartWidth
                    val y = pIndex * rowHeight + rowHeight / 2f
                    
                    val score = event.toxScore ?: 0.0
                    val strokeColor = when {
                        score < 0.50 -> strokeSottoSoglia
                        score < 0.80 -> strokeTossico
                        else -> strokeAltamenteTossico
                    }

                    val bin = (progress * densityBins).toInt().coerceIn(0, densityBins - 1)
                    val cat = getSeverityCategory(score)
                    val density = categoryDensityMap[Triple(speakerKey, bin, cat)] ?: 1
                    
                    val baseRadius = if (score >= 0.50) 4.5.dp.toPx() else 3.2.dp.toPx()
                    val extraRadius = ln1p(density.toDouble()).toFloat() * 1.5.dp.toPx()
                    val radius = (baseRadius + extraRadius).coerceAtMost(10.dp.toPx())

                    drawCircle(
                        color = strokeColor,
                        radius = radius,
                        center = Offset(x.coerceIn(horizontalPadding, width - horizontalPadding), y),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            Column(
                modifier = Modifier.width(rightColumnWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.End
            ) {
                displayLanes.forEach { (_, recipient) ->
                    Text(
                        text = recipient,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ITALY).withZone(ZoneId.systemDefault())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = leftColumnWidth, end = rightColumnWidth, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(16.dp)) 
            Row(modifier = Modifier.weight(1f)) {
                for (i in 0..6) {
                    val dayName = dayFormatter.format(Instant.ofEpochMilli(startMillis + i * 86400000L))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = dayName.replaceFirstChar { it.titlecase(Locale.ITALY) },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun IconLegendItem(label: String, painter: Painter) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = Color.Gray.copy(alpha = 0.8f)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, strokeColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
                .border(1.dp, strokeColor, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}
