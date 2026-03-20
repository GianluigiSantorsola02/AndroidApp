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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import com.example.toxicchat.androidapp.domain.model.SpeakerToxicityStat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// --- Models ---

data class BarItem(
    val name: String,
    val color: Color,
    val count: Int,
    val total: Int,
    val percentage: Float,
    val isOthers: Boolean = false
)

data class ParticipationUIModel(
    val messageShare: List<BarItem>,
    val toxicShare: List<BarItem>,
    val rawStats: List<SpeakerToxicityStat>
)

enum class SelectedChartSource { ALL_MESSAGES, TOXIC_MESSAGES }

data class SelectedParticipantState(
    val name: String,
    val source: SelectedChartSource
)

// --- Logic ---

object ParticipationDataProcessor {
    private const val TOP_N = 3

    private val participantPalette = listOf(
        Color(0xFF42A5F5), // Blue
        Color(0xFFFFA726), // Orange
        Color(0xFFAB47BC), // Purple
        Color(0xFF26A69A), // Teal
    )
    private val othersColor = Color.Gray

    fun buildUIModel(stats: List<SpeakerToxicityStat>): ParticipationUIModel {
        if (stats.isEmpty()) return ParticipationUIModel(emptyList(), emptyList(), emptyList())

        val totalMessages = stats.sumOf { it.totalCount }
        val totalToxicMessages = stats.sumOf { it.toxicCount }

        val colorMap = stats.associate {
            it.speakerLabel to generateColorForParticipant(it.speakerLabel)
        }

        val messageShare = buildTopNPlusOthers(
            stats = stats,
            total = totalMessages,
            countSelector = { it.totalCount },
            colorMap = colorMap
        )

        val toxicShare = buildTopNPlusOthers(
            stats = stats,
            total = totalToxicMessages,
            countSelector = { it.toxicCount },
            colorMap = colorMap
        )

        return ParticipationUIModel(messageShare, toxicShare, stats)
    }

    private fun buildTopNPlusOthers(
        stats: List<SpeakerToxicityStat>,
        total: Int,
        countSelector: (SpeakerToxicityStat) -> Int,
        colorMap: Map<String, Color>
    ): List<BarItem> {
        if (total == 0) return emptyList()

        val activeStats = stats.filter { countSelector(it) > 0 }
        
        if (activeStats.isEmpty()) return emptyList()

        if (activeStats.size <= TOP_N + 1) {
            return activeStats
                .sortedByDescending(countSelector)
                .map { stat ->
                    val count = countSelector(stat)
                    BarItem(
                        name = stat.speakerLabel,
                        color = colorMap[stat.speakerLabel] ?: othersColor,
                        count = count,
                        total = total,
                        percentage = (count.toFloat() / total) * 100f
                    )
                }
        }

        val sortedStats = activeStats.sortedByDescending(countSelector)
        val topN = sortedStats.take(TOP_N)
        val remaining = sortedStats.drop(TOP_N)

        val barItems = topN.map { stat ->
            val count = countSelector(stat)
            BarItem(
                name = stat.speakerLabel,
                color = colorMap[stat.speakerLabel] ?: othersColor,
                count = count,
                total = total,
                percentage = (count.toFloat() / total) * 100f
            )
        }.toMutableList()

        if (remaining.isNotEmpty()) {
            val othersCount = remaining.sumOf(countSelector)
            if (othersCount > 0) {
                barItems.add(
                    BarItem(
                        name = "Altri",
                        color = othersColor,
                        count = othersCount,
                        total = total,
                        percentage = (othersCount.toFloat() / total) * 100f,
                        isOthers = true
                    )
                )
            }
        }

        return barItems
    }

    private fun generateColorForParticipant(name: String): Color {
        val hash = name.hashCode()
        return participantPalette[abs(hash) % participantPalette.size]
    }
}

// Helper per i colori di severità (identico a DynamicsTab.kt)
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
    val uiModel = remember(stats) { ParticipationDataProcessor.buildUIModel(stats) }
    var selectedParticipantState by remember { mutableStateOf<SelectedParticipantState?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedStat = uiModel.rawStats.find { it.speakerLabel == selectedParticipantState?.name }

    val allParticipants = remember(stats) { stats.map { it.speakerLabel } }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Partecipazione",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ParticipationSection(
                    title = "Percentuale messaggi inviati",
                    barItems = uiModel.messageShare,
                    accentColor = ChartTheme.ALL_MESSAGES_COLOR,
                    onBarClick = { item -> 
                        if (!item.isOthers) {
                            selectedParticipantState = SelectedParticipantState(item.name, SelectedChartSource.ALL_MESSAGES)
                        }
                    }
                )

                ParticipationSection(
                    title = "Percentuale messaggi critici",
                    subtitle = "Percentuali calcolate solo sui messaggi critici",
                    barItems = uiModel.toxicShare,
                    accentColor = ChartTheme.TOXIC_MESSAGES_COLOR,
                    onBarClick = { item -> 
                        if (!item.isOthers) {
                            selectedParticipantState = SelectedParticipantState(item.name, SelectedChartSource.TOXIC_MESSAGES)
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Tocca sulla colonna o sul nome per mostrare i dettagli del partecipante",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (selectedStat != null && selectedParticipantState != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedParticipantState = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            // Aggiunto statusBarsPadding per non coprire notch/barra stato
            modifier = Modifier.statusBarsPadding(),
            containerColor = Color.White
        ) {
            ParticipantDetailBottomSheetContent(
                stat = selectedStat,
                initialSource = selectedParticipantState!!.source,
                allMessages = messages,
                isGroup = isGroup,
                allParticipants = allParticipants
            )
        }
    }
}

