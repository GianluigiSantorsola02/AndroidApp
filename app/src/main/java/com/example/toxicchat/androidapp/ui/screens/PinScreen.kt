package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.toxicchat.androidapp.ui.viewmodel.PinMode
import com.example.toxicchat.androidapp.ui.viewmodel.SecurityViewModel

@Composable
fun PinScreen(
    onAuthenticated: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    // Surface garantisce uno sfondo solido e previene i "glitch" grafici
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White // Forza sfondo bianco come da screenshot
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // Evita sovrapposizione con la status bar
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = when (state.mode) {
                    PinMode.CREATE -> "Crea PIN"
                    PinMode.CONFIRM -> "Conferma PIN"
                    PinMode.UNLOCK -> "Inserisci PIN"
                },
                fontSize = 28.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Protegge l'accesso in un contesto sensibile.",
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Pallini indicatori (centrati)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { i ->
                    val filled = i < state.pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(1.5.dp, Color(0xFF006064), CircleShape)
                            .background(
                                if (filled) Color(0xFF006064) else Color.Transparent, 
                                CircleShape
                            )
                    )
                }
            }

            state.error?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Tastierino numerico
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "Canc")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                keys.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.weight(1f).height(72.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(if (key == "Canc") Color.Transparent else Color(0xFFF5F5F5))
                                        .clickable {
                                            if (key == "Canc") viewModel.onCancelClick()
                                            else viewModel.onNumberClick(key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 24.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            TextButton(
                onClick = { viewModel.onContinue() },
                enabled = state.pin.length == 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Continua",
                    fontSize = 18.sp,
                    color = if (state.pin.length == 4) Color.Black else Color.Gray.copy(alpha = 0.5f)
                )
            }
            
            // Padding per la navigation bar in fondo
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
