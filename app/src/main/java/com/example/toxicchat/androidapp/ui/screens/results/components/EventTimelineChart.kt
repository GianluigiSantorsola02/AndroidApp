package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    participants: List<String>,
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

    val normalizedParticipants = remember(participants) {
        participants.filter { it.isNotBlank() }.distinct()
    }

    val laneIndexMap = remember(normalizedParticipants) {
        normalizedParticipants.mapIndexed { index, name -> name.lowercase() to index }.toMap()
    }

    val safeEndMillis = if (endMillis > startMillis) endMillis else startMillis + 86400000L * 7
    val timeSpan = (safeEndMillis - startMillis).coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem("Sotto soglia", colorSottoSoglia)
            LegendItem("Tossico", colorTossico)
            LegendItem("Altamente tossico", colorAltamenteTossico)
        }

        Spacer(Modifier.height(20.dp))

        if (normalizedParticipants.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Nessun dato disponibile", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Y Axis (Participants)
            Column(
                modifier = Modifier.width(72.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                normalizedParticipants.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Canvas with Day Separators and Tap Detection
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(startMillis, timeSpan) {
                        detectTapGestures { offset ->
                            val tappedX = offset.x
                            val dayIndex = (tappedX / size.width * 7).toInt().coerceIn(0, 6)
                            val tappedDayStart = startMillis + dayIndex * 86400000L
                            onDayClick(tappedDayStart)
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val rowHeight = height / normalizedParticipants.size.toFloat()

                // Highlight selected day
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

                // 1. Draw Day Separators (Vertical)
                for (i in 0..7) {
                    val x = (i / 7f) * width
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 2. Draw Participant Lanes (Horizontal)
                normalizedParticipants.indices.forEach { i ->
                    val y = i * rowHeight + rowHeight / 2f
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 3. Draw Events
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
                        radius = if (score >= 0.50) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(x.coerceIn(0f, width), y)
                    )
                }
            }
        }

        // X Axis Labels (Days)
        val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ITALY).withZone(ZoneId.systemDefault())
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 72.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0..6) {
                val dayMillis = startMillis + i * 86400000L
                val dayName = dayFormatter.format(Instant.ofEpochMilli(dayMillis))
                Text(
                    text = dayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString() },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}