@Composable
private fun ParticipantDetailBottomSheetContent(
    stat: SpeakerToxicityStat,
    initialSource: SelectedChartSource,
    allMessages: List<MessageEvent>,
    isGroup: Boolean,
    allParticipants: List<String>
) {
    var showOnlyToxic by remember { mutableStateOf(initialSource == SelectedChartSource.TOXIC_MESSAGES) }
    
    val participantMessages = remember(allMessages, stat.speakerLabel) {
        allMessages.filter { it.speakerRaw == stat.speakerLabel }
    }
    
    val filteredMessages = if (showOnlyToxic) {
        participantMessages.filter { it.isToxic }
    } else {
        participantMessages
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
            // Limitiamo l'altezza all'85% per garantire che resti un bottom sheet
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        // Header
        Text(
            text = stat.speakerLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (showOnlyToxic) "Visualizzazione dei soli messaggi critici inviati nel periodo analizzato."
                   else "Visualizzazione dei messaggi inviati nel periodo analizzato.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(20.dp))

        // KPI Pills (Style from DynamicsTab DetailPills)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val critColor = getSeverityColor(stat.toxicCount, maxToxScore)
            DetailPillInternal("Messaggi", stat.totalCount.toString(), Color.Gray)
            DetailPillInternal("Critici", stat.toxicCount.toString(), critColor)
            DetailPillInternal("Tossicità max", "%.2f".format(maxToxScore), critColor)
        }

        Spacer(Modifier.height(32.dp))

        // Toggle Section - Matching image style
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Solo messaggi critici",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = showOnlyToxic,
                onCheckedChange = { showOnlyToxic = it },
                modifier = Modifier.scale(0.8f).padding(start = 8.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF006064)
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Messages List
        if (filteredMessages.isEmpty()) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (showOnlyToxic) "Nessun messaggio critico trovato per questo partecipante." 
                           else "Nessun messaggio disponibile per questo partecipante.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                // weight(1f, fill = false) evita il vuoto se ci sono pochi elementi
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    ParticipantMessageItem(msg, dateTimeFormatter, isGroup, allParticipants)
                }
            }
        }
    }
}

@Composable
private fun DetailPillInternal(label: String, value: String, color: Color) {
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
private fun ParticipantMessageItem(
    msg: MessageEvent,
    formatter: DateTimeFormatter,
    isGroup: Boolean,
    allParticipants: List<String>
) {
    val timeStr = formatter.format(Instant.ofEpochMilli(msg.timestampEpochMillis))
    val itemSeverityColor = getSeverityColor(if (msg.isToxic) 1 else 0, msg.toxScore)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (msg.isToxic) itemSeverityColor.copy(alpha = 0.04f) else Color(0xFFF9F9F9)
        ),
        border = if (msg.isToxic) BorderStroke(1.dp, itemSeverityColor.copy(alpha = 0.1f)) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.speakerRaw,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (msg.isToxic) itemSeverityColor else Color(0xFF006064),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF006064).copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.width(8.dp))

                    if (isGroup) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Gruppo",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Gray
                        )
                    } else {
                        val recipient = allParticipants.firstOrNull { !it.equals(msg.speakerRaw, ignoreCase = true) } ?: "Altro"
                        Text(
                            text = recipient,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )

            if (msg.isToxic) {
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
                            text = "Tossicità: %.2f".format(msg.toxScore ?: 0.0),
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
private fun ParticipationSection(
    title: String,
    subtitle: String? = null,
    barItems: List<BarItem>,
    accentColor: Color,
    onBarClick: (BarItem) -> Unit
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = accentColor)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(16.dp))

        if (barItems.isEmpty()) {
            Text(
                text = "Nessun dato rilevante nel periodo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                barItems.forEach { item ->
                    BarItemView(
                        item = item,
                        accentColor = accentColor,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !item.isOthers) { onBarClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BarItemView(item: BarItem, accentColor: Color, modifier: Modifier = Modifier) {
    val barHeight = (item.percentage * 1.2f).coerceIn(4f, 100f)

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "${item.percentage.roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (item.isOthers) Color.Gray else accentColor
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(barHeight.dp)
                .width(48.dp) // Risolto: larghezza fissa per evitare colonne sproporzionate
                .clip(RoundedCornerShape(4.dp))
                .background(if (item.isOthers) Color.LightGray else accentColor)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${item.count}/${item.total}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Float.roundToInt() = (this + 0.5f).toInt()
