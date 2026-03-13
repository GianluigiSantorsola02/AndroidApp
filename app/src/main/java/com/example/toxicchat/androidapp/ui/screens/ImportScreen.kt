package com.example.toxicchat.androidapp.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.ui.components.ServerAnalysisConsentDialog
import com.example.toxicchat.androidapp.ui.viewmodel.ImportState
import com.example.toxicchat.androidapp.ui.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateToImportCompleted: (String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentSubScreen by remember { mutableStateOf("source") } // source, guide, picker
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedFileName = getFileName(context, it)
            selectedFileSize = getFileSize(context, it)
        }
    }

    Scaffold(
        topBar = {
            if (currentSubScreen != "source" || uiState !is ImportState.Idle) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { 
                            if (uiState !is ImportState.Idle) {
                                viewModel.reset()
                            } else {
                                currentSubScreen = "source"
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            when (val state = uiState) {
                is ImportState.Idle -> {
                    AnimatedContent(
                        targetState = currentSubScreen,
                        transitionSpec = {
                            if (targetState != "source") {
                                (slideInHorizontally { it } + fadeIn(tween(300))).togetherWith(
                                    slideOutHorizontally { -it / 2 } + fadeOut(tween(300))
                                )
                            } else {
                                (slideInHorizontally { -it / 2 } + fadeIn(tween(300))).togetherWith(
                                    slideOutHorizontally { it } + fadeOut(tween(300))
                                )
                            }.using(SizeTransform(clip = false))
                        },
                        label = "subscreen_transition"
                    ) { subScreen ->
                        when (subScreen) {
                            "source" -> ImportSourceContent(
                                onWhatsAppClick = { currentSubScreen = "picker" },
                                onGuideClick = { currentSubScreen = "guide" }
                            )
                            "guide" -> ExportGuideContent(onConfirm = { currentSubScreen = "source" })
                            "picker" -> FilePickerContent(
                                fileName = selectedFileName,
                                fileSize = selectedFileSize,
                                onPickFile = { filePickerLauncher.launch(arrayOf("text/plain", "application/zip")) },
                                onClearFile = { selectedUri = null; selectedFileName = null },
                                onStartImport = { selectedUri?.let { viewModel.startImport(it, selectedFileName!!) } }
                            )
                        }
                    }
                }
                is ImportState.Importing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF006064))
                    }
                }
                is ImportState.NeedsDateOrderChoice -> {
                    DateFormatContent(
                        onChoice = { viewModel.onDateOrderSelected(state.uri, state.fileName, it) }
                    )
                }
                is ImportState.Imported -> {
                    LaunchedEffect(state.conversationId) {
                        onNavigateToImportCompleted(state.conversationId)
                        viewModel.reset()
                    }
                }
                is ImportState.Error -> {
                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Errore", style = MaterialTheme.typography.headlineSmall)
                        Text(state.message, textAlign = TextAlign.Center)
                        Button(onClick = { viewModel.reset(); currentSubScreen = "source" }) { Text("Torna alla Home") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSourceContent(onWhatsAppClick: () -> Unit, onGuideClick: () -> Unit) {
    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy e Sicurezza") },
            text = {
                Text("L'elaborazione avviene su un server sicuro, ma nessun messaggio personale viene memorizzato permanentemente. I dati vengono trattati esclusivamente per l'analisi e rimossi subito dopo.")
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Ho capito")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Importa una chat",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Start
        )
        Text(
            text = "Seleziona la sorgente della conversazione.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Start
        )
        
        Spacer(Modifier.height(48.dp))
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SourceCard(
                title = "WhatsApp",
                subtitle = "Import da export .txt (o .zip)",
                icon = Icons.Default.ChatBubbleOutline,
                iconBg = Color(0xFFE0F2F1),
                iconTint = Color(0xFF25D366),
                onClick = onWhatsAppClick,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
        
        Spacer(Modifier.weight(1f))
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onGuideClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.HelpOutline, null, tint = Color(0xFF006064), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Come esportare da WhatsApp", color = Color(0xFF006064), fontWeight = FontWeight.SemiBold)
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPrivacyDialog = true },
                color = Color(0xFFF5F5F5),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF25D366)))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "ELABORAZIONE SU SERVER E IN LOCALE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(
    title: String, 
    subtitle: String, 
    icon: ImageVector, 
    iconBg: Color, 
    iconTint: Color, 
    enabled: Boolean = true, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF9F9F9)),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = iconBg) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (enabled) Color.Black else Color.Gray)
                Text(subtitle, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ExportGuideContent(onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Guida esportazione", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        
        GuideStep(1, "WhatsApp → apri chat → menu → Altro → Esporta chat")
        GuideStep(2, "Senza/Includi media")
        GuideStep(3, "Seleziona questa app dal pannello di condivisione")
        
        Spacer(Modifier.height(32.dp))
        
        Surface(
            color = Color(0xFFFFF8E1),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFFFE082)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircleOutline, null, tint = Color(0xFFF57C00))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Consigliato: ") }
                        append("senza media (l'analisi usa solo il testo per garantire velocità e privacy).")
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF795548)
                )
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        TextButton(onClick = onConfirm, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)) {
            Text("Ok, ho capito", fontSize = 18.sp, color = Color.Black)
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(32.dp), shape = CircleShape, color = Color(0xFF006064)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FilePickerContent(fileName: String?, fileSize: String?, onPickFile: () -> Unit, onClearFile: () -> Unit, onStartImport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Seleziona file chat", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(20.dp))
                .clickable { onPickFile() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(64.dp), shape = CircleShape, color = Color(0xFFF1F8F9)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FileUpload, null, tint = Color(0xFF006064), modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Scegli file (.txt)", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFF5F5F5), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, null, tint = Color.Gray)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Importa da condivisione", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Puoi anche usare \"Esporta chat\" su WhatsApp e selezionare questa app dal pannello \"Condividi\".", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        if (fileName != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF1F8F9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFB2DFDB))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, tint = Color(0xFF006064))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(fileSize ?: "", fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = onClearFile) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                }
            }
        }
        
        Spacer(Modifier.weight(1f))
        
        TextButton(
            onClick = onStartImport,
            enabled = fileName != null,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)
        ) {
            Text("Avvia import", fontSize = 18.sp, color = if (fileName != null) Color.Black else Color.LightGray)
        }
    }
}

