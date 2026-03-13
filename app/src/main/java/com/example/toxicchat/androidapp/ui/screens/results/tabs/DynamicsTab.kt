package com.example.toxicchat.androidapp.ui.screens.results.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.domain.model.DayStat
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import com.example.toxicchat.androidapp.domain.model.WeeklyPoint
import com.example.toxicchat.androidapp.ui.screens.results.components.EventTimelineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun getSeverityColor(toxicMessages: Int, maxTox: Double?): Color = when {
    toxicMessages <= 0 -> Color(0xFFBDBDBD)
    (maxTox ?: 0.0) >= 0.80 -> Color(0xFFD32F2F)
    (maxTox ?: 0.0) >= 0.50 -> Color(0xFFFFA000)
    else -> Color(0xFFBDBDBD)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicsTab(
    result: AnalysisResult,
    selectedWeek: WeeklyPoint?,
    selectedWeekEvents: List<MessageEvent>,
    selectedWeekDayStats: List<DayStat>,
    selectedDayStartMillis: Long?,
    onSelectWeek: (WeeklyPoint) -> Unit,
    onSelectDay: (Long?) -> Unit
) {
    val scrollState = rememberScrollState()
    val italianLocale = Locale.ITALY
    var showWeekPicker by remember { mutableStateOf(false) }

    val fullDateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE dd MMMM", italianLocale)
            .withZone(ZoneId.systemDefault())
    }
    val rangeFormatter = remember {
        DateTimeFormatter.ofPattern("dd/MM", italianLocale)
            .withZone(ZoneId.systemDefault())
    }

    val currentIndex = result.weeklySeries.indexOfFirst { it.weekId == selectedWeek?.weekId }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex != -1 && currentIndex < result.weeklySeries.size - 1

    val headerSeverityColor = getSeverityColor(
        selectedWeek?.toxicMessages ?: 0,
        selectedWeek?.maxToxScore
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Dettaglio Conversazione",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Esplora l'andamento nel tempo e i singoli eventi",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (hasPrev) onSelectWeek(result.weeklySeries[currentIndex - 1]) },
                        enabled = hasPrev
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Precedente",
                            tint = if (hasPrev) Color.DarkGray else Color.LightGray
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showWeekPicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(headerSeverityColor, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        selectedWeek?.let {
                            val startStr = rangeFormatter.format(Instant.ofEpochMilli(it.startMillis))
                            val endStr = rangeFormatter.format(Instant.ofEpochMilli(it.endMillisExclusive - 1000))
                            Text(
                                text = "$startStr - $endStr",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                    }

                    IconButton(
                        onClick = { if (hasNext) onSelectWeek(result.weeklySeries[currentIndex + 1]) },
                        enabled = hasNext
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Successiva",
                            tint = if (hasNext) Color.DarkGray else Color.LightGray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (selectedWeek != null) {
                    val weekParticipants = remember(selectedWeekEvents) {
                        selectedWeekEvents
                            .map { it.speakerRaw.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                    }

                    EventTimelineChart(
                        events = selectedWeekEvents,
                        participants = weekParticipants,
                        startMillis = selectedWeek.startMillis,
                        endMillis = selectedWeek.endMillisExclusive,
                        selectedDayMillis = selectedDayStartMillis,
                        onDayClick = { onSelectDay(it) },
                        modifier = Modifier.height(280.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tocca una colonna del grafico per aprire il dettaglio del giorno",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        val criticalDays = selectedWeekDayStats
            .filter { it.toxicMessages > 0 }
            .sortedByDescending { it.toxicMessages }
            .take(3)

        if (criticalDays.isNotEmpty()) {
            Text(
                "Picchi di Criticità",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            criticalDays.forEach { day ->
                CriticalDayRow(day, fullDateFormatter) { onSelectDay(day.dayStartMillis) }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.EventNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Usa la timeline per identificare i momenti di maggiore attività o tensione tra i partecipanti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    if (showWeekPicker) {
        ModalBottomSheet(
            onDismissRequest = { showWeekPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
            ) {
                WeekPickerContent(
                    weeks = result.weeklySeries,
                    selectedWeekId = selectedWeek?.weekId,
                    rangeFormatter = rangeFormatter,
                    onSelect = {
                        onSelectWeek(it)
                        showWeekPicker = false
                    },
                    getSeverityColor = { toxicMessages, maxTox ->
                        getSeverityColor(toxicMessages, maxTox)
                    }
                )
            }
        }
    }

    if (selectedDayStartMillis != null) {
        val dayEvents = remember(selectedDayStartMillis, selectedWeekEvents) {
            val start = selectedDayStartMillis
            val end = start + 86_400_000L
            selectedWeekEvents.filter { it.timestampEpochMillis in start until end }
        }

        ModalBottomSheet(
            onDismissRequest = { onSelectDay(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
            ) {
                DailyBurstDetail(
                    dayStartMillis = selectedDayStartMillis,
                    events = dayEvents,
                    fullDateFormatter = fullDateFormatter
                )
            }
        }
    }
}

@Composable
fun WeekPickerContent(
    weeks: List<WeeklyPoint>,
    selectedWeekId: String?,
    rangeFormatter: DateTimeFormatter,
    onSelect: (WeeklyPoint) -> Unit,
    getSeverityColor: (Int, Double?) -> Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Seleziona Periodo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(weeks) { week ->
                val isSelected = week.weekId == selectedWeekId
                val startStr = rangeFormatter.format(Instant.ofEpochMilli(week.startMillis))
                val endStr = rangeFormatter.format(Instant.ofEpochMilli(week.endMillisExclusive - 1000))
                val dotColor = getSeverityColor(week.toxicMessages, week.maxToxScore)

                Surface(
                    onClick = { onSelect(week) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF006064).copy(alpha = 0.1f) else Color.Transparent,
                    border = if (isSelected) {
                        BorderStroke(1.dp, Color(0xFF006064))
                    } else {
                        BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(dotColor, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = "$startStr - $endStr",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF006064) else Color.DarkGray
                        )

                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF006064))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyBurstDetail(
    dayStartMillis: Long,
    events: List<MessageEvent>,
    fullDateFormatter: DateTimeFormatter
) {
    var toxicOnly by remember { mutableStateOf(false) }
    val displayEvents = if (toxicOnly) events.filter { it.isToxic } else events
    val hourFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY)
            .withZone(ZoneId.systemDefault())
    }

    // Calcola la severità massima del giorno per i colori del riepilogo
    val toxicCount = events.count { it.isToxic }
    val peak = events.maxOfOrNull { it.toxScore ?: 0.0 } ?: 0.0
    val daySeverityColor = getSeverityColor(toxicCount, peak)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        val dateText = fullDateFormatter.format(Instant.ofEpochMilli(dayStartMillis))
        Text(
            text = dateText.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString()
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailPill("Messaggi", events.size.toString(), Color.Gray)
            DetailPill("Critici", toxicCount.toString(), daySeverityColor)
            DetailPill("Tossicità max", "%.2f".format(peak), daySeverityColor)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = if (toxicOnly) {
                "Distribuzione Oraria (Messaggi Critici)"
            } else {
                "Distribuzione Oraria (Tutti i Messaggi)"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        HourlyTimeline(events = displayEvents, isToxicMode = toxicOnly)

        Spacer(Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (toxicOnly) "Solo messaggi critici" else "Messaggi del giorno",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("Solo critici", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = toxicOnly,
                onCheckedChange = { toxicOnly = it },
                modifier = Modifier
                    .scale(0.8f)
                    .padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(displayEvents) { event ->
                MessageItem(event, hourFormatter)
            }
            if (displayEvents.isEmpty()) {
                item {
                    Text(
                        "Nessun messaggio rilevato per i filtri selezionati.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyTimeline(
    events: List<MessageEvent>, 
    isToxicMode: Boolean
) {
    val bucketCount = 12
    val buckets = remember(events) {
        val counts = IntArray(bucketCount) { 0 }
        val maxTox = DoubleArray(bucketCount) { 0.0 }
        
        events.forEach {
            val hour = Instant.ofEpochMilli(it.timestampEpochMillis)
                .atZone(ZoneId.systemDefault())
                .hour
            val bucketIndex = (hour / 2).coerceIn(0, bucketCount - 1)
            counts[bucketIndex]++
            
            val score = it.toxScore ?: 0.0
            if (score > maxTox[bucketIndex]) {
                maxTox[bucketIndex] = score
            }
        }
        counts to maxTox
    }
    
    val counts = buckets.first
    val maxTox = buckets.second
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                counts.forEachIndexed { index, count ->
                    val heightPct = count.toFloat() / maxCount
                    val bucketMaxScore = maxTox[index]
                    
                    // Colore specifico per questa colonna basato sulla severità massima rilevata nel bucket
                    val barColor = if (isToxicMode) {
                        getSeverityColor(count, bucketMaxScore)
                    } else {
                        Color(0xFF006064)
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                fontSize = 9.sp,
                                color = barColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightPct.coerceAtLeast(0.05f))
                                .background(
                                    if (count > 0) barColor.copy(alpha = 0.7f)
                                    else Color.LightGray.copy(alpha = 0.3f),
                                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:00", fontSize = 9.sp, color = Color.Gray)
                Text("12:00", fontSize = 9.sp, color = Color.Gray)
                Text("23:59", fontSize = 9.sp, color = Color.Gray)
            }

            if (events.isNotEmpty()) {
                val peakBucket = counts.indices.maxByOrNull { counts[it] } ?: 0
                val peakStart = peakBucket * 2
                val peakEnd = peakStart + 2
                val count = counts[peakBucket]
                val msgLabel = if (count == 1) "messaggio" else "messaggi"

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Fascia più attiva: %02d:00–%02d:00 · $count $msgLabel".format(peakStart, peakEnd),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MessageItem(event: MessageEvent, formatter: DateTimeFormatter) {
    val timeStr = formatter.format(Instant.ofEpochMilli(event.timestampEpochMillis))
    
    // Colore specifico per il livello di tossicità di questo messaggio
    val itemSeverityColor = getSeverityColor(if (event.isToxic) 1 else 0, event.toxScore)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (event.isToxic) itemSeverityColor.copy(alpha = 0.04f) else Color(0xFFF9F9F9)
        ),
        border = if (event.isToxic) BorderStroke(1.dp, itemSeverityColor.copy(alpha = 0.1f)) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.speakerRaw,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (event.isToxic) itemSeverityColor else Color(0xFF006064)
                )
                Spacer(Modifier.weight(1f))
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )

            if (event.isToxic) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = itemSeverityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = itemSeverityColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Tossicità: %.2f".format(event.toxScore ?: 0.0),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = itemSeverityColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailPill(label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CriticalDayRow(
    stat: DayStat,
    formatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val dateStr = formatter.format(Instant.ofEpochMilli(stat.dayStartMillis)).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString()
    }

    // Determina il colore dell'icona in base alla severità massima del giorno (usando l'helper esistente)
    val severityColor = getSeverityColor(stat.toxicMessages, stat.peakToxicity)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(severityColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${stat.toxicMessages} messaggi sopra soglia",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}