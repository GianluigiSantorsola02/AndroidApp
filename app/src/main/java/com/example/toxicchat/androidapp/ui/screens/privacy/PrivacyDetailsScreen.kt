package com.example.toxicchat.androidapp.ui.screens.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDetailsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dettagli privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PrivacySection("Cosa viene inviato", "Per ogni messaggio inviamo solo il testo e un identificativo tecnico (messageLocalId).\n" +
                    "La richiesta include anche un identificativo interno della chat (conversationId) e la versione del modello.\n" +
                    "Non inviamo nomi dei partecipanti, nome della chat, numeri di telefono, né allegati/media.")
            PrivacySection("Finalità", "I dati vengono usati esclusivamente per calcolare il punteggio di tossicità dei messaggi e restituire i risultati all’app per la visualizzazione e le statistiche.")
            PrivacySection("Conservazione", "I messaggi vengono elaborati in memoria sul server e non vengono conservati in modo permanente oltre il tempo strettamente necessario a produrre la risposta dell'analisi.")
            PrivacySection("Sicurezza", "Il trasferimento avviene tramite connessione cifrata (TLS)")
            PrivacySection("Controllo utente", "Puoi scegliere di non analizzare le chat o disattivare l'analisi automatica in qualsiasi momento.")
            
            Spacer(Modifier.height(32.dp))
            Text(
                "Questa analisi è automatica e non sostituisce un parere professionale.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF006064))
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = Color.DarkGray)
    }
}
