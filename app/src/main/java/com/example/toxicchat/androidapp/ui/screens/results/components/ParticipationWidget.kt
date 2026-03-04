package com.example.toxicchat.androidapp.ui.screens.results.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.SpeakerToxicityStat
import kotlin.math.abs
import kotlin.math.roundToInt

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

// --- Logic ---

object ParticipationDataProcessor {
    private const val TOP_N = 3

    // Colori fissi per la coerenza delle barre (IO/ALTRO o nomi)
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

        // Prendiamo solo chi ha almeno un messaggio nel conteggio specifico
        val activeStats = stats.filter { countSelector(it) > 0 }
        
        if (activeStats.isEmpty()) return emptyList()

        // Se abbiamo pochi partecipanti, mostriamoli tutti senza raggruppare in "Altri"
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

// --- Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipationWidget(
    stats: List<SpeakerToxicityStat>,
    modifier: Modifier = Modifier
) {
    val uiModel = remember(stats) { ParticipationDataProcessor.buildUIModel(stats) }
    var selectedSpeakerName by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val selectedStat = uiModel.rawStats.find { it.speakerLabel == selectedSpeakerName }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Partecipazione per partecipante",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ParticipationSection(
                    title = "Quota messaggi nel periodo",
                    barItems = uiModel.messageShare,
                    accentColor = ChartTheme.ALL_MESSAGES_COLOR,
                    onBarClick = { item -> if (!item.isOthers) selectedSpeakerName = item.name }
                )

                ParticipationSection(
                    title = "Quota messaggi sopra soglia",
                    subtitle = "Percentuali calcolate sui soli messaggi sopra soglia",
                    barItems = uiModel.toxicShare,
                    accentColor = ChartTheme.TOXIC_MESSAGES_COLOR,
                    onBarClick = { item -> if (!item.isOthers) selectedSpeakerName = item.name }
                )
            }
        }
    }

    if (selectedStat != null) {
        val totalMsgs = uiModel.rawStats.sumOf { it.totalCount }
        val totalToxic = uiModel.rawStats.sumOf { it.toxicCount }

        ModalBottomSheet(
            onDismissRequest = { selectedSpeakerName = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = selectedStat.speakerLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(24.dp))

                DetailRow("Messaggi totali", "${selectedStat.totalCount} / $totalMsgs", (selectedStat.totalCount.toFloat() / totalMsgs * 100).roundToInt(), ChartTheme.ALL_MESSAGES_COLOR)
                DetailRow("Sopra soglia", "${selectedStat.toxicCount} / $totalToxic", if (totalToxic > 0) (selectedStat.toxicCount.toFloat() / totalToxic * 100).roundToInt() else 0, ChartTheme.TOXIC_MESSAGES_COLOR)

                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { selectedSpeakerName = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Chiudi")
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
                .fillMaxWidth(0.7f)
                .clip(MaterialTheme.shapes.extraSmall)
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

@Composable
private fun DetailRow(label: String, value: String, pct: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Text(
                text = "$pct%",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
