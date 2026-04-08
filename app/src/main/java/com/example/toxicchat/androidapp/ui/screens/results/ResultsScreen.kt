package com.example.toxicchat.androidapp.ui.screens.results

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import com.example.toxicchat.androidapp.ui.components.ServerAnalysisConsentDialog
import com.example.toxicchat.androidapp.ui.screens.results.tabs.DynamicsTab
import com.example.toxicchat.androidapp.ui.screens.results.tabs.ParticipantsTab
import com.example.toxicchat.androidapp.ui.screens.results.tabs.SummaryTab
import com.example.toxicchat.androidapp.ui.viewmodel.AnalysisViewModel
import com.example.toxicchat.androidapp.util.ReportPrivacyMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenHighlighted: () -> Unit,
    onExportPdf: () -> Unit,
    onRequestPaohVisExport: () -> Unit,
    onShowPrivacy: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val exportUri by viewModel.exportUri.collectAsState()
    val context = LocalContext.current

    // Dynamics State Collection
    val selectedWeek by viewModel.selectedWeek.collectAsState()
    val selectedWeekEvents by viewModel.selectedWeekEvents.collectAsState()
    val selectedWeekDayStats by viewModel.selectedWeekDayStats.collectAsState()
    val selectedDayStartMillis by viewModel.selectedDayStartMillis.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showRangeSheet by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingAnalysisAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val sheetState = rememberModalBottomSheetState()

    var hasAutoStarted by rememberSaveable(conversationId) { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(conversationId) {
        viewModel.loadResult(conversationId)
        selectedTab = 0
        hasAutoStarted = false
    }

    // Handle PDF Export sharing and feedback
    LaunchedEffect(exportUri) {
        exportUri?.let { uri ->
            // Feedback visivo: conferma il salvataggio
            Toast.makeText(context, "Report salvato nella cartella Download", Toast.LENGTH_LONG).show()

            // Opzione: Apri il file per visualizzarlo o condividerlo
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Apri o Condividi Report PDF"))
            viewModel.clearExportUri()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Dettagli Analisi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Altro")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            val currentStatus = (uiState as? AnalysisResultUiState.Success)?.result?.status
            val isAnalyzed = currentStatus == AnalysisStatus.ANALIZZATA

            if (isAnalyzed) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF006064)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Riepilogo") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Dettagli") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Partecipanti") }
                    )
                }
            }

            when (val state = uiState) {
                is AnalysisResultUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF006064))
                    }
                }

                is AnalysisResultUiState.Success -> {
                    val result = state.result
                    when (result.status) {
                        AnalysisStatus.NON_ANALIZZATA -> {
                            NonAnalizzataView(
                                metadata = result.metadata,
                                onChangePreset = { preset ->
                                    viewModel.setRangePreset(preset)
                                    hasAutoStarted = false
                                },
                                onStart = {
                                    pendingAnalysisAction = {
                                        viewModel.startAnalysis()
                                        hasAutoStarted = true
                                    }
                                    showConsentDialog = true
                                }
                            )
                        }

                        AnalysisStatus.IN_CORSO -> InProgressView(result.metadata)

                        AnalysisStatus.PAUSA -> PausaView(
                            metadata = result.metadata,
                            onChangePreset = { preset ->
                                viewModel.setRangePreset(preset)
                                hasAutoStarted = false
                            },
                            onRetry = {
                                pendingAnalysisAction = { viewModel.retryAnalysis() }
                                showConsentDialog = true
                            }
                        )

                        AnalysisStatus.ERRORE -> ErroreView(
                            metadata = result.metadata,
                            onChangePreset = { preset ->
                                viewModel.setRangePreset(preset)
                                hasAutoStarted = false
                            },
                            onRetry = {
                                pendingAnalysisAction = { viewModel.retryAnalysis() }
                                showConsentDialog = true
                            }
                        )

                        AnalysisStatus.ANALIZZATA -> {
                            when (selectedTab) {
                                0 -> SummaryTab(
                                    result = result,
                                    onOpenHighlighted = onOpenHighlighted,
                                    onExportPdf = { mode -> 
                                        viewModel.exportPdfReport(mode)
                                    }
                                )
                                1 -> DynamicsTab(
                                    result = result,
                                    selectedWeek = selectedWeek,
                                    selectedWeekEvents = selectedWeekEvents,
                                    selectedWeekDayStats = selectedWeekDayStats,
                                    selectedDayStartMillis = selectedDayStartMillis,
                                    onSelectWeek = { viewModel.selectWeek(it) },
                                    onSelectDay = { viewModel.selectDay(it) }
                                )
                                2 -> ParticipantsTab(
                                    result = result,
                                    conversationId = conversationId,
                                    chatTitle = conversation?.title ?: "Chat"
                                )
                            }
                        }
                    }
                }

                is AnalysisResultUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Errore: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showConsentDialog) {
        ServerAnalysisConsentDialog(
            onDismiss = {
                showConsentDialog = false
                pendingAnalysisAction = null
            },
            onAccept = {
                showConsentDialog = false
                pendingAnalysisAction?.invoke()
                pendingAnalysisAction = null
            },
            onReadMore = onShowPrivacy
        )
    }

    if (showRangeSheet) {
        val selectedPreset = (uiState as? AnalysisResultUiState.Success)?.result?.metadata?.rangePreset
        ModalBottomSheet(
            onDismissRequest = { showRangeSheet = false },
            sheetState = sheetState
        ) {
            RangePickerContent(
                selected = selectedPreset,
                onSelect = { preset ->
                    viewModel.setRangePreset(preset)
                    hasAutoStarted = false
                    showRangeSheet = false
                }
            )
        }
    }
}

@Composable
fun RangePickerContent(
    selected: AnalysisRangePreset?,
    onSelect: (AnalysisRangePreset) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Text(
            "Scegli intervallo analisi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        RangeOption("Ultimo mese", "Analizza solo i messaggi degli ultimi 30 giorni.", selected == AnalysisRangePreset.LAST_MONTH) {
            onSelect(AnalysisRangePreset.LAST_MONTH)
        }
        RangeOption("Ultimi 6 mesi", "Analizza i messaggi degli ultimi 180 giorni.", selected == AnalysisRangePreset.LAST_6_MONTHS) {
            onSelect(AnalysisRangePreset.LAST_6_MONTHS)
        }
        RangeOption("Tutta la chat", "Analizza l'intera cronologia della chat.", selected == AnalysisRangePreset.ALL_TIME) {
            onSelect(AnalysisRangePreset.ALL_TIME)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun RangeOption(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            if (isSelected) {
                RadioButton(selected = true, onClick = null)
            }
        }
    }
}
