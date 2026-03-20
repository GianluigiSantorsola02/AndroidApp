package com.example.toxicchat.androidapp.ui.screens.results.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.domain.model.HeatmapCell
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import com.example.toxicchat.androidapp.ui.screens.results.components.HeatmapGrid
import com.example.toxicchat.androidapp.ui.screens.results.components.TrendComboChart
import com.example.toxicchat.androidapp.ui.viewmodel.AnalysisViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Helper per determinare il colore di severità in base alle soglie dell'app (coerente con DynamicsTab)
private fun getSeverityColor(toxicMessages: Int, score: Double?): Color = when {
    toxicMessages <= 0 -> Color(0xFFBDBDBD)
    (score ?: 0.0) >= 0.80 -> Color(0xFFD32F2F) // Rosso: criticità alta
    (score ?: 0.0) >= 0.50 -> Color(0xFFFFA000) // Giallo: criticità media
    else -> Color(0xFFBDBDBD)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryTab(
    result: AnalysisResult,
    onOpenHighlighted: () -> Unit,
    onExportPdf: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    var isDisclaimerExpanded by rememberSaveable { mutableStateOf(false) }
    
    // UI State for Bottom Sheets
    var selectedCell by remember { mutableStateOf<HeatmapCell?>(null) }
    var activeFilterCell by remember { mutableStateOf<HeatmapCell?>(null) }
    var showSlotMessagesSheet by remember { mutableStateOf(false) }

    val heatmapMessages by viewModel.heatmapDetailMessages.collectAsState()

    val totalMsg = result.metadata.totalCount
    val totalToxic = result.speakerStats.sumOf { it.toxicCount }
    val toxPct = if (totalMsg > 0) (totalToxic.toDouble() / totalMsg) * 100 else 0.0
    
    val maxWeeklyToxic = result.weeklySeries.maxByOrNull { it.toxicMessages }
    val participantsCount = result.speakerStats.size

    val toxicColor = when {
        toxPct > 15.0 -> Color(0xFFD32F2F) // High: Red
        toxPct > 5.0 -> Color(0xFFFFA000)  // Med: Amber
        else -> Color(0xFF2E7D32)          // Low: Green
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Panoramica Analisi",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Riepilogo generale del clima della chat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // --- 1. KPI AREA ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMiniCard(
                label = "Tossicità",
                value = "%.1f%%".format(toxPct),
                icon = Icons.Default.Warning,
                color = toxicColor,
                modifier = Modifier.weight(1f)
            )
            KpiMiniCard(
                label = "Msg. Critici",
                value = totalToxic.toString(),
                icon = Icons.Default.ChatBubble,
                color = Color(0xFF757575),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiMiniCard(
                label = "Picco settimanale",
                value = maxWeeklyToxic?.toxicMessages?.toString() ?: "0",
                icon = Icons.Default.TrendingUp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.weight(1f)
            )
            KpiMiniCard(
                label = "Partecipanti",
                value = participantsCount.toString(),
                icon = Icons.Default.People,
                color = Color(0xFF757575),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(32.dp))

        // --- 2. INSIGHT CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = toxicColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = when {
                        toxPct == 0.0 -> "La conversazione è stata tranquilla, senza messaggi critici nel periodo analizzato."
                        toxPct < 5.0 -> "La conversazione appare per lo più tranquilla, con pochi momenti di tensione."
                        toxPct < 15.0 -> "Sono presenti alcuni momenti critici che meritano attenzione."
                        else -> "Si osservano frequenti segnali di tensione, con numerosi messaggi critici."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- DISCLAIMER ---
        Surface(
            onClick = { isDisclaimerExpanded = !isDisclaimerExpanded },
            color = Color(0xFFFFF8E1),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFFFE082))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Disclaimer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE65100)
                    )
                    Icon(
                        imageVector = if (isDisclaimerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = isDisclaimerExpanded) {
                    Text(
                        text = "L’analisi è automatica e si basa su modelli di intelligenza artificiale, quindi può contenere errori o imprecisioni. Non sostituisce il parere di un medico, psicologo o altro professionista qualificato.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF795548),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )

        Spacer(Modifier.height(48.dp))
        // --- 3. MAIN TEMPORAL OVERVIEW ---
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TrendComboChart(series = result.weeklySeries)
        }

        Spacer(Modifier.height(58.dp))
        // --- GLOBAL HEATMAP SECTION ---
        Text(
            "Distribuzione per giorno e orario",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Media dei messaggi tossici per giorno e orario. Tocca una cella per vedere il dettaglio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        HeatmapGrid(
            cells = result.heatmap,
            onCellClick = { selectedCell = it }
        )

        Spacer(Modifier.height(48.dp))

        // --- ACTIONS ---
        Button(
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.FileDownload, null)
            Spacer(Modifier.width(8.dp))
            Text("Genera Report PDF", fontWeight = FontWeight.Bold)
        }

    }

    // Sheet 1: Aggregated Pattern Detail
    if (selectedCell != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedCell = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            HeatmapCellDetail(
                cell = selectedCell!!,
                onShowMessages = {
                    activeFilterCell = selectedCell
                    viewModel.setHeatmapFilter(selectedCell)
                    showSlotMessagesSheet = true
                    selectedCell = null // Close this sheet to show the next one
                }
            )
        }
    }

    // Sheet 2: Real Message List for the Slot
    if (showSlotMessagesSheet && activeFilterCell != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                showSlotMessagesSheet = false
                activeFilterCell = null
                viewModel.setHeatmapFilter(null)
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Box(Modifier.fillMaxHeight(0.85f)) {
                val allParticipants = remember(result.speakerStats) {
                    result.speakerStats
                        .map { it.speakerLabel.trim() }
                        .filter { it.isNotEmpty() && !it.equals("Sistema", ignoreCase = true) }
                        .distinct()
                }
                HeatmapSlotMessages(
                    cell = activeFilterCell!!,
                    messages = heatmapMessages,
                    isGroup = result.metadata.isGroup,
                    allParticipants = allParticipants
                )
            }
        }
    }
}

