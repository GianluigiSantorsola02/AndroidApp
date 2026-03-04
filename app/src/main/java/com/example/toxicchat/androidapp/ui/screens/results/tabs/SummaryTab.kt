package com.example.toxicchat.androidapp.ui.screens.results.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.ui.screens.results.components.DistributionChart
import com.example.toxicchat.androidapp.ui.screens.results.components.ParticipationWidget

@Composable
fun SummaryTab(
    result: AnalysisResult,
    onOpenHighlighted: () -> Unit,
    onExportPdf: () -> Unit
) {
    var isDisclaimerExpanded by rememberSaveable { mutableStateOf(false) }

    val totalMsg = result.weeklySeries.sumOf { it.totalMessages }
    val totalToxic = result.weeklySeries.sumOf { it.toxicMessages }
    
    val toxPct = if (totalMsg > 0) (totalToxic.toDouble() / totalMsg) * 100 else 0.0
    val toxPctString = if (result.weeklySeries.isEmpty()) "—" else "%.1f%%".format(toxPct)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Card KPI principale
        val cardColor = if (toxPct == 0.0 && result.weeklySeries.isNotEmpty()) Color.White else Color(0xFF006064)
        val contentColor = if (toxPct == 0.0 && result.weeklySeries.isNotEmpty()) Color.Black else Color.White
        
        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(12.dp),
            border = if (toxPct == 0.0 && result.weeklySeries.isNotEmpty()) BorderStroke(1.dp, Color.LightGray) else null
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TOSSICITÀ TOTALE", color = contentColor.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (result.weeklySeries.isNotEmpty()) {
                        val badge = when { 
                            toxPct == 0.0 -> "ASSENTE"
                            toxPct < 10 -> "BASSA" 
                            toxPct < 25 -> "MEDIA" 
                            else -> "ALTA" 
                        }
                        Surface(
                            color = if (toxPct == 0.0) Color.LightGray.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f), 
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(badge, color = if (toxPct == 0.0) Color.Gray else Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(toxPctString, color = contentColor, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("dei messaggi classificati come tossici", color = contentColor, fontSize = 14.sp)
                Text("${result.metadata.analyzedCount} su ${result.metadata.totalCount} messaggi analizzati", color = contentColor.copy(alpha = 0.6f), fontSize = 12.sp)
                
                Spacer(Modifier.height(16.dp))
                
                // Messaggio descrittivo migliorato
                Text(
                    text = when {
                        toxPct == 0.0 -> "Ottimo! Non sono stati rilevati messaggi sopra la soglia nel periodo selezionato."
                        toxPct < 5.0 -> "La conversazione è prevalentemente tranquilla, con pochissimi messaggi isolati sopra soglia."
                        toxPct < 20.0 -> "Sono presenti alcuni episodi di tensione. Consulta i grafici sotto per individuare i momenti critici."
                        else -> "Si nota una frequenza elevata di messaggi sopra soglia. I dati suggeriscono una conversazione accesa o problematica."
                    },
                    color = contentColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card Disclaimer importante (Collapsible)
        OutlinedCard(
            onClick = { isDisclaimerExpanded = !isDisclaimerExpanded }, 
            colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFF8E1)), 
            border = BorderStroke(1.dp, Color(0xFFFFE082))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFE65100))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Disclaimer importante", 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE65100)
                    )
                    Icon(
                        imageVector = if (isDisclaimerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFE65100)
                    )
                }
                
                AnimatedVisibility(visible = isDisclaimerExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Questi dati sono basati su algoritmi automatici di rilevamento del linguaggio. Non sostituiscono il parere di un esperto, un consulto medico o legale professionale. Usare con cautela.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF795548),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Widget Partecipazione
        ParticipationWidget(stats = result.speakerStats)

        Spacer(Modifier.height(24.dp))

        // Il grafico include già il titolo "Distribuzione per giorno"
        DistributionChart(result.heatmap)

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onExportPdf, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), 
            border = BorderStroke(1.dp, Color.LightGray), 
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileDownload, null)
            Spacer(Modifier.width(8.dp))
            Text("Esporta Report PDF")
        }
    }
}
