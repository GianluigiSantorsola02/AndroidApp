package com.example.toxicchat.androidapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DisclaimerScreen(onAccepted: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "Avviso Importante",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Text(
                text = "Leggi attentamente prima di procedere.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                DisclaimerItem(
                    icon = Icons.Outlined.Info,
                    title = "Strumento informativo",
                    description = "Analisi automatica basata su algoritmi linguistici. Può commettere errori (falsi positivi/negativi)."
                )
                
                DisclaimerItem(
                    icon = Icons.Outlined.GppMaybe,
                    title = "Non sostituisce professionisti",
                    description = "Questa app non sostituisce psicologi, assistenti sociali o mediatori."
                )
                
                DisclaimerItem(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy assicurata",
                    description = "L'elaborazione avviene su server. Nessun dato viene memorizzato in modo permanente o associato a te"
                )
                
                DisclaimerItem(
                    icon = Icons.Outlined.WarningAmber,
                    title = "Situazioni gravi",
                    description = "In caso di violenza, minacce o bullismo, contatta immediatamente le autorità o i servizi di supporto."
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = onAccepted,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        text = "Ho capito, continua",
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun DisclaimerItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = Color(0xFFE0F2F1),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF006064),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
