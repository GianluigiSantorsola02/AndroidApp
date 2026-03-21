package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    // Colori di severità
    val colorSottoSoglia = Color(0xFFBDBDBD) 
    val colorTossico = Color(0xFFFFA000)    
    val colorAltamenteTossico = Color(0xFFD32F2F)

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

    // Icone Material
    val sendPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Send)
    val inboxPainter = rememberVectorPainter(Icons.Filled.Inbox)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Legend + Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Legenda Tossicità
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Sotto soglia", colorSottoSoglia)
                LegendItem("Tossico", colorTossico)
                LegendItem("Critico", colorAltamenteTossico)
            }
            
            // Legenda Ruoli (Sostituisce il riquadro grigio "Destinatario: Gruppo")
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
            // COLONNA SINISTRA
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

            // CENTRO: Plot Timeline con Icone
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

                // Sfondo giorno selezionato
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

                // Linee verticali
                for (i in 0..7) {
                    val x = horizontalPadding + (i / 7f) * chartWidth
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(x, 0f), end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Disegno Righe con Icone Send e Inbox
                for (i in 0 until rowCount) {
                    val y = i * rowHeight + rowHeight / 2f
                    val lineStart = horizontalPadding
                    val lineEnd = width - horizontalPadding
                    
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        start = Offset(lineStart, y), end = Offset(lineEnd, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    val iconSizePx = 14.dp.toPx()
                    
                    // 1. Icona Send (Mittente) all'inizio
                    translate(left = lineStart - iconSizePx - 4.dp.toPx(), top = y - iconSizePx / 2f) {
                        with(sendPainter) {
                            draw(size = Size(iconSizePx, iconSizePx), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Gray.copy(alpha = 0.8f)))
                        }
                    }

                    // 2. Icona Inbox (Destinatario) alla fine
                    translate(left = lineEnd + 4.dp.toPx(), top = y - iconSizePx / 2f) {
                        with(inboxPainter) {
                            draw(size = Size(iconSizePx, iconSizePx), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Gray.copy(alpha = 0.8f)))
                        }
                    }
                }

                // Messaggi
                events.forEach { event ->
                    val pIndex = laneIndexMap[event.speakerRaw.trim().lowercase()] ?: return@forEach
                    val progress = (event.timestampEpochMillis - startMillis).toFloat() / timeSpan
                    val x = horizontalPadding + progress * chartWidth
                    val y = pIndex * rowHeight + rowHeight / 2f
                    
                    val score = event.toxScore ?: 0.0
                    val color = when {
                        score < 0.50 -> colorSottoSoglia
                        score < 0.80 -> colorTossico
                        else -> colorAltamenteTossico
                    }

                    drawCircle(
                        color = color,
                        radius = if (score >= 0.50) 4.5.dp.toPx() else 3.2.dp.toPx(),
                        center = Offset(x.coerceIn(horizontalPadding, width - horizontalPadding), y)
                    )
                }
            }

            // COLONNA DESTRA
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

        // X Axis Labels
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
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}
