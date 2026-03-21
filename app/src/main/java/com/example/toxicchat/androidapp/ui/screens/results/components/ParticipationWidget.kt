package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import com.example.toxicchat.androidapp.domain.model.SpeakerToxicityStat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Models ---

data class BucketData(
    val index: Int,
    val total: Int,
    val toxic: Int
)

data class ParticipantRowData(
    val speakerLabel: String,
    val totalCount: Int,
    val toxicCount: Int,
    val buckets: List<BucketData>,
    val globalPercentage: Int
)

data class ParticipationUIModel(
    val rows: List<ParticipantRowData>,
    val maxTotal: Int,
    val maxBucketTotal: Int,
    val isDaily: Boolean
)

enum class SelectedChartSource { ALL_MESSAGES, TOXIC_MESSAGES }

data class SelectedParticipantState(
    val name: String,
    val source: SelectedChartSource,
    val bucketFilter: Int? = null,
    val isDaily: Boolean = false
)

// --- Logic ---

object ParticipationDataProcessor {
    
    fun getBucketIndex(timestamp: Long, isDaily: Boolean): Int {
        val zdt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        return if (isDaily) {
            zdt.hour
        } else {
            // Monday = 1, Sunday = 7 -> map to 0-6
            zdt.dayOfWeek.value - 1
        }
    }

    fun buildUIModel(
        stats: List<SpeakerToxicityStat>,
        messages: List<MessageEvent>
    ): ParticipationUIModel {
        if (stats.isEmpty()) return ParticipationUIModel(emptyList(), 1, 1, false)

        val totalMessagesInPeriod = stats.sumOf { it.totalCount }.coerceAtLeast(1)
        val rangeStart = messages.minOfOrNull { it.timestampEpochMillis } ?: 0L
        val rangeEnd = messages.maxOfOrNull { it.timestampEpochMillis } ?: 0L
        val isDaily = (rangeEnd - rangeStart) <= 90_000_000L 

        val numBuckets = if (isDaily) 24 else 7
        val messagesBySpeaker = messages.groupBy { it.speakerRaw }

        val rows = stats.map { stat ->
            val speakerMsgs = messagesBySpeaker[stat.speakerLabel] ?: emptyList()
            val bucketCounts = IntArray(numBuckets) { 0 }
            val bucketToxicCounts = IntArray(numBuckets) { 0 }

            speakerMsgs.forEach { msg ->
                val idx = getBucketIndex(msg.timestampEpochMillis, isDaily)
                if (idx in 0 until numBuckets) {
                    bucketCounts[idx]++
                    if (msg.isToxic) bucketToxicCounts[idx]++
                }
            }

            ParticipantRowData(
                speakerLabel = stat.speakerLabel,
                totalCount = stat.totalCount,
                toxicCount = stat.toxicCount,
                globalPercentage = ((stat.totalCount.toFloat() / totalMessagesInPeriod) * 100).toInt(),
                buckets = List(numBuckets) { i ->
                    BucketData(i, bucketCounts[i], bucketToxicCounts[i])
                }
            )
        }.filter { it.totalCount > 0 }
         .sortedByDescending { it.totalCount }

        val maxTotal = rows.maxOfOrNull { it.totalCount } ?: 1
        val maxBucketTotal = rows.flatMap { it.buckets }.maxOfOrNull { it.total } ?: 1

        return ParticipationUIModel(rows, maxTotal, maxBucketTotal, isDaily)
    }
}

private fun getSeverityColor(toxicMessages: Int, maxTox: Double?): Color = when {
    toxicMessages <= 0 -> Color(0xFFBDBDBD)
    (maxTox ?: 0.0) >= 0.80 -> Color(0xFFD32F2F)
    (maxTox ?: 0.0) >= 0.50 -> Color(0xFFFFA000)
    else -> Color(0xFFBDBDBD)
}