@Composable
private fun DateFormatContent(onChoice: (DateOrderUsed) -> Unit) {
    var selected by remember { mutableStateOf(DateOrderUsed.DMY) }
    
    Column(Modifier.fillMaxSize()) {
        Text("Formato data", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("L'export può usare DD/MM o MM/DD. Seleziona il formato corretto per garantire un'analisi precisa.", color = Color.Gray)
        
        Spacer(Modifier.height(24.dp))
        
        Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF1976D2))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))) { append("Suggerimento: ") }
                        append("Predefinito coerente con l'area geografica del tuo dispositivo.")
                    },
                    fontSize = 13.sp, color = Color(0xFF1976D2)
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        DateOption("Giorno/Mese (DD/MM)", "Esempio: 24/12/2023", selected == DateOrderUsed.DMY) { selected = DateOrderUsed.DMY }
        Spacer(Modifier.height(16.dp))
        DateOption("Mese/Giorno (MM/DD)", "Esempio: 12/24/2023", selected == DateOrderUsed.MDY) { selected = DateOrderUsed.MDY }
        
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onChoice(selected) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)) {
            Text("Continua", fontSize = 18.sp, color = Color.Black)
        }
    }
}

@Composable
private fun DateOption(title: String, example: String, isSelected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFF006064) else Color.LightGray),
        color = if (isSelected) Color(0xFFF1F8F9) else Color.Transparent
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(example, fontSize = 13.sp, color = Color.Gray)
            }
            RadioButton(selected = isSelected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF006064)))
        }
    }
}

private fun getFileName(c: Context, u: Uri): String {
    var n = "file.txt"
    c.contentResolver.query(u, null, null, null, null)?.use { cursor ->
        val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i != -1 && cursor.moveToFirst()) n = cursor.getString(i)
    }
    return n
}

private fun getFileSize(c: Context, u: Uri): String {
    var s = 0L
    c.contentResolver.query(u, null, null, null, null)?.use { cursor ->
        val i = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (i != -1 && cursor.moveToFirst()) s = cursor.getLong(i)
    }
    return if (s > 1024 * 1024) "%.1f MB".format(s / (1024.0 * 1024.0)) else "%.1f KB".format(s / 1024.0)
}
