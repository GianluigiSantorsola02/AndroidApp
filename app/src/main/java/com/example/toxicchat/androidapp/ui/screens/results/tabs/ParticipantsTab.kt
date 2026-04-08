package com.example.toxicchat.androidapp.ui.screens.results.tabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.export.PaohvisGranularity
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
    val allMessages by analysisViewModel.allMessagesInRange.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Dominant Section: Comparison of total vs toxic share
        ParticipationWidget(
            stats = result.speakerStats,
            messages = allMessages,
            isGroup = result.metadata.isGroup
        )

        Spacer(Modifier.height(24.dp))

        // 2. Metrics Detail: Response Times (solo se NON è un gruppo)
        if (!result.metadata.isGroup) {
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

        // 3. Research Mode Card (PAOHVis) - Inlined configuration and results
        ResearchModeCardInternal(
            conversationId = conversationId,
            chatTitle = chatTitle,
            rangeStart = result.metadata.rangeStartMillis ?: 0L,
            rangeEnd = result.metadata.rangeEndMillis ?: System.currentTimeMillis(),
            viewModel = exportViewModel
        )

        Spacer(Modifier.height(48.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResearchModeCardInternal(
    conversationId: String,
    chatTitle: String,
    rangeStart: Long,
    rangeEnd: Long,
    viewModel: PaohvisExportViewModel
) {
    val exportState by viewModel.exportState.collectAsState()
    var isConfigExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedGranularity by rememberSaveable { mutableStateOf(PaohvisGranularity.AUTO) }
    val context = LocalContext.current

    // Feedback Toast when success
    LaunchedEffect(exportState) {
        if (exportState is PaohvisExportViewModel.ExportState.Success) {
            Toast.makeText(context, "Export PAOHVis salvato nella cartella Download", Toast.LENGTH_LONG).show()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFCE93D8).copy(alpha = 0.5f))
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
                "Esporta i dati in formato PAOHVis per l'analisi avanzata del comportamento comunicativo.",
                fontSize = 14.sp,
                color = Color.DarkGray
            )
            
            Spacer(Modifier.height(20.dp))

            // Expandable Config Section + Button
            Surface(
                onClick = { isConfigExpanded = !isConfigExpanded },
                color = Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE1BEE7))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Configura aggregazione",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B1FA2),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isConfigExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF7B1FA2)
                        )
                    }
                    
                    AnimatedVisibility(visible = isConfigExpanded) {
                        Column(Modifier.padding(top = 12.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                PaohvisGranularity.entries.forEachIndexed { index, gran ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PaohvisGranularity.entries.size),
                                        onClick = { selectedGranularity = gran },
                                        selected = selectedGranularity == gran
                                    ) {
                                        Text(gran.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                                    }
                                }
                            }
                            val description = when(selectedGranularity) {
                                PaohvisGranularity.AUTO -> "Scelta automatica (Giorno/Settimana)."
                                PaohvisGranularity.WEEK -> "Aggregazione settimanale ISO."
                                PaohvisGranularity.DAY -> "Aggregazione giornaliera."
                            }
                            Text(
                                description,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.startExport(conversationId, chatTitle, rangeStart, rangeEnd, selectedGranularity)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = exportState !is PaohvisExportViewModel.ExportState.Loading
                            ) {
                                if (exportState is PaohvisExportViewModel.ExportState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Genera CSV PAOHVis", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Inline Results
                            AnimatedVisibility(visible = exportState is PaohvisExportViewModel.ExportState.Success || exportState is PaohvisExportViewModel.ExportState.Error) {
                                Column(Modifier.padding(top = 16.dp)) {
                                    when (val state = exportState) {
                                        is PaohvisExportViewModel.ExportState.Success -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("CSV pronto!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { openPaohVisInBrowser(context) },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Apri", fontSize = 12.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = { shareFile(context, state.uri) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Invia", fontSize = 12.sp)
                                                }
                                                IconButton(onClick = { viewModel.clearExportState() }) {
                                                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                                                }
                                            }
                                        }
                                        is PaohvisExportViewModel.ExportState.Error -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { viewModel.clearExportState() }) {
                                                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
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