@Composable
fun HeatmapCellDetail(
    cell: HeatmapCell,
    onShowMessages: () -> Unit
) {
    val days = listOf("", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val dayName = days.getOrElse(cell.dayOfWeek) { "" }
    val hourStart = "%02d:00".format(cell.hour)
    val hourEnd = "%02d:00".format((cell.hour + 1) % 24)

    // Colore basato sul toxicRate medio della cella per coerenza con il "tau"
    // Qui usiamo il toxicRate come indicatore di severità media per la cella
    val cellSeverityColor = if (cell.toxicCount > 0) {
        if (cell.toxicRate >= 0.3) Color(0xFFD32F2F) else Color(0xFFFFA000)
    } else {
        Color.DarkGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "$dayName · $hourStart–$hourEnd",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Valori medi del giorno e fascia oraria scelti",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(Modifier.height(32.dp))

        // Aggregated Metrics
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetricPill(
                label = "Messaggi totali",
                value = cell.totalCount.toString(),
                color = Color.DarkGray,
                modifier = Modifier.weight(1f)
            )
            DetailMetricPill(
                label = "Messaggi critici",
                value = cell.toxicCount.toString(),
                color = cellSeverityColor,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetricPill(
                label = "Percentuale messaggi critici",
                value = "%.1f%%".format(cell.toxicRate * 100),
                color = cellSeverityColor,
                modifier = Modifier.weight(1f)
            )
            DetailMetricPill(
                label = "Rilevanza",
                value = when {
                    cell.toxicRate > 0.3 -> "Alta"
                    cell.toxicRate > 0.1 -> "Media"
                    cell.toxicRate > 0 -> "Bassa"
                    else -> "Assente"
                },
                color = if (cell.toxicRate > 0) Color(0xFFFFA000) else Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onShowMessages,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Mostra i messaggi di questa fascia", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HeatmapSlotMessages(
    cell: HeatmapCell,
    messages: List<MessageEvent>,
    isGroup: Boolean,
    allParticipants: List<String>
) {
    var toxicOnly by remember { mutableStateOf(false) }
    val displayMessages = if (toxicOnly) messages.filter { it.isToxic } else messages

    val days = listOf("", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val dayName = days.getOrElse(cell.dayOfWeek) { "" }
    val hourStart = "%02d:00".format(cell.hour)
    val hourEnd = "%02d:00".format((cell.hour + 1) % 24)
    
    val italianLocale = Locale.ITALY
    val dateFormatter = remember { 
        DateTimeFormatter.ofPattern("dd MMM yyyy", italianLocale).withZone(ZoneId.systemDefault()) 
    }
    val timeFormatter = remember { 
        DateTimeFormatter.ofPattern("HH:mm", italianLocale).withZone(ZoneId.systemDefault()) 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "$dayName · $hourStart–$hourEnd",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (toxicOnly) "Visualizzazione dei soli messaggi critici inviati in questa fascia oraria nei giorni corrispondenti."
                   else "Tutti i messaggi inviati in questa fascia oraria nei giorni corrispondenti.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text(
                text = "Solo messaggi critici",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = toxicOnly,
                onCheckedChange = { toxicOnly = it },
                modifier = Modifier.scale(0.8f)
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(displayMessages) { msg ->
                // Colore di severità basato sul toxScore (tau) del singolo messaggio
                val itemSeverityColor = getSeverityColor(if (msg.isToxic) 1 else 0, msg.toxScore)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.isToxic) itemSeverityColor.copy(alpha = 0.05f) else Color(0xFFF5F5F5)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (msg.isToxic) BorderStroke(1.dp, itemSeverityColor.copy(alpha = 0.1f)) else null
                ) {
                    Column(Modifier.padding(12.dp)) {
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

                            val instant = Instant.ofEpochMilli(msg.timestampEpochMillis)
                            Text(
                                text = "${dateFormatter.format(instant)} · ${timeFormatter.format(instant)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        if (msg.isToxic) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = itemSeverityColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = itemSeverityColor, modifier = Modifier.size(12.dp))
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
            if (displayMessages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (toxicOnly) "Nessun messaggio critico trovato per questa fascia."
                                   else "Nessun messaggio trovato per questa fascia oraria.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun KpiMiniCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color.copy(alpha = 0.8f))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
