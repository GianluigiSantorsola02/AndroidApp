package com.example.toxicchat.androidapp.ui.screens.results.tabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.export.PaohvisGranularity
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.domain.model.SpeakerToxicityStat
import com.example.toxicchat.androidapp.ui.screens.results.components.HeatmapGrid
import com.example.toxicchat.androidapp.ui.screens.results.components.TrendComboChart
import com.example.toxicchat.androidapp.ui.viewmodel.PaohvisExportViewModel

@Composable
fun AdvancedTab(
    result: AnalysisResult,
    conversationId: String,
    chatTitle: String,
    viewModel: PaohvisExportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TrendComboChart(result.weeklySeries)

        Spacer(Modifier.height(24.dp))

        if (result.speakerStats.size == 2) {
            result.responseStats?.let { stats ->
                Text(
                    "Tempi di risposta (1-1)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val speaker1 = result.speakerStats.getOrNull(0)?.speakerLabel ?: "Partecipante 1"
                    val speaker2 = result.speakerStats.getOrNull(1)?.speakerLabel ?: "Partecipante 2"

                    Column(Modifier.padding(16.dp)) {
                        ResponseStatRow(speaker1, speaker2, "%.1f min".format(stats.medianSelfToOtherMin))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))
                        ResponseStatRow(speaker2, speaker1, "%.1f min".format(stats.medianOtherToSelfMin))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (result.speakerStats.isNotEmpty()) {
            SpeakerDistributionSection(stats = result.speakerStats)
            Spacer(Modifier.height(24.dp))
        }

        HeatmapGrid(result.heatmap)

        Spacer(Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            shape = RoundedCornerShape(16.dp)
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
                    "Esporta i dati in formato PAOHVis per l'analisi temporale avanzata del comportamento comunicativo.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(20.dp))
                
                var showConfig by remember { mutableStateOf(false) }
                
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
                            viewModel.startExport(
                                conversationId,
                                chatTitle,
                                result.metadata.rangeStartMillis ?: 0L,
                                result.metadata.rangeEndMillis ?: System.currentTimeMillis(),
                                granularity
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    exportState?.let { state ->
        ExportResultDialog(
            state = state,
            onDismiss = { viewModel.clearExportState() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaohvisConfigDialog(
    onDismiss: () -> Unit,
    onExport: (PaohvisGranularity) -> Unit
) {
    var selectedGranularity by remember { mutableStateOf(PaohvisGranularity.AUTO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configura Export PAOHVis") },
        text = {
            Column {
                Text("Seleziona la granularità temporale per i dati:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PaohvisGranularity.entries.forEachIndexed { index, gran ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PaohvisGranularity.entries.size),
                            onClick = { selectedGranularity = gran },
                            selected = selectedGranularity == gran
                        ) {
                            Text(gran.name.lowercase().capitalize())
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                val description = when(selectedGranularity) {
                    PaohvisGranularity.AUTO -> "Sceglie automaticamente tra Giorno e Settimana in base al periodo."
                    PaohvisGranularity.WEEK -> "Aggrega i dati per settimana (ISO 8601)."
                    PaohvisGranularity.DAY -> "Aggrega i dati giorno per giorno."
                }
                Text(description, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedGranularity) }) {
                Text("Esporta CSV")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
fun ExportResultDialog(
    state: PaohvisExportViewModel.ExportState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Mostra il Toast quando l'export ha successo (come nel report PDF)
    LaunchedEffect(state) {
        if (state is PaohvisExportViewModel.ExportState.Success) {
            Toast.makeText(context, "Export PAOHVis salvato nella cartella Download", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (state is PaohvisExportViewModel.ExportState.Success) Color(0xFFE8F5E9) else Color(0xFFFDECEA)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state is PaohvisExportViewModel.ExportState.Success) Icons.Default.Science else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (state is PaohvisExportViewModel.ExportState.Success) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (state is PaohvisExportViewModel.ExportState.Success) "Export completato!" else "Errore export",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when(state) {
                        is PaohvisExportViewModel.ExportState.Success -> "Il file CSV è pronto nella cartella Download per essere analizzato su PAOHVis."
                        is PaohvisExportViewModel.ExportState.Error -> state.message
                        else -> ""
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                if (state is PaohvisExportViewModel.ExportState.Success) {
                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = { 
                            openPaohVisInBrowser(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Apri PAOHVis", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { shareFile(context, state.uri) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Condividi CSV", fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("Chiudi")
                }
            }
        }
    }
}

private fun openPaohVisInBrowser(context: Context) {
    val url = "https://aviz.fr/paohvis/paoh.html"
    try {
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}

private fun shareFile(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Condividi CSV PAOHVis"))
}

@Composable
private fun ResponseStatRow(from: String, to: String, value: String) {
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
fun SpeakerDistributionSection(stats: List<SpeakerToxicityStat>) {
    var expanded by remember { mutableStateOf(false) }
    val displayStats = if (expanded) stats else stats.take(5)

    Text("Messaggi critici per partecipante", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            displayStats.forEachIndexed { index, stat ->
                SpeakerRow(stat)
                if (index < displayStats.lastIndex) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
                }
            }
            if (stats.size > 5) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (expanded) "Mostra meno" else "Mostra tutti (${stats.size})")
                }
            }
        }
    }
}

@Composable
fun SpeakerRow(stat: SpeakerToxicityStat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stat.speakerLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${stat.toxicCount}/${stat.totalCount} messaggi tossici sui totali mandati",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Text(
            "%.1f%%".format(stat.toxicRate * 100),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F)
        )
    }
}
