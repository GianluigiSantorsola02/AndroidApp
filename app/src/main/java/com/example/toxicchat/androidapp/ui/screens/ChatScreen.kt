package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import com.example.toxicchat.androidapp.ui.components.DateSeparatorBubble
import com.example.toxicchat.androidapp.ui.components.MessageBubble
import com.example.toxicchat.androidapp.ui.components.ServerAnalysisConsentDialog
import com.example.toxicchat.androidapp.ui.viewmodel.AnalysisViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.ChatUiItem
import com.example.toxicchat.androidapp.ui.viewmodel.ChatViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.SettingsViewModel
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateToResults: (String) -> Unit,
    onNavigateToPrivacy: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
    analysisViewModel: AnalysisViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val conv by chatViewModel.conversation.collectAsState()
    val messages = chatViewModel.messages.collectAsLazyPagingItems()
    val showOnlyToxic by chatViewModel.showOnlyToxic.collectAsState()
    
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingAnalysisAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showCompletedBanner by rememberSaveable(conversationId) { mutableStateOf(true) }

    LaunchedEffect(conversationId) {
        chatViewModel.loadConversation(conversationId)
        analysisViewModel.loadResult(conversationId)
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
            onReadMore = onNavigateToPrivacy
        )
    }

    val dateSeparatorFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFECE5DD))
        ) {
            conv?.let { c ->
                val status = c.analysisStatus
                if (status != AnalysisStatus.ANALIZZATA || (status == AnalysisStatus.ANALIZZATA && showCompletedBanner)) {
                    AnalysisBanner(
                        status = status,
                        onAction = {
                            when (status) {
                                AnalysisStatus.NON_ANALIZZATA -> {
                                    pendingAnalysisAction = { analysisViewModel.startAnalysis() }
                                    showConsentDialog = true
                                }
                                AnalysisStatus.IN_CORSO -> onNavigateToResults(conversationId)
                                AnalysisStatus.ANALIZZATA -> onNavigateToResults(conversationId)
                                AnalysisStatus.PAUSA, AnalysisStatus.ERRORE -> {
                                    pendingAnalysisAction = { analysisViewModel.retryAnalysis() }
                                    showConsentDialog = true
                                }
                            }
                        },
                        onDismiss = { showCompletedBanner = false }
                    )
                }
            }

            // Filter Toggle (Pinned in alto)
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !showOnlyToxic,
                        onClick = { chatViewModel.toggleToxicFilter(false) },
                        label = { Text("Tutti", fontSize = 12.sp) },
                        modifier = Modifier.semantics { contentDescription = "Mostra tutti i messaggi" },
                        shape = RoundedCornerShape(16.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = showOnlyToxic,
                        onClick = { chatViewModel.toggleToxicFilter(true) },
                        label = { Text("Segnalati", fontSize = 12.sp) },
                        modifier = Modifier.semantics { contentDescription = "Mostra solo messaggi segnalati" },
                        shape = RoundedCornerShape(16.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                reverseLayout = true
            ) {
                items(
                    count = messages.itemCount,
                    key = messages.itemKey { item ->
                        when (item) {
                            is ChatUiItem.MessageItem -> item.message.messageId
                            is ChatUiItem.DateSeparatorItem -> item.key
                        }
                    }
                ) { idx ->
                    when (val item = messages[idx]) {
                        is ChatUiItem.MessageItem -> {
                            MessageBubble(
                                message = item.message,
                                selectedSelfName = conv?.selectedSelfName,
                                selfAliases = conv?.selfAliases ?: emptyList(),
                                isGroupChat = conv?.isGroup ?: false
                            )
                        }
                        is ChatUiItem.DateSeparatorItem -> {
                            DateSeparatorBubble(
                                date = item.date.format(dateSeparatorFormatter)
                            )
                        }
                        null -> { /* Placeholder */ }
                    }
                }
            }
        }
    }
}

data class BannerColors(
    val text: String,
    val cta: String,
    val bgColor: Color,
    val contentColor: Color,
    val icon: ImageVector
)

@Composable
fun AnalysisBanner(
    status: AnalysisStatus,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = when (status) {
        AnalysisStatus.NON_ANALIZZATA -> BannerColors("Analisi non avviata", "Avvia analisi", Color(0xFFE3F2FD), Color(0xFF1976D2), Icons.Default.PlayArrow)
        AnalysisStatus.IN_CORSO -> BannerColors("Analisi in corso...", "Vedi risultati", Color(0xFFFFF8E1), Color(0xFFF57C00), Icons.AutoMirrored.Filled.ArrowForward)
        AnalysisStatus.ANALIZZATA -> BannerColors("Analisi completata", "Vedi risultati", Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.AutoMirrored.Filled.ArrowForward)
        AnalysisStatus.PAUSA -> BannerColors("Analisi in pausa", "Riprendi", Color(0xFFF5F5F5), Color.Gray, Icons.Default.Refresh)
        AnalysisStatus.ERRORE -> BannerColors("Errore analisi", "Riprova", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, Icons.Default.Refresh)
    }

    Surface(
        color = colors.bgColor,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(colors.icon, null, tint = colors.contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(colors.text, style = MaterialTheme.typography.bodyMedium, color = colors.contentColor, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) {
                Text(
                    text = colors.cta,
                    fontWeight = FontWeight.Bold,
                    color = colors.contentColor,
                    fontSize = 14.sp
                )
            }
            if (status == AnalysisStatus.ANALIZZATA) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi notifica", tint = colors.contentColor)
                }
            }
        }
    }
}
