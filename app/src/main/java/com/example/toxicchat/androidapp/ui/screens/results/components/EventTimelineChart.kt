package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    // Thresholds: tau=0.50, tau_high=0.80
    val colorSottoSoglia = Color(0xFFBDBDBD) // Gray
    val colorTossico = Color(0xFFFFA000)    // Amber
    val colorAltamenteTossico = Color(0xFFD32F2F) // Red

    // Larghezze ottimizzate per massimizzare lo spazio centrale (plot)
    val leftColumnWidth = 60.dp
    val rightColumnWidth = 32.dp

    // Filtriamo partecipanti validi
    val filteredActive = remember(activeParticipants) {
        activeParticipants.filter { it.isNotBlank() && !it.equals("Sistema", ignoreCase = true) }
    }

    // Logica per le corsie: [Mittente] -> [Destinatario]
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp) // Ridotto da 16 a 12 per guadagnare spazio orizzontale
    ) {
        // Legend + Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("Sotto soglia", colorSottoSoglia)
                LegendItem("Tossico", colorTossico)
                LegendItem("Critico", colorAltamenteTossico)
            }
            
            if (isGroup) {
                Surface(
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Destinatario: Gruppo", // Più corto
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Header Colonne con prevenzione wrap per "Destinatario"
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "Mittente",
                modifier = Modifier.width(leftColumnWidth),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            // Box che permette al testo Destinatario di stare su una riga e debordare a sinistra
            Box(modifier = Modifier.width(rightColumnWidth), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "Destinatario",
                    modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(Modifier.height(8.dp))

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

            // CENTRO: Plot Espanso
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(startMillis, timeSpan) {
                        detectTapGestures { offset ->
                            val tappedX = offset.x
                            val dayIndex = (tappedX / size.width * 7).toInt().coerceIn(0, 6)
                            onDayClick(startMillis + dayIndex * 86400000L)
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val rowCount = filteredActive.size
                val rowHeight = height / rowCount.toFloat()

                selectedDayMillis?.let { sel ->
                    val dayIdx = ((sel - startMillis) / 86400000L).toInt()
                    if (dayIdx in 0..6) {
                        drawRect(
                            color = Color(0xFF006064).copy(alpha = 0.05f),
                            topLeft = Offset((dayIdx / 7f) * width, 0f),
                            size = Size(width / 7f, height)
                        )
                    }
                }

                for (i in 0..7) {
                    val x = (i / 7f) * width
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(x, 0f), end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                for (i in 0 until rowCount) {
                    val y = i * rowHeight + rowHeight / 2f
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        start = Offset(0f, y), end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                events.forEach { event ->
                    val pIndex = laneIndexMap[event.speakerRaw.trim().lowercase()] ?: return@forEach
                    val x = ((event.timestampEpochMillis - startMillis).toFloat() / timeSpan) * width
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
                        center = Offset(x.coerceIn(0f, width), y)
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
                    if (isGroup) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Gruppo",
                            modifier = Modifier.size(16.dp),
                            tint = Color.LightGray
                        )
                    } else {
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
        }

        // X Axis Labels
        val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ITALY).withZone(ZoneId.systemDefault())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = leftColumnWidth, end = rightColumnWidth, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
