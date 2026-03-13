package com.example.toxicchat.androidapp.ui.screens.results.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.ui.screens.results.components.ParticipationWidget
import com.example.toxicchat.androidapp.ui.viewmodel.AnalysisViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.PaohvisExportViewModel

@Composable
fun ParticipantsTab(
    result: AnalysisResult,
    conversationId: String,
    chatTitle: String,
    exportViewModel: PaohvisExportViewModel = hiltViewModel(),
    analysisViewModel: AnalysisViewModel = hiltViewModel()
) {
    val exportState by exportViewModel.exportState.collectAsState()
    val allMessages by analysisViewModel.allMessagesInRange.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Dominant Section: Comparison of total vs toxic share
        // Passiamo anche la lista completa dei messaggi per il dettaglio
        ParticipationWidget(
            stats = result.speakerStats,
            messages = allMessages
        )

        Spacer(Modifier.height(24.dp))

        // 2. Metrics Detail: Response Times (solo se ci sono esattamente 2 speaker)
        if (result.speakerStats.size == 2) {
            result.responseStats?.let { stats ->
                Text(
                    "Tempi di risposta medi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val speaker1 = result.speakerStats.getOrNull(0)?.speakerLabel ?: "Partecipante 1"
                    val speaker2 = result.speakerStats.getOrNull(1)?.speakerLabel ?: "Partecipante 2"

                    Column(Modifier.padding(16.dp)) {
                        ResponseStatRowInternal(speaker1, speaker2, "%.1f min".format(stats.medianSelfToOtherMin))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.3f))
                        ResponseStatRowInternal(speaker2, speaker1, "%.1f min".format(stats.medianOtherToSelfMin))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // Rimosso: SpeakerDistributionSectionInternal (Messaggi critici per partecipante)
        // La logica è ora integrata nel dettaglio del ParticipationWidget

        // 4. Advanced Export Flow (PAOHVis)
        ResearchModeCardInternal(
            conversationId = conversationId,
            chatTitle = chatTitle,
            rangeStart = result.metadata.rangeStartMillis ?: 0L,
            rangeEnd = result.metadata.rangeEndMillis ?: System.currentTimeMillis(),
            viewModel = exportViewModel
        )

        Spacer(Modifier.height(48.dp))
    }

    // Export Result Dialogs
    exportState?.let { state ->
        ExportResultDialog(
            state = state,
            onDismiss = { exportViewModel.clearExportState() }
        )
    }
}

@Composable
private fun ResponseStatRowInternal(from: String, to: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(from, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp).padding(horizontal = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(to, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color(0xFF006064))
    }
}

@Composable
private fun ResearchModeCardInternal(
    conversationId: String,
    chatTitle: String,
    rangeStart: Long,
    rangeEnd: Long,
    viewModel: PaohvisExportViewModel
) {
    var showConfig by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE93D8).copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, null, tint = Color(0xFF7B1FA2))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Modalità Ricerca",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7B1FA2),
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Configura i dati per partecipante in formato PAOHVis per l'analisi avanzata.",
                fontSize = 14.sp,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(20.dp))
            
            Button(
                onClick = { showConfig = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Configura Export PAOHVis", fontWeight = FontWeight.Bold)
            }

            if (showConfig) {
                PaohvisConfigDialog(
                    onDismiss = { showConfig = false },
                    onExport = { granularity ->
                        showConfig = false
                        viewModel.startExport(conversationId, chatTitle, rangeStart, rangeEnd, granularity)
                    }
                )
            }
        }
    }
}