// --- Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipationWidget(
    stats: List<SpeakerToxicityStat>,
    messages: List<MessageEvent> = emptyList(),
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiModel = remember(stats, messages) { ParticipationDataProcessor.buildUIModel(stats, messages) }
    var selectedState by remember { mutableStateOf<SelectedParticipantState?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Analisi Partecipazione",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                uiModel.rows.forEach { rowData ->
                    ParticipantBlock(
                        data = rowData,
                        maxTotal = uiModel.maxTotal,
                        maxBucketTotal = uiModel.maxBucketTotal,
                        isDaily = uiModel.isDaily,
                        onDetailClick = {
                            selectedState = SelectedParticipantState(rowData.speakerLabel, SelectedChartSource.ALL_MESSAGES, isDaily = uiModel.isDaily)
                        },
                        onBucketClick = { idx ->
                            selectedState = SelectedParticipantState(rowData.speakerLabel, SelectedChartSource.ALL_MESSAGES, idx, isDaily = uiModel.isDaily)
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Tocca un blocco o una cella per i dettagli filtrati",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (selectedState != null) {
        val stat = stats.find { it.speakerLabel == selectedState?.name } ?: return
        ModalBottomSheet(
            onDismissRequest = { selectedState = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = Modifier.statusBarsPadding(),
            containerColor = Color.White
        ) {
            ParticipantDetailBottomSheetContent(
                stat = stat,
                selectedState = selectedState!!,
                allMessages = messages,
                isGroup = isGroup,
                allParticipants = stats.map { it.speakerLabel },
                onClearBucketFilter = {
                    selectedState = selectedState?.copy(bucketFilter = null)
                }
            )
        }
    }
}

@Composable
private fun ParticipantBlock(
    data: ParticipantRowData,
    maxTotal: Int,
    maxBucketTotal: Int,
    isDaily: Boolean,
    onDetailClick: () -> Unit,
    onBucketClick: (Int) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Nome Partecipante
            Text(
                text = data.speakerLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // 2. Riga "Volume totale"
            Text(
                text = "Volume totale",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clickable { onDetailClick() }
                ) {
                    // Totale (Verde)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(data.totalCount.toFloat() / maxTotal)
                            .fillMaxHeight()
                            .background(ChartTheme.ALL_MESSAGES_COLOR)
                    )
                    // Overlay Critici (Rosso)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(data.toxicCount.toFloat() / maxTotal)
                            .fillMaxHeight()
                            .background(ChartTheme.TOXIC_MESSAGES_COLOR)
                    )
                }
                Text(
                    text = "${data.globalPercentage}%",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${data.totalCount} messaggi (${data.toxicCount} critici)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            // 3. Sezione "Distribuzione settimanale"
            Text(
                text = if (isDaily) "Distribuzione oraria (0-23)" else "Distribuzione settimanale",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            
            Column(modifier = Modifier.fillMaxWidth()) {
                // Etichette Giorni/Ore VICINE al grafico
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val labels = if (isDaily) listOf("00", "06", "12", "18", "23") 
                                 else listOf("Lu", "Ma", "Me", "Gi", "Ve", "Sa", "Do")
                    
                    labels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(Modifier.height(4.dp))

                // Mini Timeline Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    data.buckets.forEach { bucket ->
                        val totalHeight = (bucket.total.toFloat() / maxBucketTotal).coerceIn(0.05f, 1f)
                        val toxicHeight = (bucket.toxic.toFloat() / maxBucketTotal).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.05f))
                            .clickable { onBucketClick(bucket.index) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(totalHeight)
                                .background(ChartTheme.ALL_MESSAGES_COLOR.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(toxicHeight)
                                .background(ChartTheme.TOXIC_MESSAGES_COLOR)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ParticipantDetailBottomSheetContent(
    stat: SpeakerToxicityStat,
    selectedState: SelectedParticipantState,
    allMessages: List<MessageEvent>,
    isGroup: Boolean,
    allParticipants: List<String>,
    onClearBucketFilter: () -> Unit
) {
    var showOnlyToxic by remember { mutableStateOf(selectedState.source == SelectedChartSource.TOXIC_MESSAGES) }
    val participantMessages = remember(allMessages, stat.speakerLabel) {
        allMessages.filter { it.speakerRaw == stat.speakerLabel }
    }
    
    val filteredMessages = remember(participantMessages, showOnlyToxic, selectedState.bucketFilter) {
        var list = participantMessages
        if (showOnlyToxic) {
            list = list.filter { it.isToxic }
        }
        if (selectedState.bucketFilter != null) {
            list = list.filter { msg ->
                ParticipationDataProcessor.getBucketIndex(msg.timestampEpochMillis, selectedState.isDaily) == selectedState.bucketFilter
            }
        }
        list
    }

    val maxToxScore = remember(participantMessages) {
        participantMessages.maxOfOrNull { it.toxScore ?: 0.0 } ?: 0.0
    }

    val dateTimeFormatter = remember {
        DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.ITALY)
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Text(text = stat.speakerLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        selectedState.bucketFilter?.let { filter ->
            val label = if (selectedState.isDaily) "Ore $filter:00" else listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")[filter]
            Spacer(Modifier.height(8.dp))
            SuggestionChip(
                onClick = onClearBucketFilter,
                label = { Text(label) },
                icon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val critColor = getSeverityColor(stat.toxicCount, maxToxScore)
            DetailPillVertical(label = "Messaggi", value = stat.totalCount.toString(), color = Color.Gray)
            DetailPillVertical(label = "Critici", value = stat.toxicCount.toString(), color = critColor)
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Solo messaggi critici", style = MaterialTheme.typography.bodyLarge, color = Color.Gray, modifier = Modifier.weight(1f))
            Switch(checked = showOnlyToxic, onCheckedChange = { showOnlyToxic = it }, modifier = Modifier.scale(0.85f))
        }

        Spacer(Modifier.height(16.dp))
        if (filteredMessages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nessun messaggio trovato", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(filteredMessages, key = { it.id }) { msg ->
                    ParticipantMessageItem(msg, dateTimeFormatter, isGroup, allParticipants)
                }
            }
        }
    }
}

@Composable
private fun DetailPillVertical(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
private fun ParticipantMessageItem(
    msg: MessageEvent,
    formatter: DateTimeFormatter,
    isGroup: Boolean,
    allParticipants: List<String>
) {
    val time = formatter.format(Instant.ofEpochMilli(msg.timestampEpochMillis))
    val itemSeverityColor = getSeverityColor(if (msg.isToxic) 1 else 0, msg.toxScore)

    Card(
        colors = CardDefaults.cardColors(containerColor = if (msg.isToxic) itemSeverityColor.copy(alpha = 0.04f) else Color(0xFFF8F8F8)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.speakerRaw, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (msg.isToxic) itemSeverityColor else Color(0xFF006064), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(horizontal = 6.dp).size(14.dp), tint = Color.Gray)
                val target = if (isGroup) "Gruppo" else allParticipants.firstOrNull { it != msg.speakerRaw } ?: "Altro"
                Text(target, style = MaterialTheme.typography.labelLarge, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(Modifier.height(6.dp))
            Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            if (msg.isToxic) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = itemSeverityColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Tossicità: %.2f".format(msg.toxScore ?: 0.0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = itemSeverityColor)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ParticipationWidgetPreview() {
    val stats = listOf(
        SpeakerToxicityStat("Alice", "Alice", 100, 10, 0.1),
        SpeakerToxicityStat("Bob", "Bob", 150, 50, 0.33)
    )
    val now = System.currentTimeMillis()
    val messages = listOf(
        MessageEvent(1, now, "Alice", "Alice", "Hello", 0.1, false),
        MessageEvent(2, now + 3600000, "Bob", "Bob", "Toxic message", 0.9, true)
    )
    MaterialTheme {
        ParticipationWidget(stats = stats, messages = messages)
    }
}
