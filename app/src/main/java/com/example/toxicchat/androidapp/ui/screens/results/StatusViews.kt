package com.example.toxicchat.androidapp.ui.screens.results

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.toxicchat.androidapp.domain.model.AnalysisMetadata
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonAnalizzataView(
    metadata: AnalysisMetadata,
    onChangePreset: (AnalysisRangePreset) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Analytics, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Text("Configura analisi", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Scegli l’intervallo e avvia l’analisi per generare i grafici.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        RangePresetSelector(
            selected = metadata.rangePreset,
            onSelect = onChangePreset
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = formatRange(metadata.rangeStartMillis, metadata.rangeEndMillis),
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onStart,
            enabled = metadata.rangePreset != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF006064),
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            )
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Avvia ora")
        }
    }
}

@Composable
fun InProgressView(metadata: AnalysisMetadata) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color(0xFF006064))
        Spacer(Modifier.height(24.dp))
        Text("Analisi in corso...", style = MaterialTheme.typography.titleLarge)

        if (metadata.totalCount > 0) {
            val progress = (metadata.analyzedCount.toFloat() / metadata.totalCount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                color = Color(0xFF006064)
            )
            Text("${metadata.analyzedCount} / ${metadata.totalCount} messaggi elaborati")
        } else {
            Text("Preparazione…", color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun PausaView(metadata: AnalysisMetadata, onChangePreset: (AnalysisRangePreset) -> Unit, onRetry: () -> Unit) {
    BannerWithRange(
        title = "Analisi in pausa",
        description = "Puoi riprendere oppure cambiare intervallo e ripartire.",
        metadata = metadata,
        onChangePreset = onChangePreset,
        buttonText = "Riprendi",
        onAction = onRetry
    )
}

@Composable
fun ErroreView(metadata: AnalysisMetadata, onChangePreset: (AnalysisRangePreset) -> Unit, onRetry: () -> Unit) {
    BannerWithRange(
        title = "Si è verificato un errore",
        description = "Riprova o cambia intervallo per ridurre il carico.",
        metadata = metadata,
        onChangePreset = onChangePreset,
        buttonText = "Riprova",
        onAction = onRetry,
        isError = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BannerWithRange(
    title: String,
    description: String,
    metadata: AnalysisMetadata,
    onChangePreset: (AnalysisRangePreset) -> Unit,
    buttonText: String,
    onAction: () -> Unit,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified
        )
        Text(
            description,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
            color = Color.Gray
        )

        RangePresetSelector(
            selected = metadata.rangePreset,
            onSelect = onChangePreset
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatRange(metadata.rangeStartMillis, metadata.rangeEndMillis),
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAction,
            enabled = metadata.rangePreset != null
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangePresetSelector(
    selected: AnalysisRangePreset?,
    onSelect: (AnalysisRangePreset) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        PresetChip("Ultimo mese", selected == AnalysisRangePreset.LAST_MONTH) { onSelect(AnalysisRangePreset.LAST_MONTH) }
        Spacer(Modifier.width(8.dp))
        PresetChip("Ultimi 6 mesi", selected == AnalysisRangePreset.LAST_6_MONTHS) { onSelect(AnalysisRangePreset.LAST_6_MONTHS) }
        Spacer(Modifier.width(8.dp))
        PresetChip("Tutta la chat", selected == AnalysisRangePreset.ALL_TIME) { onSelect(AnalysisRangePreset.ALL_TIME) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

private fun formatRange(start: Long?, end: Long?): String {
    if (start == null || end == null) return "Intervallo: non impostato"
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val zs = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(start).atZone(zs).toLocalDate().format(fmt)
    val e = Instant.ofEpochMilli(end).atZone(zs).toLocalDate().format(fmt)
    return "Intervallo: $s → $e"
}
