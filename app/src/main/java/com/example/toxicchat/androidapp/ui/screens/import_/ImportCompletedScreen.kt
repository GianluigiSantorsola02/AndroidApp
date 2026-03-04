package com.example.toxicchat.androidapp.ui.screens.import_

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCompletedScreen(
    conversationId: String,
    onAnalyzeNow: () -> Unit,
    onLater: () -> Unit,
    onOpenChat: () -> Unit,
    onShowPrivacy: () -> Unit,
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Icona e testi principali (Centrati verticalmente nel primo blocco)
            Surface(Modifier.size(100.dp), shape = CircleShape, color = Color(0xFFE0F2F1)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF25D366), modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Import completato", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("La chat è stata caricata correttamente.", color = Color.Gray, fontSize = 16.sp)
            
            Spacer(Modifier.height(16.dp))
            
            TextButton(onClick = onShowPrivacy) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = Color(0xFF006064))
                Spacer(Modifier.width(8.dp))
                Text("Dettagli privacy", color = Color(0xFF006064), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(48.dp))

            // Azioni di analisi (Blocco centrale)
            Button(
                onClick = {
                    onAnalyzeNow()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006064)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Imposta analisi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onLater) {
                Text("Più tardi", color = Color.Gray, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.weight(1.2f)) // Spazio maggiore sotto per spingere "Apri chat" in fondo

            // Apri chat (In fondo alla pagina)
            TextButton(
                onClick = { onOpenChat() },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("Apri chat", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
